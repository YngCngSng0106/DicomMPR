package com.zlyd.mpr.mpr;

import java.awt.event.KeyEvent;

import org.apache.commons.lang3.StringUtils;

import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.geometry.VoxelProbe;
import com.zlyd.mpr.ui.WindowLevelToolbar;

import vtk.vtkRenderWindowInteractor;

/**
 * M2.1 单平面阅片视图。
 *
 * <p>交互：滚轮/上下键翻层，左键拖动调窗宽窗位，中键拖动平移，右键拖动缩放，R 重置。</p>
 */
public final class VtkStackView extends VtkViewPanel {

    private static final long serialVersionUID = 1L;
    private static final int PAGE_STEP = 10;

    private final StackScene scene;
    private final WindowLevelToolbar windowLevelToolbar = new WindowLevelToolbar();
    private SeriesInfo series;
    private VoxelProbe lastProbe;

    private boolean leftDown;
    private boolean middleDown;
    private boolean rightDown;
    private int lastX;
    private int lastY;

    public VtkStackView() {
        super("请双击或拖拽左侧序列到此处");
        scene = new StackScene(getCanvas());
        windowLevelToolbar.setWindowLevelListener(scene::setWindowLevel);
        getPrimaryRow().add(windowLevelToolbar);
        registerObservers();
    }

    private void registerObservers() {
        vtkRenderWindowInteractor interactor = getInteractor();
        interactor.AddObserver("MouseWheelForwardEvent", this, "onWheelForward");
        interactor.AddObserver("MouseWheelBackwardEvent", this, "onWheelBackward");
        interactor.AddObserver("LeftButtonPressEvent", this, "onLeftDown");
        interactor.AddObserver("LeftButtonReleaseEvent", this, "onLeftUp");
        interactor.AddObserver("MiddleButtonPressEvent", this, "onMiddleDown");
        interactor.AddObserver("MiddleButtonReleaseEvent", this, "onMiddleUp");
        interactor.AddObserver("RightButtonPressEvent", this, "onRightDown");
        interactor.AddObserver("RightButtonReleaseEvent", this, "onRightUp");
        interactor.AddObserver("MouseMoveEvent", this, "onMouseMove");
        interactor.AddObserver("LeaveEvent", this, "onLeave");
    }

    @Override
    protected String defaultExportName() {
        return "slice";
    }

    @Override
    protected void onVolumeReady(BuiltVolume volume, SeriesInfo target) {
        this.series = target;
        scene.setVolume(volume, target);
        windowLevelToolbar.setCurrent(scene.getWindowLevel());
        updateStatus();
        requestCanvasFocus();
    }

    @Override
    protected void onKeyPressed(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.VK_UP:
                stepSlice(-1);
                break;
            case KeyEvent.VK_DOWN:
                stepSlice(1);
                break;
            case KeyEvent.VK_PAGE_UP:
                stepSlice(-PAGE_STEP);
                break;
            case KeyEvent.VK_PAGE_DOWN:
                stepSlice(PAGE_STEP);
                break;
            case KeyEvent.VK_HOME:
                jumpTo(0);
                break;
            case KeyEvent.VK_END:
                jumpTo(scene.getSliceCount() - 1);
                break;
            case KeyEvent.VK_R:
                scene.resetView();
                break;
            default:
                break;
        }
    }

    public void onWheelForward() {
        stepSlice(-1);
    }

    public void onWheelBackward() {
        stepSlice(1);
    }

    public void onLeftDown() {
        leftDown = true;
        recordPosition();
    }

    public void onLeftUp() {
        leftDown = false;
    }

    public void onMiddleDown() {
        middleDown = true;
        recordPosition();
    }

    public void onMiddleUp() {
        middleDown = false;
    }

    public void onRightDown() {
        rightDown = true;
        recordPosition();
    }

    public void onRightUp() {
        rightDown = false;
    }

    public void onLeave() {
        leftDown = false;
        middleDown = false;
        rightDown = false;
    }

    public void onMouseMove() {
        if (!scene.isVolumeReady()) {
            return;
        }
        int[] position = getInteractor().GetEventPosition();
        VoxelProbe probe = scene.probe(position[0], position[1]);
        if (probe != lastProbe) {
            lastProbe = probe;
            updateStatus();
        }
        int dx = position[0] - lastX;
        int dy = position[1] - lastY;
        lastX = position[0];
        lastY = position[1];

        if (leftDown) {
            scene.adjustWindowLevel(dx, dy);
            windowLevelToolbar.setCurrent(scene.getWindowLevel());
        } else if (middleDown) {
            scene.pan(dx, dy);
        } else if (rightDown) {
            scene.zoom(dy);
        }
    }

    private void stepSlice(int direction) {
        scene.stepSlice(direction);
        updateStatus();
    }

    private void jumpTo(int index) {
        scene.setSlice(index);
        updateStatus();
    }

    private void recordPosition() {
        int[] position = getInteractor().GetEventPosition();
        lastX = position[0];
        lastY = position[1];
    }

    private void updateStatus() {
        String description = series == null
                ? "-" : StringUtils.defaultIfBlank(series.getAttributes().getSeriesDescription(), "-");
        String probeText = lastProbe == null ? "HU=-" : String.format("HU=%.0f", lastProbe.getValue());
        setStatus(String.format(
                "%s   |   层 %d / %d   |   %s   |   滚轮/↑↓翻层 · 左键调窗 · 中键平移 · 右键缩放 · R 重置",
                description, scene.getSliceIndex() + 1,
                Math.max(scene.getSliceCount(), scene.getSliceIndex() + 1), probeText));
    }
}
