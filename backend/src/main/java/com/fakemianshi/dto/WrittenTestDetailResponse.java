package com.fakemianshi.dto;

import lombok.Data;

import java.util.List;

/**
 * 笔试详情响应（考试已结束，可查看答案与解析）。
 */
@Data
public class WrittenTestDetailResponse {

    /** 笔试会话 ID */
    private Long sessionId;

    /** 逐题结果（含用户答案、参考答案、对错、解析） */
    private List<QuestionResult> results;

    /** 总分（从作答记录汇总，100 制，保留 1 位小数） */
    private Double totalScore;

    /** 考试时长限制（分钟） */
    private Integer timeLimit;

    /** 实际用时（秒，从开始到交卷） */
    private Long elapsedSeconds;

    /** 是否超时提交（超出时长限制 + 宽限期）；报告页据此提示成绩仅供参考 */
    private Boolean expired;
}
