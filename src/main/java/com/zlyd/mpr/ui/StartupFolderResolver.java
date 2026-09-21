package com.zlyd.mpr.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 启动时默认载入目录的解析：在基准目录下查找名字以指定前缀开头的**子目录**，按名字升序取第一个。
 *
 * <p>用于启动程序时自动载入当前文件夹下形如 {@code 3120221229008001} 的影像目录；
 * 找不到或读取失败时返回空，交由调用方保持空列表。</p>
 */
public final class StartupFolderResolver {

    private static final Logger LOG = LogManager.getLogger(StartupFolderResolver.class);

    /** 默认目录名前缀。 */
    public static final String DEFAULT_PREFIX = "3";

    private StartupFolderResolver() {
    }

    /**
     * 用默认前缀解析。
     */
    public static Optional<Path> resolve(Path baseDir) {
        return resolve(baseDir, DEFAULT_PREFIX);
    }

    /**
     * 在基准目录下查找以指定前缀开头的子目录。
     *
     * @param baseDir 基准目录（通常为程序当前工作目录）
     * @param prefix 目录名前缀
     * @return 排序后的第一个匹配目录；无匹配返回空
     */
    public static Optional<Path> resolve(Path baseDir, String prefix) {
        if (baseDir == null || prefix == null || prefix.isEmpty() || !Files.isDirectory(baseDir)) {
            return Optional.empty();
        }
        try (Stream<Path> children = Files.list(baseDir)) {
            return children
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .min(Comparator.comparing(path -> path.getFileName().toString()));
        } catch (IOException e) {
            LOG.warn("扫描默认影像目录失败: {} ({})", baseDir, e.getMessage());
            return Optional.empty();
        }
    }
}
