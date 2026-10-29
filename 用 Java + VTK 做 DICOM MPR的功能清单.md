用 Java + VTK 做 DICOM MPR，我按模块列一份功能清单（★=MVP 必需，☆=进阶）：

A. DICOM 数据层
★ 读取 DICOM 文件/目录/多帧序列，解析全部标签
★ 按 SeriesInstanceUID 分组，按 ImagePositionPatient 沿法向量排序切片
★ 建立 IJK↔LPS 仿射矩阵（Orientation/Position/PixelSpacing）
★ Rescale Slope/Intercept 还原 HU 真实值
★ 处理压缩传输语法（RLE/JPEG/JPEG2000，经 GDCM）
☆ 多模态支持（CT/MR/US）、多序列并存、缺失切片/层间距不一致告警
☆ 本地文件 / DICOMweb(WADO-RS) / PACS(DIMSE C-GET) 接入

B. 体数据构建与预处理
★ 由序列构建 vtkImageData 三维体数据
★ 体数据缓存与内存管理（大序列懒加载/流式）
☆ 等体素重采样（消除斜切阶梯伪影）
☆ 多帧/4D（时间序列）、PET-CT 融合配准

C. MPR 渲染核心
★ 横断/冠状/矢状三正交视图（vtkImageReslice）
★ 三视图十字线联动（vtkResliceCursor + Widget）
★ 任意斜切（oblique）
★ 插值方式选择（最近邻/三线性/三次）
☆ 厚层 MPR（Slab 厚度可调）
☆ MIP / MinIP / AvIP 投影
☆ CPR 曲面重建、参考线、方向标记（orientation marker）

D. 交互
★ 鼠标滚轮翻层、平移、缩放
★ 十字线拖拽平移/旋转（旋转 plane）
★ 窗宽窗位调节、重置
★ 在任一视图定位后其余两视图同步跳转
☆ 鼠标功能绑定预设/键盘快捷键
☆ 光标位置与 HU 值实时读数

E. 显示与窗宽窗位
★ 窗宽窗位预设（肺/骨/软组织/脑等）
★ 灰度映射、反色、物理值标尺
★ 叠加信息（患者/序列/层位/层厚/窗值）
☆ 伪彩/LUT、VOI LUT、DICOM Presentation State(GSPS)

F. 测量与标注
★ 距离、角度、矩形/椭圆 ROI
★ ROI 内 HU 统计（均值/最大/最小/标准差）
☆ 探针、文本/箭头标注、自由多边形 ROI
☆ 标注保存/加载（自定义或 DICOM SR）

G. 3D 与高级后处理
☆ 体绘制 VR（vtkGPUVolumeRayCastMapper）+ 传输函数
☆ 面绘制（Marching Cubes）、3D 裁剪/切割
☆ 分割结果叠加、图像滤波/增强

H. I/O 与导出
★ 导出当前 MPR 视图为图片（PNG/JPEG/BMP）
☆ DICOM 二次捕获(SC)/视频录制、测量报告导出、打印

I. 系统与非功能
★ 布局管理（1×3、2×2、含 3D 的四视图）
★ 异常处理与日志（log4j）、线程模型
☆ 配置持久化、国际化、多屏/HiDPI
☆ 性能：GPU 纹理、多线程、降采样预览

J. Java + VTK 特有工程点（容易漏）
VTK Java 绑定（vtkJava）通过 JNI 桥接，注意 JVM Native 内存回收与 GC
GUI 用 Swing（vtkCanvas，AWT）或 JavaFX 嵌入渲染窗口的方案选型
DICOM 读取建议 GDCM/DCMTK；ITK Java 绑定较弱，图像处理需评估
Maven/Gradle 打包与各平台 native 库分发