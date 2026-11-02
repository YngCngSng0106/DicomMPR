# MPR2D 右键环形菜单：功能与移植说明

本文档归纳“选择序列并进入 MPR2D 后，在 MPR2D 图像上右键弹出菜单”的交互方式、菜单样式、功能项、状态流转与依赖清单，供移植到其他项目时参考。

---

## 1. 触发流程

MPR2D 面板为 `unitedpacs.function.mpr.mpr2d.MPR2DPanel`，其内部注册 `DefaultListener` 统一监听 XY / XZ / YZ 三个视图。

- 鼠标按下：调用 `workMenu(e)`，启动 300ms 定时器（`minPressTime = 300`）。
- 鼠标释放：调用 `showMenu(comp, e)` 后调用 `handleMenu()`。

不同按键的行为：

| 操作 | 判定 | 结果 |
|---|---|---|
| 左键短按 | `BUTTON1` 且未长按 | 普通点选 / 定位 |
| 左键长按 (>300ms) | `BUTTON1` | `menu.startZoom()`，进入放大/缩小 |
| 中键长按 | `BUTTON2` | `menu.startMove()`，进入移动 |
| 右键长按 (>300ms) | `BUTTON3` | `menu.startWindowing()`，进入窗宽/窗位调节 |
| 右键短按释放 | 右键且 `!isLongPress` | 弹出环形菜单 |

菜单显示条件见 `verfiyMenu`：右键、菜单非空、当前未显示、且不是长按。

关键代码位置：

- `MPR2DPanel.java:721` `workMenu`
- `MPR2DPanel.java:754` `showMenu`
- `MPR2DPanel.java:789` `verfiyMenu`
- `MPR2DPanel.java:797` `handleMenu`

> 长按的“功能触发”与短按的“弹菜单”互斥：长按后释放不会弹菜单，而是结束当前临时操作。

---

## 2. 菜单的类型与整体风格

### 2.1 不是 JPopupMenu，而是透明浮层

菜单类为 `menuOnly.Menu_12Update extends JPanel`，并非标准 `JPopupMenu`。

- 固定尺寸 `350 × 340`，绝对布局 `setLayout(null)`。
- `setOpaque(false)` 背景透明，只绘制“图标按钮”。
- 由 `AbsOperationMPRPanel.setMenu()` 以 `JLayeredPane.POPUP_LAYER` 加入 `layPane`，浮于三视图之上。
- 初始 `setVisible(false)`，右键时按光标位置显示。

```java
// AbsOperationMPRPanel.java:51
public void setMenu(Menu_12Update menu) {
    this.menu = menu;
    menu.setOpaque(false);
    menu.setVisible(false);
    layPane.add(menu, JLayeredPane.POPUP_LAYER);
}
```

### 2.2 环形（放射状）布局

主菜单与各子菜单都采用**围绕中心、环形排布的大图标按钮**，中心区域留空。展开某一类功能时，隐藏其余按钮，只显示该类按钮，视觉上形成“换圈”的效果。

主菜单的中心约在 `(175, 170)`，10 个 56×56 图标绕中心一圈。

### 2.3 按钮外观（ImageButton）

按钮类 `menuOnly.ImageButton extends JComponent`：

- 固定尺寸 `56 × 56`。
- 三种图片状态：
  - 默认：`*_defort.png`
  - 悬停：`*_yellow.png`（黄色高亮）
  - 按下：默认使用 `down_b`（多数场景传入与默认图相同的图）
- “特殊按钮”模式（`special=true`，默认）：点击即执行，不保持选中状态。
- 纯图标，无 Swing 边框/LookAndFeel 背景；悬停通过换图实现高亮。
- 可选文字叠加：`Dialog`、`BOLD`、14 号、白色、绘制位置 `(16, 34)`（仅 `str != null` 时）。

```java
// ImageButton.java:133
public void paintComponent(Graphics g) {
    if (moveing) {
        g.drawImage(PassBy, 0, 0, this);
    } else if (down) {
        g.drawImage(DOWNimage, 0, 0, this);
    } else {
        g.drawImage(UPimage, 0, 0, this);
    }
    ...
    g2.setFont(new Font("Dialog", Font.BOLD, 14));
    g2.setColor(new java.awt.Color(255, 255, 255));
    if (str != null) g2.drawString(str, 16, 34);
}
```

### 2.4 弹窗定位

菜单以鼠标点为中心显示，并做边界收敛，保证不出 `layPane`：

