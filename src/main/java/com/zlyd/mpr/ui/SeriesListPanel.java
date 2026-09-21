package com.zlyd.mpr.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.dicom.SeriesInfo;

/**
 * 左侧序列列表：选择目录、显示缩略图、双击打开 2D 阅片、右键菜单打开 MPR。
 */
public final class SeriesListPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LogManager.getLogger(SeriesListPanel.class);
    private static final int PANEL_WIDTH = 280;
    private static final int CELL_HEIGHT = 108;

    /** 双击/拖拽序列时的回调。 */
    public interface SeriesSelectionListener {
        void onSeriesSelected(SeriesInfo series);
    }

    /** 右键菜单选择「MPR」时的回调。 */
    public interface MprRequestListener {
        void onMprRequested(SeriesInfo series);
    }

    private final DefaultListModel<SeriesEntry> model = new DefaultListModel<>();
    private final JList<SeriesEntry> list = new JList<>(model);
    private final JLabel statusLabel = new JLabel("尚未加载数据");
    private final JButton openButton = new JButton("选择 DICOM 文件夹…");
    private final SeriesLoader seriesLoader = new SeriesLoader();

    private SeriesSelectionListener selectionListener;
    private MprRequestListener mprRequestListener;
    private File lastDirectory;

    public SeriesListPanel() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(PANEL_WIDTH, 0));

        openButton.addActionListener(event -> chooseFolder());
        JPanel toolbar = new JPanel(new BorderLayout(0, 4));
        toolbar.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        toolbar.add(openButton, BorderLayout.NORTH);
        toolbar.add(statusLabel, BorderLayout.SOUTH);

        configureList();

        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    private void configureList() {
        list.setCellRenderer(new SeriesListCellRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(CELL_HEIGHT);
        list.setDragEnabled(true);
        list.setTransferHandler(SeriesTransferHandler.createSourceHandler(list));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showPopupIfNeeded(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showPopupIfNeeded(event);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    notifySelection();
                }
            }
        });
    }

    public void setSeriesSelectionListener(SeriesSelectionListener listener) {
        this.selectionListener = listener;
    }

    public void setMprRequestListener(MprRequestListener listener) {
        this.mprRequestListener = listener;
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择 DICOM 文件夹");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        lastDirectory = chooser.getSelectedFile();
        loadFolder(lastDirectory.toPath());
    }

    /**
     * 直接载入指定目录（供启动默认载入使用），并记录为最近目录。
     *
     * @param root 影像目录
     */
    public void loadDirectory(Path root) {
        if (root == null) {
            return;
        }
        lastDirectory = root.toFile();
        loadFolder(root);
    }

    private void loadFolder(Path root) {
        openButton.setEnabled(false);
        statusLabel.setText("正在扫描…");
        model.clear();
        seriesLoader.load(root, this::onLoaded, this::onLoadFailed);
    }

    private void onLoaded(List<SeriesEntry> entries) {
        for (SeriesEntry entry : entries) {
            model.addElement(entry);
        }
        statusLabel.setText("共 " + entries.size() + " 个序列");
        openButton.setEnabled(true);
        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(this, "该目录下未找到可用序列。", "提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void onLoadFailed(Exception error) {
        LOG.error("加载 DICOM 失败", error);
        statusLabel.setText("加载失败");
        openButton.setEnabled(true);
        JOptionPane.showMessageDialog(this, "加载失败：" + error.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
    }

    private void showPopupIfNeeded(MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        int index = list.locationToIndex(event.getPoint());
        if (index < 0 || index >= model.getSize()) {
            return;
        }
        list.setSelectedIndex(index);
        SeriesEntry entry = model.getElementAt(index);
        JPopupMenu menu = SeriesContextMenu.create(() -> notifyMprRequest(entry.getSeries()));
        menu.show(list, event.getX(), event.getY());
    }

    private void notifySelection() {
        SeriesEntry entry = list.getSelectedValue();
        if (entry != null && selectionListener != null) {
            selectionListener.onSeriesSelected(entry.getSeries());
        }
    }

    private void notifyMprRequest(SeriesInfo series) {
        if (mprRequestListener != null) {
            mprRequestListener.onMprRequested(series);
        }
    }
}
