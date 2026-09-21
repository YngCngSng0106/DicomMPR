package com.zlyd.mpr.mpr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zlyd.mpr.dicom.VolumeGeometry;
import com.zlyd.mpr.geometry.Measurement;
import com.zlyd.mpr.geometry.MeasurementCalculator;
import com.zlyd.mpr.geometry.MeasurementType;
import com.zlyd.mpr.geometry.MprViewOrientation;
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
 * 测量结果的绘制层：把长度/角度/ROI 画成折线，并在中点显示数值标签。
 *
 * <p>几何用世界坐标（与缩放无关）；标签用屏幕坐标，渲染前刷新位置。测量集合变化时整体重建。</p>
 */
public final class MprMeasurementOverlay {

    private static final int VIEW_COUNT = 3;
    private static final int ELLIPSE_SEGMENTS = 64;
    private static final double COLOR_RED = 1.0;
    private static final double COLOR_GREEN = 0.85;
    private static final double COLOR_BLUE = 0.1;
    private static final float LINE_WIDTH = 2.0f;
    private static final int LABEL_FONT_SIZE = 14;
    private static final double LABEL_OFFSET_PIXELS = 12.0;

    private final vtkRenderer[] renderers;
    private final MprViewMapper viewMapper;
    private final List<Measurement> measurements = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();

    private VolumeGeometry geometry;
    private MeasurementType pendingType;
    private int pendingView = -1;
    private List<double[]> pendingPoints;

    /** 一条测量（或预览）对应的渲染对象。 */
    private static final class Entry {
        private final int view;
        private final vtkPoints points = new vtkPoints();
        private final vtkCellArray cells = new vtkCellArray();
        private final vtkPolyData polyData = new vtkPolyData();
        private final vtkPolyDataMapper mapper = new vtkPolyDataMapper();
        private final vtkActor actor = new vtkActor();
        private final vtkTextActor label = new vtkTextActor();
        private double[] anchor;

        private Entry(int view) {
            this.view = view;
        }
    }

    public MprMeasurementOverlay(vtkRenderer[] renderers, MprViewMapper viewMapper) {
        this.renderers = renderers;
        this.viewMapper = viewMapper;
    }

    public void setGeometry(VolumeGeometry geometry) {
        this.geometry = geometry;
    }

    List<Measurement> getMeasurements() {
        return Collections.unmodifiableList(measurements);
    }

    public void addMeasurement(Measurement measurement) {
        measurements.add(measurement);
        rebuild();
    }

    void clear() {
        measurements.clear();
        clearPending();
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
        for (Measurement measurement : measurements) {
            addEntry(measurement.getType(), measurement.getView(), measurement.getPoints(),
                    MeasurementFormatter.format(measurement));
        }
        if (pendingType != null && pendingPoints != null && pendingPoints.size() >= 2) {
            addEntry(pendingType, pendingView, pendingPoints, "");
        }
    }

    private void addEntry(MeasurementType type, int view, List<double[]> points, String text) {
        if (geometry == null || view < 0 || points.size() < 2) {
            return;
        }
        Entry entry = new Entry(view);
        List<double[]> outline = buildOutline(type, view, points);
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
        entry.actor.GetProperty().SetColor(COLOR_RED, COLOR_GREEN, COLOR_BLUE);
        entry.actor.GetProperty().SetLineWidth(LINE_WIDTH);
        entry.actor.GetProperty().LightingOff();
        renderers[view].AddActor(entry.actor);

        entry.anchor = centroid(points);
        entry.label.SetInput(text);
        entry.label.SetTextScaleModeToNone();
        vtkTextProperty property = entry.label.GetTextProperty();
        property.SetFontSize(LABEL_FONT_SIZE);
        property.SetColor(COLOR_RED, COLOR_GREEN, COLOR_BLUE);
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
    private List<double[]> buildOutline(MeasurementType type, int view, List<double[]> points) {
        List<double[]> outline = new ArrayList<>();
        if (type == MeasurementType.RECT_ROI || type == MeasurementType.ELLIPSE_ROI) {
            double[] axis1 = MprViewOrientation.screenRight(geometry, view);
            double[] axis2 = MprViewOrientation.up(geometry, view);
            double[] start = points.get(0);
            double[] end = points.get(1);
            if (type == MeasurementType.RECT_ROI) {
                double[] corner1 = offset(start, axis1, end, axis1);
                double[] corner2 = offset(start, axis2, end, axis2);
                outline.add(start);
                outline.add(corner1);
                outline.add(end);
                outline.add(corner2);
                outline.add(start);
            } else {
                addEllipse(outline, start, end, axis1, axis2);
            }
            return outline;
        }
        outline.addAll(points);
        return outline;
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
        double[] delta = new double[]{
                target[0] - origin[0], target[1] - origin[1], target[2] - origin[2]};
        double projection = delta[0] * targetAxis[0] + delta[1] * targetAxis[1] + delta[2] * targetAxis[2];
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

    private void disposeEntries() {
        for (Entry entry : entries) {
            renderers[entry.view].RemoveViewProp(entry.actor);
            renderers[entry.view].RemoveActor2D(entry.label);
            entry.actor.Delete();
            entry.mapper.Delete();
            entry.polyData.Delete();
            entry.cells.Delete();
            entry.points.Delete();
            entry.label.Delete();
        }
        entries.clear();
    }
}
