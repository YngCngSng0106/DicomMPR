package com.zlyd.mpr.geometry;

import java.util.Arrays;

import com.zlyd.mpr.dicom.VolumeGeometry;

/**
 * 体数据包围盒的几何运算（纯数学，不依赖任何渲染库）。
 *
 * <p>盒以体中心 M 为原点、以体数据解剖轴为轴，提供两套半长：</p>
 * <ul>
 *   <li><b>体素中心盒</b>（{@code (n−1)/2 × 间距}）：十字线裁剪用，与改造前"按索引 0..n−1 铺满"的行为逐值一致；</li>
 *   <li><b>体素边缘盒</b>（{@code n/2 × 间距}）：取景用，与改造前"n × 间距"的取景公式逐值一致。</li>
 * </ul>
 *
 * <p>斜切后盒在光标坐标系里不再是轴对齐的，因此直线裁剪用标准 slab 法在解剖轴下求解。</p>
 */
public final class VolumeBox {

    private static final int AXIS_COUNT = 3;
    private static final double PARALLEL_EPSILON = 1e-9;

    private final double[] center;
    private final double[][] axes;
    private final double[] halfExtents;
    private final double[] fitHalfExtents;

    private VolumeBox(double[] center, double[][] axes, double[] halfExtents, double[] fitHalfExtents) {
        this.center = Arrays.copyOf(center, AXIS_COUNT);
        this.axes = new double[AXIS_COUNT][];
        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            this.axes[axis] = Arrays.copyOf(axes[axis], AXIS_COUNT);
        }
        this.halfExtents = Arrays.copyOf(halfExtents, AXIS_COUNT);
        this.fitHalfExtents = Arrays.copyOf(fitHalfExtents, AXIS_COUNT);
    }

    /**
     * 由体数据几何构建。
     */
    public static VolumeBox of(VolumeGeometry geometry) {
        double[] spacing = geometry.getSpacing();
        int[] dimensions = geometry.getDimensions();
        double[] halfExtents = new double[AXIS_COUNT];
        double[] fitHalfExtents = new double[AXIS_COUNT];
        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            halfExtents[axis] = (dimensions[axis] - 1) / 2.0 * spacing[axis];
            fitHalfExtents[axis] = dimensions[axis] / 2.0 * spacing[axis];
        }
        return new VolumeBox(geometry.center(),
                new double[][]{geometry.getAxisX(), geometry.getAxisY(), geometry.getAxisZ()},
                halfExtents, fitHalfExtents);
    }

    /**
     * 盒中心 M（= 体中心，患者坐标）。
     */
    public double[] getCenter() {
        return Arrays.copyOf(center, center.length);
    }

    /**
     * 体素中心盒的半长（mm）。
     */
    public double[] getHalfExtents() {
        return Arrays.copyOf(halfExtents, halfExtents.length);
    }

    /**
     * 包围盒对角线长度（mm）：相机沿视线退避的距离基准。
     */
    public double diagonal() {
        double total = 0;
        for (double extent : fitHalfExtents) {
            total += extent * extent;
        }
        return 2.0 * Math.sqrt(total);
    }

    /**
     * 直线 {@code origin + t·direction} 与体素中心盒的交集参数区间。
     *
     * @param origin 直线起点（患者坐标）
     * @param direction 方向（无需归一化，但需非零）
     * @return 长度 2 的数组 {@code {tMin, tMax}}；无交集返回 {@code null}
     */
    public double[] clipRay(double[] origin, double[] direction) {
        double[] relative = subtract(origin, center);
        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            double originAlong = dot(relative, axes[axis]);
            double directionAlong = dot(direction, axes[axis]);
            double half = halfExtents[axis];
            if (Math.abs(directionAlong) < PARALLEL_EPSILON) {
                if (Math.abs(originAlong) > half) {
                    return null;
                }
                continue;
            }
            double first = (-half - originAlong) / directionAlong;
            double second = (half - originAlong) / directionAlong;
            tMin = Math.max(tMin, Math.min(first, second));
            tMax = Math.min(tMax, Math.max(first, second));
            if (tMin > tMax) {
                return null;
            }
        }
        return tMin > tMax ? null : new double[]{tMin, tMax};
    }

    /**
     * 盒在给定平面内两正交轴上的支撑半长（mm）：盒投影到该平面后的外接矩形半尺寸。
     *
     * <p>取景用；使用体素边缘盒，故轴对齐时与改造前的取景公式完全一致。</p>
     *
     * @param axisA 平面内单位向量 A
     * @param axisB 平面内单位向量 B
     * @return 长度 2 的数组：A、B 方向的半尺寸
     */
    public double[] supportHalfExtents(double[] axisA, double[] axisB) {
        return new double[]{support(axisA), support(axisB)};
    }

    private double support(double[] direction) {
        double total = 0;
        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            total += fitHalfExtents[axis] * Math.abs(dot(direction, axes[axis]));
        }
        return total;
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] subtract(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }
}
