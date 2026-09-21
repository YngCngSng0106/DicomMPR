package com.zlyd.mpr;

import java.nio.file.Paths;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.mpr.VtkMprView;
import com.zlyd.mpr.mpr.VtkStackView;
import com.zlyd.mpr.ui.MainFrame;

/**
 * 桌面程序入口（需 VTK，使用 -Pvtk 编译运行）。
 *
 * <p>用法：{@code java com.zlyd.mpr.App [影像目录]}。</p>
 *
 * <p>命令行**未给目录**时不自动载入任何影像，由用户在界面里自行选择；
 * 给了目录（例如启动脚本 {@code run-app.ps1 <目录>}）则默认打开该目录。</p>
 */
public final class App {

    private static final Logger LOG = LogManager.getLogger(App.class);

    private App() {
    }

    public static void main(String[] args) {
        String dataDir = args.length > 0 ? args[0] : null;
        SwingUtilities.invokeLater(() -> launch(dataDir));
    }

    private static void launch(String dataDir) {
        applySystemLookAndFeel();
        MainFrame frame = new MainFrame(new VtkStackView(), new VtkMprView());
        frame.setVisible(true);
        if (dataDir != null && !dataDir.isBlank()) {
            frame.loadFolder(Paths.get(dataDir));
        } else {
            LOG.info("未指定影像目录：请点击「选择 DICOM 文件夹…」自行选择");
        }
    }

    private static void applySystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (UnsupportedLookAndFeelException | ClassNotFoundException
                 | InstantiationException | IllegalAccessException e) {
            LOG.warn("设置系统外观失败，使用默认外观: {}", e.getMessage());
        }
    }
}
