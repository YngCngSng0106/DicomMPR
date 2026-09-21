package com.zlyd.mpr.ui;

import javax.swing.JComponent;

import com.zlyd.mpr.dicom.SeriesInfo;

/**
 * 右侧视图区的抽象：接收一个序列并显示。
 *
 * <p>由 VTK 实现（M2.1/M3），主界面只依赖本接口，从而无需在编译期依赖 VTK。</p>
 */
public interface SeriesView {

    /**
     * @return 用于放入界面的 Swing 组件
     */
    JComponent getComponent();

    /**
     * 显示指定序列。
     *
     * @param series 序列
     */
    void showSeries(SeriesInfo series);

    /**
     * 注册"请求关闭本视图页面"的回调（例如视图清空自身缓存后希望切回默认页面）。
     *
     * <p>默认不做处理；实现类可覆盖。放在本接口上是为了让 {@code MainFrame}（不含 VTK 的模块）
     * 也能在不知道具体实现类型的情况下接线。</p>
     *
     * @param listener 回调（在 EDT 执行）
     */
    default void setCloseRequestListener(Runnable listener) {
    }
}
