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
    private final com.fakemianshi.service.VoiceConfigService voiceConfigService;

    /** 腾讯云语音密钥配置状态（数据库配置或环境变量，供前端展示，不暴露密钥本身） */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        com.fakemianshi.dto.VoiceConfigDTO cfg = voiceConfigService.getActiveConfig();
        boolean configured = cfg != null && Boolean.TRUE.equals(cfg.getConfigured());
        boolean appIdConfigured = cfg != null && cfg.getAppId() != null && !cfg.getAppId().isBlank();
        return ApiResponse.success(Map.of(
                "configured", configured,
                "appIdConfigured", appIdConfigured));
    }

    /** 获取当前生效的语音配置（脱敏，页面回显用） */
    @GetMapping("/config")
    public ApiResponse<com.fakemianshi.dto.VoiceConfigDTO> getConfig() {
        return ApiResponse.success(voiceConfigService.getActiveConfig());
    }

    /** 保存语音配置并激活（页面配置，打包分发后无需改环境变量） */
    @PostMapping("/config")
    public ApiResponse<com.fakemianshi.dto.VoiceConfigDTO> saveConfig(
            @RequestBody com.fakemianshi.dto.VoiceConfigDTO dto) {
        voiceConfigService.saveConfig(dto);
        return ApiResponse.success(voiceConfigService.getActiveConfig());
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
