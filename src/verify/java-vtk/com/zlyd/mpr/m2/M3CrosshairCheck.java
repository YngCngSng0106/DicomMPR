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
import com.zlyd.mpr.geometry.MprCursorFrame;
import com.zlyd.mpr.geometry.MprViewOrientation;
import com.zlyd.mpr.geometry.MprViewRig;
import com.zlyd.mpr.geometry.VolumeBox;
import com.zlyd.mpr.mpr.BuiltVolume;
import com.zlyd.mpr.mpr.MprCameraController;
import com.zlyd.mpr.mpr.MprCrosshairOverlay;
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
 * 十字线可见性校验：旋转后渲染，统计各视口内"十字线配色像素"，确认线既没被图像遮挡、也没被裁剪掉。
 *
 * <p>配色（与 {@code MprCrosshairOverlay.Plane} 一致）：矢状面=蓝、冠状面=绿、轴位面=红；</p>
 * <ul>
 *   <li>轴位视口应同时出现 蓝(矢状痕迹)+绿(冠状痕迹)；</li>
 *   <li>矢状视口：绿+红；冠状视口：蓝+红。</li>
 * </ul>
 */
public final class M3CrosshairCheck {

    private static final Logger LOG = LogManager.getLogger(M3CrosshairCheck.class);
    private static final int VIEW_COUNT = MprViewRig.VIEW_COUNT;
    private static final int SIZE = 400;
    private static final int MIN_PIXELS_PER_LINE = 60;
    /** 配色存在性下限（1 像素线在抗锯齿下会被冲淡，只做弱存在性判断）。 */
    private static final int MIN_COLOR_PIXELS = 8;
    /** 颜色主导性阈值（抗锯齿混合后仍能区分配色）。 */
    private static final int COLOR_TOLERANCE = 30;

    /** 各视口期望出现的配色（RGB 三元组，顺序：矢状面蓝、冠状面绿、轴位面红 中该视图应有的两条）。 */
    private static final int[][][] EXPECTED_COLORS = {
            {{64, 128, 255}, {26, 230, 26}},
            {{26, 230, 26}, {242, 38, 38}},
            {{64, 128, 255}, {242, 38, 38}}};

    static {
        VtkNativeLoader.load();
    }

    private final vtkRenderWindow renderWindow = new vtkRenderWindow();
    private final vtkRenderWindowInteractor interactor = new vtkRenderWindowInteractor();
    private final vtkRenderer[] renderers = {new vtkRenderer(), new vtkRenderer(), new vtkRenderer()};
    private final vtkPlane[] planes = new vtkPlane[VIEW_COUNT];

