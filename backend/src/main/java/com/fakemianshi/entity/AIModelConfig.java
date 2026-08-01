package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 模型配置：对接大模型服务的 API 配置。
 */
@Data
@TableName("ai_model_config")
public class AIModelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 服务提供商 */
    private String provider;

    /** API 地址 */
    @TableField("api_url")
    private String apiUrl;

    /** API 密钥 */
    @TableField("api_key")
    private String apiKey;

    /** 模型名称 */
    @TableField("model_name")
    private String modelName;

    /** 是否启用 */
    @TableField("is_active")
    private Boolean isActive;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    }
