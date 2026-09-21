package com.zlyd.mpr.mpr;

import java.util.List;

import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.dicom.VolumeGeometry;
import com.zlyd.mpr.geometry.CrosshairSegment;
import com.zlyd.mpr.geometry.Measurement;
import com.zlyd.mpr.geometry.MeasurementType;
import com.zlyd.mpr.geometry.MprCursorFrame;
import com.zlyd.mpr.geometry.MprViewOrientation;
import com.zlyd.mpr.geometry.MprViewRig;
import com.zlyd.mpr.geometry.Vectors;
import com.zlyd.mpr.geometry.VoxelProbe;
import com.zlyd.mpr.geometry.VolumeBox;
import com.zlyd.mpr.util.WindowLevel;
import com.zlyd.mpr.util.WindowLevelDefaults;

import vtk.vtkCamera;
import vtk.vtkCanvas;
import vtk.vtkRenderWindow;
import vtk.vtkRenderWindowInteractor;
import vtk.vtkRenderer;

/**
 * MPR 三视图场景：三个正交视图（轴位/矢状/冠状）+ 光标坐标系（三平面 + 交点 C）状态与操作。
 *
 * <p>职责为"状态 + 编排"：切面显示交给 {@link MprSlicePlaneActors}（支持斜切），相机交给
 * {@link MprCameraController}，十字线绘制交给 {@link MprCrosshairOverlay}，屏幕坐标换算交给
 * {@link MprViewMapper}，几何计算全部由 {@link MprCursorFrame} / {@link VolumeBox} 等纯数学类承担。</p>
 */
final class MprScene {

    static final int AXIAL = 0;
    static final int SAGITTAL = 1;
    static final int CORONAL = 2;

    private static final int VIEW_COUNT = 3;
    private static final double HOLE_HIT_RADIUS_PIXELS = 10.0;
    private static final double SEGMENT_HIT_RADIUS_PIXELS = 6.0;

    private final vtkRenderer[] renderers = new vtkRenderer[VIEW_COUNT];
    private final MprSlicePlaneActors planeActors;
    private final MprCrosshairOverlay crosshairs;
    private final MprCameraController cameraController;
    private final MprViewMapper viewMapper;
    private final VtkOrientationMarkers markers;
    private final MprMeasurementController measurementController;
    private final MprWindowLevelController windowLevelController;
    private final vtkCanvas canvas;

    private VolumeGeometry geometry;
    private MprVolumeProbe volumeProbe;
    private MprCursorFrame frame;
    private VolumeBox box;
    private MprViewRig rig = MprViewRig.continuity();
    private List<CrosshairSegment> crosshairSegments = List.of();
    private boolean volumeReady;

    MprScene(vtkCanvas canvas) {
        this.canvas = canvas;
        renderers[AXIAL] = canvas.GetRenderer();
        renderers[SAGITTAL] = new vtkRenderer();
        renderers[CORONAL] = new vtkRenderer();

        vtkRenderWindow renderWindow = canvas.GetRenderWindow();
        renderWindow.AddRenderer(renderers[SAGITTAL]);
        renderWindow.AddRenderer(renderers[CORONAL]);

        vtkRenderWindowInteractor interactor = canvas.getRenderWindowInteractor();
        configureViewports();
        planeActors = new MprSlicePlaneActors(renderers);
        crosshairs = new MprCrosshairOverlay(renderers);
        cameraController = new MprCameraController(renderers);
        viewMapper = new MprViewMapper(interactor, renderers);
        markers = new VtkOrientationMarkers(renderers);
        MprMeasurementOverlay overlay = new MprMeasurementOverlay(renderers, viewMapper);
        measurementController = new MprMeasurementController(viewMapper, overlay);
        windowLevelController = new MprWindowLevelController(canvas, planeActors.slices());
    }

    private void configureViewports() {
        renderers[AXIAL].SetViewport(0.0, 0.5, 0.5, 1.0);
        renderers[SAGITTAL].SetViewport(0.0, 0.0, 0.5, 0.5);
        renderers[CORONAL].SetViewport(0.5, 0.0, 1.0, 1.0);
        // 视图背景统一纯黑
        for (int view = 0; view < VIEW_COUNT; view++) {
            renderers[view].SetBackground(0.0, 0.0, 0.0);
        }
    }

