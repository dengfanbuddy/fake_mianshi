package com.fakemianshi.controller;

import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.dto.WrittenTestDetailResponse;
import com.fakemianshi.dto.WrittenTestStartRequest;
import com.fakemianshi.dto.WrittenTestStartResponse;
import com.fakemianshi.dto.WrittenTestSubmitRequest;
import com.fakemianshi.dto.WrittenTestSubmitResponse;
import com.fakemianshi.service.WrittenTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔试接口。
 *
 * <p>注意：服务器 context-path 为 /api，因此这里的 /written-test 实际暴露为 /api/written-test。
 */
@RestController
@RequestMapping("/written-test")
@RequiredArgsConstructor
public class WrittenTestController {

    private final WrittenTestService writtenTestService;

    /** 开始笔试：创建会话并生成题目 */
    @PostMapping("/start/{projectId}")
    public ApiResponse<WrittenTestStartResponse> start(@PathVariable Long projectId,
                                                       @RequestBody(required = false) WrittenTestStartRequest req) {
        return ApiResponse.success(writtenTestService.start(projectId, req));
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
