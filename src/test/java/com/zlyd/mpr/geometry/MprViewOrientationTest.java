package com.zlyd.mpr.geometry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.zlyd.mpr.dicom.SeriesGeometry;
import com.zlyd.mpr.dicom.VolumeGeometry;

/**
 * {@link MprViewOrientation} 的方向标记测试（关键数学函数）。
 *
 * <p>标准轴位体数据，期望：轴位 上A下P左R右L；冠状 上H下F左R右L；矢状 上H下F左A右P。</p>
 */
class MprViewOrientationTest {

    @Test
    void shouldLabelAxialViewByRadiologyConvention() {
        assertArrayEquals(new String[]{"A", "P", "R", "L"},
                MprViewOrientation.edgeLabels(axialVolume(), MprViewOrientation.VIEW_AXIAL));
    }

    @Test
    void shouldLabelCoronalView() {
        assertArrayEquals(new String[]{"H", "F", "R", "L"},
                MprViewOrientation.edgeLabels(axialVolume(), MprViewOrientation.VIEW_CORONAL));
    }

    @Test
    void shouldLabelSagittalView() {
        assertArrayEquals(new String[]{"H", "F", "A", "P"},
                MprViewOrientation.edgeLabels(axialVolume(), MprViewOrientation.VIEW_SAGITTAL));
    }

    @Test
    void shouldMapWorldDirectionToLetter() {
        assertEquals("L", MprViewOrientation.label(new double[]{1, 0, 0}));
        assertEquals("R", MprViewOrientation.label(new double[]{-1, 0, 0}));
        assertEquals("P", MprViewOrientation.label(new double[]{0, 1, 0}));
        assertEquals("A", MprViewOrientation.label(new double[]{0, -1, 0}));
        assertEquals("H", MprViewOrientation.label(new double[]{0, 0, 1}));
        assertEquals("F", MprViewOrientation.label(new double[]{0, 0, -1}));
    }

    private static VolumeGeometry axialVolume() {
        SeriesGeometry geometry = new SeriesGeometry.Builder()
                .dimension(4, 4)
                .pixelSpacing(new double[]{1.0, 1.0})
                .orientation(new double[]{1, 0, 0, 0, 1, 0})
                .normal(new double[]{0, 0, 1})
                .sliceSpacing(1.0)
                .build();
        return VolumeGeometry.of(geometry, new double[]{0, 0, 0}, 4);
    }
}
