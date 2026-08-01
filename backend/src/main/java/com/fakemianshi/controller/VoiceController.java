package com.fakemianshi.controller;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.TencentCloudProperties;
import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.dto.TtsRequest;
import com.fakemianshi.service.VoiceService;
import com.fakemianshi.util.AudioStorageUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 语音接口。
 *
 * <p>注意：服务器 context-path 为 /api，因此这里的 /voice 实际暴露为 /api/voice。
 */
@RestController
@RequestMapping("/voice")
@RequiredArgsConstructor
public class VoiceController {

    private static final String AUDIO_PATH_PREFIX = "/voice/audio/";

    private final VoiceService voiceService;
    private final AudioStorageUtil audioStorageUtil;
    private final TencentCloudProperties tencentCloudProperties;

    /** 腾讯云语音密钥配置状态（供前端展示，不暴露密钥本身） */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.success(Map.of(
                "configured", tencentCloudProperties.isConfigured(),
                "appIdConfigured", tencentCloudProperties.getAppId() != null
                        && !tencentCloudProperties.getAppId().isBlank()));
    }

    /** 语音识别（STT）：上传音频，返回识别文本 */
    @PostMapping("/stt")
    public ApiResponse<String> stt(@RequestParam("file") MultipartFile file) {
        byte[] audioData;
        try {
            audioData = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("音频读取失败: " + e.getMessage(), e);
        }
        return ApiResponse.success(voiceService.recognizeSpeech(audioData));
    }

    /** 语音合成（TTS）：文本转 wav 音频，直接返回二进制（不走 ApiResponse） */
    @PostMapping("/tts")
    public ResponseEntity<byte[]> tts(@RequestBody TtsRequest request) {
        byte[] audio = voiceService.synthesizeSpeech(request.getText(), request.getVoiceType());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=tts_" + System.currentTimeMillis() + ".wav")
                .body(audio);
    }

    /**
     * 音频回放：GET /voice/audio/{sessionId}/{fileName}，全量返回 wav 音频。
     * 相对路径相对于 {@code app.audio-dir} 解析。
     */
    @GetMapping("/audio/**")
    public ResponseEntity<Resource> audio(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf(AUDIO_PATH_PREFIX);
        String relativePath = (idx >= 0) ? uri.substring(idx + AUDIO_PATH_PREFIX.length()) : null;
        if (relativePath == null || relativePath.isBlank()) {
            throw new BusinessException("音频文件路径不能为空");
        }
        Resource resource = audioStorageUtil.loadAudio(relativePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(resource);
    }
}
