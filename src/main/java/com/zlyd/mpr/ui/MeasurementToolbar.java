package com.zlyd.mpr.ui;

import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.zlyd.mpr.geometry.MeasurementType;

/**
 * 测量工具条：选择测量工具、清除测量、显示最近一次测量结果（F1/F2）。
 */
public final class MeasurementToolbar extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String TOOL_NONE = "十字线/平移";

    /** 切换测量工具；{@code null} 表示十字线模式。 */
    public interface ToolListener {
        void onToolSelected(MeasurementType type);
    }

    /** 清除所有测量。 */
    public interface ClearListener {
        void onClearRequested();
    }

    private final JComboBox<Object> toolBox = new JComboBox<>();
    private final JButton clearButton = new JButton("清除测量");
    private final JLabel resultLabel = new JLabel("-");
    private final JLabel warningLabel = new JLabel(" ");

    private ToolListener toolListener;
    private ClearListener clearListener;
    private boolean updating;

    public MeasurementToolbar() {
        super(new FlowLayout(FlowLayout.LEFT, 6, 2));
        toolBox.addItem(TOOL_NONE);
        for (MeasurementType type : MeasurementType.values()) {
            toolBox.addItem(type);
        }
        toolBox.addActionListener(event -> notifyToolSelected());
        clearButton.addActionListener(event -> notifyClear());

        add(new JLabel("测量:"));
        add(toolBox);
        add(clearButton);
        add(resultLabel);
        add(warningLabel);
    }

    public void setToolListener(ToolListener listener) {
        this.toolListener = listener;
    }

    public void setClearListener(ClearListener listener) {
        this.clearListener = listener;
    }

    /**
     * 设置斜切状态：斜切时禁用测量工具并提示（D2）。
     *
     * @param oblique 是否处于斜切（三个平面未与解剖平面重合）
     */
    public void setOblique(boolean oblique) {
        toolBox.setEnabled(!oblique);
        clearButton.setEnabled(!oblique);
        warningLabel.setText(oblique ? "斜切状态下暂不支持测量" : " ");
        if (oblique && toolBox.getSelectedItem() != TOOL_NONE) {
            updating = true;
            try {
                toolBox.setSelectedItem(TOOL_NONE);
            } finally {
                updating = false;
            }
        }
    }

    /**
     * 显示最近一次测量结果。
     */
    public void setResultText(String text) {
        updating = true;
        try {
            resultLabel.setText(text == null || text.isEmpty() ? "-" : text);
        } finally {
            updating = false;
        }
    }

    private void notifyToolSelected() {
        if (updating || toolListener == null) {
            return;
        }
        Object selected = toolBox.getSelectedItem();
        toolListener.onToolSelected(selected instanceof MeasurementType ? (MeasurementType) selected : null);
    }

    private void notifyClear() {
        if (clearListener != null) {
            clearListener.onClearRequested();
        }
    }
}
