package com.zlyd.mpr.m2;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
import com.zlyd.mpr.util.VtkNativeLoader;

import vtk.vtkCamera;
import vtk.vtkRenderWindow;
import vtk.vtkRenderWindowInteractor;
import vtk.vtkRenderer;

/**
 * S4 离屏校验：相机"机架 + 交点钉住"。
 *
 * <p>按真实交互顺序**累积**旋转若干次，每次断言：</p>
 * <ol>
 *   <li>交点 C 在另两个视图的**屏幕坐标**严格不变（位移 &lt; 1e-6 像素）；</li>
 *   <li>被转视图的相机完全冻结（位置/焦点/up/缩放都不变）；</li>
 *   <li>三个相机始终正对各自平面（|视线·平面法向| = 1）。</li>
 * </ol>
 */
public final class M3RigCheck {

    private static final Logger LOG = LogManager.getLogger(M3RigCheck.class);
    private static final int VIEW_COUNT = MprViewRig.VIEW_COUNT;
    private static final int SIZE = 320;
    private static final double TOLERANCE_PIXELS = 1e-6;
    private static final double TOLERANCE_VECTOR = 1e-9;

    static {
        VtkNativeLoader.load();
    }

    private final vtkRenderWindow renderWindow = new vtkRenderWindow();
    private final vtkRenderWindowInteractor interactor = new vtkRenderWindowInteractor();
    private final vtkRenderer[] renderers = {new vtkRenderer(), new vtkRenderer(), new vtkRenderer()};

