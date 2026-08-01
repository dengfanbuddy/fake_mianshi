package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话整体分析：一次面试会话结束后的综合评估报告。
 */
@Data
@Entity
@Table(name = "session_analysis")
public class SessionAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属面试会话 ID */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 综合得分 */
    @Column(name = "overall_score")
    private Double overallScore;

    /** 优势（JSON 数组） */
    @Column(length = 5000)
    private String strengths;

    /** 不足（JSON 数组） */
    @Column(length = 5000)
    private String weaknesses;

    /** 知识盲区（JSON 数组，元素为 {"point":"知识点","explanation":"答案要点"}） */
    @Column(name = "knowledge_gaps", length = 5000)
    private String knowledgeGaps;

    /** 能力等级总结（如 初级/中级/高级/专家级） */
    @Column(name = "overall_level", length = 200)
    private String overallLevel;

    /** 预期薪资范围（如 20k-30k） */
    @Column(name = "expected_salary_range", length = 100)
    private String expectedSalaryRange;

    /** 性格总结 */
    @Column(name = "personality_summary", length = 5000)
    private String personalitySummary;

    /** 性格特质（JSON 数组） */
    @Column(name = "character_traits", length = 3000)
    private String characterTraits;

    /** 性格缺陷与改进（JSON 数组，元素为 {"defect":"...","improvement":"..."}） */
    @Column(name = "character_defects", length = 5000)
    private String characterDefects;

    /** 沟通表达评估 */
    @Column(name = "communication_evaluation", length = 5000)
    private String communicationEvaluation;

    /** 改进计划（JSON） */
    @Column(name = "improvement_plan", length = 10000)
    private String improvementPlan;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
