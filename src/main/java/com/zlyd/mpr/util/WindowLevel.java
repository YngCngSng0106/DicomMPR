package com.zlyd.mpr.util;

/**
 * 窗宽窗位值对象。
 */
public final class WindowLevel {

    private final double width;
    private final double center;

    public WindowLevel(double width, double center) {
        this.width = width;
        this.center = center;
    }

    public double getWidth() {
        return width;
    }

    public double getCenter() {
        return center;
    }

    public double getLowerBound() {
        return center - width / 2.0;
    }

    public double getUpperBound() {
        return center + width / 2.0;
    }
}
