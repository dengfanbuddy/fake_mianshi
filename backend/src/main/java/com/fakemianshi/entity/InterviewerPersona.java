package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试官人设：模拟面试中使用的面试官角色配置。
 */
@Data
@TableName("interviewer_persona")
public class InterviewerPersona {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 人设名称 */
    private String name;

    /** 人设描述 */
    private String description;

    /** 风格配置（JSON） */
    @TableField("style_config")
    private String styleConfig;

    /** 是否系统预置 */
    @TableField("is_preset")
    private Boolean isPreset;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    }
