package com.fakemianshi.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 面试分析结果统一结构：供前端展示会话级分析与逐题分析。
 */
@Data
public class AnalysisResultDTO {

    /** 面试会话 ID */
    private Long sessionId;

    /** 综合得分（0-100） */
    private Double overallScore;

    /** 优势表现 */
    private List<String> strengths;

    /** 不足之处 */
    private List<String> weaknesses;

    /** 知识盲区（具体知识点） */
    private List<String> knowledgeGaps;

    /** 沟通表达评估（仅模拟面试） */
    private String communicationEvaluation;

    /** 改进计划 {topics: [...], suggestions: [...]} */
    private Map<String, Object> improvementPlan;

    /** 逐题分析列表 */
    private List<Map<String, Object>> questionAnalyses;
}