    private MprCursorFrame frame;

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        SeriesInfo target = pickLargestReconstructable(new SeriesGrouper()
                .group(new DicomScanner().scan(root)));
        if (target == null) {
            LOG.warn("没有可重建的序列");
            return;
        }
        BuiltVolume built = new VolumeBuilder().build(target);
        boolean ok = new M3RigCheck().check(built.getGeometry());
        System.exit(ok ? 0 : 1);
    }

    private boolean check(VolumeGeometry geometry) {
        configureRenderWindow();
        VolumeBox box = VolumeBox.of(geometry);
        MprViewRig rig = MprViewRig.continuity();
        MprCameraController controller = new MprCameraController(renderers);

        // 交点移到明显偏心处，才能检验"钉住"效果
        frame = MprCursorFrame.initial(geometry)
                .moveCenter(MprViewOrientation.VIEW_AXIAL, point(geometry, 0.3, 0.25, 0.5));
        controller.reset(frame, box, rig);
        LOG.info("初始 C={} 各视图交点屏幕坐标={}", format(frame.center()), format(screenPoints()));

        int[][] steps = {
                {MprViewOrientation.VIEW_AXIAL, 90},
                {MprViewOrientation.VIEW_SAGITTAL, -37},
                {MprViewOrientation.VIEW_CORONAL, 90},
                {MprViewOrientation.VIEW_SAGITTAL, 45},
                {MprViewOrientation.VIEW_AXIAL, -120}};
        boolean ok = true;
        for (int[] step : steps) {
            ok &= verifyRotation(box, rig, controller, step[0], step[1]);
        }
        ok &= verifyChainedRotationKeepsPreviousViewUpright(box, rig, controller);
        LOG.info("S4 结论: {}", ok);
        return ok;
    }

    /**
     * 链式旋转不歪斜：先转轴位 90°（拖动含冻结），再轻碰冠状位 0.5°，
     * 则**轴位视图的 up 不应发生大幅变化**（修复前会被机架轴带到 L 朝上，歪 90°）。
     */
    private boolean verifyChainedRotationKeepsPreviousViewUpright(VolumeBox box, MprViewRig rig,
                                                                 MprCameraController controller) {
        controller.reset(frame, box, rig);
        dragRotate(box, rig, controller, MprViewOrientation.VIEW_AXIAL, 3.0, 30);
        double[] axialUpBefore = cameraUp(MprViewOrientation.VIEW_AXIAL);
        double[] scalesBefore = scales();
        double[][] centerBefore = screenPoints();

        // 第二次：只碰冠状位 0.5°
        controller.beginRotation();
        frame = frame.rotate(MprViewOrientation.VIEW_CORONAL, Math.toRadians(0.5));
        controller.captureAnchors(frame, new int[]{MprViewOrientation.VIEW_CORONAL});
        controller.configure(frame, box, rig, others(MprViewOrientation.VIEW_CORONAL));
        controller.endRotation();
        renderWindow.Render();

        double[] axialUpAfter = cameraUp(MprViewOrientation.VIEW_AXIAL);
        double upDelta = angleDegrees(axialUpBefore, axialUpAfter);
        double[][] centerAfter = screenPoints();
        double centerDelta = Math.hypot(
                centerAfter[MprViewOrientation.VIEW_AXIAL][0] - centerBefore[MprViewOrientation.VIEW_AXIAL][0],
                centerAfter[MprViewOrientation.VIEW_AXIAL][1] - centerBefore[MprViewOrientation.VIEW_AXIAL][1]);
        double scaleDelta = Math.abs(scales()[MprViewOrientation.VIEW_AXIAL]
                - scalesBefore[MprViewOrientation.VIEW_AXIAL]);
        boolean ok = upDelta <= 2.0 && centerDelta <= TOLERANCE_PIXELS && scaleDelta <= 1e-9;
        LOG.info("链式旋转（轴位 90° 后轻碰冠状 0.5°）：轴位 up 变化 {}°、交点位移 {} px、取景差 {} -> {}",
                round(upDelta), round(centerDelta), round(scaleDelta), ok ? "PASS" : "FAIL 歪斜/跳变");
        return ok;
    }

    /**
     * 模拟一次完整拖动（含取景冻结与松手）。
     */
    private void dragRotate(VolumeBox box, MprViewRig rig, MprCameraController controller, int view,
                            double degreesPerStep, int steps) {
        controller.beginRotation();
        for (int step = 0; step < steps; step++) {
            frame = frame.rotate(view, Math.toRadians(degreesPerStep));
            controller.captureAnchors(frame, new int[]{view});
            controller.configure(frame, box, rig, others(view));
        }
        controller.endRotation();
        renderWindow.Render();
    }

    private double[] cameraUp(int view) {
        return renderers[view].GetActiveCamera().GetViewUp();
    }

    private double[] scales() {
        double[] result = new double[VIEW_COUNT];
        for (int view = 0; view < VIEW_COUNT; view++) {
            result[view] = renderers[view].GetActiveCamera().GetParallelScale();
        }
        return result;
    }

    private static double angleDegrees(double[] a, double[] b) {
        double length = Math.sqrt(dot(a, a)) * Math.sqrt(dot(b, b));
        if (length == 0) {
            return 0;
        }
        double cosine = dot(a, b) / length;
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
    }

    /**
     * 旋转某视图一次并校验三条断言。
     */
    private boolean verifyRotation(VolumeBox box, MprViewRig rig, MprCameraController controller,
                                   int view, double degrees) {
        double[][] before = screenPoints();
        double[][] cameraBefore = snapshotCameras();

        frame = frame.rotate(view, Math.toRadians(degrees));
        controller.captureAnchors(frame, new int[]{view});
        controller.configure(frame, box, rig, others(view));
        renderWindow.Render();

        double[][] after = screenPoints();
        boolean result = true;
        for (int other : others(view)) {
            double dx = after[other][0] - before[other][0];
            double dy = after[other][1] - before[other][1];
            boolean pinned = Math.hypot(dx, dy) <= TOLERANCE_PIXELS;
            result &= pinned;
            LOG.info("视图{} 转 {}° → 视图{} 交点屏幕位移=({}, {}) {}", view, degrees, other,
                    round(dx), round(dy), pinned ? "PASS" : "FAIL");
        }
        boolean frozen = cameraUnchanged(cameraBefore[view], snapshotCameras()[view]);
        result &= frozen;
        LOG.info("视图{} 转 {}° → 被转视图相机冻结 {}", view, degrees, frozen ? "PASS" : "FAIL");

        boolean facing = camerasFacePlanes();
        result &= facing;
        LOG.info("视图{} 转 {}° → 三相机正对各自平面 {}", view, degrees, facing ? "PASS" : "FAIL");
        return result;
    }

    /**
     * 每个相机是否正对所在平面（|视线·平面法向| = 1，且 up 与视线正交）。
     */
    private boolean camerasFacePlanes() {
        for (int view = 0; view < VIEW_COUNT; view++) {
            vtkCamera camera = renderers[view].GetActiveCamera();
            double[] direction = camera.GetDirectionOfProjection();
            double[] normal = frame.axis(view);
            double dot = Math.abs(this.dot(direction, normal));
            if (Math.abs(dot - 1.0) > 1e-6
                    || Math.abs(this.dot(camera.GetViewUp(), direction)) > 1e-6) {
                LOG.info("视图{} 视线与法向不一致: |dot|={}", view, round(dot));
                return false;
            }
        }
        return true;
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

    private double[][] screenPoints() {
        double[][] result = new double[VIEW_COUNT][];
        double[] center = frame.center();
        for (int view = 0; view < VIEW_COUNT; view++) {
            result[view] = display(view, center);
        }
        return result;
    }

    private double[][] snapshotCameras() {
        double[][] result = new double[VIEW_COUNT][];
        for (int view = 0; view < VIEW_COUNT; view++) {
            vtkCamera camera = renderers[view].GetActiveCamera();
            result[view] = new double[]{
                    camera.GetFocalPoint()[0], camera.GetFocalPoint()[1], camera.GetFocalPoint()[2],
                    camera.GetPosition()[0], camera.GetPosition()[1], camera.GetPosition()[2],
                    camera.GetViewUp()[0], camera.GetViewUp()[1], camera.GetViewUp()[2],
                    camera.GetParallelScale()};
        }
        return result;
    }

    private static boolean cameraUnchanged(double[] before, double[] after) {
        for (int index = 0; index < before.length; index++) {
            if (Math.abs(before[index] - after[index]) > TOLERANCE_VECTOR) {
                return false;
            }
        }
        return true;
    }

    private double[] display(int view, double[] world) {
        vtkRenderer renderer = renderers[view];
        renderer.SetWorldPoint(world[0], world[1], world[2], 1.0);
        renderer.WorldToDisplay();
        return renderer.GetDisplayPoint();
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] point(VolumeGeometry geometry, double iFraction, double jFraction,
                                  double kFraction) {
        int[] dimensions = geometry.getDimensions();
        return geometry.toWorld(iFraction * (dimensions[0] - 1), jFraction * (dimensions[1] - 1),
                kFraction * (dimensions[2] - 1));
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

    private static String format(double[] vector) {
        return String.format("(%.3f, %.3f, %.3f)", vector[0], vector[1], vector[2]);
    }

    private String format(double[][] points) {
        StringBuilder builder = new StringBuilder();
        for (double[] p : points) {
            builder.append('[').append(round(p[0])).append(", ").append(round(p[1])).append("] ");
        }
        return builder.toString().trim();
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
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
