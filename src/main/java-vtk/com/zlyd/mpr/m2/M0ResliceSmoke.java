package com.zlyd.mpr.m2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.dicom.DicomScanner;
import com.zlyd.mpr.dicom.SeriesGeometry;
import com.zlyd.mpr.dicom.SeriesGrouper;
import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.dicom.VolumeGeometry;
import com.zlyd.mpr.mpr.BuiltVolume;
import com.zlyd.mpr.mpr.VolumeBuilder;
import com.zlyd.mpr.util.VtkImageExporter;
import com.zlyd.mpr.util.VtkNativeLoader;
import com.zlyd.mpr.util.WindowLevel;
import com.zlyd.mpr.util.WindowLevelDefaults;

import vtk.vtkCamera;
import vtk.vtkDataArray;
import vtk.vtkImageData;
import vtk.vtkImageInterpolator;
import vtk.vtkImageResliceMapper;
import vtk.vtkImageSlice;
import vtk.vtkPlane;
import vtk.vtkRenderWindow;
import vtk.vtkRenderWindowInteractor;
import vtk.vtkRenderer;
import vtk.vtkShortArray;

/**
 * S0 预检：确认 {@code vtkImageResliceMapper} + {@code vtkPlane} 可用，且**显式平面**（含斜切）取样正确。
 *
 * <p>判定方法：相机平行投影、焦点放在 F、视线沿 viewDir；屏幕中心像素对应"视线与平面的交点"，
 * 其灰度应等于该交点的三线性 HU 经窗宽窗位映射后的灰度。四个用例：轴对齐、斜切 45°、
 * 平面沿法向平移 20mm（焦点仍在体中心，用于区分是否真的用了显式平面）、斜平面+侧视。</p>
 */
public final class M0ResliceSmoke {

    private static final Logger LOG = LogManager.getLogger(M0ResliceSmoke.class);
    private static final int SIZE = 256;
    private static final int GRAY_TOLERANCE = 3;
    /** 真实体数据/插值用例容差：读取中心像素存在半像素几何偏差，乘以局部 HU 梯度即为差值量级。 */
    private static final int SAMPLING_TOLERANCE = 8;

    static {
        VtkNativeLoader.load();
    }

    private M0ResliceSmoke() {
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Path outputDir = Paths.get("target", "reslice");
        Files.createDirectories(outputDir);

        SeriesInfo target = pickLargestReconstructable(new SeriesGrouper()
                .group(new DicomScanner().scan(root)));
        if (target == null) {
            LOG.warn("没有可重建的序列");
            return;
        }
        BuiltVolume built = new VolumeBuilder().build(target);
        VolumeGeometry geometry = built.getGeometry();
        WindowLevel windowLevel = new WindowLevel(400.0, 40.0);
        double[] center = geometry.center();
        double[] axisV = geometry.getAxisY();
        double[] axisW = geometry.getAxisZ();
        double[] oblique = normalize(add(axisW, axisV));
        double[] obliqueUp = normalize(subtract(axisW, axisV));

        LOG.info("体数据={} 窗宽窗位={}/{}", built, windowLevel.getWidth(), windowLevel.getCenter());

        boolean mappingOk = runSyntheticCase("常量100HU(仅验映射)", windowLevel, 2, 100.0,
                outputDir.resolve("s0_const100.png"))
                && runSyntheticCase("常量0HU(仅验映射)", windowLevel, 0, 0.0,
                outputDir.resolve("s0_const0.png"));
        boolean interpOk = runSyntheticCase("交替0/100HU(验插值,线性应为50HU)", windowLevel, 1, 50.0,
                outputDir.resolve("s0_alternating.png"));

        boolean axisOk = runCase("轴对齐(平面⊥Z, 视线+Z)", built, geometry, windowLevel,
                center, axisW, center, axisW, negate(axisV), outputDir.resolve("s0_axis.png"));
        boolean obliqueOk = runCase("斜切45°(法向(Z+Y)/√2)", built, geometry, windowLevel,
                center, oblique, center, oblique, obliqueUp, outputDir.resolve("s0_oblique.png"));
        boolean offsetOk = runCase("显式平面平移20mm", built, geometry, windowLevel,
                offset(center, axisW, 20.0), axisW, center, axisW, negate(axisV),
                outputDir.resolve("s0_offset.png"));
        double step = geometry.getSpacing()[2];
        boolean integerOk = runCase("整数体素偏移(26层)", built, geometry, windowLevel,
                offset(center, axisW, 26.0 * step), axisW, center, axisW, negate(axisV),
                outputDir.resolve("s0_integer.png"));
        boolean sideOk = runCase("斜平面+侧视(视线+Z)", built, geometry, windowLevel,
                offset(center, oblique, 20.0), oblique, center, axisW, negate(axisV),
                outputDir.resolve("s0_side.png"));

        LOG.info("S0 结论: {} (mapping={} interp={} axis={} oblique={} offset={} side={})",
                mappingOk && interpOk && axisOk && obliqueOk && offsetOk && integerOk && sideOk,
                mappingOk, interpOk, axisOk, obliqueOk, offsetOk, sideOk);
        System.exit(mappingOk && interpOk && axisOk && obliqueOk && offsetOk && sideOk ? 0 : 1);
    }

