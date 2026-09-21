package com.zlyd.mpr.mpr;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.dicom.SliceInfo;
import com.zlyd.mpr.dicom.SliceVoxelReader;
import com.zlyd.mpr.dicom.VolumeGeometry;
import com.zlyd.mpr.geometry.IsotropicResampler;
import com.zlyd.mpr.geometry.ResamplePlan;

import vtk.vtkImageData;
import vtk.vtkShortArray;

/**
 * 由序列装配 VTK 体数据（vtkImageData）。
 *
 * <p>只负责装配；像素读取见 {@link SliceVoxelReader}，几何换算见 {@link VolumeGeometry}，
 * 等体素重采样见 {@link IsotropicResampler}。</p>
 */
public final class VolumeBuilder {

    private static final Logger LOG = LogManager.getLogger(VolumeBuilder.class);

    private final SliceVoxelReader voxelReader = new SliceVoxelReader();

    /**
     * 构建体数据，并按需做等体素重采样。
     *
     * @param series 已排序的序列
     * @return 体数据与其几何（同源）
     * @throws IOException 读取像素失败
     */
    public BuiltVolume build(SeriesInfo series) throws IOException {
        return build(series, true);
    }

    /**
     * 构建体数据。
     *
     * @param series 已排序的序列
     * @param isotropic 是否对层厚较大的序列做等体素重采样
     * @return 体数据与其几何（同源）
     * @throws IOException 读取像素失败
     */
    public BuiltVolume build(SeriesInfo series, boolean isotropic) throws IOException {
        List<SliceInfo> slices = series.getSlices();
        if (slices.size() < 2) {
            throw new IllegalArgumentException("序列层数不足，无法构建体数据");
        }
        VolumeGeometry source = VolumeGeometry.of(series.getGeometry(),
                slices.get(0).getImagePositionPatient(), slices.size());
        short[] voxels = readVoxels(slices, source);
        ResamplePlan plan = isotropic
                ? IsotropicResampler.plan(source.getDimensions(), source.getSpacing())
                : null;
        boolean resampled = plan != null && plan.isNeeded();
        VolumeGeometry geometry = source;
        if (resampled) {
            voxels = IsotropicResampler.resample(voxels, source.getDimensions(), source.getSpacing(), plan);
            geometry = source.resampled(plan.getDimensions(), plan.getSpacing());
            LOG.info("等体素重采样: {} -> {}", Arrays.toString(source.getSpacing()),
                    String.format("%.3f", plan.getSpacing()));
        }
        BuiltVolume built = new BuiltVolume(toImageData(voxels, geometry), geometry, resampled);
        LOG.info("构建体数据: {}", built);
        return built;
    }

    private short[] readVoxels(List<SliceInfo> slices, VolumeGeometry geometry) throws IOException {
        int sliceSize = geometry.getColumns() * geometry.getRows();
        short[] voxels = new short[sliceSize * geometry.getSlices()];
        for (int sliceIndex = 0; sliceIndex < geometry.getSlices(); sliceIndex++) {
            voxelReader.readInto(slices.get(sliceIndex).getFile(), voxels,
                    sliceIndex * sliceSize, sliceSize);
        }
        return voxels;
    }

    private vtkImageData toImageData(short[] voxels, VolumeGeometry geometry) {
        vtkImageData image = new vtkImageData();
        int[] dimensions = geometry.getDimensions();
        double[] origin = geometry.getOrigin();
        double[] spacing = geometry.getSpacing();
        double[] axisX = geometry.getAxisX();
        double[] axisY = geometry.getAxisY();
        double[] axisZ = geometry.getAxisZ();

        image.SetDimensions(dimensions[0], dimensions[1], dimensions[2]);
        image.SetOrigin(origin[0], origin[1], origin[2]);
        image.SetSpacing(spacing[0], spacing[1], spacing[2]);
        image.SetDirectionMatrix(
                axisX[0], axisX[1], axisX[2],
                axisY[0], axisY[1], axisY[2],
                axisZ[0], axisZ[1], axisZ[2]);

        vtkShortArray scalars = new vtkShortArray();
        scalars.SetNumberOfComponents(1);
        scalars.SetJavaArray(voxels);
        image.GetPointData().SetScalars(scalars);
        return image;
    }
}
