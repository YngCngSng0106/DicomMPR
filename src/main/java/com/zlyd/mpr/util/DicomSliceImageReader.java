package com.zlyd.mpr.util;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

/**
 * 读取单张切片像素并应用 Rescale 与窗宽窗位，得到 8 位图像。
 *
 * <p>仅处理未压缩的灰度 / RGB 像素。</p>
 */
public final class DicomSliceImageReader {

    private static final int BYTE_BITS = 8;
    private static final int SHORT_BITS = 16;
    private static final int MAX_DISPLAY = 255;
    private static final int COLOR_CHANNELS = 3;

    /**
     * 读取一张切片为 8 位图像。
     *
     * @param file 切片文件
     * @param windowLevel 窗宽窗位
     * @return 图像；无像素数据时返回 {@code null}
     * @throws IOException 读取失败
     */
    public BufferedImage read(Path file, WindowLevel windowLevel) throws IOException {
        try (DicomInputStream inputStream = new DicomInputStream(file.toFile())) {
            Attributes dataset = inputStream.readDataset();
            int rows = dataset.getInt(Tag.Rows, 0);
            int columns = dataset.getInt(Tag.Columns, 0);
            byte[] raw = dataset.getSafeBytes(Tag.PixelData);
            if (rows <= 0 || columns <= 0 || raw == null) {
                return null;
            }
            int samplesPerPixel = dataset.getInt(Tag.SamplesPerPixel, 1);
            if (samplesPerPixel >= COLOR_CHANNELS) {
                return toColorImage(raw, rows, columns);
            }
            return toGrayImage(dataset, raw, rows, columns, windowLevel);
        }
    }

    private BufferedImage toColorImage(byte[] raw, int rows, int columns) {
        BufferedImage image = new BufferedImage(columns, rows, BufferedImage.TYPE_3BYTE_BGR);
        byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(raw, 0, target, 0, Math.min(target.length, rows * columns * COLOR_CHANNELS));
        return image;
    }

    private BufferedImage toGrayImage(Attributes dataset, byte[] raw, int rows, int columns,
                                      WindowLevel windowLevel) {
        int bitsAllocated = dataset.getInt(Tag.BitsAllocated, BYTE_BITS);
        double slope = dataset.getDouble(Tag.RescaleSlope, 1.0);
        double intercept = dataset.getDouble(Tag.RescaleIntercept, 0.0);
        boolean invert = "MONOCHROME1".equalsIgnoreCase(
                dataset.getString(Tag.PhotometricInterpretation, ""));

        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        BufferedImage image = new BufferedImage(columns, rows, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = image.getRaster();

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                int storedValue = storedValue(buffer, raw, bitsAllocated, index);
                int display = WindowLevelDefaults.toDisplayValue(storedValue * slope + intercept, windowLevel);
                raster.setSample(column, row, 0, invert ? MAX_DISPLAY - display : display);
            }
        }
        return image;
    }

    private int storedValue(ByteBuffer buffer, byte[] raw, int bitsAllocated, int index) {
        if (bitsAllocated == SHORT_BITS) {
            return buffer.getShort(index * 2) & 0xFFFF;
        }
        if (bitsAllocated == BYTE_BITS) {
            return raw[index] & 0xFF;
        }
        return 0;
    }
}
