package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 模型配置：对接大模型服务的 API 配置。
 */
@Data
@Entity
@Table(name = "ai_model_config")
public class AIModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 服务提供商 */
    @Column(nullable = false, length = 100)
    private String provider;

    /** API 地址 */
    @Column(name = "api_url", length = 500)
    private String apiUrl;

    /** API 密钥 */
    @Column(name = "api_key", length = 500)
    private String apiKey;

    /** 模型名称 */
    @Column(name = "model_name", length = 200)
    private String modelName;

    /** 是否启用 */
    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
