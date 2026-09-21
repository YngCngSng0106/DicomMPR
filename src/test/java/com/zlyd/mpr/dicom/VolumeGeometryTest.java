package com.zlyd.mpr.dicom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link VolumeGeometry} 的索引与患者坐标换算测试（关键数学函数）。
 */
class VolumeGeometryTest {

    private static final double DELTA = 1e-9;

    @Test
    void shouldConvertIndexToWorldAndBack() {
        VolumeGeometry geometry = VolumeGeometry.of(axialGeometry(), new double[]{10.0, 20.0, 30.0}, 5);

        double[] world = geometry.toWorld(2, 1, 3);
        assertArrayEquals(new double[]{12.0, 20.5, 36.0}, world, DELTA);

        double[] index = geometry.toIndex(world);
        assertArrayEquals(new double[]{2.0, 1.0, 3.0}, index, DELTA);
    }

    @Test
    void shouldComputeVolumeCenter() {
        VolumeGeometry geometry = VolumeGeometry.of(axialGeometry(), new double[]{10.0, 20.0, 30.0}, 5);

        assertArrayEquals(new double[]{11.0, 20.75, 34.0}, geometry.center(), DELTA);
    }

    private static SeriesGeometry axialGeometry() {
        return new SeriesGeometry.Builder()
                .dimension(4, 3)
                .pixelSpacing(new double[]{0.5, 1.0})
                .orientation(new double[]{1, 0, 0, 0, 1, 0})
                .normal(new double[]{0, 0, 1})
                .sliceSpacing(2.0)
                .build();
    }
}
