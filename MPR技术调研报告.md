# 医学影像 MPR（多平面重建）技术调研报告

> 调研范围：MPR 的含义与原理；桌面端与 Web 端成熟技术栈；选型建议与工程实践要点。
> 版本与数据基于 2026 年公开资料（库版本会持续演进，落地前请以官方最新发布为准）。

---

## 一、MPR 的含义

**MPR**（Multi-Planar Reconstruction / Reformation，多平面重建 / 多层面重建）是医学影像后处理的核心技术之一。

- **定义**：以一个三维体数据（volumetric data）为输入，沿着**任意平面**重新采样（resampling），输出该平面上的二维切片。它不依赖重新扫描，而是对已采集的体数据进行几何重组。
- **常见模态**：CT、MRI、PET-CT、CBCT（口腔锥形束 CT）等。
- **为什么叫"重建"**：CT 通常以轴位（axial）方向直接采集，冠状面（coronal）与矢状面（sagittal）是后处理"重新生成"的，因此称"重建"。
- **核心价值**：医生无需重复扫描即可从多个视角观察解剖结构与病灶；可展示腔性结构横截面、评价血管受侵、真实反映器官间位置关系。

### 三个正交视图（三视图）

| 视图 | 中文 | 切分方式 | 观察方向 | 典型用途 |
| --- | --- | --- | --- | --- |
| Axial | 横断面 / 轴位 | 水平切开人体 | 从下往上看 | 脑、胸、腹部 CT |
| Coronal | 冠状面 | 从正面切开 | 面对病人 | 鼻窦、脊柱、肺部上下关系 |
| Sagittal | 矢状面 | 左右切开 | 从侧面看 | 脊柱、膝关节、脑中线结构 |

三视图共享同一个空间坐标点 `(x, y, z)`，任一视图上的操作会同步影响另外两个视图。三张图上通常绘制"十字架"（crosshair），十字架的两条线分别是另外两个平面的投影，中心点是三者交汇处。

**MPR 与 CPR 的区别**：CPR（Curved Planar Reformation，曲面重建）本质上是一种"曲面 MPR"，沿管状结构中心线重新采样并展开显示全长，常用于血管、结肠、牙弓等。

---

## 二、MPR 的原理

### 2.1 数据模型：体素与三维标量场

CT/MRI 原始数据本质是一个**三维标量场**，可理解为 3D 数组 `Model[M][N][D]`，每个体素（voxel）保存灰度/密度值。MPR 就是这个三维数组在任意平面上的采样操作。

### 2.2 坐标系：三种坐标系的转换是 MPR 的核心

医学影像涉及三类坐标系：

1. **解剖坐标系（患者坐标系，单位 mm）**
   - **LPS**（Left, Posterior, Superior）：DICOM 标准使用，方向为"右→左、前→后、下→上"。
   - **RAS**（Right, Anterior, Superior）：与 LPS 前两轴符号相反，NIfTI 与 3D Slicer 内部使用；Slicer 内部统一用 RAS，读写文件时按 LPS 处理并翻转符号。
2. **图像坐标系（IJK，单位 pixel/voxel）**
   - `i` 向右、`j` 向下、`k` 向后（沿层叠方向）。
3. **世界/体数据坐标系**：渲染管线中使用的统一空间。

**关键 DICOM 标签**：

| 标签 | 名称 | 作用 |
| --- | --- | --- |
| (0020,0032) | Image Position Patient | 该切片左上角体素在患者坐标系（LPS）中的原点 |
| (0020,0037) | Image Orientation Patient | 行、列方向余弦（各 3 个分量），定义切片平面朝向 |
| (0028,0030) | Pixel Spacing | 行/列方向像素间距（mm） |
| (0018,0088) | Spacing Between Slices | 相邻切片物理间距（mm） |
| (0018,0050) | Slice Thickness | 切片厚度（mm） |
| (0028,1052)/(0028,1053) | Rescale Intercept / Slope | 像素值 → 真实物理值（如 CT 的 HU）的线性映射 |

由 `ImageOrientationPatient` + `ImagePositionPatient` 可构建 **IJK → LPS 的仿射变换（4×4 齐次矩阵）**：

```
         ┌                          ┐
x_patient│ A(3x3) 空间方向/尺度   t │ i
y     =  │  [行向量 | 列向量 | 法向量] │ j
z        │  0  0  0                1 │ k
1        └                          ┘ 1
```

其中 4×4 矩阵 = 3×3 线性变换矩阵（方向 + 尺度）+ 1 列平移向量。系统间转换只是前两轴符号翻转（LPS ↔ RAS）。