    /**
     * 合成体数据隔离校验。
     *
     * <p>{@code mode=2}：常量卷（与插值无关）→ 只验"窗宽窗位 → 灰度"映射；
     * {@code mode=1}：沿 i 交替 0/100HU → 采样点落在两体素之间，线性插值应得 50HU、近邻会得 0 或 100HU。</p>
     *
     * @param expectedHu 采样点处期望的 HU（线性插值前提下的理论值）
     */
    private static boolean runSyntheticCase(String title, WindowLevel windowLevel, int mode,
                                            double expectedHu, Path png) throws Exception {
        vtkImageData volume = syntheticVolume(mode);
        vtkRenderWindow renderWindow = new vtkRenderWindow();
        vtkRenderWindowInteractor interactor = new vtkRenderWindowInteractor();
        vtkRenderer renderer = new vtkRenderer();
        vtkPlane plane = new vtkPlane();
        vtkImageResliceMapper mapper = new vtkImageResliceMapper();
        vtkImageSlice slice = new vtkImageSlice();
        try {
            // 交替卷沿层方向(k)变化：平面取 z=15.5（位于两层之间），面内取值恒定，
            // 因此中心像素的读取位置不敏感，可干净地区分"线性插值(50HU)"与"近邻(0/100HU)"。
            double sample = mode == 2 ? 15.0 : 15.5;
            double planeZ = mode == 1 ? 15.5 : 15.0;
            plane.SetOrigin(sample, sample, planeZ);
            plane.SetNormal(0.0, 0.0, 1.0);
            mapper.SetInputData(volume);
            mapper.SetSlicePlane(plane);
            mapper.SetSliceFacesCamera(0);
            mapper.SetSliceAtFocalPoint(0);
            mapper.SetJumpToNearestSlice(0);
            mapper.SetSeparateWindowLevelOperation(1);
            mapper.SetAutoAdjustImageQuality(0);
            mapper.SetImageSampleFactor(2);
            vtkImageInterpolator interpolator = new vtkImageInterpolator();
            interpolator.SetInterpolationModeToLinear();
            mapper.SetInterpolator(interpolator);
            slice.SetMapper(mapper);
            slice.GetProperty().SetColorWindow(windowLevel.getWidth());
            slice.GetProperty().SetColorLevel(windowLevel.getCenter());
            renderer.AddActor(slice);
            renderer.SetBackground(0.0, 0.0, 0.0);

            renderWindow.SetOffScreenRendering(1);
            renderWindow.SetSize(SIZE, SIZE);
            renderWindow.AddRenderer(renderer);
            interactor.SetRenderWindow(renderWindow);
            configureCamera(renderer.GetActiveCamera(), 200.0, new double[]{sample, sample, planeZ},
                    new double[]{0, 0, 1}, new double[]{0, -1, 0});
            renderer.ResetCameraClippingRange();
            renderWindow.Render();

                VtkImageExporter.exportPng(renderWindow, png);
                BufferedImage image = ImageIO.read(new File(png.toString()));
            int expected = toGray(expectedHu, windowLevel);
            int actual = grayAt(image, image.getWidth() / 2, image.getHeight() / 2);
            boolean pass = Math.abs(expected - actual) <= GRAY_TOLERANCE;
            LOG.info("{}: 理论HU={} 理论灰度={} 实测灰度={} (反算HU={}) -> {}", title,
                    round(expectedHu), expected, actual, round(grayToHu(actual, windowLevel)),
                    pass ? "PASS" : "FAIL");
            return pass;
        } finally {
            slice.Delete();
            mapper.Delete();
            plane.Delete();
            renderWindow.Delete();
            interactor.Delete();
            renderer.Delete();
            volume.Delete();
        }
    }

