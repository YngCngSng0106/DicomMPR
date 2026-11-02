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
 * 冻结性实验：在某个视图内**连续旋转**（模拟真实拖动），比较渲染像素。
 *
 * <p>预期：被转视图的平面与相机都不变 ⇒ 该视图像素**逐点完全一致**（maxDiff = 0）；
 * 另两视图像素必然变化（变成斜切面），仅作参考输出。</p>
 */
public final class M3FreezeCheck {

    private static final Logger LOG = LogManager.getLogger(M3FreezeCheck.class);
    private static final int VIEW_COUNT = MprViewRig.VIEW_COUNT;
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
    private MprViewRig rig = MprViewRig.continuity();
    private MprCursorFrame currentFrame;
    private final MprCameraController cameraController = new MprCameraController(renderers);

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Files.createDirectories(Paths.get("target", "freeze"));
        SeriesInfo target = pickLargestReconstructable(new SeriesGrouper()
                .group(new DicomScanner().scan(root)));
        if (target == null) {
            LOG.warn("没有可重建的序列");
            return;
        }
        BuiltVolume built = new VolumeBuilder().build(target);
        boolean ok = new M3FreezeCheck().run(built, target);
        LOG.info("M3FreezeCheck 退出码: {}", ok ? 0 : 1);
        System.exit(ok ? 0 : 1);
    }

    private boolean run(BuiltVolume built, SeriesInfo series) throws Exception {
        geometry = built.getGeometry();
        box = VolumeBox.of(geometry);
        configureRenderWindow();
        configureSlices(built, series);

        boolean ok = true;
        ok &= simulateDrag(MprViewOrientation.VIEW_AXIAL, 3.0, 30, "拖轴位十字线旋转 90°");
        ok &= simulateDrag(MprViewOrientation.VIEW_SAGITTAL, -2.5, 36, "拖矢状十字线旋转 −90°");
        ok &= simulateDrag(MprViewOrientation.VIEW_CORONAL, 4.0, 22, "拖冠状十字线旋转 88°");
        ok &= verifySecondRotationIsContinuous();
        LOG.info("冻结性结论: {}（被转视图像素应逐点一致）", ok);
        return ok;
    }

    /**
     * 连续两次旋转的连续性实验：先转轴位 60° 并松手（含按需取景），再转矢状位 **0.5°**，
     * 检查另两个视图的相机朝向/取景/交点屏幕位置是否只发生"与 0.5° 相称"的微变（而非跳变）。
     */
    private boolean verifySecondRotationIsContinuous() throws Exception {
        MprCursorFrame frame = MprCursorFrame.initial(geometry)
                .moveCenter(MprViewOrientation.VIEW_AXIAL, point(0.35, 0.3, 0.5));
        start(frame);

        // 第一次旋转：轴位 60°（每步 3°，松手后按需取景）
        cameraController.beginRotation();
        for (int step = 0; step < 20; step++) {
            frame = frame.rotate(MprViewOrientation.VIEW_AXIAL, Math.toRadians(3.0));
            cameraController.captureAnchors(frame, new int[]{MprViewOrientation.VIEW_AXIAL});
            cameraController.configure(frame, box, rig, others(MprViewOrientation.VIEW_AXIAL));
            updatePlanes(frame);
        }
        cameraController.endRotation();
        cameraController.refitVolume(frame, box, MprCameraController.allViews());
        updatePlanes(frame);
        renderWindow.Render();
        currentFrame = frame;

        // 第二次旋转：矢状位仅 0.5°
        double[][] directionBefore = cameraVectors(true);
        double[][] upBefore = cameraVectors(false);
        double[] scalesBefore = scales();
        double[][] centerBefore = centerScreen();
        cameraController.beginRotation();
        frame = frame.rotate(MprViewOrientation.VIEW_SAGITTAL, Math.toRadians(0.5));
        cameraController.captureAnchors(frame, new int[]{MprViewOrientation.VIEW_SAGITTAL});
        cameraController.configure(frame, box, rig, others(MprViewOrientation.VIEW_SAGITTAL));
        updatePlanes(frame);
        renderWindow.Render();
        currentFrame = frame;
        double[][] directionAfter = cameraVectors(true);
        double[][] upAfter = cameraVectors(false);
        double[] scalesAfter = scales();
        double[][] centerAfter = centerScreen();

        LOG.info("第二次旋转 0.5°（另两视图应为微变）：");
        boolean ok = true;
        for (int view = 0; view < VIEW_COUNT; view++) {
            double directionDelta = angleDegrees(directionBefore[view], directionAfter[view]);
            double upDelta = angleDegrees(upBefore[view], upAfter[view]);
            double scaleDelta = Math.abs(scalesAfter[view] - scalesBefore[view]);
            double centerDelta = Math.hypot(centerAfter[view][0] - centerBefore[view][0],
                    centerAfter[view][1] - centerBefore[view][1]);
            boolean small = directionDelta <= 2.0 && upDelta <= 2.0 && scaleDelta <= 1e-9
                    && centerDelta <= 1e-6;
            ok &= small;
            LOG.info("   视图{}（{}）：视线转 {:.2f}° 上方转 {:.2f}° 取景差 {} 交点位移 {} {}",
                    view, view == MprViewOrientation.VIEW_SAGITTAL ? "被转" : "另两视图",
                    round(directionDelta), round(upDelta), round(scaleDelta), round(centerDelta),
                    small ? "PASS" : "FAIL 跳变");
        }
        return ok;
    }

    /**
     * 各视图相机朝向/上方/交点屏幕位置（供连续性实验比较）。
     */
    private double[][] cameraVectors(boolean direction) {
        double[][] result = new double[VIEW_COUNT][];
        for (int view = 0; view < VIEW_COUNT; view++) {
            vtk.vtkCamera camera = renderers[view].GetActiveCamera();
            result[view] = direction ? camera.GetDirectionOfProjection() : camera.GetViewUp();
        }
        return result;
    }

    private double[][] centerScreen() {
        double[][] result = new double[VIEW_COUNT][];
        for (int view = 0; view < VIEW_COUNT; view++) {
            double[] world = frameCenter();
            vtkRenderer renderer = renderers[view];
            renderer.SetWorldPoint(world[0], world[1], world[2], 1.0);
            renderer.WorldToDisplay();
            double[] display = renderer.GetDisplayPoint();
            result[view] = new double[]{display[0], display[1]};
        }
        return result;
    }

    private double[] frameCenter() {
        return currentFrame.center();
    }

    private static double angleDegrees(double[] a, double[] b) {
        double length = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
                * Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
        if (length == 0) {
            return 0;
        }
        double cosine = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / length;
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    /**
     * 连续小步旋转（模拟拖动），比较旋转前后各视口像素。
     */
    private boolean simulateDrag(int view, double degreesPerStep, int steps, String title) throws Exception {
        MprCursorFrame frame = MprCursorFrame.initial(geometry)
                .moveCenter(MprViewOrientation.VIEW_AXIAL, point(0.35, 0.3, 0.5));
        frame = frame.rotate(MprViewOrientation.VIEW_SAGITTAL, Math.toRadians(25.0));
        start(frame);
        BufferedImage before = export("before_" + view + ".png");
        // 自比对照：同一状态连导两次，若像素就不同则说明取帧本身不确定（与冻结性无关）
        BufferedImage repeat = export("repeat_" + view + ".png");
        int[] selfDiff = diff(before, repeat, view);
        LOG.info("{}: 自比对照（同状态导出两次）被转视口 最大灰度差={} 不同像素数={}",
                title, selfDiff[0], selfDiff[1]);
        String planeBefore = describePlane(view);
        String cameraBefore = describeCamera(view);

        cameraController.beginRotation();
        double[] scalesBefore = scales();
        for (int step = 0; step < steps; step++) {
            frame = frame.rotate(view, Math.toRadians(degreesPerStep));
            cameraController.captureAnchors(frame, new int[]{view});
            cameraController.configure(frame, box, rig, others(view));
            updatePlanes(frame);
            renderWindow.Render();
        }
        double[] scalesAfter = scales();
        BufferedImage after = export("after_" + view + ".png");

        LOG.info("{}:", title);
        boolean result = true;
        boolean scaleStable = true;
        for (int candidate = 0; candidate < VIEW_COUNT; candidate++) {
            if (Math.abs(scalesBefore[candidate] - scalesAfter[candidate]) > 1e-9) {
                scaleStable = false;
            }
        }
        result &= report(scaleStable, "拖动中三视图取景冻结（前=%s 后=%s）",
                format(scalesBefore), format(scalesAfter));

        // 松手后按用户选择：继续冻结（允许斜切面四角被裁）
        cameraController.endRotation();
        boolean stillFrozen = true;
        double[] scalesAfterRelease = scales();
        for (int candidate = 0; candidate < VIEW_COUNT; candidate++) {
            if (Math.abs(scalesBefore[candidate] - scalesAfterRelease[candidate]) > 1e-9) {
                stillFrozen = false;
            }
        }
        result &= report(stillFrozen, "松手后仍保持冻结（scale=%s）", format(scalesAfterRelease));

        // 显式重新取景（等价于 R / A / 改变窗口尺寸）才会更新取景
        cameraController.refitVolume(frame, box, MprCameraController.allViews());
        updatePlanes(frame);
        renderWindow.Render();
        LOG.info("   显式重新取景后 scale={}", format(scales()));
        LOG.info("   视口内容 前={}", describeViewports(before));
        LOG.info("   视口内容 后={}", describeViewports(after));
        LOG.info("   被转视图平面 前={}", planeBefore);
        LOG.info("   被转视图平面 后={}", describePlane(view));
        LOG.info("   被转视图相机 前={}", cameraBefore);
        LOG.info("   被转视图相机 后={}", describeCamera(view));
        for (int candidate = 0; candidate < VIEW_COUNT; candidate++) {
            int[] diff = diff(before, after, candidate);
            boolean frozen = candidate == view;
            if (frozen) {
                result &= diff[0] == 0;
            }
            LOG.info("   视图{} {}：最大灰度差={} 不同像素数={} {}", candidate,
                    frozen ? "(被转)" : "(另两视图)", diff[0], diff[1],
                    frozen ? (diff[0] == 0 ? "PASS 保持不变" : "FAIL 竟然变了") : "（变化属预期）");
        }
        return result;
    }

    /**
     * 被转视图的平面（origin + normal）描述，用于确认平面未被改动。
     */
    private String describePlane(int view) {
        double[] origin = planes[view].GetOrigin();
        double[] normal = planes[view].GetNormal();
        return String.format("origin=(%.6f, %.6f, %.6f) normal=(%.6f, %.6f, %.6f)",
                origin[0], origin[1], origin[2], normal[0], normal[1], normal[2]);
    }

    /**
     * 被转视图相机描述，用于确认相机未被改动。
     */
    private String describeCamera(int view) {
        vtk.vtkCamera camera = renderers[view].GetActiveCamera();
        double[] focal = camera.GetFocalPoint();
        double[] position = camera.GetPosition();
        double[] up = camera.GetViewUp();
        return String.format("focal=(%.4f, %.4f, %.4f) position=(%.4f, %.4f, %.4f) "
                        + "up=(%.4f, %.4f, %.4f) scale=%.6f near=%.4f far=%.4f",
                focal[0], focal[1], focal[2], position[0], position[1], position[2],
                up[0], up[1], up[2], camera.GetParallelScale(),
                camera.GetClippingRange()[0], camera.GetClippingRange()[1]);
    }

    private double[] scales() {
        double[] result = new double[VIEW_COUNT];
        for (int view = 0; view < VIEW_COUNT; view++) {
            result[view] = renderers[view].GetActiveCamera().GetParallelScale();
        }
        return result;
    }

    private static String format(double[] values) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(String.format("%.3f", values[index]));
        }
        return builder.append(']').toString();
    }

    private static boolean report(boolean ok, String format, Object... arguments) {
        LOG.info("   [{}] {}", ok ? "PASS" : "FAIL", String.format(format, arguments));
        return ok;
    }

    private void start(MprCursorFrame frame) {
        currentFrame = frame;
        cameraController.reset(frame, box, rig);
        updatePlanes(frame);
        renderWindow.Render();
    }

    /**
     * 比较两个渲染结果在某视图视口内的差异，返回 {最大灰度差, 不同像素数}。
     */
    private static int[] diff(BufferedImage before, BufferedImage after, int view) {
        int[] region = region(before, view);
        int x0 = region[0];
        int x1 = region[1];
        int y0 = region[2];
        int y1 = region[3];
        int maxDiff = 0;
        int changed = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int difference = Math.abs(gray(before, x, y) - gray(after, x, y));
                if (difference > 0) {
                    changed++;
                    maxDiff = Math.max(maxDiff, difference);
                }
            }
        }
        return new int[]{maxDiff, changed};
    }

    private BufferedImage export(String name) throws Exception {
        Path file = Paths.get("target", "freeze", name);
        VtkImageExporter.exportPng(renderWindow, file);
        return ImageIO.read(new File(file.toString()));
    }

    /**
     * 视口在 PNG（左上为原点）中的像素区域：轴位=左上、矢状=左下、冠状=右侧整栏。
     */
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

    /**
     * 各视口的"非黑像素数/平均灰度"，用于判断导出取帧是否漏掉了某个渲染器。
     */
    private static String describeViewports(BufferedImage image) {
        StringBuilder builder = new StringBuilder();
        for (int view = 0; view < VIEW_COUNT; view++) {
            int[] region = region(image, view);
            int x0 = region[0];
            int x1 = region[1];
            int y0 = region[2];
            int y1 = region[3];
            long lit = 0;
            long total = 0;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int value = gray(image, x, y);
                    total += value;
                    if (value > 60) {
                        lit++;
                    }
                }
            }
            builder.append(String.format("[视图%d 亮像素=%d 均值=%.1f] ", view, lit,
                    (double) total / ((long) (x1 - x0) * (y1 - y0))));
        }
        return builder.toString().trim();
    }

    private static int gray(BufferedImage image, int x, int y) {
        int rgb = image.getRGB(x, y);
        return ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
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
            renderer.SetBackground(0.05, 0.05, 0.05);
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

    private void updatePlanes(MprCursorFrame frame) {
        double[] center = frame.center();
        for (int view = 0; view < VIEW_COUNT; view++) {
            double[] normal = frame.axis(view);
            planes[view].SetOrigin(center[0], center[1], center[2]);
            planes[view].SetNormal(normal[0], normal[1], normal[2]);
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
