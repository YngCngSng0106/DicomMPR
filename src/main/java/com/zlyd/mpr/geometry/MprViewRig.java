package com.zlyd.mpr.geometry;

import java.util.Arrays;

/**
 * MPR 各视图的"机架"：视线方向 d 与屏幕上方 up 的取法。
 *
 * <p>两种取法：</p>
 * <ul>
 *   <li><b>连续性</b>（{@link #continuity()}，默认）：d/up 全部取自光标坐标系的轴，
 *       因此平面旋转时相机跟着转、十字线在屏幕上恒为"＋"且位置不动；代价是"上方向"会随平面滚转。</li>
 *   <li><b>回正</b>（{@link #aligned(MprCursorFrame)}，一次性动作）：在候选集合
 *       （视线取 ±平面法向、up 取另两个轴之一 ± 号，共 8 种）中挑选最贴近解剖习惯的组合
 *       （轴位→A 上 L 右、矢/冠状→H 上 P 或 L 右）；候选仍全部取自坐标系轴，故十字线仍是"＋"。</li>
 * </ul>
 *
 * <p>机架以"轴索引 + 符号"存储，故旋转时不需要更新——只要基变了，向量自动跟着变（连续性内建）。</p>
 */
public final class MprViewRig {

    /** 视图个数。 */
    public static final int VIEW_COUNT = 3;

    private static final int AXIS_U = MprCursorFrame.AXIS_U;
    private static final int AXIS_V = MprCursorFrame.AXIS_V;
    private static final int AXIS_W = MprCursorFrame.AXIS_W;
    private static final double TIE_BREAK_WEIGHT = 0.01;

    private final int[] directionAxis;
    private final double[] directionSign;
    private final int[] upAxis;
    private final double[] upSign;

    private MprViewRig(int[] directionAxis, double[] directionSign, int[] upAxis, double[] upSign) {
        this.directionAxis = Arrays.copyOf(directionAxis, VIEW_COUNT);
        this.directionSign = Arrays.copyOf(directionSign, VIEW_COUNT);
        this.upAxis = Arrays.copyOf(upAxis, VIEW_COUNT);
        this.upSign = Arrays.copyOf(upSign, VIEW_COUNT);
        validate();
    }

    /**
     * 由"轴索引 + 符号"构造（供单元测试使用，正常路径请用 {@link #continuity()} / {@link #aligned}）。
     */
    public static MprViewRig of(int[] directionAxis, double[] directionSign, int[] upAxis, double[] upSign) {
        return new MprViewRig(directionAxis, directionSign, upAxis, upSign);
    }

    /**
     * 连续性机架（默认）：
     * 轴位 d=+w、up=−v；矢状 d=−u、up=+w；冠状 d=+v、up=+w。
     */
    public static MprViewRig continuity() {
        return new MprViewRig(
                new int[]{AXIS_W, AXIS_U, AXIS_V},
                new double[]{1.0, -1.0, 1.0},
                new int[]{AXIS_V, AXIS_W, AXIS_W},
                new double[]{-1.0, 1.0, 1.0});
    }