    /**
     * 合成体数据：mode=2 常量 100HU；mode=0 常量 0HU；mode=1 沿 i 交替 0/100HU。
     */
    private static vtkImageData syntheticVolume(int mode) {
        int size = 32;
        vtkImageData image = new vtkImageData();
        image.SetDimensions(size, size, size);
        image.SetSpacing(1.0, 1.0, 1.0);
        image.SetOrigin(0.0, 0.0, 0.0);
        vtkShortArray scalars = new vtkShortArray();
        scalars.SetNumberOfComponents(1);
        scalars.SetNumberOfTuples(size * size * size);
        for (int k = 0; k < size; k++) {
            for (int j = 0; j < size; j++) {
                for (int i = 0; i < size; i++) {
                    double value = mode == 1 ? (k % 2 == 0 ? 100.0 : 0.0) : (mode == 2 ? 100.0 : 0.0);
                    scalars.SetTuple1((k * size + j) * size + i, value);
                }
            }
        }
        image.GetPointData().SetScalars(scalars);
        return image;
    }

    /**
     * 渲染一个用例并校验屏幕中心像素与理论 HU 一致。
     */
    private static boolean runCase(String title, BuiltVolume built, VolumeGeometry geometry,
                                   WindowLevel windowLevel, double[] planeOrigin, double[] planeNormal,
                                   double[] focal, double[] viewDir, double[] up, Path png) throws Exception {
        vtkRenderWindow renderWindow = new vtkRenderWindow();
        vtkRenderWindowInteractor interactor = new vtkRenderWindowInteractor();
        vtkRenderer renderer = new vtkRenderer();
        vtkPlane plane = new vtkPlane();
        vtkImageResliceMapper mapper = new vtkImageResliceMapper();
        vtkImageSlice slice = new vtkImageSlice();
        try {
            plane.SetOrigin(planeOrigin[0], planeOrigin[1], planeOrigin[2]);
            plane.SetNormal(planeNormal[0], planeNormal[1], planeNormal[2]);
            mapper.SetInputData(built.getImage());
            mapper.SetSlicePlane(plane);
            mapper.SetSliceFacesCamera(0);
            mapper.SetSliceAtFocalPoint(0);
            mapper.SetJumpToNearestSlice(0);
            mapper.SetSeparateWindowLevelOperation(1);
            mapper.SetAutoAdjustImageQuality(0);
            mapper.SetImageSampleFactor(2);
            mapper.SetResampleToScreenPixels(1);
            vtkImageInterpolator interpolator = new vtkImageInterpolator();
            interpolator.SetInterpolationModeToLinear();
            mapper.SetInterpolator(interpolator);
            slice.SetMapper(mapper);
            slice.GetProperty().SetColorWindow(windowLevel.getWidth());
            slice.GetProperty().SetColorLevel(windowLevel.getCenter());
            slice.GetProperty().SetInterpolationTypeToLinear();
            renderer.AddActor(slice);
            renderer.SetBackground(0.0, 0.0, 0.0);

            renderWindow.SetOffScreenRendering(1);
            renderWindow.SetSize(SIZE, SIZE);
            renderWindow.AddRenderer(renderer);
            interactor.SetRenderWindow(renderWindow);
            configureCamera(renderer.GetActiveCamera(), diagonal(geometry) * 2.0, focal, viewDir, up);
            renderer.ResetCameraClippingRange();
            renderWindow.Render();

            double[] expectedPoint = axisPlaneIntersection(focal, viewDir, planeOrigin, planeNormal);
            double expectedHu = sampleTrilinear(geometry, built.getImage().GetPointData().GetScalars(), expectedPoint);
            VtkImageExporter.exportPng(renderWindow, png);
            BufferedImage image = ImageIO.read(new File(png.toString()));
            int expectedGray = toGray(expectedHu, windowLevel);
            int actualGray = grayAt(image, image.getWidth() / 2, image.getHeight() / 2);
            int blockError = maxBlockError(renderer.GetActiveCamera(), geometry, windowLevel,
                    built.getImage().GetPointData().GetScalars(), planeOrigin, planeNormal, image);
            boolean pass = blockError <= GRAY_TOLERANCE;
            LOG.info("{} (2x2块最大偏差={}): 交点={} 理论HU={} 理论灰度={} 实测灰度={}(反算HU={}) -> {}",
                    title, blockError, format(expectedPoint), round(expectedHu), expectedGray, actualGray,
                    round(grayToHu(actualGray, windowLevel)), pass ? "PASS" : "FAIL");
            return pass;
        } finally {
            slice.Delete();
            mapper.Delete();
            plane.Delete();
            renderWindow.Delete();
            interactor.Delete();
            renderer.Delete();
        }
    }

