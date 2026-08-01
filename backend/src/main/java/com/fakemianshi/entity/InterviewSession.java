package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试会话：一次笔试/模拟面试的运行实例。
 */
@Data
@Entity
@Table(name = "interview_session")
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属项目 ID */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 关联的岗位要求 ID */
    @Column(name = "position_requirement_id")
    private Long positionRequirementId;

    /** 类型：WRITTEN（笔试）/ MOCK（模拟面试）/ BOTH（两者） */
    @Column(length = 20)
    private String type;

    /** 状态：PENDING / IN_PROGRESS / COMPLETED / ABANDONED */
    @Column(length = 20)
    private String status;

    /** 时长限制（分钟） */
    @Column(name = "time_limit")
    private Integer timeLimit;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
