package com.zlyd.mpr.m2;

import java.nio.file.Files;
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
import com.zlyd.mpr.geometry.CrosshairGeometry;
import com.zlyd.mpr.geometry.CrosshairSegment;
import com.zlyd.mpr.geometry.MprCursorFrame;
import com.zlyd.mpr.geometry.MprViewOrientation;
import com.zlyd.mpr.geometry.MprViewRig;
import com.zlyd.mpr.geometry.ScreenFrame;
import com.zlyd.mpr.geometry.Vectors;
import com.zlyd.mpr.geometry.VolumeBox;
import com.zlyd.mpr.mpr.BuiltVolume;
import com.zlyd.mpr.mpr.MprCameraController;
import com.zlyd.mpr.mpr.MprCrosshairOverlay;
import com.zlyd.mpr.mpr.VolumeBuilder;
import com.zlyd.mpr.util.VtkImageExporter;
import com.zlyd.mpr.util.VtkNativeLoader;
import com.zlyd.mpr.util.WindowLevel;
import com.zlyd.mpr.util.WindowLevelDefaults;

import vtk.vtkCamera;
import vtk.vtkImageInterpolator;
import vtk.vtkImageResliceMapper;
import vtk.vtkImageSlice;
import vtk.vtkPlane;
import vtk.vtkRenderWindow;
import vtk.vtkRenderWindowInteractor;
import vtk.vtkRenderer;

/**
 * S8 离屏验收：斜切行为对照 {@code MPR斜切设计.md} §4 的三张表逐条断言。
 *
 * <p>每个用例（在轴位/矢状/冠状各转 90°）：</p>
 * <ol>
 *   <li>另两个视图**显示的平面法向**符合三表；</li>
 *   <li>另两个视图**视线/上方/右方**符合三表（含"冠状视口变头→脚、A 在下、L 在右"等）；</li>
 *   <li>交点 C 在另两个视图的**屏幕坐标不变**；</li>
 *   <li>另两个视图的**十字线方向平行屏幕轴**（仍是"＋"）；</li>
 *   <li>被转视图的相机完全冻结、平面不变，且其十字线在屏内恰好转 90°；</li>
 *   <li>导出 PNG 供目视。</li>
 * </ol>
 */
public final class M3ResliceCheck {

    private static final Logger LOG = LogManager.getLogger(M3ResliceCheck.class);
    private static final int VIEW_COUNT = MprViewRig.VIEW_COUNT;
    private static final int SIZE = 400;
    private static final double TOLERANCE = 1e-6;

    static {
        VtkNativeLoader.load();
    }

    private final vtkRenderWindow renderWindow = new vtkRenderWindow();
    private final vtkRenderWindowInteractor interactor = new vtkRenderWindowInteractor();
    private final vtkRenderer[] renderers = {new vtkRenderer(), new vtkRenderer(), new vtkRenderer()};
    private final vtkPlane[] planes = new vtkPlane[VIEW_COUNT];
    private final vtkImageResliceMapper[] mappers = new vtkImageResliceMapper[VIEW_COUNT];
    private final vtkImageSlice[] slices = new vtkImageSlice[VIEW_COUNT];

