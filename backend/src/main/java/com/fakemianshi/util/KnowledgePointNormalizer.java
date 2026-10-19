package com.fakemianshi.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识点名称归一化：判断两个自由文本的知识点名是不是「同一个知识点」。
 *
 * <p>为什么需要：{@code weakness_tag.knowledge_point} 是 LLM 每次分析时现起的名字，
 * 同一个知识点会被写成十几种样子。实测一个项目跑几轮之后，库里 69 条标签里有
 * 12 条其实是 LongAdder、18 条其实是 Redis 分布式锁。后果不只是列表变吵：
 * <ul>
 *   <li>{@code occurrence_count} 永远到不了 2，{@code resolveMasteryLevel} 就永远判不出 WEAK（顽固弱点）；</li>
 *   <li>「已订正 → 掌握度上调」按名字精确匹配，名字对不上就静默失效。</li>
 * </ul>
 *
 * <p>算法是确定性、可解释、不调 LLM 的三步：
 * <ol>
 *   <li><b>归一</b>：NFKC（全角转半角）→ 只保留字母数字汉字（括号、标点、空格全部丢弃，但保留括号里的内容）
 *       → 小写 → 反复删掉噪声词（原理/底层/实现/区别/适用场景/方法论…）</li>
 *   <li><b>相似度</b>：字符 bigram 的 Dice 系数。中文不用分词，且与词序无关；
 *       比最长公共子串更稳——"redis单线程模型…" 与 "redisson看门狗…" 的最长公共子串是
 *       "redis"（比值 0.56，会被误合并），而 Dice 只有 0.36（正确分开）。</li>
 *   <li><b>判定同一知识点</b>：归一后相等 → 一方包含另一方（短的一侧 ≥6 字）→ Dice ≥ 0.45</li>
 * </ol>
 *
 * <p>刻意不删单字连词（与/及/和/的）：「线程亲和性」里的「和」与连词「和」无法区分，
 * 删了会得到「线程亲性」，反而和「线程池哈希分发」更难匹配。
 */
public final class KnowledgePointNormalizer {

    /** 归一结果短于这个长度就不做模糊匹配（「死锁」「幂等」这类短名容易误合并） */
    private static final int MIN_FUZZY_LEN = 4;

    /** 「一方包含另一方」规则要求短的一侧至少这么长，避免「死锁」吞掉一切 */
    private static final int MIN_CONTAINS_LEN = 6;

    /** bigram Dice 阈值：0.45 在 69 条真实数据上得到 23 个主题（0.5 会漏掉「线程亲和性哈希分发」） */
    static final double SAME_TOPIC_THRESHOLD = 0.45;

    /**
     * 噪声词：只放多字词，且按长度降序删除。
     * 这些词只是描述角度（原理/区别/适用场景/方法论），不承载「是哪个知识点」的信息。
     */
    private static final List<String> NOISE_WORDS = List.of(
            "方法论", "适用场景", "使用场景", "底层原理", "实现原理",
            "区别", "对比", "原理", "底层", "实现", "机制", "场景", "适用",
            "思路", "流程", "方案", "细节", "分析", "问题", "总结", "梳理",
            "详解", "深入", "理解", "掌握", "以及", "方法", "要点",
            "完整", "具体", "相关", "核心", "关键"
    ).stream().sorted(Comparator.comparingInt(String::length).reversed()).collect(Collectors.toUnmodifiableList());

    private KnowledgePointNormalizer() {
    }

