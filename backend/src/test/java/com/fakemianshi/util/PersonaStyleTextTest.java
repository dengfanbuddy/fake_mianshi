package com.fakemianshi.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 面试官风格 → 提示词文字的映射测试。
 *
 * <p>这些文字直接决定面试官的脾气，改错了会很明显，所以逐档都要钉住。
 */
class PersonaStyleTextTest {

    @Test
    void shouldTranslateAllThreeDimensions() {
        String text = PersonaStyleText.toPromptText(
                "{\"tone\":\"serious\",\"speed\":\"medium\",\"aggressiveness\":0.8,\"followupStrategy\":\"deep_dive\"}");

        assertTrue(text.contains("追问策略：不断追问底层原理"), text);
        assertTrue(text.contains("语气：冷静克制"), text);
        assertTrue(text.contains("追问强度：高压"), text);
    }

    @Test
    void shouldTranslateAggressivenessIntoDistinctBehaviour() {
        // 四档必须真的互不相同，否则这个字段又变成摆设
        String mild = PersonaStyleText.toPromptText("{\"aggressiveness\":0.1}");
        String normal = PersonaStyleText.toPromptText("{\"aggressiveness\":0.4}");
        String strict = PersonaStyleText.toPromptText("{\"aggressiveness\":0.7}");
        String harsh = PersonaStyleText.toPromptText("{\"aggressiveness\":0.95}");

        assertTrue(mild.contains("温和"));
        assertTrue(normal.contains("中性"));
        assertTrue(strict.contains("偏严"));
        assertTrue(harsh.contains("高压"));
        assertFalse(mild.equals(normal));
        assertFalse(normal.equals(strict));
        assertFalse(strict.equals(harsh));
    }

    @Test
    void shouldMapEverySeededTone() {
        assertTrue(PersonaStyleText.toneDesc("stern").startsWith("直接、有压迫感"));
        assertTrue(PersonaStyleText.toneDesc("warm").startsWith("温和鼓励"));
        assertTrue(PersonaStyleText.toneDesc("professional").startsWith("稳重、注重细节"));
        assertEquals("平稳自然", PersonaStyleText.toneDesc("neutral"));
        assertTrue(PersonaStyleText.toneDesc("serious").startsWith("冷静克制"));
        assertEquals("", PersonaStyleText.toneDesc("sarcastic"));
        assertEquals("", PersonaStyleText.toneDesc(null));
    }

    @Test
    void shouldMapEverySeededFollowupStrategy() {
        assertTrue(PersonaStyleText.followupDesc("challenge").contains("质疑回答"));
        assertTrue(PersonaStyleText.followupDesc("guide").contains("友好引导"));
        assertTrue(PersonaStyleText.followupDesc("project_drill").contains("架构决策"));
        assertTrue(PersonaStyleText.followupDesc("checklist").contains("逐个考察"));
    }

    @Test
    void shouldKeepCustomStrategyTextInsteadOfDroppingIt() {
        String text = PersonaStyleText.toPromptText("{\"followupStrategy\":\"只问分布式事务\"}");
        assertTrue(text.contains("只问分布式事务"), text);
    }

    @Test
    void shouldDegradeGracefully() {
        assertEquals("", PersonaStyleText.toPromptText(null));
        assertEquals("", PersonaStyleText.toPromptText("   "));
        assertEquals("追问策略：根据回答动态深挖", PersonaStyleText.toPromptText("{not json"));
        assertEquals("追问策略：根据回答动态深挖", PersonaStyleText.toPromptText("[1,2,3]"));
    }

    @Test
    void shouldOmitAggressivenessWhenNotNumeric() {
        String text = PersonaStyleText.toPromptText("{\"tone\":\"warm\",\"aggressiveness\":\"high\"}");
        assertFalse(text.contains("追问强度"), text);
        assertTrue(text.contains("语气：温和鼓励"), text);
    }

    @Test
    void shouldOmitToneWhenUnknown() {
        String text = PersonaStyleText.toPromptText("{\"tone\":\"sarcastic\"}");
        assertFalse(text.contains("语气"), text);
        assertTrue(text.startsWith("追问策略："));
    }
}
