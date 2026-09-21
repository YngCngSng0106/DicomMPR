package com.zlyd.mpr.ui;

import java.awt.BorderLayout;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.apache.commons.lang3.StringUtils;

import com.zlyd.mpr.dicom.SeriesInfo;

/**
 * 占位视图：只显示序列信息文本（无 VTK 时使用）。
 */
public final class PlaceholderSeriesView implements SeriesView {

    private final JPanel panel = new JPanel(new BorderLayout());
    private final JLabel label = new JLabel("", SwingConstants.CENTER);

    public PlaceholderSeriesView() {
        panel.add(label, BorderLayout.CENTER);
        showSeries(null);
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }

    @Override
    public void showSeries(SeriesInfo series) {
        if (series == null) {
            label.setText("请点击左侧「选择 DICOM 文件夹…」，然后双击或拖拽序列到此处");
            return;
        }
        StringBuilder html = new StringBuilder("<html><div style='text-align:center;font-family:sans-serif;'>");
        html.append("<h2>").append(escape(series.getAttributes().getSeriesDescription())).append("</h2>");
        html.append("<p>Modality: ").append(escape(series.getAttributes().getModality())).append("</p>");
        html.append("<p>层数: ").append(series.getSliceCount()).append("</p>");
        html.append("<p>尺寸: ").append(series.getGeometry().getColumns())
                .append('x').append(series.getGeometry().getRows()).append("</p>");
        html.append("</div></html>");
        label.setText(html.toString());
    }

    private static String escape(String value) {
        return StringUtils.defaultIfBlank(value, "-")
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
