package com.fakemianshi.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentenceSplitterTest {

    @Test
    void splitsByEndMarks() {
        List<String> parts = SentenceSplitter.split("你好。世界！再见？");
        assertEquals(List.of("你好。", "世界！", "再见？"), parts);
    }

    @Test
    void hardSplitWhenTooLong() {
        String longText = "a".repeat(200);
        List<String> parts = SentenceSplitter.split(longText, 150);
        assertTrue(parts.size() >= 2);
        assertTrue(parts.get(0).length() <= 150);
    }

    @Test
    void emptyReturnsEmpty() {
        assertTrue(SentenceSplitter.split("").isEmpty());
        assertTrue(SentenceSplitter.split(null).isEmpty());
    }
}
