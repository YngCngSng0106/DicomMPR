package com.zlyd.mpr.ui;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.ImageIcon;
import javax.swing.SwingWorker;

import org.apache.commons.collections4.CollectionUtils;

import com.zlyd.mpr.dicom.DicomInstance;
import com.zlyd.mpr.dicom.DicomScanner;
import com.zlyd.mpr.dicom.SeriesGrouper;
import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.dicom.SliceInfo;
import com.zlyd.mpr.util.ThumbnailFactory;
import com.zlyd.mpr.util.WindowLevel;
import com.zlyd.mpr.util.WindowLevelDefaults;

/**
 * 后台加载目录：扫描 → 分组 → 生成缩略图。
 */
final class SeriesLoader {

    private static final int THUMBNAIL_SIZE = 96;

    private final ThumbnailFactory thumbnailFactory = new ThumbnailFactory();

    /**
     * 异步加载指定目录。
     *
     * @param root 根目录
     * @param onSuccess 成功回调（在 EDT 执行）
     * @param onFailure 失败回调（在 EDT 执行）
     */
    void load(Path root, Consumer<List<SeriesEntry>> onSuccess, Consumer<Exception> onFailure) {
        new SwingWorker<List<SeriesEntry>, Void>() {
            @Override
            protected List<SeriesEntry> doInBackground() throws Exception {
                return scanAndBuild(root);
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    onFailure.accept(e);
                } catch (java.util.concurrent.ExecutionException e) {
                    onFailure.accept(e.getCause() instanceof Exception
                            ? (Exception) e.getCause() : new IllegalStateException(e.getCause()));
                }
            }
        }.execute();
    }

    private List<SeriesEntry> scanAndBuild(Path root) throws Exception {
        List<DicomInstance> instances = new DicomScanner().scan(root);
        List<SeriesInfo> seriesList = new SeriesGrouper().group(instances);

        List<SeriesEntry> entries = new ArrayList<>();
        for (SeriesInfo series : seriesList) {
            entries.add(new SeriesEntry(series, createThumbnail(series)));
        }
        return entries;
    }

    private ImageIcon createThumbnail(SeriesInfo series) {
        List<SliceInfo> slices = series.getSlices();
        if (CollectionUtils.isEmpty(slices)) {
            return null;
        }
        SliceInfo middleSlice = slices.get(slices.size() / 2);
        WindowLevel windowLevel = WindowLevelDefaults.forSeries(series);
        BufferedImage image = thumbnailFactory.create(middleSlice.getFile(), windowLevel, THUMBNAIL_SIZE);
        return image == null ? null : new ImageIcon(image);
    }
}
