package com.zlyd.mpr.dicom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

/**
 * 递归扫描目录并解析 DICOM 实例元数据（不含像素数据）。
 */
public final class DicomScanner {

    private static final Logger LOG = LogManager.getLogger(DicomScanner.class);

    private ScanStatistics statistics = new ScanStatistics(0, 0, 0);

    /**
     * 扫描目录下所有文件，返回可识别的 DICOM 实例。
     *
     * @param root 根目录
     * @return DICOM 实例列表（未分组）
     * @throws IOException 目录遍历失败
     */
    public List<DicomInstance> scan(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(files::add);
        }

        List<DicomInstance> result = new ArrayList<>();
        int skipped = 0;
        for (Path file : files) {
            DicomInstance instance = readInstance(file);
            if (instance == null) {
                skipped++;
            } else {
                result.add(instance);
            }
        }
        statistics = new ScanStatistics(files.size(), result.size(), skipped);
        return result;
    }

    public ScanStatistics getStatistics() {
        return statistics;
    }

    private DicomInstance readInstance(Path file) {
        try (DicomInputStream inputStream = new DicomInputStream(file.toFile())) {
            inputStream.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
            Attributes fileMeta = inputStream.readFileMetaInformation();
            Attributes dataset = inputStream.readDataset(Tag.PixelData);

            String sopClassUid = dataset.getString(Tag.SOPClassUID);
            String sopInstanceUid = dataset.getString(Tag.SOPInstanceUID);
            String seriesInstanceUid = dataset.getString(Tag.SeriesInstanceUID);
            if (StringUtils.isAnyBlank(sopClassUid, sopInstanceUid, seriesInstanceUid)) {
                return null;
            }

            SliceInfo slice = new SliceInfo.Builder()
                    .file(file)
                    .sopInstanceUid(sopInstanceUid)
                    .instanceNumber(dataset.getInt(Tag.InstanceNumber, 0))
                    .dimension(dataset.getInt(Tag.Rows, 0), dataset.getInt(Tag.Columns, 0))
                    .imagePositionPatient(dataset.getDoubles(Tag.ImagePositionPatient))
                    .imageOrientationPatient(dataset.getDoubles(Tag.ImageOrientationPatient))
                    .pixelSpacing(dataset.getDoubles(Tag.PixelSpacing))
                    .numberOfFrames(dataset.getInt(Tag.NumberOfFrames, 1))
                    .build();

            SeriesAttributes attributes = new SeriesAttributes(
                    seriesInstanceUid,
                    dataset.getString(Tag.FrameOfReferenceUID),
                    dataset.getString(Tag.Modality),
                    dataset.getString(Tag.SeriesDescription),
                    fileMeta != null ? fileMeta.getString(Tag.TransferSyntaxUID) : null,
                    dataset.getInt(Tag.SeriesNumber, 0),
                    new PixelAttributes(
                            dataset.getInt(Tag.BitsAllocated, 0),
                            dataset.getInt(Tag.PixelRepresentation, -1),
                            dataset.getDouble(Tag.RescaleSlope, 1.0),
                            dataset.getDouble(Tag.RescaleIntercept, 0.0),
                            readOptionalDouble(dataset, Tag.WindowCenter),
                            readOptionalDouble(dataset, Tag.WindowWidth)));

            return new DicomInstance(slice, attributes);
        } catch (IOException e) {
            LOG.debug("跳过非 DICOM 或读取失败的文件: {} ({})", file, e.getMessage());
            return null;
        } catch (RuntimeException e) {
            // dcm4che 对损坏文件可能抛出多种运行时异常；此处按文件粒度容错跳过
            LOG.debug("解析文件异常: {} ({})", file, e.getMessage());
            return null;
        }
    }

    private static Double readOptionalDouble(Attributes dataset, int tag) {
        if (!dataset.contains(tag)) {
            return null;
        }
        double value = dataset.getDouble(tag, Double.NaN);
        return Double.isNaN(value) ? null : value;
    }
}
