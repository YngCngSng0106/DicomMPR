package com.zlyd.mpr.geometry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.zlyd.mpr.dicom.SeriesGeometry;
import com.zlyd.mpr.dicom.VolumeGeometry;

/**
 * {@link VolumeBox} 的几何运算测试（关键数学函数）：直线裁剪与取景支撑半长。
 *
 * <p>测试体数据：3 列 × 4 行 × 5 层，间距 (列1.0, 行0.5, 层2.0)，原点 (10, 20, 30)；
 * 体中心 M = (11.0, 20.75, 34.0)；体素中心盒半长 = (1.0, 0.75, 4.0)；取景（体素边缘）半长 = (1.5, 1.0, 5.0)。</p>
 */
class VolumeBoxTest {

    private static final double DELTA = 1e-9;

    @Test
    void shouldExposeCenterAndHalfExtents() {
        VolumeBox box = VolumeBox.of(axialVolume());

        assertArrayEquals(new double[]{11.0, 20.75, 34.0}, box.getCenter(), DELTA);
        assertArrayEquals(new double[]{1.0, 0.75, 4.0}, box.getHalfExtents(), DELTA);
    }

    @Test
    void shouldClipAxisAlignedRayAtBoxFaces() {
        VolumeBox box = VolumeBox.of(axialVolume());
        double[] center = box.getCenter();

        assertArrayEquals(new double[]{-1.0, 1.0}, box.clipRay(center, new double[]{1, 0, 0}), DELTA);
        assertArrayEquals(new double[]{-0.75, 0.75}, box.clipRay(center, new double[]{0, 1, 0}), DELTA);
        assertArrayEquals(new double[]{-4.0, 4.0}, box.clipRay(center, new double[]{0, 0, 1}), DELTA);
    }

    @Test
    void shouldClipOffCenterRayAsymmetrically() {
        VolumeBox box = VolumeBox.of(axialVolume());

        double[] range = box.clipRay(new double[]{11.5, 20.75, 34.0}, new double[]{1, 0, 0});

        assertArrayEquals(new double[]{-1.5, 0.5}, range, DELTA);
    }

    @Test
    void shouldClipObliqueRayByTightestSlab() {
        VolumeBox box = VolumeBox.of(axialVolume());
        double root = Math.sqrt(2.0);

        // 方向 (u+v)/√2：受 v 轴半长 0.75 限制
        double[] range = box.clipRay(box.getCenter(), new double[]{1 / root, 1 / root, 0});

        assertArrayEquals(new double[]{-0.75 * root, 0.75 * root}, range, DELTA);
    }

    @Test
    void shouldReturnNullWhenRayMissesBox() {
        VolumeBox box = VolumeBox.of(axialVolume());

        assertNull(box.clipRay(new double[]{16.0, 20.75, 34.0}, new double[]{0, 1, 0}));
    }

    @Test
    void shouldComputeSupportHalfExtentsForViewFit() {
        VolumeBox box = VolumeBox.of(axialVolume());
        double root = Math.sqrt(2.0);

        assertArrayEquals(new double[]{1.5, 1.0}, box.supportHalfExtents(
                new double[]{1, 0, 0}, new double[]{0, 1, 0}), DELTA);
        assertArrayEquals(new double[]{2.5 / root, 2.5 / root}, box.supportHalfExtents(
                new double[]{1 / root, 1 / root, 0}, new double[]{1 / root, -1 / root, 0}), DELTA);
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
