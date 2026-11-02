package com.zlyd.mpr.m2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.dicom.DicomInstance;
import com.zlyd.mpr.dicom.DicomScanner;
import com.zlyd.mpr.dicom.SeriesGrouper;
import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.mpr.BuiltVolume;
import com.zlyd.mpr.mpr.VolumeBuilder;
import com.zlyd.mpr.util.VtkNativeLoader;
import com.zlyd.mpr.util.WindowLevel;
import com.zlyd.mpr.util.WindowLevelDefaults;

import vtk.vtkImageData;
import vtk.vtkImageSlice;
import vtk.vtkImageSliceMapper;
import vtk.vtkPNGWriter;
import vtk.vtkRenderWindow;
import vtk.vtkRenderer;
import vtk.vtkWindowToImageFilter;

/**
 * M2.1 无界面校验：构建体数据并离屏渲染首/中/末三层。
 */
public final class M2VolumeCheck {

    private static final Logger LOG = LogManager.getLogger(M2VolumeCheck.class);
    private static final int IMAGE_SIZE = 256;

    private M2VolumeCheck() {
    }

    public static void main(String[] args) throws Exception {
        VtkNativeLoader.load();

        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Path outputDir = Paths.get("target", "volume");
        Files.createDirectories(outputDir);

        SeriesInfo target = pickLargestReconstructable(new SeriesGrouper()
                .group(new DicomScanner().scan(root)));
        if (target == null) {
            LOG.warn("没有可重建的序列");
            return;
        }
        LOG.info("序列={} 层数={}", target.getAttributes().getSeriesDescription(), target.getSliceCount());

        BuiltVolume built = new VolumeBuilder().build(target);
        vtkImageData volume = built.getImage();
        int[] dimensions = volume.GetDimensions();
        LOG.info("vtkImageData dims={} spacing={}",
                Arrays.toString(dimensions), Arrays.toString(volume.GetSpacing()));

        renderSlices(volume, WindowLevelDefaults.forSeries(target), dimensions[2], outputDir);
    }

    private static void renderSlices(vtkImageData volume, WindowLevel windowLevel,
                                     int sliceCount, Path outputDir) throws IOException {
        vtkRenderer renderer = new vtkRenderer();
        renderer.SetBackground(0.0, 0.0, 0.0);

        vtkRenderWindow renderWindow = new vtkRenderWindow();
        renderWindow.SetOffScreenRendering(1);
        renderWindow.SetSize(IMAGE_SIZE, IMAGE_SIZE);
        renderWindow.AddRenderer(renderer);

        vtkImageSliceMapper mapper = new vtkImageSliceMapper();
        mapper.SetInputData(volume);
        mapper.SetOrientationToK();

        vtkImageSlice imageSlice = new vtkImageSlice();
        imageSlice.SetMapper(mapper);
        imageSlice.GetProperty().SetColorWindow(windowLevel.getWidth());
        imageSlice.GetProperty().SetColorLevel(windowLevel.getCenter());
        imageSlice.GetProperty().SetInterpolationTypeToLinear();
        renderer.AddActor(imageSlice);
        renderer.GetActiveCamera().ParallelProjectionOn();

        int[] sliceNumbers = {0, sliceCount / 2, sliceCount - 1};
        for (int sliceNumber : sliceNumbers) {
            mapper.SetSliceNumber(sliceNumber);
            renderer.ResetCamera();
            renderer.ResetCameraClippingRange();
            renderWindow.Render();

            Path file = outputDir.resolve(String.format("axial_%03d.png", sliceNumber));
            writePng(renderWindow, file);
            LOG.info("输出={}", file);
        }
    }

    private static void writePng(vtkRenderWindow renderWindow, Path file) throws IOException {
        vtkWindowToImageFilter filter = new vtkWindowToImageFilter();
        filter.SetInput(renderWindow);
        filter.SetInputBufferTypeToRGB();
        filter.Update();

        vtkPNGWriter writer = new vtkPNGWriter();
        writer.SetFileName(file.toString());
        writer.SetInputConnection(filter.GetOutputPort());
        writer.Write();
    }

    private static SeriesInfo pickLargestReconstructable(List<SeriesInfo> seriesList) {
        SeriesInfo best = null;
        for (SeriesInfo series : seriesList) {
            if (series.getGeometry().isValid()
                    && (best == null || series.getSliceCount() > best.getSliceCount())) {
                best = series;
            }
        }
        return best;
    }
}
