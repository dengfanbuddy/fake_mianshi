package com.fakemianshi.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 归一化算法的回归测试：样本是真实项目跑了几轮分析后积累的 69 条弱点标签
 * （src/test/resources/weakness-tags.sample.txt）。
 *
 * <p>调阈值时这些断言就是护栏：既防止漏合并（列表依旧吵），也防止错合并
 * （把「死锁检测」并进「线程 dump 死锁识别」这种，会让统计和掌握度全乱）。
 */
class KnowledgePointNormalizerTest {

    private static List<String> sample;
    private static List<List<Integer>> groups;

    @BeforeAll
    static void loadSample() throws Exception {
        sample = new ArrayList<>();
        try (InputStream in = KnowledgePointNormalizerTest.class.getClassLoader()
                .getResourceAsStream("weakness-tags.sample.txt")) {
            assertNotNull(in, "缺少测试资源 weakness-tags.sample.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String text = line.trim();
                if (!text.isEmpty() && !text.startsWith("#")) {
                    sample.add(text);
                }
            }
        }
        assertEquals(69, sample.size(), "样本文件应当有 69 条知识点");
        groups = KnowledgePointNormalizer.groupByTopic(sample);
    }

    @Test
    @DisplayName("真实 69 条 —— 归并成 23 个主题")
    void mergesRealSampleInto23Topics() {
        // 调参依据：0.55 → 33 个主题（漏合并），0.50 → 27，0.45 → 23，0.40 → 18 且开始错合并
        assertEquals(23, groups.size(), () -> "实际分簇: " + describeAll());
    }

    @Test
    @DisplayName("LongAdder 的 12 种写法归一成一条")
    void mergesLongAdderVariants() {
        List<String> cluster = clusterContaining("LongAdder.sum()弱一致性");
        assertEquals(12, cluster.size(), () -> "实际: " + cluster);
        assertTrue(cluster.contains("LongAdder 底层原理（Striped64）"));
        assertTrue(cluster.contains("JUC并发工具（LongAdder vs AtomicLong）"));
        assertTrue(cluster.contains("LongAdder与AtomicLong的区别及底层原理"));
    }

    @Test
    @DisplayName("Redis / Redisson 分布式锁的 18 种写法归一成一条")
    void mergesRedisLockVariants() {
        List<String> cluster = clusterContaining("Redisson看门狗机制");
        assertEquals(18, cluster.size(), () -> "实际: " + cluster);
        assertTrue(cluster.contains("Redis单线程模型与Lua原子性"));
        assertTrue(cluster.contains("Redisson 加锁 Lua 脚本与原子性"));
    }

    @Test
    @DisplayName("线上故障排查 / CPU 飙高的 10 种写法归一成一条")
    void mergesTroubleshootingVariants() {
        List<String> cluster = clusterContaining("CPU飙高排查SOP");
        assertEquals(10, cluster.size(), () -> "实际: " + cluster);
        assertTrue(cluster.contains("JVM线程快照与CPU飙高排查"));
        assertTrue(cluster.contains("线上故障排查命令与流程"));
    }

    @Test
    @DisplayName("精确性：短名与不同知识点不能被合并")
    void keepsDistinctTopicsApart() {
        // 「死锁检测」只有 4 个字，一旦允许它和「线程dump死锁与锁竞争识别」合并，
        // 就会顺着链条把整片死锁/线程 dump 的内容都并进来
        assertFalse(KnowledgePointNormalizer.sameTopic("死锁检测", "线程dump死锁与锁竞争识别"));
        assertFalse(KnowledgePointNormalizer.sameTopic("死锁检测", "jstack死锁与锁竞争识别"));
        // 完全不同领域
        assertFalse(KnowledgePointNormalizer.sameTopic("RPC失败处理策略（重试、超时、降级、补偿）", "HashMap并发死循环"));
        assertFalse(KnowledgePointNormalizer.sameTopic("GM后台重构的技术方案与量化收益", "线程池与线程分配策略"));
        // redis 与 redisson 只共享 ASCII 前缀，最长公共子串比值会误判成 0.56，Dice 只有 0.36
        assertTrue(KnowledgePointNormalizer.similarity(
                KnowledgePointNormalizer.canonical("Redis单线程模型与多命令原子性问题"),
                KnowledgePointNormalizer.canonical("Redisson看门狗与leaseTime"))
                < KnowledgePointNormalizer.SAME_TOPIC_THRESHOLD);
    }