### 2.3 MPR 数学本质：重采样 + 变换矩阵

MPR 生成过程可抽象为：

1. 目标切面用一个**四维/4×4 变换矩阵**描述（即"摄像机/切片坐标系"）：
   - 第 1 列 = X 轴方向余弦（与世界坐标 X 轴夹角 cos）
   - 第 2 列 = Y 轴方向余弦
   - 第 3 列 = Z 轴（切面法线）方向余弦，通常为 X、Y 叉积（保证正交）
   - 第 4 列 = 切面原点在世界坐标系中的位置
2. 对该矩阵所定义的网格逐像素，将其**反变换**到体数据坐标 (i, j, k)；
3. 在体数据上做**插值**得到灰度值；
4. 经过窗宽窗位映射后渲染为二维图像。

伪代码：

```
for each output pixel (u, v):
    world   = T * (u, v, 0, 1)      # 切面坐标 → 世界坐标
    index   = inv(IJKtoWorld) * world  # 世界坐标 → 体素坐标
    value   = trilinear(volume, index)
    screen[u,v] = windowLevel(value)
```

三个正交面是同一公共矩阵的三种取值：
- 横断面 = 公共矩阵；
- 冠状面 = 横断面绕 Y 轴旋转 90°；
- 矢状面 = 横断面绕 X 轴旋转 90°。

只需维护一个"公共矩阵"即可实现三视图联动。

### 2.4 插值算法（决定图像质量）

当重采样点不落在体素中心时需插值：

| 方法 | 质量 | 速度 | 说明 |
| --- | --- | --- | --- |
| 最近邻（Nearest-Neighbor） | 差，易锯齿/混叠 | 最快 | 临床诊断一般不用，仅用于标签图（labelmap） |
| 三线性（Trilinear） | 好 | 快 | **临床主力**，平滑且开销低 |
| 三次/Catmull-Rom | 更好，边缘更锐利 | 慢 | 小血管、骨细节 |
| Lanczos | 高 | 慢 | 高精度场景 |

**各向异性体素问题**：当层间距远大于层内间距（如 0.6×0.6×2.5 mm）时，斜切面会出现"阶梯状"伪影。工程上通常先做**等体素重采样（isotropic resampling）**再交互，可显著改善边缘、测量正交性与薄层稳定性。

### 2.5 由 MPR 派生的功能

- **Slab / 厚层 MPR**：切面具有厚度（沿法线方向一定范围的体素参与），DICOM 中由 `MPR Thickness Type`（THIN/SLAB）与 `MPR Slab Thickness` 描述。
- **MIP（最大密度投影）**：`I_out(x,y) = max_{z∈slab} I(x,y,z)`，突出高密度结构（造影血管、钙化、骨）。
- **MinIP**：取最小值，突出低密度（气道、肺气肿）。
- **AvIP**：取平均，抑制噪声、表现实质信号趋势。
- **CPR（曲面重建）**：沿中心线展开。
- **虚拟内镜（VE）** 等 3D 后处理。

### 2.6 典型架构与交互

```
DICOM 序列
   │
   ▼
Volume Builder（体数据构建 / 等体素重采样）
   │
   ▼
3D Volume Cache（体缓存，GPU 纹理）
   │
   ├──────────┬──────────┐
   ▼          ▼          ▼
Axial     Coronal    Sagittal
Renderer  Renderer   Renderer
   │          │          │
   └──────────┼──────────┘
              ▼
        MPR Controller（同步控制：十字线 / 窗宽窗位 / 层厚）
              ▼
            UI 显示
```

交互要点：
- **十字线平移** = 平移切面原点（对公共矩阵施加平移矩阵）；
- **十字线旋转** = 绕该平面法线旋转（对公共矩阵施加旋转矩阵；也可用四元数，注意万向锁）；
- 三视图共享同一 `(x,y,z)`，需要**同步渲染**。

临床完整 MPR 通常还包含：三视图十字线、斜切、厚层 MPR、窗宽窗位、测量（距离/角度/ROI）、参考线、方向标记、多模态融合等。

### 2.7 优缺点

**优点**
- 无需重复扫描即可生成任意方向断层；
- 忠实保留原始密度/信号值；
- 曲面重建可在一幅图内展示弯曲结构全长。

**缺点**
- 难以表达复杂空间结构（需结合 VR）；
- 曲面重建易产生假阳性；
- 各向异性数据斜切质量差。

---

## 三、成熟技术栈

可分为**桌面端（C++/Java）**与**Web 端（JavaScript/WebGL/WebGPU）**两大阵营。

