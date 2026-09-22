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
     * 折线总长度（mm）：相邻点距离之和。
     *
     * @param points 点序列（患者坐标）；少于 2 点返回 0
     */
    public static double polylineLength(java.util.List<double[]> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int index = 1; index < points.size(); index++) {
            total += distance(points.get(index - 1), points.get(index));
        }
        return total;
    }

    /**
     * 多边形面积（mm²）：把点投影到给定的平面内两轴后用鞋带公式计算。
     *
     * @param points 顶点序列（患者坐标，需共面）
     * @param axis1 平面内方向 1（单位向量）
     * @param axis2 平面内方向 2（单位向量）
     * @return 面积（mm²）；少于 3 点返回 0
     */
    public static double polygonArea(java.util.List<double[]> points, double[] axis1, double[] axis2) {
        if (points == null || points.size() < 3) {
            return 0.0;
        }
        double[] origin = points.get(0);
        double sum = 0.0;
        for (int index = 0; index < points.size(); index++) {
            double[] current = project(points.get(index), origin, axis1, axis2);
            double[] next = project(points.get((index + 1) % points.size()), origin, axis1, axis2);
            sum += current[0] * next[1] - next[0] * current[1];
        }
        return Math.abs(sum) / 2.0;
    }

    /**
     * 多边形周长（mm）。
     */
    public static double polygonPerimeter(java.util.List<double[]> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }
        double total = distance(points.get(points.size() - 1), points.get(0));
        return total + polylineLength(points);
    }

    /**
     * 判断平面内点是否落在多边形内（射线法）。
     *
     * @param i 待判点的平面坐标 1（沿 {@code axis1}，原点为 {@code points} 的第 0 个顶点）
     * @param j 待判点的平面坐标 2（沿 {@code axis2}）
     * @param points 多边形顶点（患者坐标，需共面）
     */
    public static boolean polygonContains(double i, double j, java.util.List<double[]> points,
                                          double[] axis1, double[] axis2) {
        if (points == null || points.size() < 3) {
            return false;
        }
        double[] origin = points.get(0);
        boolean inside = false;
        for (int index = 0, previous = points.size() - 1; index < points.size(); previous = index++) {
            double[] current = project(points.get(index), origin, axis1, axis2);
            double[] before = project(points.get(previous), origin, axis1, axis2);
            boolean straddles = (current[1] > j) != (before[1] > j);
            if (straddles) {
                double crossI = current[0] + (j - current[1]) / (before[1] - current[1])
                        * (before[0] - current[0]);
                if (i < crossI) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    /**
     * 把患者坐标点投影到以 {@code origin} 为原点、{@code axis1/axis2} 为轴的平面坐标。
     */
    public static double[] project(double[] point, double[] origin, double[] axis1, double[] axis2) {
        double[] delta = subtract(point, origin);
        return new double[]{dot(delta, axis1), dot(delta, axis2)};
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