    @Test
    @DisplayName("召回：措辞不同的同一知识点要合并")
    void mergesDifferentWording() {
        assertTrue(KnowledgePointNormalizer.sameTopic("架构决策量化指标", "架构拆分量化指标"));
        assertTrue(KnowledgePointNormalizer.sameTopic("LongAdder底层原理", "LongAdder与AtomicLong的区别及适用场景"));
        assertTrue(KnowledgePointNormalizer.sameTopic("HashMap 并发死循环与热点代码", "HashMap并发死循环"));
        assertTrue(KnowledgePointNormalizer.sameTopic("线程亲和性哈希分发", "线程亲和性与顺序保证"));
        assertTrue(KnowledgePointNormalizer.sameTopic("Redisson 看门狗机制", "Redisson看门狗与leaseTime"));
    }

    @Test
    @DisplayName("归一化：去噪声词、去括号但保留括号内容、不毁「亲和性」")
    void canonicalizesAsExpected() {
        assertEquals("longadderstriped64", KnowledgePointNormalizer.canonical("LongAdder 底层原理（Striped64）"));
        assertEquals(KnowledgePointNormalizer.canonical("LongAdder底层原理"),
                KnowledgePointNormalizer.canonical("LongAdder底层实现原理"));
        assertEquals("线上故障排查", KnowledgePointNormalizer.canonical("线上故障排查方法论"));
        // 单字连词不能删：「线程亲和性」里的「和」不是连词
        assertTrue(KnowledgePointNormalizer.canonical("线程亲和性与顺序保证").contains("亲和性"));
        assertEquals("", KnowledgePointNormalizer.canonical("（）  -- "));
        assertEquals("", KnowledgePointNormalizer.canonical(null));
    }

    @Test
    @DisplayName("代表名取簇内平均相似度最高的那个，而不是最具体的那个")
    void picksCentroidRepresentative() {
        List<Integer> cluster = clusterIndexOf("LongAdder.sum()弱一致性");
        int rep = KnowledgePointNormalizer.pickRepresentative(sample, cluster);
        // 「LongAdder内部执行路径」这种太具体，当标题会误导；medoid 应当是「LongAdder底层原理」
        assertEquals("LongAdder底层原理", sample.get(rep));
    }

    @Test
    @DisplayName("空输入与单元素输入不炸")
    void handlesTinyInput() {
        assertTrue(KnowledgePointNormalizer.groupByTopic(null).isEmpty());
        assertTrue(KnowledgePointNormalizer.groupByTopic(List.of()).isEmpty());
        List<List<Integer>> single = KnowledgePointNormalizer.groupByTopic(List.of("死锁检测"));
        assertEquals(1, single.size());
        assertEquals(List.of(0), single.get(0));
        assertEquals(-1, KnowledgePointNormalizer.pickRepresentative(sample, List.of()));
    }

    private static List<Integer> clusterIndexOf(String needle) {
        for (List<Integer> group : groups) {
            for (int index : group) {
                if (sample.get(index).equals(needle)) {
                    return group;
                }
            }
        }
        throw new AssertionError("样本里找不到: " + needle);
    }

    private static List<String> clusterContaining(String needle) {
        return clusterIndexOf(needle).stream().map(sample::get).toList();
    }

    private static String describeAll() {
        StringBuilder sb = new StringBuilder();
        for (List<Integer> group : groups) {
            sb.append('[').append(group.size()).append("] ")
                    .append(KnowledgePointNormalizer.describe(sample, group)).append('\n');
        }
        return sb.toString();
    }
}
