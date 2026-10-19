package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 薄弱知识点标签：用于追踪用户在哪些知识点上掌握不足。
 *
 * <p>一个知识点可能出现多种写法（LLM 每轮现起名字），归并逻辑见
 * {@link com.fakemianshi.util.KnowledgePointNormalizer} 与
 * {@link com.fakemianshi.service.WeaknessTagService}：
 * 同一个知识点只留一行，{@code knowledgePoint} 存代表写法，其余写法进 {@code aliases}。
 */
@Data
@TableName("weakness_tag")
public class WeaknessTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    @TableField("project_id")
    private Long projectId;

    /** 知识点名称（归并后保留的代表写法） */
    @TableField("knowledge_point")
    private String knowledgePoint;

    /** 归一化后的知识点键：同义写法共享同一个值，用于归并与匹配 */
    @TableField("canonical_point")
    private String canonicalPoint;

    /** 同义写法列表的 JSON 数组字符串（落库原文，接口输出请用 {@link #aliasList}） */
    @TableField("aliases")
    private String aliases;

    /** 同义写法列表：只用于接口输出，不落库 */
    @TableField(exist = false)
    private List<String> aliasList;

    /** 掌握程度：WEAK / FAIR / GOOD / STRONG */
    @TableField("mastery_level")
    private String masteryLevel;

    /** 出现次数 */
    @TableField("occurrence_count")
    private Integer occurrenceCount;

    /** 最近一次关联的面试会话 ID */
    @TableField("last_session_id")
    private Long lastSessionId;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
