package com.zlyd.mpr.m2;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

import vtk.vtkImageData;
import vtk.vtkImageInterpolator;
import vtk.vtkImageResliceMapper;
import vtk.vtkImageSlice;
import vtk.vtkPlane;
import vtk.vtkRenderWindow;
import vtk.vtkRenderWindowInteractor;
import vtk.vtkRenderer;

/**
 * 测量绘制校验：确认**删除测量后图形（轮廓）真的从画面消失**（不只数值标签消失）。
 *
 * <p>做法：离屏渲染 → 统计测量轮廓颜色像素 → 删除该测量 → 再渲染 → 断言轮廓像素归零；
 * 同时校验"全部删除"同样生效。</p>
 */
public final class M3MeasurementDrawCheck {

    private static final Logger LOG = LogManager.getLogger(M3MeasurementDrawCheck.class);
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
    private MprCameraController cameraController;
    private MprMeasurementOverlay overlay;
    private MprViewMapper viewMapper;

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Files.createDirectories(Paths.get("target", "measure"));
        SeriesInfo target = pickLargestReconstructable(new SeriesGrouper()
                .group(new DicomScanner().scan(root)));
        if (target == null) {
            LOG.warn("没有可重建的序列");
            return;
        }
        BuiltVolume built = new VolumeBuilder().build(target);
        boolean ok = new M3MeasurementDrawCheck().run(built, target);
        LOG.info("测量绘制校验结论: {}", ok);
        System.exit(ok ? 0 : 1);
    }

    private boolean run(BuiltVolume built, SeriesInfo series) throws Exception {
        geometry = built.getGeometry();
        box = VolumeBox.of(geometry);
        configureRenderWindow();
        configureSlices(built, series);
        cameraController = new MprCameraController(renderers);
        viewMapper = new MprViewMapper(interactor, renderers);
        overlay = new MprMeasurementOverlay(renderers, viewMapper);

        MprCursorFrame frame = MprCursorFrame.initial(geometry);
        cameraController.reset(frame, box, MprViewRig.continuity());
        updatePlanes(frame);
        renderWindow.Render();
        renderWindow.Render();

        double[] center = frame.center();
        double[] axisX = geometry.getAxisX();
        double[] axisY = geometry.getAxisY();
        double[] start = offset(center, axisX, -60);
        double[] end = offset(center, axisX, 60);
        double[] secondStart = offset(center, axisY, -60);
        double[] secondEnd = offset(center, axisY, 60);

        // 在轴位视图加两条线段测量
        overlay.addMeasurement(new Measurement(MeasurementType.LENGTH, MprViewOrientation.VIEW_AXIAL,
                List.of(start, end), 120.0, null));
        overlay.addMeasurement(new Measurement(MeasurementType.LENGTH, MprViewOrientation.VIEW_AXIAL,
                List.of(secondStart, secondEnd), 120.0, null));
        renderWindow.Render();
        int withTwo = countOutlinePixels();

        // 删除选中的第二条
        overlay.setSelectedIndex(1);
        overlay.removeMeasurement(1);
        renderWindow.Render();
        int withOne = countOutlinePixels();

        // 全部删除
        overlay.clear();
        renderWindow.Render();
        int withNone = countOutlinePixels();

        boolean ok = true;
        ok &= report(withTwo > 200, "两条线段时轮廓像素=%d（应 >200）", withTwo);
        ok &= report(withOne > 80 && withOne < withTwo, "删除选中后轮廓像素=%d（应明显减少且 >0）", withOne);
        ok &= report(withNone == 0, "全部删除后轮廓像素=%d（应为 0）", withNone);

        // 移动图形：移动后"原位置"不得残留（防止原地留副本）
        overlay.clear();
        overlay.addMeasurement(new Measurement(MeasurementType.RECT_ROI, MprViewOrientation.VIEW_AXIAL,
                List.of(start, end), 120.0 * 120.0, null));
        renderWindow.Render();
        int beforeMove = countOutlinePixels();
        // 命中判定：轮廓角点 / 图形内部 / 数值标签附近 / 远处空白
        double[] anchor = {(start[0] + end[0]) / 2.0, (start[1] + end[1]) / 2.0, (start[2] + end[2]) / 2.0};
        double[] displayCorner = viewMapper.worldToDisplay(MprViewOrientation.VIEW_AXIAL, start);
        double[] displayCenter = viewMapper.worldToDisplay(MprViewOrientation.VIEW_AXIAL, anchor);
        int hitOutline = overlay.hitTest(MprViewOrientation.VIEW_AXIAL, (int) displayCorner[0],
                (int) displayCorner[1]);
        int hitInside = overlay.hitTest(MprViewOrientation.VIEW_AXIAL, (int) displayCenter[0],
                (int) displayCenter[1]);
        int hitLabel = overlay.hitTest(MprViewOrientation.VIEW_AXIAL,
                (int) (displayCenter[0] + 12), (int) (displayCenter[1] + 12));
        int hitOutside = overlay.hitTest(MprViewOrientation.VIEW_AXIAL, 5, 395);
        ok &= report(hitOutline == 0, "命中轮廓角点 -> 下标 %d（应为 0）", hitOutline);
        ok &= report(hitInside == 0, "命中图形内部 -> 下标 %d（应为 0）", hitInside);
        ok &= report(hitLabel == 0, "命中数值标签 -> 下标 %d（应为 0）", hitLabel);
        ok &= report(hitOutside < 0, "命中远处空白 -> 下标 %d（应为 -1）", hitOutside);
        double[] centroidBefore = outlineCentroid();
        int oldSpotBefore = countOutlinePixelsNear(centroidBefore, 12);
        double[] shift = {150.0, 0.0, 0.0};
        List<double[]> moved = List.of(add(start, shift), add(end, shift));
        overlay.replaceMeasurement(0, new Measurement(MeasurementType.RECT_ROI,
                MprViewOrientation.VIEW_AXIAL, moved, 120.0 * 120.0, null));
        renderWindow.Render();
        int afterMove = countOutlinePixels();
        double[] centroidAfter = outlineCentroid();
        int oldSpotAfter = countOutlinePixelsNear(centroidBefore, 12);
        ok &= report(beforeMove > 100 && afterMove > 100, "移动前后轮廓像素=%d/%d（应都 >100）",
                beforeMove, afterMove);
        ok &= report(oldSpotBefore > 0 && oldSpotAfter == 0,
                "原位置(质心±12px)轮廓像素=%d→%d（应归零，不残留副本）", oldSpotBefore, oldSpotAfter);
        ok &= report(centroidAfter[0] - centroidBefore[0] > 50,
                "轮廓质心 X %.1f→%.1f（应明显右移，证明图形真的移动）", centroidBefore[0], centroidAfter[0]);
        return ok;
    }

    /**
     * 统计画面**左半部分**（原图形所在区域）的轮廓像素数。
     */
    private int countOutlinePixelsInLeftHalf() throws Exception {
        Path file = Paths.get("target", "measure", "outline.png");
        VtkImageExporter.exportPng(renderWindow, file);
        BufferedImage image = ImageIO.read(new File(file.toString()));
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth() / 2; x++) {
                if (isOutline(image.getRGB(x, y))) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 统计以给定点为中心、半径 radius 的窗口内轮廓像素数。
     */
    private int countOutlinePixelsNear(double[] center, int radius) throws Exception {
        Path file = Paths.get("target", "measure", "outline.png");
        VtkImageExporter.exportPng(renderWindow, file);
        BufferedImage image = ImageIO.read(new File(file.toString()));
        int count = 0;
        for (int y = Math.max(0, (int) center[1] - radius); y <= Math.min(image.getHeight() - 1,
                (int) center[1] + radius); y++) {
            for (int x = Math.max(0, (int) center[0] - radius); x <= Math.min(image.getWidth() - 1,
                    (int) center[0] + radius); x++) {
                if (isOutline(image.getRGB(x, y))) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 轮廓像素的质心（用于确认图形确实移动）。
     */
    private double[] outlineCentroid() throws Exception {
        Path file = Paths.get("target", "measure", "outline.png");
        VtkImageExporter.exportPng(renderWindow, file);
        BufferedImage image = ImageIO.read(new File(file.toString()));
        double sumX = 0;
        double sumY = 0;
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isOutline(image.getRGB(x, y))) {
                    sumX += x;
                    sumY += y;
                    count++;
                }
            }
        }
        return count == 0 ? new double[]{0, 0} : new double[]{sumX / count, sumY / count};
    }

    private static boolean isOutline(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return red > 150 && red - blue > 80 && green < red;
    }

    private static double[] add(double[] point, double[] shift) {
        return new double[]{point[0] + shift[0], point[1] + shift[1], point[2] + shift[2]};
    }

    /**
     * 统计画面中"测量轮廓色"的像素数（红/黄系：R 明显高于 B）。
     */
    private int countOutlinePixels() throws Exception {
        Path file = Paths.get("target", "measure", "outline.png");
        VtkImageExporter.exportPng(renderWindow, file);
        BufferedImage image = ImageIO.read(new File(file.toString()));
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                if (red > 150 && red - blue > 80 && green < red) {
                    count++;
                }
            }
        }
        return count;
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

    private static boolean report(boolean ok, String format, Object... arguments) {
        LOG.info("[{}] {}", ok ? "PASS" : "FAIL", String.format(format, arguments));
        return ok;
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
