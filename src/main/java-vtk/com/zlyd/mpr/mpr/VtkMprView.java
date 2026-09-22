package com.zlyd.mpr.mpr;

import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;

import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.geometry.Measurement;
import com.zlyd.mpr.geometry.MeasurementType;
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
public final class VtkMprView extends VtkViewPanel implements MeasurementToolbar.Listener {

    private static final long serialVersionUID = 1L;
    private static final int CLICK_MOVE_THRESHOLD = 3;
    private static final long DOUBLE_CLICK_MILLIS = 400;
    private static final double ROTATE_MIN_RADIUS_PIXELS = 8.0;

    /** 左键拖动模式。 */
    private enum DragMode {
        NONE, MOVE_CENTER, ROTATE, DRAW, MOVE_SHAPE
    }

    private final MprScene scene;
    private final WindowLevelToolbar windowLevelToolbar = new WindowLevelToolbar();
    private final MeasurementToolbar measurementToolbar = new MeasurementToolbar();

    private SeriesInfo series;
    private Runnable closeListener;
    private VoxelProbe lastProbe;
    private DragMode dragMode = DragMode.NONE;
    private int rotationView = -1;
    private double lastRotationAngle = Double.NaN;
    private long lastCurveClickTime;
    private int lastCurveClickX = -1;
    private int lastCurveClickY = -1;
    private boolean rightDown;
    private int pressX;
    private int pressY;
    private int lastRightX;
    private int lastRightY;

    public VtkMprView() {
        super("在左侧序列上右键，选择「MPR」打开三视图");
        scene = new MprScene(getCanvas());
        windowLevelToolbar.setWindowLevelListener(scene::setWindowLevel);
        measurementToolbar.setListener(this);
        getPrimaryRow().add(windowLevelToolbar);
        getPrimaryRow().add(createClearButton());
        addToolbarRow(measurementToolbar);

        getInteractor().AddObserver("MouseWheelForwardEvent", this, "onWheelForward");
        getInteractor().AddObserver("MouseWheelBackwardEvent", this, "onWheelBackward");
        getInteractor().AddObserver("LeftButtonPressEvent", this, "onLeftButtonDown");
        getInteractor().AddObserver("LeftButtonReleaseEvent", this, "onLeftButtonUp");
        getInteractor().AddObserver("RightButtonPressEvent", this, "onRightButtonDown");
        getInteractor().AddObserver("RightButtonReleaseEvent", this, "onRightButtonUp");
        getInteractor().AddObserver("MouseMoveEvent", this, "onMouseMove");
    }

    private JButton createClearButton() {
        JButton button = new JButton("清理缓存");
        button.setToolTipText("释放 MPR 已载入的体数据与测量，关闭本页面（之后可重新打开 MPR）");
        button.addActionListener(event -> clearVolumeAndClose());
        return button;
    }

    @Override
    public void setCloseRequestListener(Runnable listener) {
        this.closeListener = listener;
    }

    /**
     * 请求关闭本视图页面（清理由按钮触发时使用；自动清理不会关闭页面）。
     */
    private void notifyCloseRequest() {
        if (closeListener != null) {
            closeListener.run();
        }
    }

    /**
     * 清理 MPR 缓存：释放体数据/几何/测量并回到未载入状态；再次打开 MPR 会重新构建体数据。
     */
    public void clearVolume() {
        series = null;
        lastProbe = null;
        dragMode = DragMode.NONE;
        rotationView = -1;
        scene.clearVolume();
        measurementToolbar.setResultText(null);
        updateMeasurementUi();
        updateStatus();
    }

    /**
     * 清理缓存**并请求关闭本页面**（工具条「清理缓存」按钮使用）：
     * 释放体数据/测量后切回默认页面，之后可重新打开 MPR。
     */
    public void clearVolumeAndClose() {
        clearVolume();
        notifyCloseRequest();
    }

