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
}
