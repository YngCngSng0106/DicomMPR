package com.zlyd.mpr.mpr;

import com.zlyd.mpr.dicom.VolumeGeometry;
import com.zlyd.mpr.geometry.VoxelProbe;

import vtk.vtkDataArray;
import vtk.vtkImageData;

/**
 * MPR 体素探测（D6）：把患者坐标换算为体素索引，并在**原始体数据**上做三线性插值取值。
 *
 * <p>由于直接采样原始体数据，斜切后 HU 读数依然准确，且与显示管线的重采样方式无关
 * （显示管线同样是三线性，见 S0 预检）。</p>
 */
final class MprVolumeProbe {

    private final VolumeGeometry geometry;
    private final vtkDataArray scalars;

    MprVolumeProbe(VolumeGeometry geometry, vtkImageData volume) {
        this.geometry = geometry;
        this.scalars = volume.GetPointData().GetScalars();
    }

    /**
     * 探测患者坐标处的体素值（三线性插值）。
     *
     * @param view 指针所在视图
     * @param world 指针处的患者坐标
     * @return 探测结果；超出体数据范围返回 {@code null}
     */
    VoxelProbe probe(int view, double[] world) {
        double[] index = geometry.toIndex(world);
        int i = (int) Math.round(index[0]);
        int j = (int) Math.round(index[1]);
        int k = (int) Math.round(index[2]);
        if (!isInside(i, j, k)) {
            return null;
        }
        return new VoxelProbe(view, i, j, k, sampleTrilinear(index));
    }

    /**
     * 按小数索引三线性插值取值；越界处截断到边界（不外推）。
     */
    double sampleTrilinear(double[] index) {
        int columns = geometry.getColumns();
        int rows = geometry.getRows();
        int slices = geometry.getSlices();
        double cx = clamp(index[0], columns);
        double cy = clamp(index[1], rows);
        double cz = clamp(index[2], slices);
        int i0 = (int) Math.floor(cx);
        int j0 = (int) Math.floor(cy);
        int k0 = (int) Math.floor(cz);
        int i1 = Math.min(i0 + 1, columns - 1);
        int j1 = Math.min(j0 + 1, rows - 1);
        int k1 = Math.min(k0 + 1, slices - 1);
        double fx = cx - i0;
        double fy = cy - j0;
        double fz = cz - k0;

        double row00 = lerp(valueAt(i0, j0, k0), valueAt(i1, j0, k0), fx);
        double row10 = lerp(valueAt(i0, j1, k0), valueAt(i1, j1, k0), fx);
        double row01 = lerp(valueAt(i0, j0, k1), valueAt(i1, j0, k1), fx);
        double row11 = lerp(valueAt(i0, j1, k1), valueAt(i1, j1, k1), fx);
        return lerp(lerp(row00, row10, fy), lerp(row01, row11, fy), fz);
    }

    /**
     * 读取指定体素的值（调用方需保证索引在范围内；ROI 统计按整数体素取样）。
     */
    double valueAt(int i, int j, int k) {
        long offset = ((long) k * geometry.getRows() + j) * geometry.getColumns() + i;
        return scalars.GetTuple1(offset);
    }

    /**
     * 索引是否在体数据范围内。
     */
    boolean isInside(int i, int j, int k) {
        return i >= 0 && i < geometry.getColumns()
                && j >= 0 && j < geometry.getRows()
                && k >= 0 && k < geometry.getSlices();
    }

    private static double lerp(double from, double to, double fraction) {
        return from + (to - from) * fraction;
    }

    private static double clamp(double value, int size) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, size - 1);
    }
}
