package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位要求：目标岗位的能力要求信息。
 */
@Data
@TableName("position_requirement")
public class PositionRequirement {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    @TableField("project_id")
    private Long projectId;

    /** 岗位名称 */
    @TableField("job_title")
    private String jobTitle;

    /** 工作经验要求 */
    private String experience;

    /** 职级要求 */
    private String seniority;

    /** 公司类型要求 */
    @TableField("company_type")
    private String companyType;

    /** 技术栈要求（JSON 数组） */
    @TableField("tech_stack")
    private String techStack;

    /** 岗位职责描述 */
    @TableField("job_description")
    private String jobDescription;

    /** 来源：RESUME（从简历提取）/ MANUAL（手动填写） */
    private String source;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    }
