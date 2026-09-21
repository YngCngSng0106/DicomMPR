package com.zlyd.mpr.geometry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.zlyd.mpr.dicom.SeriesGeometry;
import com.zlyd.mpr.dicom.VolumeGeometry;

/**
 * {@link MprViewRig} 的纯数学测试：连续性机架符合固定映射表、回正机架挑选解剖最优组合。
 */
class MprViewRigTest {

    private static final double DELTA = 1e-9;
    private static final int AXIAL = MprViewOrientation.VIEW_AXIAL;
    private static final int SAGITTAL = MprViewOrientation.VIEW_SAGITTAL;
    private static final int CORONAL = MprViewOrientation.VIEW_CORONAL;

    @Test
    void continuityShouldMatchFixedMappingTable() {
        MprCursorFrame frame = MprCursorFrame.initial(axialVolume());
        MprViewRig rig = MprViewRig.continuity();

        assertArrayEquals(new double[]{0, 0, 1}, rig.direction(frame, AXIAL), DELTA);
        assertArrayEquals(new double[]{0, -1, 0}, rig.up(frame, AXIAL), DELTA);
        assertArrayEquals(new double[]{1, 0, 0}, rig.right(frame, AXIAL), DELTA);

        assertArrayEquals(new double[]{-1, 0, 0}, rig.direction(frame, SAGITTAL), DELTA);
        assertArrayEquals(new double[]{0, 0, 1}, rig.up(frame, SAGITTAL), DELTA);
        assertArrayEquals(new double[]{0, 1, 0}, rig.right(frame, SAGITTAL), DELTA);

        assertArrayEquals(new double[]{0, 1, 0}, rig.direction(frame, CORONAL), DELTA);
        assertArrayEquals(new double[]{0, 0, 1}, rig.up(frame, CORONAL), DELTA);
        assertArrayEquals(new double[]{1, 0, 0}, rig.right(frame, CORONAL), DELTA);
    }

    @Test
    void shouldKeepUpPerpendicularAndRightHandedAfterRotation() {
        MprCursorFrame frame = MprCursorFrame.initial(axialVolume())
                .rotate(AXIAL, Math.toRadians(37.0))
                .rotate(SAGITTAL, Math.toRadians(64.0));
        MprViewRig rig = MprViewRig.continuity();

        for (int view = 0; view < MprViewRig.VIEW_COUNT; view++) {
            double[] direction = rig.direction(frame, view);
            double[] up = rig.up(frame, view);
            assertEquals(1.0, norm(direction), DELTA);
            assertEquals(1.0, norm(up), DELTA);
            assertEquals(0.0, dot(direction, up), DELTA);
            assertArrayEquals(cross(direction, up), rig.right(frame, view), DELTA);
        }
    }

    @Test
    void shouldCarryUpWithFrameForContinuity() {
        // 在轴位视图内旋转（绕 w）时，另两视图的 up 恒为 +w
        MprCursorFrame frame = MprCursorFrame.initial(axialVolume()).rotate(AXIAL, Math.toRadians(45.0));
        MprViewRig rig = MprViewRig.continuity();

        assertArrayEquals(new double[]{0, 0, 1}, rig.up(frame, SAGITTAL), DELTA);
        assertArrayEquals(new double[]{0, 0, 1}, rig.up(frame, CORONAL), DELTA);
    }

    @Test
    void alignedShouldEqualContinuityAtInitialFrame() {
        MprCursorFrame frame = MprCursorFrame.initial(axialVolume());
        MprViewRig aligned = MprViewRig.aligned(frame);
        MprViewRig continuity = MprViewRig.continuity();

        for (int view = 0; view < MprViewRig.VIEW_COUNT; view++) {
            assertArrayEquals(continuity.direction(frame, view), aligned.direction(frame, view), DELTA);
            assertArrayEquals(continuity.up(frame, view), aligned.up(frame, view), DELTA);
        }
    }

    @Test
    void alignedShouldNotBeWorseThanContinuity() {
        MprCursorFrame frame = MprCursorFrame.initial(axialVolume())
                .rotate(SAGITTAL, Math.toRadians(90.0))
                .rotate(AXIAL, Math.toRadians(30.0));
        MprViewRig aligned = MprViewRig.aligned(frame);
        MprViewRig continuity = MprViewRig.continuity();

        for (int view = 0; view < MprViewRig.VIEW_COUNT; view++) {
            assertTrue(aligned.score(frame, view) >= continuity.score(frame, view) - 1e-9,
                    "回正不应比连续性更差（视图 " + view + "）");
            assertEquals(0.0, dot(aligned.direction(frame, view), aligned.up(frame, view)), DELTA);
        }
    }

    @Test
    void shouldProjectOntoPlaneForRollContinuity() {
        // 把 up 连续投影到新平面：平面内向量保持不变；带法向分量时去掉法向分量
        assertArrayEquals(new double[]{1, 0, 0},
                MprViewRig.projectOntoPlane(new double[]{1, 0, 0}, new double[]{0, 0, 1}), DELTA);
        assertArrayEquals(new double[]{1 / Math.sqrt(2.0), 1 / Math.sqrt(2.0), 0},
                MprViewRig.projectOntoPlane(new double[]{1, 1, 5}, new double[]{0, 0, 1}), 1e-12);

        // 与平面法向平行（退化）时返回 null，调用方退回机架轴
        assertNull(MprViewRig.projectOntoPlane(new double[]{0, 0, 1}, new double[]{0, 0, 1}));
    }

    @Test
    void shouldRejectUpAxisEqualToDirectionAxis() {
        assertThrows(IllegalArgumentException.class, () -> MprViewRig.of(
                new int[]{0, 0, 0}, new double[]{1, 1, 1}, new int[]{0, 1, 2}, new double[]{1, 1, 1}));
        assertThrows(IllegalArgumentException.class, () -> MprViewRig.of(
                new int[]{0, 1, 2}, new double[]{2, 1, 1}, new int[]{1, 2, 0}, new double[]{1, 1, 1}));
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
