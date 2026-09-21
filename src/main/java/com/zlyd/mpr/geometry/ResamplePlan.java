package com.zlyd.mpr.geometry;

import java.util.Arrays;

/**
 * 等体素重采样方案（值对象，纯数据）。
 *
 * <p>由 {@link IsotropicResampler#plan(int[], double[])} 生成，描述是否需要重采样、
 * 输出维度与输出间距。间距为各向同性，因此只用一个标量表示。</p>
 */
public final class ResamplePlan {

    private final boolean needed;
    private final int[] dimensions;
    private final double spacing;

    public ResamplePlan(boolean needed, int[] dimensions, double spacing) {
        this.needed = needed;
        this.dimensions = Arrays.copyOf(dimensions, dimensions.length);
        this.spacing = spacing;
    }

    /**
     * 是否需要重采样（原间距已在容差内等体素时为 false）。
     */
    public boolean isNeeded() {
        return needed;
    }

    /**
     * 输出维度（{列, 行, 层}）；不重采样时为原维度。
     */
    public int[] getDimensions() {
        return Arrays.copyOf(dimensions, dimensions.length);
    }

    /**
     * 输出间距（三方向相同）。
     */
    public double getSpacing() {
        return spacing;
    }

    /**
     * 输出体素数。
     */
    public long getVoxelCount() {
        return (long) dimensions[0] * dimensions[1] * dimensions[2];
    }

    @Override
    public String toString() {
        return "ResamplePlan{needed=" + needed + ", dimensions=" + Arrays.toString(dimensions)
                + ", spacing=" + spacing + ", voxels=" + getVoxelCount() + "}";
    }
}
