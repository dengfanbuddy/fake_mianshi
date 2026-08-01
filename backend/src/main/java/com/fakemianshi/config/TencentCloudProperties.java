package com.fakemianshi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯云语音服务配置。
 *
 * <p>密钥通过环境变量注入（{@code TENCENT_SECRET_ID} / {@code TENCENT_SECRET_KEY} / {@code TENCENT_APP_ID}），
 * 未配置时调用语音服务会抛出 {@link BusinessException}。
 */
@Data
@ConfigurationProperties(prefix = "tencent.cloud")
public class TencentCloudProperties {

    /** 腾讯云 SecretId */
    private String secretId;

    /** 腾讯云 SecretKey */
    private String secretKey;

    /** 腾讯云 AppId（部分语音服务需要） */
    private String appId;

    /** 一句话识别（ASR）接口地址 */
    private String asrUrl = "https://asr.tencentcloudapi.com";

    /** 语音合成（TTS）接口地址 */
    private String ttsUrl = "https://tts.tencentcloudapi.com";

    /** 地域，默认广州 */
    private String region = "ap-guangzhou";

    /** 是否已配置密钥 */
    public boolean isConfigured() {
        return secretId != null && !secretId.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
