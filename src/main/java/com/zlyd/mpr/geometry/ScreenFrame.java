package com.zlyd.mpr.geometry;

import java.util.Arrays;

/**
 * 视图的屏幕参考系（纯数学值对象）：相机焦点、屏幕右方/上方/视线方向，以及视口在**焦点平面**上的半宽高（世界单位）。
 *
 * <p>平行投影下"世界单位 ↔ 像素"的换算与深度无关，因此可以据此把十字线精确铺满整个视口矩形
 * （而不是裁到体数据包围盒——那样在斜切/偏心时会整条消失）。</p>
 */
public final class ScreenFrame {

    private static final int COMPONENTS = 3;

    private final double[] focal;
    private final double[] right;
    private final double[] up;
    private final double[] viewDirection;
    private final double halfWidth;
    private final double halfHeight;

    public ScreenFrame(double[] focal, double[] right, double[] up, double[] viewDirection,
                       double halfWidth, double halfHeight) {
        this.focal = Arrays.copyOf(focal, COMPONENTS);
        this.right = Arrays.copyOf(right, COMPONENTS);
        this.up = Arrays.copyOf(up, COMPONENTS);
        this.viewDirection = Arrays.copyOf(viewDirection, COMPONENTS);
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
    }

    public double[] getFocal() {
        return Arrays.copyOf(focal, focal.length);
    }

    public double[] getRight() {
        return Arrays.copyOf(right, right.length);
    }

    public double[] getUp() {
        return Arrays.copyOf(up, up.length);
    }

    public double[] getViewDirection() {
        return Arrays.copyOf(viewDirection, viewDirection.length);
    }

    /**
     * 视口半宽（世界单位，= parallelScale × 宽高比）。
     */
    public double getHalfWidth() {
        return halfWidth;
    }

    /**
     * 视口半高（世界单位，= parallelScale）。
     */
    public double getHalfHeight() {
        return halfHeight;
    }

    /**
     * 世界点 → 屏幕平面坐标（相对焦点，沿屏幕右/上方向，单位与世界一致）。
     */
    public double[] toScreen(double[] world) {
        double[] relative = Vectors.subtract(world, focal);
        return new double[]{Vectors.dot(relative, right), Vectors.dot(relative, up)};
    }
}
