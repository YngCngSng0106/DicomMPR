package com.zlyd.mpr.util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 由切片生成缩略图（读取 + 缩放）。
 */
public final class ThumbnailFactory {

    private static final Logger LOG = LogManager.getLogger(ThumbnailFactory.class);

    private final DicomSliceImageReader imageReader = new DicomSliceImageReader();

    /**
     * 生成缩略图。
     *
     * @param file 切片文件
     * @param windowLevel 窗宽窗位
     * @param maxSize 缩略图最大边长
     * @return 缩略图；失败返回 {@code null}
     */
    public BufferedImage create(Path file, WindowLevel windowLevel, int maxSize) {
        try {
            BufferedImage image = imageReader.read(file, windowLevel);
            return image == null ? null : ImageScaler.scaleToFit(image, maxSize);
        } catch (IOException e) {
            LOG.debug("生成缩略图失败: {} ({})", file, e.getMessage());
            return null;
        }
    }
}
