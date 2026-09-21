package com.zlyd.mpr.geometry;

import java.util.Arrays;

import com.zlyd.mpr.dicom.VolumeGeometry;

/**
 * MPR 光标坐标系（cursor frame）：三个正交平面与唯一交点 C 的纯数学表示。
 *
 * <h2>含义</h2>
 * <ul>
 *   <li>正交右手基 {@code (u, v, w)}：初始等于体数据自身的 {@code axisX/Y/Z}；</li>
 *   <li>平面：矢状面 = {@code ⊥u}、冠状面 = {@code ⊥v}、轴位面 = {@code ⊥w}，
 *       各自用"沿法向相对体中心 M 的偏移"（mm）表示；</li>
 *   <li>交点 C（三平面唯一交点）：{@code C = M + a·u + b·v + c·w}，
 *       偏移与旧实现的索引语义对应（{@code a↔i}、{@code b↔j}、{@code c↔k}）。</li>
 * </ul>
 *
 * <h2>视图映射（固定约定，与方向标记/相机共用）</h2>
 * <table border="1">
 *   <caption>视图与平面法向、视线方向</caption>
 *   <tr><th>视图</th><th>平面法向</th><th>视线方向 d</th></tr>
 *   <tr><td>轴位</td><td>+w</td><td>+w（脚→头）</td></tr>
 *   <tr><td>矢状</td><td>+u</td><td>−u（患者左→右）</td></tr>
 *   <tr><td>冠状</td><td>+v</td><td>+v（前→后）</td></tr>
 * </table>
 *
 * <p>旋转规则：在视图 V 内顺时针拖动 θ ⇔ 把整个光标坐标系<b>绕该视图视线方向 d 按右手定则转 +θ</b>；
 * 旋转后重算偏移以保证 C 不动。因此三平面永远两两正交、C 恒定。</p>
 *
 * <p>本类为纯数学（不含 VTK/几何渲染），关键数学函数均有单元测试；用途见 {@link MprViewRig} 与
 * {@code MPR斜切设计.md}。</p>
 */
public final class MprCursorFrame {

    /** 轴索引：u（矢状面法向）。 */
    public static final int AXIS_U = 0;
    /** 轴索引：v（冠状面法向）。 */
    public static final int AXIS_V = 1;
    /** 轴索引：w（轴位面法向）。 */
    public static final int AXIS_W = 2;

    private static final int OFFSET_X = 0;
    private static final int OFFSET_Y = 1;
    private static final int OFFSET_Z = 2;

    private final double[][] basis;
    private final double[][] anatomical;
    private final double[] reference;
    private final double[] offsets;

    private MprCursorFrame(double[][] basis, double[][] anatomical, double[] reference, double[] offsets) {
        this.basis = copyBasis(basis);
        this.anatomical = copyBasis(anatomical);
        this.reference = Arrays.copyOf(reference, OFFSET_Z + 1);
        this.offsets = Arrays.copyOf(offsets, OFFSET_Z + 1);
    }

    /**
     * 初始状态：基 = 体数据解剖轴，三平面过体中心（偏移全 0）。
     *
     * @param geometry 体数据几何
     * @return 光标坐标系
     */
    public static MprCursorFrame initial(VolumeGeometry geometry) {
        double[][] basis = {geometry.getAxisX(), geometry.getAxisY(), geometry.getAxisZ()};
        return new MprCursorFrame(basis, basis, geometry.center(), new double[]{0.0, 0.0, 0.0});
    }

    /**
     * 由显式状态构造（供测试与反序列化使用）。
     */
    public static MprCursorFrame of(double[][] basis, double[][] anatomical, double[] reference,
                                    double[] offsets) {
        return new MprCursorFrame(basis, anatomical, reference, offsets);
    }

    /**
     * 某视图平面所垂直的轴索引（矢状→u、冠状→v、轴位→w）。
     */
    public static int planeAxis(int view) {
        if (view == MprViewOrientation.VIEW_SAGITTAL) {
            return AXIS_U;
        }
        if (view == MprViewOrientation.VIEW_CORONAL) {
            return AXIS_V;
        }
        if (view == MprViewOrientation.VIEW_AXIAL) {
            return AXIS_W;
        }
        throw new IllegalArgumentException("未知视图: " + view);
    }

    /**
     * 某视图的视线方向相对平面法向的符号（矢状为 −1，轴位/冠状为 +1）。
     */
    public static double directionSign(int view) {
        return planeAxis(view) == AXIS_U ? -1.0 : 1.0;
    }

    /**
     * 三平面唯一交点 C（患者坐标 mm）。
     */
    public double[] center() {
        double[] center = Arrays.copyOf(reference, reference.length);
        for (int axis = 0; axis < basis.length; axis++) {
            for (int component = 0; component < center.length; component++) {
                center[component] += offsets[axis] * basis[axis][component];
            }
        }
        return center;
    }

    /**
     * 某视图平面的法向（+u / +v / +w）。
     */
    public double[] axis(int view) {
        return copyVector(basis[planeAxis(view)]);
    }

    /**
     * 某视图的视线方向 d（= {@link #axis(int)} × {@link #directionSign(int)}）。
     */
    public double[] viewDirection(int view) {
        return scale(axis(view), directionSign(view));
    }

    /**
     * 某视图平面沿自身法向的偏移（mm，相对体中心）。
     */
    public double normalOffset(int view) {
        return offsets[planeAxis(view)];
    }

