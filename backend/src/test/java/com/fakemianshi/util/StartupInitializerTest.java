package com.fakemianshi.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 启动初始化工具单元测试：
 * 通过环境变量把路径指向临时目录，验证目录与数据库文件会被自动创建。
 */
class StartupInitializerTest {

    @Test
    void ensureRuntimeDirectories_shouldCreateDirsAndDbFile() throws Exception {
        Path tmp = Files.createTempDirectory("startup-init-test");
        Path dataDir = tmp.resolve("data");
        Path dbFile = tmp.resolve("data").resolve("fake_mianshi.db");
        Path audioDir = tmp.resolve("uploads").resolve("audio");

        System.setProperty("FAKE_MIANSHI_DATA_DIR", dataDir.toString());
        System.setProperty("FAKE_MIANSHI_DB_FILE", dbFile.toString());
        System.setProperty("FAKE_MIANSHI_AUDIO_DIR", audioDir.toString());

        try {
            StartupInitializer.ensureRuntimeDirectories();
        } finally {
            System.clearProperty("FAKE_MIANSHI_DATA_DIR");
            System.clearProperty("FAKE_MIANSHI_DB_FILE");
            System.clearProperty("FAKE_MIANSHI_AUDIO_DIR");
        }

        assertTrue(Files.isDirectory(dataDir), "数据库目录应被创建");
        assertTrue(Files.exists(dbFile), "数据库文件应被创建");
        assertTrue(Files.isDirectory(audioDir), "上传目录应被创建");
    }
}
