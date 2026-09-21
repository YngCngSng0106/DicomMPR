# DICOM MPR Demo 实施方案（Java 17 + VTK + Swing + dcm4che）

> 范围：实现功能清单中 **C2 及之前**（A 层数据、B1/B2、C1/C2），外加最小必要观察交互。
> 交付物：可运行桌面 Demo —— 选择本地 DICOM 文件夹 → 左侧列出各序列缩略图 → 双击序列 → 右侧单窗口三视口（轴/冠/矢）显示且十字线联动。

---

## 一、技术选型（已确认）

| 项 | 选择 |
| --- | --- |
| 语言/构建 | Java 17 + Maven |
| GUI | Swing + VTK 的 AWT 组件 `vtk.vtkCanvas` |
| 渲染 | VTK（自行构建，开启 Java 包装）|
| DICOM 解析 | dcm4che（纯 Java）|
| 多帧 | **不支持**（仅单帧序列：一文件一层）|
| 交互 | C2 十字线联动 + 最小交互（滚轮翻层 / 窗宽窗位 / 缩放）|
| 布局 | 单个 `vtkRenderWindow` 切三个视口（内含于一个 `vtkCanvas`）|

**范围内**：目录扫描、按 SeriesUID 分组、按位置排序、构建 `vtkImageData`、三正交视图、`vtkResliceCursor` 十字线联动、基础窗宽窗位与翻层。
**范围外**：斜切（C3）、厚层/Slab（C5）、MIP（C6）、CPR（C7）、测量标注（F）、3D 体绘制（G）、导出（H）、PACS 网络接入（A7）、多帧/4D/融合（B4 相关）。

---

## 二、环境准备：构建带 Java 包装的 VTK（最大风险点）

### 2.1 前置

- **Visual Studio 2022**（含"使用 C++ 的桌面开发"）
- **CMake ≥ 3.20**
- **JDK 17**（`JAVA_HOME` 指向 JDK，注意不是 JRE）
- **VTK 源码**：使用 **v9.6.2**（含 Java 包装；较 9.3.x 与新 CMake 更兼容）。
  GitHub：`https://github.com/Kitware/VTK.git`（或 GitLab 官方镜像）
- **Ninja（可选）** 或直接用 VS 生成器

### 2.2 CMake 配置（关键开关）

```powershell
# JDK 17 与 CMake 由 scripts\env.ps1 自动探测（JAVA_HOME / 常见安装目录 / PATH）；
# 也可用 -JdkHome / -CMake 显式覆盖。手工执行时先保证 JAVA_HOME 指向 JDK 17。
$env:JAVA_HOME = "<JDK17-目录>"   # 注意：路径含空格，用环境变量而非 -D 传入
$cmake = "cmake"                  # 或在 PATH 中直接调用

& $cmake -S <vtk-src> -B <vtk-build> -G "Visual Studio 17 2022" -A x64 `
  -DVTK_WRAP_JAVA=ON `
  -DVTK_BUILD_TESTING=OFF `
  -DVTK_BUILD_EXAMPLES=OFF `
  -DCMAKE_BUILD_TYPE=Release `
  "-DCMAKE_POLICY_VERSION_MINIMUM=3.5" `
  -DVTK_MODULE_ENABLE_VTK_RenderingOpenGL2=YES `
  -DVTK_MODULE_ENABLE_VTK_RenderingUI=YES `
  -DVTK_MODULE_ENABLE_VTK_InteractionWidgets=YES `
  -DVTK_MODULE_ENABLE_VTK_ImagingCore=YES `
  -DVTK_MODULE_ENABLE_VTK_ImagingSources=YES `
  -DVTK_MODULE_ENABLE_VTK_IOImage=YES `
  -DVTK_MODULE_ENABLE_VTK_IOLegacy=YES
```

> - `CMAKE_POLICY_VERSION_MINIMUM=3.5`：规避 CMake 4.x 对旧 `cmake_minimum_required` 的拒绝。
> - `InteractionWidgets` 提供 `vtkCanvas` 与 `vtkResliceCursorWidget`（必需）。
> - 只"显式开启"必要模块；未列出的模块按默认状态构建，第一次编译时间较长（数十分钟）。

