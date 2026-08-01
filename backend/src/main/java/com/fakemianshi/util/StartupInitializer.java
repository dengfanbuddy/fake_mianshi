package com.fakemianshi.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 启动初始化工具：在 Spring 容器启动之前确保运行时目录/文件存在。
 *
 * <p>数据库文件（SQLite）与上传目录（简历/录音）不会提交到 git 仓库，
 * 克隆项目后首次启动必须自动创建，否则 Hibernate 建表/文件上传会失败。
 *
 * <p>默认路径与 {@code application.yml} 保持一致（相对进程工作目录），
 * 可通过环境变量 {@code FAKE_MIANSHI_DATA_DIR} / {@code FAKE_MIANSHI_DB_FILE} /
 * {@code FAKE_MIANSHI_AUDIO_DIR} 覆盖。
 */
public final class StartupInitializer {

    private static final Logger log = LoggerFactory.getLogger(StartupInitializer.class);

    /** 数据库目录（与 application.yml 的 jdbc:sqlite:./data 一致） */
    private static final String DATA_DIR = envOr("FAKE_MIANSHI_DATA_DIR", "./data");

    /** 数据库文件 */
    private static final String DB_FILE = envOr("FAKE_MIANSHI_DB_FILE", "./data/fake_mianshi.db");

    /** 简历/录音上传目录（与 application.yml 的 app.upload-dir/app.audio-dir 一致） */
    private static final String AUDIO_DIR = envOr("FAKE_MIANSHI_AUDIO_DIR", "./uploads/audio");

    private StartupInitializer() {
    }

    /**
     * 在 {@code SpringApplication.run} 之前调用。
     * 确保 data 目录、上传目录存在；数据库文件不存在时创建空文件
     * （表结构由 Hibernate ddl-auto:update 在连接时自动创建）。
     */
    public static void ensureRuntimeDirectories() {
        ensureDirectory(DATA_DIR, "数据库目录");
        ensureDirectory(AUDIO_DIR, "上传目录");
        ensureDatabaseFile();
    }

    private static void ensureDirectory(String dirPath, String label) {
        Path dir = Paths.get(dirPath).toAbsolutePath().normalize();
        if (Files.isDirectory(dir)) {
            return;
        }
        try {
            Files.createDirectories(dir);
            log.info("已创建{}: {}", label, dir);
        } catch (IOException e) {
            throw new IllegalStateException("初始化" + label + "失败: " + dir, e);
        }
    }

    private static void ensureDatabaseFile() {
        Path file = Paths.get(DB_FILE).toAbsolutePath().normalize();
        if (Files.exists(file)) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.createFile(file);
            log.info("已创建 SQLite 数据库文件: {}", file);
        } catch (IOException e) {
            throw new IllegalStateException("初始化数据库文件失败: " + file, e);
        }
    }

    /** 配置取值：系统属性优先（便于测试/启动参数），其次环境变量，最后默认值 */
    private static String envOr(String name, String defaultValue) {
        String prop = System.getProperty(name);
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
