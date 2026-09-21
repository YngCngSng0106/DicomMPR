package com.zlyd.mpr.dicom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 SeriesInstanceUID 分组，并装配为序列。
 */
public final class SeriesGrouper {

    private final SeriesGeometryAnalyzer geometryAnalyzer = new SeriesGeometryAnalyzer();

    /**
     * 分组并装配序列。
     *
     * @param instances 未分组的 DICOM 实例
     * @return 序列列表（按序列号升序）
     */
    public List<SeriesInfo> group(List<DicomInstance> instances) {
        Map<String, List<DicomInstance>> bySeriesUid = new LinkedHashMap<>();
        for (DicomInstance instance : instances) {
            String uid = instance.getSeriesAttributes().getSeriesInstanceUid();
            bySeriesUid.computeIfAbsent(uid, key -> new ArrayList<>()).add(instance);
        }

        List<SeriesInfo> result = new ArrayList<>();
        for (List<DicomInstance> group : bySeriesUid.values()) {
            result.add(assemble(group));
        }
        result.sort(Comparator.comparingInt(series -> series.getAttributes().getSeriesNumber()));
        return result;
    }

    private SeriesInfo assemble(List<DicomInstance> group) {
        SeriesAttributes attributes = group.get(0).getSeriesAttributes();
        List<SliceInfo> slices = new ArrayList<>();
        int multiFrameCount = 1;
        for (DicomInstance instance : group) {
            slices.add(instance.getSlice());
            multiFrameCount = Math.max(multiFrameCount, instance.getSlice().getNumberOfFrames());
        }
        SeriesGeometry geometry = geometryAnalyzer.analyze(slices, multiFrameCount);
        return new SeriesInfo(attributes, geometry, slices, multiFrameCount);
    }
}
