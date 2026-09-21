# DICOM MPR 业务流程说明（含代码入口）

> **路径约定**：`src/main/java/` 记为 **[java]**，`src/main/java-vtk/` 记为 **[java-vtk]**（后者需 `-Pvtk` 编译）。
> **行号**：基于当前版本源码，若后续改动请以实际为准。
> **相关文档**：行为规范与设计结论见 `MPR斜切设计.md`（斜切/光标坐标系/旋转规则/90° 变化表/交互与验收断言）；功能条目状态见 `MPR功能清单.md`。
> **C3 斜切已实现**：三平面改为"光标坐标系 + 每视图一套 `vtkImageResliceMapper`"，相机改为"机架 + 交点钉住"，交互新增"拖十字线旋转 / A 回正"，各视图相机/十字线/状态栏入口见 ⑯。

---

## 0. 总览

```
① 启动程序
      │  App.main
      ▼
② 选择文件夹 ──────────────► SeriesListPanel.chooseFolder
      ▼
③ 后台扫描解析 ────────────► SeriesLoader.load → DicomScanner.scan/readInstance
      ▼
④ 分组与几何计算 ──────────► SeriesGrouper.group/assemble → SeriesGeometryAnalyzer.analyze
      ▼
⑤ 生成缩略图 ──────────────► SeriesLoader.createThumbnail → ThumbnailFactory.create
      ▼
⑥ 左侧列表展示 ────────────► SeriesListPanel.onLoaded → SeriesEntry / SeriesListCellRenderer
      │
      ├── 双击/拖拽 ────────► ⑦ 2D 单平面阅片（VtkStackView + StackScene）
      └── 右键「MPR」 ─────► ⑧ MPR 三视图（VtkMprView + MprScene）
                                   │
                                   ▼
                              ⑨ 构建体数据 VolumeBuilder.build
                                   │
                                   ▼
                              ⑩ 渲染 相机 + 切面 + 十字线 + 方向标记
                                   │
                                   ▼
                              ⑪ 交互 翻层 / 拖洞 / 跳转 / 调窗 / 重置
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        ▼                          ▼                          ▼
 ⑬ 方向标记·HU读数·窗宽窗位   ⑭ 测量（长度/角度/ROI+HU）   ⑮ 导出 PNG
```

---

## ① 启动程序

| 步骤 | 入口 |
| --- | --- |
| 程序入口（EDT 上启动） | `com.zlyd.mpr.App.main` — **[java-vtk]** `App.java:25` |
| 装配主窗口与两个视图 | `com.zlyd.mpr.App.launch` — **[java-vtk]** `App.java:29` |

