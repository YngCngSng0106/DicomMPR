package com.zlyd.mpr.mpr;

import com.zlyd.mpr.geometry.ScreenFrame;
import com.zlyd.mpr.geometry.Vectors;

import vtk.vtkCamera;
import vtk.vtkRenderer;

/**
 * 从各视图相机读出 {@link ScreenFrame}（焦点、屏幕右/上/视线方向、视口半宽高）的工具。
 *
 * <p>十字线绘制与测量绘制都需要它：平行投影下"世界单位 ↔ 像素"与深度无关，
 * 因此可以据此把几何量换算到屏幕。</p>
 */
final class VtkScreenFrames {

    private static final int VIEW_COUNT = 3;
    private static final double MIN_SCALE = 1e-6;

    private VtkScreenFrames() {
    }

    /**
     * 读取三个视图的屏幕参考系（顺序与视图索引一致）。
     */
    static ScreenFrame[] of(vtkRenderer[] renderers) {
        ScreenFrame[] screens = new ScreenFrame[VIEW_COUNT];
        for (int view = 0; view < VIEW_COUNT; view++) {
            screens[view] = of(renderers[view]);
        }
        return screens;
    }

    /**
     * 读取单个视图的屏幕参考系。
     */
    static ScreenFrame of(vtkRenderer renderer) {
        vtkCamera camera = renderer.GetActiveCamera();
        int[] size = renderer.GetSize();
        double aspect = size[1] > 1 ? (double) size[0] / size[1] : 1.0;
        double scale = Math.max(camera.GetParallelScale(), MIN_SCALE);
        double[] direction = camera.GetDirectionOfProjection();
        double[] up = Vectors.orthogonalize(camera.GetViewUp(), direction);
        return new ScreenFrame(camera.GetFocalPoint(), Vectors.cross(direction, up), up, direction,
                scale * aspect, scale);
    }
}
