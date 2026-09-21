package com.zlyd.mpr.m2;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.mpr.VtkMprView;
import com.zlyd.mpr.mpr.VtkStackView;
import com.zlyd.mpr.util.VtkNativeLoader;

/**
 * M3 UI 构建冒烟测试：构造两个视图并列出其工具条控件，确认按钮/下拉框存在（不显示窗口）。
 */
public final class M3UiSmoke {

    private static final Logger LOG = LogManager.getLogger(M3UiSmoke.class);

    static {
        VtkNativeLoader.load();
    }

    private M3UiSmoke() {
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            VtkMprView mprView = new VtkMprView();
            VtkStackView stackView = new VtkStackView();
            LOG.info("MPR 视图控件: {}", describe(mprView));
            LOG.info("2D 视图控件: {}", describe(stackView));
            LOG.info("MPR 工具条行: {}", describeToolbarRows(mprView));
            LOG.info("2D 工具条行: {}", describeToolbarRows(stackView));
        });
    }

    /**
     * 打印 BorderLayout.NORTH 工具条区里每一行的首选尺寸，确认各行都存在且不为 0。
     */
    private static String describeToolbarRows(Container container) {
        Component north = ((java.awt.BorderLayout) container.getLayout()).getLayoutComponent(java.awt.BorderLayout.NORTH);
        if (!(north instanceof Container)) {
            return "无工具条区";
        }
        List<String> rows = new ArrayList<>();
        for (Component row : ((Container) north).getComponents()) {
            rows.add(row.getPreferredSize().width + "x" + row.getPreferredSize().height);
        }
        return "行数=" + rows.size() + " 尺寸=" + rows;
    }

    private static String describe(Container container) {
        List<String> controls = new ArrayList<>();
        walk(container, controls);
        return controls.toString();
    }

    private static void walk(Container container, List<String> out) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton) {
                out.add("按钮[" + ((JButton) child).getText() + "]");
            } else if (child instanceof JComboBox) {
                JComboBox<?> combo = (JComboBox<?>) child;
                List<String> items = new ArrayList<>();
                for (int index = 0; index < combo.getItemCount(); index++) {
                    items.add(String.valueOf(combo.getItemAt(index)));
                }
                out.add("下拉" + items);
            }
            if (child instanceof Container) {
                walk((Container) child, out);
            }
        }
    }
}
