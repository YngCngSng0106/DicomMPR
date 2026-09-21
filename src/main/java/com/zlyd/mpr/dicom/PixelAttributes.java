package com.zlyd.mpr.dicom;

/**
 * 像素属性：位深、符号、Rescale 与窗宽窗位。
 */
public final class PixelAttributes {

    private final int bitsAllocated;
    private final int pixelRepresentation;
    private final double rescaleSlope;
    private final double rescaleIntercept;
    private final Double windowCenter;
    private final Double windowWidth;

    public PixelAttributes(int bitsAllocated,
                           int pixelRepresentation,
                           double rescaleSlope,
                           double rescaleIntercept,
                           Double windowCenter,
                           Double windowWidth) {
        this.bitsAllocated = bitsAllocated;
        this.pixelRepresentation = pixelRepresentation;
        this.rescaleSlope = rescaleSlope;
        this.rescaleIntercept = rescaleIntercept;
        this.windowCenter = windowCenter;
        this.windowWidth = windowWidth;
    }

    public int getBitsAllocated() {
        return bitsAllocated;
    }

    public int getPixelRepresentation() {
        return pixelRepresentation;
    }

    public double getRescaleSlope() {
        return rescaleSlope;
    }

    public double getRescaleIntercept() {
        return rescaleIntercept;
    }

    public Double getWindowCenter() {
        return windowCenter;
    }

    public Double getWindowWidth() {
        return windowWidth;
    }
}
