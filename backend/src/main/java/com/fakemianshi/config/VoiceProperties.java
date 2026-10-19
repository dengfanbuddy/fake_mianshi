package com.fakemianshi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音服务供应商配置（应用级，页面配置优先）。
 *
 * <p>{@code provider} 决定默认走哪家：{@code tencent}（腾讯云）或 {@code local_qwen}（本地 Qwen3-TTS sidecar）。
 * 数据库 {@code voice_config.provider} 存在时以其为准，这里只是没有页面配置时的默认值。
 */
@Data
@ConfigurationProperties(prefix = "app.voice")
public class VoiceProperties {

    /** 默认供应商：tencent / local_qwen */
    private String provider = "tencent";

    /** 本地 Qwen 语音服务（voice-service sidecar）配置 */
    private Local local = new Local();

    @Data
    public static class Local {

        /** sidecar 地址，默认本机 9100 */
        private String baseUrl = "http://127.0.0.1:9100";

        /** 连接超时（秒） */
        private int connectTimeoutSeconds = 5;

        /** 单次请求超时（秒）；首次调用可能触发模型加载，故放宽 */
        private int timeoutSeconds = 120;

        /** 输出采样率（与前端 PcmQueuePlayer 的 16k 假设一致） */
        private int sampleRate = 16000;

        /** 本地合成失败时是否自动回退腾讯云（腾讯云已配置的前提下） */
        private boolean fallbackToTencent = true;
    }
}
