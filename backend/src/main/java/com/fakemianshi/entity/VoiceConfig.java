package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 语音服务配置（TTS/ASR）：页面可配置、存库、激活生效，方便打包分发。
 *
 * <p>{@code provider} 区分供应商：{@code tencent}（腾讯云，用 SecretId/SecretKey）与
 * {@code local_qwen}（本地 Qwen3-TTS sidecar，用 ttsUrl 指向服务地址，无需密钥）。
 */
@Data
@TableName("voice_config")
public class VoiceConfig {

    /** 腾讯云 */
    public static final String PROVIDER_TENCENT = "tencent";

    /** 本地 Qwen3-TTS sidecar（voice-service） */
    public static final String PROVIDER_LOCAL_QWEN = "local_qwen";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 服务提供商（tencent / local_qwen） */
    private String provider;

    /** 腾讯云 SecretId */
    @TableField("secret_id")
    private String secretId;

    /** 腾讯云 SecretKey */
    @TableField("secret_key")
    private String secretKey;

    /** 腾讯云 AppId */
    @TableField("app_id")
    private String appId;

    /** 地域，默认 ap-guangzhou */
    private String region;

    /** ASR 接口地址 */
    @TableField("asr_url")
    private String asrUrl;

    /** TTS 接口地址（本地 Qwen 时即为 sidecar 根地址，如 http://127.0.0.1:9100） */
    @TableField("tts_url")
    private String ttsUrl;

    /** 是否启用 */
    @TableField("is_active")
    private Boolean isActive;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 是否为本地 Qwen 供应商 */
    public boolean isLocalProvider() {
        if (provider == null) {
            return false;
        }
        String p = provider.trim().toLowerCase();
        return p.startsWith("local") || p.equals("qwen") || p.equals("qwen3");
    }

    /**
     * 是否已配置完整：本地 Qwen 只需 sidecar 地址，腾讯云需要 SecretId/SecretKey。
     */
    public boolean isConfigured() {
        if (isLocalProvider()) {
            return ttsUrl != null && !ttsUrl.isBlank();
        }
        return secretId != null && !secretId.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
