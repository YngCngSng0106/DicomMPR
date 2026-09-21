package com.zlyd.mpr.geometry;

/**
 * 测量计算（纯数学）：长度、角度、面积，以及 ROI 形状判定。
 *
 * <p>长度/角度使用患者坐标（mm），与缩放无关；ROI 面积由平面内两个方向的分量相乘得到。</p>
 */
public final class MeasurementCalculator {

    private static final double PARALLEL_EPSILON = 1e-9;
    private static final double DEGREES_PER_RADIAN = 180.0 / Math.PI;

    private MeasurementCalculator() {
    }

    /**
     * 两点距离（mm）。
     */
    public static double length(double[] point1, double[] point2) {
        return distance(point1, point2);
    }

    /**
     * 三点夹角（度），顶点为 {@code vertex}。
     */
    public static double angle(double[] point1, double[] vertex, double[] point2) {
        double[] vector1 = subtract(point1, vertex);
        double[] vector2 = subtract(point2, vertex);
        double length1 = norm(vector1);
        double length2 = norm(vector2);
        if (length1 < PARALLEL_EPSILON || length2 < PARALLEL_EPSILON) {
            return 0.0;
        }
        double cosine = dot(vector1, vector2) / (length1 * length2);
        return Math.acos(Math.max(-1.0, Math.min(1.0, cosine))) * DEGREES_PER_RADIAN;
    }

    /**
     * 平面内两点的"外接矩形"边长（沿给定的两个平面内方向）。
     *
     * @param axis1 平面内方向 1（单位向量）
     * @param axis2 平面内方向 2（单位向量）
     * @return 数组 [边1长度, 边2长度]，单位 mm
     */
    public static double[] extent(double[] point1, double[] point2, double[] axis1, double[] axis2) {
        double[] delta = subtract(point2, point1);
        return new double[]{Math.abs(dot(delta, axis1)), Math.abs(dot(delta, axis2))};
    }

    /**
     * 矩形面积（mm²）。
     */
    public static double rectangleArea(double[] extent) {
        return extent[0] * extent[1];
    }

    /**
     * 椭圆面积（mm²），其中 {@code extent} 为外接矩形的两个边长。
     */
    public static double ellipseArea(double[] extent) {
        return Math.PI / 4.0 * extent[0] * extent[1];
    }

    /**
     * 判断平面内点是否落在矩形 ROI 内（索引坐标）。
     */
    public static boolean rectangleContains(double i, double j,
                                            double startI, double startJ, double endI, double endJ) {
        return i >= Math.min(startI, endI) && i <= Math.max(startI, endI)
                && j >= Math.min(startJ, endJ) && j <= Math.max(startJ, endJ);
    }

    /**
     * 判断平面内点是否落在椭圆 ROI 内（索引坐标；椭圆为外接矩形的内切椭圆）。
     */
    public static boolean ellipseContains(double i, double j,
                                          double startI, double startJ, double endI, double endJ) {
        double centerI = (startI + endI) / 2.0;
        double centerJ = (startJ + endJ) / 2.0;
        double radiusI = Math.abs(endI - startI) / 2.0;
        double radiusJ = Math.abs(endJ - startJ) / 2.0;
        if (radiusI < PARALLEL_EPSILON || radiusJ < PARALLEL_EPSILON) {
            return false;
        }
        double normalizedI = (i - centerI) / radiusI;
        double normalizedJ = (j - centerJ) / radiusJ;
        return normalizedI * normalizedI + normalizedJ * normalizedJ <= 1.0;
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double[] subtract(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double norm(double[] vector) {
        return Math.sqrt(dot(vector, vector));
    }
}
