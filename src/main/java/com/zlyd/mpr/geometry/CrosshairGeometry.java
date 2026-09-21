package com.zlyd.mpr.geometry;

import java.util.ArrayList;
import java.util.List;

import com.zlyd.mpr.dicom.VolumeGeometry;

/**
 * 十字线坐标计算（纯数学，不依赖任何渲染库）。
 *
 * <h2>含义（斜切通用）</h2>
 * 每个视图显示体数据的一个平面；该视图里的两条十字线是<b>另外两个平面</b>与本视图平面相交的痕迹，
 * 两线交点就是三平面的唯一交点 C（中心点）。
 * <pre>
 *   视图   显示平面   线 0（竖线）              线 1（横线）
 *   轴位   ⊥w         u 轴（矢状面的痕迹）       v 轴（冠状面的痕迹）
 *   矢状   ⊥u         v 轴（冠状面的痕迹）       w 轴（轴位面的痕迹）
 *   冠状   ⊥v         u 轴（矢状面的痕迹）       w 轴（轴位面的痕迹）
 * </pre>
 *
 * <h2>铺满视口</h2>
 * 线段端点按**视口矩形**（{@link ScreenFrame}）裁剪，而不是裁到体数据包围盒：
 * 这样线一定铺满整个视图展示区，且不会因为斜切/偏心而整条消失；
 * 只有当整条线的投影都不落在视口内（C 被移到画面外）时才不可见。
 *
 * <h2>交点留洞</h2>
 * 每条线在交点两侧各留 {@code gap}（世界单位，由调用方按屏幕像素换算）的空白，拆成两段；
 * 越界段退化为不可见（起点终点重合）。
 */
public final class CrosshairGeometry {

    /** 每个视图两条中线：0 = 竖线，1 = 横线。 */
    public static final int LINE_COUNT = 2;
    /** 每条中线因留洞拆成两段。 */
    public static final int SEGMENT_COUNT = 2;
    /** 小于该长度的线段视为退化（不绘制）。 */
    private static final double MIN_SEGMENT_LENGTH = 1e-6;
    private static final double PARALLEL_EPSILON = 1e-12;

    private CrosshairGeometry() {
    }

    /**
     * 按光标坐标系与各视图屏幕参考系计算 3 视图 × 2 线 × 2 段 = 12 条线段。
     *
     * @param frame 光标坐标系（三平面与交点 C）
     * @param screens 各视图的屏幕参考系（顺序与视图索引一致）
     * @param gapWorldPerView 各视图"留洞半径"（世界单位），长度需 ≥ 3
     */
    public static List<CrosshairSegment> compute(MprCursorFrame frame, ScreenFrame[] screens,
                                                 double[] gapWorldPerView) {
        List<CrosshairSegment> segments = new ArrayList<>();
        double[] center = frame.center();
        for (int view = 0; view < screens.length; view++) {
            int planeAxis = MprCursorFrame.planeAxis(view);
            ScreenFrame screen = screens[view];
            for (int line = 0; line < LINE_COUNT; line++) {
                double[] direction = frame.basisAxis(traceDirectionAxis(planeAxis, line));
                addScreenLine(segments, frame, screen, view, line, direction, center,
                        gapWorldPerView[view]);
            }
        }
        return segments;
    }

    /**
     * 一条线：以 C 的屏幕投影为基点、方向在屏幕上的投影为方向，裁到视口矩形后映射回显示平面。
     */
    private static void addScreenLine(List<CrosshairSegment> out, MprCursorFrame frame,
                                      ScreenFrame screen, int view, int line, double[] direction,
                                      double[] center, double gap) {
        double[] base = screen.toScreen(center);
        double[] flat = {Vectors.dot(direction, screen.getRight()), Vectors.dot(direction, screen.getUp())};
        double[] range = clipToRect(base, flat, screen.getHalfWidth(), screen.getHalfHeight());
        if (range == null) {
            out.add(degenerate(view, line, 0, center));
            out.add(degenerate(view, line, 1, center));
            return;
        }
        double[] start = toPlane(screen, frame, view, base, flat, range[0], center);
        double[] end = toPlane(screen, frame, view, base, flat, range[1], center);
        addLine(out, view, line, start, end, center, gap);
    }

    /**
     * 直线（屏幕平面坐标，点 {@code point} + 方向 {@code direction}）与矩形
     * {@code [-halfWidth, halfWidth] × [-halfHeight, halfHeight]} 的交集参数区间。
     *
     * @return {@code {tMin, tMax}}；不相交返回 {@code null}
     */
    static double[] clipToRect(double[] point, double[] direction, double halfWidth, double halfHeight) {
        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;
        double[] origin = {point[0], point[1]};
        double[] limits = {halfWidth, halfHeight};
        for (int axis = 0; axis < 2; axis++) {
            double start = origin[axis];
            double step = direction[axis];
            double limit = limits[axis];
            if (Math.abs(step) < PARALLEL_EPSILON) {
                if (Math.abs(start) > limit) {
                    return null;
                }
                continue;
            }
            double first = (-limit - start) / step;
            double second = (limit - start) / step;
            tMin = Math.max(tMin, Math.min(first, second));
            tMax = Math.min(tMax, Math.max(first, second));
            if (tMin > tMax) {
                return null;
            }
        }
        return tMin > tMax ? null : new double[]{tMin, tMax};
    }

