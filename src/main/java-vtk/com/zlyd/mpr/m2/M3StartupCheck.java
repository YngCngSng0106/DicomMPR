package com.zlyd.mpr.m2;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JList;
import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.mpr.VtkMprView;
import com.zlyd.mpr.mpr.VtkStackView;
import com.zlyd.mpr.ui.MainFrame;
import com.zlyd.mpr.util.VtkNativeLoader;

/**
 * 启动默认载入冒烟测试：构造主窗口、触发默认载入，等待后台扫描结束后打印序列数。
 *
 * <p>须在含形如 {@code 3xxxxxxxx} 影像目录的工作目录下运行。</p>
 */
public final class M3StartupCheck {

    private static final Logger LOG = LogManager.getLogger(M3StartupCheck.class);
    private static final long TIMEOUT_MILLIS = 60_000L;
    private static final long POLL_MILLIS = 500L;

    static {
        VtkNativeLoader.load();
    }

    private M3StartupCheck() {
    }

    public static void main(String[] args) throws Exception {
        MainFrame frame = new MainFrame(new VtkStackView(), new VtkMprView());
        SwingUtilities.invokeAndWait(frame::loadStartupFolder);

        JList<?> list = findSeriesList(frame);
        if (list == null) {
            LOG.error("未找到序列列表控件");
            return;
        }
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (list.getModel().getSize() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MILLIS);
        }
        int size = list.getModel().getSize();
        LOG.info("默认载入序列数={}", size);
        if (size > 0) {
            LOG.info("首个序列={}", list.getModel().getElementAt(0));
        }
    }

    private static JList<?> findSeriesList(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JList) {
                return (JList<?>) child;
            }
            if (child instanceof Container) {
                JList<?> found = findSeriesList((Container) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
