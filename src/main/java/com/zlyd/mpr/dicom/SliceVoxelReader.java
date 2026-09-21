package com.zlyd.mpr.dicom;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

/**
 * 读取单张切片的像素值（应用 Rescale 后写入目标数组）。
 */
public final class SliceVoxelReader {

    private static final int BYTE_BITS = 8;
    private static final int SHORT_BITS = 16;

    /**
     * 将一张切片的体素值写入目标数组。
     *
     * @param file 切片文件
     * @param target 目标数组
     * @param offset 写入起始下标
     * @param pixelCount 该层像素数（行 × 列）
     * @throws IOException 读取失败或缺少像素数据
     */
    public void readInto(Path file, short[] target, int offset, int pixelCount) throws IOException {
        try (DicomInputStream inputStream = new DicomInputStream(file.toFile())) {
            Attributes dataset = inputStream.readDataset();
            byte[] raw = dataset.getSafeBytes(Tag.PixelData);
            if (raw == null) {
                throw new IOException("缺少像素数据: " + file);
            }

            int bitsAllocated = dataset.getInt(Tag.BitsAllocated, SHORT_BITS);
            int pixelRepresentation = dataset.getInt(Tag.PixelRepresentation, 0);
            double slope = dataset.getDouble(Tag.RescaleSlope, 1.0);
            double intercept = dataset.getDouble(Tag.RescaleIntercept, 0.0);
            ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);

            for (int index = 0; index < pixelCount; index++) {
                target[offset + index] = clampToShort(storedValue(buffer, raw, bitsAllocated,
                        pixelRepresentation, index) * slope + intercept);
            }
        }
    }

    private int storedValue(ByteBuffer buffer, byte[] raw, int bitsAllocated,
                            int pixelRepresentation, int index) {
        if (bitsAllocated == SHORT_BITS) {
            return pixelRepresentation == 1 ? buffer.getShort(index * 2) : (buffer.getShort(index * 2) & 0xFFFF);
        }
        if (bitsAllocated == BYTE_BITS) {
            return raw[index] & 0xFF;
        }
        return 0;
    }

    private short clampToShort(double value) {
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) Math.round(value);
    }
}
