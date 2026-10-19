package com.fakemianshi.config;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务线程池工厂。
 *
 * <p>项目里原先有 4 处直接使用 {@code Executors.newCachedThreadPool()}：线程数**无上限**，
 * 任务只要提交就会新建线程。SSE 分析/出题单个任务可占用线程 2–7 分钟，语音回复任务还会
 * 串行等待 TTS；一旦 LLM 侧卡住或客户端反复重试，线程数会持续上涨（每线程默认约 1MB 栈），
 * 最终把进程拖垮。
 *
 * <p>统一改为「固定线程数 + 有界队列 + AbortPolicy」：
 * 队列满时 {@link ThreadPoolExecutor.AbortPolicy} 抛 {@link java.util.concurrent.RejectedExecutionException}，
 * 由调用方给出明确的「服务繁忙」提示，而不是静默排队等到超时。
 * 线程命名带业务前缀，便于 jstack/日志定位。
 */
public final class AppExecutors {

    private AppExecutors() {
    }

    /**
     * @param namePrefix  线程名前缀（如 written-sse）
     * @param threads     并发线程数
     * @param queueCapacity 等待队列容量（0 表示不排队，超出即拒绝）
     */
    public static ThreadPoolExecutor bounded(String namePrefix, int threads, int queueCapacity) {
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, namePrefix + "-" + seq.incrementAndGet());
            // 守护线程：不阻止 JVM 退出（容器里仍有 PreDestroy 主动 shutdown）
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                threads, threads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(Math.max(1, queueCapacity)),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }
}
