package com.zlyd.mpr.mpr;

import java.util.List;

import com.zlyd.mpr.geometry.CrosshairGeometry;
import com.zlyd.mpr.geometry.CrosshairSegment;
import com.zlyd.mpr.geometry.MprCursorFrame;
import com.zlyd.mpr.geometry.ScreenFrame;
import com.zlyd.mpr.geometry.Vectors;

import vtk.vtkActor;
import vtk.vtkCamera;
import vtk.vtkLineSource;
import vtk.vtkPolyDataMapper;
import vtk.vtkRenderer;

/**
 * MPR 十字线的绘制层：把 {@link CrosshairGeometry} 算出的线段映射到三个视图的演员上。
 *
 * <p>颜色按"切面"划分（同一平面在三个视图里同色）：矢状面(X)=蓝、冠状面(Y)=绿、轴位面(Z)=红；
 * 每个视图内两条线的颜色由 {@link #VIEW_PLANES} 决定。线条关闭光照，保证在任何视图都可见。</p>
 */
public final class MprCrosshairOverlay {

    /** 十字线代表的切面（含配色）：矢状面 X=i=蓝、冠状面 Y=j=绿、轴位面 Z=k=红。 */
    private enum Plane {
        /** 矢状面 X=i → 蓝。 */
        SAGITTAL(0.25, 0.50, 1.00),
        /** 冠状面 Y=j → 绿。 */
        CORONAL(0.10, 0.90, 0.10),
        /** 轴位面 Z=k → 红。 */
        AXIAL(0.95, 0.15, 0.15);

        private final double red;
        private final double green;
        private final double blue;

        Plane(double red, double green, double blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }

    /** 每个视图两条线：[竖线, 横线] 各自代表的切面（索引同 CrosshairSegment：轴位/矢状/冠状）。 */
    private static final Plane[][] VIEW_PLANES = {
            {Plane.SAGITTAL, Plane.CORONAL},
            {Plane.CORONAL, Plane.AXIAL},
            {Plane.SAGITTAL, Plane.AXIAL}
    };

    private static final int VIEW_COUNT = 3;
    private static final int SLOTS_PER_VIEW = CrosshairGeometry.LINE_COUNT * CrosshairGeometry.SEGMENT_COUNT;
    private static final double GAP_HALF_PIXELS = 5.0;
    private static final int FALLBACK_VIEW_HEIGHT = 320;
    private static final double MAX_GAP_RATIO = 0.2;
    /** 朝相机方向的微小深度偏移（占视口半高的比例，约 0.5 像素）：避免与切片图像共面被深度测试遮挡。 */
    private static final double DEPTH_OFFSET_RATIO = 0.001;
    private static final float LINE_WIDTH = 2.0f;

    private final vtkRenderer[] renderers;
    private final vtkLineSource[][] sources = new vtkLineSource[VIEW_COUNT][SLOTS_PER_VIEW];
    private final vtkActor[][] actors = new vtkActor[VIEW_COUNT][SLOTS_PER_VIEW];

    /**
     * 在给定 renderer 上创建十字线（顺序须与 CrosshairSegment 的视图常量一致）。
     *
     * @param renderers 三个视图的 renderer
     */
    public MprCrosshairOverlay(vtkRenderer[] renderers) {
        this.renderers = renderers;
        for (int view = 0; view < VIEW_COUNT; view++) {
            for (int slot = 0; slot < SLOTS_PER_VIEW; slot++) {
                Plane plane = VIEW_PLANES[view][slot / CrosshairGeometry.SEGMENT_COUNT];
                vtkLineSource source = new vtkLineSource();
                vtkPolyDataMapper mapper = new vtkPolyDataMapper();
                mapper.SetInputConnection(source.GetOutputPort());
                vtkActor actor = new vtkActor();
                actor.SetMapper(mapper);
                actor.GetProperty().SetColor(plane.red, plane.green, plane.blue);
                actor.GetProperty().SetLineWidth(LINE_WIDTH);
                actor.GetProperty().LightingOff();
                renderers[view].AddActor(actor);
                sources[view][slot] = source;
                actors[view][slot] = actor;
            }
        }
    }

