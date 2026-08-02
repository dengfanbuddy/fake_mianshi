package com.fakemianshi.controller;

import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.dto.AnalysisResultDTO;
import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.entity.HistoricalAnalysis;
import com.fakemianshi.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    /** 分析流式推送用线程池 */
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

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
     * 分析已存在时直接推送 done（不重复生成）。
     */
    @GetMapping(value = "/stream/{sessionId}", produces = "text/event-stream")
    public SseEmitter streamSession(@PathVariable Long sessionId) {
        SseEmitter emitter = new SseEmitter(240_000L);

        // 分析已存在：直接返回完整报告
        try {
            AnalysisResultDTO dto = analysisService.getSessionAnalysis(sessionId);
            emitter.send(SseEmitter.event().name("done").data(dto));
            emitter.complete();
            return emitter;
        } catch (ResourceNotFoundException ignored) {
            // 未生成，进入流式生成
        } catch (IOException e) {
            emitter.complete();
            return emitter;
        }

        sseExecutor.execute(() -> {
            try {
                analysisService.analyzeSessionStream(sessionId, delta -> {
                    try {
                        emitter.send(SseEmitter.event().name("delta").data(delta));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE 推送中断", e);
                    }
                });
                // 生成完成后推送完整报告
                AnalysisResultDTO dto = analysisService.getSessionAnalysis(sessionId);
                emitter.send(SseEmitter.event().name("done").data(dto));
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
        return emitter;
    }

    /**
     * 历史综合分析；已有最新一条快照则直接返回，否则生成。
     */
    @GetMapping("/history/{projectId}")
    public ApiResponse<HistoricalAnalysis> history(@PathVariable Long projectId) {
        return ApiResponse.success(analysisService.getLatestHistory(projectId));
    }

    /**
     * 强制重新生成会话分析：先删除旧分析再重新分析。
     */
    @PostMapping("/refresh/{sessionId}")
    public ApiResponse<AnalysisResultDTO> refresh(@PathVariable Long sessionId) {
        return ApiResponse.success(analysisService.refreshAnalysis(sessionId));
    }
}
