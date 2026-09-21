package com.zlyd.mpr.mpr;

import com.zlyd.mpr.geometry.MprViewOrientation;

import vtk.vtkCoordinate;
import vtk.vtkRenderer;
import vtk.vtkTextActor;
import vtk.vtkTextProperty;

/**
 * 三视图的解剖方向标记（H/F/A/P/L/R）：在每个视图四边显示方位字母。
 *
 * <p>使用归一化视口坐标定位，因此与视口布局无关；字号固定不随缩放变化。</p>
 */
public final class VtkOrientationMarkers {

    private static final int VIEW_COUNT = 3;
    private static final int FONT_SIZE = 16;
    private static final double TEXT_RED = 1.0;
    private static final double TEXT_GREEN = 1.0;
    private static final double TEXT_BLUE = 1.0;
    private static final double BACKGROUND_OPACITY = 0.45;
    private static final int BOLD = 1;

    /** 标记位置（归一化视口坐标）：上、下、左、右。 */
    private static final double[][] POSITIONS = {
            {0.5, 0.96}, {0.5, 0.02}, {0.02, 0.5}, {0.96, 0.5}
    };

    private final vtkTextActor[][] actors =
            new vtkTextActor[VIEW_COUNT][MprViewOrientation.LABEL_COUNT];

    /**
     * 在给定 renderer 上创建方向标记（顺序同视图常量）。
     *
     * @param renderers 三个视图的 renderer
     */
    public VtkOrientationMarkers(vtkRenderer[] renderers) {
        for (int view = 0; view < VIEW_COUNT; view++) {
            for (int slot = 0; slot < MprViewOrientation.LABEL_COUNT; slot++) {
                vtkTextActor actor = new vtkTextActor();
                actor.SetInput("");
                actor.SetTextScaleModeToNone();

                vtkTextProperty property = actor.GetTextProperty();
                property.SetFontSize(FONT_SIZE);
                property.SetColor(TEXT_RED, TEXT_GREEN, TEXT_BLUE);
                property.SetBold(BOLD);
                property.SetJustificationToCentered();
                // 半透明黑底：避免白字压在亮图像上看不清
                property.SetBackgroundColor(0.0, 0.0, 0.0);
                property.SetBackgroundOpacity(BACKGROUND_OPACITY);

                vtkCoordinate coordinate = actor.GetPositionCoordinate();
                coordinate.SetCoordinateSystemToNormalizedViewport();
                coordinate.SetValue(POSITIONS[slot][0], POSITIONS[slot][1]);

                renderers[view].AddActor2D(actor);
                actors[view][slot] = actor;
            }
        }
    }

    /**
     * 更新标记文本。
     *
     * @param labelsPerView 每个视图 4 个标签（上、下、左、右）
     */
    public void update(String[][] labelsPerView) {
        for (int view = 0; view < VIEW_COUNT; view++) {
            for (int slot = 0; slot < MprViewOrientation.LABEL_COUNT; slot++) {
                actors[view][slot].SetInput(labelsPerView[view][slot]);
            }
        }
    }
}
