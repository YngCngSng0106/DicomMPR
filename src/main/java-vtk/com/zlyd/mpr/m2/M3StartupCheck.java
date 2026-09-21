package com.zlyd.mpr.m2;

import java.awt.Component;
import java.awt.Container;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JList;
import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.mpr.VtkMprView;
import com.zlyd.mpr.mpr.VtkStackView;
import com.zlyd.mpr.ui.MainFrame;
import com.zlyd.mpr.util.VtkNativeLoader;

/**
 * 载入冒烟测试：构造主窗口、按命令行参数载入指定影像目录，等待后台扫描结束后打印序列数。
 *
 * <p>用法：{@code ... M3StartupCheck <影像目录>}（对应启动脚本 {@code run-app.ps1 <目录>} 的行为）。</p>
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
        if (args.length == 0 || args[0].isBlank()) {
            LOG.warn("未提供影像目录，跳过（用法：M3StartupCheck <影像目录>）");
            return;
        }
        Path folder = Paths.get(args[0]);
        MainFrame frame = new MainFrame(new VtkStackView(), new VtkMprView());
        SwingUtilities.invokeAndWait(() -> frame.loadFolder(folder));

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
