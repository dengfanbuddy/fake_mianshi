package com.fakemianshi.util;

/**
 * 笔试自动判分的唯一实现。
 *
 * <p>抽出来的原因：错题本要「重练同一道题并重新判分」，如果错题本自己写一份判分逻辑，
 * 迟早会和笔试交卷的判分规则不一致——同一道题交卷判错、重练判对，用户会当场发现。
 *
 * <p>简答题（{@code SHORT_ANSWER}）无法自动判分，由 LLM 分析环节评分，这里明确返回 false。
 */
public final class WrittenTestGrader {

    public static final String TYPE_SINGLE = "SINGLE_CHOICE";
    public static final String TYPE_MULTIPLE = "MULTIPLE_CHOICE";
    public static final String TYPE_FILL = "FILL_BLANK";
    public static final String TYPE_SHORT = "SHORT_ANSWER";

    private WrittenTestGrader() {
    }

    /** 该题型能否自动判分（简答题只能靠 LLM 评分） */
    public static boolean autoGradable(String type) {
        return TYPE_SINGLE.equals(type) || TYPE_MULTIPLE.equals(type) || TYPE_FILL.equals(type);
    }

    /**
     * 判分。
     *
     * @param type          题型
     * @param correctAnswer 参考答案（选择题为字母串，如 "ABC"）
     * @param userAnswer    用户作答
     */
    public static boolean grade(String type, String correctAnswer, String userAnswer) {
        if (!autoGradable(type)) {
            return false;
        }
        if (userAnswer == null || userAnswer.trim().isEmpty()) {
            return false;
        }
        String submitted = userAnswer.trim();
        String correct = correctAnswer == null ? "" : correctAnswer.trim();
        return switch (type) {
            case TYPE_SINGLE, TYPE_FILL -> submitted.equalsIgnoreCase(correct);
            case TYPE_MULTIPLE -> normalizeMultiple(submitted).equals(normalizeMultiple(correct));
            default -> false;
        };
    }

    /** 多选答案规范化：去除空白/分隔符、转大写并按字母排序（"ACB"、"A,C,B" 均归为 "ABC"） */
    public static String normalizeMultiple(String answer) {
        if (answer == null) {
            return "";
        }
        return answer.toUpperCase()
                .replaceAll("[^A-Z]", "")
                .chars()
                .sorted()
                .collect(StringBuilder::new, (sb, c) -> sb.append((char) c), StringBuilder::append)
                .toString();
    }
}
