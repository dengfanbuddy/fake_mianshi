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

        /**
         * 后端启动时：若生效供应商是本地 Qwen 且 sidecar 未运行，是否自动拉起。
         *
         * <p>测试环境必须关掉（见 src/test/resources/application.yml），否则跑测试会真的去 spawn Python。
         */
        private boolean autoStart = true;

        /**
         * 后端关闭时是否结束由本进程拉起的 sidecar。
         *
         * <p>只结束"自己拉起的"——如果启动时 sidecar 已在运行（用户手工跑的），
         * 不接管也不结束，避免把别人的服务误杀。
         */
        private boolean stopOnShutdown = true;

        /**
         * voice-service 目录；留空则自动探测（../voice-service、voice-service）。
         * 目录下需存在 app/main.py。
         */
        private String serviceDir = "";

        /** Python 解释器；留空则自动探测 {@code <serviceDir>/.venv/Scripts/python.exe}（Windows）或 .venv/bin/python */
        private String python = "";

        /** 结束进程时的等待秒数，超时则强杀 */
        private int stopTimeoutSeconds = 15;

        /**
         * 记录"本后端拉起的 sidecar PID"的文件。
         *
         * <p>用途是崩溃/硬杀后的接管：关闭钩子没机会执行时进程会变成孤儿（占着 GPU 显存），
         * 下次启动读到这个 PID 且进程仍活着，就接管它，从而在下次正常关闭时被清理。
         */
        private String pidFile = "data/voice-sidecar.pid";
    }
}
