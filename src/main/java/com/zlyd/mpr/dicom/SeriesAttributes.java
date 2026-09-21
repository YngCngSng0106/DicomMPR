package com.zlyd.mpr.dicom;

/**
 * 序列级标识与属性：UID、模态、描述、传输语法与像素属性。
 */
public final class SeriesAttributes {

    private final String seriesInstanceUid;
    private final String frameOfReferenceUid;
    private final String modality;
    private final String seriesDescription;
    private final String transferSyntaxUid;
    private final int seriesNumber;
    private final PixelAttributes pixelAttributes;

    public SeriesAttributes(String seriesInstanceUid,
                            String frameOfReferenceUid,
                            String modality,
                            String seriesDescription,
                            String transferSyntaxUid,
                            int seriesNumber,
                            PixelAttributes pixelAttributes) {
        this.seriesInstanceUid = seriesInstanceUid;
        this.frameOfReferenceUid = frameOfReferenceUid;
        this.modality = modality;
        this.seriesDescription = seriesDescription;
        this.transferSyntaxUid = transferSyntaxUid;
        this.seriesNumber = seriesNumber;
        this.pixelAttributes = pixelAttributes;
    }

    public String getSeriesInstanceUid() {
        return seriesInstanceUid;
    }

    public String getFrameOfReferenceUid() {
        return frameOfReferenceUid;
    }

    public String getModality() {
        return modality;
    }

    public String getSeriesDescription() {
        return seriesDescription;
    }

    public String getTransferSyntaxUid() {
        return transferSyntaxUid;
    }

    public int getSeriesNumber() {
        return seriesNumber;
    }

    public PixelAttributes getPixelAttributes() {
        return pixelAttributes;
    }
}