```java
// MPR2DPanel.java:767
Point location = e.getPoint();
location = SwingUtilities.convertPoint(comp, location, MPR2DPanel.this);
location.translate(-menu.getWidth() / 2, -menu.getHeight() / 2);
// 再分别对左/右/上/下越界做平移修正
menu.setLocation(location);
menu.setVisible(true);
```

### 2.5 提示与国际化

每个按钮通过 `setTip()` 设置 Tooltip，文案来自 `menuOnly.JavaI18N`（资源 `resources/I18N_zh_CN.properties` 等）。

| 资源键 | 中文 |
|---|---|
| Windowing | 窗宽窗位 |
| Zoom | 放大/缩小 |
| Move | 移动 |
| Cross | 交叉定位 |
| Invert | 反白 |
| Original | 还原 |
| DitanceMeasurement | 长度测量 |
| AngleMeasurement | 角度测量 |
| SaveImg | 保存影像 |
| SaveToJPG | 以JPG格式保存影像 |
| SaveToBMP | 以BMP格式保存影像 |
| SavePath | 保存路径 |
| SurveySign | 测量/标记 |
| Arrow | 箭头 |
| Polygon | 多边形 |
| Polyline | 折线 |
| Round | 圆 |
| Text | 文本 |
| Rect | 矩形 |
| ClosePoly | 闭合折线 |
| CleanAll | 清除所有 |
| RealSize | 真实大小 |
| Crosswise | 横向适应 |
| Lengthwise | 纵向适应 |
| Allwise | 全部适应 |
| Custom | 自定义 |

### 2.6 光标样式

进入操作模式时切换自定义光标（`Toolkit.createCustomCursor`，热点 `(8,8)`）：

| 模式 | 光标资源 |
|---|---|
| 放大/缩小 | `/button/zoom.png` |
| 移动 | `/button/hand.gif` |
| 窗宽/窗位 | `/button/windowing.gif` |
| 交叉定位/退出 | 系统默认光标 |

XY / XZ / YZ 两个操作面板（`myPnlA`、`myPnlB`）同时切换光标。

---

## 3. 主菜单功能项

右键 `rightChick()` → `displayMainMenu(true)` + `showSonPane(false)`，显示以下 10 个主项。

| 图标资源前缀 | Tooltip | 左键 / 单击 | 右键 | 调用 |
|---|---|---|---|---|
| `windowing` | 窗宽窗位 | 进入调窗模式 | 展开预设窗子菜单 | `startWindowing()` |
| `zoom` | 放大/缩小 | 进入缩放模式 | 同左键（无独立子菜单） | `startZoom()` |
| `move` | 移动 | 进入拖动平移 | 同左键 | `startMove()` |
| `cross` | 交叉定位 | 取消操作、恢复默认光标、隐藏菜单 | 同左键 | `startCross()` |
| `win_invert` | 反白 | 图像反色 | 同左键 | `imageOperation.inverted()` |
| `original` | 还原 | 还原图像；MPR2D 下先 `initImagePoint()` | 同左键 | `imageOperation.revert()` |
| `ditanceMeasurement` | 长度测量 | 直线测量工具 | 同左键 | `TOOL_LINE` |
| `angleMeasurement` | 角度测量 | 角度测量工具 | 同左键 | `TOOL_OPENANGLE` |
| `imgSave` | 保存影像 | 直接保存 JPEG | 展开保存子菜单 | `SaveImage("JPEG")` |
| `surveySign` | 测量/标记 | 展开标记子菜单 | 展开标记子菜单 | `showSurveySign(true)` |

主菜单按钮绝对坐标（对应 `init()`）：

```text
(124, 69)  windowing     (176, 69)  move
( 81,100)  zoom          (219,100)  ditanceMeasurement
( 65,150)  original      (235,150)  angleMeasurement
( 81,200)  cross         (219,200)  surveySign
(124,231)  win_invert    (176,231)  imgSave
```

> 说明：`zoom` 虽预留了缩放子菜单（真实大小/横向/纵向/全部适应），但未绑定右键展开，实际只执行缩放；该子菜单默认隐藏。

---

## 4. 子菜单功能项

### 4.1 窗宽/窗位预设

右键点击 `windowing` 时展开。预设数据来自 `xml/test.xml`，按当前图像 Modality 读取对应节点。

