package com.zlyd.mpr.ui;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * 序列右键菜单。
 */
final class SeriesContextMenu {

    private SeriesContextMenu() {
    }

    /**
     * 创建菜单。
     *
     * @param onMprSelected 选择「MPR」时的动作
     * @return 弹出菜单
     */
    static JPopupMenu create(Runnable onMprSelected) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem mprItem = new JMenuItem("MPR（三视图）");
        mprItem.addActionListener(event -> onMprSelected.run());
        menu.add(mprItem);
        return menu;
    }
}
