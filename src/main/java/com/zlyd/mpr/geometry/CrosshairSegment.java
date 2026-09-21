package com.zlyd.mpr.geometry;

import java.util.Arrays;

/**
 * 十字线中的一条线段（世界坐标）。
 *
 * <p>十字线的每条中线都要在交点处留出空白，因此会被拆成两段，用 {@link #getSegment()} 区分。</p>
 */
public final class CrosshairSegment {

    /** 视图索引：轴位。 */
    public static final int VIEW_AXIAL = 0;
    /** 视图索引：矢状。 */
    public static final int VIEW_SAGITTAL = 1;
    /** 视图索引：冠状。 */
    public static final int VIEW_CORONAL = 2;

    private final int view;
    private final int line;
    private final int segment;
    private final double[] start;
    private final double[] end;
    private final boolean visible;

    public CrosshairSegment(int view, int line, int segment, double[] start, double[] end, boolean visible) {
        this.view = view;
        this.line = line;
        this.segment = segment;
        this.start = Arrays.copyOf(start, start.length);
        this.end = Arrays.copyOf(end, end.length);
        this.visible = visible;
    }

    public int getView() {
        return view;
    }

    public int getLine() {
        return line;
    }

    public int getSegment() {
        return segment;
    }

    public double[] getStart() {
        return Arrays.copyOf(start, start.length);
    }

    public double[] getEnd() {
        return Arrays.copyOf(end, end.length);
    }

    public boolean isVisible() {
        return visible;
    }
}
