package com.fakemianshi.service.impl;

import com.fakemianshi.config.TencentCloudProperties;
import com.fakemianshi.config.VoiceProperties;
import com.fakemianshi.dto.VoiceConfigDTO;
import com.fakemianshi.entity.VoiceConfig;
import com.fakemianshi.repository.VoiceConfigRepository;
import com.fakemianshi.service.VoiceConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 语音配置服务实现：数据库配置优先，无配置时回退环境变量/配置文件默认值。
 *
 * <p>支持两种供应商：{@code tencent}（腾讯云，需密钥）与 {@code local_qwen}
 * （本地 voice-service sidecar，只需地址）。切换供应商即「保存配置并激活」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceConfigServiceImpl implements VoiceConfigService {

    private static final String PROVIDER_TENCENT = VoiceConfig.PROVIDER_TENCENT;
    private static final String DEFAULT_ASR_URL = "https://asr.tencentcloudapi.com";
    private static final String DEFAULT_TTS_URL = "https://tts.tencentcloudapi.com";

    private final VoiceConfigRepository voiceConfigRepository;
    private final TencentCloudProperties properties;
    private final VoiceProperties voiceProperties;

    @Override
    @Transactional
    public VoiceConfig saveConfig(VoiceConfigDTO dto) {
        // 停用旧配置，并保留其密钥用于「留空保留原值」
        VoiceConfig old = voiceConfigRepository.findByIsActiveTrue().orElse(null);
        if (old != null) {
            old.setIsActive(false);
            old.setUpdatedAt(LocalDateTime.now());
            voiceConfigRepository.updateById(old);
        }

        boolean local = isLocal(dto.getProvider());
        VoiceConfig cfg = new VoiceConfig();
        cfg.setProvider(local ? VoiceConfig.PROVIDER_LOCAL_QWEN : PROVIDER_TENCENT);
        // 密钥留空或为脱敏值（含 *）时保留原值：避免页面回显的脱敏 SecretId 被误存、SecretKey 被清空
        cfg.setSecretId(resolveSecret(dto.getSecretId(), old == null ? null : old.getSecretId()));
        cfg.setSecretKey(resolveSecret(dto.getSecretKey(), old == null ? null : old.getSecretKey()));
        cfg.setAppId(resolveSecret(dto.getAppId(), old == null ? null : old.getAppId()));
        cfg.setRegion(dto.getRegion() == null || dto.getRegion().isBlank()
                ? "ap-guangzhou" : dto.getRegion().trim());
        cfg.setAsrUrl(dto.getAsrUrl() == null || dto.getAsrUrl().isBlank()
                ? DEFAULT_ASR_URL : dto.getAsrUrl().trim());
        if (local) {
            // 本地模式下 ttsUrl 存放 sidecar 根地址
            cfg.setTtsUrl(dto.getTtsUrl() == null || dto.getTtsUrl().isBlank()
                    ? voiceProperties.getLocal().getBaseUrl() : trimSlash(dto.getTtsUrl().trim()));
        } else {
            cfg.setTtsUrl(dto.getTtsUrl() == null || dto.getTtsUrl().isBlank()
                    ? DEFAULT_TTS_URL : dto.getTtsUrl().trim());
        }
        cfg.setIsActive(true); // 保存的配置即激活
        cfg.setUpdatedAt(LocalDateTime.now());
        voiceConfigRepository.insert(cfg);
        log.info("语音配置已激活: provider={} ttsUrl={}", cfg.getProvider(), cfg.getTtsUrl());
        return cfg;
    }

    /** 入参为空或为脱敏值（含 *）时返回原值，否则返回 trim 后的入参。 */
    private String resolveSecret(String input, String original) {
        if (input == null || input.isBlank() || input.contains("*")) {
            return original;
        }
        return input.trim();
    }

    private static boolean isLocal(String provider) {
        if (provider == null) {
            return false;
        }
        String p = provider.trim().toLowerCase();
        return p.startsWith("local") || p.equals("qwen") || p.equals("qwen3");
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceConfigDTO getActiveConfig() {
        VoiceConfig cfg = resolveActive();
        VoiceConfigDTO dto = new VoiceConfigDTO();
        dto.setProvider(cfg == null ? null : cfg.getProvider());
        dto.setSecretId(cfg == null ? null : mask(cfg.getSecretId()));
        dto.setSecretKey(cfg == null ? null : mask(cfg.getSecretKey()));
        dto.setAppId(cfg == null ? null : cfg.getAppId());
        dto.setRegion(cfg == null ? null : cfg.getRegion());
        dto.setAsrUrl(cfg == null ? null : cfg.getAsrUrl());
        dto.setTtsUrl(cfg == null ? null : cfg.getTtsUrl());
        dto.setIsActive(cfg == null ? false : cfg.getIsActive());
        dto.setConfigured(cfg != null && cfg.isConfigured());
        dto.setLocal(cfg != null && cfg.isLocalProvider());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceConfig resolveActive() {
        VoiceConfig cfg = voiceConfigRepository.findByIsActiveTrue().orElse(null);
        if (cfg != null && cfg.isConfigured()) {
            return cfg;
        }
        // 回退环境变量（打包分发场景用户未配置数据库时）
        if (properties.isConfigured()) {
            VoiceConfig env = new VoiceConfig();
            env.setProvider(PROVIDER_TENCENT);
            env.setSecretId(properties.getSecretId());
            env.setSecretKey(properties.getSecretKey());
            env.setAppId(properties.getAppId());
            env.setRegion(properties.getRegion());
            env.setAsrUrl(properties.getAsrUrl());
            env.setTtsUrl(properties.getTtsUrl());
            env.setIsActive(true);
            return env;
        }
        // 配置默认供应商为本地 Qwen 时，无需任何密钥
        if (isLocal(voiceProperties.getProvider())) {
            VoiceConfig env = new VoiceConfig();
            env.setProvider(VoiceConfig.PROVIDER_LOCAL_QWEN);
            env.setRegion(properties.getRegion());
            env.setAsrUrl(properties.getAsrUrl());
            env.setTtsUrl(voiceProperties.getLocal().getBaseUrl());
            env.setIsActive(true);
            return env;
        }
        return cfg; // 可能为 null（完全未配置）
    }

    private String mask(String s) {
        if (s == null || s.isBlank()) return null;
        if (s.length() <= 6) return "******";
        return s.substring(0, 3) + "****" + s.substring(s.length() - 3);
    }
}