    void setVolume(BuiltVolume volume, SeriesInfo series) {
        this.geometry = volume.getGeometry();
        this.box = VolumeBox.of(this.geometry);
        this.frame = MprCursorFrame.initial(this.geometry);
        this.volumeProbe = new MprVolumeProbe(this.geometry, volume.getImage());
        windowLevelController.reset(series);
        this.volumeReady = true;
        measurementController.setVolume(this.geometry, this.volumeProbe);

        planeActors.setInput(volume.getImage());
        planeActors.setVisibility(1);
        resetView();
    }

    /**
     * 清理 MPR 缓存：释放体数据引用、隐藏切片/十字线/测量，回到"未载入"状态；之后可重新打开 MPR。
     *
     * <p>切片 mapper 的输入会被切断（`RemoveAllInputs`），配合置空体数据/几何引用，
     * 使大体积体数据（short 数组）尽快被回收；所有交互因 `volumeReady=false` 自动失效。</p>
     */
    void clearVolume() {
        planeActors.release();
        crosshairs.release();
        measurementController.release();
        crosshairSegments = List.of();
        volumeProbe = null;
        geometry = null;
        frame = null;
        box = null;
        volumeReady = false;
        render();
    }

    /**
     * 设置窗宽窗位并联动到三个视图（E1）。
     */
    void setWindowLevel(WindowLevel level) {
        windowLevelController.setWindowLevel(level);
        render();
    }

    WindowLevel getWindowLevel() {
        return windowLevelController.getWindowLevel();
    }

    /**
     * 按鼠标位移增量调节窗宽窗位（右键拖动）。
     */
    void adjustWindowLevel(int dx, int dy) {
        windowLevelController.adjust(dx, dy);
        render();
    }

    /**
     * 光标处探测：返回所在视图、体素索引与 HU 值（D6）。
     *
     * @return 探测结果；不在体数据范围内返回 {@code null}
     */
    VoxelProbe probe(int displayX, int displayY) {
        if (!volumeReady) {
            return null;
        }
        int view = viewMapper.viewAt(displayX, displayY);
        if (view < 0) {
            return null;
        }
        double[] world = viewMapper.displayToWorld(view, displayX, displayY);
        return world == null ? null : volumeProbe.probe(view, world);
    }

    /**
     * 滚轮/上下键翻层：当前平面沿自身法向移动一个体素步长。
     *
     * <p>{@code wheelDirection = +1} 表示"前滚"（滚轮上推、↑键），沿用改造前的手感：
     * 轴位 → 脚→头、冠状 → 后→前、矢状 → 患者左→右。</p>
     */
    void stepActivePlane(int wheelDirection) {
        if (!volumeReady) {
            return;
        }
        int view = viewMapper.activeView();
        if (view < 0) {
            return;
        }
        int axis = MprCursorFrame.planeAxis(view);
        double step = geometry.getSpacing()[axis];
        double halfExtent = box.getHalfExtents()[axis];
        double current = frame.normalOffset(view);
        double target = clamp(current + wheelDirection * sliceDirectionSign(view) * step,
                -halfExtent, halfExtent);
        frame = frame.withOffset(view, target - current);
        cameraController.captureAnchors(frame, MprCameraController.allViews());
        applyFrame();
    }

    /**
     * 各视图"前滚"对应的平面移动方向（与改造前的索引增减一致）。
     *
     * <ul>
     *   <li>轴位：前滚 → 脚→头（+法向）</li>
     *   <li>冠状：前滚 → 后→前（−法向）</li>
     *   <li>矢状：前滚 → 患者左→右（−法向）</li>
     * </ul>
     */
    private int sliceDirectionSign(int view) {
        return view == AXIAL ? +1 : -1;
    }

    /**
     * 将指针位置映射到被拖视图平面内，移动交点 C（该视图自身平面不动）。
     */
    void moveCenter(int displayX, int displayY) {
        if (!volumeReady) {
            return;
        }
        int view = viewMapper.viewAt(displayX, displayY);
        if (view < 0) {
            return;
        }
        double[] world = viewMapper.displayToWorld(view, displayX, displayY);
        if (world == null) {
            return;
        }
        frame = frame.moveCenter(view, world);
        cameraController.captureAnchors(frame, MprCameraController.allViews());
        applyFrame();
    }

