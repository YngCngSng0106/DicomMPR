package com.zlyd.mpr.geometry;

/**
 * 感兴趣区（ROI）内的体素值统计结果（CT 为 HU）。
 */
public final class RoiStatistics {

    private final int count;
    private final double min;
    private final double max;
    private final double mean;
    private final double standardDeviation;

    private RoiStatistics(int count, double min, double max, double mean, double standardDeviation) {
        this.count = count;
        this.min = min;
        this.max = max;
        this.mean = mean;
        this.standardDeviation = standardDeviation;
    }

    /**
     * 统计一组体素值。
     *
     * @param values 体素值数组
     * @param length 参与统计的个数
     * @return 统计结果；无有效值时返回全 0 且 count=0
     */
    public static RoiStatistics of(double[] values, int length) {
        if (values == null || length <= 0) {
            return new RoiStatistics(0, 0.0, 0.0, 0.0, 0.0);
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0.0;
        for (int index = 0; index < length; index++) {
            double value = values[index];
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
        }
        double mean = sum / length;
        double squareSum = 0.0;
        for (int index = 0; index < length; index++) {
            double delta = values[index] - mean;
            squareSum += delta * delta;
        }
        return new RoiStatistics(length, min, max, mean, Math.sqrt(squareSum / length));
    }

    public int getCount() {
        return count;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getMean() {
        return mean;
    }

    public double getStandardDeviation() {
        return standardDeviation;
    }
}
