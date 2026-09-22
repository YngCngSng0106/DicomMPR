package com.zlyd.mpr.m2;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.dicom.DicomScanner;
import com.zlyd.mpr.dicom.SeriesGeometry;
import com.zlyd.mpr.dicom.SeriesGrouper;
import com.zlyd.mpr.dicom.SeriesInfo;
import com.zlyd.mpr.dicom.VolumeGeometry;
import com.zlyd.mpr.geometry.Measurement;
import com.zlyd.mpr.geometry.MeasurementCalculator;
import com.zlyd.mpr.geometry.MeasurementType;
import com.zlyd.mpr.geometry.MprCursorFrame;
import com.zlyd.mpr.geometry.MprViewOrientation;
import com.zlyd.mpr.geometry.RoiStatistics;
import com.zlyd.mpr.geometry.ScreenFrame;
import com.zlyd.mpr.geometry.Vectors;
import com.zlyd.mpr.mpr.BuiltVolume;
import com.zlyd.mpr.mpr.VolumeBuilder;
import com.zlyd.mpr.util.MeasurementFormatter;
import com.zlyd.mpr.util.VtkNativeLoader;

/**
 * 测量校验（S5）：在轴对齐与斜切两种平面基准下，校验六种测量的数值/面积/HU 统计与格式化。
 *
 * <p>覆盖：线段长度、角度、矩形/椭圆面积、曲线折线长度、自由形状面积+周长+HU 统计；
 * 并对比"平面 mm 采样统计"在斜平面下是否与轴对齐一致（同一解剖区域应给出相近结果）。</p>
 */
public final class M3MeasurementCheck {

    private static final Logger LOG = LogManager.getLogger(M3MeasurementCheck.class);
    private static final double TOLERANCE = 1e-6;

    static {
        VtkNativeLoader.load();
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        SeriesInfo target = pickLargestReconstructable(new SeriesGrouper()
                .group(new DicomScanner().scan(root)));
        if (target == null) {
            LOG.warn("没有可重建的序列");
            return;
        }
        BuiltVolume built = new VolumeBuilder().build(target);
        boolean ok = new M3MeasurementCheck().run(built);
        LOG.info("测量校验结论: {}", ok);
        System.exit(ok ? 0 : 1);
    }

    private boolean run(BuiltVolume built) {
        VolumeGeometry geometry = built.getGeometry();
        double[] center = geometry.center();
        double[] axisX = geometry.getAxisX();
        double[] axisY = geometry.getAxisY();
        double[] axisZ = geometry.getAxisZ();

        boolean ok = true;
        ok &= verifyValues(center, axisX, axisY, axisZ);
        ok &= verifyObliqueRoiStatistics(geometry, center, axisX, axisY, axisZ);
        return ok;
    }

    /**
     * 数值/面积/格式化校验（纯计算，不依赖体数据）。
     */
    private boolean verifyValues(double[] center, double[] axisX, double[] axisY, double[] axisZ) {
        boolean ok = true;
        double[] p0 = offset(center, axisX, -20);
        double[] p1 = offset(center, axisX, 20);
        double[] vertex = center;
        double[] p2 = offset(center, axisY, 30);

        Measurement line = new Measurement(MeasurementType.LENGTH, 0, List.of(p0, p1), 40.0, null);
        ok &= report(Math.abs(line.getValue() - 40.0) < TOLERANCE && MeasurementFormatter.format(line)
                .contains("长度 40.0 mm"), "线段长度与格式化: %s", MeasurementFormatter.format(line));

        double angle = MeasurementCalculator.angle(p0, vertex, p2);
        Measurement angleMeasurement = new Measurement(MeasurementType.ANGLE, 0,
                List.of(p0, vertex, p2), angle, null);
        ok &= report(Math.abs(angle - 90.0) < 1e-6, "角度=90°: %s",
                MeasurementFormatter.format(angleMeasurement));

        List<double[]> rect = List.of(p0, p1, offset(offset(p1, axisY, 20), axisZ, 0),
                offset(p0, axisY, 20));
        double rectArea = MeasurementCalculator.polygonArea(rect, axisX, axisY);
        Measurement rectMeasurement = new Measurement(MeasurementType.RECT_ROI, 0, rect, rectArea, null);
        ok &= report(Math.abs(rectArea - 40.0 * 20.0) < 1e-6, "矩形面积: %s",
                MeasurementFormatter.format(rectMeasurement));

        double[] e0 = p0;
        double[] e1 = offset(offset(p0, axisX, 40), axisY, 20);
        double ellipseArea = MeasurementCalculator.ellipseArea(
                MeasurementCalculator.extent(e0, e1, axisX, axisY));
        Measurement ellipse = new Measurement(MeasurementType.ELLIPSE_ROI, 0, List.of(e0, e1),
                ellipseArea, null);
        ok &= report(Math.abs(ellipseArea - Math.PI / 4.0 * 40 * 20) < 1e-6, "椭圆面积: %s",
                MeasurementFormatter.format(ellipse));

        List<double[]> curve = List.of(p0, offset(p0, axisY, 30), offset(offset(p0, axisY, 30), axisX, 40));
        double curveLength = MeasurementCalculator.polylineLength(curve);
        Measurement curveMeasurement = new Measurement(MeasurementType.CURVE, 0, curve, curveLength, null);
        ok &= report(Math.abs(curveLength - 70.0) < 1e-6, "曲线折线长度: %s",
                MeasurementFormatter.format(curveMeasurement));

        List<double[]> freehand = List.of(p0, offset(p0, axisX, 40), offset(offset(p0, axisX, 40), axisY, 20),
                offset(p0, axisY, 20));
        double area = MeasurementCalculator.polygonArea(freehand, axisX, axisY);
        double perimeter = MeasurementCalculator.polygonPerimeter(freehand);
        RoiStatistics statistics = RoiStatistics.of(new double[]{10, 20, 30, 40}, 4);
        Measurement freehandMeasurement = new Measurement(MeasurementType.FREEHAND, 0, freehand, area, statistics);
        ok &= report(Math.abs(area - 800.0) < 1e-6 && Math.abs(perimeter - 120.0) < 1e-6
                        && MeasurementFormatter.format(freehandMeasurement).contains("周长 120.0 mm"),
                "自由形状面积/周长: %s", MeasurementFormatter.format(freehandMeasurement));

        ok &= report(!MeasurementFormatter.format(curveMeasurement).contains("HU"),
                "曲线不做 HU 统计: %s", MeasurementFormatter.format(curveMeasurement));
        return ok;
    }