    /**
     * 屏幕平面上的点（焦点 + x·右 + y·上）沿视线投影到本视图显示平面上的世界点。
     */
    private static double[] toPlane(ScreenFrame screen, MprCursorFrame frame, int view, double[] base,
                                    double[] flat, double t, double[] center) {
        double x = base[0] + flat[0] * t;
        double y = base[1] + flat[1] * t;
        double[] right = screen.getRight();
        double[] up = screen.getUp();
        double[] onFocalPlane = {
                screen.getFocal()[0] + x * right[0] + y * up[0],
                screen.getFocal()[1] + x * right[1] + y * up[1],
                screen.getFocal()[2] + x * right[2] + y * up[2]};
        double[] normal = frame.axis(view);
        double denominator = Vectors.dot(screen.getViewDirection(), normal);
        if (Math.abs(denominator) < PARALLEL_EPSILON) {
            return onFocalPlane;
        }
        double distance = Vectors.dot(Vectors.subtract(center, onFocalPlane), normal) / denominator;
        return Vectors.add(onFocalPlane, Vectors.scale(screen.getViewDirection(), distance));
    }



    /**
     * 轴对齐便捷入口：在索引 {@code (i, j, k)} 处按解剖轴建立光标坐标系。
     */
    public static MprCursorFrame frameAt(VolumeGeometry geometry, int i, int j, int k) {
        double[] spacing = geometry.getSpacing();
        int[] dimensions = geometry.getDimensions();
        return MprCursorFrame.initial(geometry).withOffsets(
                (i - (dimensions[0] - 1) / 2.0) * spacing[0],
                (j - (dimensions[1] - 1) / 2.0) * spacing[1],
                (k - (dimensions[2] - 1) / 2.0) * spacing[2]);
    }

    /**
     * 某条线所代表的平面轴索引（线 0 = 索引较小的另一轴，线 1 = 较大者）。
     */
    static int tracePlaneAxis(int planeAxis, int line) {
        int[] others = new int[LINE_COUNT];
        int index = 0;
        for (int axis = 0; axis <= MprCursorFrame.AXIS_W; axis++) {
            if (axis != planeAxis) {
                others[index++] = axis;
            }
        }
        return others[line];
    }

    /**
     * 某条线在显示平面内的方向轴索引（三轴索引和为 3，故为第三条轴）。
     */
    static int traceDirectionAxis(int planeAxis, int line) {
        return MprCursorFrame.AXIS_U + MprCursorFrame.AXIS_V + MprCursorFrame.AXIS_W
                - planeAxis - tracePlaneAxis(planeAxis, line);
    }

    private static CrosshairSegment degenerate(int view, int line, int segment, double[] point) {
        return new CrosshairSegment(view, line, segment, point, point, false);
    }

    /**
     * 生成"穿过中心点、并在中心两侧各留 gap 空白"的两条线段。
     */
    private static void addLine(List<CrosshairSegment> out, int view, int line,
                                double[] start, double[] end, double[] center, double gap) {
        double[] direction = unitVector(start, end);
        double length = distance(start, end);
        double projection = dot(subtract(center, start), direction);

        double firstEnd = clamp(projection - gap, 0.0, length);
        double secondStart = clamp(projection + gap, 0.0, length);

        double[] firstPoint = pointAt(start, direction, firstEnd);
        double[] secondPoint = pointAt(start, direction, secondStart);
        out.add(new CrosshairSegment(view, line, 0, start, firstPoint,
                distance(start, firstPoint) > MIN_SEGMENT_LENGTH));
        out.add(new CrosshairSegment(view, line, 1, secondPoint, end,
                distance(secondPoint, end) > MIN_SEGMENT_LENGTH));
    }

    private static double[] pointAt(double[] origin, double[] direction, double distanceAlong) {
        return new double[]{
                origin[0] + direction[0] * distanceAlong,
                origin[1] + direction[1] * distanceAlong,
                origin[2] + direction[2] * distanceAlong
        };
    }

    private static double[] unitVector(double[] start, double[] end) {
        double[] vector = subtract(end, start);
        double length = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
        if (length == 0.0) {
            return new double[]{0.0, 0.0, 0.0};
        }
        return new double[]{vector[0] / length, vector[1] / length, vector[2] / length};
    }

    private static double[] subtract(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
