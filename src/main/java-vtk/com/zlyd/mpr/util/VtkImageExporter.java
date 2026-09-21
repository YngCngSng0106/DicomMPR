package com.zlyd.mpr.util;

import java.io.IOException;
import java.nio.file.Path;

import vtk.vtkPNGWriter;
import vtk.vtkRenderWindow;
import vtk.vtkWindowToImageFilter;

/**
 * 把 VTK 渲染窗口导出为 PNG（H1）。
 */
public final class VtkImageExporter {

    private VtkImageExporter() {
    }

    /**
     * 导出当前渲染窗口。
     *
     * @param renderWindow 渲染窗口
     * @param file 目标文件（.png）
     * @throws IOException 写入失败
     */
    public static void exportPng(vtkRenderWindow renderWindow, Path file) throws IOException {
        vtkWindowToImageFilter filter = new vtkWindowToImageFilter();
        vtkPNGWriter writer = new vtkPNGWriter();
        try {
            // 双缓冲窗口下默认读"前缓冲"可能拿到旧帧；强制重渲染并读"后缓冲"更可靠
            renderWindow.Render();
            filter.SetInput(renderWindow);
            filter.SetInputBufferTypeToRGB();
            filter.ReadFrontBufferOff();
            filter.ShouldRerenderOn();
            filter.Modified();
            filter.Update();

            writer.SetFileName(file.toString());
            writer.SetInputConnection(filter.GetOutputPort());
            writer.Write();
        } finally {
            writer.Delete();
            filter.Delete();
        }
    }
}
