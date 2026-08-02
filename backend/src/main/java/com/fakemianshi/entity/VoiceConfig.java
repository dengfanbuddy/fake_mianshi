package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 腾讯云语音服务配置（TTS/ASR）：页面可配置、存库、激活生效，方便打包分发。
 */
@Data
@TableName("voice_config")
public class VoiceConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 服务提供商（固定 tencent） */
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

    /** TTS 接口地址 */
    @TableField("tts_url")
    private String ttsUrl;

    /** 是否启用 */
    @TableField("is_active")
    private Boolean isActive;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 是否已配置完整密钥 */
    public boolean isConfigured() {
        return secretId != null && !secretId.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
