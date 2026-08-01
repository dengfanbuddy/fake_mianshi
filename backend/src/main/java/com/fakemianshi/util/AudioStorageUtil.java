package com.fakemianshi.util;

import com.fakemianshi.config.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 音频文件存储工具：将语音音频保存到磁盘并支持按路径读取回放。
 *
 * <p>目录结构：{@code app.audio-dir}/{sessionId}/{role}_{时间戳}.wav。
 */
@Component
public class AudioStorageUtil {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final String audioDir;

    public AudioStorageUtil(@Value("${app.audio-dir:./uploads/audio}") String audioDir) {
        this.audioDir = audioDir;
    }

    /**
     * 保存音频数据，返回文件绝对路径。
     *
     * @param data      音频字节
     * @param sessionId 面试会话 ID（为空则存到 0 目录下）
     * @param role      角色标识（如 interviewer / candidate），用于文件名前缀
     * @return 保存后的文件绝对路径
     */
    public String saveAudio(byte[] data, Long sessionId, String role) {
        String safeRole = (role == null || role.isBlank()) ? "unknown"
                : role.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
        long session = (sessionId == null) ? 0L : sessionId;

        Path dir = Paths.get(audioDir).resolve(String.valueOf(session));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessException("音频目录创建失败: " + e.getMessage(), e);
        }

        String fileName = safeRole + "_" + LocalDateTime.now().format(TS_FORMAT)
                + "_" + System.nanoTime() + ".wav";
        Path file = dir.resolve(fileName);
        try {
            Files.write(file, data);
        } catch (IOException e) {
            throw new BusinessException("音频文件保存失败: " + e.getMessage(), e);
        }
        return file.toAbsolutePath().toString();
    }

    /**
     * 加载音频文件为 {@link Resource}。支持绝对路径，也支持相对于音频目录的路径。
     *
     * @param filePath 文件绝对路径或相对音频目录的路径
     * @return 文件资源
     */
    public Resource loadAudio(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new BusinessException("音频文件路径不能为空");
        }
        Path root = Paths.get(audioDir).toAbsolutePath().normalize();
        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) {
            path = root.resolve(path).normalize();
        }
        // 防止路径穿越：相对路径解析结果必须仍位于音频目录内
        if (!path.startsWith(root)) {
            throw new BusinessException("音频文件路径非法: " + filePath);
        }
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BusinessException("音频文件不存在: " + filePath);
        }
        return new FileSystemResource(path);
    }
}