- 预设按钮：`lung`、`mediastinum`、`abdomen`、`brain`、`bone`、`other`。
- 附加按钮：`win_original`（恢复原始窗宽窗位，`windowLevelRevert`）、`win_set`（打开自定义 W/L 对话框 `WinCenSet`）。
- 展开前会重新 `getWinStr()` 并刷新各按钮 Tooltip，格式为 `部位:w/l`。

坐标：

```text
(124,  7)  win_other
( 74, 33)  win_abdomen   (174, 33)  win_original
( 54, 82)  win_lung      (194, 82)  win_set
( 74,131)  win_bone      (174,131)  win_brain
(124,153)  win_mediastinum
```

`xml/test.xml` 结构示例（按模态分组，节点名即部位名，值为 `w窗宽/l窗位`）：

```xml
<config>
    <CT>
        <肺窗>w2000/l-600</肺窗>
        <骨骼>w2000/l500</骨骼>
        <腹部>w500/l50</腹部>
        <头部>w100/l40</头部>
        <纵隔>w350/l50</纵隔>
        <other>w300/l35</other>
    </CT>
    <CR> ... </CR>
    <MR> ... </MR>
</config>
```

代码读取方式：取该模态下 `(子节点数-1)/2` 个预设，逐个读取“节点名 + 值”。

> 注意：按钮名称与实际读取索引存在错位（如 `jb_win_mediastinum` 取索引 1，而 CT 索引 1 为“骨骼”）。Tooltip 与动作使用同一索引，因此内部一致，但图标语义可能与实际预设不符。移植时建议改为“部位名 → 预设”映射，避免错位。

### 4.2 保存影像

右键点击 `imgSave` 展开。

| 按钮 | 行为 |
|---|---|
| `saveJpg` | 保存当前视图为 JPEG |
| `saveBmp` | 保存当前视图为 BMP |
| `savePath` | 选择并保存输出目录，写入 `xml/directores.properties` 的 `path` |
| `save_2` / `save_3` / `save_4` | 空白占位按钮（`defort.png`），无实际功能 |

坐标：

```text
(176,179)  saveJpg
(129,206)  saveBmp      (225,206)  savePath
(129,260)  save_4       (225,260)  save_3
(176,283)  save_2
```

保存实现：取当前 `AnnotationUnitedPacsSingleImagePanel.getImage()` 的 `RenderedImage`，通过 JAI `filestore` 写出；路径默认 `./时间戳`。

### 4.3 测量/标记

点击 `surveySign` 展开（左右键均可）。

| 按钮 | Tooltip | 工具常量 |
|---|---|---|
| `arrow` | 箭头 | `TOOL_ARROW` |
| `irreular` | 多边形 | `TOOL_POLYGON` |
| `polyline` | 折线 | `TOOL_PENCIL` |
| `unClosePoly` | 闭合折线 | `TOOL_CLOSEPENCIL` |
| `round` | 圆 | `TOOL_OVAL` |
| `rect` | 矩形 | `TOOL_RECT` |
| `text` | 文本 | 打开 `TextJDialog` |
| `cleanAll` | 清除所有 | `deleteAllShape()` |

坐标：

```text
(219,130)  arrow
(169,150)  irreular     (269,150)  unClosePoly
(149,200)  polyline     (289,200)  cleanAll
(169,250)  round        (269,250)  rect
(219,270)  text
```

工具切换逻辑：遍历 `mprPanel.getSourcePanelVec()`，对每个视图 `setOption(operation)`，并把光标恢复为默认。

---

## 5. 状态流转与显示控制

`Menu_12Update` 用两组方法控制可见性：

- `displayMainMenu(flag)`：控制 10 个主按钮。
- `showSonPane(flag)`：同时控制四类子菜单（窗、缩放、保存、标记）。
- `showWinMenu / showZoomMenu / showSaveImgMenu / showSurveySign`：分别控制各子菜单。

流转：

```text
初始化            → showSonPane(false)
右键图像           → rightChick() = displayMainMenu(true) + showSonPane(false)
右键 windowing     → showWinMenu(true) + displayMainMenu(false)
右键 imgSave       → showSaveImgMenu(true) + displayMainMenu(false)
单击 surveySign    → showSurveySign(true) + displayMainMenu(false)
win_set            → 打开 WinCenSet，关闭后回到主菜单
执行任意功能        → inner.setVisible(false) 隐藏菜单
```

由于是透明 `JPanel` 浮层而非 `JPopupMenu`，**没有系统级自动关闭**；主要依赖“选中功能后隐藏”和“再次右键时 `setVisible(false)`”来收起。移植时建议补充点击空白处/ESC 关闭。

---

