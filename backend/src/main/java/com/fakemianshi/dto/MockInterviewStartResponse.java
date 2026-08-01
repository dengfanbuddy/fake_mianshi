package com.fakemianshi.dto;

import lombok.Data;

/**
 * 开始模拟面试响应。
 */
@Data
public class MockInterviewStartResponse {

    /** 会话 ID */
    private Long sessionId;

    /** 本次面试使用的人设 ID */
    private Long personaId;

    /** 人设名称 */
    private String personaName;

    /** 面试官开场白（已作为第一条 INTERVIEWER 消息保存） */
    private String openingMessage;

    /** 面试大纲（JSON 字符串，原样返回前端展示，MVP 不持久化） */
    private String outline;
}