**载入方式**：**默认不打开任何影像**。影像目录由启动命令/脚本传入：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-app.ps1                 # 不传：由用户在界面里选
powershell -ExecutionPolicy Bypass -File scripts\run-app.ps1 D:\dicom\series  # 传目录：启动即打开
```

| 步骤 | 入口 |
| --- | --- |
| 读取命令行参数（无则空列表） | `com.zlyd.mpr.App.main` — **[java-vtk]** `App.java` → `launch :40` |
| 有目录则载入，否则提示自行选择 | `com.zlyd.mpr.ui.MainFrame.loadFolder` — **[java]** |
| 载入并记为最近目录 | `com.zlyd.mpr.ui.SeriesListPanel.loadDirectory` — **[java]** |

| 步骤 | 入口 |
| --- | --- |
| 主窗口构造（左列表 + 右侧卡片视图） | `com.zlyd.mpr.ui.MainFrame` — **[java]** `MainFrame.java:18` |

---

## ② 选择 DICOM 文件夹

| 步骤 | 入口 |
| --- | --- |
| 点击「选择 DICOM 文件夹…」 | `com.zlyd.mpr.ui.SeriesListPanel.chooseFolder` — **[java]** `SeriesListPanel.java:108` |
| 清空列表并启动后台加载 | `com.zlyd.mpr.ui.SeriesListPanel.loadFolder` — **[java]** `SeriesListPanel.java:119` |

---

## ③ 后台扫描解析

| 步骤 | 入口 |
| --- | --- |
| 异步加载（SwingWorker，回调在 EDT） | `com.zlyd.mpr.ui.SeriesLoader.load` — **[java]** `SeriesLoader.java:34` |
| 扫描 + 分组 + 缩略图编排 | `com.zlyd.mpr.ui.SeriesLoader.scanAndBuild` — **[java]** `SeriesLoader.java:56` |
| 递归扫描目录 | `com.zlyd.mpr.dicom.DicomScanner.scan` — **[java]** `DicomScanner.java:29` |
| 解析单个文件（`IncludeBulkData.NO`，不读像素） | `com.zlyd.mpr.dicom.DicomScanner.readInstance` — **[java]** `DicomScanner.java:53` |

---

## ④ 分组与几何计算

| 步骤 | 入口 |
| --- | --- |
| 按序列 UID 分组并装配 | `com.zlyd.mpr.dicom.SeriesGrouper.group` — **[java]** `SeriesGrouper.java:20` |
| 装配单个序列 | `com.zlyd.mpr.dicom.SeriesGrouper.assemble` — **[java]** `SeriesGrouper.java:35` |
| 几何分析（法向量/排序/层距/校验） | `com.zlyd.mpr.dicom.SeriesGeometryAnalyzer.analyze` — **[java]** `SeriesGeometryAnalyzer.java:28` |

规则：法向量 = 行方向 × 列方向；排序键 = `ImagePositionPatient · 法向量`；层间距取相邻投影差中位数。

---

## ⑤ 生成缩略图

| 步骤 | 入口 |
| --- | --- |
| 取中间层并生成图标 | `com.zlyd.mpr.ui.SeriesLoader.createThumbnail` — **[java]** `SeriesLoader.java:67` |
| 读取 + 缩放编排 | `com.zlyd.mpr.util.ThumbnailFactory.create` — **[java]** `ThumbnailFactory.java:24` |
| 读单张切片并映射为 8 位图 | `com.zlyd.mpr.util.DicomSliceImageReader.read` — **[java]** `DicomSliceImageReader.java:33` |
| 等比缩放到目标尺寸 | `com.zlyd.mpr.util.ImageScaler.scaleToFit` — **[java]** `ImageScaler.java:20` |
| 窗宽窗位取值与回退 | `com.zlyd.mpr.util.WindowLevelDefaults.forSeries` — **[java]** `WindowLevelDefaults.java:26` |

---

## ⑥ 左侧列表展示

| 步骤 | 入口 |
| --- | --- |
| 加载完成回调（EDT 更新 UI） | `com.zlyd.mpr.ui.SeriesListPanel.onLoaded` — **[java]** `SeriesListPanel.java:126` |
| 右键弹出菜单 | `com.zlyd.mpr.ui.SeriesListPanel.showPopupIfNeeded` — **[java]** `SeriesListPanel.java:144` |
| 菜单构建 | `com.zlyd.mpr.ui.SeriesContextMenu.create` — **[java]** `SeriesContextMenu.java:17` |
| 双击回调 | `com.zlyd.mpr.ui.SeriesListPanel.notifySelection` — **[java]** `SeriesListPanel.java:158` |
| 右键「MPR」回调 | `com.zlyd.mpr.ui.SeriesListPanel.notifyMprRequest` — **[java]** `SeriesListPanel.java:165` |

回调接线：`MainFrame.java:35`（双击）、`MainFrame.java:39`（MPR）；拖拽 `SeriesTransferHandler.java:35 / :54`。

---

## ⑦ 打开 2D 单平面阅片（双击 / 拖拽）

| 步骤 | 入口 |
| --- | --- |
| 视图通用入口（异步构建体数据 + 工具条） | `com.zlyd.mpr.mpr.VtkViewPanel.showSeries` — **[java-vtk]** `VtkViewPanel.java:126` |
| 构建完成回调 | `com.zlyd.mpr.mpr.VtkStackView.onVolumeReady` — **[java-vtk]** `VtkStackView.java:62` |
| 绑定事件/键盘/焦点 | `com.zlyd.mpr.mpr.VtkStackView.registerObservers` — **[java-vtk]** `VtkStackView.java:42` |
| 场景装载 | `com.zlyd.mpr.mpr.StackScene.setVolume` — **[java-vtk]** `StackScene.java:52` |
| 翻层 / 调窗 / 平移 / 缩放 / 重置 | `StackScene.stepSlice` `:129`、`adjustWindowLevel` `:146`、`pan` `:153`、`zoom` `:171`、`resetView` `:180` |
| 鼠标移动（含 HU 读数） | `com.zlyd.mpr.mpr.VtkStackView.onMouseMove` — **[java-vtk]** `VtkStackView.java:140` |
| 光标处探测（2D） | `com.zlyd.mpr.mpr.StackScene.probe` — **[java-vtk]** `StackScene.java:86` |

---

## ⑧ 打开 MPR 三视图（右键 → MPR）

| 步骤 | 入口 |
| --- | --- |
| 构建完成回调 | `com.zlyd.mpr.mpr.VtkMprView.onVolumeReady` — **[java-vtk]** `VtkMprView.java:59` |
| 场景装载（三切面 + 取景 + 十字线 + 方向标记 + 窗宽窗位） | `com.zlyd.mpr.mpr.MprScene.setVolume` — **[java-vtk]** `MprScene.java:104` |
| 尺寸/显示变化后重新取景 | `VtkMprView.onViewResized` `:74` → `MprScene.refitCameras` `:250` |

布局：轴位左上 `(0,0.5,0.5,1)`、矢状左下 `(0,0,0.5,0.5)`、冠状右侧整栏 `(0.5,0,1,1)`；视图构建 `MprScene.configureViewports` `:79`、`createSlices` `:88`。

---

## ⑨ 构建体数据（vtkImageData）

| 步骤 | 入口 |
| --- | --- |
| 装配体数据 | `com.zlyd.mpr.mpr.VolumeBuilder.build` — **[java-vtk]** `VolumeBuilder.java:33` |
| 体数据几何 | `com.zlyd.mpr.dicom.VolumeGeometry.of` — **[java]** `VolumeGeometry.java:37` |
| 逐层读取像素（Rescale → HU） | `com.zlyd.mpr.dicom.SliceVoxelReader.readInto` — **[java]** `SliceVoxelReader.java:26` |

索引约定：`i`=列(X)、`j`=行(Y)、`k`=层(Z)；体素数组按 VTK "x 最快"排布。

---

## ⑩ MPR 渲染

| 步骤 | 入口 |
| --- | --- |
| 相机配置（三视图方向 + 填满窗格） | `com.zlyd.mpr.mpr.MprCameraController.configure` — **[java-vtk]** `MprCameraController.java:32` |
| 应用中心点（切面号 + 十字线 + 渲染） | `com.zlyd.mpr.mpr.MprScene.applyCenter` — **[java-vtk]** `MprScene.java:339` |
| 十字线绘制（按视口矩形裁剪、铺满视图区、恒不被遮挡/裁掉） | `MprCrosshairOverlay.update` / `screenFrame` / `gaps` / `apply`（朝相机微偏移 0.5px）；纯数学 `geometry/CrosshairGeometry` + `geometry/ScreenFrame`；`MprScene.applyFrame` 更新后重算裁剪范围 |
| 方向标记刷新 | `com.zlyd.mpr.mpr.MprScene.updateOrientationMarkers` — **[java-vtk]** `MprScene.java:158` |

**显示方向约定**（`com.zlyd.mpr.geometry.MprViewOrientation`，相机与标记共用）

| 视图 | 视线方向 | 屏幕上 | 屏幕下 | 屏幕左 | 屏幕右 |
| --- | --- | --- | --- | --- | --- |
| 轴位 | 脚→头（+Z） | A | P | R | L |
| 矢状 | 患者左→右（−X） | H | F | A | P |
| 冠状 | 前→后（+Y） | H | F | R | L |

---

## ⑪ MPR 交互

| 操作 | 入口（**[java-vtk]**） | 说明 |
| --- | --- | --- |
| 滚轮/↑↓ 翻层 | `VtkMprView.onWheelForward` `:96` / `onWheelBackward` `:101` → `MprScene.stepActivePlane` `:175`（方向系数 `sliceDirectionSign` `:200`） | 前滚：轴位 脚→头（k+1）、冠状 后→前（j−1）、矢状 患者左→右（i−1） |
| 按下左键 | `VtkMprView.onLeftButtonDown` `:106` → `MprScene.isOnHole` `:274` | 十字线模式下：只有落在交点 10px 空白内才进入拖动 |
| 拖动 | `VtkMprView.onMouseMove` `:163` → `MprScene.moveCenter` `:213` | 本视图层号不变、十字线跟随；另两视图换层 |
| 松开左键 | `VtkMprView.onLeftButtonUp` `:120` | 位移 ≤3px 判为单击 → 中心跳到该点 |
| 右键拖动 | `VtkMprView.onRightButtonDown` `:145` → `MprScene.adjustWindowLevel` `:135` | 调窗宽窗位 |
| 键盘 | `VtkMprView.onKeyPressed` `:79` | ↑/↓ 翻层；`R` → `MprScene.resetView` `:239` |

**"拖哪个视图、谁变"**：拖某视图只改该视图平面内的两个索引，第三个（本视图层号）不变；另两视图换层。

---

## ⑫ 核心算法：十字线坐标计算

| 内容 | 入口 |
| --- | --- |
| 算法主体（类注释含含义/留洞/步骤） | `com.zlyd.mpr.geometry.CrosshairGeometry.compute` — **[java]** `CrosshairGeometry.java:46` |
| 单条线的留洞拆分 | `CrosshairGeometry.addLine` — **[java]** `CrosshairGeometry.java:81` |
| 线段值对象 | `com.zlyd.mpr.geometry.CrosshairSegment` |
| 索引 ↔ 患者坐标 | `VolumeGeometry.toWorld` `:55` / `toIndex` `:65` / `center` `:78` |

| 视图 | 竖线（平面） | 横线（平面） | 颜色 |
| --- | --- | --- | --- |
| 轴位 | i（矢状面 X=i） | j（冠状面 Y=j） | 竖=蓝、横=绿 |
| 矢状 | j（冠状面 Y=j） | k（轴位面 Z=k） | 竖=绿、横=红 |
| 冠状 | i（矢状面 X=i） | k（轴位面 Z=k） | 竖=蓝、横=红 |

配色按**切面**划分（同一平面在三个视图里同色）：**矢状面（X=i）= 蓝、冠状面（Y=j）= 绿、轴位面（Z=k）= 红**（`MprCrosshairOverlay.Plane`）。

---

## ⑬ 方向标记（C7）· HU 读数（D6）· 窗宽窗位（E1）

### C7 方向标记

| 步骤 | 入口 |
| --- | --- |
| 方向推导（纯数学：法向/上方/右方/四边标签） | `com.zlyd.mpr.geometry.MprViewOrientation` — **[java]** `MprViewOrientation.java:62 / :70 / :78` |
| 标记绘制（每视图 4 个文本，归一化视口坐标 + 半透明黑底） | `com.zlyd.mpr.mpr.VtkOrientationMarkers` — **[java-vtk]** `VtkOrientationMarkers.java:36 / :65` |
| 装载体数据时刷新 | `MprScene.updateOrientationMarkers` — **[java-vtk]** `MprScene.java:158` |

### D6 HU / 索引读数

| 步骤 | 入口 |
| --- | --- |
| MPR 探测 | `MprScene.probe` `:145` → `MprVolumeProbe.probe` `:29`（取值 `valueAt` `:52`） |
| MPR 鼠标移动刷新 | `VtkMprView.onMouseMove` `:163` / `updateStatus` `:185` |
| 2D 阅片探测 | `StackScene.probe` `:86` → `VtkStackView.onMouseMove` `:140` / `updateStatus` `:181` |

状态栏：`序列 | 中心 i j k | HU=… | 操作提示`（2D 为 `层 n/N`）。

### E1 窗宽窗位预设与联动

| 步骤 | 入口 |
| --- | --- |
| 预设列表 | `com.zlyd.mpr.util.WindowLevelPresets.all` — **[java]** `WindowLevelPresets.java:53` |
| 工具条 | `com.zlyd.mpr.ui.WindowLevelToolbar` — **[java]** `WindowLevelToolbar.java:29 / :40 / :46` |
| MPR 应用/取值/微调 | `MprScene.setWindowLevel` `:123`、`getWindowLevel` `:128`、`adjustWindowLevel` `:135` → `MprWindowLevelController` |
| 2D 应用/取值 | `StackScene.setWindowLevel` `:73`、`getWindowLevel` `:108` |

---

## ⑭ 测量（F1/F2）：长度 / 角度 / 矩形ROI / 椭圆ROI + HU 统计

| 步骤 | 入口 |
| --- | --- |
| 工具条（工具选择 / 清除 / 结果显示） | `com.zlyd.mpr.ui.MeasurementToolbar` — **[java]** `MeasurementToolbar.java:38 / :53 / :63` |
| 测量模型与类型 | `com.zlyd.mpr.geometry.Measurement`、`MeasurementType` |
| 数值计算（长度/角度/面积/形状包含） | `com.zlyd.mpr.geometry.MeasurementCalculator` — **[java]** `:17 / :23 / :48 / :54` |
| ROI 统计（均值/最小/最大/标准差） | `com.zlyd.mpr.geometry.RoiStatistics.of` — **[java]** `RoiStatistics.java:25` |
| 测量控制器（选工具/落点/预览/清空） | `com.zlyd.mpr.mpr.MprMeasurementController` — **[java-vtk]** `setTool :42`、`addPoint :67`、`updatePreview :94`、`clear :117`、`refresh :122` |
| ROI 取样统计（当前层遍历平面内索引） | `MprMeasurementController.computeRoiStatistics` — **[java-vtk]** `:153` |
| 绘制层（折线 + 数值标签） | `com.zlyd.mpr.mpr.MprMeasurementOverlay` — **[java-vtk]** `addMeasurement :78`、`refresh :104`、`buildOutline :168` |
| 文本格式化 | `com.zlyd.mpr.util.MeasurementFormatter.format` — **[java]** `:17` |
| 界面接线 | `VtkMprView.onLeftButtonDown` `:106`（落点）、`updateMeasurementResult` `:140`、`clearMeasurements` `:135`、`onRightButtonUp` `:152`（右键取消未完成） |
| 场景委托 | `MprScene` — `setMeasurementTool :290`、`getLastMeasurement :299`、`addMeasurementPoint :310`、`updateMeasurementPreview :318`、`cancelPending :326`、`clearMeasurements :334` |

**操作**：工具条选「长度 / 角度 / 矩形 ROI / 椭圆 ROI」→ 在任一视图依次点击所需点数（长度/ROI 2 点、角度 3 点）→ 自动生成结果（画线 + 数值标签 + 工具条显示）；右键单击取消未完成；「清除测量」清空。

---

## ⑮ 导出当前视图为 PNG（H1）

| 步骤 | 入口 |
| --- | --- |
| 工具条「导出 PNG」按钮（两个视图共用） | `com.zlyd.mpr.mpr.VtkViewPanel` — **[java-vtk]** `getToolbarPanel :72`、`createExportButton :82`、`exportPng :88` |
| 默认文件名 | `VtkViewPanel.defaultExportName :78`（`VtkMprView :69` = "mpr"、`VtkStackView :57` = "slice"） |
| 导出实现（`vtkWindowToImageFilter` + `vtkPNGWriter`） | `com.zlyd.mpr.util.VtkImageExporter.exportPng` — **[java-vtk]** `:22` |

---

## ⑯ 斜切（C3）：光标坐标系 / 旋转 / 回正

> 行为规范（含 **90° 三张变化表**、中心点钉住、up 连续性 + 回正、状态栏字段）见 **`MPR斜切设计.md`**。

| 步骤 | 入口 |
| --- | --- |
| 场景状态与编排（装载、旋转、回正、重置、翻层、移中心） | **[java-vtk]** `MprScene` — `setVolume :91`、`probe :130`、`stepActivePlane :148`、`moveCenter :183`、`rotate :206`、`alignRig :219`、`resetView :228`、`refitCameras :238`、`isOnHole :273`、`applyFrame :342`、`activeView :352`、`centerDisplay :359`、`isOnCrosshair :366`、`updateOrientationMarkers :404` |
| 切面显示：每视图一套 `vtkImageResliceMapper + vtkPlane`（支持斜切、实时） | **[java-vtk]** `mpr/MprSlicePlaneActors` — 构造 `:31`、`setInput :61`、`update :79`、`slices :91` |
| 相机：机架（视线/上方）+ 交点钉住 + 旋转不变取景 | **[java-vtk]** `mpr/MprCameraController` — `reset :44`、`configure :58`、`captureAnchors :74`、`refitVolume :91`、`fitScale :108`、`focalPoint :118` |
| 十字线绘制（frame 版；配色：矢状面蓝/冠状面绿/轴位面红；线段按**视口矩形**裁剪铺满视图区） | **[java-vtk]** `mpr/MprCrosshairOverlay` — 构造 `:68`、`update :95`（返回线段供命中判定）、`screenFrame`、`gaps`、配色 `:28/:30/:32` |
| 交互：拖线旋转 / 拖洞移动 / 滚轮翻层 / `R` 重置 / `A` 回正 / 状态栏 | **[java-vtk]** `mpr/VtkMprView` — `onKeyPressed :94`、`onWheelForward :118`、`onLeftButtonDown :128`、`onLeftButtonUp :152`、`updateRotation :171`、`onMouseMove :228`、`refreshMeasurementAvailability :257`、`updateStatus :261` |
| HU 读数（斜切下仍准确：原始体数据世界坐标三线性采样） | **[java-vtk]** `mpr/MprVolumeProbe.probe :32`、`sampleTrilinear :46`、`valueAt :73` |
| 纯数学层（均有单测） | **[java]** `geometry/MprCursorFrame`、`MprViewRig`、`VolumeBox`、`CrosshairGeometry`、`Vectors` |

**斜切下的测量**：暂不支持（`frame` 非轴对齐时工具条禁用并提示；见 `MeasurementToolbar.setOblique`）。
**旋转拖动中的取景**：`beginRotation` 冻结三视图 `parallelScale`，**松手后继续保持冻结**（画面尺寸不随旋转改变，允许斜切面四角被裁）；`R` 重置、`A` 回正、改变窗口尺寸才会重新取景（见 `MPR斜切设计.md` D1）。
**屏幕上方（up）的取法**：`MprCameraController.chooseUp` 把该视图**上一次的 up 连续投影**到新平面（`MprViewRig.projectOntoPlane`），避免"先转某视图 90° 再碰另一个视图时它瞬间歪掉"；装载/重置用机架轴。

**清理缓存 / 重新打开**：工具条第 1 行「**清理缓存**」按钮，或每次载入序列前自动清理（`VtkViewPanel.onBeforeVolumeLoad` → `VtkMprView.clearVolume`）→ 释放体数据引用与测量、回到未载入状态，可反复重新打开 MPR。实现见 `MprScene.clearVolume`、`MprSlicePlaneActors.release`（把 mapper 输入换成 1×1×1 占位体数据，**不能用 `RemoveAllInputs()`**——在 mapper 上会抛 C++ 异常）、`MprCrosshairOverlay.release`、`MprMeasurementController.release`。实测：清理并 GC 后占用从 243.9MB 降到 10.1MB。
**离屏校验**：`M3ReopenCheck`（载入→清理→再载入→再清理→再载入全部 PASS；会短暂弹窗，因为 MPR 的 canvas 必须已显示才有有效 GL 上下文，故不纳入无界面一键校验）。
**离屏验收**：`M3ResliceCheck`（§4 三表逐条断言 + 交点钉住 + 十字线屏幕对齐 + 被转视图冻结 + PNG 导出）；`M3RigCheck`（累积旋转下 C 钉住 / 相机冻结 / 相机正对平面）。

---

---

## 附录 A：类职责一览（SRP）

| 类 | 单一职责 |
| --- | --- |
| `dicom.DicomScanner` | 扫描目录、解析元数据 |
| `dicom.SliceInfo` / `SeriesAttributes` / `PixelAttributes` / `DicomInstance` | 数据载体 |
| `dicom.SeriesGrouper` | 按 UID 分组并装配 |
| `dicom.SeriesGeometryAnalyzer` | 几何计算与校验 |
| `dicom.VolumeGeometry` | 索引 ↔ 患者坐标（纯数学） |
| `dicom.SliceVoxelReader` | 读单层像素为 HU |
| `util.WindowLevelDefaults` / `WindowLevel` / `WindowLevelPresets` | 取值回退、预设 |
| `util.MeasurementFormatter` / `VtkImageExporter` | 文本格式化、导出 |
| `util.ImageScaler` / `DicomSliceImageReader` / `ThumbnailFactory` | 缩放、读图、缩略图编排 |
| `ui.WindowLevelToolbar` / `MeasurementToolbar` | 两个工具条 |
| `ui.SeriesListPanel` / `SeriesLoader` / `SeriesContextMenu` / `SeriesEntry` / `SeriesListCellRenderer` / `SeriesTransferHandler` | 列表交互、后台加载、菜单、条目与渲染、拖拽 |
| `mpr.VtkViewPanel` | 视图公共基类（画布/工具条/状态栏/焦点/异步构建模板/导出） |
| `mpr.StackScene` | 单平面阅片场景与操作 |
| `mpr.MprScene` | 三视图状态与编排 |
| `mpr.MprCameraController` | 三视图相机与取景 |
| `mpr.MprViewMapper` | 屏幕 ↔ 世界、视图命中 |
| `mpr.MprCrosshairOverlay` | 十字线绘制 |
| `mpr.VtkOrientationMarkers` | 方向标记绘制 |
| `mpr.MprMeasurementController` | 测量状态与计算调度 |
| `mpr.MprMeasurementOverlay` | 测量绘制与标签 |
| `mpr.MprWindowLevelController` | 窗宽窗位状态与联动 |
| `mpr.MprVolumeProbe` | 体素索引换算与取值 |
| `geometry.MprCursorFrame` | **光标坐标系（三正交平面 + 交点 C，纯数学）** |
| `geometry.MprViewRig` | **各视图机架取法（连续性/回正，纯数学）** |
| `geometry.CrosshairGeometry` | **十字线坐标计算（纯数学）** |
| `geometry.MprViewOrientation` | **视图方向约定（纯数学）** |
| `geometry.MeasurementCalculator` / `RoiStatistics` | **测量与统计（纯数学）** |
| `geometry.CrosshairSegment` / `VoxelProbe` / `Measurement` / `MeasurementType` | 值对象 |
| `mpr.VolumeBuilder` | 装配 vtkImageData |

## 附录 B：验收与自检入口

| 目的 | 命令/入口 |
| --- | --- |
| 单元测试（27 个） | `mvn test` |
| 数据层报告（M1） | `mvn -q exec:java "-Dexec.mainClass=com.zlyd.mpr.m1.M1Main" "-Dexec.args=<目录>"` |
| 缩略图校验（M2） | `mvn -q exec:java "-Dexec.mainClass=com.zlyd.mpr.m2.M2ThumbnailCheck" "-Dexec.args=<目录>"` |
| 体数据/单平面校验 | `com.zlyd.mpr.m2.M2VolumeCheck`（`-Pvtk`） |
| MPR 校验（离屏 + 方向断言 + HU 采样 + 测量示例） | `com.zlyd.mpr.m2.M3MprCheck`（`-Pvtk`） |
| 启动 GUI | `powershell -ExecutionPolicy Bypass -File scripts\run-app.ps1 [影像目录]` |

> 注意：`mvn test`（默认 profile）会清理 `-Pvtk` 的编译产物；运行 GUI/离屏校验前先执行一次 `mvn -Pvtk -DskipTests compile`（`run-app.ps1` 已包含）。
