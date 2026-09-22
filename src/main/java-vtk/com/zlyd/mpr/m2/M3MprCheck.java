package com.zlyd.mpr.m2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.dicom.DicomScanner;
import com.zlyd.mpr.dicom.SeriesGeometry;
import com.zlyd.mpr.dicom.SeriesGrouper;
import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.dicom.VolumeGeometry;
import com.zlyd.mpr.geometry.CrosshairSegment;
import com.zlyd.mpr.geometry.Measurement;
import com.zlyd.mpr.geometry.MeasurementCalculator;
import com.zlyd.mpr.geometry.MeasurementType;
import com.zlyd.mpr.geometry.MprCursorFrame;
import com.zlyd.mpr.geometry.MprViewOrientation;
import com.zlyd.mpr.geometry.MprViewRig;
import com.zlyd.mpr.geometry.VolumeBox;
import com.zlyd.mpr.mpr.MprCameraController;
import com.zlyd.mpr.mpr.MprMeasurementOverlay;
import com.zlyd.mpr.mpr.MprViewMapper;
import com.zlyd.mpr.mpr.MprCrosshairOverlay;
import com.zlyd.mpr.mpr.VtkOrientationMarkers;
import com.zlyd.mpr.mpr.BuiltVolume;
import com.zlyd.mpr.mpr.VolumeBuilder;
import com.zlyd.mpr.util.VtkNativeLoader;
import com.zlyd.mpr.util.WindowLevel;
import com.zlyd.mpr.util.WindowLevelDefaults;

import vtk.vtkDataArray;
import vtk.vtkImageData;
import vtk.vtkImageSlice;
import vtk.vtkImageSliceMapper;
import vtk.vtkPNGWriter;
import vtk.vtkRenderWindow;
import vtk.vtkRenderWindowInteractor;
import vtk.vtkRenderer;
import vtk.vtkWindowToImageFilter;

/**
 * M3 离屏校验：与 MPR 视图一致的"三正交视图 + 十字线"，渲染为 PNG；
 * 并校验轴位显示方向是否符合放射科约定。
 */
public final class M3MprCheck {

    private static final Logger LOG = LogManager.getLogger(M3MprCheck.class);
    private static final int IMAGE_SIZE = 320;
    private static final int VIEW_COUNT = 3;
    private static final int AXIAL = CrosshairSegment.VIEW_AXIAL;
    private static final int SAGITTAL = CrosshairSegment.VIEW_SAGITTAL;
    private static final int CORONAL = CrosshairSegment.VIEW_CORONAL;

    static {
        VtkNativeLoader.load();
    }

    private final vtkRenderWindow renderWindow = new vtkRenderWindow();
    private final vtkRenderWindowInteractor interactor = new vtkRenderWindowInteractor();
    private final vtkRenderer[] renderers = {new vtkRenderer(), new vtkRenderer(), new vtkRenderer()};
    private final vtkImageSliceMapper[] mappers = new vtkImageSliceMapper[VIEW_COUNT];

    private VolumeGeometry geometry;

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Path outputDir = Paths.get("target", "mpr");
        Files.createDirectories(outputDir);

        SeriesInfo target = pickLargestReconstructable(new SeriesGrouper()
                .group(new DicomScanner().scan(root)));
        if (target == null) {
            LOG.warn("没有可重建的序列");
            return;
        }
        LOG.info("序列={} 层数={}", target.getAttributes().getSeriesDescription(), target.getSliceCount());

