package com.zlyd.mpr.m2;

import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
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
import com.zlyd.mpr.geometry.MeasurementType;
import com.zlyd.mpr.geometry.MprViewOrientation;
import com.zlyd.mpr.mpr.VtkMprView;
import com.zlyd.mpr.util.VtkNativeLoader;

import vtk.vtkCanvas;
import vtk.vtkRenderWindowInteractor;

/**
 * "拖动状态无法释放"回归校验（GUI 实路径）：复现"测量工具激活时按下十字线洞 → 松开 → 移动鼠标"
 * 之后中心仍跟着鼠标走的缺陷。
 *
 * <p>缺陷成因：{@code VtkMprView.onLeftButtonUp} 在测量工具激活时提前返回、没有复位
 * {@code dragMode}，于是按下洞设置的 {@code MOVE_CENTER} 一直残留，{@code onMouseMove}
 * 持续调用 {@code scene.moveCenter(...)}；点工具条下拉框也无法解除。</p>
 *
 * <p>校验用交互器直接派发事件（{@code SetEventInformation + InvokeEvent}），避免合成 AWT
 * 事件送不进 VTK 交互器的问题。需要真实 GL 上下文，故本检查**必须显示窗口**，不纳入无界面
 * 一键校验（与 {@code M3ReopenCheck} 同类）。</p>
 *
 * <p>用法：{@code scripts\run-check.ps1 center-release}（短名见 {@code -List}，
 * 缺省使用项目内测试数据目录）。</p>
 */
public final class M3CenterReleaseCheck {

    private static final Logger LOG = LogManager.getLogger(M3CenterReleaseCheck.class);
    private static final double TOLERANCE = 1e-6;
    private static final int DRAG_PIXELS = 60;

    static {
        VtkNativeLoader.load();
    }

