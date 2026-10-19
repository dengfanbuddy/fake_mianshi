package com.fakemianshi.voice;

/**
 * 一轮实时识别的抽象会话：浏览器推 PCM 进来，识别结果通过 {@link Listener} 回调出去。
 *
 * <p>两个实现：
 * <ul>
 *   <li>{@link TencentAsrSession} —— 腾讯云实时语音识别（真流式，分句增量返回）；</li>
 *   <li>{@link LocalAsrSession} —— 本地 Qwen3-ASR（离线模型，靠累积缓冲重识别模拟出中间结果）。</li>
 * </ul>
 *
 * <p>上层 {@link InterviewVoiceWebSocketHandler} 只依赖这个接口，按当前生效供应商选择实现，
 * 业务侧（LLM 流式回复、录音落盘）完全不感知差异。
 */
public interface AsrSession extends AutoCloseable {

    /** 识别结果回调 */
    interface Listener {

        /** 中间结果（会变化，仅用于实时展示，不应累加） */
        void onPartialResult(String text);

        /** 最终结果（整段识别完成，触发面试官回复） */
        void onFinalResult(String text);

        /** 识别失败（前端据此降级到 HTTP 一句话识别） */
        void onError(String message);
    }

    /** 推送一段 PCM 音频（16k / 16bit / 单声道） */
    void sendAudio(byte[] pcm16k);

    /** 松手：结束本轮并产出最终结果 */
    void stop();

    @Override
    void close();
}
