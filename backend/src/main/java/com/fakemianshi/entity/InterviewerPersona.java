package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试官人设：模拟面试中使用的面试官角色配置。
 */
@Data
@Entity
@Table(name = "interviewer_persona")
public class InterviewerPersona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 人设名称 */
    @Column(nullable = false, length = 200)
    private String name;

    /** 人设描述 */
    @Column(length = 2000)
    private String description;

    /** 风格配置（JSON） */
    @Column(name = "style_config", length = 5000)
    private String styleConfig;

    /** 是否系统预置 */
    @Column(name = "is_preset")
    private Boolean isPreset;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