### 2.3 编译

```powershell
cmake --build <vtk-build> --config Release -j
```

### 2.4 预期产物（用搜索定位，实际路径随生成器而定）

```powershell
# Java 类库（vtk.jar）
Get-ChildItem -Path <vtk-build> -Recurse -Filter vtk.jar | Select-Object -ExpandProperty FullName
# JNI 包装库（*Java.dll）
Get-ChildItem -Path <vtk-build> -Recurse -Filter *Java.dll | Select-Object -First 10 -ExpandProperty FullName
# VTK 核心运行库（vtkCommonCore-9.6.dll 等，通常在 <vtk-build>\bin\Release）
Get-ChildItem -Path <vtk-build>\bin -Recurse -Filter vtkCommonCore*.dll | Select-Object -ExpandProperty FullName
```

- `vtk.jar` 一般在 `<vtk-build>/lib/java/`
- JNI 包装库一般在 `<vtk-build>/lib/java/vtk-Windows-amd64/`
- 核心 DLL 一般在 `<vtk-build>/bin/Release/`
- 运行时需让 `java.library.path` / `PATH` 同时覆盖**核心 DLL 目录**与**JNI 目录**

### 2.5 安装到本地 Maven 仓库

```powershell
mvn install:install-file -Dfile=<vtk-build>/lib/java/vtk.jar `
  -DgroupId=org.vtk -DartifactId=vtk -Dversion=9.6.2 -Dpackaging=jar
```

### 2.6 运行时加载原生库

- 启动参数：`-Djava.library.path=<vtk-build>/bin/Release`（或把该目录加入 `PATH`）
- 程序启动时显式加载：`vtk.vtkNativeLibrary.LoadAllNativeLibraries()`（以实际构建版本提供的方法为准）
- **先用最小程序（弹出一个 `vtkCanvas`）验证 Java 绑定可用，再进入业务开发。**

---

## 三、依赖与项目结构

### 3.1 Maven 依赖

```xml
<dependencies>
  <dependency>
    <groupId>org.dcm4che</groupId><artifactId>dcm4che-core</artifactId><version>5.31.1</version>
  </dependency>
  <dependency>
    <groupId>org.dcm4che</groupId><artifactId>dcm4che-imageio</artifactId><version>5.31.1</version>
  </dependency>
  <!-- JPEG2000 等有损/无损压缩解码（可选） -->
  <dependency>
    <groupId>org.dcm4che</groupId><artifactId>dcm4che-imageio-opencv</artifactId><version>5.31.1</version>
  </dependency>
  <!-- 日志（遵循组织 log4j 规范） -->
  <dependency>
    <groupId>org.apache.logging.log4j</groupId><artifactId>log4j-slf4j2-impl</artifactId><version>2.23.1</version>
  </dependency>
  <!-- 本地安装的 VTK -->
  <dependency>
    <groupId>org.vtk</groupId><artifactId>vtk</artifactId><version>9.3.1</version>
  </dependency>
</dependencies>
```

### 3.2 目录结构

```
dicom-mpr-demo/
├── pom.xml
└── src/main/java/com/zlyd/mpr/
    ├── App.java                       # 入口：加载 native、启动 Swing
    ├── ui/
    │   ├── MainFrame.java             # JSplitPane：左列表 / 右 MPR
    │   ├── SeriesListPanel.java       # 左侧序列缩略图列表
    │   └── VtkMprPanel.java           # 右侧 vtkCanvas 宿主
    ├── dicom/
    │   ├── DicomScanner.java          # 递归扫描目录、解析文件
    │   ├── SeriesInfo.java            # 序列元数据模型
    │   ├── SliceInfo.java             # 单层元数据模型
    │   ├── SeriesGrouping.java        # 分组 + 按法向量排序
    │   └── VolumeBuilder.java         # 构建 vtkImageData
    ├── mpr/
    │   ├── MprViewer.java             # 三视口 + reslice cursor + widgets
    │   └── MprInteractor.java         # 滚轮翻层 / 窗宽窗位 / 缩放
    └── util/
        ├── ThumbnailFactory.java      # 由切片生成 BufferedImage 缩略图
        └── WindowLevelDefaults.java   # 从 tag 取窗宽窗位/回退默认
