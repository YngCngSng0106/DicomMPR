package com.zlyd.mpr.ui;

import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import com.zlyd.mpr.geometry.MeasurementType;

/**
 * 测量工具条（第 2 行）：测量工具下拉（含"删除选中/全部删除"两个动作项）、
 * `十字线/平移` 切换按钮、`重置`、`回正`、结果与提示标签。
 *
 * <p>下拉中只放测量工具与删除动作；"十字线/平移"作为独立按钮（按下表示处于十字线模式）。</p>
 */
public final class MeasurementToolbar extends JPanel {

    private static final long serialVersionUID = 1L;

    /** 动作项：删除选中。 */
    private static final String ACTION_DELETE_SELECTED = "删除选中";
    /** 动作项：全部删除。 */
    private static final String ACTION_DELETE_ALL = "全部删除";

    /** 工具条回调（统一接口，避免多个小接口）。 */
    public interface Listener {
        /** 选择测量工具；{@code null} 表示回到十字线/平移模式。 */
        void onToolSelected(MeasurementType type);

        /** 删除选中的测量。 */
        void onDeleteSelected();

        /** 全部删除。 */
        void onDeleteAll();

        /** 重置视图（等价 R）。 */
        void onResetView();

        /** 回正（等价 A）。 */
        void onAlignRig();
    }

    private final JComboBox<Object> toolBox = new JComboBox<>();
    private final JToggleButton crosshairButton = new JToggleButton("十字线/平移", true);
    private final JButton resetButton = new JButton("重置");
    private final JButton alignButton = new JButton("回正");
    private final JLabel resultLabel = new JLabel("-");
    private final JLabel hintLabel = new JLabel(" ");

    private Listener listener;
    private boolean updating;

    public MeasurementToolbar() {
        super(new FlowLayout(FlowLayout.LEFT, 6, 2));
        for (MeasurementType type : MeasurementType.values()) {
            toolBox.addItem(type);
        }
        toolBox.addItem(ACTION_DELETE_SELECTED);
        toolBox.addItem(ACTION_DELETE_ALL);
        toolBox.addActionListener(event -> notifyToolSelected());
        crosshairButton.setToolTipText("回到十字线/平移模式（也可按 Esc）");
        crosshairButton.addActionListener(event -> notifyCrosshairSelected());
        resetButton.setToolTipText("重置视图与光标（等价快捷键 R）");
        resetButton.addActionListener(event -> notify(listener -> listener.onResetView()));
        alignButton.setToolTipText("回正：把各视图摆回解剖习惯方向（等价快捷键 A）");
        alignButton.addActionListener(event -> notify(listener -> listener.onAlignRig()));

        add(new JLabel("测量:"));
        add(toolBox);
        add(crosshairButton);
        add(resetButton);
        add(alignButton);
        add(resultLabel);
        add(hintLabel);
        setCrosshairMode(true);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * 设置是否处于"十字线/平移"模式（仅更新按钮外观，不回调）。
     */
    public void setCrosshairMode(boolean crosshair) {
        updating = true;
        try {
            crosshairButton.setSelected(crosshair);
            if (crosshair) {
                toolBox.setSelectedIndex(-1);
            }
        } finally {
            updating = false;
        }
    }

    /**
     * 显示最近一次（或选中的）测量结果。
     */
    public void setResultText(String text) {
        updating = true;
        try {
            resultLabel.setText(text == null || text.isEmpty() ? "-" : text);
        } finally {
            updating = false;
        }
    }

    /**
     * 显示提示信息（例如"未选中测量"）。
     */
    public void setHintText(String text) {
        hintLabel.setText(text == null || text.isEmpty() ? " " : text);
    }

    private void notifyToolSelected() {
        if (updating || listener == null) {
            return;
        }
        Object selected = toolBox.getSelectedItem();
        if (ACTION_DELETE_SELECTED.equals(selected)) {
            setCrosshairMode(true);
            listener.onDeleteSelected();
            return;
        }
        if (ACTION_DELETE_ALL.equals(selected)) {
            setCrosshairMode(true);
            listener.onDeleteAll();
            return;
        }
        crosshairButton.setSelected(false);
        listener.onToolSelected(selected instanceof MeasurementType ? (MeasurementType) selected : null);
    }

    private void notifyCrosshairSelected() {
        if (updating || listener == null) {
            return;
        }
        if (crosshairButton.isSelected()) {
            updating = true;
            try {
                toolBox.setSelectedIndex(-1);
            } finally {
                updating = false;
            }
            listener.onToolSelected(null);
        } else {
            // 不允许"既不选工具也不是十字线"：取消按下即恢复十字线
            crosshairButton.setSelected(true);
        }
    }

    private void notify(java.util.function.Consumer<Listener> action) {
        if (listener != null) {
            action.accept(listener);
        }
    }
}
