package com.zlyd.mpr.util;

import vtk.vtkNativeLibrary;

/**
 * 按依赖顺序加载 VTK native 库。
 *
 * <p>不能用 {@code LoadAllNativeLibraries()}：它按枚举顺序（高阶模块在前）加载，
 * 而部分 JNI 库依赖其他 JNI 库（如 {@code vtkInteractionWidgetsJava} 依赖
 * {@code vtkRenderingContext2DJava}），会导致 "procedure not found" 而加载失败。
 * 这里按"底层 → 上层"的顺序显式加载。</p>
 *
 * <p>运行前需保证 {@code PATH} 中包含：
 * JDK\bin、VTK 核心 DLL 目录、VTK JNI 库目录。</p>
 */
public final class VtkNativeLoader {

    private static final vtkNativeLibrary[] LOAD_ORDER = {
            vtkNativeLibrary.vtkCommonCore,
            vtkNativeLibrary.vtkCommonMath,
            vtkNativeLibrary.vtkCommonMisc,
            vtkNativeLibrary.vtkCommonSystem,
            vtkNativeLibrary.vtkCommonTransforms,
            vtkNativeLibrary.vtkCommonDataModel,
            vtkNativeLibrary.vtkCommonExecutionModel,
            vtkNativeLibrary.vtkCommonColor,
            vtkNativeLibrary.vtkCommonComputationalGeometry,
            vtkNativeLibrary.vtkFiltersCore,
            vtkNativeLibrary.vtkFiltersSources,
            vtkNativeLibrary.vtkFiltersGeneral,
            vtkNativeLibrary.vtkFiltersGeometry,
            vtkNativeLibrary.vtkImagingCore,
            vtkNativeLibrary.vtkImagingSources,
            vtkNativeLibrary.vtkRenderingCore,
            vtkNativeLibrary.vtkRenderingFreeType,
            vtkNativeLibrary.vtkRenderingContext2D,
            vtkNativeLibrary.vtkRenderingAnnotation,
            vtkNativeLibrary.vtkRenderingOpenGL2,
            vtkNativeLibrary.vtkRenderingImage,
            vtkNativeLibrary.vtkRenderingVolume,
            vtkNativeLibrary.vtkInteractionStyle,
            vtkNativeLibrary.vtkInteractionWidgets,
            vtkNativeLibrary.vtkRenderingUI,
            vtkNativeLibrary.vtkIOCore,
            vtkNativeLibrary.vtkIOImage,
            vtkNativeLibrary.vtkIOLegacy
    };

    private VtkNativeLoader() {
    }

    /**
     * 加载 MPR 所需的全部 native 库；任一失败即抛出 {@link UnsatisfiedLinkError}。
     */
    public static void load() {
        for (vtkNativeLibrary library : LOAD_ORDER) {
            library.LoadLibrary();
        }
    }
}
