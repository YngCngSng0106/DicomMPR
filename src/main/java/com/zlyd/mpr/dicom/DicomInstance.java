package com.zlyd.mpr.dicom;

/**
 * 一个 DICOM 实例：切片几何信息 + 序列级属性。
 */
public final class DicomInstance {

    private final SliceInfo slice;
    private final SeriesAttributes seriesAttributes;

    public DicomInstance(SliceInfo slice, SeriesAttributes seriesAttributes) {
        this.slice = slice;
        this.seriesAttributes = seriesAttributes;
    }

    public SliceInfo getSlice() {
        return slice;
    }

    public SeriesAttributes getSeriesAttributes() {
        return seriesAttributes;
    }
}
