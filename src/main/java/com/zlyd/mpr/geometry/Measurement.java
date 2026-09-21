package com.zlyd.mpr.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一条测量结果：类型、所在视图、端点（患者坐标）、数值与可选的 ROI 统计。
 */
public final class Measurement {

    private final MeasurementType type;
    private final int view;
    private final List<double[]> points;
    private final double value;
    private final RoiStatistics statistics;

    /**
     * @param type 测量类型
     * @param view 视图索引
     * @param points 端点（患者坐标，mm）
     * @param value 数值：长度 mm / 角度 度 / ROI 面积 mm²
     * @param statistics ROI 统计；非 ROI 类型为 {@code null}
     */
    public Measurement(MeasurementType type, int view, List<double[]> points,
                       double value, RoiStatistics statistics) {
        this.type = type;
        this.view = view;
        this.points = Collections.unmodifiableList(copyPoints(points));
        this.value = value;
        this.statistics = statistics;
    }

    public MeasurementType getType() {
        return type;
    }

    public int getView() {
        return view;
    }

    public List<double[]> getPoints() {
        return points;
    }

    public double getValue() {
        return value;
    }

    public RoiStatistics getStatistics() {
        return statistics;
    }

    private static List<double[]> copyPoints(List<double[]> source) {
        List<double[]> copy = new ArrayList<>(source.size());
        for (double[] point : source) {
            copy.add(new double[]{point[0], point[1], point[2]});
        }
        return copy;
    }
}
