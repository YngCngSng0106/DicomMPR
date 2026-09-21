package com.zlyd.mpr.m2;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.dicom.DicomInstance;
import com.zlyd.mpr.dicom.DicomScanner;
import com.zlyd.mpr.dicom.SeriesGrouper;
import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.dicom.SliceInfo;
import com.zlyd.mpr.util.ThumbnailFactory;
import com.zlyd.mpr.util.WindowLevel;
import com.zlyd.mpr.util.WindowLevelDefaults;

/**
 * M2 无界面校验：为每个序列生成缩略图 PNG 到 target/thumbs。
 *
 * <p>用法：{@code mvn -q exec:java -Dexec.mainClass=com.zlyd.mpr.m2.M2ThumbnailCheck -Dexec.args="<目录>"}</p>
 */
public final class M2ThumbnailCheck {

    private static final Logger LOG = LogManager.getLogger(M2ThumbnailCheck.class);
    private static final int THUMBNAIL_SIZE = 96;

    private M2ThumbnailCheck() {
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Path outputDir = Paths.get("target", "thumbs");
        Files.createDirectories(outputDir);

        List<DicomInstance> instances = new DicomScanner().scan(root);
        List<SeriesInfo> seriesList = new SeriesGrouper().group(instances);
        LOG.info("序列数={} 输出目录={}", seriesList.size(), outputDir.toAbsolutePath());

        ThumbnailFactory thumbnailFactory = new ThumbnailFactory();
        for (int index = 0; index < seriesList.size(); index++) {
            writeThumbnail(thumbnailFactory, index + 1, seriesList.get(index), outputDir);
        }
    }

    private static void writeThumbnail(ThumbnailFactory thumbnailFactory, int index,
                                       SeriesInfo series, Path outputDir) throws IOException {
        LOG.info("[{}] Modality={} Desc={} 层数={} 可重建={}", index,
                series.getAttributes().getModality(), series.getAttributes().getSeriesDescription(),
                series.getSliceCount(), series.getGeometry().isValid());

        if (series.getSlices().isEmpty()) {
            LOG.warn("    无切片，跳过");
            return;
        }
        List<SliceInfo> slices = series.getSlices();
        SliceInfo middleSlice = slices.get(slices.size() / 2);
        WindowLevel windowLevel = WindowLevelDefaults.forSeries(series);
        BufferedImage image = thumbnailFactory.create(middleSlice.getFile(), windowLevel, THUMBNAIL_SIZE);
        if (image == null) {
            LOG.warn("    缩略图生成失败");
            return;
        }
        Path file = outputDir.resolve(String.format("series_%02d.png", index));
        ImageIO.write(image, "png", file.toFile());
        LOG.info("    缩略图={} ({}x{})", file.getFileName(), image.getWidth(), image.getHeight());
    }
}