### 3.1 桌面端技术栈

#### （1）VTK（Visualization Toolkit）— 事实标准

- **语言/许可**：C++，BSD 许可；已原生支持 OpenGL/Vulkan（VTK 9.0+）；提供 Python/Java/.NET(ActiViz) 绑定。
- **MPR 核心类**：
  - `vtkImageReslice`：**任意切面重建的核心**，可旋转/缩放/平移/重采样/插值。
  - `vtkResliceCursor` + `vtkResliceCursorWidget`：三视图联动十字线。
  - `vtkImageViewer2` / `vtkImageActor`：二维切片显示。
  - `vtkImageMapToWindowLevelColors`：窗宽窗位。
  - `vtkGPUVolumeRayCastMapper`：GPU 体绘制（512³ 以上相比 CPU 可快 20 倍以上）。
  - Slab 模式用于厚层 MPR；`vtkDistanceWidget` / `vtkContourWidget` 用于测量/ROI。
- **DICOM 读取**：`vtkDICOMImageReader`（仅做最基础支持，不自动做 RAS/LPS 统一，生产项目通常配合 GDCM/DCMTK/ITK）。
- **定位**：渲染与可视化引擎，库而非完整应用。
- **注意事项**：`vtkImageViewer2` / `vtkResliceImageViewer` 把 mapper/actor/renderer/window/interactor 封装在一起，易用但不利于理解原理，开源实现多不直接采用。

#### （2）ITK + VTK + Qt 组合 — 临床/科研经典

- **分工**：ITK 负责 DICOM 解析、坐标校准、重采样、分割/配准/滤波；VTK 负责渲染管线；Qt 负责 GUI（`QVTKOpenGLWidget` 嵌入渲染窗口）。
- **ITK 关键点**：管道式 Filter 架构；4.0 之后移除对 VTK 的硬依赖，需通过 `ITKVtkGlue` 转换 `itk::Image` ↔ `vtkImageData`；ITK 默认 LPS（MHD 格式），Slicer 内部为 RAS。
- **适用**：需要精确分割/配准 + 自研 UI 的商业或科研项目。
- **包管理**：vcpkg（`vcpkg install gdcm vtk qt5`）。

#### （3）3D Slicer（应用级平台）

- **架构**：基于 VTK + ITK + CTK + Qt + Python + DCMTK + tbb 等的 SuperBuild。
- **核心抽象**：**MRML 场景**（数据/显示/存储三节点分离）+ 事件驱动同步视图 + 四类模块机制（CLI / Loadable / Scripted / Core）。
- **坐标系**：内部统一 RAS，读写文件按 LPS 处理（翻转前两轴符号）。
- **MPR 能力**：三视图、斜切、测量、多模态融合、4D、DICOM RT/SEG、MONAI Label AI 集成。
- **定位**：功能广的研究平台，可扩展性强（169+ 扩展），但启动慢、UI 层级深、内存占用高。
- **适合**：研究、术前规划、影像组学、AI 标注。

#### （4）MITK（Medical Imaging Interaction Toolkit）

- **架构**：C++，核心仅依赖 VTK + ITK（无 Qt 依赖），可当"工具包"或"应用平台"使用；提供 MITK Workbench 应用。
- **版本动态**：MITK v2026.06，新增 nnInteractive/TotalSegmentator AI 分割、实时 3D 分割可视化、`mitk-python`（PyPI）。
- **与 Slicer 对比**：MITK 更强调作为"可嵌入的工具箱/自定义应用基座"，Slicer 更强调"开箱即用的平台 + Python 生态"。分层有效。

#### （5）Weasis（Java 桌面/Web 启动）

- **语言/许可**：Java，EPL 2.0 / Apache 2.0 双许可。
- **能力**：**Oblique MPR**、MIP、3D 体绘制（v4.x）、多屏/HiDPI、GSPS、DICOM SEG/RT/RTDOSE/DVH、DICOMweb / DIMSE Q/R。
- **版本**：v4.6.6（2025-12）。
- **部署**：原生桌面安装包；通过 `weasis://` 协议从 Web 上下文启动（Web 端调起本地客户端，非纯浏览器渲染）。
- **注意**：开源发行版非 CE/FDA 认证医疗器械。

#### （6）其他桌面工具

OsiriX/Horos（macOS）、RadiAnt（Windows，商业）、ITK-SNAP（轻量分割/可视化）、Elastix（命令行配准）等。

### 3.2 Web 端技术栈（当前主流方向）

#### （1）Cornerstone3D — Web 端首选

