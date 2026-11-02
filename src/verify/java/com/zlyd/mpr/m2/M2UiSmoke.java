package com.zlyd.mpr.m2;

import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.ui.MainFrame;

/**
 * M2 UI 构建冒烟测试：仅构造并销毁主窗口，不显示、不阻塞。
 */
public final class M2UiSmoke {

    private static final Logger LOG = LogManager.getLogger(M2UiSmoke.class);

    private M2UiSmoke() {
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            frame.pack();
            frame.setVisible(false);
            frame.dispose();
        });
        LOG.info("MainFrame 构建成功（未显示）");
    }
}
