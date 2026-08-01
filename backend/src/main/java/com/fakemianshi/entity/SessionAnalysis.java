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

    /** 知识盲区（JSON 数组） */
    @Column(name = "knowledge_gaps", length = 5000)
    private String knowledgeGaps;

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
