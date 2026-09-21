package com.zlyd.mpr.geometry;

/**
 * 等体素（isotropic）重采样：纯数学、无第三方依赖。
 *
 * <p>用于把层厚较大的序列（例如 0.7×0.7×5.0mm）重采样为三方向等间距体素，
 * 使 MPR 各方向分辨率一致、斜切与厚层重建不出现台阶。</p>
 *
 * <p>约定：维度数组为 {列, 行, 层}，间距数组为 {列间距, 行间距, 层间距}，
 * 体素数组按 VTK "x 最快" 排布，索引 = k*列*行 + j*列 + i。</p>
 */
public final class IsotropicResampler {

    /** 三方向间距相对差不超过 5% 即视为等体素，无需重采样。 */
    public static final double ISOTROPIC_TOLERANCE = 0.05;

    /** 输出体素数上限（约 256M，short 标量约 512MB）。 */
    public static final long DEFAULT_MAX_VOXELS = 268_435_456L;

    private IsotropicResampler() {
    }

    /**
     * 三方向间距是否已在容差内视为等体素。
     */
    public static boolean isIsotropic(double[] spacing) {
        double min = min(spacing);
        double max = max(spacing);
        if (min <= 0) {
            return false;
        }
        return (max - min) / max <= ISOTROPIC_TOLERANCE;
    }

    /**
     * 生成重采样方案（体素数上限取 {@link #DEFAULT_MAX_VOXELS}）。
     */
    public static ResamplePlan plan(int[] dimensions, double[] spacing) {
        return plan(dimensions, spacing, DEFAULT_MAX_VOXELS);
    }

    /**
     * 生成重采样方案。
     *
     * <p>目标间距取三方向最小间距（保留最细方向的分辨率）；已等体素时返回
     * 不需要重采样的方案。若输出体素数超过上限，按体积比
     * cbrt(体素数/上限) 放大目标间距，直至不超限。</p>
     */
    public static ResamplePlan plan(int[] dimensions, double[] spacing, long maxVoxels) {
        validate(dimensions, spacing, maxVoxels);
        double target = min(spacing);
        if (isIsotropic(spacing)) {
            return new ResamplePlan(false, dimensions, target);
        }
        int[] output = outputDimensions(dimensions, spacing, target);
        long voxels = voxelCount(output);
        while (voxels > maxVoxels) {
            target = target * Math.max(1.05, Math.cbrt((double) voxels / maxVoxels));
            output = outputDimensions(dimensions, spacing, target);
            voxels = voxelCount(output);
        }
        return new ResamplePlan(true, output, target);
    }

    private static void validate(int[] dimensions, double[] spacing, long maxVoxels) {
        if (dimensions.length != 3 || spacing.length != 3) {
            throw new IllegalArgumentException("维度与间距必须是 3 个方向");
        }
        for (int axis = 0; axis < 3; axis++) {
            if (dimensions[axis] < 1) {
                throw new IllegalArgumentException("维度必须为正: " + dimensions[axis]);
            }
            if (spacing[axis] <= 0) {
                throw new IllegalArgumentException("间距必须为正: " + spacing[axis]);
            }
        }
        if (maxVoxels < 1) {
            throw new IllegalArgumentException("体素数上限必须为正: " + maxVoxels);
        }
    }

    /**
     * 目标间距下的输出维度：各方向物理长度保持不变，四舍五入且至少 1。
     */
    public static int[] outputDimensions(int[] dimensions, double[] spacing, double targetSpacing) {
        int[] output = new int[dimensions.length];
        for (int axis = 0; axis < dimensions.length; axis++) {
            double count = dimensions[axis] * spacing[axis] / targetSpacing;
            output[axis] = Math.max(1, (int) Math.round(count));
        }
        return output;
    }

    /**
     * 三线性插值重采样。
     *
     * @param source 源体素数组
     * @param sourceDimensions 源维度 {列, 行, 层}
     * @param sourceSpacing 源间距 {列, 行, 层}
     * @param plan 重采样方案
     * @return 重采样后的体素数组
     */
    public static short[] resample(short[] source, int[] sourceDimensions, double[] sourceSpacing,
                                   ResamplePlan plan) {
        int[] output = plan.getDimensions();
        double stepX = plan.getSpacing() / sourceSpacing[0];
        double stepY = plan.getSpacing() / sourceSpacing[1];
        double stepZ = plan.getSpacing() / sourceSpacing[2];
        short[] result = new short[Math.toIntExact(plan.getVoxelCount())];
        int index = 0;
        for (int k = 0; k < output[2]; k++) {
            for (int j = 0; j < output[1]; j++) {
                for (int i = 0; i < output[0]; i++) {
                    double value = trilinear(source, sourceDimensions, i * stepX, j * stepY, k * stepZ);
                    result[index++] = (short) Math.round(value);
                }
            }
        }
        return result;
    }

    /**
     * 三线性插值取值；采样点越界时截断到边界（不外推）。
     */
    static double trilinear(short[] source, int[] dimensions, double x, double y, double z) {
        double cx = clamp(x, dimensions[0]);
        double cy = clamp(y, dimensions[1]);
        double cz = clamp(z, dimensions[2]);
        int x0 = (int) Math.floor(cx);
        int y0 = (int) Math.floor(cy);
        int z0 = (int) Math.floor(cz);
        int x1 = Math.min(x0 + 1, dimensions[0] - 1);
        int y1 = Math.min(y0 + 1, dimensions[1] - 1);
        int z1 = Math.min(z0 + 1, dimensions[2] - 1);
        double fx = cx - x0;
        double fy = cy - y0;
        double fz = cz - z0;

        double row0 = lerp(at(source, dimensions, x0, y0, z0), at(source, dimensions, x1, y0, z0), fx);
        double row1 = lerp(at(source, dimensions, x0, y1, z0), at(source, dimensions, x1, y1, z0), fx);
        double row2 = lerp(at(source, dimensions, x0, y0, z1), at(source, dimensions, x1, y0, z1), fx);
        double row3 = lerp(at(source, dimensions, x0, y1, z1), at(source, dimensions, x1, y1, z1), fx);

        double plane0 = lerp(row0, row1, fy);
        double plane1 = lerp(row2, row3, fy);
        return lerp(plane0, plane1, fz);
    }

    private static double at(short[] source, int[] dimensions, int i, int j, int k) {
        return source[(k * dimensions[1] + j) * dimensions[0] + i];
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

    private static double min(double[] spacing) {
        double value = spacing[0];
        for (double element : spacing) {
            value = Math.min(value, element);
        }
        return value;
    }

    private static double max(double[] spacing) {
        double value = spacing[0];
        for (double element : spacing) {
            value = Math.max(value, element);
        }
        return value;
    }

    private static long voxelCount(int[] dimensions) {
        long count = 1;
        for (int element : dimensions) {
            count *= element;
        }
        return count;
    }
}
