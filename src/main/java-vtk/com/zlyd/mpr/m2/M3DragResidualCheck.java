package com.zlyd.mpr.m2;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.dicom.DicomScanner;
import com.zlyd.mpr.dicom.SeriesGeometry;
import com.zlyd.mpr.dicom.SeriesGrouper;
import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.dicom.VolumeGeometry;
import com.zlyd.mpr.geometry.Measurement;
import com.zlyd.mpr.geometry.MeasurementType;
import com.zlyd.mpr.geometry.MprCursorFrame;
import com.zlyd.mpr.geometry.MprViewOrientation;
import com.zlyd.mpr.geometry.MprViewRig;
import com.zlyd.mpr.geometry.VolumeBox;
import com.zlyd.mpr.mpr.BuiltVolume;
import com.zlyd.mpr.mpr.MprCameraController;
import com.zlyd.mpr.mpr.MprMeasurementOverlay;
import com.zlyd.mpr.mpr.MprViewMapper;
import com.zlyd.mpr.mpr.VolumeBuilder;
import com.zlyd.mpr.util.VtkImageExporter;
import com.zlyd.mpr.util.VtkNativeLoader;
import com.zlyd.mpr.util.WindowLevel;
import com.zlyd.mpr.util.WindowLevelDefaults;

import vtk.vtkImageInterpolator;
import vtk.vtkImageResliceMapper;
import vtk.vtkImageSlice;
import vtk.vtkPlane;
import vtk.vtkRenderWindow;
import vtk.vtkRenderWindowInteractor;
import vtk.vtkRenderer;

/**
 * 拖动残留排查（只做诊断，不改产品代码）：
 *
 * <p>复现"画一条线段 → 选中拖动（逐帧 replaceMeasurement，模拟真实拖动）→ 原地是否残留副本"。
 * 判定：拖动结束后，**起始位置附近**的轮廓像素应归零、且全屏轮廓像素约等于一条线（不应翻倍）。</p>
 */
public final class M3DragResidualCheck {

    private static final Logger LOG = LogManager.getLogger(M3DragResidualCheck.class);
    private static final int VIEW_COUNT = 3;
    private static final int SIZE = 400;

    static {
        VtkNativeLoader.load();
    }

    private final vtkRenderWindow renderWindow = new vtkRenderWindow();
    private final vtkRenderWindowInteractor interactor = new vtkRenderWindowInteractor();
    private final vtkRenderer[] renderers = {new vtkRenderer(), new vtkRenderer(), new vtkRenderer()};
    private final vtkPlane[] planes = new vtkPlane[VIEW_COUNT];

