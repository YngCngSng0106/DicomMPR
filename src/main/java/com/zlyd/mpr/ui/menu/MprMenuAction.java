package com.zlyd.mpr.ui.menu;

/**
 * 环形菜单的全部功能项：名称用于按钮文字与提示，图标为内置矢量图。
 */
public enum MprMenuAction {

    WINDOWING("窗宽窗位", MenuGlyph.CONTRAST, "windowing"),
    ZOOM("放大/缩小", MenuGlyph.ZOOM, "zoom"),
    MOVE("移动", MenuGlyph.MOVE, "move"),
    CROSSHAIR("交叉定位", MenuGlyph.CROSSHAIR, "cross"),
    INVERT("反白", MenuGlyph.INVERT, "invert"),
    ORIGINAL("还原", MenuGlyph.RESET, "original"),
    DISTANCE_MEASUREMENT("长度测量", MenuGlyph.LINE, "ditanceMeasurement"),
    ANGLE_MEASUREMENT("角度测量", MenuGlyph.ANGLE, "angleMeasurement"),
    SAVE_IMAGE("保存影像", MenuGlyph.SAVE, "saveImg"),
    SURVEY_SIGN("测量/标记", MenuGlyph.PEN, "surveySign"),

    WINDOW_LUNG("肺窗", MenuGlyph.LUNG, "lung"),
    WINDOW_MEDIASTINUM("纵隔窗", MenuGlyph.MEDIASTINUM, "mediastinum"),
    WINDOW_ABDOMEN("腹部窗", MenuGlyph.ABDOMEN, "abdomen"),
    WINDOW_BRAIN("头部窗", MenuGlyph.BRAIN, "head"),
    WINDOW_BONE("骨骼窗", MenuGlyph.BONE, "bone"),
    WINDOW_OTHER("其他窗", MenuGlyph.DOTS, "other"),
    WINDOW_ORIGINAL("原始窗", MenuGlyph.RESET, "original"),
    WINDOW_SET("自定义", MenuGlyph.GEAR, "winSet"),

    SAVE_JPG("JPG", MenuGlyph.DOCUMENT_J, "saveJpg"),
    SAVE_BMP("BMP", MenuGlyph.DOCUMENT_B, "saveBmp"),
    SAVE_PATH("保存路径", MenuGlyph.FOLDER, "savePath"),

    SURVEY_ARROW("箭头", MenuGlyph.ARROW, "arrow"),
    SURVEY_POLYGON("多边形", MenuGlyph.POLYGON, "irreular"),
    SURVEY_POLYLINE("折线", MenuGlyph.POLYLINE, "polyline"),
    SURVEY_CLOSED_POLYLINE("闭合折线", MenuGlyph.CLOSED_POLYLINE, "unClosePoly"),
    SURVEY_CIRCLE("圆", MenuGlyph.CIRCLE, "round"),
    SURVEY_RECT("矩形", MenuGlyph.RECT, "rect"),
    SURVEY_TEXT("文本", MenuGlyph.TEXT, "text"),
    SURVEY_CLEAR_ALL("清除所有", MenuGlyph.CLEAR, "cleanAll");

    private final String displayName;
    private final MenuGlyph glyph;
    private final String iconName;

    MprMenuAction(String displayName, MenuGlyph glyph, String iconName) {
        this.displayName = displayName;
        this.glyph = glyph;
        this.iconName = iconName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public MenuGlyph getGlyph() {
        return glyph;
    }

    /**
     * 图标资源前缀（对应 {@code resources/button/<iconName>_defort.png} 与
     * {@code <iconName>_yellow.png}）。
     */
    public String getIconName() {
        return iconName;
    }
}