    private VolumeGeometry geometry;
    private VolumeBox box;
    private MprViewRig rig = MprViewRig.continuity();
    private final MprCameraController cameraController = new MprCameraController(renderers);

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Path outputDir = Paths.get("target", "reslice");
        Files.createDirectories(outputDir);
        SeriesInfo target = pickLargestReconstructable(new SeriesGrouper()
                .group(new DicomScanner().scan(root)));
        if (target == null) {
            LOG.warn("没有可重建的序列");
            return;
        }
        BuiltVolume built = new VolumeBuilder().build(target);
        boolean ok = new M3ResliceCheck().run(built, target, outputDir);
        System.exit(ok ? 0 : 1);
    }

    private boolean run(BuiltVolume built, SeriesInfo series, Path outputDir) throws Exception {
        geometry = built.getGeometry();
        box = VolumeBox.of(geometry);
        configureRenderWindow();
        configureSlices(built, series);
        new MprCrosshairOverlay(renderers);

        boolean ok = true;
        ok &= verifyAxialNinetyDegrees();
        ok &= verifySagittalNinetyDegrees();
        ok &= verifyCoronalNinetyDegrees();
        LOG.info("S8 结论: {}", ok);
        return ok;
    }

    /**
     * §4-A：轴位视图顺时针 90°。
     */
    private boolean verifyAxialNinetyDegrees() throws Exception {
        MprCursorFrame initial = startFrame();
        MprCursorFrame rotated = initial.rotate(MprViewOrientation.VIEW_AXIAL, Math.toRadians(90.0));
        boolean ok = verifyRotation(MprViewOrientation.VIEW_AXIAL, initial, rotated);
        ok &= expectPlane(rotated, MprViewOrientation.VIEW_AXIAL, new double[]{0, 0, 1}, "轴位面不变");
        ok &= expectPlane(rotated, MprViewOrientation.VIEW_SAGITTAL, new double[]{0, 1, 0}, "矢状平面→冠状面");
        ok &= expectPlane(rotated, MprViewOrientation.VIEW_CORONAL, new double[]{1, 0, 0}, "冠状平面→矢状面");
        ok &= expectCamera(rotated, MprViewOrientation.VIEW_SAGITTAL, new double[]{0, -1, 0},
                new double[]{0, 0, 1}, "矢状视口：后→前、H 在上");
        ok &= expectCamera(rotated, MprViewOrientation.VIEW_CORONAL, new double[]{-1, 0, 0},
                new double[]{0, 0, 1}, "冠状视口：左→右、H 在上（标准矢状位）");
        return ok;
    }

    /**
     * §4-B：矢状视图顺时针 90°（用户预期：冠状视口变"头→脚、A 在下、L 在右"）。
     */
    private boolean verifySagittalNinetyDegrees() throws Exception {
        MprCursorFrame initial = startFrame();
        MprCursorFrame rotated = initial.rotate(MprViewOrientation.VIEW_SAGITTAL, Math.toRadians(90.0));
        boolean ok = verifyRotation(MprViewOrientation.VIEW_SAGITTAL, initial, rotated);
        ok &= expectPlane(rotated, MprViewOrientation.VIEW_SAGITTAL, new double[]{1, 0, 0}, "矢状面不变");
        ok &= expectPlane(rotated, MprViewOrientation.VIEW_CORONAL, new double[]{0, 0, 1}, "冠状平面→轴位面");
        ok &= expectPlane(rotated, MprViewOrientation.VIEW_AXIAL, new double[]{0, 1, 0}, "轴位平面→冠状面");
        ok &= expectCamera(rotated, MprViewOrientation.VIEW_CORONAL, new double[]{0, 0, -1},
                new double[]{0, 1, 0}, "冠状视口：头→脚、P 在上(A 在下)");
        ok &= expectCamera(rotated, MprViewOrientation.VIEW_AXIAL, new double[]{0, 1, 0},
                new double[]{0, 0, 1}, "轴位视口：前→后、H 在上（标准冠状位）");
        return ok;
    }

    /**
     * §4-C：冠状视图顺时针 90°。
     */
    private boolean verifyCoronalNinetyDegrees() throws Exception {
        MprCursorFrame initial = startFrame();
        MprCursorFrame rotated = initial.rotate(MprViewOrientation.VIEW_CORONAL, Math.toRadians(90.0));
        boolean ok = verifyRotation(MprViewOrientation.VIEW_CORONAL, initial, rotated);
        ok &= expectPlane(rotated, MprViewOrientation.VIEW_CORONAL, new double[]{0, 1, 0}, "冠状面不变");
        ok &= expectPlane(rotated, MprViewOrientation.VIEW_AXIAL, new double[]{1, 0, 0}, "轴位平面→矢状面");
        ok &= expectPlane(rotated, MprViewOrientation.VIEW_SAGITTAL, new double[]{0, 0, 1}, "矢状平面→轴位面");
        ok &= expectCamera(rotated, MprViewOrientation.VIEW_AXIAL, new double[]{1, 0, 0},
                new double[]{0, -1, 0}, "轴位视口：右→左、A 在上");
        return ok;
    }

    /**
     * 通用断言：交点钉住、十字线屏幕对齐、被转视图冻结且十字线转 90°。
     */
    private boolean verifyRotation(int view, MprCursorFrame initial, MprCursorFrame rotated) throws Exception {
        cameraController.reset(initial, box, rig);
        List<CrosshairSegment> before = crosshairs(initial);
        double[][] centerBefore = screenPoints(initial);
        double[][] camerasBefore = snapshotCameras();
        double beforeAngle = screenAngle(before, view);

        cameraController.captureAnchors(rotated, new int[]{view});
        cameraController.configure(rotated, box, rig, others(view));
        updatePlanes(rotated);
        renderWindow.Render();
        List<CrosshairSegment> after = crosshairs(rotated);
        double[][] centerAfter = screenPoints(rotated);

        boolean ok = true;
        for (int other : others(view)) {
            double displacement = Math.hypot(centerAfter[other][0] - centerBefore[other][0],
                    centerAfter[other][1] - centerBefore[other][1]);
            ok &= report(displacement <= TOLERANCE, "视图%d 转 90° → 视图%d 交点屏幕位移 %.6f px",
                    view, other, displacement);
            ok &= report(crosshairAligned(rotated, after, other),
                    "视图%d 转 90° → 视图%d 十字线平行屏幕轴", view, other);
        }
        ok &= report(cameraUnchanged(camerasBefore[view], snapshotCameras()[view]),
                "视图%d 转 90° → 被转视图相机冻结", view);
        double deltaAngle = angleDelta(beforeAngle, screenAngle(after, view));
        ok &= report(Math.abs(deltaAngle - 90.0) <= 0.5,
                "视图%d 转 90° → 该视图十字线屏内转了 %.2f°", view, deltaAngle);
        VtkImageExporter.exportPng(renderWindow,
                Paths.get("target", "reslice", "s8_view" + view + "_90.png"));
        return ok;
    }

    /**
     * 某视图的十字线方向是否平行于该视图屏幕的右/上方向（"＋"）。
     */
    private boolean crosshairAligned(MprCursorFrame frame, List<CrosshairSegment> segments, int view) {
        vtkCamera camera = renderers[view].GetActiveCamera();
        double[] direction = camera.GetDirectionOfProjection();
        double[] up = Vectors.orthogonalize(camera.GetViewUp(), direction);
        double[] right = Vectors.cross(direction, up);
        for (CrosshairSegment segment : segments) {
            if (segment.getView() != view || !segment.isVisible()) {
                continue;
            }
            double[] line = Vectors.normalize(Vectors.subtract(segment.getEnd(), segment.getStart()));
            boolean aligned = Math.abs(Math.abs(Vectors.dot(line, right)) - 1.0) <= TOLERANCE
                    || Math.abs(Math.abs(Vectors.dot(line, up)) - 1.0) <= TOLERANCE;
            if (!aligned) {
                return false;
            }
        }
        return true;
    }

    private boolean expectPlane(MprCursorFrame frame, int view, double[] axis, String title) {
        double[] normal = frame.axis(view);
        boolean parallel = Math.abs(Math.abs(Vectors.dot(normal, axis)) - 1.0) <= TOLERANCE;
        return report(parallel, "%s（视图%d 平面法向 %s）", title, view, format(normal));
    }

    private boolean expectCamera(MprCursorFrame frame, int view, double[] direction, double[] up,
                                 String title) {
        vtkCamera camera = renderers[view].GetActiveCamera();
        boolean ok = parallel(camera.GetDirectionOfProjection(), direction)
                && parallel(camera.GetViewUp(), up);
        return report(ok, "%s（视线 %s、上方 %s）", title,
                format(camera.GetDirectionOfProjection()), format(camera.GetViewUp()));
    }

    private MprCursorFrame startFrame() {
        MprCursorFrame frame = MprCursorFrame.initial(geometry)
                .moveCenter(MprViewOrientation.VIEW_AXIAL, point(0.35, 0.3, 0.5));
        updatePlanes(frame);
        return frame;
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
            mappers[view] = mapper;
            slices[view] = slice;
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

    private double[] gaps() {
        return new double[]{0.5, 0.5, 0.5};
    }

    /**
     * 按各视图相机实际参数构造屏幕参考系后计算十字线（与 App 行为一致）。
     */
    private List<CrosshairSegment> crosshairs(MprCursorFrame frame) {
        ScreenFrame[] screens = new ScreenFrame[VIEW_COUNT];
        for (int view = 0; view < VIEW_COUNT; view++) {
            vtkRenderer renderer = renderers[view];
            vtkCamera camera = renderer.GetActiveCamera();
            int[] size = renderer.GetSize();
            double aspect = size[1] > 1 ? (double) size[0] / size[1] : 1.0;
            double scale = Math.max(camera.GetParallelScale(), 1e-6);
            double[] direction = camera.GetDirectionOfProjection();
            double[] up = Vectors.orthogonalize(camera.GetViewUp(), direction);
            screens[view] = new ScreenFrame(camera.GetFocalPoint(), Vectors.cross(direction, up), up,
                    direction, scale * aspect, scale);
        }
        return CrosshairGeometry.compute(frame, screens, gaps());
    }

    private double[][] screenPoints(MprCursorFrame frame) {
        double[][] result = new double[VIEW_COUNT][];
        double[] center = frame.center();
        for (int view = 0; view < VIEW_COUNT; view++) {
            vtkRenderer renderer = renderers[view];
            renderer.SetWorldPoint(center[0], center[1], center[2], 1.0);
            renderer.WorldToDisplay();
            result[view] = renderer.GetDisplayPoint();
        }
        return result;
    }

    /**
     * 某视图十字线的屏幕倾角（度，0~180）。
     */
    private double screenAngle(List<CrosshairSegment> segments, int view) {
        for (CrosshairSegment segment : segments) {
            if (segment.getView() == view && segment.isVisible()) {
                double[] start = display(view, segment.getStart());
                double[] end = display(view, segment.getEnd());
                double angle = Math.toDegrees(Math.atan2(end[1] - start[1], end[0] - start[0]));
                return ((angle % 180.0) + 180.0) % 180.0;
            }
        }
        return 0.0;
    }

    private double[] display(int view, double[] world) {
        vtkRenderer renderer = renderers[view];
        renderer.SetWorldPoint(world[0], world[1], world[2], 1.0);
        renderer.WorldToDisplay();
        return renderer.GetDisplayPoint();
    }

    private double[][] snapshotCameras() {
        double[][] result = new double[VIEW_COUNT][];
        for (int view = 0; view < VIEW_COUNT; view++) {
            vtkCamera camera = renderers[view].GetActiveCamera();
            double[] focal = camera.GetFocalPoint();
            double[] position = camera.GetPosition();
            double[] up = camera.GetViewUp();
            result[view] = new double[]{focal[0], focal[1], focal[2], position[0], position[1],
                    position[2], up[0], up[1], up[2], camera.GetParallelScale()};
        }
        return result;
    }

    private static boolean cameraUnchanged(double[] before, double[] after) {
        for (int index = 0; index < before.length; index++) {
            if (Math.abs(before[index] - after[index]) > TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private static double angleDelta(double from, double to) {
        double delta = ((to - from) % 180.0 + 180.0) % 180.0;
        return delta > 90.0 ? 180.0 - delta : delta;
    }

    private static boolean parallel(double[] actual, double[] expected) {
        return Math.abs(Math.abs(Vectors.dot(Vectors.normalize(actual), expected)) - 1.0) <= TOLERANCE;
    }

    private static boolean report(boolean ok, String format, Object... arguments) {
        LOG.info("[{}] {}", ok ? "PASS" : "FAIL", String.format(format, arguments));
        return ok;
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