- **包/许可**：`@cornerstonejs/core` 等，MIT；**当前 npm 版本 5.8.2（2026-08）**，依赖 `@kitware/vtk.js ~36.4.x`。
- **架构**：TypeScript + WebGL（GPU 加速），底层以 **vtk.js 为渲染骨干**；提供 `RenderingEngine`、`StackViewport`（2D 栈）、`VolumeViewport`（**天然支持 MPR**）、`VolumeViewport3D`、Whole Slide 视口。
- **MPR 要点**：`VolumeViewport` 通过 `setOrientation`（AXIAL/SAGITTAL/CORONAL/ACQUISITION/REFORMAT）与相机 `viewPlaneNormal`/`viewUp` 实现任意方向切面；支持 slab 厚度、Crosshairs 三视图联动、Reference Lines、DICOM Reformats in MPR。
- **渲染引擎演进**：默认 `ContextPoolRenderingEngine`（WebGL 上下文池、逐视口离屏渲染）替代旧的 `TiledRenderingEngine`，解决多视口/大画布/多屏问题；`SharedVolumeMappers` 复用纹理支持 PET-CT 融合。
- **新特性**：实验性 **WebGPU** 后端（GenericViewport 的 stack 与 volume-slice/MPR），WebGPU 在大数据集（如 12×10 标准 150µm 数据集）上明显优于 WebGL。
- **注意**：v4.12.0 起升级 vtk.js 34.x 曾引入 Linux 独显/iOS Safari 体渲染黑屏问题（后续通过 `preferSizeOverAccuracy` / 纹理过滤修复），选型时需回归测试目标环境。

#### （2）VTK.js（@kitware/vtk.js）

- **定位**：VTK 的 JavaScript/WASM 版本，WebGL/WebGPU 渲染，`vtkImageData`、`vtkImageReslice`、`vtkVolumeMapper`、`vtkVolume` 等与 C++ VTK 概念一致。
- **角色**：Cornerstone3D 的底层渲染库；也可单独用于自研 Web 3D/体绘制应用。
- **版本**：约 36.4.x。

#### （3）OHIF Viewer — 开源 Web 查看器参考实现

- **架构**：React + TypeScript，基于 Cornerstone3D 扩展体系（`@ohif/extension-cornerstone` 等），支持 DICOMweb。
- **MPR**：v3 起完全基于 Cornerstone3D 实现 MPR（v2 曾用 VTK viewport）；提供 Crosshairs、MPR Slab Oblique 等。
- **版本**：v3.11（2026-04）新增多模态融合、RT Dose、超声模式、DICOM Labelmap、RT Structure Set 等。
- **定位**：可直接部署，也可作为构建自定义影像应用的框架；是 Web 场景最成熟的开源起点。

#### （4）其他 Web 查看器/库

- **Weasis Web**：通过协议调起本地 Java 客户端。
- **dwv（DICOM Web Viewer）**：轻量 JS 查看器，社区活跃，MPR 能力相对基础。
- **BlueLight**：纯 JS/HTML5 单页应用，低成本计算算法，支持 MPR/VR，移动端表现好（学术论文）。
- **Med3Web / VolView / Image-IN** 等：各有侧重（如 VolView 的电影级体渲染）。
- **dcmjs / dicomParser / dcmjs-codecs**：DICOM 解析与编解码基础库。

### 3.3 DICOM 解析与 I/O 库

| 库 | 语言 | 说明 |
| --- | --- | --- |
| DCMTK | C++ | 最全面的 DICOM 工具包，网络服务（SCU/SCP） |
| GDCM | C++ | Grassroots DICOM，压缩格式/多帧支持好，VTK/ITK 常用 |
| ITK | C++/Python | 影像 I/O + 处理（DICOM/NIfTI/MHD/MetaImage） |
| dcmjs | JS | Web 端 DICOM 解析与 SR/SEG 适配 |
| cornerstone DICOM image loader | JS | Cornerstone 的 DICOM 加载/解码（含 WASM 编解码器） |

### 3.4 技术栈对比