    private boolean passed = true;

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        SeriesInfo target = pickLargestReconstructable(new SeriesGrouper()
                .group(new DicomScanner().scan(root)));
        if (target == null) {
            LOG.error("没有可重建的序列");
            Runtime.getRuntime().halt(1);
            return;
        }
        M3CenterReleaseCheck check = new M3CenterReleaseCheck();
        try {
            check.run(target);
        } finally {
            // 带窗口的 VTK 在 JVM 关闭阶段清理原生对象时会偶发访问违例（0xC0000005），
            // 直接 halt 跳过关闭钩子，让退出码如实反映校验结果（日志由 log4j 立即刷新）。
            Runtime.getRuntime().halt(check.passed ? 0 : 1);
        }
    }
    private void run(SeriesInfo series) throws Exception {
        VtkMprView view = new VtkMprView();
        JFrame frame = new JFrame("M3CenterReleaseCheck（中心释放回归校验，自动关闭）");
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
            passed = false;
            SwingUtilities.invokeAndWait(frame::dispose);
            return;
        }
        Thread.sleep(800);

        vtkCanvas canvas = findCanvas(view);
        if (canvas == null) {
            LOG.error("未找到画布");
            passed = false;
            SwingUtilities.invokeAndWait(frame::dispose);
            return;
        }
        vtkRenderWindowInteractor interactor = canvas.GetRenderWindow().GetInteractor();

        // 工具激活（复现缺陷所在分支）
        SwingUtilities.invokeAndWait(() -> view.onToolSelected(MeasurementType.LENGTH));
        Thread.sleep(300);

        // 场景 0（前置条件 + 对照）：按住洞拖动时中心必须跟随。
        // 该断言同时证明"按下确实命中了十字线洞"，因此后面几条"中心不跟随"不可能是空测。
        int[] hole = settledHole(view);
        double[] beforeDrag = view.getCenterWorld();
        fire(interactor, "LeftButtonPressEvent", hole[0], hole[1]);
        fire(interactor, "MouseMoveEvent", hole[0] + DRAG_PIXELS, hole[1] + DRAG_PIXELS);
        Thread.sleep(200);
        report(!same(beforeDrag, view.getCenterWorld()),
                "对照：按住洞拖动时中心跟随（证明命中洞，应 true）");
        fire(interactor, "LeftButtonReleaseEvent", hole[0] + DRAG_PIXELS, hole[1] + DRAG_PIXELS);
        Thread.sleep(250);

        // 场景 1：洞上按下 + 松开（画布内正常路径），再移动鼠标
        hole = settledHole(view);
        double[] before = view.getCenterWorld();
        fire(interactor, "LeftButtonPressEvent", hole[0], hole[1]);
        fire(interactor, "LeftButtonReleaseEvent", hole[0], hole[1]);
        Thread.sleep(200);
        fire(interactor, "MouseMoveEvent", hole[0] + DRAG_PIXELS, hole[1] + DRAG_PIXELS);
        Thread.sleep(250);
        report(same(before, view.getCenterWorld()),
                "工具激活时按下洞并松开后，移动鼠标中心不再跟随（应 true）");

        // 场景 2：洞上按下后松开事件丢给画布外组件（工具条/下拉框），再移动鼠标
        hole = settledHole(view);
        double[] beforeStray = view.getCenterWorld();
        fire(interactor, "LeftButtonPressEvent", hole[0], hole[1]);
        Thread.sleep(150);
        postReleaseOutside(frame);
        Thread.sleep(300);
        fire(interactor, "MouseMoveEvent", hole[0] + DRAG_PIXELS, hole[1] + DRAG_PIXELS);
        Thread.sleep(250);
        report(same(beforeStray, view.getCenterWorld()),
                "画布外松开（工具条/下拉框）后，中心不再跟随鼠标（应 true）");

        // 场景 3：再次按下并松开，松开后中心必须停住（缺陷的最终表现）
        hole = settledHole(view);
        double[] beforeStop = view.getCenterWorld();
        fire(interactor, "LeftButtonPressEvent", hole[0], hole[1]);
        fire(interactor, "LeftButtonReleaseEvent", hole[0], hole[1]);
        Thread.sleep(200);
        fire(interactor, "MouseMoveEvent", hole[0] - DRAG_PIXELS, hole[1] - DRAG_PIXELS);
        Thread.sleep(250);
        report(same(beforeStop, view.getCenterWorld()), "松开后中心停住，不再跟着鼠标走（应 true）");

        LOG.info("中心释放校验结论: {}", passed);
        SwingUtilities.invokeAndWait(() -> {
            frame.setVisible(false);
            frame.dispose();
        });
    }

    /**
     * 读取"已稳定"的十字线洞屏幕坐标。
     *
     * <p>工具条提示文字变化会改变画布尺寸，进而触发 {@code refitCameras()} 把交点重新钉到视口中心：
     * 世界中心不变，但**屏幕位置会变**。因此每次按下前都重新读取，并等到连续两次读数一致
     * （布局与取景已稳定）再返回。</p>
     */
    private int[] settledHole(VtkMprView view) throws Exception {
        int[] previous = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            double[] center = view.centerDisplay(MprViewOrientation.VIEW_AXIAL);
            int[] current = {(int) Math.round(center[0]), (int) Math.round(center[1])};
            if (previous != null && previous[0] == current[0] && previous[1] == current[1]) {
                return current;
            }
            previous = current;
            Thread.sleep(150);
        }
        return previous;
    }

    /**
     * 用交互器直接派发事件（不经过 AWT，确保 VTK 观察者一定收到）。
     */
    private static void fire(vtkRenderWindowInteractor interactor, String event, int x, int y)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            interactor.SetEventInformation(x, y, 0, 0, (char) 0, 0, "");
            interactor.InvokeEvent(event);
        });
    }

    /**
     * 把"松开"投递到画布之外的组件（模拟鼠标在工具条/下拉框上松开），使画布收不到松开事件。
     */
    private static void postReleaseOutside(JFrame frame) throws Exception {
        SwingUtilities.invokeAndWait(() -> Toolkit.getDefaultToolkit().getSystemEventQueue()
                .postEvent(new MouseEvent(frame, MouseEvent.MOUSE_RELEASED,
                        System.currentTimeMillis(), 0, 10, 10, 1, false, MouseEvent.BUTTON1)));
    }

    private static boolean same(double[] first, double[] second) {
        if (first == null || second == null) {
            return false;
        }
        return Math.abs(first[0] - second[0]) <= TOLERANCE
                && Math.abs(first[1] - second[1]) <= TOLERANCE
                && Math.abs(first[2] - second[2]) <= TOLERANCE;
    }

    private boolean report(boolean ok, String message) {
        passed = passed && ok;
        LOG.info("[{}] {}", ok ? "PASS" : "FAIL", message);
        return ok;
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
