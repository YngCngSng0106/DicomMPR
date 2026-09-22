package com.zlyd.mpr.mpr;

import java.util.ArrayList;
import java.util.List;

import com.zlyd.mpr.dicom.VolumeGeometry;
import com.zlyd.mpr.geometry.Measurement;
import com.zlyd.mpr.geometry.MeasurementCalculator;
import com.zlyd.mpr.geometry.MeasurementType;
import com.zlyd.mpr.geometry.RoiStatistics;
import com.zlyd.mpr.geometry.ScreenFrame;

import vtk.vtkRenderer;

/**
 * 测量控制器（F1/F2）：维护当前工具与未完成测量的点，计算数值/HU 统计，并驱动绘制层。
 *
 * <p>平面基准取自各视图**相机**（{@link ScreenFrame} 的右/上方向），因此斜切后矩形/椭圆的
 * 形状与面积、以及 ROI 取样都仍然正确；取样按"平面 mm 坐标 → 世界 → 体素索引 → 三线性取值"进行。</p>
 */
final class MprMeasurementController {

    /** ROI 每轴采样点上限（防止大 ROI 卡顿）。 */
    private static final int MAX_ROI_SAMPLES_PER_AXIS = 256;
    /** 自由形状描画时的最小屏幕间距（像素），用于抽稀点集。 */
    private static final double FREEHAND_MIN_STEP_PIXELS = 2.0;

    private final MprViewMapper viewMapper;
    private final MprMeasurementOverlay overlay;
    private final vtkRenderer[] renderers;
    private final List<double[]> pendingPoints = new ArrayList<>();

    private VolumeGeometry geometry;
    private MprVolumeProbe probe;
    private MeasurementType tool;
    private int pendingView = -1;
    private int movingIndex = -1;
    private double[] movingLastWorld;
    private int lastFreehandX = -1;
    private int lastFreehandY = -1;

    MprMeasurementController(MprViewMapper viewMapper, MprMeasurementOverlay overlay,
                             vtkRenderer[] renderers) {
        this.viewMapper = viewMapper;
        this.overlay = overlay;
        this.renderers = renderers;
    }

    void setVolume(VolumeGeometry geometry, MprVolumeProbe probe) {
        this.geometry = geometry;
        this.probe = probe;
        this.pendingPoints.clear();
        this.pendingView = -1;
        overlay.clear();
    }

    void setTool(MeasurementType type) {
        this.tool = type;
        cancel();
    }

    MeasurementType getTool() {
        return tool;
    }

    boolean isActive() {
        return tool != null;
    }

    List<Measurement> getMeasurements() {
        return overlay.getMeasurements();
    }