    /**
     * 回正机架：为每个视图挑选"解剖习惯最优"的 (视线符号, up 轴与符号) 组合。
     *
     * @param frame 当前光标坐标系（解剖方向由其解剖基给出）
     */
    public static MprViewRig aligned(MprCursorFrame frame) {
        int[] direction = new int[VIEW_COUNT];
        double[] signedDirection = new double[VIEW_COUNT];
        int[] up = new int[VIEW_COUNT];
        double[] signedUp = new double[VIEW_COUNT];
        for (int view = 0; view < VIEW_COUNT; view++) {
            int planeAxis = MprCursorFrame.planeAxis(view);
            double[] preferredUp = preferredUp(frame, view);
            double[] preferredRight = preferredRight(frame, view);
            double[] preferredDirection = preferredDirection(frame, view);
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int candidateUp = 0; candidateUp < VIEW_COUNT; candidateUp++) {
                if (candidateUp == planeAxis) {
                    continue;
                }
                for (double candidateUpSign : new double[]{1.0, -1.0}) {
                    for (double candidateDirectionSign : new double[]{
                            MprCursorFrame.directionSign(view), -MprCursorFrame.directionSign(view)}) {
                        double[] upVector = scale(frame.basisAxis(candidateUp), candidateUpSign);
                        double[] directionVector = scale(frame.basisAxis(planeAxis), candidateDirectionSign);
                        double[] rightVector = cross(directionVector, upVector);
                        double score = dot(upVector, preferredUp) + dot(rightVector, preferredRight)
                                + TIE_BREAK_WEIGHT * dot(directionVector, preferredDirection);
                        if (score > bestScore + 1e-12) {
                            bestScore = score;
                            direction[view] = planeAxis;
                            signedDirection[view] = candidateDirectionSign;
                            up[view] = candidateUp;
                            signedUp[view] = candidateUpSign;
                        }
                    }
                }
            }
        }
        return new MprViewRig(direction, signedDirection, up, signedUp);
    }

    /**
     * 某视图的视线方向 d。
     */
    public double[] direction(MprCursorFrame frame, int view) {
        return scale(frame.basisAxis(directionAxis[view]), directionSign[view]);
    }

    /**
     * 某视图的屏幕上方 up。
     */
    public double[] up(MprCursorFrame frame, int view) {
        return scale(frame.basisAxis(upAxis[view]), upSign[view]);
    }

    /**
     * 某视图的屏幕右方（= d × up）。
     */
    public double[] right(MprCursorFrame frame, int view) {
        return cross(direction(frame, view), up(frame, view));
    }

    /**
     * 把向量投影到垂直于 {@code normal} 的平面并归一化（用于"滚动连续性"：把上一次的 up 连续地搬到新平面）。
     *
     * @return 单位向量；与 {@code normal} 接近平行（投影退化）时返回 {@code null}
     */
    public static double[] projectOntoPlane(double[] vector, double[] normal) {
        double[] projected = subtract(vector, scale(normal, dot(vector, normal)));
        double length = Math.sqrt(dot(projected, projected));
        if (length < 1e-6) {
            return null;
        }
        return scale(projected, 1.0 / length);
    }

    /**
     * 解剖"上"方向在该视图平面内的单位投影；退化时返回 {@code null}。
     */
    public double[] preferredUpProjection(MprCursorFrame frame, int view) {
        return projectOntoPlane(preferredUp(frame, view), direction(frame, view));
    }

    /**
     * 某视图与解剖习惯的贴合度（越大越好）：up 与"解剖上"、right 与"解剖右"方向的一致度。
     */
    public double score(MprCursorFrame frame, int view) {
        return dot(up(frame, view), preferredUp(frame, view))
                + dot(right(frame, view), preferredRight(frame, view))
                + TIE_BREAK_WEIGHT * dot(direction(frame, view), preferredDirection(frame, view));
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("MprViewRig{");
        for (int view = 0; view < VIEW_COUNT; view++) {
            if (view > 0) {
                builder.append(", ");
            }
            builder.append("v").append(view)
                    .append("(d=").append(sign(directionSign[view])).append(axisName(directionAxis[view]))
                    .append(", up=").append(sign(upSign[view])).append(axisName(upAxis[view])).append(')');
        }
        return builder.append('}').toString();
    }

    private void validate() {
        for (int view = 0; view < VIEW_COUNT; view++) {
            if (directionAxis[view] == upAxis[view]) {
                throw new IllegalArgumentException("视线与 up 不能同轴（视图 " + view + "）");
            }
            if (Math.abs(directionSign[view]) != 1.0 || Math.abs(upSign[view]) != 1.0) {
                throw new IllegalArgumentException("符号必须为 ±1（视图 " + view + "）");
            }
        }
    }

    /**
     * 解剖"上"方向：轴位取 −v（前方）、矢状/冠状取 +w（头侧）。
     */
    private static double[] preferredUp(MprCursorFrame frame, int view) {
        if (view == MprViewOrientation.VIEW_AXIAL) {
            return scale(frame.anatomicalAxis(AXIS_V), -1.0);
        }
        return frame.anatomicalAxis(AXIS_W);
    }

    /**
     * 解剖"右"方向（放射科习惯）：轴位/冠状取 +u（患者左）、矢状取 +v（后方）。
     */
    private static double[] preferredRight(MprCursorFrame frame, int view) {
        return view == MprViewOrientation.VIEW_SAGITTAL
                ? frame.anatomicalAxis(AXIS_V) : frame.anatomicalAxis(AXIS_U);
    }

    /**
     * 标准观察侧的视线方向（仅作同分时的次要项）。
     */
    private static double[] preferredDirection(MprCursorFrame frame, int view) {
        if (view == MprViewOrientation.VIEW_AXIAL) {
            return frame.anatomicalAxis(AXIS_W);
        }
        if (view == MprViewOrientation.VIEW_SAGITTAL) {
            return scale(frame.anatomicalAxis(AXIS_U), -1.0);
        }
        return frame.anatomicalAxis(AXIS_V);
    }

    private static String sign(double value) {
        return value > 0 ? "+" : "-";
    }

    private static String axisName(int axis) {
        if (axis == AXIS_U) {
            return "u";
        }
        return axis == AXIS_V ? "v" : "w";
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] subtract(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] scale(double[] vector, double factor) {
        return new double[]{vector[0] * factor, vector[1] * factor, vector[2] * factor};
    }
}
