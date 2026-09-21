package com.zlyd.mpr.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link MeasurementCalculator} 与 {@link RoiStatistics} 的测量计算测试（关键数学函数）。
 */
class MeasurementCalculatorTest {

    private static final double DELTA = 1e-9;
    private static final double[] AXIS_X = {1, 0, 0};
    private static final double[] AXIS_Y = {0, 1, 0};

    @Test
    void shouldComputeLengthInMillimeters() {
        assertEquals(5.0, MeasurementCalculator.length(new double[]{0, 0, 0}, new double[]{3, 4, 0}), DELTA);
    }

    @Test
    void shouldComputeRightAngle() {
        assertEquals(90.0,
                MeasurementCalculator.angle(new double[]{1, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 1, 0}),
                DELTA);
    }

    @Test
    void shouldComputeExtentAndAreas() {
        double[] extent = MeasurementCalculator.extent(
                new double[]{0, 0, 0}, new double[]{30, 20, 0}, AXIS_X, AXIS_Y);
        assertEquals(30.0, extent[0], DELTA);
        assertEquals(20.0, extent[1], DELTA);
        assertEquals(600.0, MeasurementCalculator.rectangleArea(extent), DELTA);
        assertEquals(Math.PI / 4.0 * 600.0, MeasurementCalculator.ellipseArea(extent), DELTA);
    }

    @Test
    void shouldTestShapeContainment() {
        assertTrue(MeasurementCalculator.rectangleContains(5, 5, 10, 10, 0, 0));
        assertFalse(MeasurementCalculator.rectangleContains(11, 5, 10, 10, 0, 0));
        assertTrue(MeasurementCalculator.ellipseContains(5, 5, 0, 0, 10, 10));
        assertFalse(MeasurementCalculator.ellipseContains(0, 0, 0, 0, 10, 10));
    }

    @Test
    void shouldComputeRoiStatistics() {
        RoiStatistics statistics = RoiStatistics.of(new double[]{-100, 0, 100, 200}, 4);
        assertEquals(4, statistics.getCount());
        assertEquals(-100.0, statistics.getMin(), DELTA);
        assertEquals(200.0, statistics.getMax(), DELTA);
        assertEquals(50.0, statistics.getMean(), DELTA);
        assertEquals(111.80339887498948, statistics.getStandardDeviation(), 1e-9);
    }

    @Test
    void shouldReturnEmptyStatisticsForNoValues() {
        assertEquals(0, RoiStatistics.of(new double[0], 0).getCount());
    }
}