```

---

## 四、模块设计

### 4.1 DICOM 层

**`DicomScanner`**
- 递归扫描所选目录，逐个 `DicomInputStream` 解析；跳过非 DICOM 文件（无 SOPClassUID）。
- 只读取需要的 tag，避免整文件载入；对大目录用文件过滤（`.dcm` 优先，但**不依赖扩展名**）。

**读取的关键 tag**

| 用途 | tag |
| --- | --- |
| 分组 | `SeriesInstanceUID (0020,000E)` |
| 显示信息 | `Modality (0008,0060)`、`SeriesDescription (0008,103E)`、`SeriesNumber (0020,0011)` |
| 几何 | `Rows (0028,0010)`、`Columns (0028,0011)`、`PixelSpacing (0028,0030)`、`ImageOrientationPatient (0020,0037)`、`ImagePositionPatient (0020,0032)`、`SpacingBetweenSlices (0018,0088)`、`SliceThickness (0018,0050)` |
| 像素 | `BitsAllocated (0028,0100)`、`BitsStored`、`PixelRepresentation (0028,0103)`、`RescaleSlope (0028,1053)`、`RescaleIntercept (0028,1052)`、`TransferSyntaxUID (0002,0010)` |
| 显示 | `WindowCenter (0028,1050)`、`WindowWidth (0028,1051)` |

**`SeriesGrouping`**
- 按 `SeriesInstanceUID` 分组；
- 每组内：以 `ImageOrientationPatient` 求法向量 `Z = row × col`，对每层 `d = ImagePositionPatient · Z` 排序；
- **一致性校验**：同组朝向差异过大 → 该序列标记为"不可建体数据"并在 UI 灰显/提示；层间距异常（与中位数偏差大）→ 日志告警并在列表加"⚠"标记。

**`VolumeBuilder`**（对应 B1）
- 尺寸 `nx=Columns, ny=Rows, nz=切片数`；
- `SetOrigin(第一层位置)`、`SetSpacing(列间距, 行间距, 层间距)`、`SetDirectionMatrix(X,Y,Z)`；
- 按符号/位深选类型（`VTK_SHORT` / `VTK_UNSIGNED_SHORT`），`AllocateScalars`；
- 逐层 `System.arraycopy` 到偏移 `k*nx*ny`（利用"VTK x 最快 = DICOM 行优先"）；
- 用 `vtkImageShiftScale(slope, intercept, 输出 short)` 得到 HU 体数据。

**`ThumbnailFactory`**
- 取该序列**中间层**，解码像素 → 应用 rescale + 窗宽窗位（tag 缺失时用 CT 默认软组织窗 W=400/L=40）→ 缩放到约 128×128 → `BufferedImage`。

### 4.2 UI 层

**`MainFrame`**：`JSplitPane`，左侧 `SeriesListPanel`（宽约 180），右侧 `VtkMprPanel`。

**`SeriesListPanel`**
- `JList<SeriesInfo>` + 自定义 `ListCellRenderer` 显示缩略图 + 序列描述/层数/模态；
- 顶部"选择文件夹"按钮（`JFileChooser`，`DIRECTORIES_ONLY`）；
- 双击（`MouseListener` 的 `clickCount==2`）→ 回调打开 MPR。
- 扫描/解析在后台线程（`SwingWorker`），完成后 `EDT` 更新列表。

**`VtkMprPanel`**
- 持有一个 `vtkCanvas`（铺满），将 `vtkRenderWindow` 注入；
- 提供 `showVolume(vtkImageData)`：重建/刷新 `MprViewer`。

### 4.3 MPR 层

**`MprViewer`**（C1 + C2）
- 一个 `vtkRenderWindow` + 一个 `vtkRenderWindowInteractor`（由 `vtkCanvas` 提供）；
- 三个 `vtkRenderer`，视口采用 **左侧上下分栏 + 右侧整栏**（VTK 视口坐标原点在左下）：
  - **Axial 横断面**：左上，视口 `(0.0, 0.5, 0.5, 1.0)`
  - **Sagittal 矢状面**：左下，视口 `(0.0, 0.0, 0.5, 0.5)`
  - **Coronal 冠状面**：右侧整栏，视口 `(0.5, 0.0, 1.0, 1.0)`
  - 每个 renderer 背景色不同以便区分（如分别深灰/深蓝/深绿）；
- 一个共享 `vtkResliceCursor`：`SetImage(volume)`、`SetCenter(volume 中心的世界坐标)`；
- 每个视图：`vtkResliceCursorWidget` + `vtkResliceCursorLineRepresentation`；
  - 三个 rep 都 `GetResliceCursorActor().GetCursorAlgorithm().SetResliceCursor(cursor)`；
  - 朝向：Axial→`SetPlaneOrientationToZAxis()`、Sagittal→`ToXAxis()`、Coronal→`ToYAxis()`；
  - `widget.SetInteractor(interactor)`、`widget.SetRepresentation(rep)`、`widget.EnabledOn()`；
- `renderWindow.SetSize(...)`、`Render()`。

**联动原理**：三个 rep 共享同一 cursor，任一十字线被拖动 → cursor 更新 → 其余两视图自动重切并刷新（即 C2）。

**`MprInteractor`**（最小交互）
- **翻层**：监听 `MouseWheelForward/BackwardEvent`，用 `renderWindow.FindPokedRenderer()` 判断当前视口，沿该视口平面法线移动 cursor 中心一个层间距；
- **窗宽窗位**：**工具栏 `JSlider` 控制**（窗宽/窗位各一个滑块），拖动即调用 `rep.SetWindowLevel(w, l, 1)` 应用到三个视图（以实际 VTK 版本 API 为准）；预留"预设"下拉（软组织/肺/骨）；
- **缩放**：中键拖动 → 对该视口 `vtkRenderer` 的相机执行 `Dolly`（或复用 `vtkInteractorStyleImage`）；
- **默认窗宽窗位**：打开序列时用 tag 值（多值时取第一组，缺失用软组织窗）初始化滑块与视图；
- **旋转（Shift+左键）**：C3 不在范围内，Demo 中**禁用**（`rep` 关闭旋转交互 / 不挂接旋转手势）。
- 事件优先级需与 `vtkResliceCursorWidget` 协调：Widget 优先处理十字线的左键拖动，其余手势由自定义 observer 处理。

### 4.4 线程、内存与异常

- DICOM 扫描/解码/缩略图生成放后台线程；VTK 渲染与 UI 更新在 EDT。
- **VTK 对象生命周期**：Java 侧必须持有对 `vtkImageData`/cursor/widget 的**强引用**，防止 GC 时 native 对象被释放导致崩溃；窗口关闭时显式释放。
- 体数据内存估算与 B2：MVP 一次只打开一个序列；切换序列时释放旧体数据。
- 统一异常处理 + Log4j 日志；对"无法解析/朝向不一致/层间距异常"给出可读提示。

---

## 五、关键流程

```
[选择文件夹]
   → DicomScanner 扫描/解析
   → SeriesGrouping 分组 + 排序 + 校验
   → ThumbnailFactory 生成缩略图
   → SeriesListPanel 显示列表
