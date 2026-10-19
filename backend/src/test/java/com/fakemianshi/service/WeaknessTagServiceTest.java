package com.fakemianshi.service;

import com.fakemianshi.dto.WeaknessConsolidateResult;
import com.fakemianshi.entity.WeaknessTag;
import com.fakemianshi.repository.WeaknessTagRepository;
import com.fakemianshi.service.impl.WeaknessTagServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 弱点标签归并测试：用内存列表模拟仓储，不碰数据库。
 *
 * <p>覆盖：写入时同义写法复用同一行并记别名、归并时求和与保留最差掌握度、
 * 原写法不丢、重复归并幂等、脏 JSON 不炸。
 */
class WeaknessTagServiceTest {

    private WeaknessTagRepository repository;
    private WeaknessTagService service;
    private final List<WeaknessTag> store = new ArrayList<>();
    private long idSequence = 1;

    @BeforeEach
    void setUp() {
        repository = mock(WeaknessTagRepository.class);
        service = new WeaknessTagServiceImpl(repository);
        when(repository.findByProjectId(anyLong())).thenAnswer(inv -> store.stream()
                .filter(t -> inv.<Long>getArgument(0).equals(t.getProjectId()))
                .collect(Collectors.toCollection(ArrayList::new)));
        when(repository.insert(any(WeaknessTag.class))).thenAnswer(inv -> {
            WeaknessTag tag = inv.getArgument(0);
            if (tag.getId() == null) {
                tag.setId(idSequence++);
            }
            store.add(tag);
            return 1;
        });
        when(repository.updateById(any(WeaknessTag.class))).thenAnswer(inv -> {
            WeaknessTag tag = inv.getArgument(0);
            store.removeIf(t -> t.getId().equals(tag.getId()));
            store.add(tag);
            return 1;
        });
        when(repository.deleteByIds(any())).thenAnswer(inv -> {
            java.util.Collection<?> ids = inv.getArgument(0);
            int before = store.size();
            store.removeIf(t -> ids.contains(t.getId()));
            return before - store.size();
        });
        when(repository.findAllProjectIds()).thenAnswer(inv -> store.stream()
                .map(WeaknessTag::getProjectId).distinct().toList());
    }

    @Test
    @DisplayName("写入：换一种写法命中同一行，并记进别名")
    void recordOccurrence_shouldReuseRowForVariantWording() {
        List<WeaknessTag> existing = new ArrayList<>();
        service.recordOccurrence(existing, 1L, 10L, "LongAdder底层原理");
        // 换一种说法：不该新建一行
        service.recordOccurrence(existing, 1L, 11L, "LongAdder 底层实现原理");

        assertEquals(1, store.size(), () -> "同义写法不该新增行: " + store);
        WeaknessTag tag = store.get(0);
        assertEquals(2, tag.getOccurrenceCount());
        assertEquals("WEAK", tag.getMasteryLevel(), "出现 2 次即为顽固弱点");
        assertEquals("LongAdder 底层实现原理", tag.getAliasList().get(0));
    }

    @Test
    @DisplayName("写入：不同知识点照旧各占一行")
    void recordOccurrence_shouldKeepDistinctTopicsApart() {
        List<WeaknessTag> existing = new ArrayList<>();
        service.recordOccurrence(existing, 1L, 10L, "JVM垃圾回收");
        service.recordOccurrence(existing, 1L, 10L, "Spring事务传播机制");
        assertEquals(2, store.size());
    }