## 6. 移植依赖清单

### 6.1 必须移植的类

| 类 | 作用 |
|---|---|
| `menuOnly.Menu_12Update` | 环形菜单主体 |
| `menuOnly.ImageButton` | 图标按钮组件（三态 + 特殊点击模式） |
| `menuOnly.JavaI18N` | Tooltip 国际化 |
| `menuOnly.WinCenSet` | 自定义 W/L 对话框（可选） |
| `unitedpacs.util.ScreenViewComponentLocation` | WinCenSet 居中定位（可选） |

### 6.2 图像资源

均位于 `resources/button/`，每个功能一组 `*_defort.png`（默认）+ `*_yellow.png`（悬停），尺寸 56×56：

```text
windowing invert original zoom trueSize crosswise lengthwise allwise
move cross ditanceMeasurement angleMeasurement saveImg saveJpg saveBmp
savePath surveySign arrow irreular polyline round text rect cleanAll
unClosePoly lung mediastinum abdomen head bone other winSet defort yellow
```

光标资源：`zoom.png`、`hand.gif`、`windowing.gif`。

### 6.3 配置文件

- `xml/test.xml`：窗宽窗位预设（按 Modality 分组）。
- `xml/directores.properties`：保存路径（键 `path`）。

### 6.4 运行时依赖

- 图像操作：`unitedpacs.function.MPROperation`（windowing/zoom/move/revert/inverted/windowLevelRevert/deleteAllShape/cancelOperation 等）。
- 面板抽象：`unitedpacs.function.mpr.AbsOperationMPRPanel`、`AbsMPRPanel`。
- 图像面板与工具常量：`UnitedPacsDrawImagePanel`（`TOOL_LINE`、`TOOL_OPENANGLE`、`TOOL_ARROW`、`TOOL_OVAL`、`TOOL_POLYGON`、`TOOL_PENCIL`、`TOOL_CLOSEPENCIL`、`TOOL_RECT`）、`AnnotationUnitedPacsSingleImagePanel`。
- 文本标注：`unitedpacs.shape.TextJDialog`。
- 第三方库：`javax.media.jai`（JAI filestore 保存图片）、`nu.xom`（XML 解析）、`log4j`。

---

## 7. 移植建议

### 7.1 解耦业务接口

原实现把菜单与 `MPROperation`、`MPR2DPanel` 强绑定。建议在目标项目定义动作接口，菜单只依赖接口：

```java
public interface MprMenuActions {
    void windowing();
    void zoom();
    void move();
    void cancelOperation();
    void invert();
    void revert();
    void windowLevelRevert();
    void applyWindow(double w, double l);
    void setTool(int tool);
    void deleteAllShape();
    void openTextDialog();
    void saveImage(String format);
    void chooseSavePath();
    void openWindowPresetDialog();
}
```

`Menu_12Update` 的构造函数改为接收 `MprMenuActions` + 目标容器 + 屏幕尺寸/坐标，不再 `instanceof MPR2DPanel` 判断。

### 7.2 宿主挂载与事件

在目标图像面板中复刻以下三段逻辑即可：

1. 创建菜单，`setOpaque(false)`，加入 `JLayeredPane.POPUP_LAYER`，初始隐藏。
2. 监听鼠标按下/释放：短按右键弹菜单，长按左/中/右键进入缩放/移动/调窗。
3. 弹菜单时按光标居中并做边界收敛。

### 7.3 UI 风格可替换点

- 图标尺寸：`ImageButton.defaultSize`（默认 56），布局坐标需同步缩放。
- 高亮色：替换 `*_yellow.png` 即可。
- 菜单整体尺寸：`setSize(350, 340)` 与各 `setBounds` 坐标同步调整。
- 如需标准菜单风格，可将环形浮层替换为 `JPopupMenu` + `JMenuItem`，功能项与状态机可原样保留。

### 7.4 已知问题（移植时建议一并修正）

1. 窗宽窗位预设索引与图标命名错位，建议改为按部位名映射。
2. 缩放子菜单已实现但未启用，`showZoomMenu` 实际不被右键触发。
3. 保存子菜单存在 3 个空白占位按钮，无功能。
4. 菜单无自动关闭（缺少点击空白/ESC 收起）。
5. 代码中仍有 `ex.printStackTrace()` / `System.out`，不符合规范，建议改为日志。
6. `Windowing / SaveImage` 等路径与配置依赖工作目录相对路径（`./xml/...`），移植时建议改为可配置绝对路径或注入。
```