    /**
     * 重新打开（或切换）序列前，先清掉上一次的体数据缓存（不关闭页面）。
     */
    @Override
    protected void onBeforeVolumeLoad() {
        clearVolume();
    }

    /**
     * 当前是否已载入体数据（供离屏校验与外部查询）。
     */
    public boolean isVolumeReady() {
        return scene.isVolumeReady();
    }

    @Override
    protected void onVolumeReady(BuiltVolume volume, SeriesInfo target) {
        this.series = target;
        scene.setVolume(volume, target);
        windowLevelToolbar.setCurrent(scene.getWindowLevel());
        updateMeasurementUi();
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
                updateMeasurementUi();
                updateStatus();
                break;
            case KeyEvent.VK_DELETE:
                deleteSelectedMeasurement();
                break;
            case KeyEvent.VK_ESCAPE:
                exitMeasurementMode();
                break;
            case KeyEvent.VK_ENTER:
                scene.finishMeasurementCurve();
                updateMeasurementResult();
                break;
            case KeyEvent.VK_A:
                // 回正：把各视图 up 摆回最贴近解剖习惯的方向
                scene.alignRig();
                updateMeasurementUi();
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
        MeasurementType tool = scene.getMeasurementTool();
        // 十字线交点洞：任何模式下都可拖动平移中心（作图时也放开）
        if (scene.isOnHole(pressX, pressY)) {
            dragMode = DragMode.MOVE_CENTER;
            measurementToolbar.setHintText("拖动平移十字线中心");
            return;
        }
        // 命中已有图形：拖动即移动该图形（任何模式都优先，避免被当成新作图而"原地留一个"）
        if (scene.beginMoveMeasurement(pressX, pressY)) {
            dragMode = DragMode.MOVE_SHAPE;
            measurementToolbar.setHintText("拖动移动图形（松手生效）");
            return;
        }
        if (tool != null) {
            if (!scene.isVolumeReady()) {
                return;
            }
            if (tool.isFreehand()) {
                // 自由形状：按住拖动描画
                measurementToolbar.setHintText("描画自由形状（松开闭合）");
                dragMode = DragMode.DRAW;
                scene.beginFreehand(pressX, pressY);
                return;
            }
            if (tool.isPolyline()) {
                handleCurveClick(pressX, pressY);
                return;
            }
            // 固定点数工具：左键落点
            measurementToolbar.setHintText(tool.getDisplayName() + "：落点作图");
            scene.addMeasurementPoint(pressX, pressY);
            updateMeasurementResult();
            return;
        }
        if (scene.isOnCrosshair(pressX, pressY)) {
            // 锁定起始视图：拖动中指针越界也不会把旋转轴切到别的视图
            dragMode = DragMode.ROTATE;
            rotationView = scene.viewAt(pressX, pressY);
            scene.beginRotation();
        } else {
            dragMode = DragMode.NONE;
        }
    }

    public void onLeftButtonUp() {
        if (dragMode == DragMode.MOVE_SHAPE) {
            scene.endMoveMeasurement();
            updateMeasurementResult();
            dragMode = DragMode.NONE;
            return;
        }
        if (dragMode == DragMode.DRAW) {
            // 自由形状：松开闭合
            scene.endFreehand();
            updateMeasurementResult();
            dragMode = DragMode.NONE;
            return;
        }
        if (scene.getMeasurementTool() != null) {
            return;
        }
        int[] position = getInteractor().GetEventPosition();
        boolean isClick = Math.abs(position[0] - pressX) <= CLICK_MOVE_THRESHOLD
                && Math.abs(position[1] - pressY) <= CLICK_MOVE_THRESHOLD;
        if (dragMode == DragMode.NONE && isClick) {
            // 单击：命中测量则选中（不移动中心），否则中心跳到该点
            if (!scene.trySelectMeasurement(position[0], position[1])) {
                scene.moveCenter(position[0], position[1]);
            }
            updateMeasurementResult();
            updateStatus();
        }
        if (dragMode == DragMode.ROTATE) {
            // 松手后保持取景冻结（拖动过程中亦冻结，见方案 B）
            scene.endRotation();
        }
        dragMode = DragMode.NONE;
        rotationView = -1;
        lastRotationAngle = Double.NaN;
    }

