package com.zlyd.mpr.m1;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.dicom.DicomInstance;
import com.zlyd.mpr.dicom.DicomScanner;
import com.zlyd.mpr.dicom.PixelAttributes;
import com.zlyd.mpr.dicom.ScanStatistics;
import com.zlyd.mpr.dicom.SeriesAttributes;
import com.zlyd.mpr.dicom.SeriesGeometry;
import com.zlyd.mpr.dicom.SeriesGrouper;
import com.zlyd.mpr.dicom.SeriesInfo;

/**
 * M1 数据层验证入口：扫描 → 分组 → 排序 → 输出报告。
 *
 * <p>用法：{@code mvn -q exec:java -Dexec.mainClass=com.zlyd.mpr.m1.M1Main -Dexec.args="<目录>"}</p>
 */
public final class M1Main {

    private static final Logger LOG = LogManager.getLogger(M1Main.class);
    private static final int BYTES_PER_MEGABYTE = 1024 * 1024;

    private M1Main() {
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        LOG.info("扫描目录: {}", root.toAbsolutePath());

        DicomScanner scanner = new DicomScanner();
        List<DicomInstance> instances = scanner.scan(root);
        List<SeriesInfo> seriesList = new SeriesGrouper().group(instances);

        reportSummary(scanner.getStatistics(), seriesList.size());
        for (int index = 0; index < seriesList.size(); index++) {
            reportSeries(index + 1, seriesList.get(index));
        }
    }

    private static void reportSummary(ScanStatistics statistics, int seriesCount) {
        LOG.info("文件总数={} DICOM={} 跳过={} 序列数={}",
                statistics.getScannedFiles(), statistics.getDicomFiles(),
                statistics.getSkippedFiles(), seriesCount);
    }

    private static void reportSeries(int index, SeriesInfo series) {
        SeriesAttributes attributes = series.getAttributes();
        SeriesGeometry geometry = series.getGeometry();
        PixelAttributes pixels = attributes.getPixelAttributes();

        LOG.info("[{}] Modality={} Series#={} Desc={}", index, attributes.getModality(),
                attributes.getSeriesNumber(), attributes.getSeriesDescription());
        LOG.info("    SeriesInstanceUID={}", attributes.getSeriesInstanceUid());
        LOG.info("    层数={} 尺寸={}x{}", series.getSliceCount(), geometry.getColumns(), geometry.getRows());
        LOG.info("    层间距={} 朝向一致={} 层距一致={} 丢层={} 可重建={}",
                format("%.4f", geometry.getSliceSpacing()), geometry.isOrientationConsistent(),
                geometry.isSpacingConsistent(), geometry.hasGap(), geometry.isValid());
        LOG.info("    像素: BitsAllocated={} PixelRepresentation={} Rescale={}/{} 窗={}/{}",
                pixels.getBitsAllocated(), pixels.getPixelRepresentation(),
                format("%.1f", pixels.getRescaleSlope()), format("%.1f", pixels.getRescaleIntercept()),
                formatDouble(pixels.getWindowWidth()), formatDouble(pixels.getWindowCenter()));
        LOG.info("    体积估算={} MB", format("%.1f", estimateMegabytes(series)));
        if (StringUtils.isNotBlank(geometry.getWarning())) {
            LOG.warn("    提示: {}", geometry.getWarning());
        }
    }

    private static double estimateMegabytes(SeriesInfo series) {
        SeriesGeometry geometry = series.getGeometry();
        int bytesPerVoxel = Math.max(1, series.getAttributes().getPixelAttributes().getBitsAllocated() / 8);
        long bytes = (long) geometry.getColumns() * geometry.getRows() * series.getSliceCount() * bytesPerVoxel;
        return (double) bytes / BYTES_PER_MEGABYTE;
    }

    private static String format(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }

    private static String formatDouble(Double value) {
        return value == null ? "N/A" : String.format(Locale.ROOT, "%.1f", value);
    }
}
