package com.fakemianshi.util;

import com.fakemianshi.config.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 音频存储工具单元测试：验证保存/读取/目录自动创建。
 */
class AudioStorageUtilTest {

    @TempDir
    Path tempDir;

    private AudioStorageUtil newUtil() {
        return new AudioStorageUtil(tempDir.toString());
    }

    @Test
    void saveAudio_shouldCreateFileUnderSessionDirAndReturnAbsolutePath() throws Exception {
        AudioStorageUtil util = newUtil();
        byte[] data = "fake-audio-bytes".getBytes(StandardCharsets.UTF_8);

        String filePath = util.saveAudio(data, 42L, "interviewer");

        assertNotNull(filePath);
        assertTrue(filePath.endsWith(".wav"));
        Path file = Path.of(filePath);
        assertTrue(file.isAbsolute());
        assertTrue(Files.exists(file));
        assertArrayEquals(data, Files.readAllBytes(file));

        // 位于 audio-dir/42/ 目录下
        assertTrue(file.getParent().startsWith(tempDir.resolve("42")));
        assertTrue(file.getFileName().toString().startsWith("interviewer_"));
    }

    @Test
    void saveAudio_shouldAutoCreateNestedDirectories() throws Exception {
        AudioStorageUtil util = newUtil();
        String filePath = util.saveAudio(new byte[]{1, 2}, 7L, "candidate");

        Path dir = tempDir.resolve("7");
        assertTrue(Files.isDirectory(dir));
        assertTrue(Files.exists(Path.of(filePath)));
    }

    @Test
    void loadAudio_shouldReadBackSavedFileByAbsolutePath() throws Exception {
        AudioStorageUtil util = newUtil();
        byte[] data = "hello-audio".getBytes(StandardCharsets.UTF_8);
        String filePath = util.saveAudio(data, 1L, "interviewer");

        Resource resource = util.loadAudio(filePath);
        assertNotNull(resource);
        assertTrue(resource.exists());
        byte[] readBack = resource.getInputStream().readAllBytes();
        assertArrayEquals(data, readBack);
    }

    @Test
    void loadAudio_shouldResolveRelativePathUnderAudioDir() throws Exception {
        AudioStorageUtil util = newUtil();
        byte[] data = new byte[]{9, 8, 7};
        util.saveAudio(data, 3L, "interviewer");

        // 列出生成的相对文件名，验证相对路径读取
        String fileName;
        try (var stream = Files.list(tempDir.resolve("3"))) {
            fileName = stream.findFirst().orElseThrow().getFileName().toString();
        }
        Resource resource = util.loadAudio("3/" + fileName);
        assertTrue(resource.exists());
        assertArrayEquals(data, resource.getInputStream().readAllBytes());
    }

    @Test
    void loadAudio_shouldThrowBusinessException_whenFileMissing() {
        AudioStorageUtil util = newUtil();
        assertThrows(BusinessException.class, () -> util.loadAudio(tempDir.resolve("99/nonexist.wav").toString()));
    }

    @Test
    void loadAudio_shouldRejectPathTraversal() {
        AudioStorageUtil util = newUtil();
        assertThrows(BusinessException.class, () -> util.loadAudio("../secret.txt"));
    }
}
