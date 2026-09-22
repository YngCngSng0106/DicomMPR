package com.zlyd.mpr.mpr;

import com.zlyd.mpr.geometry.MprCursorFrame;
import com.zlyd.mpr.geometry.MprViewRig;
import com.zlyd.mpr.geometry.ScreenFrame;

import vtk.vtkImageData;
import vtk.vtkImageInterpolator;
import vtk.vtkImageResliceMapper;
import vtk.vtkImageSlice;
import vtk.vtkPlane;
import vtk.vtkShortArray;
import vtk.vtkRenderer;

/**
 * MPR 三视图的切面显示：每个视图一套 {@code vtkImageResliceMapper + vtkPlane}，支持任意斜切平面。
 *
 * <p>体数据不重采样：每帧只更新平面（过交点 C、法向为各视图平面法向），因此斜切是实时的。</p>
 *
 * <p>关键设置来自 S0 预检（`M0ResliceSmoke`，逐像素校验通过）：显式平面、关闭"面向相机/贴合焦点/
 * 吸附体素层"、窗宽窗位在 HU 域单独处理、线性插值、按屏幕像素重采样。</p>
 */
final class MprSlicePlaneActors {

    private static final int VIEW_COUNT = MprViewRig.VIEW_COUNT;
    private static final int IMAGE_SAMPLE_FACTOR = 2;
    /**
     * 切片图像沿视线方向后退的比例（占视口半高，约 1 像素）。
     *
     * <p>平行投影下"沿视线平移"在画面上看不出任何变化，但深度上能让与图像共面的
     * 十字线/测量轮廓稳定压在其上（否则深度测试平手，线会被图像压掉）。</p>
     */
    private static final double DEPTH_NUDGE_RATIO = 0.002;

    private final vtkPlane[] planes = new vtkPlane[VIEW_COUNT];
    private final vtkImageResliceMapper[] mappers = new vtkImageResliceMapper[VIEW_COUNT];
    private final vtkImageSlice[] slices = new vtkImageSlice[VIEW_COUNT];
    private final vtkImageInterpolator interpolator = new vtkImageInterpolator();
    private final vtkRenderer[] renderers;

    MprSlicePlaneActors(vtkRenderer[] renderers) {
        this.renderers = renderers;
        interpolator.SetInterpolationModeToLinear();
        for (int view = 0; view < VIEW_COUNT; view++) {
            vtkPlane plane = new vtkPlane();
            vtkImageResliceMapper mapper = new vtkImageResliceMapper();
            mapper.SetSlicePlane(plane);
            mapper.SetSliceFacesCamera(0);
            mapper.SetSliceAtFocalPoint(0);
            mapper.SetJumpToNearestSlice(0);
            mapper.SetSeparateWindowLevelOperation(1);
            mapper.SetAutoAdjustImageQuality(0);
            mapper.SetImageSampleFactor(IMAGE_SAMPLE_FACTOR);
            mapper.SetResampleToScreenPixels(1);
            mapper.SetInterpolator(interpolator);

            vtkImageSlice slice = new vtkImageSlice();
            slice.SetMapper(mapper);
            slice.GetProperty().SetInterpolationTypeToLinear();
            slice.SetVisibility(0);
            renderers[view].AddActor(slice);

            planes[view] = plane;
            mappers[view] = mapper;
            slices[view] = slice;
        }
    }

    /**
     * 设置体数据输入。
     */
    void setInput(vtkImageData volume) {
        for (int view = 0; view < VIEW_COUNT; view++) {
            mappers[view].SetInputData(volume);
        }
    }

    /**
     * 释放体数据引用并隐藏切片（清理 MPR 缓存用）。
     *
     * <p>注意：不能用 {@code RemoveAllInputs()}（该方法要求端口式输入，在 mapper 上会抛 C++ 异常），
     * 这里把输入替换成 1×1×1 的占位体数据，从而切断对大体积体数据的引用、便于内存回收。</p>
     */
    void release() {
        vtkImageData placeholder = placeholderVolume();
        for (int view = 0; view < VIEW_COUNT; view++) {
            mappers[view].SetInputData(placeholder);
            slices[view].SetVisibility(0);
        }
    }

    /**
     * 1×1×1、单值 0 的占位体数据（避免 mapper 处于"无输入/无标量"的异常状态）。
     */
    private static vtkImageData placeholderVolume() {
        vtkImageData image = new vtkImageData();
        image.SetDimensions(1, 1, 1);
        image.SetSpacing(1.0, 1.0, 1.0);
        image.SetOrigin(0.0, 0.0, 0.0);
        vtkShortArray scalars = new vtkShortArray();
        scalars.SetNumberOfComponents(1);
        scalars.SetNumberOfTuples(1);
        scalars.SetTuple1(0, 0);
        image.GetPointData().SetScalars(scalars);
        return image;
    }

    /**
     * 统一设置可见性。
     */
    void setVisibility(int visible) {
        for (vtkImageSlice slice : slices) {
            slice.SetVisibility(visible);
        }
    }

    /**
     * 按光标坐标系更新三个切面平面（过交点 C，法向为各视图平面的法向）。
     */
    void update(MprCursorFrame frame) {
        double[] center = frame.center();
        for (int view = 0; view < VIEW_COUNT; view++) {
            double[] normal = frame.axis(view);
            planes[view].SetOrigin(center[0], center[1], center[2]);
            planes[view].SetNormal(normal[0], normal[1], normal[2]);
            // 图像沿视线方向后退约 1 像素：画面无变化，但共面的十字线/测量轮廓不再被压掉
            ScreenFrame screen = VtkScreenFrames.of(renderers[view]);
            double[] direction = screen.getViewDirection();
            double nudge = DEPTH_NUDGE_RATIO * screen.getHalfHeight();
            slices[view].SetPosition(direction[0] * nudge, direction[1] * nudge,
                    direction[2] * nudge);
        }
    }

    /**
     * 供窗宽窗位控制器使用的演员数组。
     */
    vtkImageSlice[] slices() {
        return slices;
    }
}