    /**
     * 按光标坐标系刷新十字线（斜切通用）。
     *
     * <p>线段的延伸范围取**视口矩形**（而非体数据包围盒），因此十字线始终铺满整个视图展示区，
     * 不会因斜切/偏心而整条消失。</p>
     *
     * @param frame 光标坐标系（三平面与交点 C）
     * @return 本次绘制的线段（供命中判定复用）
     */
    public List<CrosshairSegment> update(MprCursorFrame frame) {
        ScreenFrame[] screens = new ScreenFrame[VIEW_COUNT];
        for (int view = 0; view < VIEW_COUNT; view++) {
            screens[view] = screenFrame(view);
        }
        List<CrosshairSegment> segments = CrosshairGeometry.compute(frame, screens, gaps(screens));
        for (CrosshairSegment segment : segments) {
            apply(segment, screens[segment.getView()]);
        }
        return segments;
    }

    /**
     * 由相机读出某视图的屏幕参考系（焦点、屏幕右/上/视线方向、视口半宽高）。
     */
    private ScreenFrame screenFrame(int view) {
        vtkRenderer renderer = renderers[view];
        vtkCamera camera = renderer.GetActiveCamera();
        int[] size = renderer.GetSize();
        double aspect = size[1] > 1 ? (double) size[0] / size[1] : 1.0;
        double scale = Math.max(camera.GetParallelScale(), 1e-6);
        double[] direction = camera.GetDirectionOfProjection();
        double[] up = Vectors.orthogonalize(camera.GetViewUp(), direction);
        return new ScreenFrame(camera.GetFocalPoint(), Vectors.cross(direction, up), up, direction,
                scale * aspect, scale);
    }

    /**
     * 隐藏全部十字线（清理 MPR 缓存用）。
     */
    public void release() {
        for (int view = 0; view < VIEW_COUNT; view++) {
            for (int slot = 0; slot < SLOTS_PER_VIEW; slot++) {
                actors[view][slot].SetVisibility(0);
            }
        }
    }

    private double[] gaps(ScreenFrame[] screens) {
        double[] gaps = new double[VIEW_COUNT];
        for (int view = 0; view < VIEW_COUNT; view++) {
            int[] size = renderers[view].GetSize();
            int height = size[1] > 1 ? size[1] : FALLBACK_VIEW_HEIGHT;
            double gap = 2.0 * screens[view].getHalfHeight() / height * GAP_HALF_PIXELS;
            double minExtent = 2.0 * Math.min(screens[view].getHalfWidth(), screens[view].getHalfHeight());
            gaps[view] = Math.min(gap, minExtent * MAX_GAP_RATIO);
        }
        return gaps;
    }

    private void apply(CrosshairSegment segment, ScreenFrame screen) {
        int slot = segment.getLine() * CrosshairGeometry.SEGMENT_COUNT + segment.getSegment();
        double offset = DEPTH_OFFSET_RATIO * screen.getHalfHeight();
        double[] start = towardCamera(segment.getStart(), screen, offset);
        double[] end = towardCamera(segment.getEnd(), screen, offset);
        sources[segment.getView()][slot].SetPoint1(start[0], start[1], start[2]);
        sources[segment.getView()][slot].SetPoint2(end[0], end[1], end[2]);
        actors[segment.getView()][slot].SetVisibility(segment.isVisible() ? 1 : 0);
    }

    /**
     * 把点沿"朝相机"方向（−视线方向）平移，使十字线始终压在切片图像之上。
     */
    private static double[] towardCamera(double[] point, ScreenFrame screen, double offset) {
        double[] direction = screen.getViewDirection();
        return new double[]{
                point[0] - direction[0] * offset,
                point[1] - direction[1] * offset,
                point[2] - direction[2] * offset};
    }

}