    private static void configureCamera(vtkCamera camera, double distance, double[] focal,
                                        double[] viewDir, double[] up) {
        camera.SetFocalPoint(focal[0], focal[1], focal[2]);
        camera.SetPosition(
                focal[0] - viewDir[0] * distance,
                focal[1] - viewDir[1] * distance,
                focal[2] - viewDir[2] * distance);
        camera.SetViewUp(up[0], up[1], up[2]);
        camera.ParallelProjectionOn();
        // 校验取景缩到 40mm 视野：把"显示中心像素"与"焦点/交点"的偏差压到远小于 1 体素
        camera.SetParallelScale(20.0);
    }

    /**
     * 视线（过 focal、方向 viewDir）与平面（过 planeOrigin、法向 planeNormal）的交点。
     */
    private static double[] axisPlaneIntersection(double[] focal, double[] viewDir,
                                                  double[] planeOrigin, double[] planeNormal) {
        double denominator = dot(viewDir, planeNormal);
        if (Math.abs(denominator) < 1e-9) {
            return focal;
        }
        double t = dot(subtract(planeOrigin, focal), planeNormal) / denominator;
        return offset(focal, viewDir, t);
    }

    private static int grayAt(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >> 16) & 0xFF;
    }

    /**
     * 严格的显示一致性校验：对中心 2×2 像素，逐个用显示坐标反算世界点、再做体数据三线性采样，
     * 与渲染灰度比较，返回最大偏差（灰度级）。
     *
     * <p>逐像素比对可避开"中心像素几何半像素歧义"，从而真正验证平面位置 + 重采样 + 窗宽窗位映射。</p>
     */
    private static int maxBlockError(vtkCamera camera, VolumeGeometry geometry, WindowLevel windowLevel,
                                     vtkDataArray scalars, double[] planeOrigin, double[] planeNormal,
                                     BufferedImage image) {
        double[] viewDir = camera.GetDirectionOfProjection();
        double[] viewUp = camera.GetViewUp();
        double[] up = normalize(subtract(viewUp, multiply(viewDir, dot(viewUp, viewDir))));
        double[] right = cross(viewDir, up);
        double[] focal = camera.GetFocalPoint();
        int width = image.getWidth();
        int height = image.getHeight();
        double millimetersPerPixel = 2.0 * camera.GetParallelScale() / height;

        int maxError = 0;
        for (int dx = -1; dx <= 0; dx++) {
            for (int dy = -1; dy <= 0; dy++) {
                int pixelX = width / 2 + dx;
                int pixelY = height / 2 + dy;
                int vtkY = height - 1 - pixelY;
                double offsetX = (pixelX + 0.5 - width / 2.0) * millimetersPerPixel;
                double offsetY = (vtkY + 0.5 - height / 2.0) * millimetersPerPixel;
                double[] onFocalPlane = add(focal, add(multiply(right, offsetX), multiply(up, offsetY)));
                // 像素视线（沿视线方向）与实际显示平面求交：偏移平面不在焦点平面上
                double[] world = axisPlaneIntersection(onFocalPlane, viewDir, planeOrigin, planeNormal);
                int expected = toGray(sampleTrilinear(geometry, scalars, world), windowLevel);
                int actual = grayAt(image, pixelX, pixelY);
                maxError = Math.max(maxError, Math.abs(expected - actual));
            }
        }
        return maxError;
    }

    /**
     * 窗宽窗位映射：灰度 = clamp((HU − (窗位 − 窗宽/2)) / 窗宽, 0, 1)。
     */
    private static int toGray(double hu, WindowLevel windowLevel) {
        double lower = windowLevel.getCenter() - windowLevel.getWidth() / 2.0;
        double factor = (hu - lower) / windowLevel.getWidth();
        return (int) Math.round(Math.min(1.0, Math.max(0.0, factor)) * 255.0);
    }

    /**
     * 期望点邻域（沿两方向各 ±1 体素）的 HU 采样，用于判断"半像素偏差 × 局部梯度"能否解释实测差值。
     */
    private static String neighborhood(VolumeGeometry geometry, BuiltVolume built, double[] point) {
        vtkDataArray scalars = built.getImage().GetPointData().GetScalars();
        double[] index = geometry.toIndex(point);
        double[] offsets = {0.0, 1.0, -1.0};
        StringBuilder builder = new StringBuilder();
        for (int axis = 0; axis < 2; axis++) {
            for (double delta : offsets) {
                double[] shifted = new double[]{index[0], index[1], index[2]};
                shifted[axis] += delta;
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(round(sampleTrilinearIndex(geometry, scalars, shifted)));
            }
            builder.append(" | ");
        }
        return builder.toString();
    }

    /**
     * 按小数索引三线性取样（用于邻域诊断）。
     */
    private static double sampleTrilinearIndex(VolumeGeometry geometry, vtkDataArray scalars, double[] index) {
        double[] world = geometry.toWorld(index[0], index[1], index[2]);
        return sampleTrilinear(geometry, scalars, world);
    }

    /**
     * 灰度反算 HU（与 {@link #toGray} 互逆），用于把实测灰度换算回 HU 便于判断偏差量级。
     */
    private static double grayToHu(int gray, WindowLevel windowLevel) {
        double lower = windowLevel.getCenter() - windowLevel.getWidth() / 2.0;
        return lower + gray / 255.0 * windowLevel.getWidth();
    }

    /**
     * 体数据三线性插值取样（体素中心对齐）。
     */
    private static double sampleTrilinear(VolumeGeometry geometry, vtkDataArray scalars, double[] world) {
        int columns = geometry.getColumns();
        int rows = geometry.getRows();
        int slices = geometry.getSlices();
        double[] index = geometry.toIndex(world);
        double cx = clamp(index[0], columns);
        double cy = clamp(index[1], rows);
        double cz = clamp(index[2], slices);
        int i0 = (int) Math.floor(cx);
        int j0 = (int) Math.floor(cy);
        int k0 = (int) Math.floor(cz);
        int i1 = Math.min(i0 + 1, columns - 1);
        int j1 = Math.min(j0 + 1, rows - 1);
        int k1 = Math.min(k0 + 1, slices - 1);
        double fx = cx - i0;
        double fy = cy - j0;
        double fz = cz - k0;

        double row00 = lerp(at(scalars, columns, rows, i0, j0, k0), at(scalars, columns, rows, i1, j0, k0), fx);
        double row10 = lerp(at(scalars, columns, rows, i0, j1, k0), at(scalars, columns, rows, i1, j1, k0), fx);
        double row01 = lerp(at(scalars, columns, rows, i0, j0, k1), at(scalars, columns, rows, i1, j0, k1), fx);
        double row11 = lerp(at(scalars, columns, rows, i0, j1, k1), at(scalars, columns, rows, i1, j1, k1), fx);
        double plane0 = lerp(row00, row10, fy);
        double plane1 = lerp(row01, row11, fy);
        return lerp(plane0, plane1, fz);
    }

    private static double at(vtkDataArray scalars, int columns, int rows, int i, int j, int k) {
        long offset = ((long) k * rows + j) * columns + i;
        return scalars.GetTuple1(offset);
    }

    private static double lerp(double from, double to, double fraction) {
        return from + (to - from) * fraction;
    }

    private static double clamp(double value, int size) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, size - 1);
    }

    private static double diagonal(VolumeGeometry geometry) {
        double[] spacing = geometry.getSpacing();
        return Math.sqrt(Math.pow(geometry.getColumns() * spacing[0], 2)
                + Math.pow(geometry.getRows() * spacing[1], 2)
                + Math.pow(geometry.getSlices() * spacing[2], 2));
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

    private static String format(double[] vector) {
        return String.format("(%.2f, %.2f, %.2f)", vector[0], vector[1], vector[2]);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double[] multiply(double[] vector, double factor) {
        return new double[]{vector[0] * factor, vector[1] * factor, vector[2] * factor};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static double[] subtract(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] negate(double[] vector) {
        return new double[]{-vector[0], -vector[1], -vector[2]};
    }

    private static double[] offset(double[] origin, double[] direction, double distance) {
        return new double[]{
                origin[0] + direction[0] * distance,
                origin[1] + direction[1] * distance,
                origin[2] + direction[2] * distance};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] normalize(double[] vector) {
        double length = Math.sqrt(dot(vector, vector));
        return new double[]{vector[0] / length, vector[1] / length, vector[2] / length};
    }
}
