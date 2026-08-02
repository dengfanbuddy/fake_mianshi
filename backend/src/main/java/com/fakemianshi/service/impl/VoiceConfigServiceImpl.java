package com.fakemianshi.service.impl;

import com.fakemianshi.config.TencentCloudProperties;
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
 * 腾讯云语音配置服务实现：数据库配置优先，无配置时回退环境变量。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceConfigServiceImpl implements VoiceConfigService {

    private static final String PROVIDER_TENCENT = "tencent";

    private final VoiceConfigRepository voiceConfigRepository;
    private final TencentCloudProperties properties;

    @Override
    @Transactional
    public VoiceConfig saveConfig(VoiceConfigDTO dto) {
        // 停用旧配置
        voiceConfigRepository.findByIsActiveTrue().ifPresent(old -> {
            old.setIsActive(false);
            old.setUpdatedAt(LocalDateTime.now());
            voiceConfigRepository.updateById(old);
        });

        VoiceConfig cfg = new VoiceConfig();
        cfg.setProvider(PROVIDER_TENCENT);
        // 密钥留空表示保留原值
        cfg.setSecretId(dto.getSecretId() == null || dto.getSecretId().isBlank()
                ? null : dto.getSecretId().trim());
        cfg.setSecretKey(dto.getSecretKey() == null || dto.getSecretKey().isBlank()
                ? null : dto.getSecretKey().trim());
        cfg.setAppId(dto.getAppId() == null ? null : dto.getAppId().trim());
        cfg.setRegion(dto.getRegion() == null || dto.getRegion().isBlank()
                ? "ap-guangzhou" : dto.getRegion().trim());
        cfg.setAsrUrl(dto.getAsrUrl() == null || dto.getAsrUrl().isBlank()
                ? "https://asr.tencentcloudapi.com" : dto.getAsrUrl().trim());
        cfg.setTtsUrl(dto.getTtsUrl() == null || dto.getTtsUrl().isBlank()
                ? "https://tts.tencentcloudapi.com" : dto.getTtsUrl().trim());
        cfg.setIsActive(true); // 保存的配置即激活
        cfg.setUpdatedAt(LocalDateTime.now());
        voiceConfigRepository.insert(cfg);
        return cfg;
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
        return cfg; // 可能为 null（完全未配置）
    }

    private String mask(String s) {
        if (s == null || s.isBlank()) return null;
        if (s.length() <= 6) return "******";
        return s.substring(0, 3) + "****" + s.substring(s.length() - 3);
    }
}
