package com.zlyd.mpr.geometry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * {@link IsotropicResampler} 的等体素重采样测试（关键数学函数）。
 *
 * <p>覆盖率要点：等体素判定、目标间距取最细方向、体素数上限、输出维度、
 * 三线性插值取值与越界截断。</p>
 */
class IsotropicResamplerTest {

    private static final double DELTA = 1e-9;

    @Test
    void shouldTreatNearlyEqualSpacingAsIsotropic() {
        assertTrue(IsotropicResampler.isIsotropic(new double[]{0.758, 0.758, 0.75}));
        assertTrue(IsotropicResampler.isIsotropic(new double[]{1.0, 1.0, 1.0}));
        assertFalse(IsotropicResampler.isIsotropic(new double[]{0.7, 0.7, 5.0}));
    }

    @Test
    void shouldTreatNonPositiveSpacingAsAnisotropic() {
        assertFalse(IsotropicResampler.isIsotropic(new double[]{0.0, 0.7, 0.7}));
        assertFalse(IsotropicResampler.isIsotropic(new double[]{-1.0, 0.7, 0.7}));
    }

    @Test
    void shouldSkipResampleWhenAlreadyIsotropic() {
        ResamplePlan plan = IsotropicResampler.plan(
                new int[]{512, 512, 447}, new double[]{0.758, 0.758, 0.75});

        assertFalse(plan.isNeeded());
        assertArrayEquals(new int[]{512, 512, 447}, plan.getDimensions());
    }

    @Test
    void shouldUseFinestSpacingAsTarget() {
        ResamplePlan plan = IsotropicResampler.plan(
                new int[]{512, 512, 100}, new double[]{0.7, 0.7, 5.0});

        assertTrue(plan.isNeeded());
        assertEquals(0.7, plan.getSpacing(), DELTA);
        assertArrayEquals(new int[]{512, 512, 714}, plan.getDimensions());
        assertEquals(512L * 512 * 714, plan.getVoxelCount());
    }

    @Test
    void shouldEnlargeTargetSpacingToRespectVoxelLimit() {
        long limit = 20_000_000L;
        ResamplePlan plan = IsotropicResampler.plan(
                new int[]{512, 512, 100}, new double[]{0.7, 0.7, 5.0}, limit);

        assertTrue(plan.isNeeded());
        assertTrue(plan.getVoxelCount() <= limit, "体素数应不超上限: " + plan);
        assertTrue(plan.getSpacing() > 0.7, "目标间距应被放大: " + plan);
        assertTrue(plan.getSpacing() <= 5.0, "目标间距不应超过源最粗间距: " + plan);
    }

    @Test
    void shouldRejectInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> IsotropicResampler.plan(
                new int[]{0, 512, 100}, new double[]{0.7, 0.7, 5.0}));
        assertThrows(IllegalArgumentException.class, () -> IsotropicResampler.plan(
                new int[]{512, 512, 100}, new double[]{0.0, 0.7, 5.0}));
        assertThrows(IllegalArgumentException.class, () -> IsotropicResampler.plan(
                new int[]{512, 512, 100}, new double[]{0.7, 0.7, 5.0}, 0L));
    }

    @Test
    void shouldComputeOutputDimensionsKeepingPhysicalExtent() {
        int[] dimensions = IsotropicResampler.outputDimensions(
                new int[]{100, 100, 10}, new double[]{0.5, 0.5, 3.0}, 0.5);

        assertArrayEquals(new int[]{100, 100, 60}, dimensions);
    }

    @Test
    void shouldInterpolateLinearlyAlongAxis() {
        short[] ramp = {0, 10, 20, 30};
        ResamplePlan plan = new ResamplePlan(true, new int[]{8, 1, 1}, 0.5);

        short[] result = IsotropicResampler.resample(ramp, new int[]{4, 1, 1}, new double[]{1.0, 1.0, 1.0}, plan);

        assertArrayEquals(new short[]{0, 5, 10, 15, 20, 25, 30, 30}, result);
    }

    @Test
    void shouldAverageFourNeighboursAtSlabCenter() {
        short[] data = {0, 100, 200, 300};
        int[] dimensions = {2, 1, 2};

        assertEquals(150.0, IsotropicResampler.trilinear(data, dimensions, 0.5, 0.0, 0.5), DELTA);
    }

    @Test
    void shouldNotExtrapolateOutsideVolume() {
        short[] data = new short[8];
        Arrays.fill(data, (short) 100);

        assertEquals(100.0, IsotropicResampler.trilinear(data, new int[]{2, 2, 2}, -5.0, 10.0, 0.0), DELTA);
        assertEquals(100.0, IsotropicResampler.trilinear(data, new int[]{2, 2, 2}, 1.0, 1.0, 1.0), DELTA);
    }

    @Test
    void shouldKeepConstantVolumeConstant() {
        short[] data = new short[4 * 4 * 4];
        Arrays.fill(data, (short) -1024);
        ResamplePlan plan = new ResamplePlan(true, new int[]{8, 8, 8}, 0.5);

        short[] result = IsotropicResampler.resample(data, new int[]{4, 4, 4}, new double[]{1, 1, 1}, plan);

        assertEquals(512, result.length);
        for (short value : result) {
            assertEquals(-1024, value);
        }
    }
}
