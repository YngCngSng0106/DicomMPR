package com.zlyd.mpr.ui.menu;

/**
 * 环形菜单的功能回调：菜单只负责布局与显隐，具体业务由宿主实现。
 */
@FunctionalInterface
public interface MprMenuListener {

    /**
     * 用户点击了某个叶子功能项（子菜单入口不会触发）。
     *
     * @param action 被点击的功能项
     */
    void onMenuAction(MprMenuAction action);
}
