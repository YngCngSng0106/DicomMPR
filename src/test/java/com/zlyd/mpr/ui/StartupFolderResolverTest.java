package com.zlyd.mpr.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link StartupFolderResolver} 的启动默认目录解析测试。
 */
class StartupFolderResolverTest {

    @TempDir
    Path baseDir;

    @Test
    void shouldPickFirstDirectoryStartingWithPrefix() throws IOException {
        Files.createDirectory(baseDir.resolve("3120221229008001"));
        Files.createDirectory(baseDir.resolve("3abc"));
        Files.createDirectory(baseDir.resolve("2other"));

        Optional<Path> resolved = StartupFolderResolver.resolve(baseDir);

        assertTrue(resolved.isPresent());
        assertEquals("3120221229008001", resolved.get().getFileName().toString());
    }

    @Test
    void shouldIgnoreFilesStartingWithPrefix() throws IOException {
        Files.createFile(baseDir.resolve("3notes.txt"));

        assertTrue(StartupFolderResolver.resolve(baseDir).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenNothingMatches() throws IOException {
        Files.createDirectory(baseDir.resolve("data"));

        assertTrue(StartupFolderResolver.resolve(baseDir).isEmpty());
    }

    @Test
    void shouldReturnEmptyForMissingDirectory() {
        assertTrue(StartupFolderResolver.resolve(baseDir.resolve("missing")).isEmpty());
        assertTrue(StartupFolderResolver.resolve(null).isEmpty());
    }

    @Test
    void shouldSortByName() throws IOException {
        Files.createDirectory(baseDir.resolve("39series"));
        Files.createDirectory(baseDir.resolve("30series"));

        Optional<Path> resolved = StartupFolderResolver.resolve(baseDir);

        assertTrue(resolved.isPresent());
        assertEquals("30series", resolved.get().getFileName().toString());
    }
}
