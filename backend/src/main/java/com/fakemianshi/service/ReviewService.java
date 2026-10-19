package com.fakemianshi.service;

import com.fakemianshi.dto.RetryAnswerResult;
import com.fakemianshi.dto.ReviewOverview;
import com.fakemianshi.dto.WrongQuestionItem;

import java.util.List;

/**
 * 错题本与薄弱点复习闭环。
 *
 * <p>闭环设计：分析报告给出薄弱点 → 错题本列出「真做错过的题」→ 重练同一道题 →
 * 判对则记为已订正，并把该知识点的掌握程度往上调 → 掌握度反过来影响下一次自适应出题。
 *
 * <p>刻意不做的事：不调 LLM。错题本是高频反复使用的功能，每次复习都等模型生成会让人放弃使用，
 * 而且「重做自己错过的原题」本来就不需要生成——原文就在库里。
 */
public interface ReviewService {

    /** 项目维度的错题统计 */
    ReviewOverview overview(Long projectId);

    /**
     * 错题清单。
     *
     * @param projectId      项目 ID
     * @param status         过滤：null/空=全部，pending=待订正，corrected=已订正
     * @param knowledgePoint 按知识点过滤（包含匹配，null/空=全部）
     */
    List<WrongQuestionItem> listWrongQuestions(Long projectId, String status, String knowledgePoint);

    /**
     * 重练一道错题并重新判分。
     *
     * <p>判对且此前未订正 → 记录订正时间，并上调该题知识点的掌握程度。
     *
     * @param questionId 题目 ID
     * @param userAnswer 本次作答
     */
    RetryAnswerResult retry(Long questionId, String userAnswer);
}
