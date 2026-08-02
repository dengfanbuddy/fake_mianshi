package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试项目：一次求职准备/面试练习的主题项目。
 */
@Data
@TableName("interview_project")
public class InterviewProject {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目名称 */
    private String name;

    /** 目标岗位/职业（如 Java后端开发、产品经理），用于按职业匹配面试提示词 */
    @TableField("target_position")
    private String targetPosition;

    /** 项目描述 */
    private String description;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
