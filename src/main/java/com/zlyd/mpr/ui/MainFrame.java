package com.zlyd.mpr.ui;

import java.awt.CardLayout;
import java.nio.file.Paths;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 主窗口：左侧序列列表，右侧视图区。
 *
 * <p>双击/拖拽序列 → 2D 阅片视图；右键菜单「MPR」→ 三视图 MPR 视图。</p>
 */
public final class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LogManager.getLogger(MainFrame.class);
    private static final int DIVIDER_LOCATION = 320;
    private static final String CARD_STACK = "stack";
    private static final String CARD_MPR = "mpr";

    private final SeriesView stackView;
    private final SeriesView mprView;
    private final SeriesListPanel listPanel = new SeriesListPanel();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel rightPanel = new JPanel(cardLayout);

    public MainFrame() {
        this(new PlaceholderSeriesView(), null);
    }

    public MainFrame(SeriesView stackView, SeriesView mprView) {
        super("DICOM MPR Demo");
        this.stackView = stackView;
        this.mprView = mprView;

        listPanel.setSeriesSelectionListener(series -> {
            stackView.showSeries(series);
            cardLayout.show(rightPanel, CARD_STACK);
        });
        listPanel.setMprRequestListener(series -> {
            if (mprView == null) {
                return;
            }
            mprView.showSeries(series);
            cardLayout.show(rightPanel, CARD_MPR);
        });

        rightPanel.add(stackView.getComponent(), CARD_STACK);
        if (mprView != null) {
            rightPanel.add(mprView.getComponent(), CARD_MPR);
        }
        rightPanel.setTransferHandler(SeriesTransferHandler.createTargetHandler(stackView));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, rightPanel);
        splitPane.setDividerLocation(DIVIDER_LOCATION);
        splitPane.setResizeWeight(0.0);

        setContentPane(splitPane);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);
    }

    /**
     * 启动时默认载入当前工作目录下以 "3" 开头的影像目录（无匹配则保持空列表，仍可手动选择）。
     */
    public void loadStartupFolder() {
        StartupFolderResolver.resolve(Paths.get(System.getProperty("user.dir")))
                .ifPresentOrElse(path -> {
                    LOG.info("启动默认载入: {}", path);
                    listPanel.loadDirectory(path);
                }, () -> LOG.info("当前目录下未找到 3 开头的影像目录，保持空列表"));
    }

    public SeriesView getStackView() {
        return stackView;
    }

    public SeriesView getMprView() {
        return mprView;
    }
}
