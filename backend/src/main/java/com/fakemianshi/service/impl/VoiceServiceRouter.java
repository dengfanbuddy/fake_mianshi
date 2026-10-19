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
 * <p>TTS 与 ASR 都支持双供应商：本地侧的 Qwen3-TTS 与 Qwen3-ASR 跑在同一个 voice-service
 * 进程里（transformers 原生支持两张模型），腾讯云侧沿用原有的实时识别与基础合成。
 * 本地失败时按 {@code app.voice.local.fallback-to-tencent} 决定是否回退腾讯云。
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

    /**
     * 实时识别（WebSocket 路径）是否应走本地 Qwen3-ASR。
     *
     * <p>与 {@link #activeIsLocal()} 的差别是还要求 ASR 没被单独关掉——
     * 有人可能想用本地合成 + 腾讯云识别（比如显卡被别的任务占着）。
     */
    public boolean activeAsrIsLocal() {
        return activeIsLocal() && voiceProperties.getLocal().getAsr().isEnabled();
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
        if (!activeAsrIsLocal()) {
            return tencentVoiceService.recognizeSpeech(audioData);
        }
        try {
            return localQwenVoiceService.recognizeSpeech(audioData);
        } catch (RuntimeException e) {
            if (!shouldFallback()) {
                // 没有腾讯云兜底时，把本地失败原因原样抛出（比"未配置密钥"有用得多）
                throw e instanceof BusinessException be ? be
                        : new BusinessException("本地语音识别失败: " + e.getMessage(), e);
            }
            log.warn("本地 ASR 失败，回退腾讯云: {}", e.getMessage());
            try {
                return tencentVoiceService.recognizeSpeech(audioData);
            } catch (BusinessException fallbackError) {
                // 两边都不可用时，优先报本地原因（用户刚配的是本地）
                throw new BusinessException("本地语音识别失败（" + e.getMessage()
                        + "），且回退腾讯云也失败（" + fallbackError.getMessage() + "）", e);
            }
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
