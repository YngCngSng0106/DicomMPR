package com.zlyd.mpr.dicom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * {@link SeriesGrouper} 的分组与排序测试。
 */
class SeriesGrouperTest {

    private static final double[] AXIAL_ORIENTATION = {1, 0, 0, 0, 1, 0};

    @Test
    void shouldGroupBySeriesUidAndSortBySeriesNumber() {
        List<DicomInstance> instances = new ArrayList<>();
        instances.add(instance("1.2.3", 2, 0.0));
        instances.add(instance("1.2.3", 2, 1.0));
        instances.add(instance("9.9.9", 1, 0.0));

        List<SeriesInfo> seriesList = new SeriesGrouper().group(instances);

        assertEquals(2, seriesList.size());
        assertEquals(1, seriesList.get(0).getAttributes().getSeriesNumber());
        assertEquals(2, seriesList.get(1).getSliceCount());
        assertTrue(seriesList.get(1).getGeometry().isValid());
    }

    private static DicomInstance instance(String seriesUid, int seriesNumber, double z) {
        SliceInfo slice = new SliceInfo.Builder()
                .file(Paths.get("slice.dcm"))
                .dimension(4, 4)
                .imagePositionPatient(new double[]{0, 0, z})
                .imageOrientationPatient(AXIAL_ORIENTATION)
                .pixelSpacing(new double[]{0.5, 0.5})
                .build();
        SeriesAttributes attributes = new SeriesAttributes(seriesUid, null, "CT", "test", null, seriesNumber,
                new PixelAttributes(16, 0, 1.0, -1024.0, 40.0, 400.0));
        return new DicomInstance(slice, attributes);
    }
}