[双击序列]
   → VolumeBuilder 构建 vtkImageData + ShiftScale(HU)
   → VtkMprPanel.showVolume(volume)
        → MprViewer：cursor + 3 renderer + 3 widget/rep
        → MprInteractor 绑定滚轮/右键/中键
   → renderWindow.Render()  → 三视图 + 联动十字线
```

---

## 六、里程碑

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| M0 | 构建 VTK Java + 跑通 `vtkCanvas` 最小程序 | 能弹出窗口并绘制 |
| M1 | DICOM 扫描、分组、排序（控制台验证） | 正确列出序列数/层数/几何 |
| M2 | 左侧缩略图列表 + 选择目录 | 缩略图正确、可双击 |
| M3 | `VolumeBuilder` + 三正交视图显示（C1） | 三视图方向正确、无左右颠倒 |
| M4 | 十字线联动（C2） | 任一视图拖动，另两视图同步 |
| M5 | 最小交互（滚轮/窗宽窗位/缩放） | 可翻层、调窗、缩放 |
| M6 | 联调、异常提示、验收 | 满足下方验收标准 |

---

## 七、风险与对策

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| VTK Java 包装构建失败/不可用 | 阻断 | 先用最小 case 验证；只编必要模块；必要时换 VTK 版本 |
| native 库加载失败 | 启动崩溃 | 正确设置 `java.library.path`；把 `bin/Release` 加入 `PATH`；依赖 DLL 齐全 |
| VTK 对象被 GC 导致 JNI 崩溃 | 随机崩溃 | 全程保持强引用；退出时统一释放 |
| AWT `vtkCanvas` 与 Swing 混用 | 遮挡/闪烁 | 让 canvas 独占一块区域，不在其上叠加 Swing 组件 |
| 序列朝向不一致/丢层 | MPR 错误 | 分组时校验并提示；不满足则不允许构建 |
| `vtkResliceCursorWidget` 与自定义交互抢事件 | 操作失灵 | 以 Widget 为左键优先，其余手势用自定义 observer，必要时改自研 reslice |
| 大序列内存 | OOM | MVP 单序列、切换即释放；后续再做 B2 |

---

## 八、验收标准

1. 能通过"选择文件夹"加载本地单帧 DICOM 目录；
2. 左侧正确列出所有序列（缩略图 + 描述），混合序列能分开；
3. 双击任一序列，右侧显示 Axial/Coronal/Sagittal 三视图，方向/左右正确；
4. 在任一视图拖动十字线，另外两视图同步定位；中心点对应同一解剖位置；
5. 滚轮可翻层、工具栏滑块可调窗宽窗位、中键可缩放；
6. 加载中途不崩溃；异常数据有可读提示；
7. 只依赖 C2 及之前功能，代码结构清晰、各模块可独立测试。

---

## 九、决策记录与遗留项

**已确认决策**

1. **VTK 版本**：使用 **9.3.x 稳定版**自行构建（开启 Java 包装）。✅
2. **三视图布局**：**左侧上下分栏 + 右侧整栏**——Axial 左上、Sagittal 左下、Coronal 右侧整栏。✅
3. **十字线旋转**：Demo 中**禁用**（属 C3）。✅
4. **窗宽窗位交互**：**工具栏 `JSlider`** 控制。✅

**环境实施结果**

1. 本机原只有 JDK 8（`JAVA_HOME` 仍指向它）；已通过 `winget` 安装 **Temurin JDK 17**。
   路径不再硬编码：`scripts\env.ps1` 会跳过 JDK 8 自动选中 JDK 17，也可用 `-JdkHome` 指定。
2. **dcm4che 版本修正为 `5.31.2`**：仓库里 `5.31.3` 不完整（缺 `dcm4che-parent` / `dcm4che-imageio`），`5.31.2` 三类工件齐全；仓库地址 `https://maven.dcm4che.org/`。
3. M1 阶段暂不引入 `dcm4che-imageio-opencv`（压缩像素解码留到 M3）。

