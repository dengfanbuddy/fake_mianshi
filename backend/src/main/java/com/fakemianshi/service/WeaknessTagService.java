package com.fakemianshi.service;

import com.fakemianshi.dto.WeaknessConsolidateResult;
import com.fakemianshi.entity.WeaknessTag;

import java.util.List;

/**
 * 弱点标签的写入与归并。
 *
 * <p>存在的理由：{@code weakness_tag.knowledge_point} 是 LLM 现起的自由文本，
 * 同一个知识点会出现十几种写法。这里负责「同一个知识点只留一行」，
 * 归一化判定见 {@link com.fakemianshi.util.KnowledgePointNormalizer}。
 */
public interface WeaknessTagService {

    /** 项目下的弱点标签（已填好同义写法的解析结果 {@code aliasList}） */
    List<WeaknessTag> listByProject(Long projectId);

    /**
     * 记录一次知识点「出现」：命中已有同义标签就累加次数并记下这一种写法，否则新建一行。
     *
     * @param existing   该项目当前已有的标签（调用方持有列表，便于同批次内连续命中，方法会就地追加新建的行）
     * @param projectId  项目 ID
     * @param sessionId  触发本次出现的会话 ID
     * @param rawPoint   LLM 原始给出的知识点名
     * @return 命中或新建的标签；入参为空时返回 null
     */
    WeaknessTag recordOccurrence(List<WeaknessTag> existing, Long projectId, Long sessionId, String rawPoint);

    /** 在已有标签里找同一个知识点的行（归一化匹配），找不到返回 null */
    WeaknessTag findSameTopic(List<WeaknessTag> tags, String knowledgePoint);

    /** 归并某项目下的同义标签（幂等） */
    WeaknessConsolidateResult consolidate(Long projectId);

    /** 归并所有项目，返回总共合并掉的条数（启动时调用一次） */
    int consolidateAll();

    /** 标签的同义写法列表（解析 {@code aliases} 里的 JSON 数组，脏数据按空处理） */
    List<String> aliasesOf(WeaknessTag tag);

    /** 出现次数 → 掌握程度：≥2 次为 WEAK（顽固弱点），1 次为 FAIR（轻微） */
    static String resolveMasteryLevel(int occurrenceCount) {
        return occurrenceCount >= 2 ? "WEAK" : "FAIR";
    }
}
