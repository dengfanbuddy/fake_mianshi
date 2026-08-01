package com.fakemianshi.service;

import com.fakemianshi.entity.WrittenTestQuestion;

import java.util.List;

/**
 * 出题引擎：基于职位需求、简历与弱点标签，调用 LLM 生成笔试题与模拟面试大纲。
 */
public interface QuestionGenerationService {

    /**
     * 生成笔试题：按 questionCount 控制总数，保存到 WrittenTestQuestion 并返回。
     */
    List<WrittenTestQuestion> generateWrittenTestQuestions(Long sessionId, Long projectId, int questionCount);

    /**
     * 生成模拟面试大纲（JSON 文本），返回给前端展示。
     */
    String generateMockInterviewOutline(Long projectId);

    /**
     * 生成面试官开场白（自然语言一段话，非 JSON）。
     */
    String generateOpeningMessage(Long projectId, Long personaId);
}
