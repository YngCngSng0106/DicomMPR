package com.zlyd.mpr.mpr;

import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.dicom.VolumeGeometry;
import com.zlyd.mpr.geometry.MprViewOrientation;
import com.zlyd.mpr.geometry.VoxelProbe;
import com.zlyd.mpr.util.WindowLevel;
import com.zlyd.mpr.util.WindowLevelDefaults;

import vtk.vtkCamera;
import vtk.vtkCanvas;
import vtk.vtkImageData;
import vtk.vtkImageSlice;
import vtk.vtkImageSliceMapper;
import vtk.vtkRenderer;

/**
 * 单平面阅片场景：封装渲染对象与翻层 / 调窗 / 平移 / 缩放操作。
 */
final class StackScene {

    private static final double ZOOM_STEP = 0.01;
    private static final double MIN_PARALLEL_SCALE = 1e-3;
    private static final double MAX_PARALLEL_SCALE = 1e6;
    private static final double WINDOW_LEVEL_SENSITIVITY = 4.0;
    private static final double MIN_WINDOW = 1.0;
    private static final int MIN_PIXELS = 1;

    private final vtkCanvas canvas;
    private final vtkRenderer renderer;
    private final vtkImageSliceMapper mapper = new vtkImageSliceMapper();
    private final vtkImageSlice imageSlice = new vtkImageSlice();

    private int sliceCount;
    private int sliceIndex;
    private WindowLevel windowLevel;
    private VolumeGeometry geometry;
    private MprVolumeProbe volumeProbe;
    private boolean volumeReady;

    StackScene(vtkCanvas canvas) {
        this.canvas = canvas;
        this.renderer = canvas.GetRenderer();
        renderer.SetBackground(0.05, 0.05, 0.05);
        mapper.SetOrientationToK();
        imageSlice.SetMapper(mapper);
        imageSlice.GetProperty().SetInterpolationTypeToLinear();
        imageSlice.SetVisibility(0);
        renderer.AddActor(imageSlice);
    }

    void setVolume(BuiltVolume volume, SeriesInfo series) {
        geometry = volume.getGeometry();
        sliceCount = geometry.getSlices();
        sliceIndex = sliceCount / 2;
        volumeReady = true;

        mapper.SetInputData(volume.getImage());
        mapper.SetSliceNumber(sliceIndex);

        windowLevel = WindowLevelDefaults.forSeries(series);
        volumeProbe = new MprVolumeProbe(geometry, volume.getImage());
        applyWindowLevel();
        imageSlice.SetVisibility(1);

        resetView();
    }

    /**
     * 设置窗宽窗位（E1）。
     */
    void setWindowLevel(WindowLevel level) {
        this.windowLevel = level;
        applyWindowLevel();
        render();
    }

    /**
     * 光标处探测：返回体素索引与数值（D6，2D 阅片）。
     *
     * @param displayX 屏幕 X
     * @param displayY 屏幕 Y
     * @return 探测结果；不在体数据范围内返回 {@code null}
     */
    VoxelProbe probe(int displayX, int displayY) {
        if (!volumeReady || volumeProbe == null) {
            return null;
        }
        double[] world = displayToWorld(displayX, displayY);
        if (world == null) {
            return null;
        }
        // 2D 阅片显示的是采集平面（mapper 方向 K），故按轴位取平面内 i、j，k 用当前层
        return volumeProbe.probe(MprViewOrientation.VIEW_AXIAL, world);
    }

    private double[] displayToWorld(int displayX, int displayY) {
        renderer.SetDisplayPoint(displayX, displayY, 0.0);
        renderer.DisplayToWorld();
        double[] world = renderer.GetWorldPoint();
        if (world[3] == 0.0) {
            return null;
        }
        return new double[]{world[0] / world[3], world[1] / world[3], world[2] / world[3]};
    }

    WindowLevel getWindowLevel() {
        return windowLevel;
    }

    private void applyWindowLevel() {
        imageSlice.GetProperty().SetColorWindow(windowLevel.getWidth());
        imageSlice.GetProperty().SetColorLevel(windowLevel.getCenter());
    }

    boolean isVolumeReady() {
        return volumeReady;
    }

    int getSliceIndex() {
        return sliceIndex;
    }

    int getSliceCount() {
        return sliceCount;
    }

    void stepSlice(int direction) {
        setSlice(sliceIndex + direction);
    }

    void setSlice(int index) {
        if (!volumeReady) {
            return;
        }
        int clamped = Math.max(0, Math.min(sliceCount - 1, index));
        if (clamped == sliceIndex) {
            return;
        }
        sliceIndex = clamped;
        mapper.SetSliceNumber(sliceIndex);
        render();
    }

    void adjustWindowLevel(int dx, int dy) {
        double window = Math.max(MIN_WINDOW, windowLevel.getWidth()
                + relativeDelta(dx, canvas.getWidth(), windowLevel.getWidth()));
        double level = windowLevel.getCenter() + relativeDelta(dy, canvas.getHeight(), windowLevel.getCenter());
        setWindowLevel(new WindowLevel(window, level));
    }

    void pan(int dx, int dy) {
        vtkCamera camera = renderer.GetActiveCamera();
        double worldPerPixel = 2.0 * camera.GetParallelScale() / Math.max(canvas.getHeight(), MIN_PIXELS);
        double[] right = rightVector(camera);
        double[] up = camera.GetViewUp();

        double offsetX = (-dx * right[0] - dy * up[0]) * worldPerPixel;
        double offsetY = (-dx * right[1] - dy * up[1]) * worldPerPixel;
        double offsetZ = (-dx * right[2] - dy * up[2]) * worldPerPixel;

        double[] position = camera.GetPosition();
        double[] focalPoint = camera.GetFocalPoint();
        camera.SetPosition(position[0] + offsetX, position[1] + offsetY, position[2] + offsetZ);
        camera.SetFocalPoint(focalPoint[0] + offsetX, focalPoint[1] + offsetY, focalPoint[2] + offsetZ);
        renderer.ResetCameraClippingRange();
        render();
    }

    void zoom(int dy) {
        vtkCamera camera = renderer.GetActiveCamera();
        camera.ParallelProjectionOn();
        double scale = camera.GetParallelScale() * Math.pow(1.0 + ZOOM_STEP, dy);
        camera.SetParallelScale(Math.max(MIN_PARALLEL_SCALE, Math.min(MAX_PARALLEL_SCALE, scale)));
        renderer.ResetCameraClippingRange();
        render();
    }

    void resetView() {
        renderer.ResetCamera();
        renderer.ResetCameraClippingRange();
        renderer.GetActiveCamera().ParallelProjectionOn();
        render();
    }

    void render() {
        canvas.Render();
    }

    private double relativeDelta(int pixels, int viewSize, double current) {
        return pixels * WINDOW_LEVEL_SENSITIVITY / Math.max(viewSize, MIN_PIXELS) * Math.max(Math.abs(current), 1.0);
    }

    private double[] rightVector(vtkCamera camera) {
        double[] direction = camera.GetDirectionOfProjection();
        double[] up = camera.GetViewUp();
        double x = direction[1] * up[2] - direction[2] * up[1];
        double y = direction[2] * up[0] - direction[0] * up[2];
        double z = direction[0] * up[1] - direction[1] * up[0];
        double norm = Math.sqrt(x * x + y * y + z * z);
        if (norm <= 0) {
            return new double[]{0, 0, 0};
        }
        return new double[]{x / norm, y / norm, z / norm};
    }
}
