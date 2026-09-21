package com.zlyd.mpr.mpr;

import java.util.ArrayList;
import java.util.List;

import com.zlyd.mpr.dicom.VolumeGeometry;
import com.zlyd.mpr.geometry.Measurement;
import com.zlyd.mpr.geometry.MeasurementCalculator;
import com.zlyd.mpr.geometry.MeasurementType;
import com.zlyd.mpr.geometry.MprViewOrientation;
import com.zlyd.mpr.geometry.RoiStatistics;

/**
 * 测量控制器（F1/F2）：维护当前工具与未完成测量的点，计算数值/HU 统计，并驱动绘制层。
 *
 * <p>与界面解耦：坐标换算用 {@link MprViewMapper}，绘制用 {@link MprMeasurementOverlay}，
 * 体素取值用 {@link MprVolumeProbe}；调用方传入当前中心索引以确定所在层。</p>
 */
final class MprMeasurementController {

    private final MprViewMapper viewMapper;
    private final MprMeasurementOverlay overlay;
    private final List<double[]> pendingPoints = new ArrayList<>();

    private VolumeGeometry geometry;
    private MprVolumeProbe probe;
    private MeasurementType tool;
    private int pendingView = -1;

    MprMeasurementController(MprViewMapper viewMapper, MprMeasurementOverlay overlay) {
        this.viewMapper = viewMapper;
        this.overlay = overlay;
    }

    void setVolume(VolumeGeometry geometry, MprVolumeProbe probe) {
        this.geometry = geometry;
        this.probe = probe;
        this.pendingPoints.clear();
        this.pendingView = -1;
        overlay.setGeometry(geometry);
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

    /**
     * 记录一个测量点；点数满足后生成测量结果。
     *
     * @return 是否产生了新的测量结果
     */
    boolean addPoint(int displayX, int displayY, int centerI, int centerJ, int centerK) {
        if (tool == null || geometry == null) {
            return false;
        }
        int view = viewMapper.viewAt(displayX, displayY);
        if (view < 0) {
            return false;
        }
        double[] world = viewMapper.displayToWorld(view, displayX, displayY);
        if (world == null) {
            return false;
        }
        if (pendingView != view) {
            pendingPoints.clear();
            pendingView = view;
        }
        pendingPoints.add(world);
        if (pendingPoints.size() >= tool.getPointCount()) {
            completeMeasurement(centerI, centerJ, centerK);
            return true;
        }
        overlay.setPending(tool, pendingView, pendingPoints);
        return false;
    }

    /**
     * 移动指针时更新未完成测量的预览（最后一点跟随指针）。
     */
    void updatePreview(int displayX, int displayY) {
        if (tool == null || pendingPoints.isEmpty()) {
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

    void cancel() {
        pendingPoints.clear();
        pendingView = -1;
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

    private void completeMeasurement(int centerI, int centerJ, int centerK) {
        int view = pendingView;
        double value = computeValue(view);
        RoiStatistics statistics = tool.isRoi()
                ? computeRoiStatistics(view, centerI, centerJ, centerK, tool == MeasurementType.ELLIPSE_ROI)
                : null;
        overlay.addMeasurement(new Measurement(tool, view, pendingPoints, value, statistics));
        pendingPoints.clear();
        pendingView = -1;
    }

    private double computeValue(int view) {
        if (tool == MeasurementType.LENGTH) {
            return MeasurementCalculator.length(pendingPoints.get(0), pendingPoints.get(1));
        }
        if (tool == MeasurementType.ANGLE) {
            return MeasurementCalculator.angle(pendingPoints.get(0), pendingPoints.get(1), pendingPoints.get(2));
        }
        double[] extent = MeasurementCalculator.extent(pendingPoints.get(0), pendingPoints.get(1),
                MprViewOrientation.screenRight(geometry, view), MprViewOrientation.up(geometry, view));
        return tool == MeasurementType.RECT_ROI
                ? MeasurementCalculator.rectangleArea(extent)
                : MeasurementCalculator.ellipseArea(extent);
    }

    /**
     * 统计 ROI 内体素值：在当前层上遍历平面内索引范围，按形状包含判定取样。
     */
    private RoiStatistics computeRoiStatistics(int view, int centerI, int centerJ, int centerK, boolean ellipse) {
        double[] start = geometry.toIndex(pendingPoints.get(0));
        double[] end = geometry.toIndex(pendingPoints.get(1));
        int first0 = inPlaneIndex(start, view, 0);
        int first1 = inPlaneIndex(start, view, 1);
        int last0 = inPlaneIndex(end, view, 0);
        int last1 = inPlaneIndex(end, view, 1);

        int min0 = Math.min(first0, last0);
        int max0 = Math.max(first0, last0);
        int min1 = Math.min(first1, last1);
        int max1 = Math.max(first1, last1);
        double[] values = new double[Math.max(1, (max0 - min0 + 1) * (max1 - min1 + 1))];
        int count = 0;

        for (int a = min0; a <= max0; a++) {
            for (int b = min1; b <= max1; b++) {
                if (ellipse && !MeasurementCalculator.ellipseContains(a, b, first0, first1, last0, last1)) {
                    continue;
                }
                int[] voxel = voxelIndex(view, a, b, centerI, centerJ, centerK);
                if (probe.isInside(voxel[0], voxel[1], voxel[2])) {
                    values[count++] = probe.valueAt(voxel[0], voxel[1], voxel[2]);
                }
            }
        }
        return RoiStatistics.of(values, count);
    }

    /**
     * 取索引用作"平面内第 position 个方向"（0/1）。
     */
    private int inPlaneIndex(double[] index, int view, int position) {
        if (view == MprViewOrientation.VIEW_AXIAL) {
            return (int) Math.round(index[position]);
        }
        if (view == MprViewOrientation.VIEW_CORONAL) {
            return (int) Math.round(index[position == 0 ? 0 : 2]);
        }
        return (int) Math.round(index[position == 0 ? 1 : 2]);
    }

    /**
     * 把"平面内两个索引"还原为体素索引 (i, j, k)。
     */
    private int[] voxelIndex(int view, int inPlane0, int inPlane1, int centerI, int centerJ, int centerK) {
        if (view == MprViewOrientation.VIEW_AXIAL) {
            return new int[]{inPlane0, inPlane1, centerK};
        }
        if (view == MprViewOrientation.VIEW_CORONAL) {
            return new int[]{inPlane0, centerJ, inPlane1};
        }
        return new int[]{centerI, inPlane0, inPlane1};
    }
}
