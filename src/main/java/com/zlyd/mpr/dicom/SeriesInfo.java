package com.zlyd.mpr.dicom;

import java.util.Collections;
import java.util.List;

/**
 * 序列聚合根：序列属性 + 几何信息 + 已排序切片。
 *
 * <p>自身不做计算，几何由 {@link SeriesGeometryAnalyzer} 完成后注入。</p>
 */
public final class SeriesInfo {

    private final SeriesAttributes attributes;
    private final SeriesGeometry geometry;
    private final List<SliceInfo> slices;
    private final int multiFrameCount;

    public SeriesInfo(SeriesAttributes attributes, SeriesGeometry geometry,
                      List<SliceInfo> slices, int multiFrameCount) {
        this.attributes = attributes;
        this.geometry = geometry;
        this.slices = Collections.unmodifiableList(slices);
        this.multiFrameCount = multiFrameCount;
    }

    public SeriesAttributes getAttributes() {
        return attributes;
    }

    public SeriesGeometry getGeometry() {
        return geometry;
    }

    public List<SliceInfo> getSlices() {
        return slices;
    }

    public int getSliceCount() {
        return slices.size();
    }

    public int getMultiFrameCount() {
        return multiFrameCount;
    }
}
