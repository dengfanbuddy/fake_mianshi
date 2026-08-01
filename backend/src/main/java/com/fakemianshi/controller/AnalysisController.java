package com.fakemianshi.controller;

import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.dto.AnalysisResultDTO;
import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.entity.HistoricalAnalysis;
import com.fakemianshi.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面试分析接口。
 *
 * <p>注意：服务器 context-path 为 /api，因此这里的 /analysis 实际暴露为 /api/analysis。
 */
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    /**
     * 获取会话分析报告；若尚未生成则自动触发分析后再返回，方便前端直接调用。
     */
    @GetMapping("/session/{sessionId}")
    public ApiResponse<AnalysisResultDTO> sessionAnalysis(@PathVariable Long sessionId) {
        AnalysisResultDTO dto;
        try {
            dto = analysisService.getSessionAnalysis(sessionId);
        } catch (ResourceNotFoundException e) {
            analysisService.analyzeSession(sessionId);
            dto = analysisService.getSessionAnalysis(sessionId);
        }
        return ApiResponse.success(dto);
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