    @Test
    @DisplayName("归并：同义写法合并成一行，次数求和，原写法进别名")
    void consolidate_shouldMergeAndSumOccurrences() {
        store.add(tag(1L, "LongAdder底层原理", "FAIR", 1));
        store.add(tag(2L, "LongAdder底层实现原理", "WEAK", 1));
        store.add(tag(3L, "LongAdder与AtomicLong的区别及适用场景", "FAIR", 2));
        store.add(tag(4L, "RPC失败处理策略", "FAIR", 1));

        WeaknessConsolidateResult result = service.consolidate(1L);

        assertEquals(4, result.getBefore());
        assertEquals(2, result.getAfter());
        assertEquals(2, result.getMerged());
        WeaknessTag merged = store.stream()
                .filter(t -> t.getKnowledgePoint().contains("LongAdder")).findFirst().orElseThrow();
        assertEquals(4, merged.getOccurrenceCount(), "1 + 1 + 2 = 4");
        assertEquals("WEAK", merged.getMasteryLevel(), "取簇内最差的掌握度");
        assertNotNull(merged.getCanonicalPoint());
        assertEquals(2, merged.getAliasList().size(), () -> "别名: " + merged.getAliasList());
    }

    @Test
    @DisplayName("归并：掌握度只取更差的，不能被次数冲回 WEAK")
    void consolidate_shouldNotDowngradeCorrectedTag() {
        // 两条都已经订正成 GOOD，合并后仍是 GOOD（不能因为次数变多就退回 WEAK）
        store.add(tag(1L, "HashMap并发死循环", "GOOD", 1));
        store.add(tag(2L, "HashMap 并发死循环与热点代码", "GOOD", 1));

        service.consolidate(1L);

        assertEquals(1, store.size());
        assertEquals("GOOD", store.get(0).getMasteryLevel());
    }

    @Test
    @DisplayName("归并幂等：重复跑不会再变化")
    void consolidate_isIdempotent() {
        store.add(tag(1L, "LongAdder底层原理", "WEAK", 1));
        store.add(tag(2L, "LongAdder 底层原理（Striped64）", "WEAK", 1));

        WeaknessConsolidateResult first = service.consolidate(1L);
        WeaknessConsolidateResult second = service.consolidate(1L);

        assertEquals(1, first.getMerged());
        assertEquals(0, second.getMerged(), "第二次没有可合并的");
        assertEquals(1, store.size());
        assertEquals(1, store.get(0).getAliasList().size(), "别名也不该重复累加");
    }

    @Test
    @DisplayName("归并：旧库没有 canonical_point 也能回填，脏别名不炸")
    void consolidate_shouldBackfillCanonicalAndSurviveDirtyAliases() {
        WeaknessTag tag = tag(1L, "线上故障排查方法论", "FAIR", 1);
        tag.setAliases("not-json");
        store.add(tag);

        service.consolidate(1L);

        assertEquals("线上故障排查", store.get(0).getCanonicalPoint());
        assertTrue(service.aliasesOf(store.get(0)).isEmpty());
    }

    @Test
    @DisplayName("listByProject 会把别名解析好供接口输出")
    void listByProject_shouldExposeAliasList() {
        WeaknessTag tag = tag(1L, "LongAdder底层原理", "FAIR", 1);
        tag.setAliases("[\"LongAdder 源码执行路径\"]");
        store.add(tag);

        List<WeaknessTag> list = service.listByProject(1L);

        assertEquals(1, list.size());
        assertEquals(List.of("LongAdder 源码执行路径"), list.get(0).getAliasList());
    }

    @Test
    @DisplayName("null / 空白输入不炸")
    void handlesEmptyInput() {
        assertNull(service.recordOccurrence(new ArrayList<>(), 1L, 1L, "  "));
        assertNull(service.findSameTopic(List.of(), "任意"));
        assertEquals(0, service.consolidate(null).getAfter());
        assertEquals(0, service.consolidate(99L).getAfter());
    }

    private WeaknessTag tag(Long id, String point, String mastery, int count) {
        WeaknessTag tag = new WeaknessTag();
        tag.setId(id);
        tag.setProjectId(1L);
        tag.setKnowledgePoint(point);
        tag.setMasteryLevel(mastery);
        tag.setOccurrenceCount(count);
        return tag;
    }
}
