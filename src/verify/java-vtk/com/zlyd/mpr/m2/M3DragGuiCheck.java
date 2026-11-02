package com.zlyd.mpr.m2;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.dicom.DicomScanner;
import com.zlyd.mpr.dicom.SeriesGeometry;
import com.zlyd.mpr.dicom.SeriesGrouper;
import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.geometry.MeasurementType;
import com.zlyd.mpr.mpr.VtkMprView;
import com.zlyd.mpr.util.VtkImageExporter;
import com.zlyd.mpr.util.VtkNativeLoader;

import vtk.vtkCanvas;

/**
 * 拖动残留排查（GUI 真实路径）：可见窗口 + 真实 AWT 鼠标事件 → 画线段 → 拖动 → 统计轮廓像素。
 *
 * <p>目的：复现"拖动后原地留下一条无标注副本"的现象；离屏逐帧拖动校验是干净的，
 * 因此需要走 App 的完整链路（面板/场景/画布/真实事件）。</p>
 */
public final class M3DragGuiCheck {

    private static final Logger LOG = LogManager.getLogger(M3DragGuiCheck.class);

    static {
        VtkNativeLoader.load();
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        SeriesInfo target = pickLargestReconstructable(new SeriesGrouper()
                .group(new DicomScanner().scan(root)));
        if (target == null) {
            LOG.warn("没有可重建的序列");
            return;
        }
        new M3DragGuiCheck().run(target);
    }

    private void run(SeriesInfo series) throws Exception {
        VtkMprView view = new VtkMprView();
        JFrame frame = new JFrame("M3DragGuiCheck（排查期间自动开关）");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(view);
        frame.setSize(900, 700);
        frame.setLocationRelativeTo(null);
        SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
        Thread.sleep(1200);

        SwingUtilities.invokeAndWait(() -> view.showSeries(series));
        long deadline = System.currentTimeMillis() + 120_000;
        while (!view.isVolumeReady() && System.currentTimeMillis() < deadline) {
            Thread.sleep(250);
        }
        if (!view.isVolumeReady()) {
            LOG.error("载入超时，退出");
            SwingUtilities.invokeAndWait(frame::dispose);
            return;
        }
        Thread.sleep(800);

        vtkCanvas canvas = findCanvas(view);
        if (canvas == null) {
            LOG.error("未找到画布");
            SwingUtilities.invokeAndWait(frame::dispose);
            return;
        }

        // 选"线段"工具，画一条水平线段（轴位视口左上角区域内）
        SwingUtilities.invokeAndWait(() -> view.onToolSelected(MeasurementType.LENGTH));
        Thread.sleep(300);
        int viewY = 320;
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, 60, viewY);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 60, viewY);
        Thread.sleep(200);
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, 140, viewY);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 140, viewY);
        Thread.sleep(400);
        int before = countOutline(canvas);
        LOG.info("画完线段: 轮廓像素={}", before);
        logLabels("画完线段后", view);

        // 回到十字线模式（避免拖动被当作新作图），然后在线上拖动
        SwingUtilities.invokeAndWait(() -> view.onToolSelected(null));
        Thread.sleep(300);
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, 100, viewY);
        for (int step = 1; step <= 20; step++) {
            dispatch(canvas, MouseEvent.MOUSE_MOVED, 100, viewY + step * 4);
        }
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 100, viewY + 80);
        Thread.sleep(500);
        int after = countOutline(canvas);
        int startSpot = countOutlineNear(canvas, 60, viewY, 150, viewY + 12);
        int movedSpot = countOutlineNear(canvas, 60, viewY + 80, 150, viewY + 92);
        LOG.info("拖动后: 轮廓像素={}（拖动前 {}）| 原位置附近={}（应≈0）| 新位置附近={}",
                after, before, startSpot, movedSpot);
        logLabels("拖动后", view);
        LOG.info("排查结论: 起始位置残留={} 像素是否翻倍={}", startSpot > 0 ? "有" : "无",
                after > before * 1.5 ? "是" : "否");

        SwingUtilities.invokeAndWait(() -> {
            frame.setVisible(false);
            frame.dispose();
        });
    }

    private static void dispatch(vtkCanvas canvas, int id, int x, int y) throws Exception {
        SwingUtilities.invokeAndWait(() -> canvas.dispatchEvent(new MouseEvent(canvas, id,
                System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1)));
    }

    private int countOutline(vtkCanvas canvas) throws Exception {
        return countOutlineNear(canvas, 0, 0, 900, 900);
    }

    private int countOutlineNear(vtkCanvas canvas, int x0, int y0, int x1, int y1) throws Exception {
        Path file = Paths.get("target", "drag", "gui.png");
        VtkImageExporter.exportPng(canvas.GetRenderWindow(), file);
        BufferedImage image = ImageIO.read(new File(file.toString()));
        int count = 0;
        for (int x = Math.max(0, x0); x <= Math.min(image.getWidth() - 1, x1); x++) {
            for (int y = Math.max(0, y0); y <= Math.min(image.getHeight() - 1, y1); y++) {
                int pngY = image.getHeight() - 1 - y;
                if (isOutline(image.getRGB(x, pngY))) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isOutline(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return red > 150 && red - blue > 80 && green < red;
    }

    /**
     * 打印视图内所有 JLabel 文本（用于确认事件是否被处理、当前提示是什么）。
     */
    private static void logLabels(String title, Container container) {
        StringBuilder builder = new StringBuilder();
        collectLabels(container, builder);
        LOG.info("{} 标签: {}", title, builder);
    }

    private static void collectLabels(Container container, StringBuilder builder) {
        for (Component child : container.getComponents()) {
            if (child instanceof javax.swing.JLabel) {
                builder.append('[').append(((javax.swing.JLabel) child).getText()).append("] ");
            }
            if (child instanceof Container) {
                collectLabels((Container) child, builder);
            }
        }
    }

    private static vtkCanvas findCanvas(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof vtkCanvas) {
                return (vtkCanvas) child;
            }
            if (child instanceof Container) {
                vtkCanvas found = findCanvas((Container) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static SeriesInfo pickLargestReconstructable(List<SeriesInfo> seriesList) {
        SeriesInfo best = null;
        for (SeriesInfo series : seriesList) {
            SeriesGeometry geometry = series.getGeometry();
            if (geometry.isValid() && (best == null || series.getSliceCount() > best.getSliceCount())) {
                best = series;
            }
        }
        return best;
    }
}