    /**
     * 曲线：逐点累加；检测到双击（≤400ms 且位移 ≤5px）则结束并生成测量。
     */
    private void handleCurveClick(int displayX, int displayY) {
        long now = System.currentTimeMillis();
        boolean doubleClick = now - lastCurveClickTime <= DOUBLE_CLICK_MILLIS
                && Math.abs(displayX - lastCurveClickX) <= CLICK_MOVE_THRESHOLD
                && Math.abs(displayY - lastCurveClickY) <= CLICK_MOVE_THRESHOLD;
        lastCurveClickTime = now;
        lastCurveClickX = displayX;
        lastCurveClickY = displayY;
        if (doubleClick) {
            scene.finishMeasurementCurve();
        } else {
            scene.addMeasurementPoint(displayX, displayY);
        }
        updateMeasurementResult();
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
        updateMeasurementUi();
        lastProbe = scene.probe(displayX, displayY);
        updateStatus();
    }

    /**
     * 刷新结果标签：优先显示**选中的**测量，否则显示最近一条。
     */
    private void updateMeasurementResult() {
        Measurement measurement = scene.getSelectedMeasurement();
        if (measurement == null) {
            measurement = scene.getLastMeasurement();
        }
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
        if (dragMode == DragMode.MOVE_SHAPE) {
            scene.updateMoveMeasurement(position[0], position[1]);
            updateMeasurementResult();
            return;
        }
        if (dragMode == DragMode.DRAW) {
            scene.extendFreehand(position[0], position[1]);
            return;
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
        // 悬停选中：指针移到某个图形上即选中它（未命中则取消）
        if (scene.hoverMeasurement(position[0], position[1])) {
            updateMeasurementResult();
        }
        lastProbe = scene.probe(position[0], position[1]);
        updateStatus();
    }

    // ------------------------------------------------------- 工具条回调（MeasurementToolbar.Listener）

    @Override
    public void onToolSelected(MeasurementType type) {
        scene.setMeasurementTool(type);
        measurementToolbar.setCrosshairMode(type == null);
        measurementToolbar.setHintText(type == null ? " " : type.getDisplayName() + "：左键落点");
        updateMeasurementResult();
        requestCanvasFocus();
    }

    @Override
    public void onDeleteSelected() {
        deleteSelectedMeasurement();
    }

    @Override
    public void onDeleteAll() {
        scene.clearMeasurements();
        measurementToolbar.setResultText(null);
        measurementToolbar.setHintText("已全部删除");
        updateMeasurementResult();
        requestCanvasFocus();
    }

    @Override
    public void onResetView() {
        scene.resetView();
        updateMeasurementUi();
        updateStatus();
    }

    @Override
    public void onAlignRig() {
        scene.alignRig();
        updateMeasurementUi();
        updateStatus();
    }

    /**
     * 删除选中的测量；未选中时给出提示。
     */
    private void deleteSelectedMeasurement() {
        boolean deleted = scene.deleteSelectedMeasurement();
        measurementToolbar.setHintText(deleted ? "已删除选中测量" : "未选中测量");
        updateMeasurementResult();
        requestCanvasFocus();
    }

    /**
     * 退出测量模式，回到十字线/平移模式（Esc）。
     */
    private void exitMeasurementMode() {
        scene.setMeasurementTool(null);
        measurementToolbar.setCrosshairMode(true);
        measurementToolbar.setHintText(" ");
        updateMeasurementResult();
    }

    /**
     * 刷新测量工具条的显示状态（结果文本 + 十字线模式）。
     */
    private void updateMeasurementUi() {
        measurementToolbar.setCrosshairMode(scene.getMeasurementTool() == null);
        updateMeasurementResult();
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
