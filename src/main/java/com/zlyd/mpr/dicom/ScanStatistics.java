package com.zlyd.mpr.dicom;

/**
 * 目录扫描统计结果。
 */
public final class ScanStatistics {

    private final int scannedFiles;
    private final int dicomFiles;
    private final int skippedFiles;

    public ScanStatistics(int scannedFiles, int dicomFiles, int skippedFiles) {
        this.scannedFiles = scannedFiles;
        this.dicomFiles = dicomFiles;
        this.skippedFiles = skippedFiles;
    }

    public int getScannedFiles() {
        return scannedFiles;
    }

    public int getDicomFiles() {
        return dicomFiles;
    }

    public int getSkippedFiles() {
        return skippedFiles;
    }
}
