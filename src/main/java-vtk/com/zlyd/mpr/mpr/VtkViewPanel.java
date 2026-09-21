package com.zlyd.mpr.mpr;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ExecutionException;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.ui.SeriesView;
import com.zlyd.mpr.util.VtkImageExporter;
import com.zlyd.mpr.util.VtkNativeLoader;

import vtk.vtkCanvas;
import vtk.vtkInteractorStyleUser;
import vtk.vtkRenderWindowInteractor;

/**
 * VTK 视图公共基类：承载 canvas、状态栏、焦点与按键转发，并封装体数据的异步构建。
 *
 * <p>子类只需实现 {@link #onVolumeReady(vtkImageData, SeriesInfo)} 与 {@link #onKeyPressed(KeyEvent)}。</p>
 */
public abstract class VtkViewPanel extends JPanel implements SeriesView {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LogManager.getLogger(VtkViewPanel.class);

    static {
        // 必须在任何 VTK 对象创建之前加载 native 库
        VtkNativeLoader.load();
    }

    private final vtkCanvas canvas = new vtkCanvas();
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JPanel toolbarPanel = new JPanel();
    private final JPanel primaryRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JCheckBox isotropicBox = new JCheckBox("等体素重采样", true);
    private final vtkRenderWindowInteractor interactor;

    private SeriesInfo currentSeries;

    protected VtkViewPanel(String initialStatus) {
        super(new BorderLayout());
        interactor = canvas.getRenderWindowInteractor();
        interactor.SetInteractorStyle(new vtkInteractorStyleUser());

        toolbarPanel.setLayout(new BoxLayout(toolbarPanel, BoxLayout.Y_AXIS));
        primaryRow.add(createExportButton());
        primaryRow.add(createIsotropicBox());
        toolbarPanel.add(primaryRow);
        add(toolbarPanel, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        setStatus(initialStatus);
        installFocusAndKeys();
    }

    /**
     * 工具条第一行（已含「导出 PNG」），子类可继续往里加控件。
     */
    protected final JPanel getPrimaryRow() {
        return primaryRow;
    }

    /**
     * 向工具条区追加一行（每行独立，避免宽度不足被裁掉）。
     */
    protected final void addToolbarRow(JComponent component) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(component);
        toolbarPanel.add(row);
    }

    /**
     * 导出文件默认名（不含扩展名），子类可覆盖。
     */
    protected String defaultExportName() {
        return "view";
    }

    private JCheckBox createIsotropicBox() {
        isotropicBox.setToolTipText("层厚较大时按最细方向做三线性等体素重采样，切面更均匀（切换后重建体数据）");
        isotropicBox.addActionListener(event -> rebuildCurrentSeries());
        return isotropicBox;
    }

    private void rebuildCurrentSeries() {
        if (currentSeries != null) {
            showSeries(currentSeries);
        }
    }

    private JButton createExportButton() {
        JButton button = new JButton("导出 PNG");
        button.addActionListener(event -> exportPng());
        return button;
    }

    private void exportPng() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出为 PNG");
        chooser.setSelectedFile(new File(defaultExportName() + ".png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            VtkImageExporter.exportPng(canvas.GetRenderWindow(), chooser.getSelectedFile().toPath());
            setStatus("已导出: " + chooser.getSelectedFile().getAbsolutePath());
        } catch (IOException e) {
            LOG.error("导出图片失败", e);
            JOptionPane.showMessageDialog(this, "导出失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    protected final vtkCanvas getCanvas() {
        return canvas;
    }

    protected final vtkRenderWindowInteractor getInteractor() {
        return interactor;
    }

    protected final void setStatus(String text) {
        statusLabel.setText(text);
    }

    protected final void requestCanvasFocus() {
        SwingUtilities.invokeLater(canvas::requestFocusInWindow);
    }

    @Override
    public final JComponent getComponent() {
        return this;
    }

    @Override
    public final void showSeries(SeriesInfo series) {
        if (series == null) {
            return;
        }
        if (!series.getGeometry().isValid()) {
            setStatus("该序列无法重建体数据：" + series.getGeometry().getWarning());
            return;
        }
        currentSeries = series;
        setStatus("正在构建体数据…");
        boolean isotropic = isotropicBox.isSelected();
        new SwingWorker<BuiltVolume, Void>() {
            @Override
            protected BuiltVolume doInBackground() throws Exception {
                return new VolumeBuilder().build(series, isotropic);
            }

            @Override
            protected void done() {
                try {
                    onVolumeReady(get(), series);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    setStatus("加载被中断");
                } catch (ExecutionException e) {
                    LOG.error("构建体数据失败", e.getCause());
                    setStatus("构建体数据失败：" + e.getCause().getMessage());
                }
            }
        }.execute();
    }

    /**
     * 体数据构建完成后的回调（在 EDT 执行）。
     */
    protected abstract void onVolumeReady(BuiltVolume volume, SeriesInfo series);

    /**
     * 画布按键回调（在 EDT 执行）。
     */
    protected abstract void onKeyPressed(KeyEvent event);

    /**
     * 画布尺寸变化回调（在 EDT 执行），默认不做处理。
     */
    protected void onViewResized() {
    }

    private void installFocusAndKeys() {
        canvas.setFocusable(true);
        canvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                onViewResized();
            }

            @Override
            public void componentShown(ComponentEvent event) {
                // 视图切换（如 CardLayout）导致首次可见时，重新取景并刷新，避免首帧未绘制
                onViewResized();
            }
        });
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                canvas.requestFocusInWindow();
            }
        });
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                onKeyPressed(event);
            }
        });
    }
}
