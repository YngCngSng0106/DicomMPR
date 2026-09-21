package com.zlyd.mpr.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.zlyd.mpr.dicom.PixelAttributes;
import com.zlyd.mpr.dicom.SeriesAttributes;
import com.zlyd.mpr.dicom.SeriesGeometry;
import com.zlyd.mpr.dicom.SeriesInfo;

/**
 * {@link WindowLevelDefaults} 的窗宽窗位回退与灰度映射测试（关键数学函数）。
 */
class WindowLevelDefaultsTest {

    private static final double DELTA = 1e-9;

    @Test
    void shouldClampBelowLowerBound() {
        assertEquals(0, WindowLevelDefaults.toDisplayValue(-200.0, new WindowLevel(400.0, 40.0)));
    }

    @Test
    void shouldClampAboveUpperBound() {
        assertEquals(255, WindowLevelDefaults.toDisplayValue(300.0, new WindowLevel(400.0, 40.0)));
    }

    @Test
    void shouldMapMiddleValueLinearly() {
        assertEquals(38, WindowLevelDefaults.toDisplayValue(-100.0, new WindowLevel(400.0, 40.0)));
    }

    @Test
    void shouldUseSeriesWindowWhenPresent() {
        WindowLevel windowLevel = WindowLevelDefaults.forSeries(series("CT", 1200.0, -600.0));
        assertEquals(1200.0, windowLevel.getWidth(), DELTA);
        assertEquals(-600.0, windowLevel.getCenter(), DELTA);
    }

    @Test
    void shouldFallbackToCtDefaultsWhenMissing() {
        WindowLevel windowLevel = WindowLevelDefaults.forSeries(series("CT", null, null));
        assertEquals(400.0, windowLevel.getWidth(), DELTA);
        assertEquals(40.0, windowLevel.getCenter(), DELTA);
    }

    @Test
    void shouldFallbackToGenericDefaultsForNonCt() {
        WindowLevel windowLevel = WindowLevelDefaults.forSeries(series("MR", null, null));
        assertEquals(256.0, windowLevel.getWidth(), DELTA);
        assertEquals(128.0, windowLevel.getCenter(), DELTA);
    }

    private static SeriesInfo series(String modality, Double width, Double center) {
        SeriesAttributes attributes = new SeriesAttributes("1.2.3", null, modality, "test", null, 1,
                new PixelAttributes(16, 0, 1.0, -1024.0, center, width));
        SeriesGeometry geometry = new SeriesGeometry.Builder().dimension(1, 1).build();
        return new SeriesInfo(attributes, geometry, Collections.emptyList(), 1);
    }
}
