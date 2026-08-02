package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试提示词模板：按职业（occupation）× 场景（scene）分类存储。
 *
 * <p>occupation 为 "default" 时表示通用模板（所有职业的兜底）；
 * 场景取值见 {@link com.fakemianshi.service.PromptTemplateService.Scene}。
 * 模板文本中的 {@code {position}} 占位符在业务调用时替换为实际目标岗位。
 */
@Data
@TableName("prompt_template")
public class PromptTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 职业（default = 通用兜底） */
    private String occupation;

    /** 场景：WRITTEN_QUESTION / MOCK_OUTLINE / WRITTEN_ANALYSIS / MOCK_ANALYSIS / HISTORY_ANALYSIS */
    private String scene;

    /** 提示词模板文本（含 {position} 等占位符） */
    private String content;

    /** 是否启用 */
    @TableField("is_active")
    private Boolean isActive;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
