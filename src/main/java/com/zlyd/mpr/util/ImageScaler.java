package com.zlyd.mpr.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 图像缩放工具。
 */
public final class ImageScaler {

    private ImageScaler() {
    }

    /**
     * 等比缩放，使长边等于 {@code maxSize}。
     *
     * @param source 源图
     * @param maxSize 目标长边
     * @return 缩放后的 RGB 图像
     */
    public static BufferedImage scaleToFit(BufferedImage source, int maxSize) {
        double factor = Math.min((double) maxSize / source.getWidth(), (double) maxSize / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));

        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }
}
