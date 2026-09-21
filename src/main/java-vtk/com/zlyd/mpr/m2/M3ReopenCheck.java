package com.zlyd.mpr.m2;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.dicom.DicomScanner;
import com.zlyd.mpr.dicom.SeriesGeometry;
import com.zlyd.mpr.dicom.SeriesGrouper;
import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.mpr.VtkMprView;
import com.zlyd.mpr.util.VtkNativeLoader;

import vtk.vtkPlane;

/**
 * MPR 清理/重开校验：载入 → 清理缓存 → 再载入，确认状态正确回收且能反复重新打开。
 *
 * <p>断言：①首次载入后 ready；②clearVolume 后立刻未载入；③再次载入仍 ready（可反复打开）；
 * ④全过程无异常（异常则退出码非 0）。同时打印清理前后的内存占用供参考。</p>
 *
 * <p><b>注意</b>：本校验会短暂弹出一个窗口——MPR 视图内部会渲染 canvas，
 * 而"未显示的 canvas"没有有效 OpenGL 上下文，直接渲染会让 VTK 抛 C++ 异常并终止进程，
 * 因此必须先把窗口显示出来（这也是它不放进无界面一键校验的原因）。</p>
 */
public final class M3ReopenCheck {

    private static final Logger LOG = LogManager.getLogger(M3ReopenCheck.class);
    private static final long TIMEOUT_MILLIS = 120_000L;
    private static final long POLL_MILLIS = 250L;

    static {
        VtkNativeLoader.load();
        // 无窗口环境下 canvas 渲染会刷 "wglMakeCurrent failed" 日志（不影响状态断言），此处关闭传统警告输出
        new vtkPlane().GlobalWarningDisplayOff();
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        SeriesInfo target = pickLargestReconstructable(new SeriesGrouper()
                .group(new DicomScanner().scan(root)));
        if (target == null) {
            LOG.warn("没有可重建的序列");
            return;
        }
        boolean ok = new M3ReopenCheck().run(target);
        LOG.info("清理/重开结论: {}", ok);
        System.exit(ok ? 0 : 1);
    }

    private boolean run(SeriesInfo series) throws Exception {
        VtkMprView view = new VtkMprView();
        JFrame frame = new JFrame("M3ReopenCheck（校验期间自动开关）");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(view);
        frame.setSize(900, 700);
        frame.setLocationRelativeTo(null);
        SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
        Thread.sleep(1000);

        boolean ok = load(view, series, "1) 首次载入");
        LOG.info("   载入后内存: {}", memory());

        SwingUtilities.invokeAndWait(view::clearVolume);
        boolean released = !view.isVolumeReady();
        ok &= released;
        LOG.info("2) 清理缓存后 isVolumeReady={}（应 false） -> {}", view.isVolumeReady(),
                released ? "PASS" : "FAIL");
        System.gc();
        Thread.sleep(500);
        LOG.info("   清理并 GC 后内存: {}", memory());

        ok &= load(view, series, "3) 重新打开（第二次载入）");

        // 「清理缓存」按钮的路径：清理并请求关闭页面 → 断言关闭回调被触发
        boolean[] closed = {false};
        SwingUtilities.invokeAndWait(() -> view.setCloseRequestListener(() -> closed[0] = true));
        SwingUtilities.invokeAndWait(view::clearVolumeAndClose);
        boolean notified = closed[0] && !view.isVolumeReady();
        ok &= notified;
        LOG.info("5) 清理缓存并关闭页面：回调触发={} isVolumeReady={} -> {}", closed[0],
                view.isVolumeReady(), notified ? "PASS" : "FAIL");

        ok &= load(view, series, "6) 关闭后再次打开（第三次载入）");
        SwingUtilities.invokeAndWait(() -> {
            frame.setVisible(false);
            frame.dispose();
        });
        return ok;
    }

    /**
     * 发起一次 showSeries 并等待进入 ready。
     */
    private boolean load(VtkMprView view, SeriesInfo series, String title) throws Exception {
        SwingUtilities.invokeAndWait(() -> view.showSeries(series));
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (!view.isVolumeReady() && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MILLIS);
        }
        boolean pass = view.isVolumeReady();
        LOG.info("{}：isVolumeReady={} -> {}", title, pass, pass ? "PASS" : "FAIL");
        return pass;
    }

    private static String memory() {
        Runtime runtime = Runtime.getRuntime();
        return String.format("used=%.1fMB max=%.1fMB",
                (runtime.totalMemory() - runtime.freeMemory()) / 1048576.0,
                runtime.maxMemory() / 1048576.0);
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
