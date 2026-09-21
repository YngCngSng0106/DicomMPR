package com.zlyd.mpr.geometry;

import com.zlyd.mpr.dicom.VolumeGeometry;

/**
 * MPR 各视图的显示方向约定（纯数学，供相机与方向标记共用，保证二者一致）。
 *
 * <h2>放射科约定</h2>
 * <ul>
 *   <li><b>轴位</b>：从脚往头看（法向 +Z），屏幕上方=前方，屏幕右方=患者左（即患者右在图像左侧）；</li>
 *   <li><b>冠状</b>：从前往后看（法向 +Y），屏幕上方=头侧，屏幕右方=患者左；</li>
 *   <li><b>矢状</b>：从患者左侧往右看（法向 −X），屏幕上方=头侧，屏幕右方=后方（前方在图像左侧）。</li>
 * </ul>
 *
 * <p>其中"屏幕右方 = 法向 × 上方"（叉积）。解剖轴向含义（LPS）：
 * +X=患者左(L)、−X=患者右(R)、+Y=患者后(P)、−Y=患者前(A)、+Z=头侧(H)、−Z=脚侧(F)。</p>
 */
public final class MprViewOrientation {

    /** 视图索引：轴位。 */
    public static final int VIEW_AXIAL = CrosshairSegment.VIEW_AXIAL;
    /** 视图索引：矢状。 */
    public static final int VIEW_SAGITTAL = CrosshairSegment.VIEW_SAGITTAL;
    /** 视图索引：冠状。 */
    public static final int VIEW_CORONAL = CrosshairSegment.VIEW_CORONAL;

    /** 标签个数与顺序：上、下、左、右。 */
    public static final int LABEL_COUNT = 4;

    private static final String LABEL_HEAD = "H";
    private static final String LABEL_FEET = "F";
    private static final String LABEL_ANTERIOR = "A";
    private static final String LABEL_POSTERIOR = "P";
    private static final String LABEL_LEFT = "L";
    private static final String LABEL_RIGHT = "R";

    private MprViewOrientation() {
    }

    /**
     * 视图法向量（相机视线方向）：等价于"解剖轴对齐帧 + 连续性机架"。
     */
    public static double[] normal(VolumeGeometry geometry, int view) {
        return MprViewRig.continuity()
                .direction(MprCursorFrame.initial(geometry), view);
    }

    /**
     * 视图屏幕上方对应的世界方向（同上，来自连续性机架）。
     */
    public static double[] up(VolumeGeometry geometry, int view) {
        return MprViewRig.continuity()
                .up(MprCursorFrame.initial(geometry), view);
    }

    /**
     * 视图屏幕右方对应的世界方向（= 视线 × 上方）。
     */
    public static double[] screenRight(VolumeGeometry geometry, int view) {
        return cross(normal(geometry, view), up(geometry, view));
    }

    /**
     * 由"屏幕上方/屏幕右方"两个世界向量给出四边解剖标记（斜切时取主导轴，可能是近似值）。
     *
     * @return 长度 4 的数组：上、下、左、右
     */
    public static String[] edgeLabels(double[] upVector, double[] rightVector) {
        String top = label(upVector);
        String right = label(rightVector);
        return new String[]{top, opposite(top), opposite(right), right};
    }

    /**
     * 视图四边的解剖标记（轴对齐便捷入口）。
     *
     * @return 长度 4 的数组：上、下、左、右
     */
    public static String[] edgeLabels(VolumeGeometry geometry, int view) {
        return edgeLabels(up(geometry, view), screenRight(geometry, view));
    }

    /**
     * 世界方向 → 解剖字母（取分量绝对值最大的轴向）。
     */
    public static String label(double[] direction) {
        int axis = 0;
        for (int index = 1; index < 3; index++) {
            if (Math.abs(direction[index]) > Math.abs(direction[axis])) {
                axis = index;
            }
        }
        boolean positive = direction[axis] >= 0;
        if (axis == 0) {
            return positive ? LABEL_LEFT : LABEL_RIGHT;
        }
        if (axis == 1) {
            return positive ? LABEL_POSTERIOR : LABEL_ANTERIOR;
        }
        return positive ? LABEL_HEAD : LABEL_FEET;
    }

    /**
     * 相反的解剖字母。
     */
    public static String opposite(String label) {
        if (LABEL_HEAD.equals(label)) {
            return LABEL_FEET;
        }
        if (LABEL_FEET.equals(label)) {
            return LABEL_HEAD;
        }
        if (LABEL_ANTERIOR.equals(label)) {
            return LABEL_POSTERIOR;
        }
        if (LABEL_POSTERIOR.equals(label)) {
            return LABEL_ANTERIOR;
        }
        return LABEL_LEFT.equals(label) ? LABEL_RIGHT : LABEL_LEFT;
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private static double[] negate(double[] vector) {
        return new double[]{-vector[0], -vector[1], -vector[2]};
    }
}