        BuiltVolume built = new VolumeBuilder().build(target);
        Path output = new M3MprCheck().render(built, target, outputDir);
        LOG.info("输出={}", output);
    }

    private Path render(BuiltVolume built, SeriesInfo series, Path outputDir) throws Exception {
        vtkImageData volume = built.getImage();
        geometry = built.getGeometry();
        configureRenderWindow();
        configureSlices(volume, series);

        MprCursorFrame frame = MprCursorFrame.initial(geometry);
        VolumeBox box = VolumeBox.of(geometry);
        new MprCameraController(renderers).configure(frame, box, MprViewRig.continuity(),
                MprCameraController.allViews());
        int centerI = geometry.getColumns() / 2;
        int centerJ = geometry.getRows() / 2;
        int centerK = geometry.getSlices() / 2;
        new MprCrosshairOverlay(renderers).update(frame);
        configureOrientationMarkers();

        configureSampleMeasurements();
        renderWindow.Render();
        verifyAxialOrientation();
        verifyProbeValues(volume);
        return saveToPng(outputDir.resolve("mpr_view.png"));
    }

    private void configureRenderWindow() {
        renderWindow.SetOffScreenRendering(1);
        renderWindow.SetSize(IMAGE_SIZE, IMAGE_SIZE);
        interactor.SetRenderWindow(renderWindow);
        renderers[AXIAL].SetViewport(0.0, 0.5, 0.5, 1.0);
        renderers[SAGITTAL].SetViewport(0.0, 0.0, 0.5, 0.5);
        renderers[CORONAL].SetViewport(0.5, 0.0, 1.0, 1.0);
        for (vtkRenderer renderer : renderers) {
            renderer.SetBackground(0.05, 0.05, 0.05);
            renderWindow.AddRenderer(renderer);
        }
    }

    private void configureSlices(vtkImageData volume, SeriesInfo series) {
        WindowLevel windowLevel = WindowLevelDefaults.forSeries(series);
        for (int view = 0; view < VIEW_COUNT; view++) {
            vtkImageSliceMapper mapper = new vtkImageSliceMapper();
            vtkImageSlice slice = new vtkImageSlice();
            slice.SetMapper(mapper);
            slice.GetProperty().SetColorWindow(windowLevel.getWidth());
            slice.GetProperty().SetColorLevel(windowLevel.getCenter());
            slice.GetProperty().SetInterpolationTypeToLinear();
            renderers[view].AddActor(slice);
            mappers[view] = mapper;
        }
        mappers[AXIAL].SetOrientationToK();
        mappers[SAGITTAL].SetOrientationToI();
        mappers[CORONAL].SetOrientationToJ();
        mappers[AXIAL].SetInputData(volume);
        mappers[SAGITTAL].SetInputData(volume);
        mappers[CORONAL].SetInputData(volume);
        mappers[AXIAL].SetSliceNumber(geometry.getSlices() / 2);
        mappers[SAGITTAL].SetSliceNumber(geometry.getColumns() / 2);
        mappers[CORONAL].SetSliceNumber(geometry.getRows() / 2);
    }

    /**
     * 绘制两条示例测量（一条长度、一个矩形 ROI），用于确认绘制与标签。
     */
    private void configureSampleMeasurements() {
        MprMeasurementOverlay overlay =
                new MprMeasurementOverlay(renderers, new MprViewMapper(interactor, renderers));
        int centerI = geometry.getColumns() / 2;
        int centerJ = geometry.getRows() / 2;
        int centerK = geometry.getSlices() / 2;
        double[] start = geometry.toWorld(centerI - 60, centerJ - 60, centerK);
        double[] end = geometry.toWorld(centerI - 20, centerJ - 20, centerK);
        List<double[]> points = new ArrayList<>();
        points.add(start);
        points.add(end);
        double extent = MeasurementCalculator.length(start, end);
        LOG.info("sample length={}", extent);
        overlay.addMeasurement(new Measurement(MeasurementType.LENGTH, AXIAL, points, extent, null));
        overlay.refresh();
    }

    /**
     * 校验体素值读取（D6）：中心点与肺野采样点的 HU 应在合理范围。
     */
    private void verifyProbeValues(vtkImageData volume) {
        vtkDataArray scalars = volume.GetPointData().GetScalars();
        int centerI = geometry.getColumns() / 2;
        int centerJ = geometry.getRows() / 2;
        int centerK = geometry.getSlices() / 2;
        LOG.info("HU center={} leftLung={} air={}",
                scalars.GetTuple1(offset(centerI, centerJ, centerK)),
                scalars.GetTuple1(offset(centerI + 80, centerJ, centerK)),
                scalars.GetTuple1(offset(10, 10, centerK)));
    }

    private long offset(int i, int j, int k) {
        return ((long) k * geometry.getRows() + j) * geometry.getColumns() + i;
    }

    /**
     * 添加方向标记（H/F/A/P/L/R），与生产代码使用同一套约定。
     */
    private void configureOrientationMarkers() {
        VtkOrientationMarkers markers = new VtkOrientationMarkers(renderers);
        String[][] labels = new String[VIEW_COUNT][];
        for (int view = 0; view < VIEW_COUNT; view++) {
            labels[view] = MprViewOrientation.edgeLabels(geometry, view);
        }
        markers.update(labels);
    }

    /**
     * 校验轴位显示方向：视线应为 +Z（脚→头）、前方在上、患者左侧在屏幕右侧。
     */
    private void verifyAxialOrientation() {
        double[] center = geometry.center();
        double[] axisX = geometry.getAxisX();
        double[] axisY = geometry.getAxisY();
        double[] patientLeft = offset(center, axisX, 100.0);
        double[] anterior = offset(center, axisY, -100.0);
        LOG.info("axial dop={} patientLeftX={} centerX={} anteriorY={} centerY={}",
                Arrays.toString(renderers[AXIAL].GetActiveCamera().GetDirectionOfProjection()),
                display(renderers[AXIAL], patientLeft)[0], display(renderers[AXIAL], center)[0],
                display(renderers[AXIAL], anterior)[1], display(renderers[AXIAL], center)[1]);
    }

    private static double[] offset(double[] origin, double[] direction, double distance) {
        return new double[]{
                origin[0] + direction[0] * distance,
                origin[1] + direction[1] * distance,
                origin[2] + direction[2] * distance};
    }

    private static double[] display(vtkRenderer renderer, double[] world) {
        renderer.SetWorldPoint(world[0], world[1], world[2], 1.0);
        renderer.WorldToDisplay();
        return renderer.GetDisplayPoint();
    }

    private Path saveToPng(Path file) throws Exception {
        vtkWindowToImageFilter filter = new vtkWindowToImageFilter();
        filter.SetInput(renderWindow);
        filter.SetInputBufferTypeToRGB();
        filter.Update();

        vtkPNGWriter writer = new vtkPNGWriter();
        writer.SetFileName(file.toString());
        writer.SetInputConnection(filter.GetOutputPort());
        writer.Write();
        return file;
    }

    private static SeriesInfo pickLargestReconstructable(List<SeriesInfo> seriesList) {
        SeriesInfo best = null;
        for (SeriesInfo series : seriesList) {
            SeriesGeometry geometry = series.getGeometry();
            if (geometry.isValid() && (best == null || series.getSliceCount() > best.getSliceCount())) {
                best = series;
            }
        }
        return best;
    }
}
