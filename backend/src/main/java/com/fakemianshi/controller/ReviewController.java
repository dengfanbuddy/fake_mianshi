package com.fakemianshi.controller;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.dto.RetryAnswerRequest;
import com.fakemianshi.dto.RetryAnswerResult;
import com.fakemianshi.dto.ReviewOverview;
import com.fakemianshi.dto.WrongQuestionItem;
import com.fakemianshi.service.ReviewService;
import com.fakemianshi.service.impl.ReviewServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 错题本 / 薄弱点复习接口。
 *
 * <p>服务器 context-path 为 /api，因此这里实际暴露为 /api/review/**。
 */
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 错题统计概览 */
    @GetMapping("/overview")
    public ApiResponse<ReviewOverview> overview(@RequestParam Long projectId) {
        return ApiResponse.success(reviewService.overview(projectId));
    }

    /**
     * 错题清单。
     *
     * @param status         all / pending / corrected
     * @param knowledgePoint 按知识点名过滤（归一化匹配，兼容旧调用方）
     * @param tagId          按弱点标签过滤（连带该标签的全部同义写法，优先于 knowledgePoint）
     */
    @GetMapping("/wrong-questions")
    public ApiResponse<List<WrongQuestionItem>> wrongQuestions(
            @RequestParam Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String knowledgePoint,
            @RequestParam(required = false) Long tagId) {
        if (!ReviewServiceImpl.isValidStatus(status)) {
            throw new BusinessException("status 只能是 all / pending / corrected，收到: " + status);
        }
        return ApiResponse.success(reviewService.listWrongQuestions(projectId, status, knowledgePoint, tagId));
    }

    /** 重练一道错题并重新判分 */
    @PostMapping("/wrong-questions/{questionId}")
    public ApiResponse<RetryAnswerResult> retry(@PathVariable Long questionId,
                                                @RequestBody(required = false) RetryAnswerRequest request) {
        String userAnswer = request == null ? null : request.getUserAnswer();
        return ApiResponse.success(reviewService.retry(questionId, userAnswer));
    }
}
