package com.zlyd.mpr.ui;

import java.awt.FlowLayout;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.zlyd.mpr.util.WindowLevel;
import com.zlyd.mpr.util.WindowLevelPresets;

/**
 * 窗宽窗位工具条：预设下拉框 + 当前值显示（E1）。
 */
public final class WindowLevelToolbar extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String CURRENT_FORMAT = "W %.0f / L %.0f";

    /** 选择预设时的回调。 */
    public interface WindowLevelListener {
        void onWindowLevelSelected(WindowLevel windowLevel);
    }

    private final JComboBox<WindowLevelPresets.Preset> presetBox = new JComboBox<>();
    private final JLabel currentLabel = new JLabel();
    private WindowLevelListener listener;
    private boolean updating;

    public WindowLevelToolbar() {
        super(new FlowLayout(FlowLayout.LEFT, 6, 2));
        for (WindowLevelPresets.Preset preset : WindowLevelPresets.all()) {
            presetBox.addItem(preset);
        }
        presetBox.addActionListener(event -> notifySelection());
        add(presetBox);
        add(currentLabel);
        updateCurrentLabel(null);
    }

    public void setWindowLevelListener(WindowLevelListener listener) {
        this.listener = listener;
    }

    /**
     * 更新"当前值"显示（外部交互调整时调用）。
     */
    public void setCurrent(WindowLevel windowLevel) {
        updating = true;
        try {
            updateCurrentLabel(windowLevel);
        } finally {
            updating = false;
        }
    }

    private void notifySelection() {
        if (updating || listener == null) {
            return;
        }
        WindowLevelPresets.Preset preset = (WindowLevelPresets.Preset) presetBox.getSelectedItem();
        if (preset != null) {
            updateCurrentLabel(preset.getWindowLevel());
            listener.onWindowLevelSelected(preset.getWindowLevel());
        }
    }

    private void updateCurrentLabel(WindowLevel windowLevel) {
        currentLabel.setText(windowLevel == null ? "-"
                : String.format(CURRENT_FORMAT, windowLevel.getWidth(), windowLevel.getCenter()));
    }
}
