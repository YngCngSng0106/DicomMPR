package com.zlyd.mpr.mpr;

import java.awt.event.KeyEvent;

import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;

import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.geometry.Measurement;
import com.zlyd.mpr.geometry.MprCursorFrame;
import com.zlyd.mpr.geometry.VoxelProbe;
import com.zlyd.mpr.ui.MeasurementToolbar;
import com.zlyd.mpr.ui.WindowLevelToolbar;
import com.zlyd.mpr.util.MeasurementFormatter;


/**
 * M3 MPR 三视图（C1 + C2 + C3 斜切）：轴位 / 冠状 / 矢状，十字线联动，含方向标记与 HU 读数。
 *
 * <p>交互：</p>
 * <ul>
 *   <li>拖<b>十字线</b> = 绕该视图视线旋转（屏幕顺时针为正，角度无限制）；</li>
 *   <li>拖<b>交点空白洞</b> = 在被拖视图平面内移动中心；洞外单击 = 中心跳到该点；</li>
 *   <li>右键拖动调窗宽窗位；滚轮/↑↓ 翻层（沿平面法向）；<code>R</code> 重置；<code>A</code> 回正。</li>
 * </ul>
 */
public final class VtkMprView extends VtkViewPanel {

    private static final long serialVersionUID = 1L;
    private static final int CLICK_MOVE_THRESHOLD = 3;
    private static final double ROTATE_MIN_RADIUS_PIXELS = 8.0;

    /** 左键拖动模式。 */
    private enum DragMode {
        NONE, MOVE_CENTER, ROTATE
    }

    private final MprScene scene;
    private final WindowLevelToolbar windowLevelToolbar = new WindowLevelToolbar();
    private final MeasurementToolbar measurementToolbar = new MeasurementToolbar();

    private SeriesInfo series;
    private VoxelProbe lastProbe;
    private DragMode dragMode = DragMode.NONE;
    private int rotationView = -1;
    private double lastRotationAngle = Double.NaN;
    private boolean rightDown;
    private int pressX;
    private int pressY;
    private int lastRightX;
    private int lastRightY;

    public VtkMprView() {
        super("在左侧序列上右键，选择「MPR」打开三视图");
        scene = new MprScene(getCanvas());
        windowLevelToolbar.setWindowLevelListener(scene::setWindowLevel);
        measurementToolbar.setToolListener(scene::setMeasurementTool);
        measurementToolbar.setClearListener(this::clearMeasurements);
        getPrimaryRow().add(windowLevelToolbar);
        addToolbarRow(measurementToolbar);

        getInteractor().AddObserver("MouseWheelForwardEvent", this, "onWheelForward");
        getInteractor().AddObserver("MouseWheelBackwardEvent", this, "onWheelBackward");
        getInteractor().AddObserver("LeftButtonPressEvent", this, "onLeftButtonDown");
        getInteractor().AddObserver("LeftButtonReleaseEvent", this, "onLeftButtonUp");
        getInteractor().AddObserver("RightButtonPressEvent", this, "onRightButtonDown");
        getInteractor().AddObserver("RightButtonReleaseEvent", this, "onRightButtonUp");
        getInteractor().AddObserver("MouseMoveEvent", this, "onMouseMove");
    }

    @Override
    protected void onVolumeReady(BuiltVolume volume, SeriesInfo target) {
        this.series = target;
        scene.setVolume(volume, target);
        windowLevelToolbar.setCurrent(scene.getWindowLevel());
        refreshMeasurementAvailability();
        updateStatus();
        requestCanvasFocus();
        // 布局稳定后再取景/刷新一次，保证首帧就画出三个视图与十字线
        SwingUtilities.invokeLater(scene::refitCameras);
    }

    @Override
    protected String defaultExportName() {
        return "mpr";
    }

    @Override
    protected void onViewResized() {
        scene.refitCameras();
    }

