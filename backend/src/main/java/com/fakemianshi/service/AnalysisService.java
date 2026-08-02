package com.fakemianshi.service;

import com.fakemianshi.dto.AnalysisResultDTO;
import com.fakemianshi.entity.HistoricalAnalysis;
import com.fakemianshi.entity.QuestionAnalysis;
import com.fakemianshi.entity.SessionAnalysis;

import java.util.List;

/**
 * 面试分析服务：笔试/模拟面试结束后的查漏补缺分析、单题分析、历史综合分析与弱点标签更新。
 *
 * <p>这是产品的价值核心：将一次面试沉淀为可复用的弱项清单与学习方向。
 */
public interface AnalysisService {

    /**
     * 生成并保存会话分析（幂等：已存在分析时直接返回，不重复调用 LLM）。
     * 生成完成后自动更新弱点标签。
     */
    SessionAnalysis analyzeSession(Long sessionId);

    /**
     * 流式生成会话分析：LLM 以「markdown 展示 + ==JSON_START== + JSON」双输出，
     * 增量文本经 onDelta 回调推送（供 SSE 实时展示），返回保存后的分析。
     */
    SessionAnalysis analyzeSessionStream(Long sessionId, java.util.function.Consumer<String> onDelta);

    /**
     * 单题分析。MVP 简化：analyzeSession 时已一并生成，本方法直接查询已保存的分析。
     */
    List<QuestionAnalysis> analyzeQuestions(Long sessionId);

    /**
     * 历史综合分析：跨多次会话的趋势、反复弱点与推荐重点，并保存快照。
     */
    HistoricalAnalysis analyzeHistory(Long projectId);

    /**
     * 弱点标签更新：从会话分析的 knowledgeGaps 提取知识点，累加出现次数并更新掌握程度。
     */
    void updateWeaknessTags(Long projectId, Long sessionId);

    /**
     * 查询已生成的会话分析，组装为前端展示结构；无分析时抛 ResourceNotFoundException。
     */
    AnalysisResultDTO getSessionAnalysis(Long sessionId);

    /**
     * 历史综合分析（controller 用）：已有最新一条快照则直接返回，否则生成。
     */
    HistoricalAnalysis getLatestHistory(Long projectId);

    /**
     * 强制重新生成会话分析（controller 用）：先删除旧分析再重新生成。
     */
    AnalysisResultDTO refreshAnalysis(Long sessionId);

    /**
     * 异步触发会话分析：分析不存在且当前未在生成中时，后台线程生成，立即返回。
     * 用于报告页轮询场景，避免同步阻塞 LLM 调用（可达 60s+）。
     */
    void triggerAnalyze(Long sessionId);
}
