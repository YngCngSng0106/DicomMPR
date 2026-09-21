package com.zlyd.mpr.m0;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zlyd.mpr.util.VtkNativeLoader;

import vtk.vtkActor;
import vtk.vtkCanvas;
import vtk.vtkImageData;
import vtk.vtkImageReslice;
import vtk.vtkPolyDataMapper;
import vtk.vtkRenderer;
import vtk.vtkSphereSource;

/**
 * M0 环境验证：加载 VTK native 库并显示一个球体。
 *
 * <p>运行前需保证 {@code PATH} 与 {@code -Djava.library.path} 覆盖
 * JDK\bin、VTK 核心 DLL 目录、VTK JNI 库目录。</p>
 */
public final class VtkSmokeTest {

    private static final Logger LOG = LogManager.getLogger(VtkSmokeTest.class);
    private static final int CANVAS_WIDTH = 640;
    private static final int CANVAS_HEIGHT = 480;
    private static final String CHECK_ONLY_ARG = "--check-only";

    static {
        VtkNativeLoader.load();
    }

    private VtkSmokeTest() {
    }

    public static void main(String[] args) {
        LOG.info("VTK Java 冒烟测试开始");
        checkImagingClasses();
        if (args.length > 0 && CHECK_ONLY_ARG.equals(args[0])) {
            LOG.info("仅校验模式：native 库与 Imaging 类均可用，未打开窗口");
            return;
        }
        SwingUtilities.invokeLater(VtkSmokeTest::showSphereWindow);
    }

    private static void checkImagingClasses() {
        vtkImageData image = new vtkImageData();
        image.SetDimensions(4, 4, 4);
        image.SetSpacing(1.0, 1.0, 1.0);
        vtkImageReslice reslice = new vtkImageReslice();
        reslice.SetOutputDimensionality(2);
        LOG.info("Imaging 类可用: vtkImageData, vtkImageReslice");
    }

    private static void showSphereWindow() {
        vtkCanvas canvas = new vtkCanvas();
        vtkRenderer renderer = canvas.GetRenderer();
        renderer.AddActor(createActor());
        renderer.SetBackground(0.1, 0.2, 0.3);
        renderer.ResetCamera();

        JFrame frame = new JFrame("VTK Java 冒烟测试 (M0)");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(canvas, BorderLayout.CENTER);
        frame.setPreferredSize(new Dimension(CANVAS_WIDTH + 40, CANVAS_HEIGHT + 40));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        canvas.Render();
        LOG.info("渲染窗口已显示");
    }

    private static vtkActor createActor() {
        vtkSphereSource sphere = new vtkSphereSource();
        sphere.SetRadius(50.0);
        sphere.SetThetaResolution(60);
        sphere.SetPhiResolution(60);
        sphere.Update();

        vtkPolyDataMapper mapper = new vtkPolyDataMapper();
        mapper.SetInputConnection(sphere.GetOutputPort());

        vtkActor actor = new vtkActor();
        actor.SetMapper(mapper);
        actor.GetProperty().SetColor(0.95, 0.45, 0.35);
        return actor;
    }
}
