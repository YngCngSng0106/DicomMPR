package com.zlyd.mpr.mpr;

import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.util.WindowLevel;
import com.zlyd.mpr.util.WindowLevelDefaults;

import vtk.vtkCanvas;
import vtk.vtkImageSlice;

/**
 * MPR 窗宽窗位控制（E1）：保存当前值并联动三个视图，支持预设设置与鼠标拖动微调。
 */
final class MprWindowLevelController {

    private static final double SENSITIVITY = 4.0;
    private static final double MIN_WINDOW = 1.0;
    private static final int MIN_PIXELS = 1;

    private final vtkCanvas canvas;
    private final vtkImageSlice[] slices;
    private WindowLevel windowLevel;

    MprWindowLevelController(vtkCanvas canvas, vtkImageSlice[] slices) {
        this.canvas = canvas;
        this.slices = slices;
    }

    /**
     * 按序列的 DICOM 窗宽窗位初始化并应用。
     */
    void reset(SeriesInfo series) {
        setWindowLevel(WindowLevelDefaults.forSeries(series));
    }

    void setWindowLevel(WindowLevel level) {
        this.windowLevel = level;
        for (vtkImageSlice slice : slices) {
            slice.GetProperty().SetColorWindow(level.getWidth());
            slice.GetProperty().SetColorLevel(level.getCenter());
        }
    }

    WindowLevel getWindowLevel() {
        return windowLevel;
    }

    /**
     * 按鼠标位移增量调节（右键拖动）。
     */
    void adjust(int dx, int dy) {
        if (windowLevel == null) {
            return;
        }
        double window = Math.max(MIN_WINDOW,
                windowLevel.getWidth() + relativeDelta(dx, canvas.getWidth(), windowLevel.getWidth()));
        double level = windowLevel.getCenter()
                + relativeDelta(dy, canvas.getHeight(), windowLevel.getCenter());
        setWindowLevel(new WindowLevel(window, level));
    }

    private double relativeDelta(int pixels, int viewSize, double current) {
        return pixels * SENSITIVITY / Math.max(viewSize, MIN_PIXELS) * Math.max(Math.abs(current), 1.0);
    }
}
