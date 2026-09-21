package com.zlyd.mpr.dicom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * {@link SeriesGeometryAnalyzer} 的法向量、排序与校验测试（关键数学函数）。
 */
class SeriesGeometryAnalyzerTest {

    private static final double DELTA = 1e-9;
    private static final double[] AXIAL_ORIENTATION = {1, 0, 0, 0, 1, 0};

    private final SeriesGeometryAnalyzer analyzer = new SeriesGeometryAnalyzer();

    @Test
    void shouldComputeNormalAndSortByProjection() {
        List<SliceInfo> slices = new ArrayList<>();
        slices.add(slice(0, 0, 4.0));
        slices.add(slice(0, 0, 0.0));
        slices.add(slice(0, 0, 2.0));

        SeriesGeometry geometry = analyzer.analyze(slices, 1);

        assertTrue(geometry.isValid());
        assertArrayEquals(new double[]{0, 0, 1}, geometry.getNormal(), DELTA);
        assertEquals(2.0, geometry.getSliceSpacing(), DELTA);
        assertEquals(0.0, slices.get(0).getProjectedPosition(), DELTA);
        assertEquals(2.0, slices.get(1).getProjectedPosition(), DELTA);
        assertEquals(4.0, slices.get(2).getProjectedPosition(), DELTA);
    }

    @Test
    void shouldDetectInconsistentSpacing() {
        List<SliceInfo> slices = new ArrayList<>();
        slices.add(slice(0, 0, 0.0));
        slices.add(slice(0, 0, 1.0));
        slices.add(slice(0, 0, 5.0));

        SeriesGeometry geometry = analyzer.analyze(slices, 1);

        assertFalse(geometry.isSpacingConsistent());
        assertTrue(geometry.hasGap());
        assertNotNull(geometry.getWarning());
    }

    @Test
    void shouldInvalidateWhenSingleSlice() {
        List<SliceInfo> slices = new ArrayList<>();
        slices.add(slice(0, 0, 0.0));

        SeriesGeometry geometry = analyzer.analyze(slices, 1);

        assertFalse(geometry.isValid());
        assertNotNull(geometry.getWarning());
    }

    @Test
    void shouldInvalidateWhenGeometryMissing() {
        List<SliceInfo> slices = new ArrayList<>();
        slices.add(new SliceInfo.Builder().file(Paths.get("a.dcm")).dimension(4, 4).build());
        slices.add(new SliceInfo.Builder().file(Paths.get("b.dcm")).dimension(4, 4).build());

        SeriesGeometry geometry = analyzer.analyze(slices, 1);

        assertFalse(geometry.isValid());
    }

    @Test
    void shouldInvalidateMultiFrame() {
        List<SliceInfo> slices = new ArrayList<>();
        slices.add(slice(0, 0, 0.0));
        slices.add(slice(0, 0, 1.0));

        SeriesGeometry geometry = analyzer.analyze(slices, 3);

        assertFalse(geometry.isValid());
    }

    private static SliceInfo slice(double x, double y, double z) {
        return new SliceInfo.Builder()
                .file(Paths.get("slice.dcm"))
                .dimension(4, 4)
                .imagePositionPatient(new double[]{x, y, z})
                .imageOrientationPatient(AXIAL_ORIENTATION)
                .pixelSpacing(new double[]{0.5, 0.5})
                .build();
    }
}
