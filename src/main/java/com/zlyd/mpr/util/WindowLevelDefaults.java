package com.zlyd.mpr.util;

import org.apache.commons.lang3.StringUtils;

import com.zlyd.mpr.dicom.PixelAttributes;
import com.zlyd.mpr.dicom.SeriesInfo;

/**
 * 窗宽窗位的取用与回退规则，以及到显示灰度的映射。
 */
public final class WindowLevelDefaults {

    private static final double CT_DEFAULT_WIDTH = 400.0;
    private static final double CT_DEFAULT_CENTER = 40.0;
    private static final double GENERIC_WIDTH = 256.0;
    private static final double GENERIC_CENTER = 128.0;
    private static final int DISPLAY_MAX = 255;

    private WindowLevelDefaults() {
    }

    /**
     * 取序列窗宽窗位；缺失时按模态回退。
     *
     * @param series 序列
     * @return 窗宽窗位
     */
    public static WindowLevel forSeries(SeriesInfo series) {
        PixelAttributes pixels = series.getAttributes().getPixelAttributes();
        Double center = pixels.getWindowCenter();
        Double width = pixels.getWindowWidth();
        if (center != null && width != null && width > 0) {
            return new WindowLevel(width, center);
        }
        if (StringUtils.equalsIgnoreCase(series.getAttributes().getModality(), "CT")) {
            return new WindowLevel(CT_DEFAULT_WIDTH, CT_DEFAULT_CENTER);
        }
        return new WindowLevel(GENERIC_WIDTH, GENERIC_CENTER);
    }

    /**
     * 将物理值映射为 0~255 显示灰度。
     *
     * @param value 物理值（如 HU）
     * @param windowLevel 窗宽窗位
     * @return 0~255
     */
    public static int toDisplayValue(double value, WindowLevel windowLevel) {
        if (value <= windowLevel.getLowerBound()) {
            return 0;
        }
        if (value >= windowLevel.getUpperBound()) {
            return DISPLAY_MAX;
        }
        return (int) Math.round((value - windowLevel.getLowerBound()) / windowLevel.getWidth() * DISPLAY_MAX);
    }
}
