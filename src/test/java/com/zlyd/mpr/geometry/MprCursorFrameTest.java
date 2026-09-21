package com.zlyd.mpr.geometry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.zlyd.mpr.dicom.SeriesGeometry;
import com.zlyd.mpr.dicom.VolumeGeometry;

/**
 * {@link MprCursorFrame} 的纯数学测试（关键数学函数）。
 *
 * <p>测试体数据：4 列 × 3 行 × 5 层，间距 (1.0, 0.5, 2.0)，原点 (10, 20, 30)，标准轴位朝向；
 * 体中心 M = (11.0, 20.75, 34.0)，初始 u=X、v=Y、w=Z。</p>
 */
class MprCursorFrameTest {

    private static final double DELTA = 1e-9;
    private static final int AXIAL = MprViewOrientation.VIEW_AXIAL;
    private static final int SAGITTAL = MprViewOrientation.VIEW_SAGITTAL;
    private static final int CORONAL = MprViewOrientation.VIEW_CORONAL;

    @Test
    void shouldStartAtVolumeCenterWithAnatomicalBasis() {
        MprCursorFrame frame = MprCursorFrame.initial(axialVolume());

        assertArrayEquals(new double[]{11.0, 20.75, 34.0}, frame.center(), DELTA);
        assertArrayEquals(new double[]{1, 0, 0}, frame.axis(SAGITTAL), DELTA);
        assertArrayEquals(new double[]{0, 1, 0}, frame.axis(CORONAL), DELTA);
        assertArrayEquals(new double[]{0, 0, 1}, frame.axis(AXIAL), DELTA);
        assertArrayEquals(new double[]{0, 0, 1}, frame.viewDirection(AXIAL), DELTA);
        assertArrayEquals(new double[]{-1, 0, 0}, frame.viewDirection(SAGITTAL), DELTA);
        assertArrayEquals(new double[]{0, 1, 0}, frame.viewDirection(CORONAL), DELTA);
        assertTrue(frame.isAxisAligned(1e-6));
        for (double angle : frame.obliquityDegrees()) {
            assertEquals(0.0, angle, 1e-6);
        }
        assertEquals(0.0, frame.normalOffset(AXIAL), DELTA);
    }

    @Test
    void shouldRejectUnknownView() {
        assertThrows(IllegalArgumentException.class, () -> MprCursorFrame.planeAxis(9));
    }

    @Test
    void shouldKeepBasisOrthonormalRightHandedAfterArbitraryRotations() {
        MprCursorFrame frame = MprCursorFrame.initial(axialVolume())
                .rotate(AXIAL, Math.toRadians(37.0))
                .rotate(SAGITTAL, Math.toRadians(123.0))
                .rotate(CORONAL, Math.toRadians(-64.0));

        double[] u = frame.basisAxis(MprCursorFrame.AXIS_U);
        double[] v = frame.basisAxis(MprCursorFrame.AXIS_V);
        double[] w = frame.basisAxis(MprCursorFrame.AXIS_W);

        for (double[] vector : new double[][]{u, v, w}) {
            assertEquals(1.0, norm(vector), 1e-9);
        }
        assertEquals(0.0, dot(u, v), 1e-9);
        assertEquals(0.0, dot(v, w), 1e-9);
        assertEquals(0.0, dot(w, u), 1e-9);
        assertArrayEquals(w, cross(u, v), 1e-9);
    }

    @Test
    void shouldKeepCenterFixedWhileRotating() {
        MprCursorFrame frame = MprCursorFrame.initial(axialVolume())
                .moveCenter(AXIAL, new double[]{13.0, 21.0, 34.0});
        double[] before = frame.center();

        MprCursorFrame rotated = frame
                .rotate(SAGITTAL, Math.toRadians(90.0))
                .rotate(CORONAL, Math.toRadians(-45.0));

        assertArrayEquals(before, rotated.center(), 1e-9);
    }

    @Test
    void shouldMatchApprovedAxialNinetyDegreeRotation() {
        // 设计文档 §4-A：在轴位视图顺时针 90°：轴位面不变，矢状视口显示冠状面、冠状视口显示矢状面
        MprCursorFrame frame = MprCursorFrame.initial(axialVolume()).rotate(AXIAL, Math.toRadians(90.0));

        assertParallel(new double[]{0, 0, 1}, frame.axis(AXIAL));
        assertParallel(new double[]{0, 1, 0}, frame.axis(SAGITTAL));
        assertParallel(new double[]{1, 0, 0}, frame.axis(CORONAL));
        assertArrayEquals(new double[]{0, -1, 0}, frame.viewDirection(SAGITTAL), 1e-9);
        assertArrayEquals(new double[]{-1, 0, 0}, frame.viewDirection(CORONAL), 1e-9);
    }