    /**
     * 在某个视图内顺时针旋转十字线 {@code radians}（弧度）。
     *
     * <p>被转视图的平面/相机/图像保持冻结，只有十字线在屏幕内转动；另两个视图的相机随平面重排
     * （交点 C 的屏幕位置严格不变）。</p>
     */
    void rotate(int view, double radians) {
        if (!volumeReady || view < 0) {
            return;
        }
        frame = frame.rotate(view, radians);
        cameraController.captureAnchors(frame, new int[]{view});
        cameraController.configure(frame, box, rig, others(view));
        applyFrame();
    }

    /**
     * 开始旋转拖动：冻结各视图取景，旋转中画面尺寸完全不变（方案 B）。
     */
    void beginRotation() {
        cameraController.beginRotation();
    }

    /**
     * 结束旋转拖动：**保持取景冻结**（按用户选择：松手不重新取景、允许裁角）。
     *
     * <p>恢复"装得下"的取景需显式操作：<code>R</code> 重置、<code>A</code> 回正，或改变窗口尺寸。</p>
     */
    void endRotation() {
        cameraController.endRotation();
    }

    /**
     * 回正：把各视图的 up 重排到最贴近解剖习惯的方向（一次性动作）。
     */
    void alignRig() {
        if (!volumeReady) {
            return;
        }
        rig = MprViewRig.aligned(frame);
        // 回正是显式操作：解除取景冻结，让各视图按新朝向重新装下
        cameraController.clearScaleFreeze();
        cameraController.configure(frame, box, rig, MprCameraController.allViews());
        applyFrame();
    }

    void resetView() {
        if (!volumeReady) {
            return;
        }
        frame = MprCursorFrame.initial(geometry);
        rig = MprViewRig.continuity();
        cameraController.reset(frame, box, rig);
        applyFrame();
    }

    void refitCameras() {
        if (!volumeReady) {
            return;
        }
        cameraController.refitVolume(frame, box, MprCameraController.allViews());
        applyFrame();
    }

    /**
     * 当前光标坐标系；**未载入体数据时为 null**（调用方需判空）。
     */
    MprCursorFrame getFrame() {
        return frame;
    }

    MprViewRig getRig() {
        return rig;
    }

    /**
     * 当前三个平面是否（在 1° 容差内）与解剖平面重合（斜切时测量应禁用，D2）。
     */
    boolean isAxisAligned() {
        return frame == null || frame.isAxisAligned(1.0);
    }

    boolean isVolumeReady() {
        return volumeReady;
    }

    void render() {
        measurementController.refresh();
        canvas.Render();
    }

    /**
     * 指针是否落在十字线交点的空白区域（用于判定"拖洞"操作）。
     */
    boolean isOnHole(int displayX, int displayY) {
        if (!volumeReady) {
            return false;
        }
        int view = viewMapper.viewAt(displayX, displayY);
        if (view < 0) {
            return false;
        }
        double[] display = viewMapper.worldToDisplay(view, frame.center());
        double dx = display[0] - displayX;
        double dy = display[1] - displayY;
        return Math.sqrt(dx * dx + dy * dy) <= HOLE_HIT_RADIUS_PIXELS;
    }

    // ---------------------------------------------------------------- 测量（F1/F2，委托给控制器）

    void setMeasurementTool(MeasurementType type) {
        measurementController.setTool(type);
        render();
    }

    MeasurementType getMeasurementTool() {
        return measurementController.getTool();
    }

    Measurement getLastMeasurement() {
        return measurementController.getLastMeasurement();
    }

    List<Measurement> getMeasurements() {
        return measurementController.getMeasurements();
    }

    /**
     * 记录一个测量点（左键落点）。
     */
    void addMeasurementPoint(int displayX, int displayY) {
        int[] center = centerIndex();
        measurementController.addPoint(displayX, displayY, center[0], center[1], center[2]);
        render();
    }

    /**
     * 移动指针时刷新未完成测量的预览。
     */
    void updateMeasurementPreview(int displayX, int displayY) {
        measurementController.updatePreview(displayX, displayY);
        render();
    }

