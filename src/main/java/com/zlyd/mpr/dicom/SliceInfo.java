package com.zlyd.mpr.dicom;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * 单张切片的几何与标识信息。
 *
 * <p>只在构建体数据/排序时使用；不包含序列级属性（见 {@link SeriesAttributes}）。</p>
 */
public final class SliceInfo {

    private final Path file;
    private final String sopInstanceUid;
    private final int instanceNumber;
    private final int rows;
    private final int columns;
    private final double[] imagePositionPatient;
    private final double[] imageOrientationPatient;
    private final double[] pixelSpacing;
    private final int numberOfFrames;

    private double projectedPosition;

    private SliceInfo(Builder builder) {
        this.file = builder.file;
        this.sopInstanceUid = builder.sopInstanceUid;
        this.instanceNumber = builder.instanceNumber;
        this.rows = builder.rows;
        this.columns = builder.columns;
        this.imagePositionPatient = builder.imagePositionPatient;
        this.imageOrientationPatient = builder.imageOrientationPatient;
        this.pixelSpacing = builder.pixelSpacing;
        this.numberOfFrames = builder.numberOfFrames;
    }

    public Path getFile() {
        return file;
    }

    public String getSopInstanceUid() {
        return sopInstanceUid;
    }

    public int getInstanceNumber() {
        return instanceNumber;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public double[] getImagePositionPatient() {
        return copy(imagePositionPatient);
    }

    public double[] getImageOrientationPatient() {
        return copy(imageOrientationPatient);
    }

    public double[] getPixelSpacing() {
        return copy(pixelSpacing);
    }

    public int getNumberOfFrames() {
        return numberOfFrames;
    }

    /**
     * 是否具备构建体数据所需的几何信息。
     */
    public boolean hasGeometry() {
        return imagePositionPatient != null && imagePositionPatient.length >= 3
                && imageOrientationPatient != null && imageOrientationPatient.length >= 6
                && pixelSpacing != null && pixelSpacing.length >= 2;
    }

    private static double[] copy(double[] source) {
        return source == null ? null : Arrays.copyOf(source, source.length);
    }

    public double getProjectedPosition() {
        return projectedPosition;
    }

    void setProjectedPosition(double projectedPosition) {
        this.projectedPosition = projectedPosition;
    }

    /**
     * {@link SliceInfo} 的构建器。
     */
    public static final class Builder {

        private Path file;
        private String sopInstanceUid;
        private int instanceNumber;
        private int rows;
        private int columns;
        private double[] imagePositionPatient;
        private double[] imageOrientationPatient;
        private double[] pixelSpacing;
        private int numberOfFrames = 1;

        public Builder file(Path file) {
            this.file = file;
            return this;
        }

        public Builder sopInstanceUid(String sopInstanceUid) {
            this.sopInstanceUid = sopInstanceUid;
            return this;
        }

        public Builder instanceNumber(int instanceNumber) {
            this.instanceNumber = instanceNumber;
            return this;
        }

        public Builder dimension(int rows, int columns) {
            this.rows = rows;
            this.columns = columns;
            return this;
        }

        public Builder imagePositionPatient(double[] imagePositionPatient) {
            this.imagePositionPatient = imagePositionPatient;
            return this;
        }

        public Builder imageOrientationPatient(double[] imageOrientationPatient) {
            this.imageOrientationPatient = imageOrientationPatient;
            return this;
        }

        public Builder pixelSpacing(double[] pixelSpacing) {
            this.pixelSpacing = pixelSpacing;
            return this;
        }

        public Builder numberOfFrames(int numberOfFrames) {
            this.numberOfFrames = numberOfFrames;
            return this;
        }

        public SliceInfo build() {
            return new SliceInfo(this);
        }
    }
}
