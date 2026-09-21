package com.zlyd.mpr.geometry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link Vectors} 的基本运算测试。
 */
class VectorsTest {

    private static final double DELTA = 1e-12;

    @Test
    void shouldComputeBasicOperations() {
        double[] a = {1, 2, 3};
        double[] b = {4, 5, 6};

        assertEquals(32.0, Vectors.dot(a, b), DELTA);
        assertArrayEquals(new double[]{-3, 6, -3}, Vectors.cross(a, b), DELTA);
        assertArrayEquals(new double[]{5, 7, 9}, Vectors.add(a, b), DELTA);
        assertArrayEquals(new double[]{-3, -3, -3}, Vectors.subtract(a, b), DELTA);
        assertArrayEquals(new double[]{2, 4, 6}, Vectors.scale(a, 2.0), DELTA);
        assertEquals(Math.sqrt(14.0), Vectors.norm(a), DELTA);
    }

    @Test
    void shouldNormalizeAndOrthogonalize() {
        assertArrayEquals(new double[]{1, 0, 0}, Vectors.normalize(new double[]{3, 0, 0}), DELTA);

        double[] up = Vectors.orthogonalize(new double[]{1, 1, 0}, new double[]{0, 0, 1});
        assertArrayEquals(new double[]{1 / Math.sqrt(2.0), 1 / Math.sqrt(2.0), 0}, up, 1e-12);
        assertEquals(0.0, Vectors.dot(up, new double[]{0, 0, 1}), 1e-12);

        // 与轴平行的输入应回退到另一条正交方向
        double[] fallback = Vectors.orthogonalize(new double[]{0, 0, 1}, new double[]{0, 0, 1});
        assertEquals(0.0, Vectors.dot(fallback, new double[]{0, 0, 1}), 1e-12);
        assertEquals(1.0, Vectors.norm(fallback), 1e-12);
    }
}