    @Override
    protected void onKeyPressed(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.VK_UP:
                onWheelForward();
                break;
            case KeyEvent.VK_DOWN:
                onWheelBackward();
                break;
            case KeyEvent.VK_R:
                scene.resetView();
                refreshMeasurementAvailability();
                updateStatus();
                break;
            case KeyEvent.VK_A:
                // 回正：把各视图 up 摆回最贴近解剖习惯的方向
                scene.alignRig();
                refreshMeasurementAvailability();
                updateStatus();
                break;
            default:
                break;
        }
    }

    public void onWheelForward() {
        scene.stepActivePlane(1);
        updateStatus();
    }

    public void onWheelBackward() {
        scene.stepActivePlane(-1);
        updateStatus();
    }

    public void onLeftButtonDown() {
        int[] position = getInteractor().GetEventPosition();
        pressX = position[0];
        pressY = position[1];
        lastRotationAngle = Double.NaN;
        if (scene.getMeasurementTool() != null) {
            if (!scene.isVolumeReady()) {
                return;
            }
            if (!scene.isAxisAligned()) {
                setStatus("斜切状态下暂不支持测量（请先用 R 重置或 A 回正）");
                return;
            }
            // 测量模式：左键落点
            scene.addMeasurementPoint(pressX, pressY);
            updateMeasurementResult();
            return;
        }
        if (scene.isOnHole(pressX, pressY)) {
            dragMode = DragMode.MOVE_CENTER;
        } else if (scene.isOnCrosshair(pressX, pressY)) {
            // 锁定起始视图：拖动中指针越界也不会把旋转轴切到别的视图
            dragMode = DragMode.ROTATE;
            rotationView = scene.viewAt(pressX, pressY);
            scene.beginRotation();
        } else {
            dragMode = DragMode.NONE;
        }
    }

    public void onLeftButtonUp() {
        if (scene.getMeasurementTool() != null) {
            return;
        }
        int[] position = getInteractor().GetEventPosition();
        boolean isClick = Math.abs(position[0] - pressX) <= CLICK_MOVE_THRESHOLD
                && Math.abs(position[1] - pressY) <= CLICK_MOVE_THRESHOLD;
        if (dragMode == DragMode.NONE && isClick) {
            // 洞外单击：中心直接跳到该点
            scene.moveCenter(position[0], position[1]);
            updateStatus();
        }
        if (dragMode == DragMode.ROTATE) {
            // 松手后按当前朝向重新取景一次（拖动过程中冻结，见方案 B）
            scene.endRotation();
        }
        dragMode = DragMode.NONE;
        rotationView = -1;
        lastRotationAngle = Double.NaN;
    }

    /**
     * 拖动十字线旋转：按指针绕交点 C 的角增量（屏幕顺时针为正）实时旋转，角度不设限。
     */
    private void updateRotation(int displayX, int displayY) {
        int view = rotationView;
        if (view < 0) {
            return;
        }
        double[] center = scene.centerDisplay(view);
        double dx = displayX - center[0];
        double dy = displayY - center[1];
        if (Math.hypot(dx, dy) < ROTATE_MIN_RADIUS_PIXELS) {
            return;
        }
        double angle = Math.atan2(dy, dx);
        if (Double.isNaN(lastRotationAngle)) {
            lastRotationAngle = angle;
            return;
        }
        // display 坐标 y 向上：顺时针 = 角度减小，故取 (上次 − 本次) 并对 2π 归一
        double clockwise = Math.atan2(Math.sin(lastRotationAngle - angle),
                Math.cos(lastRotationAngle - angle));
        lastRotationAngle = angle;
        if (clockwise == 0.0) {
            return;
        }
        scene.rotate(view, clockwise);
        refreshMeasurementAvailability();
        lastProbe = scene.probe(displayX, displayY);
        updateStatus();
    }

    private void clearMeasurements() {
        scene.clearMeasurements();
        measurementToolbar.setResultText(null);
    }

    private void updateMeasurementResult() {
        Measurement measurement = scene.getLastMeasurement();
        measurementToolbar.setResultText(measurement == null ? null : MeasurementFormatter.format(measurement));
    }

    public void onRightButtonDown() {
        int[] position = getInteractor().GetEventPosition();
        rightDown = true;
        lastRightX = position[0];
        lastRightY = position[1];
    }

    public void onRightButtonUp() {
        int[] position = getInteractor().GetEventPosition();
        boolean isClick = Math.abs(position[0] - lastRightX) <= CLICK_MOVE_THRESHOLD
                && Math.abs(position[1] - lastRightY) <= CLICK_MOVE_THRESHOLD;
        if (rightDown && isClick && scene.getMeasurementTool() != null) {
            // 测量模式下右键单击：取消未完成的测量
            scene.cancelPending();
        }
        rightDown = false;
    }

    public void onMouseMove() {
        int[] position = getInteractor().GetEventPosition();
        if (scene.getMeasurementTool() != null) {
            scene.updateMeasurementPreview(position[0], position[1]);
        }
        if (dragMode == DragMode.ROTATE) {
            updateRotation(position[0], position[1]);
            return;
        }
        if (dragMode == DragMode.MOVE_CENTER) {
            scene.moveCenter(position[0], position[1]);
            lastProbe = scene.probe(position[0], position[1]);
            updateStatus();
            return;
        }
        if (rightDown) {
            scene.adjustWindowLevel(position[0] - lastRightX, position[1] - lastRightY);
            lastRightX = position[0];
            lastRightY = position[1];
            windowLevelToolbar.setCurrent(scene.getWindowLevel());
            return;
        }
        lastProbe = scene.probe(position[0], position[1]);
        updateStatus();
    }

    /**
     * 斜切时禁用测量工具（D2）。
     */
    private void refreshMeasurementAvailability() {
        measurementToolbar.setOblique(!scene.isAxisAligned());
    }

    private void updateStatus() {
        if (!scene.isVolumeReady() || scene.getFrame() == null) {
            // 尚未载入序列（或载入失败）：保持初始提示，避免空指针
            setStatus("在左侧序列上右键，选择「MPR」打开三视图");
            return;
        }
        String description = series == null
                ? "-" : StringUtils.defaultIfBlank(series.getAttributes().getSeriesDescription(), "-");
        String probeText = lastProbe == null ? "HU=-"
                : String.format("HU=%.0f", lastProbe.getValue());
        double[] offsets = scene.getFrame().getOffsets();
        double[] obliquity = scene.getFrame().obliquityDegrees();
        setStatus(String.format(
                "%s   |   偏移 矢%.1f/冠%.1f/轴%.1f mm   |   斜切 矢%.0f°/冠%.0f°/轴%.0f°   |   %s"
                        + "   |   拖十字线旋转 · 拖洞移动中心 · 右键调窗 · 滚轮翻层 · R 重置 · A 回正",
                description,
                offsets[MprCursorFrame.AXIS_U], offsets[MprCursorFrame.AXIS_V],
                offsets[MprCursorFrame.AXIS_W],
                obliquity[1], obliquity[2], obliquity[0],
                probeText));
    }
}
