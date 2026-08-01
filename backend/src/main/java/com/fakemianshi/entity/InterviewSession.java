package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试会话：一次笔试/模拟面试的运行实例。
 */
@Data
@TableName("interview_session")
public class InterviewSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    @TableField("project_id")
    private Long projectId;

    /** 关联的岗位要求 ID */
    @TableField("position_requirement_id")
    private Long positionRequirementId;

    /** 类型：WRITTEN（笔试）/ MOCK（模拟面试）/ BOTH（两者） */
    private String type;

    /** 状态：PENDING / IN_PROGRESS / COMPLETED / ABANDONED */
    private String status;

    /** 时长限制（分钟） */
    @TableField("time_limit")
    private Integer timeLimit;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    }
