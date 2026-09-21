package com.zlyd.mpr.geometry;

/**
 * 三维向量小工具（纯数学）：供光标坐标系、机架、相机与十字线绘制共用，避免各文件重复实现。
 */
public final class Vectors {

    private static final double EPSILON = 1e-12;

    private Vectors() {
    }

    public static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    public static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    public static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    public static double[] subtract(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    public static double[] scale(double[] vector, double factor) {
        return new double[]{vector[0] * factor, vector[1] * factor, vector[2] * factor};
    }

    public static double norm(double[] vector) {
        return Math.sqrt(dot(vector, vector));
    }

    /**
     * 归一化；零向量返回原向量（长度 0）。
     */
    public static double[] normalize(double[] vector) {
        double length = norm(vector);
        if (length < EPSILON) {
            return new double[]{vector[0], vector[1], vector[2]};
        }
        return scale(vector, 1.0 / length);
    }

    /**
     * 把 {@code vector} 投影到垂直于 {@code axis} 的平面并归一化（相机 viewUp 只保证不与视线同向）。
     *
     * @return 单位向量；投影退化时返回与 axis 不平行的任一单位向量
     */
    public static double[] orthogonalize(double[] vector, double[] axis) {
        double[] projected = subtract(vector, scale(axis, dot(vector, axis)));
        if (norm(projected) < 1e-9) {
            double[] fallback = Math.abs(axis[0]) < 0.9
                    ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
            projected = subtract(fallback, scale(axis, dot(fallback, axis)));
        }
        return normalize(projected);
    }
}
