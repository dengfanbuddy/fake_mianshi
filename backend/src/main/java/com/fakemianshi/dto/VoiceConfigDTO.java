package com.fakemianshi.dto;

/**
 * 腾讯云语音配置 DTO：页面展示/保存用（密钥脱敏）。
 */
public class VoiceConfigDTO {

    private String provider;
    private String secretId;
    private String secretKey;
    private String appId;
    private String region;
    private String asrUrl;
    private String ttsUrl;
    /** 本地识别热词（页面可编辑；为空 = 用内置默认表） */
    private String asrPrompt;
    /** 内置默认热词表（只读下发，供页面做 placeholder / 一键填入） */
    private String asrPromptDefault;
    private Boolean isActive;
    /** 是否已配置完整密钥（脱敏后的状态标识） */
    private Boolean configured;
    /** 当前生效供应商是否为本地 Qwen sidecar（前端用于切换 UI 状态） */
    private Boolean local;

    public Boolean getLocal() {
        return local;
    }

    public void setLocal(Boolean local) {
        this.local = local;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAsrUrl() {
        return asrUrl;
    }

    public void setAsrUrl(String asrUrl) {
        this.asrUrl = asrUrl;
    }

    public String getTtsUrl() {
        return ttsUrl;
    }

    public void setTtsUrl(String ttsUrl) {
        this.ttsUrl = ttsUrl;
    }

    public String getAsrPrompt() {
        return asrPrompt;
    }

    public void setAsrPrompt(String asrPrompt) {
        this.asrPrompt = asrPrompt;
    }

    public String getAsrPromptDefault() {
        return asrPromptDefault;
    }

    public void setAsrPromptDefault(String asrPromptDefault) {
        this.asrPromptDefault = asrPromptDefault;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Boolean getConfigured() {
        return configured;
    }

    public void setConfigured(Boolean configured) {
        this.configured = configured;
    }
}