    private VolumeGeometry geometry;
    private VolumeBox box;
    private MprViewMapper viewMapper;
    private MprMeasurementOverlay overlay;

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Files.createDirectories(Paths.get("target", "drag"));
        SeriesInfo target = pickLargestReconstructable(new SeriesGrouper()
                .group(new DicomScanner().scan(root)));
        if (target == null) {
            LOG.warn("没有可重建的序列");
            return;
        }
        BuiltVolume built = new VolumeBuilder().build(target);
        new M3DragResidualCheck().run(built, target);
    }

    private void run(BuiltVolume built, SeriesInfo series) throws Exception {
        geometry = built.getGeometry();
        box = VolumeBox.of(geometry);
        configureRenderWindow();
        configureSlices(built, series);
        viewMapper = new MprViewMapper(interactor, renderers);
        overlay = new MprMeasurementOverlay(renderers, viewMapper);
        MprCameraController cameraController = new MprCameraController(renderers);

        MprCursorFrame frame = MprCursorFrame.initial(geometry);
        cameraController.reset(frame, box, MprViewRig.continuity());
        updatePlanes(frame);
        renderWindow.Render();

        double[] center = frame.center();
        double[] axisX = geometry.getAxisX();
        double[] start = offset(center, axisX, -60);
        double[] end = offset(center, axisX, 60);
        int view = MprViewOrientation.VIEW_AXIAL;

        // 画一条线段
        overlay.addMeasurement(new Measurement(MeasurementType.LENGTH, view, List.of(start, end), 120.0, null));
        renderWindow.Render();
        int linePixels = countOutlinePixels();
        double[] displayStart = viewMapper.worldToDisplay(view, start);
        double[] displayEnd = viewMapper.worldToDisplay(view, end);
        int startSpotBefore = countOutlinePixelsNear(displayStart, 8);
        LOG.info("初始: 线段像素={} 起点附近={}", linePixels, startSpotBefore);

        // 模拟真实拖动：逐帧平移（每帧 replaceMeasurement + Render），共 30 帧
        double[] shift = {0.0, 6.0, 0.0};
        List<double[]> current = new ArrayList<>(List.of(start, end));
        for (int step = 0; step < 30; step++) {
            List<double[]> moved = new ArrayList<>();
            for (double[] point : current) {
                moved.add(new double[]{point[0] + shift[0], point[1] + shift[1], point[2] + shift[2]});
            }
            current = moved;
            overlay.replaceMeasurement(0, new Measurement(MeasurementType.LENGTH, view, moved, 120.0, null));
            renderWindow.Render();
        }
        int linePixelsAfter = countOutlinePixels();
        int startSpotAfter = countOutlinePixelsNear(displayStart, 8);
        double[] displayMovedStart = viewMapper.worldToDisplay(view, current.get(0));
        int movedSpotAfter = countOutlinePixelsNear(displayMovedStart, 8);
        LOG.info("拖动后: 线段像素={}（应为一条线的量级） 原起点附近={}（应≈0） 新位置附近={}",
                linePixelsAfter, startSpotAfter, movedSpotAfter);
        LOG.info("排查结论: 起始位置残留={} 全屏像素是否翻倍={}",
                startSpotAfter > 0 ? "有" : "无",
                linePixelsAfter > linePixels * 1.5 ? "是（疑似残留副本）" : "否");
    }

    private int countOutlinePixels() throws Exception {
        return countOutlinePixelsInRegion(0, 0, SIZE, SIZE);
    }

    private int countOutlinePixelsNear(double[] display, int radius) throws Exception {
        return countOutlinePixelsInRegion((int) display[0] - radius, (int) display[1] - radius,
                (int) display[0] + radius, (int) display[1] + radius);
    }

    /**
     * 统计指定显示坐标范围内的轮廓像素（注意 PNG 与显示坐标 y 轴相反）。
     */
    private int countOutlinePixelsInRegion(int x0, int y0, int x1, int y1) throws Exception {
        Path file = Paths.get("target", "drag", "frame.png");
        VtkImageExporter.exportPng(renderWindow, file);
        BufferedImage image = ImageIO.read(new File(file.toString()));
        int count = 0;
        for (int x = Math.max(0, x0); x <= Math.min(image.getWidth() - 1, x1); x++) {
            for (int y = Math.max(0, y0); y <= Math.min(image.getHeight() - 1, y1); y++) {
                int pngY = image.getHeight() - 1 - y;
                if (isOutline(image.getRGB(x, pngY))) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isOutline(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return red > 150 && red - blue > 80 && green < red;
    }

    private void updatePlanes(MprCursorFrame frame) {
        double[] center = frame.center();
        for (int view = 0; view < VIEW_COUNT; view++) {
            double[] normal = frame.axis(view);
            planes[view].SetOrigin(center[0], center[1], center[2]);
            planes[view].SetNormal(normal[0], normal[1], normal[2]);
        }
    }

    private void configureRenderWindow() {
        renderWindow.SetOffScreenRendering(1);
        renderWindow.SetSize(SIZE, SIZE);
        interactor.SetRenderWindow(renderWindow);
        renderers[0].SetViewport(0.0, 0.5, 0.5, 1.0);
        renderers[1].SetViewport(0.0, 0.0, 0.5, 0.5);
        renderers[2].SetViewport(0.5, 0.0, 1.0, 1.0);
        for (vtkRenderer renderer : renderers) {
            renderer.SetBackground(0.0, 0.0, 0.0);
            renderWindow.AddRenderer(renderer);
        }
    }

    private void configureSlices(BuiltVolume built, SeriesInfo series) {
        WindowLevel windowLevel = WindowLevelDefaults.forSeries(series);
        vtkImageInterpolator interpolator = new vtkImageInterpolator();
        interpolator.SetInterpolationModeToLinear();
        for (int view = 0; view < VIEW_COUNT; view++) {
            vtkPlane plane = new vtkPlane();
            vtkImageResliceMapper mapper = new vtkImageResliceMapper();
            mapper.SetInputData(built.getImage());
            mapper.SetSlicePlane(plane);
            mapper.SetSliceFacesCamera(0);
            mapper.SetSliceAtFocalPoint(0);
            mapper.SetJumpToNearestSlice(0);
            mapper.SetSeparateWindowLevelOperation(1);
            mapper.SetAutoAdjustImageQuality(0);
            mapper.SetImageSampleFactor(2);
            mapper.SetResampleToScreenPixels(1);
            mapper.SetInterpolator(interpolator);
            vtkImageSlice slice = new vtkImageSlice();
            slice.SetMapper(mapper);
            slice.GetProperty().SetColorWindow(windowLevel.getWidth());
            slice.GetProperty().SetColorLevel(windowLevel.getCenter());
            renderers[view].AddActor(slice);
            planes[view] = plane;
        }
    }

    private static double[] offset(double[] origin, double[] direction, double distance) {
        return new double[]{
                origin[0] + direction[0] * distance,
                origin[1] + direction[1] * distance,
                origin[2] + direction[2] * distance};
    }

    private static SeriesInfo pickLargestReconstructable(List<SeriesInfo> seriesList) {
        SeriesInfo best = null;
        for (SeriesInfo series : seriesList) {
            SeriesGeometry geometry = series.getGeometry();
            if (geometry.isValid() && (best == null || series.getSliceCount() > best.getSliceCount())) {
                best = series;
            }
        }
        return best;
    }
}
