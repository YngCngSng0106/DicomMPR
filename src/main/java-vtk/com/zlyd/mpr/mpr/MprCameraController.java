package com.zlyd.mpr.mpr;

import java.util.Arrays;

import com.zlyd.mpr.geometry.MprCursorFrame;
import com.zlyd.mpr.geometry.MprViewRig;
import com.zlyd.mpr.geometry.Vectors;
import com.zlyd.mpr.geometry.VolumeBox;

import vtk.vtkCamera;
import vtk.vtkRenderer;

/**
 * MPR 三视图相机控制：按"机架（视线/上方）"摆位，并保证交点 C 的屏幕位置在旋转时不变。
 *
 * <h2>取景</h2>
 * 平行投影；{@code ParallelScale} 取"体数据盒在当前屏幕两轴上的支撑半长"（只与朝向有关，
 * 因此旋转时自动放缩到不裁角，轴对齐时与改造前的紧致取景逐值一致）。
 *
 * <h2>交点钉住</h2>
 * 每视图记录交点 C 的**归一化屏幕偏移** {@code (x0, y0)}（单位：ParallelScale），
 * 焦点取 {@code focal = C − S·(x0·right + y0·up)}，于是 C 的屏幕位置恒等于记录值——
 * 旋转（S 变化）时也严格不变（代价：焦点随缩放绕 C 微移）。
 */
public final class MprCameraController {

    private static final int VIEW_COUNT = MprViewRig.VIEW_COUNT;
    private static final double FIT_MARGIN = 1.0;
    private static final double MIN_PARALLEL_SCALE = 1.0;
    private static final double DISTANCE_FACTOR = 2.0;
    private static final double EPSILON = 1e-9;

    private final vtkRenderer[] renderers;
    private final double[] anchorX = new double[VIEW_COUNT];
    private final double[] anchorY = new double[VIEW_COUNT];
    private final double[] frozenScales = new double[VIEW_COUNT];

    private boolean scaleFrozen;

    public MprCameraController(vtkRenderer[] renderers) {
        this.renderers = renderers;
    }

    /**
     * 开始旋转拖动：冻结各视图当前取景，旋转过程中画面尺寸完全不变（避免"呼吸"）。
     *
     * <p>冻结**在松手后继续保持**（允许斜切面四角被裁）：只有显式重新取景
     * （{@link #reset}、{@link #clearScaleFreeze()} 后重新配置、{@link #refitVolume}）才会改变取景。</p>
     */
    public void beginRotation() {
        for (int view = 0; view < VIEW_COUNT; view++) {
            frozenScales[view] = Math.max(renderers[view].GetActiveCamera().GetParallelScale(),
                    MIN_PARALLEL_SCALE);
        }
        scaleFrozen = true;
    }

    /**
     * 旋转拖动结束：保持取景冻结（按用户选择，松手不重新取景、允许裁角）。
     */
    public void endRotation() {
        // 有意不做处理：冻结延续到下一次显式重新取景
    }

    /**
     * 解除取景冻结（"回正"/重置等显式重新取景前调用）。
     */
    public void clearScaleFreeze() {
        scaleFrozen = false;
    }

    /**
     * 重置：交点回到屏幕中心（锚点归零）并按当前机架配置三个视图。
     */
    public void reset(MprCursorFrame frame, VolumeBox box, MprViewRig rig) {
        Arrays.fill(anchorX, 0.0);
        Arrays.fill(anchorY, 0.0);
        clearScaleFreeze();
        configure(frame, box, rig, allViews(), true);
    }

    /**
     * 按机架摆放相机（装载/重置/旋转后调用）。
     *
     * @param frame 光标坐标系（提供交点 C）
     * @param box 体数据包围盒（提供取景范围与退避距离）
     * @param rig 各视图的机架
     * @param views 需要配置的视图（被旋转的视图应排除，以保持"当前视图冻结"）
     */
    public void configure(MprCursorFrame frame, VolumeBox box, MprViewRig rig, int[] views) {
        configure(frame, box, rig, views, false);
    }

    /**
     * 按机架摆放相机。
     *
     * @param frameRule 是否强制以机架轴作为 up（装载/重置等确定性场合用 true；
     *                  false 时取"与相机当前 up 夹角最小"的候选，保持滚动连续，
     *                  避免"先转某视图 90° 再碰另一个视图时前一视图瞬间歪掉"）
     */
    public void configure(MprCursorFrame frame, VolumeBox box, MprViewRig rig, int[] views,
                          boolean frameRule) {
        double[] center = frame.center();
        double distance = box.diagonal() * DISTANCE_FACTOR;
        for (int view : views) {
            double[] direction = rig.direction(frame, view);
            double[] up = chooseUp(rig, frame, view, frameRule);
            double[] right = Vectors.cross(direction, up);
            double scale = effectiveScale(box, renderers[view], right, up, view);
            double[] focal = focalPoint(center, right, up, scale, view);
            applyCamera(renderers[view], focal, direction, up, scale, distance);
        }
    }