**遗留项（待确认）**

1. **VTK 构建**：需按第二节步骤自行构建（开启 `VTK_WRAP_JAVA`），完成后执行 M0 验证。
2. **交付形式**：M0–M1 完成后，是否继续 M2–M6（可选：按里程碑逐步推进）。

---

## 十、M0–M1 实施记录

### 测试数据

- 路径：`3120221229008001\`（项目根目录下），含子目录 `1`~`5`。
- 共 **1032 个 DICOM 文件**，解析出 **7 个序列**。

### M1 运行结果（已验证）

运行命令：

```powershell
# 在项目根目录执行；测试数据用相对路径（JAVA_HOME 需指向 JDK 17）
$env:JAVA_HOME = "<JDK17-目录>"
mvn -q exec:java "-Dexec.mainClass=com.zlyd.mpr.m1.M1Main" "-Dexec.args=3120221229008001"
```

结果摘要：

| # | Modality | 描述 | 切片 | 尺寸/间距 | 朝向/法向量 | 可重建 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | CT | 定位像 0.70 Tr20 冠状位 | 2 | 512×512, 1.000 | (1,0,0\|0,0,-1) / (0,1,0) | 否（仅 2 层） |
| 2 | CT | 胸部 5.00 Br60 S3 | 67 | 512×512, 0.758 / 5.0 | 轴位 / (0,0,1) | 是 |
| 3 | CT | 胸部 5.00 Br40 S3 | 67 | 512×512, 0.758 / 5.0 | 轴位 / (0,0,1) | 是 |
| 4 | CT | 胸部 1.00 Br60 S3 | 447 | 512×512, 0.758 / 0.75 | 轴位 / (0,0,1) | 是 |
| 5 | CT | 胸部 1.00 Br40 S3 | 447 | 512×512, 0.758 / 0.75 | 轴位 / (0,0,1) | 是 |
| 6 | CT | 2D 图（无几何） | 1 | 512×512 | 无 | 否（缺几何） |
| 7 | SR | 检查报告 | 1 | - | 无 | 否（非影像） |

结论：分组、法向量排序、层间距计算、几何校验、非体数据序列的容错均符合预期。**可作为 M3 数据源。**

### M0 结果：**已通过**（2026-09-16）

- VTK 9.6.2 编译成功，JNI 库与核心 DLL 齐全；
- 按依赖顺序加载后，MPR 所需的 27 个 native 库**全部 OK**（含 `RenderingCore`、`RenderingOpenGL2`、`InteractionWidgets`、`ImagingCore`、`RenderingFreeType`、`InteractionStyle`）；
- `vtkImageData`、`vtkImageReslice` 可正常实例化。
- 新增 `VtkNativeLoader`（按依赖顺序加载），供 M0 与 M3 复用。

**构建/运行方式（已脚本化）**

```powershell
# 1) 构建 VTK 并生成 vtk.jar
powershell -ExecutionPolicy Bypass -File scripts\build-vtk.ps1

