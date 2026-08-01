package com.fakemianshi.dto;

import lombok.Data;

import java.util.List;

/**
 * 提交笔试响应：总分 + 逐题判分结果。
 */
@Data
public class WrittenTestSubmitResponse {

    /** 笔试会话 ID */
    private Long sessionId;

    /** 总分（100 制，保留 1 位小数） */
    private Double totalScore;

    /** 题目总数 */
    private Integer totalQuestions;

    /** 自动判分正确的题数 */
    private Integer correctCount;

    /** 逐题判分结果 */
    private List<QuestionResult> results;
}
