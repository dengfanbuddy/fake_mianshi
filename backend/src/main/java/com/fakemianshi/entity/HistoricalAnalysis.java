package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史分析快照：保存某时间点的项目级分析结果。
 */
@Data
@Entity
@Table(name = "historical_analysis")
public class HistoricalAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属项目 ID */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 分析数据（JSON） */
    @Column(name = "analysis_data", length = 20000)
    private String analysisData;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @PrePersist
    public void prePersist() {
        this.generatedAt = LocalDateTime.now();
    }
}
