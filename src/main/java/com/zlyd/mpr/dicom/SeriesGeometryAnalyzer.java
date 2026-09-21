package com.zlyd.mpr.dicom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.commons.collections4.CollectionUtils;

/**
 * 序列几何分析：法向量、切片排序、层间距与一致性校验。
 *
 * <p>只负责几何计算；分组由 {@link SeriesGrouper} 负责。</p>
 */
public final class SeriesGeometryAnalyzer {

    /** 判定朝向一致的方向余弦点积阈值。 */
    private static final double ORIENTATION_TOLERANCE = 0.999;
    /** 判定层间距一致的相对容差。 */
    private static final double SPACING_REL_TOLERANCE = 0.01;
    /** 判定存在丢层的层距倍数阈值。 */
    private static final double GAP_FACTOR = 1.5;
    private static final double SPACING_MIN_TOLERANCE = 0.001;

    /**
     * 分析并排序切片（会就地对列表排序并写入投影位置）。
     *
     * @param slices 同序列切片
     * @param multiFrameCount 该序列最大帧数
     * @return 几何信息与校验结果
     */
    public SeriesGeometry analyze(List<SliceInfo> slices, int multiFrameCount) {
        SeriesGeometry.Builder builder = new SeriesGeometry.Builder();
        List<String> warnings = new ArrayList<>();

        SliceInfo reference = slices.get(0);
        builder.dimension(reference.getRows(), reference.getColumns());

        if (multiFrameCount > 1) {
            warnings.add("包含多帧对象 (NumberOfFrames=" + multiFrameCount + ")，MVP 暂不支持");
        }
        if (!allSlicesHaveGeometry(slices)) {
            warnings.add("缺少 ImagePositionPatient/ImageOrientationPatient/PixelSpacing，无法构建体数据");
            return finish(builder, false, warnings);
        }
        if (slices.size() < 2) {
            warnings.add("仅 " + slices.size() + " 层，无法重建体数据");
            return finish(builder, false, warnings);
        }

        builder.pixelSpacing(reference.getPixelSpacing());
        double[] orientation = reference.getImageOrientationPatient();
        double[] normal = normalize(cross(rowOf(orientation), columnOf(orientation)));
        builder.orientation(orientation).normal(normal);

        boolean orientationConsistent = checkOrientation(slices, orientation);
        if (!orientationConsistent) {
            warnings.add("切片朝向不一致");
        }
        builder.orientationConsistent(orientationConsistent);

        sortByProjection(slices, normal);
        analyzeSpacing(slices, builder, warnings);
        boolean valid = multiFrameCount <= 1;
        return finish(builder, valid, warnings);
    }

    private SeriesGeometry finish(SeriesGeometry.Builder builder, boolean valid, List<String> warnings) {
        String warning = CollectionUtils.isEmpty(warnings) ? null : String.join("; ", warnings);
        return builder.valid(valid).warning(warning).build();
    }

    private boolean allSlicesHaveGeometry(List<SliceInfo> slices) {
        for (SliceInfo slice : slices) {
            if (!slice.hasGeometry()) {
                return false;
            }
        }
        return true;
    }

    private boolean checkOrientation(List<SliceInfo> slices, double[] reference) {
        for (SliceInfo slice : slices) {
            if (dot(slice.getImageOrientationPatient(), reference) < ORIENTATION_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private void sortByProjection(List<SliceInfo> slices, double[] normal) {
        for (SliceInfo slice : slices) {
            slice.setProjectedPosition(dot(slice.getImagePositionPatient(), normal));
        }
        slices.sort(Comparator.comparingDouble(SliceInfo::getProjectedPosition));
    }

    private void analyzeSpacing(List<SliceInfo> slices, SeriesGeometry.Builder builder, List<String> warnings) {
        double[] differences = new double[slices.size() - 1];
        for (int index = 1; index < slices.size(); index++) {
            differences[index - 1] = slices.get(index).getProjectedPosition()
                    - slices.get(index - 1).getProjectedPosition();
        }

        double median = median(differences);
        double min = Arrays.stream(differences).min().orElse(0.0);
        double max = Arrays.stream(differences).max().orElse(0.0);
        double tolerance = Math.max(SPACING_MIN_TOLERANCE, Math.abs(median) * SPACING_REL_TOLERANCE);

        builder.sliceSpacing(Math.abs(median));
        boolean consistent = (max - min) <= tolerance;
        builder.spacingConsistent(consistent);
        if (!consistent) {
            warnings.add(String.format(Locale.ROOT,
                    "层间距不一致 (min=%.3f, max=%.3f, 典型=%.3f)", min, max, median));
        }

        boolean hasGap = max > Math.abs(median) * GAP_FACTOR;
        builder.hasGap(hasGap);
        if (hasGap) {
            warnings.add(String.format(Locale.ROOT,
                    "疑似丢层：最大层距 %.3f 明显大于典型 %.3f", max, median));
        }
    }

    private static double[] rowOf(double[] orientation) {
        return new double[]{orientation[0], orientation[1], orientation[2]};
    }

    private static double[] columnOf(double[] orientation) {
        return new double[]{orientation[3], orientation[4], orientation[5]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private static double dot(double[] a, double[] b) {
        int length = Math.min(a.length, b.length);
        double sum = 0.0;
        for (int index = 0; index < length; index++) {
            sum += a[index] * b[index];
        }
        return sum;
    }

    private static double[] normalize(double[] vector) {
        double length = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
        if (length == 0.0) {
            return vector;
        }
        return new double[]{vector[0] / length, vector[1] / length, vector[2] / length};
    }

    private static double median(double[] values) {
        double[] copy = values.clone();
        Arrays.sort(copy);
        int size = copy.length;
        if (size % 2 == 1) {
            return copy[size / 2];
        }
        return (copy[size / 2 - 1] + copy[size / 2]) / 2.0;
    }
}
