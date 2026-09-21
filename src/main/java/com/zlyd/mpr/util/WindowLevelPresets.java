package com.zlyd.mpr.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 窗宽窗位预设（E1）。数值取自常用阅片窗口。
 */
public final class WindowLevelPresets {

    /** 一个预设项：名称 + 窗宽窗位。 */
    public static final class Preset {

        private final String name;
        private final WindowLevel windowLevel;

        public Preset(String name, WindowLevel windowLevel) {
            this.name = name;
            this.windowLevel = windowLevel;
        }

        public String getName() {
            return name;
        }

        public WindowLevel getWindowLevel() {
            return windowLevel;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final List<Preset> PRESETS;

    static {
        List<Preset> presets = new ArrayList<>();
        presets.add(new Preset("软组织 (400/40)", new WindowLevel(400.0, 40.0)));
        presets.add(new Preset("肺 (1500/-600)", new WindowLevel(1500.0, -600.0)));
        presets.add(new Preset("骨 (2000/400)", new WindowLevel(2000.0, 400.0)));
        presets.add(new Preset("脑 (80/40)", new WindowLevel(80.0, 40.0)));
        presets.add(new Preset("纵隔 (350/50)", new WindowLevel(350.0, 50.0)));
        presets.add(new Preset("肝 (150/60)", new WindowLevel(150.0, 60.0)));
        presets.add(new Preset("腹部 (400/60)", new WindowLevel(400.0, 60.0)));
        PRESETS = Collections.unmodifiableList(presets);
    }

    private WindowLevelPresets() {
    }

    public static List<Preset> all() {
        return PRESETS;
    }
}
