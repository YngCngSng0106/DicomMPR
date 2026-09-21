package com.zlyd.mpr.ui;

import javax.swing.ImageIcon;

import org.apache.commons.lang3.StringUtils;

import com.zlyd.mpr.dicom.SeriesAttributes;
import com.zlyd.mpr.dicom.SeriesGeometry;
import com.zlyd.mpr.dicom.SeriesInfo;

/**
 * 列表中的序列条目：序列 + 缩略图 + 显示文本。
 */
public final class SeriesEntry {

    private final SeriesInfo series;
    private final ImageIcon thumbnail;
    private final String title;
    private final String subtitle;

    public SeriesEntry(SeriesInfo series, ImageIcon thumbnail) {
        this.series = series;
        this.thumbnail = thumbnail;
        this.title = buildTitle(series);
        this.subtitle = buildSubtitle(series);
    }

    public SeriesInfo getSeries() {
        return series;
    }

    public ImageIcon getThumbnail() {
        return thumbnail;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    @Override
    public String toString() {
        return title + " | " + subtitle;
    }

    private static String buildTitle(SeriesInfo series) {
        SeriesAttributes attributes = series.getAttributes();
        return String.format("Series %d  [%s]  %s",
                attributes.getSeriesNumber(),
                StringUtils.defaultIfBlank(attributes.getModality(), "-"),
                StringUtils.defaultIfBlank(attributes.getSeriesDescription(), "-"));
    }

    private static String buildSubtitle(SeriesInfo series) {
        SeriesGeometry geometry = series.getGeometry();
        StringBuilder builder = new StringBuilder();
        builder.append(series.getSliceCount()).append(" 层");
        if (geometry.getRows() > 0) {
            builder.append("  •  ").append(geometry.getColumns()).append('x').append(geometry.getRows());
        }
        if (geometry.getSliceSpacing() > 0) {
            builder.append(String.format("  •  层距 %.2fmm", geometry.getSliceSpacing()));
        }
        if (!geometry.isValid()) {
            builder.append("  ⚠ 不可重建");
        }
        return builder.toString();
    }
}