    /**
     * 三个偏移 {a, b, c}（mm，对应 u/v/w 轴；状态栏"沿法向偏移"用）。
     */
    public double[] getOffsets() {
        return Arrays.copyOf(offsets, offsets.length);
    }

    /**
     * 基向量（0=u、1=v、2=w）。
     */
    public double[] basisAxis(int index) {
        return copyVector(basis[index]);
    }

    /**
     * 解剖基向量（装载体数据时的基；用于"回正"与斜切角）。
     */
    public double[] anatomicalAxis(int index) {
        return copyVector(anatomical[index]);
    }

    /**
     * 绕某视图的视线方向按右手定则旋转 {@code radians}（屏幕顺时针为正），保持交点 C 不变。
     *
     * @param view 视图索引
     * @param radians 旋转角（弧度）
     * @return 新的光标坐标系
     */
    public MprCursorFrame rotate(int view, double radians) {
        double[] direction = viewDirection(view);
        double[][] rotated = {
                rotateVector(basis[AXIS_U], direction, radians),
                rotateVector(basis[AXIS_V], direction, radians),
                rotateVector(basis[AXIS_W], direction, radians)};
        double[] center = center();
        double[] rotatedOffsets = {
                dot(subtract(center, reference), rotated[AXIS_U]),
                dot(subtract(center, reference), rotated[AXIS_V]),
                dot(subtract(center, reference), rotated[AXIS_W])};
        return new MprCursorFrame(rotated, anatomical, reference, rotatedOffsets);
    }

    /**
     * 直接给定三个偏移（a/b/c，单位 mm，对应 u/v/w 轴）。
     */
    public MprCursorFrame withOffsets(double a, double b, double c) {
        return new MprCursorFrame(basis, anatomical, reference, new double[]{a, b, c});
    }

    /**
     * 某视图平面沿自身法向平移 {@code delta} mm（即翻层；正方向为法向正向）。
     */
    public MprCursorFrame withOffset(int view, double delta) {
        double[] moved = Arrays.copyOf(offsets, offsets.length);
        moved[planeAxis(view)] += delta;
        return new MprCursorFrame(basis, anatomical, reference, moved);
    }

    /**
     * 在被拖视图所在平面内移动交点 C：只改另两个轴的偏移，被拖视图自身偏移不变。
     *
     * @param view 被拖视图
     * @param world 目标点（患者坐标）
     * @return 新的光标坐标系
     */
    public MprCursorFrame moveCenter(int view, double[] world) {
        double[] relative = subtract(world, reference);
        double[] moved = Arrays.copyOf(offsets, offsets.length);
        int fixedAxis = planeAxis(view);
        for (int axis = 0; axis < moved.length; axis++) {
            if (axis != fixedAxis) {
                moved[axis] = dot(relative, basis[axis]);
            }
        }
        return new MprCursorFrame(basis, anatomical, reference, moved);
    }

    /**
     * 三平面相对解剖方向的偏离角（度，0~180；180 表示法向翻转、即从对侧看同一平面）。
     *
     * @return 长度 3 的数组，顺序与 {@link MprViewOrientation#VIEW_AXIAL} 等视图索引一致
     */
    public double[] obliquityDegrees() {
        double[] angles = new double[OFFSET_Z + 1];
        int[] views = {MprViewOrientation.VIEW_AXIAL, MprViewOrientation.VIEW_SAGITTAL,
                MprViewOrientation.VIEW_CORONAL};
        for (int index = 0; index < views.length; index++) {
            int axis = planeAxis(views[index]);
            angles[index] = Math.toDegrees(Math.acos(clamp(dot(basis[axis], anatomical[axis]))));
        }
        return angles;
    }

    /**
     * 三个平面是否（在容差内）与解剖平面重合（法向平行，**含翻转**：180° 视为对齐）。
     *
     * <p>用于判断"显示的是不是解剖轴位/矢状/冠状面"（例如斜切时是否允许测量）。</p>
     *
     * @param toleranceDegrees 容差（度）
     */
    public boolean isAxisAligned(double toleranceDegrees) {
        double limit = Math.cos(Math.toRadians(toleranceDegrees));
        for (int axis = 0; axis < basis.length; axis++) {
            if (Math.abs(dot(basis[axis], anatomical[axis])) < limit) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "MprCursorFrame{center=" + format(center())
                + ", offsets=" + format(offsets)
                + ", obliquity=" + format(obliquityDegrees()) + "}";
    }

    private static double[] rotateVector(double[] vector, double[] axis, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double[] cross = cross(axis, vector);
        double projection = dot(axis, vector) * (1.0 - cos);
        return new double[]{
                vector[0] * cos + cross[0] * sin + axis[0] * projection,
                vector[1] * cos + cross[1] * sin + axis[1] * projection,
                vector[2] * cos + cross[2] * sin + axis[2] * projection};
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

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    private static double[] copyVector(double[] vector) {
        return Arrays.copyOf(vector, vector.length);
    }

    private static double[][] copyBasis(double[][] basis) {
        double[][] copy = new double[basis.length][];
        for (int index = 0; index < basis.length; index++) {
            copy[index] = Arrays.copyOf(basis[index], basis[index].length);
        }
        return copy;
    }

    private static String format(double[] values) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(String.format("%.3f", values[index]));
        }
        return builder.append(']').toString();
    }
}
