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

    /** 考试时长限制（分钟） */
    private Integer timeLimit;

    /** 本次考试实际用时（秒，从服务端记录的开始时间算到交卷时刻） */
    private Long elapsedSeconds;

    /**
     * 是否超时提交：实际用时超出「时长限制 + 宽限期」时为 true。
     * 超时仍照常判分并保存作答（不销毁用户答案），但成绩需自行折扣看待。
     */
    private Boolean expired;

    /** 逐题判分结果 */
    private List<QuestionResult> results;
}
