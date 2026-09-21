package com.zlyd.mpr.mpr;

import com.zlyd.mpr.dicom.VolumeGeometry;

import vtk.vtkImageData;

/**
 * 体数据构建结果（值对象）。
 *
 * <p>把 vtkImageData 与其 {@link VolumeGeometry} 绑定在一起，保证两者同源：
 * 等体素重采样会同时改变维度与间距，若各调用点自行从序列推算几何就会不一致。</p>
 */
public final class BuiltVolume {

    private final vtkImageData image;
    private final VolumeGeometry geometry;
    private final boolean resampled;

    public BuiltVolume(vtkImageData image, VolumeGeometry geometry, boolean resampled) {
        this.image = image;
        this.geometry = geometry;
        this.resampled = resampled;
    }

    public vtkImageData getImage() {
        return image;
    }

    public VolumeGeometry getGeometry() {
        return geometry;
    }

    /**
     * 是否经过等体素重采样。
     */
    public boolean isResampled() {
        return resampled;
    }

    /**
     * 各向同性间距（列间距，列/行/层相同）。
     */
    public double getSpacing() {
        return geometry.getSpacing()[0];
    }

    @Override
    public String toString() {
        int[] dimensions = geometry.getDimensions();
        return dimensions[0] + "x" + dimensions[1] + "x" + dimensions[2]
                + " spacing=" + String.format("%.3f", getSpacing()) + "mm"
                + (resampled ? " (等体素重采样)" : "");
    }
}
