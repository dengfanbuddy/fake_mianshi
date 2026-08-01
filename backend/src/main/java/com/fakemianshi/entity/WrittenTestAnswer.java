package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 笔试作答：用户对一道笔试题的作答及判分结果。
 */
@Data
@Entity
@Table(name = "written_test_answer")
public class WrittenTestAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的笔试题 ID */
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    /** 所属面试会话 ID */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 用户作答内容 */
    @Column(name = "user_answer", length = 5000)
    private String userAnswer;

    /** 是否正确 */
    @Column(name = "is_correct")
    private Boolean isCorrect;

    /** 得分 */
    private Double score;
}
