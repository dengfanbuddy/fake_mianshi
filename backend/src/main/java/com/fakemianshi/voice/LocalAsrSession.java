package com.fakemianshi.voice;

import com.fakemianshi.config.VoiceProperties;
import com.fakemianshi.service.LocalQwenVoiceService;
import com.fakemianshi.util.WavAudio;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地 Qwen3-ASR 的识别会话。
 *
 * <p>为什么需要它：Qwen3-ASR 只有整段离线识别（真流式推理要 vLLM backend，Windows 无轮子），
 * 但前端的交互契约是「边说边出字 + 松手出最终结果」。这里用累积缓冲把离线模型包装成流式的观感：
 *
 * <ul>
 *   <li>{@link #sendAudio} 只做追加，不触发计算；</li>
 *   <li>后台循环每隔一段时间把<b>截至目前整段</b>音频重新识别一次，作为中间结果推给前端。
 *       识别的是完整前缀，所以文本是"逐渐变长"而不是乱跳；</li>
 *   <li>识别完成后按本次耗时自我限速（{@code delay >= 2 × 上次耗时}），保证 GPU 至少
 *       2/3 时间空闲——否则长回答会把显卡占满，松手后的最终识别要排队；</li>
 *   <li>松手后停止循环，用完整音频做一次最终识别 → {@link Listener#onFinalResult}。</li>
 * </ul>
 *
 * <p>实测（RTX 4070 Ti SUPER，0.6B）：一段 12s 回答约 1.6s 出最终结果，约 6x 实时。
 * 中间结果是可以关掉的（{@code partialIntervalMs=0}），关了更省显卡、松手后更快。
 */
@Slf4j
public class LocalAsrSession implements AsrSession {

    private static final int SAMPLE_RATE = WavAudio.SAMPLE_RATE;
    private static final int BYTES_PER_SECOND = SAMPLE_RATE * 2; // 16bit 单声道
    /** 单次中间结果的等待上限，防止异常情况下循环彻底停摆 */
    private static final long MAX_BACKOFF_MS = 5000;

    private final LocalQwenVoiceService voiceService;
    private final Listener listener;
    private final VoiceProperties.Asr config;

    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream(64 * 1024);
    private final AtomicBoolean finished = new AtomicBoolean(false);
    /** 每次 stop/close 自增；在飞的中间结果回来时对不上就丢弃 */
    private final AtomicInteger generation = new AtomicInteger();
    private volatile int lastPartialBytes;
    private volatile long lastInferMs;

    private final ScheduledExecutorService scheduler;

    private LocalAsrSession(LocalQwenVoiceService voiceService, Listener listener,
                            VoiceProperties.Asr config) {
        this.voiceService = voiceService;
        this.listener = listener;
        this.config = config;
        this.scheduler = config.getPartialIntervalMs() > 0
                ? Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "local-asr-partial");
                    t.setDaemon(true);
                    return t;
                })
                : null;
    }

    /**
     * 开始一轮本地识别。
     *
     * <p>与腾讯云不同，这里没有握手、没有鉴权，所以调用方可以立即收到 ready。
     */
    public static LocalAsrSession open(LocalQwenVoiceService voiceService,
                                       VoiceProperties.Asr config, Listener listener) {
        LocalAsrSession session = new LocalAsrSession(voiceService, listener, config);
        session.startPartialLoop();
        return session;
    }

    private void startPartialLoop() {
        if (scheduler == null) {
            return;
        }
        long interval = Math.max(500, config.getPartialIntervalMs());
        scheduler.schedule(this::partialTick, interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void sendAudio(byte[] pcm16k) {
        if (pcm16k == null || pcm16k.length == 0 || finished.get()) {
            return;
        }
        synchronized (pcm) {
            pcm.writeBytes(pcm16k);
        }
    }

    /**
     * 一次中间结果尝试：条件不满足就跳过，然后按本次耗时决定下次何时再试。
     *
     * <p>整个循环跑在单线程调度器上，所以天然不会出现多个识别并发把显卡打爆。
     */
    private void partialTick() {
        if (finished.get()) {
            return;
        }
        long delay = Math.max(500, config.getPartialIntervalMs());
        try {
            int length;
            synchronized (pcm) {
                length = pcm.size();
            }
            int minDelta = Math.max(0, config.getPartialMinDeltaMs()) * BYTES_PER_SECOND / 1000;
            boolean longEnough = length >= BYTES_PER_SECOND; // 至少半秒，否则识别出来没意义
            boolean freshEnough = length - lastPartialBytes >= minDelta;
            boolean withinLimit = length <= config.getPartialMaxAudioSeconds() * BYTES_PER_SECOND;

            if (longEnough && freshEnough && withinLimit) {
                final byte[] snapshot;
                synchronized (pcm) {
                    snapshot = pcm.toByteArray();
                }
                lastPartialBytes = snapshot.length;
                final int gen = generation.get();

                long started = System.currentTimeMillis();
                String text = null;
                try {
                    // 直接送裸 PCM：省掉再封装一次 WAV 的拷贝
                    text = voiceService.recognizePcm16(snapshot, SAMPLE_RATE);
                } catch (RuntimeException e) {
                    // 中间结果失败不影响最终识别，用户松手时还会再试一次
                    log.debug("中间识别失败（忽略）: {}", e.getMessage());
                } finally {
                    lastInferMs = System.currentTimeMillis() - started;
                }
                if (text != null && !text.isBlank()
                        && !finished.get() && gen == generation.get()) {
                    listener.onPartialResult(text);
                }
                // 自我限速：保证至少 2/3 的空闲，把显卡留给最终识别
                delay = Math.max(delay, Math.min(lastInferMs * 2, MAX_BACKOFF_MS));
            }
        } catch (RuntimeException e) {
            log.debug("中间结果循环异常（继续）: {}", e.getMessage());
        } finally {
            if (!finished.get() && scheduler != null && !scheduler.isShutdown()) {
                try {
                    scheduler.schedule(this::partialTick, delay, TimeUnit.MILLISECONDS);
                } catch (RejectedExecutionException ignored) {
                    // 正在关闭
                }
            }
        }
    }

    @Override
    public void stop() {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        generation.incrementAndGet();
        // 不 interrupt：正在跑的中间识别让它在 sidecar 侧自然结束，结果会被 generation 丢弃
        if (scheduler != null) {
            scheduler.shutdown();
        }

        final byte[] snapshot;
        synchronized (pcm) {
            snapshot = pcm.toByteArray();
        }
        if (snapshot.length == 0) {
            listener.onFinalResult("");
            return;
        }
        // 最终识别放后台线程，避免阻塞 WebSocket 读线程
        Thread worker = new Thread(() -> {
            try {
                String text = voiceService.recognizePcm16(snapshot, SAMPLE_RATE);
                listener.onFinalResult(text == null ? "" : text.trim());
            } catch (RuntimeException e) {
                listener.onError("本地语音识别失败: " + e.getMessage());
            }
        }, "local-asr-final");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void close() {
        finished.set(true);
        generation.incrementAndGet();
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
