package com.zlyd.mpr.mpr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zlyd.mpr.geometry.Measurement;
import com.zlyd.mpr.geometry.MeasurementCalculator;
import com.zlyd.mpr.geometry.MeasurementType;
import com.zlyd.mpr.geometry.ScreenFrame;
import com.zlyd.mpr.geometry.Vectors;
import com.zlyd.mpr.util.MeasurementFormatter;

import vtk.vtkActor;
import vtk.vtkCellArray;
import vtk.vtkIdList;
import vtk.vtkPoints;
import vtk.vtkPolyData;
import vtk.vtkPolyDataMapper;
import vtk.vtkRenderer;
import vtk.vtkTextActor;
import vtk.vtkTextProperty;

/**
 * 测量结果的绘制层：把线段/角度/矩形/椭圆/曲线/自由形状画成折线，并在中点显示数值标签。
 *
 * <p>几何用世界坐标（与缩放无关）；形状的平面基准取自各视图**相机**（{@link ScreenFrame}），
 * 因此斜切后矩形/椭圆的"屏幕对齐"与面积计算依然正确。标签用屏幕坐标，渲染前刷新位置。</p>
 *
 * <p>选中态：被选中的测量用加粗 + 高亮色绘制，并支持按屏幕距离命中判定（供"删除选中"使用）。</p>
 */
public final class MprMeasurementOverlay {

    private static final int VIEW_COUNT = 3;
    private static final int ELLIPSE_SEGMENTS = 64;
    private static final double COLOR_RED = 1.0;
    private static final double COLOR_GREEN = 0.85;
    private static final double COLOR_BLUE = 0.1;
    private static final double PENDING_RED = 0.85;
    private static final double PENDING_GREEN = 0.85;
    private static final double PENDING_BLUE = 0.45;
    private static final double SELECTED_RED = 1.0;
    private static final double SELECTED_GREEN = 0.95;
    private static final double SELECTED_BLUE = 0.2;
    private static final float LINE_WIDTH = 1.0f;
    private static final float SELECTED_LINE_WIDTH = 2.0f;
    private static final int LABEL_FONT_SIZE = 14;
    private static final double LABEL_OFFSET_PIXELS = 12.0;
    private static final double HIT_RADIUS_PIXELS = 10.0;
    /** 数值标签的命中半径（像素）。 */
    private static final double LABEL_HIT_RADIUS_PIXELS = 18.0;

    private final vtkRenderer[] renderers;
    private final MprViewMapper viewMapper;
    private final List<Measurement> measurements = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();

    private MeasurementType pendingType;
    private int pendingView = -1;
    private List<double[]> pendingPoints;
    private int selectedIndex = -1;

    /** 一条测量（或预览）对应的渲染对象。 */
    private static final class Entry {
        private final MeasurementType type;
        private final int view;
        private final int measurementIndex;
        private final List<double[]> outline;
        private final vtkPoints points = new vtkPoints();
        private final vtkCellArray cells = new vtkCellArray();
        private final vtkPolyData polyData = new vtkPolyData();
        private final vtkPolyDataMapper mapper = new vtkPolyDataMapper();
        private final vtkActor actor = new vtkActor();
        private final vtkTextActor label = new vtkTextActor();
        private double[] anchor;

        private Entry(MeasurementType type, int view, int measurementIndex,
                      List<double[]> outline) {
            this.type = type;
            this.view = view;
            this.measurementIndex = measurementIndex;
            this.outline = outline;
        }
    }

    public MprMeasurementOverlay(vtkRenderer[] renderers, MprViewMapper viewMapper) {
        this.renderers = renderers;
        this.viewMapper = viewMapper;
    }

    List<Measurement> getMeasurements() {
        return Collections.unmodifiableList(measurements);
    }

