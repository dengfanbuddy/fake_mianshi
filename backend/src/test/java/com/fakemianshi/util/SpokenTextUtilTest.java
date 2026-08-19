package com.fakemianshi.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpokenTextUtilTest {

    @Test
    void removesFencedCodeBlock() {
        String text = "请解释\n```java\nint a=1;\n```\n为什么？";
        String result = SpokenTextUtil.stripCode(text);
        assertFalse(result.contains("int a=1;"));
        assertFalse(result.contains("```"));
        assertTrue(result.contains("请解释"));
        assertTrue(result.contains("为什么"));
    }

    @Test
    void removesInlineCodeBackticks() {
        assertEquals("使用 HashMap 即可", SpokenTextUtil.stripCode("使用 `HashMap` 即可"));
    }

    @Test
    void sanitizeCollapsesWhitespace() {
        String result = SpokenTextUtil.sanitizeForSpeech("你好。  世界。\n\n\n再见。");
        assertFalse(result.contains("   "));
        assertFalse(result.contains("\n\n\n"));
    }

    @Test
    void stripsStageDirections() {
        String text = "（语气冷淡，带着一丝不耐烦）你觉得我问这个是故意刁难你？（语气稍缓，但依然严厉）不过我可以给你一次机会。";
        String result = SpokenTextUtil.sanitizeForSpeech(text);
        assertFalse(result.contains("语气冷淡"));
        assertFalse(result.contains("语气稍缓"));
        assertTrue(result.contains("你觉得我问这个是故意刁难你"));
        assertTrue(result.contains("不过我可以给你一次机会"));
    }
}
