package com.fakemianshi.controller;

import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.dto.AnalysisResultDTO;
import com.fakemianshi.dto.HistoryStatusDTO;
import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.entity.HistoricalAnalysis;
import com.fakemianshi.entity.SessionAnalysis;
import com.fakemianshi.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * 面试分析接口。
 *
 * <p>注意：服务器 context-path 为 /api，因此这里的 /analysis 实际暴露为 /api/analysis。
 */
@Slf4j
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    /** 等待后台生成中的分析落库的最长时间（毫秒）：略短于 SSE 的 420 秒空闲上限 */
    private static final long ANALYZE_WAIT_TIMEOUT_MS = 240_000L;

    /** 等待期间轮询分析的间隔（毫秒） */
    private static final long ANALYZE_POLL_INTERVAL_MS = 1000L;

    /** 分析流式推送用线程池（有界：单任务最长可占线程数分钟） */
    private final ExecutorService sseExecutor =
            com.fakemianshi.config.AppExecutors.bounded("analysis-sse", 4, 8);

    @PreDestroy
    public void shutdown() {
        sseExecutor.shutdownNow();
    }

    /**
     * 获取会话分析报告；尚未生成时异步触发生成并返回 null，前端轮询等待（避免同步阻塞 60s+）。
     */
    @GetMapping("/session/{sessionId}")
    public ApiResponse<AnalysisResultDTO> sessionAnalysis(@PathVariable Long sessionId) {
        try {
            return ApiResponse.success(analysisService.getSessionAnalysis(sessionId));
        } catch (ResourceNotFoundException e) {
            analysisService.triggerAnalyze(sessionId);
            return ApiResponse.success(null);
        }
    }

    /**
     * 流式生成分析报告：SSE 推送 AI 分析过程（event: delta = markdown 增量片段），
     * 完成后推送 event: done = 完整报告 JSON；失败推送 event: error。
     * 分析已存在时直接推送 done（不重复生成）；force=true 时先删除旧分析再重新生成。
     */
    @GetMapping(value = "/stream/{sessionId}", produces = "text/event-stream")
    public SseEmitter streamSession(@PathVariable Long sessionId,
                                    @RequestParam(defaultValue = "false") boolean force,
                                    jakarta.servlet.http.HttpServletResponse response) {
        // 禁用缓存：避免浏览器/代理缓存 SSE 响应导致重复回放
        response.setHeader("Cache-Control", "no-cache, no-transform");
        SseEmitter emitter = new SseEmitter(420_000L);

        // 分析已存在：非强制时直接返回完整报告；强制时删除旧分析重新生成
        try {
            analysisService.getSessionAnalysis(sessionId);
            if (!force) {
                emitter.send(SseEmitter.event().name("done")
                        .data(analysisService.getSessionAnalysis(sessionId)));
                emitter.complete();
                return emitter;
            }
            // 强制重新生成：若另一任务正在生成（含上一个未结束的），避免并发写库冲突
            if (analysisService.isAnalyzing(sessionId)) {
                emitter.send(SseEmitter.event().name("error")
                        .data("分析正在生成中，请稍候刷新查看"));
                emitter.complete();
                return emitter;
            }
            analysisService.deleteAnalysis(sessionId);
        } catch (ResourceNotFoundException ignored) {
            // 未生成：进入流式生成。若已有后台任务在跑（如交卷后自动触发），
            // 交给下面执行体里的等待逻辑，不再提前返回"正在生成中"的错误。
        } catch (IOException e) {
            emitter.complete();
            return emitter;
        }

        try {
            sseExecutor.execute(() -> {
                try {
                    AnalysisResultDTO dto = null;
                    if (!analysisService.isAnalyzing(sessionId)) {
                        // 没人在生成就自己流式生成；若恰好被后台任务抢先（analyzingSessions 已在生成），
                        // 该方法会立即返回 null，走下面的等待分支
                        SessionAnalysis generated = analysisService.analyzeSessionStream(sessionId,
                                delta -> sendOrThrow(emitter, "delta", delta),
                                reasoning -> sendOrThrow(emitter, "reasoning", reasoning));
                        if (generated != null) {
                            // 生成完成后推送完整报告
                            dto = analysisService.getSessionAnalysis(sessionId);
                        }
                    }
                    if (dto == null) {
                        // 已有任务在生成（典型场景：交卷/结束面试后自动触发）：等它出结果再推送。
                        // 这里不能直接报"正在生成中"——用户从成绩页点进报告就会看到一个失败面板。
                        dto = awaitGeneratedAnalysis(sessionId, emitter);
                    }
                    if (dto == null) {
                        emitter.send(SseEmitter.event().name("error").data("分析仍在生成中，请稍候刷新查看"));
                    } else {
                        emitter.send(SseEmitter.event().name("done").data(dto));
                    }
                    emitter.complete();
                } catch (Exception e) {
                    log.warn("流式分析失败: sessionId={}, cause={}", sessionId, e.getMessage());
                    try {
                        emitter.send(SseEmitter.event().name("error").data(
                                "分析生成失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage())));
                    } catch (IOException ignored) {
                        // 客户端已断开
                    }
                    emitter.complete();
                }
            });
        } catch (RejectedExecutionException e) {
            // 线程池有界：并发分析任务占满时明确告知（前端会退回轮询，仍能拿到结果）
            log.warn("分析任务并发已满，拒绝新请求: sessionId={}", sessionId);
            try {
                emitter.send(SseEmitter.event().name("error").data("当前分析任务较多，请稍后刷新重试"));
            } catch (IOException ignored) {
                // 客户端已断开
            }
            emitter.complete();
        }
        return emitter;
    }

    /**
     * 等待后台正在生成的分析落库后再返回。
     *
     * <p>等待期间只发 SSE 注释行做保活与断连探测（前端不会触发任何监听事件），
     * 前端 8 秒收不到输出会自动切到轮询兜底，两条路都能拿到结果。
     *
     * @return 分析报告；超时、生成失败或客户端已断开时返回 null
     */
    private AnalysisResultDTO awaitGeneratedAnalysis(Long sessionId, SseEmitter emitter) {
        long deadline = System.currentTimeMillis() + ANALYZE_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (!analysisService.isAnalyzing(sessionId)) {
                try {
                    return analysisService.getSessionAnalysis(sessionId);
                } catch (ResourceNotFoundException e) {
                    // 生成已结束但没有结果（失败）或后台任务还没真正开始，继续短暂等待
                }
            }
            try {
                // 注释行只用于探活：send 抛 IOException 说明客户端已断开，没必要继续等
                emitter.send(SseEmitter.event().comment("waiting-analysis"));
                Thread.sleep(ANALYZE_POLL_INTERVAL_MS);
            } catch (IOException e) {
                log.debug("等待分析期间客户端已断开: sessionId={}", sessionId);
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /** SSE 推送工具：客户端断开时以 RuntimeException 中断生成任务 */
    private void sendOrThrow(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            throw new RuntimeException("SSE 推送中断", e);
        }
    }

    /**
     * 历史综合分析；已有最新一条快照则直接返回，否则生成。
     */
    @GetMapping("/history/{projectId}")
    public ApiResponse<HistoricalAnalysis> history(@PathVariable Long projectId) {
        return ApiResponse.success(analysisService.getLatestHistory(projectId));
    }

    /**
     * 历史综合分析状态：未完成分析的会话数、是否有新分析可重新综合。
     */
    @GetMapping("/history/status/{projectId}")
    public ApiResponse<HistoryStatusDTO> historyStatus(@PathVariable Long projectId) {
        return ApiResponse.success(analysisService.getHistoryStatus(projectId));
    }

    /**
     * 强制重新生成历史综合分析：删除旧快照后重新调用 LLM 生成。
     */
    @PostMapping("/history/refresh/{projectId}")
    public ApiResponse<HistoricalAnalysis> refreshHistory(@PathVariable Long projectId) {
        return ApiResponse.success(analysisService.refreshHistory(projectId));
    }

    /**
     * 强制重新生成会话分析：先删除旧分析再重新分析。
     */
    @PostMapping("/refresh/{sessionId}")
    public ApiResponse<AnalysisResultDTO> refresh(@PathVariable Long sessionId) {
        return ApiResponse.success(analysisService.refreshAnalysis(sessionId));
    }
}
