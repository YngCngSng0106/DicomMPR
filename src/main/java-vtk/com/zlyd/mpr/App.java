package com.zlyd.mpr;

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
 * <p>用法：{@code mvn -Pvtk -q exec:java -Dexec.mainClass=com.zlyd.mpr.App}</p>
 */
public final class App {

    private static final Logger LOG = LogManager.getLogger(App.class);

    private App() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::launch);
    }

    private static void launch() {
        applySystemLookAndFeel();
        MainFrame frame = new MainFrame(new VtkStackView(), new VtkMprView());
        frame.setVisible(true);
        frame.loadStartupFolder();
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
