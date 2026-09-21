package com.zlyd.mpr.dicom;

import java.util.Arrays;

/**
 * 序列的几何信息与校验结果。
 *
 * <p>由 {@link SeriesGeometryAnalyzer} 计算后填充；不可变对象。</p>
 */
public final class SeriesGeometry {

    private final int rows;
    private final int columns;
    private final double[] pixelSpacing;
    private final double[] orientation;
    private final double[] normal;
    private final double sliceSpacing;
    private final boolean valid;
    private final boolean orientationConsistent;
    private final boolean spacingConsistent;
    private final boolean hasGap;
    private final String warning;

    private SeriesGeometry(Builder builder) {
        this.rows = builder.rows;
        this.columns = builder.columns;
        this.pixelSpacing = builder.pixelSpacing;
        this.orientation = builder.orientation;
        this.normal = builder.normal;
        this.sliceSpacing = builder.sliceSpacing;
        this.valid = builder.valid;
        this.orientationConsistent = builder.orientationConsistent;
        this.spacingConsistent = builder.spacingConsistent;
        this.hasGap = builder.hasGap;
        this.warning = builder.warning;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public double[] getPixelSpacing() {
        return copy(pixelSpacing);
    }

    public double[] getOrientation() {
        return copy(orientation);
    }

    public double[] getNormal() {
        return copy(normal);
    }

    public double getSliceSpacing() {
        return sliceSpacing;
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isOrientationConsistent() {
        return orientationConsistent;
    }

    public boolean isSpacingConsistent() {
        return spacingConsistent;
    }

    public boolean hasGap() {
        return hasGap;
    }

    public String getWarning() {
        return warning;
    }

    private static double[] copy(double[] source) {
        return source == null ? null : Arrays.copyOf(source, source.length);
    }

    /**
     * {@link SeriesGeometry} 的构建器。
     */
    public static final class Builder {

        private int rows;
        private int columns;
        private double[] pixelSpacing;
        private double[] orientation;
        private double[] normal;
        private double sliceSpacing;
        private boolean valid = true;
        private boolean orientationConsistent = true;
        private boolean spacingConsistent = true;
        private boolean hasGap;
        private String warning;

        public Builder dimension(int rows, int columns) {
            this.rows = rows;
            this.columns = columns;
            return this;
        }

        public Builder pixelSpacing(double[] pixelSpacing) {
            this.pixelSpacing = pixelSpacing;
            return this;
        }

        public Builder orientation(double[] orientation) {
            this.orientation = orientation;
            return this;
        }

        public Builder normal(double[] normal) {
            this.normal = normal;
            return this;
        }

        public Builder sliceSpacing(double sliceSpacing) {
            this.sliceSpacing = sliceSpacing;
            return this;
        }

        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }

        public Builder orientationConsistent(boolean orientationConsistent) {
            this.orientationConsistent = orientationConsistent;
            return this;
        }

        public Builder spacingConsistent(boolean spacingConsistent) {
            this.spacingConsistent = spacingConsistent;
            return this;
        }

        public Builder hasGap(boolean hasGap) {
            this.hasGap = hasGap;
            return this;
        }

        public Builder warning(String warning) {
            this.warning = warning;
            return this;
        }

        public SeriesGeometry build() {
            return new SeriesGeometry(this);
        }
    }
}
