package com.zlyd.mpr.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
    void shouldComputePolylineLengthAndPolygonMetrics() {
        double[] axisX = {1, 0, 0};
        double[] axisY = {0, 1, 0};
        List<double[]> path = List.of(
                new double[]{0, 0, 0}, new double[]{3, 0, 0}, new double[]{3, 4, 0});
        // 折线：3 + 4 = 7
        assertEquals(7.0, MeasurementCalculator.polylineLength(path), DELTA);

        List<double[]> square = List.of(
                new double[]{0, 0, 0}, new double[]{10, 0, 0},
                new double[]{10, 20, 0}, new double[]{0, 20, 0});
        // 矩形 10×20：面积 200、周长 60
        assertEquals(200.0, MeasurementCalculator.polygonArea(square, axisX, axisY), DELTA);
        assertEquals(60.0, MeasurementCalculator.polygonPerimeter(square), DELTA);
        // 折线长度 = 10 + 20 + 10 = 40（不含闭合边）
        assertEquals(40.0, MeasurementCalculator.polylineLength(square), DELTA);

        // 三角形面积（鞋带公式）：底 10 高 5 → 25
        List<double[]> triangle = List.of(
                new double[]{0, 0, 0}, new double[]{10, 0, 0}, new double[]{0, 5, 0});
        assertEquals(25.0, MeasurementCalculator.polygonArea(triangle, axisX, axisY), DELTA);

        // 点包含（射线法）
        assertTrue(MeasurementCalculator.polygonContains(5, 10, square, axisX, axisY));
        assertFalse(MeasurementCalculator.polygonContains(15, 10, square, axisX, axisY));
        assertFalse(MeasurementCalculator.polygonContains(-1, 10, square, axisX, axisY));
    }

    @Test
    void shouldComputePolygonMetricsOnObliquePlane() {
        // 斜平面：平面内两轴为 (X+Z)/√2 与 Y，验证斜切下面积/包含仍正确
        double root = Math.sqrt(2.0);
        double[] axis1 = {1 / root, 0, 1 / root};
        double[] axis2 = {0, 1, 0};
        List<double[]> square = List.of(
                new double[]{0, 0, 0},
                new double[]{10 / root, 0, 10 / root},
                new double[]{10 / root, 20, 10 / root},
                new double[]{0, 20, 0});
        assertEquals(200.0, MeasurementCalculator.polygonArea(square, axis1, axis2), 1e-9);
        assertEquals(60.0, MeasurementCalculator.polygonPerimeter(square), 1e-9);
        assertTrue(MeasurementCalculator.polygonContains(5, 10, square, axis1, axis2));
        assertFalse(MeasurementCalculator.polygonContains(15, 10, square, axis1, axis2));
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
