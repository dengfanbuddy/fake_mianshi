package com.fakemianshi.service.impl;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.TencentCloudProperties;
import com.fakemianshi.config.VoiceProperties;
import com.fakemianshi.entity.VoiceConfig;
import com.fakemianshi.service.LocalQwenVoiceService;
import com.fakemianshi.service.VoiceConfigService;
import com.fakemianshi.service.VoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 语音服务路由：按当前生效配置把调用分发给腾讯云或本地 Qwen sidecar。
 *
 * <p>标记 {@link Primary}，全项目注入 {@link VoiceService} 的地方都会拿到它，
 * 上层业务（逐句流式 TTS、语音识别）无需感知供应商差异。
 *
 * <p>P0 阶段：TTS 支持双供应商；ASR 仍固定走腾讯云（本地 Qwen ASR 待 P1 接入）。
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class VoiceServiceRouter implements VoiceService {

    private final VoiceServiceImpl tencentVoiceService;
    private final LocalQwenVoiceService localQwenVoiceService;
    private final VoiceConfigService voiceConfigService;
    private final VoiceProperties voiceProperties;
    private final TencentCloudProperties tencentCloudProperties;

    /** 当前生效供应商是否为本地 Qwen */
    public boolean activeIsLocal() {
        VoiceConfig cfg = voiceConfigService.resolveActive();
        return cfg != null && cfg.isLocalProvider();
    }

    /** 当前生效供应商名（前端展示用） */
    public String activeProvider() {
        VoiceConfig cfg = voiceConfigService.resolveActive();
        if (cfg != null && cfg.getProvider() != null && !cfg.getProvider().isBlank()) {
            return cfg.getProvider();
        }
        return voiceProperties.getProvider();
    }

    /** 本地 sidecar 健康检查（页面连通性测试） */
    public Map<String, Object> localHealth() {
        return localQwenVoiceService.health();
    }

    /** 本地 Qwen 优先流式（首包约 0.5s）；腾讯云保持整句合成的既有行为 */
    @Override
    public boolean preferStreamingTts() {
        return activeIsLocal();
    }

    @Override
    public String recognizeSpeech(byte[] audioData) {
        // P0：本地 Qwen ASR 尚未接入，识别固定走腾讯云
        try {
            return tencentVoiceService.recognizeSpeech(audioData);
        } catch (BusinessException e) {
            if (activeIsLocal()) {
                throw new BusinessException(
                        "当前语音供应商为本地 Qwen，但本地 ASR 尚未接入（P1 阶段）；"
                                + "语音识别仍需配置腾讯云密钥。原始错误: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    @Override
    public byte[] synthesizeSpeech(String text, String voiceType) {
        if (!activeIsLocal()) {
            return tencentVoiceService.synthesizeSpeech(text, voiceType);
        }
        try {
            return localQwenVoiceService.synthesizeSpeech(text, voiceType);
        } catch (RuntimeException e) {
            return fallbackSynthesize(text, voiceType, e);
        }
    }

    @Override
    public byte[] synthesizeSpeechLong(String text, String voiceType) {
        if (!activeIsLocal()) {
            return tencentVoiceService.synthesizeSpeechLong(text, voiceType);
        }
        try {
            return localQwenVoiceService.synthesizeSpeechLong(text, voiceType);
        } catch (RuntimeException e) {
            return fallbackSynthesize(text, voiceType, e);
        }
    }

    @Override
    public void synthesizeSpeechStream(String text, String voiceType, Consumer<byte[]> onPcmChunk) {
        if (!activeIsLocal()) {
            tencentVoiceService.synthesizeSpeechStream(text, voiceType, onPcmChunk);
            return;
        }
        try {
            localQwenVoiceService.synthesizeSpeechStream(text, voiceType, onPcmChunk);
        } catch (RuntimeException e) {
            if (!shouldFallback()) {
                throw e;
            }
            log.warn("本地流式 TTS 失败，回退腾讯云: {}", e.getMessage());
            tencentVoiceService.synthesizeSpeechStream(text, voiceType, onPcmChunk);
        }
    }

    private byte[] fallbackSynthesize(String text, String voiceType, RuntimeException cause) {
        if (!shouldFallback()) {
            throw cause;
        }
        log.warn("本地 TTS 失败，回退腾讯云: {}", cause.getMessage());
        return tencentVoiceService.synthesizeSpeech(text, voiceType);
    }

    /** 仅在开启回退且腾讯云密钥可用时回退，避免本地挂了直接让语音整体失败 */
    private boolean shouldFallback() {
        if (!voiceProperties.getLocal().isFallbackToTencent()) {
            return false;
        }
        return tencentAvailable();
    }

    /** 腾讯云密钥是否可用（切换成本地供应商后，库里的旧密钥仍会被保留） */
    private boolean tencentAvailable() {
        VoiceConfig cfg = voiceConfigService.resolveActive();
        if (cfg != null
                && cfg.getSecretId() != null && !cfg.getSecretId().isBlank()
                && cfg.getSecretKey() != null && !cfg.getSecretKey().isBlank()) {
            return true;
        }
        return tencentCloudProperties.isConfigured();
    }
}
