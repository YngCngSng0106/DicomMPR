package com.zlyd.mpr.mpr;

import com.zlyd.mpr.geometry.MprCursorFrame;
import com.zlyd.mpr.geometry.MprViewRig;

import vtk.vtkImageData;
import vtk.vtkImageInterpolator;
import vtk.vtkImageResliceMapper;
import vtk.vtkImageSlice;
import vtk.vtkPlane;
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

    private final vtkPlane[] planes = new vtkPlane[VIEW_COUNT];
    private final vtkImageResliceMapper[] mappers = new vtkImageResliceMapper[VIEW_COUNT];
    private final vtkImageSlice[] slices = new vtkImageSlice[VIEW_COUNT];
    private final vtkImageInterpolator interpolator = new vtkImageInterpolator();

    MprSlicePlaneActors(vtkRenderer[] renderers) {
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
        }
    }

    /**
     * 供窗宽窗位控制器使用的演员数组。
     */
    vtkImageSlice[] slices() {
        return slices;
    }
}