    /**
     * 当前选中的测量下标；未选中返回 −1。
     */
    int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        if (selectedIndex != index) {
            selectedIndex = index;
            rebuild();
        }
    }

    /**
     * 追加一条已完成的测量，并自动选中它（便于立刻核对/删除）。
     */
    public void addMeasurement(Measurement measurement) {
        measurements.add(measurement);
        selectedIndex = measurements.size() - 1;
        rebuild();
    }

    /**
     * 删除指定下标的测量。
     */
    public void removeMeasurement(int index) {
        if (index < 0 || index >= measurements.size()) {
            return;
        }
        measurements.remove(index);
        selectedIndex = -1;
        rebuild();
    }

    public void clear() {
        measurements.clear();
        selectedIndex = -1;
        clearPending();
    }

    /**
     * 按下标取测量；越界返回 {@code null}。
     */
    public Measurement measurementAt(int index) {
        return index < 0 || index >= measurements.size() ? null : measurements.get(index);
    }

    /**
     * 替换指定下标的测量（拖动移动图形时逐帧更新点集）。
     */
    public void replaceMeasurement(int index, Measurement measurement) {
        if (index < 0 || index >= measurements.size()) {
            return;
        }
        measurements.set(index, measurement);
        rebuild();
    }

    /**
     * 清空除指定视图以外的测量（旋转后其他视图平面已变，其测量不再有效）。
     */
    public void removeMeasurementsExcept(int view) {
        measurements.removeIf(measurement -> measurement.getView() != view);
        selectedIndex = -1;
        rebuild();
    }

    void setPending(MeasurementType type, int view, List<double[]> points) {
        this.pendingType = type;
        this.pendingView = view;
        this.pendingPoints = points;
        rebuild();
    }

    void clearPending() {
        pendingType = null;
        pendingView = -1;
        pendingPoints = null;
        rebuild();
    }

    /**
     * 命中判定：返回指针附近（≤6px）最近的测量下标；未命中返回 −1。
     */
    public int hitTest(int view, int displayX, int displayY) {
        if (view < 0) {
            return -1;
        }
        int bestIndex = -1;
        double bestDistance = HIT_RADIUS_PIXELS;
        for (Entry entry : entries) {
            if (entry.view != view || entry.measurementIndex < 0 || entry.outline.size() < 2) {
                continue;
            }
            // 数值标签附近也算命中（用户常直接拖标签）
            if (nearLabel(entry, displayX, displayY)) {
                return entry.measurementIndex;
            }
            // 闭合图形（矩形/椭圆/自由形状）内部也算命中，便于整块拖动
            if (containsPointer(entry, displayX, displayY)) {
                return entry.measurementIndex;
            }
            for (int index = 1; index < entry.outline.size(); index++) {
                double[] start = viewMapper.worldToDisplay(view, entry.outline.get(index - 1));
                double[] end = viewMapper.worldToDisplay(view, entry.outline.get(index));
                double distance = distanceToSegment(start, end, displayX, displayY);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    bestIndex = entry.measurementIndex;
                }
            }
        }
        return bestIndex;
    }

    /**
     * 指针是否落在该测量的数值标签附近（标签画在锚点右下 12px 处）。
     */
    private boolean nearLabel(Entry entry, int displayX, int displayY) {
        if (entry.anchor == null) {
            return false;
        }
        double[] display = viewMapper.worldToDisplay(entry.view, entry.anchor);
        double labelX = display[0] + LABEL_OFFSET_PIXELS;
        double labelY = display[1] + LABEL_OFFSET_PIXELS;
        return Math.abs(displayX - labelX) <= LABEL_HIT_RADIUS_PIXELS
                && Math.abs(displayY - labelY) <= LABEL_HIT_RADIUS_PIXELS;
    }

    /**
     * 指针是否落在闭合图形内部（矩形/椭圆/自由形状；用轮廓多边形判定）。
     */
    private boolean containsPointer(Entry entry, int displayX, int displayY) {
        if (entry.type != MeasurementType.RECT_ROI && entry.type != MeasurementType.ELLIPSE_ROI
                && entry.type != MeasurementType.FREEHAND) {
            return false;
        }
        double[] world = viewMapper.displayToWorld(entry.view, displayX, displayY);
        if (world == null) {
            return false;
        }
        ScreenFrame screen = VtkScreenFrames.of(renderers[entry.view]);
        double[] origin = entry.outline.get(0);
        double[] plane = MeasurementCalculator.project(world, origin, screen.getRight(), screen.getUp());
        return MeasurementCalculator.polygonContains(plane[0], plane[1], entry.outline,
                screen.getRight(), screen.getUp());
    }

    /**
     * 渲染前刷新标签位置（屏幕坐标）。
     */
    public void refresh() {
        for (Entry entry : entries) {
            if (entry.anchor == null) {
                continue;
            }
            double[] display = viewMapper.worldToDisplay(entry.view, entry.anchor);
            entry.label.SetPosition(display[0] + LABEL_OFFSET_PIXELS, display[1] + LABEL_OFFSET_PIXELS);
        }
    }

    private void rebuild() {
        disposeEntries();
        ScreenFrame[] screens = VtkScreenFrames.of(renderers);
        for (int index = 0; index < measurements.size(); index++) {
            Measurement measurement = measurements.get(index);
            addEntry(measurement.getType(), measurement.getView(), measurement.getPoints(),
                    MeasurementFormatter.format(measurement), screens, index, index == selectedIndex, false);
        }
        if (pendingType != null && pendingPoints != null && pendingPoints.size() >= 2) {
            addEntry(pendingType, pendingView, pendingPoints, "", screens, -1, false, true);
        }
    }

    private void addEntry(MeasurementType type, int view, List<double[]> points, String text,
                          ScreenFrame[] screens, int measurementIndex, boolean selected,
                          boolean pending) {
        if (view < 0 || points.size() < 2) {
            return;
        }
        List<double[]> outline = buildOutline(type, view, points, screens[view]);
        if (outline.size() < 2) {
            return;
        }
        Entry entry = new Entry(type, view, measurementIndex, outline);
        entry.points.Reset();
        entry.cells.Reset();
        for (double[] point : outline) {
            entry.points.InsertNextPoint(point[0], point[1], point[2]);
        }
        vtkIdList ids = new vtkIdList();
        ids.SetNumberOfIds(outline.size());
        for (int index = 0; index < outline.size(); index++) {
            ids.SetId(index, index);
        }
        entry.cells.InsertNextCell(ids);
        entry.polyData.SetPoints(entry.points);
        entry.polyData.SetLines(entry.cells);
        entry.mapper.SetInputData(entry.polyData);
        entry.actor.SetMapper(entry.mapper);
        double red = pending ? PENDING_RED : (selected ? SELECTED_RED : COLOR_RED);
        double green = pending ? PENDING_GREEN : (selected ? SELECTED_GREEN : COLOR_GREEN);
        double blue = pending ? PENDING_BLUE : (selected ? SELECTED_BLUE : COLOR_BLUE);
        entry.actor.GetProperty().SetColor(red, green, blue);
        entry.actor.GetProperty().SetLineWidth(selected ? SELECTED_LINE_WIDTH : LINE_WIDTH);
        entry.actor.GetProperty().LightingOff();
        renderers[view].AddActor(entry.actor);

        entry.anchor = centroid(points);
        entry.label.SetInput(text);
        entry.label.SetTextScaleModeToNone();
        vtkTextProperty property = entry.label.GetTextProperty();
        property.SetFontSize(LABEL_FONT_SIZE);
        property.SetColor(red, green, blue);
        property.SetBackgroundColor(0.0, 0.0, 0.0);
        property.SetBackgroundOpacity(0.45);
        // 标签按"显示坐标"定位（vtkActor2D 默认是视口坐标，会把标签顶到视口外）
        entry.label.GetPositionCoordinate().SetCoordinateSystemToDisplay();
        renderers[view].AddActor2D(entry.label);

        entries.add(entry);
    }

    /**
     * 生成测量的绘制折线（世界坐标）。
     */
    private List<double[]> buildOutline(MeasurementType type, int view, List<double[]> points,
                                        ScreenFrame screen) {
        if (type == MeasurementType.FREEHAND) {
            List<double[]> outline = new ArrayList<>(points);
            if (outline.size() >= 3) {
                outline.add(points.get(0));
            }
            return outline;
        }
        if (type == MeasurementType.RECT_ROI || type == MeasurementType.ELLIPSE_ROI) {
            double[] axis1 = screen.getRight();
            double[] axis2 = screen.getUp();
            double[] start = points.get(0);
            double[] end = points.get(1);
            List<double[]> outline = new ArrayList<>();
            if (type == MeasurementType.RECT_ROI) {
                outline.add(start);
                outline.add(offset(start, axis1, end, axis1));
                outline.add(end);
                outline.add(offset(start, axis2, end, axis2));
                outline.add(start);
            } else {
                addEllipse(outline, start, end, axis1, axis2);
            }
            return outline;
        }
        return new ArrayList<>(points);
    }

    private void addEllipse(List<double[]> outline, double[] start, double[] end,
                            double[] axis1, double[] axis2) {
        double[] center = new double[]{
                (start[0] + end[0]) / 2.0, (start[1] + end[1]) / 2.0, (start[2] + end[2]) / 2.0};
        double[] extent = MeasurementCalculator.extent(start, end, axis1, axis2);
        double radius1 = extent[0] / 2.0;
        double radius2 = extent[1] / 2.0;
        for (int index = 0; index <= ELLIPSE_SEGMENTS; index++) {
            double angle = 2.0 * Math.PI * index / ELLIPSE_SEGMENTS;
            double cos = Math.cos(angle) * radius1;
            double sin = Math.sin(angle) * radius2;
            outline.add(new double[]{
                    center[0] + axis1[0] * cos + axis2[0] * sin,
                    center[1] + axis1[1] * cos + axis2[1] * sin,
                    center[2] + axis1[2] * cos + axis2[2] * sin});
        }
    }

    private double[] offset(double[] origin, double[] axis, double[] target, double[] targetAxis) {
        double[] delta = Vectors.subtract(target, origin);
        double projection = Vectors.dot(delta, targetAxis);
        return new double[]{
                origin[0] + axis[0] * projection,
                origin[1] + axis[1] * projection,
                origin[2] + axis[2] * projection};
    }

    private double[] centroid(List<double[]> points) {
        double[] sum = new double[]{0.0, 0.0, 0.0};
        for (double[] point : points) {
            sum[0] += point[0];
            sum[1] += point[1];
            sum[2] += point[2];
        }
        return new double[]{sum[0] / points.size(), sum[1] / points.size(), sum[2] / points.size()};
    }

    /**
     * 点到线段的屏幕距离（像素）。
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
     * 移除全部绘制对象。
     *
     * <p>双保险：先置不可见、再从渲染器移除（`RemoveActor` + `RemoveViewProp`）。
     * 实测在 App 的实时渲染链路上，仅调用移除 API 偶发不生效（表现为"数值标签消失、线段仍在原地"），
     * 置不可见后即便移除失败也不会再画出来。</p>
     *
     * <p>不做显式 {@code Delete()}：那会让渲染器持有悬空引用（同样会残留图形），由 GC 回收即可。</p>
     */
    private void disposeEntries() {
        for (Entry entry : entries) {
            entry.actor.SetVisibility(0);
            entry.label.SetVisibility(0);
            renderers[entry.view].RemoveActor(entry.actor);
            renderers[entry.view].RemoveViewProp(entry.actor);
            renderers[entry.view].RemoveActor2D(entry.label);
        }
        entries.clear();
    }
}
