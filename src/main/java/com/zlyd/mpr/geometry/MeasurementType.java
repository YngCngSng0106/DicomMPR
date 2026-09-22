package com.zlyd.mpr.geometry;

/**
 * 测量类型（F1/F2）。
 *
 * <p>点数约定：{@code pointCount > 0} 为"固定点数、点满即完成"；{@code 0} 表示点数不固定
 * （{@link #CURVE} 折线多点、{@link #FREEHAND} 自由形状拖动描画）。</p>
 */
public enum MeasurementType {

    /** 线段：2 点，输出长度。 */
    LENGTH("线段", 2),
    /** 矩形 ROI：2 点（对角），输出面积与 HU 统计。 */
    RECT_ROI("矩形", 2),
    /** 椭圆 ROI：2 点（外接矩形对角），输出面积与 HU 统计。 */
    ELLIPSE_ROI("椭圆", 2),
    /** 角度：3 点（第 2 点为顶点）。 */
    ANGLE("角度", 3),
    /** 曲线（折线）：多点，双击/Enter 结束，输出总长度。 */
    CURVE("曲线", 0),
    /** 自由形状：按住拖动描画、松开闭合，输出面积 + 周长 + HU 统计。 */
    FREEHAND("自由形状", 0);

    private final String displayName;
    private final int pointCount;

    MeasurementType(String displayName, int pointCount) {
        this.displayName = displayName;
        this.pointCount = pointCount;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 需要的点数；{@code 0} 表示点数不固定（折线/自由形状）。
     */
    public int getPointCount() {
        return pointCount;
    }

    /**
     * 是否需要统计 HU（矩形/椭圆/自由形状）。
     */
    public boolean isRoi() {
        return this == RECT_ROI || this == ELLIPSE_ROI || this == FREEHAND;
    }

    /**
     * 是否为"多点折线"（点数不固定、逐点累加、需显式结束）。
     */
    public boolean isPolyline() {
        return this == CURVE;
    }

    /**
     * 是否为"拖动描画"（按住左键描画轮廓、松开即闭合）。
     */
    public boolean isFreehand() {
        return this == FREEHAND;
    }

    /**
     * 是否点数固定（点满即自动完成）。
     */
    public boolean hasFixedPointCount() {
        return pointCount > 0;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