| 技术栈 | 平台 | 语言 | 许可 | MPR 成熟度 | 集成成本 | 适合场景 |
| --- | --- | --- | --- | --- | --- | --- |
| VTK | 桌面/嵌入 | C++/Py | BSD | ★★★★★ | 中 | 自研引擎、科研 |
| ITK+VTK+Qt | 桌面 | C++ | BSD | ★★★★★ | 高 | 需分割/配准的临床软件 |
| 3D Slicer | 桌面 | C++/Py | BSD | ★★★★★ | 低（用）/高（改） | 研究、手术规划、AI 标注 |
| MITK | 桌面 | C++ | BSD | ★★★★★ | 中 | 定制化医学应用基座 |
| Weasis | 桌面/Web启动 | Java | EPL2/Apache2 | ★★★★☆ | 低 | 快速获得完整 PACS 客户端 |
| Cornerstone3D | Web | TS | MIT | ★★★★★ | 中 | 零安装 Web 阅片/MPR |
| VTK.js | Web | JS/TS | BSD | ★★★★☆ | 中 | 自研 Web 体绘制 |
| OHIF | Web | React/TS | MIT | ★★★★★ | 低（用）/中（改） | Web PACS/研究平台 |
| dwv/BlueLight | Web | JS | 各自开源 | ★★★☆☆ | 低 | 轻量/移动查看 |

---

## 四、选型建议

### 4.1 按场景

- **纯 Web、零安装、以阅片+MPR 为主**：**Cornerstone3D（+ OHIF）** 是当前最成熟组合；需要直接可用选 OHIF，需要深度定制选 Cornerstone3D。底层可递归使用 vtk.js。
- **桌面端、需要完整 PACS 客户端**：**Weasis**（快速成型）或 **MITK**（自研基座）。
- **科研/手术规划/AI 标注**：**3D Slicer**（生态最广，Python 可扩展）。
- **完全自研引擎/算法**：**VTK + ITK + Qt**（桌面）或 **vtk.js**（Web）。

### 4.2 按自研程度

1. **直接复用**：OHIF / Weasis —— 成本最低，定制受限。
2. **框架定制**：Cornerstone3D / MITK —— 平衡。
3. **底层自研**：VTK/ITK/VTK.js —— 自由度最高，工作量大。

### 4.3 推荐组合（Web）

```
DICOM 数据
  └─ @cornerstonejs/dicom-image-loader（dcmjs + WASM 解码）
       └─ @cornerstonejs/core（VolumeViewport / ContextPoolRenderingEngine / vtk.js）
            └─ @cornerstonejs/tools（Crosshairs / 测量 / 分割）
                 └─ OHIF 或自研 React/Vue UI
```

### 4.4 工程实践与坑

- **坐标系统一**：先做 IJK↔LPS/RAS 转换（4×4 仿射），否则三视图解剖方位会错误或左右颠倒。
- **各向异性体素**：交互前做等体素重采样，减少斜切阶梯伪影。
- **插值选择**：诊断用三线性/三次；标签图/分割用最近邻。
- **性能**：体数据放 GPU 纹理、共享 mapper、按需分块/流式加载；桌面首选 `vtkGPUVolumeRayCastMapper`；Web 注意 WebGL 上下文数量与大画布上限（ContextPool 模式）。
- **兼容性**：Cornerstone3D 体渲染在 Linux 独显、iOS Safari、旧版 WebGL 上曾有回归，需覆盖目标浏览器/操作系统做回归测试；WebGPU 仍为实验特性。
- **大数据**：GB 级序列需关注内存与加载策略（流式、降采样预览）。
- **窗宽窗位与 HU**：务必使用 Rescale Slope/Intercept 还原真实值。
- **合规**：开源查看器（Weasis、OHIF 等）默认非认证医疗器械，临床主诊断用途需满足当地法规（CE/FDA/NMPA）。

---

## 五、参考来源（节选）

- DICOM Standard PS3.3 C.11.26 Multi-Planar Reconstruction Geometry Module — https://dicom.nema.org/medical/dicom/2020d/output/chtml/part03/sect_C.11.26.html
- 3D Slicer 坐标系文档 — https://slicer.readthedocs.io/en/latest/user_guide/coordinate_systems.html
- Cornerstone3D 文档（Rendering Engine / Viewports / Examples / MPR Crosshairs） — https://cornerstonejs.org/docs
- Cornerstone3D 仓库与 npm（@cornerstonejs/core） — https://github.com/cornerstonejs/cornerstone3D
- OHIF Viewer 文档（MPR） — https://docs.ohif.org/user-guide/viewer/mpr/
- VTK.js 文档（vtkImageReslice / Volume） — https://kitware.github.io/vtk-js/
- MITK 官网 — https://www.mitk.org/
- Weasis 官网/GitHub — https://weasis.org / https://github.com/nroduit/weasis
- Web-Based DICOM Viewers: A Survey and Performance Classification（PMC12092310）
- 维基百科：二维多平面重建 — https://zh.wikipedia.org/zh-cn/二维多平面重建
