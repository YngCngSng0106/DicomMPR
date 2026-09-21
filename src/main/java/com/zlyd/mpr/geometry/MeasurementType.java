package com.zlyd.mpr.geometry;

/**
 * 测量类型及其所需点数。
 */
public enum MeasurementType {

    /** 长度：两点，单位 mm。 */
    LENGTH("长度", 2),
    /** 角度：三点（第二点为顶点），单位度。 */
    ANGLE("角度", 3),
    /** 矩形 ROI：两个对角点，输出面积与 HU 统计。 */
    RECT_ROI("矩形 ROI", 2),
    /** 椭圆 ROI：外接矩形的两个对角点，输出面积与 HU 统计。 */
    ELLIPSE_ROI("椭圆 ROI", 2);

    private final String displayName;
    private final int pointCount;

    MeasurementType(String displayName, int pointCount) {
        this.displayName = displayName;
        this.pointCount = pointCount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getPointCount() {
        return pointCount;
    }

    public boolean isRoi() {
        return this == RECT_ROI || this == ELLIPSE_ROI;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
