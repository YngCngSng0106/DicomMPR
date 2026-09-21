package com.zlyd.mpr.geometry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.zlyd.mpr.dicom.SeriesGeometry;
import com.zlyd.mpr.dicom.VolumeGeometry;

/**
 * {@link CrosshairGeometry} 的十字线坐标计算测试（关键数学函数）。
 *
 * <p>测试体数据：3 列 × 4 行 × 5 层，间距 (列1.0, 行0.5, 层2.0)，原点 (10, 20, 30)，标准轴位朝向；
 * 体中心 M = (11.0, 20.75, 34.0)，初始 u=X、v=Y、w=Z。</p>
 *
 * <p>核心行为：线段按**视口矩形**裁剪（半宽/半高由 {@link ScreenFrame} 给出），
 * 因此线一定铺满整个视图展示区；只有整条线都不在视口内时才不可见。</p>
 */
class CrosshairGeometryTest {

    private static final double DELTA = 1e-9;
    private static final double VIEW_HALF = 5.0;
    private static final double GAP = 0.5;
    private static final int VIEWS = 3;

    @Test
    void shouldProduceTwoSegmentsPerLineForEveryView() {
        List<CrosshairSegment> segments = CrosshairGeometry.compute(
                frameAtCenter(), axialScreens(VIEW_HALF), gaps());

        assertEquals(VIEWS * CrosshairGeometry.LINE_COUNT * CrosshairGeometry.SEGMENT_COUNT,
                segments.size());
    }

    @Test
    void shouldSpanWholeViewportForAxisAlignedFrame() {
        double[] center = frameAtCenter().center();
        double[] x = {1, 0, 0};
        double[] y = {0, 1, 0};
        double[] z = {0, 0, 1};
        List<CrosshairSegment> segments = CrosshairGeometry.compute(
                frameAtCenter(), axialScreens(VIEW_HALF), gaps());

        // 轴位：线0 = 竖线（沿 Y，铺满视口高度）、线1 = 横线（沿 X，铺满视口宽度）
        assertSegment(segments, MprViewOrientation.VIEW_AXIAL, 0, 0, true,
                offset(center, y, -VIEW_HALF), offset(center, y, -GAP));
        assertSegment(segments, MprViewOrientation.VIEW_AXIAL, 0, 1, true,
                offset(center, y, GAP), offset(center, y, VIEW_HALF));
        assertSegment(segments, MprViewOrientation.VIEW_AXIAL, 1, 0, true,
                offset(center, x, -VIEW_HALF), offset(center, x, -GAP));
        assertSegment(segments, MprViewOrientation.VIEW_AXIAL, 1, 1, true,
                offset(center, x, GAP), offset(center, x, VIEW_HALF));

        // 矢状：竖线沿 Z、横线沿 Y
        assertSegment(segments, MprViewOrientation.VIEW_SAGITTAL, 0, 0, true,
                offset(center, z, -VIEW_HALF), offset(center, z, -GAP));
        assertSegment(segments, MprViewOrientation.VIEW_SAGITTAL, 0, 1, true,
                offset(center, z, GAP), offset(center, z, VIEW_HALF));
        assertSegment(segments, MprViewOrientation.VIEW_SAGITTAL, 1, 0, true,
                offset(center, y, -VIEW_HALF), offset(center, y, -GAP));
        assertSegment(segments, MprViewOrientation.VIEW_SAGITTAL, 1, 1, true,
                offset(center, y, GAP), offset(center, y, VIEW_HALF));

        // 冠状：竖线沿 Z、横线沿 X
        assertSegment(segments, MprViewOrientation.VIEW_CORONAL, 0, 0, true,
                offset(center, z, -VIEW_HALF), offset(center, z, -GAP));
        assertSegment(segments, MprViewOrientation.VIEW_CORONAL, 0, 1, true,
                offset(center, z, GAP), offset(center, z, VIEW_HALF));
        assertSegment(segments, MprViewOrientation.VIEW_CORONAL, 1, 0, true,
                offset(center, x, -VIEW_HALF), offset(center, x, -GAP));
        assertSegment(segments, MprViewOrientation.VIEW_CORONAL, 1, 1, true,
                offset(center, x, GAP), offset(center, x, VIEW_HALF));
    }

    @Test
    void shouldClipRotatedCrosshairToViewportBoundary() {
        // 斜切后（轴位视图屏幕冻结在原始朝向上）：外侧端点必须落在视口边界上
        MprCursorFrame frame = frameAtCenter().rotate(MprViewOrientation.VIEW_AXIAL,
                Math.toRadians(30.0));
        ScreenFrame[] screens = axialScreens(VIEW_HALF);
        List<CrosshairSegment> segments = CrosshairGeometry.compute(frame, screens, gaps());

        for (CrosshairSegment segment : segments) {
            if (!segment.isVisible()) {
                continue;
            }
            double[] point = segment.getSegment() == 0 ? segment.getStart() : segment.getEnd();
            double[] screen = screens[segment.getView()].toScreen(point);
            boolean onBoundary = Math.abs(Math.abs(screen[0]) - VIEW_HALF) < 1e-6
                    || Math.abs(Math.abs(screen[1]) - VIEW_HALF) < 1e-6;
            assertTrue(onBoundary, "外侧端点应落在视口边界: " + java.util.Arrays.toString(screen));
        }
    }

