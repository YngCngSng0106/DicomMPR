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
}