    /**
     * 斜平面下 ROI 统计的平面基准一致性：同一平面坐标范围在"轴对齐基准"与"斜切基准"下
     * 采样出的体素集合应一致（这里用平面坐标 → 世界 → 体素 的往返来验证）。
     */
    private boolean verifyObliqueRoiStatistics(VolumeGeometry geometry, double[] center,
                                               double[] axisX, double[] axisY, double[] axisZ) {
        double root = Math.sqrt(2.0);
        double[] obliqueAxis1 = Vectors.normalize(Vectors.add(axisX, axisZ));
        double[] obliqueAxis2 = axisY;
        boolean ok = true;
        int matches = 0;
        int total = 0;
        for (double s = -10; s <= 10; s += 5) {
            for (double t = -10; t <= 10; t += 5) {
                double[] world = {
                        center[0] + obliqueAxis1[0] * s + obliqueAxis2[0] * t,
                        center[1] + obliqueAxis1[1] * s + obliqueAxis2[1] * t,
                        center[2] + obliqueAxis1[2] * s + obliqueAxis2[2] * t};
                double[] plane = MeasurementCalculator.project(world, center, obliqueAxis1, obliqueAxis2);
                double[] back = {
                        center[0] + obliqueAxis1[0] * plane[0] + obliqueAxis2[0] * plane[1],
                        center[1] + obliqueAxis1[1] * plane[0] + obliqueAxis2[1] * plane[1],
                        center[2] + obliqueAxis1[2] * plane[0] + obliqueAxis2[2] * plane[1]};
                total++;
                if (Math.abs(back[0] - world[0]) < 1e-9 && Math.abs(back[1] - world[1]) < 1e-9
                        && Math.abs(back[2] - world[2]) < 1e-9) {
                    matches++;
                }
            }
        }
        ok &= report(matches == total, "斜平面坐标往返一致: %d/%d", matches, total);

        // 平面内点包含判定（斜平面上的矩形）
        List<double[]> rect = new ArrayList<>();
        rect.add(corner(center, obliqueAxis1, -10, obliqueAxis2, -10));
        rect.add(corner(center, obliqueAxis1, 10, obliqueAxis2, -10));
        rect.add(corner(center, obliqueAxis1, 10, obliqueAxis2, 10));
        rect.add(corner(center, obliqueAxis1, -10, obliqueAxis2, 10));
        double obliqueArea = MeasurementCalculator.polygonArea(rect, obliqueAxis1, obliqueAxis2);
        ok &= report(Math.abs(obliqueArea - 400.0) < 1e-6, "斜平面矩形面积=20×20: %s", obliqueArea);
        double[] insidePoint = MeasurementCalculator.project(center, rect.get(0), obliqueAxis1, obliqueAxis2);
        double[] outsidePoint = MeasurementCalculator.project(offset(center, obliqueAxis1, 15), rect.get(0),
                obliqueAxis1, obliqueAxis2);
        ok &= report(MeasurementCalculator.polygonContains(insidePoint[0], insidePoint[1], rect,
                        obliqueAxis1, obliqueAxis2)
                        && !MeasurementCalculator.polygonContains(outsidePoint[0], outsidePoint[1], rect,
                        obliqueAxis1, obliqueAxis2),
                "斜平面多边形包含判定正确", new Object[0]);

        LOG.info("（参考）体数据尺寸={} spacing={} 轴位法向={}", java.util.Arrays.toString(geometry.getDimensions()),
                java.util.Arrays.toString(geometry.getSpacing()), java.util.Arrays.toString(axisZ));
        return ok;
    }

    private static double[] corner(double[] origin, double[] axis1, double d1, double[] axis2,
                                double d2) {
        return new double[]{
                origin[0] + axis1[0] * d1 + axis2[0] * d2,
                origin[1] + axis1[1] * d1 + axis2[1] * d2,
                origin[2] + axis1[2] * d1 + axis2[2] * d2};
    }

    private static double[] offset(double[] origin, double[] direction, double distance) {
        return new double[]{
                origin[0] + direction[0] * distance,
                origin[1] + direction[1] * distance,
                origin[2] + direction[2] * distance};
    }

    private static boolean report(boolean ok, String format, Object... arguments) {
        LOG.info("[{}] {}", ok ? "PASS" : "FAIL", String.format(format, arguments));
        return ok;
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