    @Test
    void shouldHideLineWhoseProjectionMissesViewport() {
        // 交点被移到视口外 100mm：竖线（固定 x=100）整条在视口外 → 不可见；横线仍铺满视口
        MprCursorFrame frame = frameAtCenter().withOffsets(100.0, 0.0, 0.0);
        List<CrosshairSegment> segments = CrosshairGeometry.compute(
                frame, axialScreens(VIEW_HALF), gaps());

        // 竖线固定 x=100：整条在视口外 → 两段都不可见
        assertFalse(find(segments, MprViewOrientation.VIEW_AXIAL, 0, 0).isVisible());
        assertFalse(find(segments, MprViewOrientation.VIEW_AXIAL, 0, 1).isVisible());
        // 横线仍铺满视口；交点(洞)在视口外，故只有洞前的一段
        assertTrue(find(segments, MprViewOrientation.VIEW_AXIAL, 1, 0).isVisible());
        assertFalse(find(segments, MprViewOrientation.VIEW_AXIAL, 1, 1).isVisible());
    }

    @Test
    void shouldHideDegenerateSegmentWhenCenterAtEdge() {
        // 留洞半径大于半宽时，内侧段退化为不可见
        MprCursorFrame frame = frameAtCenter();
        List<CrosshairSegment> segments = CrosshairGeometry.compute(
                frame, axialScreens(0.2), new double[]{0.5, 0.5, 0.5});

        assertFalse(find(segments, MprViewOrientation.VIEW_AXIAL, 1, 0).isVisible());
        assertFalse(find(segments, MprViewOrientation.VIEW_AXIAL, 1, 1).isVisible());
    }

    @Test
    void shouldClipLineToRectangle() {
        double[] alongX = CrosshairGeometry.clipToRect(new double[]{0, 0}, new double[]{1, 0}, 5, 5);
        assertArrayEquals(new double[]{-5, 5}, alongX, DELTA);

        double[] outsideParallel = CrosshairGeometry.clipToRect(new double[]{10, 0}, new double[]{0, 1}, 5, 5);
        assertNull(outsideParallel);

        double[] outsideAlong = CrosshairGeometry.clipToRect(new double[]{10, 0}, new double[]{1, 0}, 5, 5);
        assertArrayEquals(new double[]{-15, -5}, outsideAlong, DELTA);
    }

    private static void assertSegment(List<CrosshairSegment> segments, int view, int line, int segment,
                                      boolean visible, double[] start, double[] end) {
        CrosshairSegment actual = find(segments, view, line, segment);
        assertEquals(visible, actual.isVisible(), "可见性不符: view=" + view + " line=" + line);
        assertArrayEquals(start, actual.getStart(), 1e-6);
        assertArrayEquals(end, actual.getEnd(), 1e-6);
    }

    private static CrosshairSegment find(List<CrosshairSegment> segments, int view, int line, int segment) {
        for (CrosshairSegment candidate : segments) {
            if (candidate.getView() == view && candidate.getLine() == line
                    && candidate.getSegment() == segment) {
                return candidate;
            }
        }
        throw new AssertionError("未找到对应线段");
    }

    private static double[] offset(double[] origin, double[] direction, double distance) {
        return new double[]{
                origin[0] + direction[0] * distance,
                origin[1] + direction[1] * distance,
                origin[2] + direction[2] * distance};
    }

    private static double[] gaps() {
        return new double[]{GAP, GAP, GAP};
    }

    private static MprCursorFrame frameAtCenter() {
        return MprCursorFrame.initial(axialVolume());
    }

    /**
     * 三视图的屏幕参考系（与连续性机架一致，轴对齐帧；焦点取交点 C）。
     */
    private static ScreenFrame[] axialScreens(double half) {
        double[] center = frameAtCenter().center();
        return new ScreenFrame[]{
                // 轴位：视线 +Z、右 +X、上 −Y
                new ScreenFrame(center, new double[]{1, 0, 0}, new double[]{0, -1, 0},
                        new double[]{0, 0, 1}, half, half),
                // 矢状：视线 −X、右 +Y、上 +Z
                new ScreenFrame(center, new double[]{0, 1, 0}, new double[]{0, 0, 1},
                        new double[]{-1, 0, 0}, half, half),
                // 冠状：视线 +Y、右 +X、上 +Z
                new ScreenFrame(center, new double[]{1, 0, 0}, new double[]{0, 0, 1},
                        new double[]{0, 1, 0}, half, half)};
    }

    private static VolumeGeometry axialVolume() {
        SeriesGeometry geometry = new SeriesGeometry.Builder()
                .dimension(4, 3)
                .pixelSpacing(new double[]{0.5, 1.0})
                .orientation(new double[]{1, 0, 0, 0, 1, 0})
                .normal(new double[]{0, 0, 1})
                .sliceSpacing(2.0)
                .build();
        return VolumeGeometry.of(geometry, new double[]{10.0, 20.0, 30.0}, 5);
    }
}
