package com.zlyd.mpr.mpr;

import vtk.vtkRenderWindowInteractor;
import vtk.vtkRenderer;

/**
 * 三视图的坐标与命中换算：屏幕 ↔ 世界、指针落在哪个视图。
 */
public final class MprViewMapper {

    private static final int NO_VIEW = -1;

    private final vtkRenderWindowInteractor interactor;
    private final vtkRenderer[] renderers;

    public MprViewMapper(vtkRenderWindowInteractor interactor, vtkRenderer[] renderers) {
        this.interactor = interactor;
        this.renderers = renderers;
    }

    /**
     * 指针所在视图。
     *
     * @return 视图索引；不在任何视图内返回 {@code -1}
     */
    public int viewAt(int displayX, int displayY) {
        vtkRenderer poked = interactor.FindPokedRenderer(displayX, displayY);
        if (poked == null) {
            return NO_VIEW;
        }
        for (int view = 0; view < renderers.length; view++) {
            if (poked == renderers[view]) {
                return view;
            }
        }
        return NO_VIEW;
    }

    /**
     * 当前指针所在视图；不在任何视图内时返回轴位。
     */
    public int activeView() {
        int[] position = interactor.GetEventPosition();
        int view = viewAt(position[0], position[1]);
        return view == NO_VIEW ? MprScene.AXIAL : view;
    }

    /**
     * 屏幕坐标 → 世界坐标（患者坐标）。
     */
    public double[] displayToWorld(int view, int displayX, int displayY) {
        vtkRenderer renderer = renderers[view];
        renderer.SetDisplayPoint(displayX, displayY, 0.0);
        renderer.DisplayToWorld();
        double[] world = renderer.GetWorldPoint();
        if (world[3] == 0.0) {
            return null;
        }
        return new double[]{world[0] / world[3], world[1] / world[3], world[2] / world[3]};
    }

    /**
     * 世界坐标 → 屏幕坐标（返回 [x, y]）。
     */
    public double[] worldToDisplay(int view, double[] world) {
        vtkRenderer renderer = renderers[view];
        renderer.SetWorldPoint(world[0], world[1], world[2], 1.0);
        renderer.WorldToDisplay();
        double[] display = renderer.GetDisplayPoint();
        return new double[]{display[0], display[1]};
    }
}