    /**
     * 归一化：把「LongAdder 底层原理（Striped64）」压成 {@code longadderstriped64}。
     *
     * @return 归一结果；入参为 null 或全是标点时返回空串
     */
    public static String canonical(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            // 括号/标点/空白都不是 letterOrDigit，因此天然被丢掉（括号里的内容保留）
            if (Character.isLetterOrDigit(ch)) {
                sb.append(Character.toLowerCase(ch));
            }
        }
        String text = sb.toString();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String noise : NOISE_WORDS) {
                if (text.contains(noise)) {
                    text = text.replace(noise, "");
                    changed = true;
                }
            }
        }
        return text;
    }

    /** 已归一的两个串的 bigram Dice 系数（完全相同返回 1.0） */
    public static double similarity(String canonA, String canonB) {
        if (canonA == null || canonB == null) {
            return 0.0;
        }
        if (canonA.equals(canonB)) {
            return 1.0;
        }
        int la = canonA.length();
        int lb = canonB.length();
        if (la < MIN_FUZZY_LEN || lb < MIN_FUZZY_LEN) {
            return 0.0;
        }
        Set<String> ba = bigrams(canonA);
        Set<String> bb = bigrams(canonB);
        if (ba.isEmpty() || bb.isEmpty()) {
            return 0.0;
        }
        int inter = 0;
        for (String gram : ba) {
            if (bb.contains(gram)) {
                inter++;
            }
        }
        return 2.0 * inter / (ba.size() + bb.size());
    }

    /** 两个原始知识点名是不是同一个知识点 */
    public static boolean sameTopic(String rawA, String rawB) {
        String a = canonical(rawA);
        String b = canonical(rawB);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return sameCanonical(a, b);
    }

    /** 已归一的两个串是不是同一个知识点（批量匹配时先算好归一结果，避免重复归一） */
    public static boolean sameCanonical(String canonA, String canonB) {
        if (canonA == null || canonB == null || canonA.isEmpty() || canonB.isEmpty()) {
            return false;
        }
        if (canonA.equals(canonB)) {
            return true;
        }
        int la = canonA.length();
        int lb = canonB.length();
        if (Math.min(la, lb) < MIN_FUZZY_LEN) {
            return false;
        }
        if (Math.min(la, lb) >= MIN_CONTAINS_LEN && (canonA.contains(canonB) || canonB.contains(canonA))) {
            return true;
        }
        return similarity(canonA, canonB) >= SAME_TOPIC_THRESHOLD;
    }

    /**
     * 单链接（single-linkage）聚类：两两判定同一知识点后取连通分量。
     *
     * <p>用单链接而不是「与簇中心比较」，是因为要的正是「长链条」：
     * 「LongAdder底层原理」—「LongAdder与AtomicLong的区别」—「JUC并发工具（LongAdder vs AtomicLong）」
     * 三者的首尾两两相似度并不高，但它们确实是同一个知识点，中间那条边把它们连起来。
     *
     * @return 每个簇是入参下标的列表，按簇内最小下标排序，保证结果稳定
     */
    public static List<List<Integer>> groupByTopic(List<String> names) {
        List<List<Integer>> result = new ArrayList<>();
        if (names == null || names.isEmpty()) {
            return result;
        }
        int n = names.size();
        String[] canon = new String[n];
        for (int i = 0; i < n; i++) {
            canon[i] = canonical(names.get(i));
        }
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            if (canon[i].isEmpty()) {
                continue;
            }
            for (int j = i + 1; j < n; j++) {
                if (canon[j].isEmpty()) {
                    continue;
                }
                if (sameCanonical(canon[i], canon[j])) {
                    int ri = find(parent, i);
                    int rj = find(parent, j);
                    if (ri != rj) {
                        parent[rj] = ri;
                    }
                }
            }
        }
        Map<Integer, List<Integer>> byRoot = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            byRoot.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }
        result.addAll(byRoot.values());
        return result;
    }

    /**
     * 从簇里挑最能代表整簇的名字（medoid）：到簇内其它成员的平均相似度最高者，
     * 平均分相同则取更短的（更接近规范写法），再相同取下标更小的。
     *
     * <p>不能简单取「出现次数最多」的名字：那个名字往往很具体
     * （「Redisson看门狗与leaseTime」），拿来当整簇标题会误导。
     */
    public static int pickRepresentative(List<String> names, List<Integer> members) {
        if (members == null || members.isEmpty()) {
            return -1;
        }
        if (members.size() == 1) {
            return members.get(0);
        }
        int best = members.get(0);
        double bestScore = -1.0;
        for (int i : members) {
            String canonI = canonical(names.get(i));
            double sum = 0.0;
            for (int j : members) {
                if (i != j) {
                    sum += similarity(canonI, canonical(names.get(j)));
                }
            }
            double score = sum / (members.size() - 1);
            if (score > bestScore + 1e-12) {
                best = i;
                bestScore = score;
            } else if (Math.abs(score - bestScore) <= 1e-12
                    && names.get(i).length() < names.get(best).length()) {
                best = i;
            }
        }
        return best;
    }

    /** 调试用：把一簇名字拼成可读字符串 */
    public static String describe(List<String> names, List<Integer> members) {
        return members.stream().map(names::get).collect(Collectors.joining(" | "));
    }

    private static Set<String> bigrams(String s) {
        Set<String> grams = new java.util.HashSet<>();
        if (s.length() == 1) {
            grams.add(s);
        }
        for (int i = 0; i + 1 < s.length(); i++) {
            grams.add(s.substring(i, i + 2));
        }
        return grams;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }
}