# 2) 校验（不弹窗）
powershell -ExecutionPolicy Bypass -File scripts\run-m0.ps1 -CheckOnly

# 3) 弹窗看球体
powershell -ExecutionPolicy Bypass -File scripts\run-m0.ps1
```

**踩坑记录（重要）**

1. **VS 生成器不产出 `vtk.jar`**：VTK 的 Java 打包在 Visual Studio 生成器下不执行（官方主要面向 Ninja）。
   解决：构建后手工用 JDK 的 `javac` + `jar` 把 `vtk-build\Wrapping\Java` 下的 Java 源码打包（`scripts\build-vtk.ps1` 已内置）。
2. **PowerShell 传参陷阱**：`-DXXX=a.b` 形式会被在 `.` 处拆成两个参数（如 `=3.5` → `3` + `.5`）。
   解决：所有含点的 `-D` 参数一律加引号，如 `"-DCMAKE_POLICY_VERSION_MINIMUM=3.5"`、`"-Dversion=9.6.2"`。
3. **运行库路径**：Windows 解析依赖 DLL 不走 `java.library.path`，运行时必须把
   **JDK\bin（含 `jawt.dll`）+ 核心 DLL 目录 + JNI 目录** 都加入 `PATH`。
4. **实际产物路径**：
   - `vtk.jar`：`third_party\vtk-build\lib\java\Release\vtk.jar`
   - JNI 库：`third_party\vtk-build\lib\java\vtk-Windows-AMD64\Release\`
   - 核心 DLL：`third_party\vtk-build\bin\Release\`
5. **不要用 `LoadAllNativeLibraries()`**：它按枚举顺序（高阶模块在前）加载，而部分 JNI 库依赖其他 JNI 库
   （如 `vtkInteractionWidgetsJava` 依赖 `vtkRenderingContext2DJava`），会因依赖未加载而报
   "procedure not found"。必须**按依赖顺序**加载 —— 已封装为 `com.zlyd.mpr.util.VtkNativeLoader`。
6. **PowerShell 脚本用纯 ASCII 注释**：`.ps1` 需带 BOM 才被 PS 5.1 识别为 UTF-8，否则中文注释按 GBK 解析可能破坏语法。

### M2 结果：**已通过**（2026-09-16）

范围：左侧序列缩略图列表（不含 MPR 渲染）。

**实现**
- `ui/MainFrame`：`JSplitPane`（左列表 / 右占位区），双击序列在右侧显示该序列信息（MPR 于 M3 接入）。
- `ui/SeriesListPanel`：选择文件夹（`JFileChooser`）→ `SwingWorker` 后台扫描 → 填充列表；双击回调。
- `ui/SeriesEntry` / `ui/SeriesListCellRenderer`：缩略图 + 文本单元渲染。
- `util/ThumbnailFactory`：读单张切片像素 → Rescale → 窗宽窗位 → 缩放为 96px 缩略图。
- `util/WindowLevelDefaults`：窗宽窗位取用与回退（CT 默认 400/40）。
- `App`：桌面程序入口。

**验证**
- 无界面校验 `m2.M2ThumbnailCheck`：测试数据 7 个序列生成 6 张缩略图（SR 无像素，正常失败），目视 series_04 为正确胸部 CT 影像。
- UI 构建冒烟 `m2.M2UiSmoke`：主窗口可正常构造，exit=0。

**运行**
```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-app.ps1
```

### M2.1 结果：**已通过**（2026-09-16）

范围：双击或拖拽序列 → 右侧 VTK 单平面阅片（翻层 / 窗宽窗位 / 缩放 / 平移）。

**设计要点**
- 右侧视图抽象为 `ui.SeriesView` 接口：主界面不依赖 VTK；VTK 实现 `mpr.VtkStackView`（`java-vtk` 源码目录，`-Pvtk` 编译）。
- 双击（列表事件）与拖拽（Swing DnD）两条路径都调用 `SeriesView.showSeries`。
- `mpr.VolumeBuilder`（B1）：读取序列全部切片 → rescale → 组装 `vtkImageData`（short，HU），
  体素排列利用"VTK x 最快 = DICOM 行优先"整块拷贝。
- 渲染：`vtkImageSlice` + `vtkImageSliceMapper`（`SetOrientationToK` 显示横断）。

**交互实现（关键结论）**
- `vtkInteractorStyleImage` 继承自 `TrackballCamera`：**左键=窗宽窗位、中键=平移、右键=dolly** 均已内置。
- 但**滚轮也被 TrackballCamera 占用（dolly/缩放）**。解决：相机设**平行投影**后 `Dolly` 只改距离、视觉无变化，
  于是滚轮可安全接管用于翻层；缩放改为右键拖动、通过 `SetParallelScale` 自实现。
- 快捷键：`↑/↓` 翻层，`PageUp/PageDown` 翻 10 层，`Home/End` 首末层。
- 拖拽落点：`vtkCanvas` 是 AWT 重量级组件，Swing 的 `TransferHandler` 在其上不生效，
  故在 canvas 上单独注册 AWT `DropTarget`（同 JVM 直接用 `SeriesInfo` 对象传递）。

**验证**
- `m2.M2VolumeCheck`（无界面）：体数据 `dims=[512,512,447] spacing=[0.7576,0.7576,0.75]`，
  离屏渲染首/中/末层为 PNG，目视 `axial_223.png` 为**正确胸部 CT 影像**。
- 默认 profile（无 VTK）与 `-Pvtk` 均可编译；`M2UiSmoke` 通过。

**运行（含 VTK）**
```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-app.ps1
```
> 注意：需用 `java` 直启并带 `-Djava.library.path`（`exec:java` 下 native 解析会失败），脚本已处理。
