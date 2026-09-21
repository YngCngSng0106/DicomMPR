package com.zlyd.mpr.dicom;

import java.util.Arrays;

/**
 * 体数据的几何变换（索引 &lt;-&gt; 患者坐标），纯数学、无第三方依赖。
 *
 * <p>约定：i 为列号、j 为行号、k 为层号；X/Y/Z 为方向余弦单位向量。</p>
 */
public final class VolumeGeometry {

    private final int columns;
    private final int rows;
    private final int slices;
    private final double[] origin;
    private final double[] spacing;
    private final double[] axisX;
    private final double[] axisY;
    private final double[] axisZ;

    private VolumeGeometry(int columns, int rows, int slices, double[] origin, double[] spacing,
                           double[] axisX, double[] axisY, double[] axisZ) {
        this.columns = columns;
        this.rows = rows;
        this.slices = slices;
        this.origin = origin;
        this.spacing = spacing;
        this.axisX = axisX;
        this.axisY = axisY;
        this.axisZ = axisZ;
    }

    /**
     * 由序列几何构建。
     *
     * @param geometry 序列几何（含行/列、像素间距、朝向、法向量、层间距）
     * @param firstPosition 排序后第一层的 ImagePositionPatient
     * @param sliceCount 层数
     * @return 体数据几何
     */
    public static VolumeGeometry of(SeriesGeometry geometry, double[] firstPosition, int sliceCount) {
        double[] pixelSpacing = geometry.getPixelSpacing();
        double[] orientation = geometry.getOrientation();
        double[] normal = geometry.getNormal();
        double sliceSpacing = geometry.getSliceSpacing() > 0 ? geometry.getSliceSpacing() : 1.0;
        return new VolumeGeometry(
                geometry.getColumns(),
                geometry.getRows(),
                sliceCount,
                Arrays.copyOf(firstPosition, 3),
                new double[]{pixelSpacing[1], pixelSpacing[0], sliceSpacing},
                new double[]{orientation[0], orientation[1], orientation[2]},
                new double[]{orientation[3], orientation[4], orientation[5]},
                Arrays.copyOf(normal, 3));
    }

    /**
     * 重采样后的几何：原点与朝向不变，维度与间距按重采样方案替换。
     *
     * <p>等体素重采样只改变采样密度、不改变物理范围，因此原点与方向余弦保持原值。</p>
     *
     * @param dimensions 新维度 {列, 行, 层}
     * @param spacing 新间距（三方向相同）
     * @return 新几何
     */
    public VolumeGeometry resampled(int[] dimensions, double spacing) {
        return new VolumeGeometry(
                dimensions[0],
                dimensions[1],
                dimensions[2],
                Arrays.copyOf(origin, 3),
                new double[]{spacing, spacing, spacing},
                Arrays.copyOf(axisX, 3),
                Arrays.copyOf(axisY, 3),
                Arrays.copyOf(axisZ, 3));
    }

    /**
     * 维度 {列, 行, 层}。
     */
    public int[] getDimensions() {
        return new int[]{columns, rows, slices};
    }

    /**
     * 索引 -&gt; 患者坐标。
     */
    public double[] toWorld(double i, double j, double k) {
        return new double[]{
                origin[0] + i * spacing[0] * axisX[0] + j * spacing[1] * axisY[0] + k * spacing[2] * axisZ[0],
                origin[1] + i * spacing[0] * axisX[1] + j * spacing[1] * axisY[1] + k * spacing[2] * axisZ[1],
                origin[2] + i * spacing[0] * axisX[2] + j * spacing[1] * axisY[2] + k * spacing[2] * axisZ[2]
        };
    }

    /**
     * 患者坐标 -&gt; 索引（可含小数）。
     */
    public double[] toIndex(double[] world) {
        double dx = world[0] - origin[0];
        double dy = world[1] - origin[1];
        double dz = world[2] - origin[2];
        return new double[]{
                (dx * axisX[0] + dy * axisX[1] + dz * axisX[2]) / spacing[0],
                (dx * axisY[0] + dy * axisY[1] + dz * axisY[2]) / spacing[1],
                (dx * axisZ[0] + dy * axisZ[1] + dz * axisZ[2]) / spacing[2]
        };
    }

    /**
     * 体数据中心（患者坐标）。
     */
    public double[] center() {
        return toWorld((columns - 1) / 2.0, (rows - 1) / 2.0, (slices - 1) / 2.0);
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public int getSlices() {
        return slices;
    }

    public double[] getOrigin() {
        return Arrays.copyOf(origin, origin.length);
    }

    public double[] getSpacing() {
        return Arrays.copyOf(spacing, spacing.length);
    }

    public double[] getAxisX() {
        return Arrays.copyOf(axisX, axisX.length);
    }

    public double[] getAxisY() {
        return Arrays.copyOf(axisY, axisY.length);
    }

    public double[] getAxisZ() {
        return Arrays.copyOf(axisZ, axisZ.length);
    }
}
