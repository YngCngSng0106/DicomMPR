package com.zlyd.mpr.util;

import com.zlyd.mpr.geometry.Measurement;
import com.zlyd.mpr.geometry.MeasurementCalculator;
import com.zlyd.mpr.geometry.RoiStatistics;

/**
 * 测量结果的文本格式化（画布标签与工具条共用）。
 */
public final class MeasurementFormatter {

    private static final double SQUARE_MILLIMETERS_PER_SQUARE_CENTIMETER = 100.0;

    private MeasurementFormatter() {
    }

    /**
     * 单行摘要。
     */
    public static String format(Measurement measurement) {
        switch (measurement.getType()) {
            case LENGTH:
                return String.format("长度 %.1f mm", measurement.getValue());
            case ANGLE:
                return String.format("角度 %.1f°", measurement.getValue());
            case CURVE:
                return String.format("曲线 %.1f mm (%d 点)", measurement.getValue(),
                        measurement.getPoints().size());
            case FREEHAND:
                return formatFreehand(measurement);
            default:
                return formatRoi(measurement);
        }
    }

    private static String formatRoi(Measurement measurement) {
        StringBuilder text = new StringBuilder();
        text.append(String.format("面积 %.2f cm²",
                measurement.getValue() / SQUARE_MILLIMETERS_PER_SQUARE_CENTIMETER));
        RoiStatistics statistics = measurement.getStatistics();
        if (statistics != null && statistics.getCount() > 0) {
            text.append(String.format(" | HU %.0f (min %.0f, max %.0f, n=%d)",
                    statistics.getMean(), statistics.getMin(),
                    statistics.getMax(), statistics.getCount()));
        }
        return text.toString();
    }

    /**
     * 自由形状：面积 + 周长 + HU 统计（周长由顶点序列实时计算，无需额外存储）。
     */
    private static String formatFreehand(Measurement measurement) {
        StringBuilder text = new StringBuilder(formatRoi(measurement));
        text.insert(0, String.format("周长 %.1f mm | ", MeasurementCalculator.polygonPerimeter(
                measurement.getPoints())));
        return text.toString();
    }
}
