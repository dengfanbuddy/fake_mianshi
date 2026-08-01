package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 薄弱知识点标签：用于追踪用户在哪些知识点上掌握不足。
 */
@Data
@Entity
@Table(name = "weakness_tag")
public class WeaknessTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属项目 ID */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 知识点名称 */
    @Column(name = "knowledge_point", length = 200)
    private String knowledgePoint;

    /** 掌握程度：WEAK / FAIR / GOOD / STRONG */
    @Column(name = "mastery_level", length = 20)
    private String masteryLevel;

    /** 出现次数 */
    @Column(name = "occurrence_count")
    private Integer occurrenceCount;

    /** 最近一次关联的面试会话 ID */
    @Column(name = "last_session_id")
    private Long lastSessionId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.updatedAt = LocalDateTime.now();
    }
}