    Measurement getLastMeasurement() {
        List<Measurement> all = overlay.getMeasurements();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    Measurement getSelectedMeasurement() {
        int index = overlay.getSelectedIndex();
        List<Measurement> all = overlay.getMeasurements();
        return index < 0 || index >= all.size() ? null : all.get(index);
    }

    /**
     * 记录一个测量点：固定点数工具点满即完成；曲线逐点累加（由 {@link #finishCurve()} 结束）。
     *
     * @return 是否产生了新的测量结果
     */
    boolean addPoint(int displayX, int displayY) {
        if (tool == null || geometry == null || tool.isFreehand()) {
            return false;
        }
        double[] world = pointAt(displayX, displayY);
        if (world == null) {
            return false;
        }
        pendingPoints.add(world);
        if (tool.hasFixedPointCount() && pendingPoints.size() >= tool.getPointCount()) {
            completeMeasurement();
            return true;
        }
        overlay.setPending(tool, pendingView, pendingPoints);
        return false;
    }

    /**
     * 结束曲线（双击 / Enter）：点数 ≥2 则生成测量结果。
     */
    boolean finishCurve() {
        if (tool != MeasurementType.CURVE || pendingPoints.size() < 2) {
            return false;
        }
        completeMeasurement();
        return true;
    }

    /**
     * 自由形状：按下开始描画。
     */
    void beginFreehand(int displayX, int displayY) {
        if (tool != MeasurementType.FREEHAND || geometry == null) {
            return;
        }
        double[] world = pointAt(displayX, displayY);
        if (world == null) {
            return;
        }
        pendingPoints.clear();
        pendingPoints.add(world);
        lastFreehandX = displayX;
        lastFreehandY = displayY;
        overlay.setPending(tool, pendingView, pendingPoints);
    }

    /**
     * 自由形状：拖动追加点（按最小屏幕间距抽稀）。
     */
    void extendFreehand(int displayX, int displayY) {
        if (tool != MeasurementType.FREEHAND || pendingPoints.isEmpty()) {
            return;
        }
        if (Math.hypot(displayX - lastFreehandX, displayY - lastFreehandY)
                < FREEHAND_MIN_STEP_PIXELS) {
            return;
        }
        double[] world = pointAt(displayX, displayY);
        if (world == null) {
            return;
        }
        lastFreehandX = displayX;
        lastFreehandY = displayY;
        pendingPoints.add(world);
        overlay.setPending(tool, pendingView, pendingPoints);
    }

    /**
     * 自由形状：松开闭合；点数 ≥3 才生成测量结果。
     */
    boolean endFreehand() {
        if (tool != MeasurementType.FREEHAND || pendingPoints.size() < 3) {
            cancel();
            return false;
        }
        completeMeasurement();
        return true;
    }

    /**
     * 移动指针时更新未完成测量的预览（最后一点跟随指针）。
     */
    void updatePreview(int displayX, int displayY) {
        if (tool == null || tool.isFreehand() || pendingPoints.isEmpty()) {
            return;
        }
        int view = viewMapper.viewAt(displayX, displayY);
        if (view != pendingView) {
            return;
        }
        double[] world = viewMapper.displayToWorld(view, displayX, displayY);
        if (world == null) {
            return;
        }
        List<double[]> preview = new ArrayList<>(pendingPoints);
        preview.add(world);
        overlay.setPending(tool, pendingView, preview);
    }

    /**
     * 选中指针附近（≤6px）最近的测量；未命中则取消选中。
     */
    void selectAt(int displayX, int displayY) {
        trySelect(displayX, displayY);
    }

    /**
     * 选中指针附近的测量；命中返回 true，未命中则取消选中并返回 false。
     */
    boolean trySelect(int displayX, int displayY) {
        int view = viewMapper.viewAt(displayX, displayY);
        int index = view < 0 ? -1 : overlay.hitTest(view, displayX, displayY);
        overlay.setSelectedIndex(index);
        return index >= 0;
    }

    /**
     * 悬停选中：指针移动到某个图形上时选中它（未命中则取消选中）。
     *
     * @return 选中项是否发生变化（调用方据此决定是否需要重绘）
     */
    boolean hoverSelect(int displayX, int displayY) {
        int view = viewMapper.viewAt(displayX, displayY);
        int index = view < 0 ? -1 : overlay.hitTest(view, displayX, displayY);
        if (index == overlay.getSelectedIndex()) {
            return false;
        }
        overlay.setSelectedIndex(index);
        return true;
    }

    /**
     * 开始拖动移动图形：命中则选中并进入移动状态。
     *
     * @return 是否命中某个图形
     */
    boolean beginMoveShape(int displayX, int displayY) {
        int view = viewMapper.viewAt(displayX, displayY);
        if (view < 0 || geometry == null) {
            return false;
        }
        int index = overlay.hitTest(view, displayX, displayY);
        if (index < 0) {
            return false;
        }
        // 命中已有图形：先取消未完成的测量，避免拖动后残留半成品
        cancel();
        movingIndex = index;
        movingLastWorld = viewMapper.displayToWorld(view, displayX, displayY);
        overlay.setSelectedIndex(index);
        return true;
    }

    /**
     * 拖动移动图形：按世界坐标增量平移点集（数值与 HU 统计在松手时重算）。
     */
    void updateMoveShape(int displayX, int displayY) {
        if (movingIndex < 0 || geometry == null) {
            return;
        }
        int view = viewMapper.viewAt(displayX, displayY);
        if (view < 0) {
            return;
        }
        double[] world = viewMapper.displayToWorld(view, displayX, displayY);
        Measurement current = overlay.measurementAt(movingIndex);
        if (world == null || movingLastWorld == null || current == null) {
            return;
        }
        double[] delta = {
                world[0] - movingLastWorld[0],
                world[1] - movingLastWorld[1],
                world[2] - movingLastWorld[2]};
        List<double[]> moved = new ArrayList<>(current.getPoints().size());
        for (double[] point : current.getPoints()) {
            moved.add(new double[]{point[0] + delta[0], point[1] + delta[1], point[2] + delta[2]});
        }
        overlay.replaceMeasurement(movingIndex, new Measurement(current.getType(), current.getView(),
                moved, current.getValue(), current.getStatistics()));
        movingLastWorld = world;
    }

    /**
     * 结束移动：按新位置重算数值与 HU 统计（长度/角度/面积平移不变，ROI 统计需重算）。
     */
    void endMoveShape() {
        if (movingIndex < 0) {
            return;
        }
        Measurement current = overlay.measurementAt(movingIndex);
        if (current != null) {
            double value = computeValue(current.getType(), current.getView(), current.getPoints());
            RoiStatistics statistics = current.getType().isRoi()
                    ? computeRoiStatistics(current.getType(), current.getView(), current.getPoints())
                    : null;
            overlay.replaceMeasurement(movingIndex, new Measurement(current.getType(),
                    current.getView(), current.getPoints(), value, statistics));
        }
        movingIndex = -1;
        movingLastWorld = null;
    }

    boolean isMovingShape() {
        return movingIndex >= 0;
    }

    /**
     * 删除当前选中的测量。
     *
     * @return 是否删除了测量
     */
    boolean deleteSelected() {
        cancel();
        int index = overlay.getSelectedIndex();
        if (index < 0) {
            return false;
        }
        overlay.removeMeasurement(index);
        return true;
    }

    /**
     * 清空除指定视图以外的所有测量（旋转某视图后，其他视图平面已变，其测量不再有效）。
     */
    void clearOtherViews(int view) {
        overlay.removeMeasurementsExcept(view);
    }

    void cancel() {
        pendingPoints.clear();
        pendingView = -1;
        lastFreehandX = -1;
        lastFreehandY = -1;
        overlay.clearPending();
    }

    void clear() {
        cancel();
        overlay.clear();
    }

    /**
     * 释放体数据引用与全部测量（清理 MPR 缓存用）。
     */
    void release() {
        clear();
        geometry = null;
        probe = null;
    }

    void refresh() {
        overlay.refresh();
    }

    private double[] pointAt(int displayX, int displayY) {
        int view = viewMapper.viewAt(displayX, displayY);
        if (view < 0) {
            return null;
        }
        if (pendingView != view) {
            pendingPoints.clear();
            pendingView = view;
        }
        return viewMapper.displayToWorld(view, displayX, displayY);
    }

    private void completeMeasurement() {
        int view = pendingView;
        double value = computeValue(tool, view, pendingPoints);
        RoiStatistics statistics = tool.isRoi()
                ? computeRoiStatistics(tool, view, pendingPoints)
                : null;
        overlay.addMeasurement(new Measurement(tool, view, new ArrayList<>(pendingPoints), value,
                statistics));
        pendingPoints.clear();
        pendingView = -1;
        lastFreehandX = -1;
        lastFreehandY = -1;
    }

    private double computeValue(MeasurementType type, int view, List<double[]> points) {
        double[] axis1 = VtkScreenFrames.of(renderers[view]).getRight();
        double[] axis2 = VtkScreenFrames.of(renderers[view]).getUp();
        switch (type) {
            case LENGTH:
                return MeasurementCalculator.length(points.get(0), points.get(1));
            case ANGLE:
                return MeasurementCalculator.angle(points.get(0), points.get(1),
                        points.get(2));
            case CURVE:
                return MeasurementCalculator.polylineLength(points);
            case FREEHAND:
                return MeasurementCalculator.polygonArea(points, axis1, axis2);
            case RECT_ROI:
                return MeasurementCalculator.rectangleArea(MeasurementCalculator.extent(
                        points.get(0), points.get(1), axis1, axis2));
            default:
                return MeasurementCalculator.ellipseArea(MeasurementCalculator.extent(
                        points.get(0), points.get(1), axis1, axis2));
        }
    }

    /**
     * ROI 统计：在**平面 mm 坐标**下按形状包含判定采样，再换算到体素做三线性取值（斜切同样正确）。
     */
    private RoiStatistics computeRoiStatistics(MeasurementType type, int view,
                                               List<double[]> points) {
        ScreenFrame screen = VtkScreenFrames.of(renderers[view]);
        double[] origin = points.get(0);
        double[] axis1 = screen.getRight();
        double[] axis2 = screen.getUp();

        double min1 = Double.POSITIVE_INFINITY;
        double max1 = Double.NEGATIVE_INFINITY;
        double min2 = Double.POSITIVE_INFINITY;
        double max2 = Double.NEGATIVE_INFINITY;
        for (double[] point : points) {
            double[] plane = MeasurementCalculator.project(point, origin, axis1, axis2);
            min1 = Math.min(min1, plane[0]);
            max1 = Math.max(max1, plane[0]);
            min2 = Math.min(min2, plane[1]);
            max2 = Math.max(max2, plane[1]);
        }
        double step = samplingStep(min1, max1, min2, max2);
        int capacity = Math.max(1, (int) ((max1 - min1) / step + 2) * (int) ((max2 - min2) / step + 2));
        double[] values = new double[capacity];
        int count = 0;
        for (double s = min1; s <= max1; s += step) {
            for (double t = min2; t <= max2; t += step) {
                if (!contains(type, s, t, origin, axis1, axis2, points, min1, max1, min2, max2)) {
                    continue;
                }
                double[] world = {
                        origin[0] + axis1[0] * s + axis2[0] * t,
                        origin[1] + axis1[1] * s + axis2[1] * t,
                        origin[2] + axis1[2] * s + axis2[2] * t};
                double[] index = geometry.toIndex(world);
                int i = (int) Math.round(index[0]);
                int j = (int) Math.round(index[1]);
                int k = (int) Math.round(index[2]);
                if (probe.isInside(i, j, k)) {
                    values[count++] = probe.sampleTrilinear(index);
                }
            }
        }
        return RoiStatistics.of(values, count);
    }

    /**
     * 形状包含判定（平面 mm 坐标）。
     */
    private boolean contains(MeasurementType type, double s, double t, double[] origin,
                             double[] axis1, double[] axis2, List<double[]> points,
                             double min1, double max1, double min2, double max2) {
        if (type == MeasurementType.FREEHAND) {
            return MeasurementCalculator.polygonContains(s, t, points, axis1, axis2);
        }
        if (type == MeasurementType.ELLIPSE_ROI) {
            return MeasurementCalculator.ellipseContains(s, t, min1, min2, max1, max2);
        }
        return MeasurementCalculator.rectangleContains(s, t, min1, min2, max1, max2);
    }

    /**
     * 采样步长（mm）：取体数据最小间距，并按每轴上限适当放大。
     */
    private double samplingStep(double min1, double max1, double min2, double max2) {
        double[] spacing = geometry.getSpacing();
        double step = Math.min(spacing[0], Math.min(spacing[1], spacing[2]));
        double span = Math.max(max1 - min1, max2 - min2);
        if (span / step > MAX_ROI_SAMPLES_PER_AXIS) {
            step = span / MAX_ROI_SAMPLES_PER_AXIS;
        }
        return Math.max(step, 1e-3);
    }
}
