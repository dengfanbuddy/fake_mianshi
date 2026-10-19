package com.fakemianshi.service.impl;

import com.fakemianshi.dto.WeaknessConsolidateResult;
import com.fakemianshi.entity.WeaknessTag;
import com.fakemianshi.repository.WeaknessTagRepository;
import com.fakemianshi.service.WeaknessTagService;
import com.fakemianshi.util.KnowledgePointNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 弱点标签服务实现。
 *
 * <p>归并规则（全部只依赖 {@link KnowledgePointNormalizer}，不调 LLM）：
 * <ul>
 *   <li>同一簇里挑「到簇内其它成员平均相似度最高」的写法作为保留行（medoid），
 *       因为出现次数最多的那个名字往往最具体，拿来当标题会误导；</li>
 *   <li>出现次数求和：一个知识点的 12 种写法各出现 1 次，就是这个知识点出现了 12 次；</li>
 *   <li>掌握程度取簇内**最差**的那个，不从次数重新推导 —— 否则「已订正成 GOOD」
 *       的行会被合并后的次数一冲，又变回 WEAK，等于把用户复习成果抹掉；</li>
 *   <li>其余写法全部进 {@code aliases}，界面上显示成「N 种说法」，原始信息不丢。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeaknessTagServiceImpl implements WeaknessTagService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 同义写法最多保留这么多条，防止长期运行后无限膨胀 */
    private static final int MAX_ALIASES = 50;

    /** 掌握程度排序：数字越小越差 */
    private static final Map<String, Integer> MASTERY_RANK = Map.of(
            "WEAK", 0, "FAIR", 1, "GOOD", 2, "STRONG", 3);

    private final WeaknessTagRepository weaknessTagRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WeaknessTag> listByProject(Long projectId) {
        List<WeaknessTag> tags = projectId == null ? List.of() : weaknessTagRepository.findByProjectId(projectId);
        for (WeaknessTag tag : tags) {
            tag.setAliasList(aliasesOf(tag));
        }
        return tags;
    }

    @Override
    public WeaknessTag recordOccurrence(List<WeaknessTag> existing, Long projectId, Long sessionId, String rawPoint) {
        if (rawPoint == null || rawPoint.isBlank()) {
            return null;
        }
        String point = rawPoint.trim();
        WeaknessTag hit = findSameTopic(existing, point);
        if (hit != null) {
            int count = (hit.getOccurrenceCount() == null ? 0 : hit.getOccurrenceCount()) + 1;
            hit.setOccurrenceCount(count);
            hit.setMasteryLevel(WeaknessTagService.resolveMasteryLevel(count));
            hit.setLastSessionId(sessionId);
            hit.setUpdatedAt(LocalDateTime.now());
            if (hit.getCanonicalPoint() == null || hit.getCanonicalPoint().isBlank()) {
                hit.setCanonicalPoint(KnowledgePointNormalizer.canonical(hit.getKnowledgePoint()));
            }
            // 换了写法就记进别名，界面上能看到「N 种说法」，也方便按任意写法筛错题
            Set<String> aliases = new LinkedHashSet<>(aliasesOf(hit));
            if (addAlias(aliases, point, hit.getKnowledgePoint())) {
                hit.setAliases(toAliasesJson(aliases));
                hit.setAliasList(new ArrayList<>(aliases));
            }
            weaknessTagRepository.updateById(hit);
            return hit;
        }
        WeaknessTag created = new WeaknessTag();
        created.setProjectId(projectId);
        created.setKnowledgePoint(point);
        created.setCanonicalPoint(KnowledgePointNormalizer.canonical(point));
        created.setMasteryLevel(WeaknessTagService.resolveMasteryLevel(1));
        created.setOccurrenceCount(1);
        created.setLastSessionId(sessionId);
        created.setUpdatedAt(LocalDateTime.now());
        weaknessTagRepository.insert(created);
        created.setAliasList(List.of());
        if (existing != null) {
            existing.add(created); // 供同批次后续知识点命中
        }
        return created;
    }

    @Override
    public WeaknessTag findSameTopic(List<WeaknessTag> tags, String knowledgePoint) {
        if (tags == null || tags.isEmpty() || knowledgePoint == null || knowledgePoint.isBlank()) {
            return null;
        }
        String canon = KnowledgePointNormalizer.canonical(knowledgePoint);
        if (canon.isEmpty()) {
            return null;
        }
        // 第一轮：归一化键精确命中（绝大多数情况走这条，成本 O(n)）
        for (WeaknessTag tag : tags) {
            if (canon.equals(canonicalOf(tag))) {
                return tag;
            }
            for (String alias : aliasesOf(tag)) {
                if (canon.equals(KnowledgePointNormalizer.canonical(alias))) {
                    return tag;
                }
            }
        }
        // 第二轮：模糊判定（措辞不同但确实是同一个知识点）
        for (WeaknessTag tag : tags) {
            if (KnowledgePointNormalizer.sameTopic(knowledgePoint, tag.getKnowledgePoint())) {
                return tag;
            }
        }
        return null;
    }

    @Override
    @Transactional
    public WeaknessConsolidateResult consolidate(Long projectId) {
        if (projectId == null) {
            return WeaknessConsolidateResult.of(0, 0);
        }
        List<WeaknessTag> tags = weaknessTagRepository.findByProjectId(projectId);
        if (tags == null || tags.isEmpty()) {
            return WeaknessConsolidateResult.of(0, 0);
        }
        int before = tags.size();
        List<String> names = tags.stream()
                .map(tag -> tag.getKnowledgePoint() == null ? "" : tag.getKnowledgePoint())
                .toList();
        List<List<Integer>> groups = KnowledgePointNormalizer.groupByTopic(names);
        int removed = 0;
        for (List<Integer> group : groups) {
            if (group.size() == 1) {
                refreshCanonical(tags.get(group.get(0)));
                continue;
            }
            mergeGroup(tags, names, group);
            removed += group.size() - 1;
        }
        int after = before - removed;
        if (removed > 0) {
            log.info("弱点标签归并完成: 项目 {} {} → {} 条", projectId, before, after);
        }
        return WeaknessConsolidateResult.of(before, after);
    }

    @Override
    public int consolidateAll() {
        int merged = 0;
        List<Long> projectIds;
        try {
            projectIds = weaknessTagRepository.findAllProjectIds();
        } catch (Exception e) {
            log.warn("读取弱点标签项目列表失败，跳过启动归并: {}", e.getMessage());
            return 0;
        }
        for (Long projectId : projectIds) {
            try {
                merged += consolidate(projectId).getMerged();
            } catch (Exception e) {
                // 归并失败绝不能挡住启动：老库可能还没补上 canonical_point 列
                log.warn("项目 {} 的弱点标签归并失败（忽略）: {}", projectId, e.getMessage());
            }
        }
        if (merged > 0) {
            log.info("启动时归并弱点标签：共合并 {} 条同义写法", merged);
        }
        return merged;
    }

    @Override
    public List<String> aliasesOf(WeaknessTag tag) {
        if (tag == null || tag.getAliases() == null || tag.getAliases().isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = MAPPER.readTree(tag.getAliases());
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<String> list = new ArrayList<>(node.size());
            for (JsonNode item : node) {
                String text = item.asText("").trim();
                if (!text.isEmpty()) {
                    list.add(text);
                }
            }
            return list;
        } catch (JacksonException e) {
            // 脏数据按没有别名处理，绝不因为解析失败让整个列表接口报错
            return List.of();
        }
    }

    // ---------------- 内部 ----------------

    private void mergeGroup(List<WeaknessTag> tags, List<String> names, List<Integer> group) {
        int repIndex = KnowledgePointNormalizer.pickRepresentative(names, group);
        WeaknessTag kept = tags.get(repIndex);

        int occurrences = 0;
        String worstMastery = null;
        Long lastSessionId = null;
        LocalDateTime newest = null;
        Set<String> aliases = new LinkedHashSet<>(aliasesOf(kept));
        aliases.remove(kept.getKnowledgePoint());
        List<Long> obsoleteIds = new ArrayList<>();

        for (int index : group) {
            WeaknessTag tag = tags.get(index);
            occurrences += tag.getOccurrenceCount() == null ? 1 : tag.getOccurrenceCount();
            worstMastery = worseMastery(worstMastery, tag.getMasteryLevel());
            if (newest == null || (tag.getUpdatedAt() != null && tag.getUpdatedAt().isAfter(newest))) {
                newest = tag.getUpdatedAt();
                lastSessionId = tag.getLastSessionId();
            }
            if (index != repIndex) {
                addAlias(aliases, tag.getKnowledgePoint(), kept.getKnowledgePoint());
                for (String alias : aliasesOf(tag)) {
                    addAlias(aliases, alias, kept.getKnowledgePoint());
                }
                if (tag.getId() != null) {
                    obsoleteIds.add(tag.getId());
                }
            }
        }

        kept.setOccurrenceCount(occurrences);
        if (worstMastery != null) {
            kept.setMasteryLevel(worstMastery);
        }
        if (lastSessionId != null) {
            kept.setLastSessionId(lastSessionId);
        }
        kept.setCanonicalPoint(KnowledgePointNormalizer.canonical(kept.getKnowledgePoint()));
        kept.setAliases(toAliasesJson(aliases));
        kept.setAliasList(new ArrayList<>(aliases));
        if (newest != null) {
            kept.setUpdatedAt(newest);
        }
        weaknessTagRepository.updateById(kept);
        if (!obsoleteIds.isEmpty()) {
            weaknessTagRepository.deleteByIds(obsoleteIds);
        }
        log.info("弱点标签归并: 「{}」← {} 种写法（累计出现 {} 次，保留别名 {} 条）",
                kept.getKnowledgePoint(), group.size(), occurrences, aliases.size());
    }

    /** 单行标签的归一化键补写（旧库升级后没有值，幂等回填） */
    private void refreshCanonical(WeaknessTag tag) {
        String canon = KnowledgePointNormalizer.canonical(tag.getKnowledgePoint());
        if (canon.isEmpty() || canon.equals(tag.getCanonicalPoint())) {
            return;
        }
        tag.setCanonicalPoint(canon);
        weaknessTagRepository.updateById(tag);
    }

    private static String canonicalOf(WeaknessTag tag) {
        if (tag.getCanonicalPoint() != null && !tag.getCanonicalPoint().isBlank()) {
            return tag.getCanonicalPoint();
        }
        return KnowledgePointNormalizer.canonical(tag.getKnowledgePoint());
    }

    /** 加入一条同义写法；与代表写法字面相同、或已存在、或超出上限则不记，返回是否有变化 */
    private static boolean addAlias(Set<String> aliases, String alias, String representative) {
        if (alias == null || alias.isBlank()) {
            return false;
        }
        String text = alias.trim();
        if (text.equals(representative)) {
            return false;
        }
        if (aliases.contains(text) || aliases.size() >= MAX_ALIASES) {
            return false;
        }
        return aliases.add(text);
    }

    /** 取更差（更薄弱）的掌握程度；null 视为未知，不参与比较 */
    private static String worseMastery(String current, String candidate) {
        if (candidate == null || MASTERY_RANK.get(candidate) == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        return MASTERY_RANK.get(candidate) < MASTERY_RANK.get(current) ? candidate : current;
    }

    private static String toAliasesJson(Set<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(new ArrayList<>(aliases));
        } catch (JacksonException e) {
            log.warn("序列化弱点标签别名失败（按空处理）: {}", e.getMessage());
            return "[]";
        }
    }
}
