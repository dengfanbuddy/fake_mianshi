package com.fakemianshi.controller;

import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.dto.WrittenTestDetailResponse;
import com.fakemianshi.dto.WrittenTestStartRequest;
import com.fakemianshi.dto.WrittenTestStartResponse;
import com.fakemianshi.dto.WrittenTestSubmitRequest;
import com.fakemianshi.dto.WrittenTestSubmitResponse;
import com.fakemianshi.service.WrittenTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 笔试接口。
 *
 * <p>注意：服务器 context-path 为 /api，因此这里的 /written-test 实际暴露为 /api/written-test。
 */
@Slf4j
@RestController
@RequestMapping("/written-test")
@RequiredArgsConstructor
public class WrittenTestController {

    private final WrittenTestService writtenTestService;

    /** 出题流式推送用线程池（长任务不阻塞 web 线程） */
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    /** 开始笔试：创建会话并生成题目 */
    @PostMapping("/start/{projectId}")
    public ApiResponse<WrittenTestStartResponse> start(@PathVariable Long projectId,
                                                       @RequestBody(required = false) WrittenTestStartRequest req) {
        return ApiResponse.success(writtenTestService.start(projectId, req));
    }

    /**
     * 流式开始笔试：SSE 推送 AI 出题过程（event: delta = markdown 增量片段），
     * 完成后推送 event: done = 完整响应 JSON；失败推送 event: error。
     */
    @PostMapping(value = "/stream-start/{projectId}", produces = "text/event-stream")
    public SseEmitter streamStart(@PathVariable Long projectId,
                                  @RequestBody(required = false) WrittenTestStartRequest req,
                                  jakarta.servlet.http.HttpServletResponse response) {
        // 禁用缓存：避免浏览器/代理缓存 SSE 响应导致重复回放
        response.setHeader("Cache-Control", "no-cache, no-transform");
        SseEmitter emitter = new SseEmitter(300_000L);
        sseExecutor.execute(() -> {
            try {
                WrittenTestStartResponse res = writtenTestService.streamStart(projectId, req,
                        delta -> sendOrThrow(emitter, "delta", delta),
                        reasoning -> sendOrThrow(emitter, "reasoning", reasoning));
                emitter.send(SseEmitter.event().name("done").data(res));
                emitter.complete();
            } catch (Exception e) {
                log.warn("流式出题失败: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data(
                            "AI 出题失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage())));
                } catch (IOException ignored) {
                    // 客户端已断开
                }
                emitter.complete();
            }
        });
        return emitter;
    }

    /** SSE 推送工具：客户端断开时以 RuntimeException 中断生成任务 */
    private void sendOrThrow(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            throw new RuntimeException("SSE 推送中断", e);
        }
    }

    /** 提交笔试：判分并结束会话 */
    @PostMapping("/submit/{sessionId}")
    public ApiResponse<WrittenTestSubmitResponse> submit(@PathVariable Long sessionId,
                                                         @RequestBody(required = false) WrittenTestSubmitRequest req) {
        return ApiResponse.success(writtenTestService.submit(sessionId, req));
    }

    /** 笔试详情（考试结束后查看结果/分析报告） */
    @GetMapping("/{sessionId}")
    public ApiResponse<WrittenTestDetailResponse> detail(@PathVariable Long sessionId) {
        return ApiResponse.success(writtenTestService.getDetail(sessionId));
    }
}