    /**
     * 记录交点 C 当前的归一化屏幕偏移（移动中心后调用；不移动相机）。
     */
    public void captureAnchors(MprCursorFrame frame, int[] views) {
        double[] center = frame.center();
        for (int view : views) {
            vtkCamera camera = renderers[view].GetActiveCamera();
            double[] direction = camera.GetDirectionOfProjection();
            double[] up = Vectors.orthogonalize(camera.GetViewUp(), direction);
            double[] right = Vectors.cross(direction, up);
            double scale = Math.max(camera.GetParallelScale(), MIN_PARALLEL_SCALE);
            double[] relative = Vectors.subtract(center, camera.GetFocalPoint());
            anchorX[view] = Vectors.dot(relative, right) / scale;
            anchorY[view] = Vectors.dot(relative, up) / scale;
        }
    }

    /**
     * 只更新取景（窗口尺寸变化时用）：保持各视图当前朝向，按锚点重排焦点。
     */
    public void refitVolume(MprCursorFrame frame, VolumeBox box, int[] views) {
        double[] center = frame.center();
        double distance = box.diagonal() * DISTANCE_FACTOR;
        for (int view : views) {
            vtkCamera camera = renderers[view].GetActiveCamera();
            double[] direction = camera.GetDirectionOfProjection();
            double[] up = Vectors.orthogonalize(camera.GetViewUp(), direction);
            double[] right = Vectors.cross(direction, up);
            double scale = fitScale(box, renderers[view], right, up);
            frozenScales[view] = scale;
            double[] focal = focalPoint(center, right, up, scale, view);
            applyCamera(renderers[view], focal, direction, up, scale, distance);
        }
    }

    /**
     * 当前应使用的取景缩放：取景冻结期间返回冻结值，否则按朝向计算。
     */
    private double effectiveScale(VolumeBox box, vtkRenderer renderer, double[] right, double[] up,
                                  int view) {
        return scaleFrozen ? frozenScales[view] : fitScale(box, renderer, right, up);
    }

    /**
     * 按朝向计算取景缩放：体数据盒在屏幕两轴上的支撑半长（aspect 修正后取较紧的一边）。
     */
    private static double fitScale(VolumeBox box, vtkRenderer renderer, double[] right, double[] up) {
        double[] fit = box.supportHalfExtents(right, up);
        int[] size = renderer.GetSize();
        double aspect = size[1] > 0 ? (double) size[0] / size[1] : 1.0;
        return Math.max(Math.max(fit[1], fit[0] / aspect) * FIT_MARGIN, MIN_PARALLEL_SCALE);
    }

    /**
     * 选取屏幕上方（滚动连续性）。
     *
     * <p>默认把**该视图上一次的 up 连续投影到新平面**：平面没变则 up 不变，平面缓变则 up 缓变，
     * 因此既不会出现"先转某视图 90°（其相机被冻结），再碰另一个视图时它瞬间歪 90°"，
     * 也不会在候选之间跳变。仅当投影退化（上一次 up 几乎平行于新平面法向）才退回机架轴；
     * 装载/重置等确定性场合直接用机架轴。</p>
     */
    private double[] chooseUp(MprViewRig rig, MprCursorFrame frame, int view, boolean frameRule) {
        double[] frameUp = rig.up(frame, view);
        if (frameRule) {
            return frameUp;
        }
        double[] direction = rig.direction(frame, view);
        double[] previous = renderers[view].GetActiveCamera().GetViewUp();
        double[] projected = MprViewRig.projectOntoPlane(previous, direction);
        return projected == null ? frameUp : projected;
    }

    /**
     * 焦点：{@code C − S·(x0·right + y0·up)}，使交点屏幕位置恒为记录值。
     */
    private double[] focalPoint(double[] center, double[] right, double[] up, double scale, int view) {
        return new double[]{
                center[0] - scale * (anchorX[view] * right[0] + anchorY[view] * up[0]),
                center[1] - scale * (anchorX[view] * right[1] + anchorY[view] * up[1]),
                center[2] - scale * (anchorX[view] * right[2] + anchorY[view] * up[2])};
    }

    private static void applyCamera(vtkRenderer renderer, double[] focal, double[] direction, double[] up,
                                    double scale, double distance) {
        vtkCamera camera = renderer.GetActiveCamera();
        camera.SetFocalPoint(focal[0], focal[1], focal[2]);
        camera.SetPosition(
                focal[0] - direction[0] * distance,
                focal[1] - direction[1] * distance,
                focal[2] - direction[2] * distance);
        camera.SetViewUp(up[0], up[1], up[2]);
        camera.ParallelProjectionOn();
        camera.SetParallelScale(scale);
        renderer.ResetCameraClippingRange();
    }

    /**
     * 三个视图的索引数组（便捷参数）。
     */
    public static int[] allViews() {
        int[] views = new int[VIEW_COUNT];
        for (int view = 0; view < VIEW_COUNT; view++) {
            views[view] = view;
        }
        return views;
    }
}
