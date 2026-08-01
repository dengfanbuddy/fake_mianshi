package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史分析快照：保存某时间点的项目级分析结果。
 */
@Data
@TableName("historical_analysis")
public class HistoricalAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    @TableField("project_id")
    private Long projectId;

    /** 分析数据（JSON） */
    @TableField("analysis_data")
    private String analysisData;

    @TableField(value = "generated_at", fill = FieldFill.INSERT)
    private LocalDateTime generatedAt;

    }
