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

    /** 知识盲区（元素为 {"point":"知识点","explanation":"答案要点"}，兼容旧版纯字符串） */
    private List<Object> knowledgeGaps;

    /** 能力等级（如 中级工程师） */
    private String overallLevel;

    /** 预期薪资范围（如 20k-30k） */
    private String expectedSalaryRange;

    /** 性格总结 */
    private String personalitySummary;

    /** 性格特质列表 */
    private List<String> characterTraits;

    /** 性格缺陷与改进（元素为 {"defect":"...","improvement":"..."}） */
    private List<Object> characterDefects;

    /** 沟通表达评估（仅模拟面试） */
    private String communicationEvaluation;

    /** 改进计划 {topics: [{topic,action,example}], suggestions: [...]} */
    private Map<String, Object> improvementPlan;

    /** 逐题分析列表 */
    private List<Map<String, Object>> questionAnalyses;
}
