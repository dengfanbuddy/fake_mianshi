package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 单题分析：面试中单个问题/题目的作答质量评估。
 */
@Data
@Entity
@Table(name = "question_analysis")
public class QuestionAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属面试会话 ID */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 关联的问题引用 ID（如笔试题 ID 或消息 ID） */
    @Column(name = "question_ref_id")
    private Long questionRefId;

    /** 问题内容 */
    @Column(name = "question_content", length = 5000)
    private String questionContent;

    /** 用户作答内容 */
    @Column(name = "answer_content", length = 10000)
    private String answerContent;

    /** 准确度评分（0-100） */
    private Double accuracy;

    /** 深度评分（0-100） */
    private Double depth;

    /** 清晰度评分（0-100） */
    private Double clarity;

    /** 流畅度评分（0-100） */
    private Double fluency;

    /** 语气/态度评价 */
    @Column(name = "tone_evaluation")
    private String toneEvaluation;

    /** 改进建议 */
    @Column(name = "improvement_suggestion", length = 5000)
    private String improvementSuggestion;
}