    /**
     * 取消未完成的测量。
     */
    void cancelPending() {
        measurementController.cancel();
        render();
    }

    /**
     * 清除所有测量结果。
     */
    void clearMeasurements() {
        measurementController.clear();
        render();
    }

    /**
     * 应用光标坐标系：切面平面、十字线、方向标记、渲染。
     */
    private void applyFrame() {
        planeActors.update(frame);
        crosshairSegments = crosshairs.update(frame);
        // 十字线会延伸到视口边缘（比体数据更长）：按其包围盒重算近/远裁剪面，否则远端会被裁掉
        for (int view = 0; view < VIEW_COUNT; view++) {
            renderers[view].ResetCameraClippingRange();
        }
        updateOrientationMarkers();
        render();
    }

    /**
     * 指针所在视图（不在任何视图内返回 −1）。
     */
    int activeView() {
        return viewMapper.activeView();
    }

    /**
     * 指定屏幕坐标所在的视图（不在任何视图内返回 −1）。
     */
    int viewAt(int displayX, int displayY) {
        return viewMapper.viewAt(displayX, displayY);
    }

    /**
     * 交点 C 在指定视图的屏幕坐标（旋转角计算用）。
     */
    double[] centerDisplay(int view) {
        return viewMapper.worldToDisplay(view, frame.center());
    }

    /**
     * 指针是否落在十字线的线段上（留洞处不算），用于判定"拖线旋转"。
     */
    boolean isOnCrosshair(int displayX, int displayY) {
        if (!volumeReady) {
            return false;
        }
        int view = viewMapper.viewAt(displayX, displayY);
        if (view < 0) {
            return false;
        }
        double best = Double.MAX_VALUE;
        for (CrosshairSegment segment : crosshairSegments) {
            if (segment.getView() != view || !segment.isVisible()) {
                continue;
            }
            double[] start = viewMapper.worldToDisplay(view, segment.getStart());
            double[] end = viewMapper.worldToDisplay(view, segment.getEnd());
            best = Math.min(best, distanceToSegment(start, end, displayX, displayY));
        }
        return best <= SEGMENT_HIT_RADIUS_PIXELS;
    }

    /**
     * 点到线段的距离（屏幕像素）。
     */
    private static double distanceToSegment(double[] start, double[] end, double x, double y) {
        double dx = end[0] - start[0];
        double dy = end[1] - start[1];
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < 1e-9) {
            return Math.hypot(x - start[0], y - start[1]);
        }
        double projection = ((x - start[0]) * dx + (y - start[1]) * dy) / lengthSquared;
        double clamped = Math.max(0.0, Math.min(1.0, projection));
        return Math.hypot(x - (start[0] + clamped * dx), y - (start[1] + clamped * dy));
    }

    /**
     * 方向标记取各视图相机的**实际** up/right（被冻结的视图也始终与画面一致）。
     */
    private void updateOrientationMarkers() {
        String[][] labels = new String[VIEW_COUNT][];
        for (int view = 0; view < VIEW_COUNT; view++) {
            vtkCamera camera = renderers[view].GetActiveCamera();
            double[] direction = camera.GetDirectionOfProjection();
            double[] up = Vectors.orthogonalize(camera.GetViewUp(), direction);
            labels[view] = MprViewOrientation.edgeLabels(up, Vectors.cross(direction, up));
        }
        markers.update(labels);
    }

    /**
     * 由光标坐标系推导最近的中心索引（轴对齐时语义精确；供 HU 读数与测量过渡使用）。
     */
    private int[] centerIndex() {
        int[] dimensions = geometry.getDimensions();
        double[] spacing = geometry.getSpacing();
        double[] offsets = frame.getOffsets();
        return new int[]{
                indexFromOffset(offsets[MprCursorFrame.AXIS_U], dimensions[0], spacing[0]),
                indexFromOffset(offsets[MprCursorFrame.AXIS_V], dimensions[1], spacing[1]),
                indexFromOffset(offsets[MprCursorFrame.AXIS_W], dimensions[2], spacing[2])};
    }

    private static int indexFromOffset(double offset, int dimension, double spacing) {
        double index = (dimension - 1) / 2.0 + offset / spacing;
        return (int) Math.max(0, Math.min(dimension - 1, Math.round(index)));
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
