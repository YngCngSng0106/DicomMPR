package com.zlyd.mpr.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

/**
 * 序列列表的单元渲染：左侧缩略图，右侧标题与副标题。
 */
public final class SeriesListCellRenderer extends JPanel implements ListCellRenderer<SeriesEntry> {

    private static final long serialVersionUID = 1L;

    private final JLabel thumbnailLabel = new JLabel();
    private final JLabel titleLabel = new JLabel();
    private final JLabel subtitleLabel = new JLabel();

    public SeriesListCellRenderer() {
        setLayout(new BorderLayout(8, 0));
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        thumbnailLabel.setPreferredSize(new Dimension(96, 96));
        thumbnailLabel.setHorizontalAlignment(JLabel.CENTER);

        titleLabel.setFont(titleLabel.getFont().deriveFont(titleLabel.getFont().getStyle() | java.awt.Font.BOLD));
        subtitleLabel.setForeground(Color.GRAY);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        textPanel.add(Box.createVerticalGlue());
        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(subtitleLabel);
        textPanel.add(Box.createVerticalGlue());

        add(thumbnailLabel, BorderLayout.WEST);
        add(textPanel, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends SeriesEntry> list, SeriesEntry value,
                                                  int index, boolean isSelected, boolean cellHasFocus) {
        thumbnailLabel.setIcon(value.getThumbnail());
        titleLabel.setText(value.getTitle());
        subtitleLabel.setText(value.getSubtitle());

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
            titleLabel.setForeground(list.getSelectionForeground());
            subtitleLabel.setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
            titleLabel.setForeground(list.getForeground());
            subtitleLabel.setForeground(Color.GRAY);
        }
        setOpaque(true);
        return this;
    }
}
