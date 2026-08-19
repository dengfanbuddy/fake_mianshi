package com.fakemianshi.dto;

import lombok.Data;

import java.util.List;

/**
 * 开始笔试响应：会话信息 + 脱敏后的题目列表。
 */
@Data
public class WrittenTestStartResponse {

    /** 笔试会话 ID */
    private Long sessionId;

    /** 时长限制（分钟） */
    private Integer timeLimit;

    /** 考试开始时间（epoch 毫秒），前端据此计算截止时间，避免刷新后重置倒计时 */
    private Long startedAt;

    /** 题目列表（不包含答案与解析） */
    private List<ExamQuestion> questions;
}