    @Test
    void shouldMatchApprovedSagittalNinetyDegreeRotation() {
        // 设计文档 §4-B：矢状视图顺时针 90°：矢状面不变、冠状视口显示轴位面且视线变为 头→脚
        MprCursorFrame frame = MprCursorFrame.initial(axialVolume()).rotate(SAGITTAL, Math.toRadians(90.0));

        assertParallel(new double[]{1, 0, 0}, frame.axis(SAGITTAL));
        assertParallel(new double[]{0, 0, 1}, frame.axis(CORONAL));
        assertParallel(new double[]{0, 1, 0}, frame.axis(AXIAL));
        assertArrayEquals(new double[]{0, 0, -1}, frame.viewDirection(CORONAL), 1e-9);
        assertArrayEquals(new double[]{0, 1, 0}, frame.viewDirection(AXIAL), 1e-9);
    }

    @Test
    void shouldMatchApprovedCoronalNinetyDegreeRotation() {
        // 设计文档 §4-C：冠状视图顺时针 90°：冠状面不变、轴位视口显示矢状面、视线变为 右→左
        MprCursorFrame frame = MprCursorFrame.initial(axialVolume()).rotate(CORONAL, Math.toRadians(90.0));

        assertParallel(new double[]{0, 1, 0}, frame.axis(CORONAL));
        assertParallel(new double[]{1, 0, 0}, frame.axis(AXIAL));
        assertParallel(new double[]{0, 0, 1}, frame.axis(SAGITTAL));
        assertArrayEquals(new double[]{1, 0, 0}, frame.viewDirection(AXIAL), 1e-9);
    }

    @Test
    void shouldSwapPlanesAfterNinetyDegreeRotation() {
        // 一般规律：转 90° 时另两个视图互相交换平面（且与第三个平面重合）
        MprCursorFrame initial = MprCursorFrame.initial(axialVolume());
        MprCursorFrame frame = initial.rotate(SAGITTAL, Math.toRadians(90.0));

        assertParallel(initial.axis(CORONAL), frame.axis(AXIAL));
        assertParallel(initial.axis(AXIAL), frame.axis(CORONAL));
        assertParallel(initial.axis(SAGITTAL), frame.axis(SAGITTAL));
    }

    @Test
    void shouldMovePlaneAlongItsNormalWhenOffset() {
        MprCursorFrame frame = MprCursorFrame.initial(axialVolume()).withOffset(AXIAL, 5.0);

        assertArrayEquals(new double[]{11.0, 20.75, 39.0}, frame.center(), DELTA);
        assertEquals(5.0, frame.normalOffset(AXIAL), DELTA);
        assertEquals(0.0, frame.normalOffset(SAGITTAL), DELTA);
        assertEquals(0.0, frame.normalOffset(CORONAL), DELTA);
    }

    @Test
    void shouldMoveCenterWithinDraggedViewPlaneOnly() {
        // 在轴位视图拖动：只改另两个偏移（u/v），轴位面自身偏移不变，点的 Z 被忽略
        MprCursorFrame frame = MprCursorFrame.initial(axialVolume())
                .moveCenter(AXIAL, new double[]{20.0, 30.0, 999.0});

        assertArrayEquals(new double[]{20.0, 30.0, 34.0}, frame.center(), DELTA);
        assertEquals(0.0, frame.normalOffset(AXIAL), DELTA);
        assertEquals(20.0 - 11.0, frame.normalOffset(SAGITTAL), DELTA);
        assertEquals(30.0 - 20.75, frame.normalOffset(CORONAL), DELTA);
    }

    @Test
    void shouldReportObliquityAndAxisAlignment() {
        MprCursorFrame initial = MprCursorFrame.initial(axialVolume());
        MprCursorFrame rotated = initial.rotate(AXIAL, Math.toRadians(90.0));

        double[] angles = rotated.obliquityDegrees();
        assertEquals(0.0, angles[0], 1e-6);
        assertEquals(90.0, angles[1], 1e-6);
        assertEquals(90.0, angles[2], 1e-6);
        assertFalse(rotated.isAxisAligned(1.0));
        assertTrue(rotated.isAxisAligned(91.0));

        MprCursorFrame flipped = initial.rotate(AXIAL, Math.toRadians(180.0));
        assertEquals(180.0, flipped.obliquityDegrees()[1], 1e-6);
        assertTrue(flipped.isAxisAligned(1.0));
    }

    private static void assertParallel(double[] expected, double[] actual) {
        double[] normalized = scale(actual, 1.0 / norm(actual));
        assertTrue(Math.abs(Math.abs(dot(expected, normalized)) - 1.0) < 1e-9,
                "应平行: expected=" + java.util.Arrays.toString(expected)
                        + " actual=" + java.util.Arrays.toString(actual));
    }

    private static double norm(double[] vector) {
        return Math.sqrt(dot(vector, vector));
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] scale(double[] vector, double factor) {
        return new double[]{vector[0] * factor, vector[1] * factor, vector[2] * factor};
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