    private VolumeGeometry geometry;
    private VolumeBox box;
    private final MprViewRig rig = MprViewRig.continuity();
    private final MprCameraController cameraController = new MprCameraController(renderers);
    private MprCrosshairOverlay crosshairs;
    private MprCursorFrame frame;

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Files.createDirectories(Paths.get("target", "crosshair"));
        SeriesInfo target = pickLargestReconstructable(new SeriesGrouper()
                .group(new DicomScanner().scan(root)));
        if (target == null) {
            LOG.warn("没有可重建的序列");
            return;
        }
        BuiltVolume built = new VolumeBuilder().build(target);
        boolean ok = new M3CrosshairCheck().run(built, target);
        LOG.info("十字线可见性结论: {}", ok);
        System.exit(ok ? 0 : 1);
    }

    private boolean run(BuiltVolume built, SeriesInfo series) throws Exception {
        geometry = built.getGeometry();
        box = VolumeBox.of(geometry);
        configureRenderWindow();
        configureSlices(built, series);
        crosshairs = new MprCrosshairOverlay(renderers);

        boolean ok = true;
        ok &= verifyAfterRotation("初始状态（0°）", new int[0]);
        ok &= verifyAfterRotation("转轴位 90°", new int[]{MprViewOrientation.VIEW_AXIAL, 90});
        ok &= verifyAfterRotation("轴位 90° 后转冠状 −60°", new int[]{
                MprViewOrientation.VIEW_AXIAL, 90, MprViewOrientation.VIEW_CORONAL, -60});
        ok &= verifyAfterRotation("再转矢状 120°", new int[]{
                MprViewOrientation.VIEW_AXIAL, 90, MprViewOrientation.VIEW_CORONAL, -60,
                MprViewOrientation.VIEW_SAGITTAL, 120});
        return ok;
    }

    /**
     * 按给定的 (视图, 角度) 序列做拖动式旋转，然后渲染并统计各视口配色像素。
     */
    private boolean verifyAfterRotation(String title, int[] rotations) throws Exception {
        frame = MprCursorFrame.initial(geometry)
                .moveCenter(MprViewOrientation.VIEW_AXIAL, point(0.4, 0.35, 0.5));
        cameraController.reset(frame, box, rig);
        updateScene();
        for (int index = 0; index < rotations.length; index += 2) {
            dragRotate(rotations[index], rotations[index + 1]);
        }
        renderWindow.Render();
        BufferedImage withCrosshair = export(title);

        // 隐藏十字线再渲染一次，用"有/无十字线"的差分统计每条线实际画出的像素（对 1 像素线+抗锯齿也稳定）
        crosshairs.release();
        renderWindow.Render();
        BufferedImage withoutCrosshair = export(title + "_no_crosshair");

        LOG.info("{}:", title);
        boolean ok = true;
        for (int view = 0; view < VIEW_COUNT; view++) {
            int changed = countChanged(withoutCrosshair, withCrosshair, view);
            boolean pass = changed >= MIN_PIXELS_PER_LINE;
            ok &= pass;
            LOG.info("   [{}] 视图{} 十字线像素数（差分）={}", pass ? "PASS" : "FAIL", view, changed);
            for (int[] color : EXPECTED_COLORS[view]) {
                int count = countColor(withCrosshair, withoutCrosshair, view, color);
                boolean colored = count >= MIN_COLOR_PIXELS;
                ok &= colored;
                LOG.info("   [{}] 视图{} 配色 RGB({},{},{}) 像素数={}", colored ? "PASS" : "FAIL", view,
                        color[0], color[1], color[2], count);
            }
        }
        updateScene();
        return ok;
    }

    /**
     * 模拟一次拖动旋转（含取景冻结）。
     */
    private void dragRotate(int view, double degrees) {
        int steps = 20;
        double perStep = degrees / steps;
        cameraController.beginRotation();
        for (int step = 0; step < steps; step++) {
            frame = frame.rotate(view, Math.toRadians(perStep));
            cameraController.captureAnchors(frame, new int[]{view});
            cameraController.configure(frame, box, rig, others(view));
            updateScene();
        }
        cameraController.endRotation();
    }

    private void updateScene() {
        double[] center = frame.center();
        for (int view = 0; view < VIEW_COUNT; view++) {
            double[] normal = frame.axis(view);
            planes[view].SetOrigin(center[0], center[1], center[2]);
            planes[view].SetNormal(normal[0], normal[1], normal[2]);
        }
        crosshairs.update(frame);
        for (vtkRenderer renderer : renderers) {
            renderer.ResetCameraClippingRange();
        }
    }

    /**
     * 统计某视口内符合给定配色的像素数。
     *
     * <p>线宽 1 像素时抗锯齿会把每个像素与背景混合，因此按**颜色主导性**判断，而不是精确比较 RGB：
     * 蓝线要求蓝通道明显高于红/绿；绿线要求绿通道明显高于红/蓝；红线要求红通道明显高于绿/蓝。</p>
     */
    private static int countColor(BufferedImage with, BufferedImage without, int view, int[] color) {
        int[] region = region(with, view);
        int count = 0;
        for (int y = region[2]; y < region[3]; y++) {
            for (int x = region[0]; x < region[1]; x++) {
                int rgb = with.getRGB(x, y);
                int base = without.getRGB(x, y);
                if (rgb == base) {
                    continue;
                }
                if (shiftedToward(color, rgb, base)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 统计某视口内"有/无十字线"两次渲染的差异像素数（即十字线实际覆盖的像素）。
     */
    private static int countChanged(BufferedImage without, BufferedImage with, int view) {
        int[] region = region(with, view);
        int count = 0;
        for (int y = region[2]; y < region[3]; y++) {
            for (int x = region[0]; x < region[1]; x++) {
                if (with.getRGB(x, y) != without.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 该像素是否因画上此配色而"朝该色偏移"。
     *
     * <p>比较"有/无十字线"两次渲染的通道增量（ΔR/ΔG/ΔB）：线宽 1 像素时抗锯齿会与背景混合，
     * 绝对颜色不可靠，但"相对于背景朝线色偏移"始终成立，且与背景亮度无关。</p>
     */
    private static boolean shiftedToward(int[] color, int withRgb, int withoutRgb) {
        int deltaRed = ((withRgb >> 16) & 0xFF) - ((withoutRgb >> 16) & 0xFF);
        int deltaGreen = ((withRgb >> 8) & 0xFF) - ((withoutRgb >> 8) & 0xFF);
        int deltaBlue = (withRgb & 0xFF) - (withoutRgb & 0xFF);
        int dominant = Math.max(color[0], Math.max(color[1], color[2]));
        if (color[2] == dominant) {
            return deltaBlue - deltaRed >= COLOR_TOLERANCE
                    && deltaBlue - deltaGreen >= COLOR_TOLERANCE;
        }
        if (color[1] == dominant) {
            return deltaGreen - deltaRed >= COLOR_TOLERANCE
                    && deltaGreen - deltaBlue >= COLOR_TOLERANCE;
        }
        return deltaRed - deltaGreen >= COLOR_TOLERANCE && deltaRed - deltaBlue >= COLOR_TOLERANCE;
    }

    private static int[] region(BufferedImage image, int view) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (view == 0) {
            return new int[]{0, width / 2, 0, height / 2};
        }
        if (view == 1) {
            return new int[]{0, width / 2, height / 2, height};
        }
        return new int[]{width / 2, width, 0, height};
    }

    private BufferedImage export(String title) throws Exception {
        Path file = Paths.get("target", "crosshair", title.replaceAll("[^0-9A-Za-z\\u4e00-\\u9fa5]", "_") + ".png");
        VtkImageExporter.exportPng(renderWindow, file);
        return ImageIO.read(new File(file.toString()));
    }

    private double[] point(double iFraction, double jFraction, double kFraction) {
        int[] dimensions = geometry.getDimensions();
        return geometry.toWorld(iFraction * (dimensions[0] - 1), jFraction * (dimensions[1] - 1),
                kFraction * (dimensions[2] - 1));
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

    private static int[] others(int view) {
        int[] result = new int[VIEW_COUNT - 1];
        int index = 0;
        for (int candidate = 0; candidate < VIEW_COUNT; candidate++) {
            if (candidate != view) {
                result[index++] = candidate;
            }
        }
        return result;
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
