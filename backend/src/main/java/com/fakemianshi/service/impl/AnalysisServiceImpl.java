package com.fakemianshi.service.impl;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.dto.AnalysisResultDTO;
import com.fakemianshi.dto.LlmResponse;
import com.fakemianshi.entity.HistoricalAnalysis;
import com.fakemianshi.entity.InterviewSession;
import com.fakemianshi.entity.MockInterviewMessage;
import com.fakemianshi.entity.QuestionAnalysis;
import com.fakemianshi.entity.SessionAnalysis;
import com.fakemianshi.entity.WeaknessTag;
import com.fakemianshi.entity.WrittenTestAnswer;
import com.fakemianshi.entity.WrittenTestQuestion;
import com.fakemianshi.repository.HistoricalAnalysisRepository;
import com.fakemianshi.repository.InterviewSessionRepository;
import com.fakemianshi.repository.MockInterviewMessageRepository;
import com.fakemianshi.repository.QuestionAnalysisRepository;
import com.fakemianshi.repository.SessionAnalysisRepository;
import com.fakemianshi.repository.WeaknessTagRepository;
import com.fakemianshi.repository.WrittenTestAnswerRepository;
import com.fakemianshi.repository.WrittenTestQuestionRepository;
import com.fakemianshi.service.AnalysisService;
import com.fakemianshi.service.LlmService;
import com.fakemianshi.service.impl.LlmServiceImpl;
import com.fakemianshi.util.JsonExtractor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 面试分析服务实现。
 *
 * <p>分析流程：组装题目/对话上下文 → 调用 LLM 生成结构化 JSON → 分层容错解析
 * （去围栏 + 截取 JSON 主体 + 整体解析）→ 保存 SessionAnalysis 与 QuestionAnalysis →
 * 自动更新弱点标签。历史分析则跨多次会话汇总趋势与反复弱点。
 */
@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisServiceImpl.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String TYPE_WRITTEN = "WRITTEN";
    private static final String TYPE_MOCK = "MOCK";

    /** 笔试分析 system prompt：偏客观，自动判分给错因，简答题给 LLM 评分 */
    private static final String WRITTEN_SYSTEM_PROMPT = """
            你是资深 Java 面试官。请分析以下笔试结果，为候选人生成一份查漏补缺的分析报告。
            要求：
            1. 对自动判分的客观题（单选/多选/填空）指出答错原因与正确思路；
            2. 对未自动判分的简答题给出准确度评分和完整参考答案要点；
            3. strengths、weaknesses 各列出 2-5 条具体表现；
            4. knowledgeGaps 必须是具体知识点（如 "JVM垃圾回收"、"Spring事务传播机制"），每项要附 explanation：该知识点的核心答案要点（100-200 字），让面试者看了就能补上这块知识；
            5. improvementPlan.topics 每项给出 {topic: 学习方向, action: 具体做法, example: 实例}，suggestions 给出可执行建议；
            6. 根据面试表现给出 overallLevel（能力等级：初级/中级/高级/资深/专家 工程师）与 expectedSalaryRange（按国内一线城市行情给出合理月薪区间，如 "20k-30k"）；
            7. personalitySummary 总结候选人性格与工作风格（100 字内）；characterTraits 列出 2-4 个性格特质；characterDefects 列出 1-3 个性格/习惯缺陷及改进方法；
            8. questionAnalyses 每项给出 category（技术分类，如 JVM/并发/Spring/数据库）、difficulty（难度：初级/中级/高级）、focusPoint（本题考察的方向与能力）、answerApproach（这类题的回答思路，给面试者方法论）、example（一个优秀回答示例片段）；
            9. 每个评分维度都要给出打分依据：accuracyReason/depthReason/clarityReason/fluencyReason（各 30-80 字，具体指出回答中得分点和扣分点，如"答案正确但未展开锁升级细节，故深度不足"，让候选人明白为什么是这个分数）。

            请严格返回如下 JSON 结构，不要输出任何额外文字或 Markdown 代码块：
            {"overallScore":0-100,"overallLevel":"...","expectedSalaryRange":"...","strengths":["..."],"weaknesses":["..."],"knowledgeGaps":[{"point":"知识点","explanation":"答案要点"}],"personalitySummary":"...","characterTraits":["..."],"characterDefects":[{"defect":"缺陷","improvement":"改进方法"}],"improvementPlan":{"topics":[{"topic":"学习方向","action":"具体做法","example":"实例"}],"suggestions":["建议"]},"questionAnalyses":[{"questionContent":"...","answerContent":"...","category":"技术分类","difficulty":"难度","focusPoint":"考察方向","accuracy":0-10,"accuracyReason":"依据","depth":0-10,"depthReason":"依据","clarity":0-10,"clarityReason":"依据","fluency":0-10,"fluencyReason":"依据","tone":"","answerApproach":"回答思路","example":"优秀示例","improvementSuggestion":"..."}]}
            说明：
            - questionAnalyses 每项对应一道笔试题，questionContent 请原样引用题目内容，便于程序匹配；
            - 客观题 fluency 填 0，tone 填简短语气评价或空字符串；
            - accuracy/depth/clarity/fluency 均按 0-10 打分。
            """;

    /** 模拟面试分析 system prompt：偏主观，逐题评估准确度/深度/清晰度/流畅度/语气 */
    private static final String MOCK_SYSTEM_PROMPT = """
            你是资深 Java 面试官。请分析以下模拟面试对话，为候选人生成一份查漏补缺的分析报告。
            要求：
            1. 结合对话逐题评估候选人的回答：accuracy（准确度）、depth（深度）、clarity（清晰度）、fluency（流畅度，从停顿、语气词、重复表述等推断）；
            2. communicationEvaluation 评价候选人的表达、逻辑、语气、流畅度，不超过 500 字；
            3. strengths、weaknesses 各列出 2-5 条具体表现；
            4. knowledgeGaps 必须是具体知识点（如 "JVM垃圾回收"、"Spring事务传播机制"），每项要附 explanation：该知识点的核心答案要点（100-200 字），让面试者看了就能补上这块知识；
            5. improvementPlan.topics 每项给出 {topic: 学习方向, action: 具体做法, example: 实例}，suggestions 给出可执行建议；
            6. 根据面试表现给出 overallLevel（能力等级：初级/中级/高级/资深/专家 工程师）与 expectedSalaryRange（按国内一线城市行情给出合理月薪区间，如 "20k-30k"）；
            7. personalitySummary 总结候选人性格与工作风格（100 字内）；characterTraits 列出 2-4 个性格特质；characterDefects 列出 1-3 个性格/习惯缺陷及改进方法；
            8. questionAnalyses 每项给出 category（技术分类，如 JVM/并发/Spring/数据库）、difficulty（难度：初级/中级/高级）、focusPoint（本题考察的方向与能力）、answerApproach（这类题的回答思路，给面试者方法论）、example（一个优秀回答示例片段）；
            9. 每个评分维度都要给出打分依据：accuracyReason/depthReason/clarityReason/fluencyReason（各 30-80 字，具体指出回答中得分点和扣分点，如"答案正确但未展开锁升级细节，故深度不足"，让候选人明白为什么是这个分数）。
            请严格返回如下 JSON 结构，不要输出任何额外文字或 Markdown 代码块：
            {"overallScore":0-100,"overallLevel":"...","expectedSalaryRange":"...","strengths":["..."],"weaknesses":["..."],"knowledgeGaps":[{"point":"知识点","explanation":"答案要点"}],"personalitySummary":"...","characterTraits":["..."],"characterDefects":[{"defect":"缺陷","improvement":"改进方法"}],"communicationEvaluation":"...","improvementPlan":{"topics":[{"topic":"学习方向","action":"具体做法","example":"实例"}],"suggestions":["建议"]},"questionAnalyses":[{"questionContent":"...","answerContent":"...","category":"技术分类","difficulty":"难度","focusPoint":"考察方向","accuracy":0-10,"accuracyReason":"依据","depth":0-10,"depthReason":"依据","clarity":0-10,"clarityReason":"依据","fluency":0-10,"fluencyReason":"依据","tone":"","answerApproach":"回答思路","example":"优秀示例","improvementSuggestion":"..."}]}
            说明：
            - questionAnalyses 每项对应一轮问答，questionContent 请引用面试官的原问题；
            - accuracy/depth/clarity/fluency 均按 0-10 打分。
            """;

    /** 历史综合分析 system prompt：跨会话趋势、反复弱点、推荐重点 */
    private static final String HISTORY_SYSTEM_PROMPT = """
            你是资深 Java 面试官，负责为候选人的长期学习做规划。请基于以下多次面试（笔试/模拟面试）的分析结果，输出历史综合分析报告。
            要求：
            1. trends 至少包含「总体评分」「技术深度」「沟通表达」三个维度（原始数据缺失的维度可省略），trend 取值为 上升/平稳/下降；
            2. recurringWeaknesses 列出多次面试中反复出现的薄弱知识点（尽量与知识盲区对齐，使用具体知识点）；
            3. recommendedFocus 给出 3-5 个下次重点练习的方向；
            4. overallProgress 用一段话整体评价候选人的成长情况。
            请严格返回如下 JSON 结构，不要输出任何额外文字或 Markdown 代码块：
            {"trends":[{"dimension":"技术深度","trend":"上升/平稳/下降","detail":"说明"}],"recurringWeaknesses":["反复出现的弱点知识点"],"recommendedFocus":["下次重点练习"],"overallProgress":"整体评价"}
            """;

    private final InterviewSessionRepository sessionRepository;
    private final WrittenTestQuestionRepository writtenTestQuestionRepository;
    private final WrittenTestAnswerRepository writtenTestAnswerRepository;
    private final MockInterviewMessageRepository mockInterviewMessageRepository;
    private final SessionAnalysisRepository sessionAnalysisRepository;
    private final QuestionAnalysisRepository questionAnalysisRepository;
    private final WeaknessTagRepository weaknessTagRepository;
    private final HistoricalAnalysisRepository historicalAnalysisRepository;
    private final LlmService llmService;
    private final com.fakemianshi.service.PromptTemplateService promptTemplateService;
    private final com.fakemianshi.repository.InterviewProjectRepository interviewProjectRepository;

    /** 注入自身代理：异步线程里通过代理调用带 @Transactional 的方法，避免自调用绕过事务 */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.fakemianshi.service.AnalysisService self;

    /** 分析生成中标记（防重复触发） */
    private final java.util.Set<Long> analyzingSessions = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 分析异步生成线程池 */
    private final java.util.concurrent.ExecutorService analysisExecutor =
            java.util.concurrent.Executors.newCachedThreadPool();

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        analysisExecutor.shutdownNow();
    }

    @Override
    public void triggerAnalyze(Long sessionId) {
        // 已存在分析则无需生成
        if (!sessionAnalysisRepository.findBySessionId(sessionId).isEmpty()) {
            return;
        }
        if (isAnalyzing(sessionId)) {
            return; // 已在生成中
        }
        analysisExecutor.execute(() -> {
            try {
                // 经代理调用，保证 @Transactional 生效（三步写库原子）；analyzeSession 内部有 analyzingSessions 防重
                self.analyzeSession(sessionId);
            } catch (Exception e) {
                log.warn("会话分析异步生成失败: sessionId={}, cause={}", sessionId, e.getMessage());
            }
        });
    }

    @Override
    @Transactional
    public SessionAnalysis analyzeSession(Long sessionId) {
        // 与流式入口统一用 analyzingSessions 防重，避免并发（afterCommit 自动触发 + 手动触发）重复调用 LLM / 重复插库
        if (!analyzingSessions.add(sessionId)) {
            List<SessionAnalysis> existing = sessionAnalysisRepository.findBySessionId(sessionId);
            return existing.isEmpty() ? null : existing.get(0);
        }
        try {
            return analyzeSessionInternal(sessionId, null, null);
        } finally {
            analyzingSessions.remove(sessionId);
        }
    }

    @Override
    @Transactional
    public SessionAnalysis analyzeSessionStream(Long sessionId, java.util.function.Consumer<String> onDelta,
                                                java.util.function.Consumer<String> onReasoning) {
        // 防重：已有任务在生成时不再重复调用 LLM（调用方应先检查 isAnalyzing）
        if (!analyzingSessions.add(sessionId)) {
            List<SessionAnalysis> existing = sessionAnalysisRepository.findBySessionId(sessionId);
            return existing.isEmpty() ? null : existing.get(0);
        }
        try {
            return analyzeSessionInternal(sessionId, onDelta, onReasoning);
        } finally {
            analyzingSessions.remove(sessionId);
        }
    }

    @Override
    public boolean isAnalyzing(Long sessionId) {
        return analyzingSessions.contains(sessionId);
    }

    /** 分析主流程：LLM 生成（可选流式）→ 解析 JSON 存库 → 更新弱点标签 */
    private SessionAnalysis analyzeSessionInternal(Long sessionId, java.util.function.Consumer<String> onDelta,
                                                   java.util.function.Consumer<String> onReasoning) {
        InterviewSession session = Optional.ofNullable(sessionRepository.selectById(sessionId))
                .orElseThrow(() -> new BusinessException("面试会话不存在: id=" + sessionId));

        // 幂等：已存在分析直接返回，不重复调用 LLM
        List<SessionAnalysis> existing = sessionAnalysisRepository.findBySessionId(sessionId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        String type = session.getType();
        JsonNode analysisNode;
        if (TYPE_WRITTEN.equals(type)) {
            String systemPrompt = promptTemplateService.getTemplate(
                    resolveOccupation(session.getProjectId()),
                    com.fakemianshi.service.PromptTemplateService.Scene.WRITTEN_ANALYSIS.name(),
                    resolvePosition(session.getProjectId()));
            analysisNode = onDelta == null
                    ? callLlm(systemPrompt, buildWrittenUserPrompt(sessionId))
                    : callLlmStream(systemPrompt, buildWrittenUserPrompt(sessionId), onDelta, onReasoning);
        } else if (TYPE_MOCK.equals(type)) {
            String systemPrompt = promptTemplateService.getTemplate(
                    resolveOccupation(session.getProjectId()),
                    com.fakemianshi.service.PromptTemplateService.Scene.MOCK_ANALYSIS.name(),
                    resolvePosition(session.getProjectId()));
            analysisNode = onDelta == null
                    ? callLlm(systemPrompt, buildMockUserPrompt(sessionId))
                    : callLlmStream(systemPrompt, buildMockUserPrompt(sessionId), onDelta, onReasoning);
        } else {
            throw new BusinessException("不支持的会话类型: " + type);
        }

        SessionAnalysis analysis = toSessionAnalysis(sessionId, analysisNode);
        sessionAnalysisRepository.insert(analysis);

        for (QuestionAnalysis qa : parseQuestionAnalyses(analysisNode, sessionId, type)) {
            qa.setSessionId(sessionId);
            questionAnalysisRepository.insert(qa);
        }

        // 笔试：把 LLM 对简答题的评分回写到答案，修正总分
        if (TYPE_WRITTEN.equals(type)) {
            writeBackShortAnswerScores(sessionId, analysisNode);
        }

        // 返回前自动更新弱点标签
        updateWeaknessTags(session.getProjectId(), sessionId);

        return analysis;
    }

    /** 项目目标岗位（职业）；无则 null（走 default 模板） */
    private String resolveOccupation(Long projectId) {
        if (projectId == null) {
            return null;
        }
        com.fakemianshi.entity.InterviewProject project = interviewProjectRepository.selectById(projectId);
        return project == null ? null : project.getTargetPosition();
    }

    /** 项目目标岗位文本（用于 {position} 占位替换） */
    private String resolvePosition(Long projectId) {
        String occupation = resolveOccupation(projectId);
        return occupation == null ? "" : occupation;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionAnalysis> analyzeQuestions(Long sessionId) {
        // MVP：analyzeSession 时已一并生成，直接查库返回
        return questionAnalysisRepository.findBySessionId(sessionId);
    }

    @Override
    @Transactional
    public HistoricalAnalysis analyzeHistory(Long projectId) {
        List<SessionAnalysis> analyses = collectProjectAnalyses(projectId);
        if (analyses.isEmpty()) {
            throw new BusinessException("该项目暂无面试记录");
        }

        String systemPrompt = promptTemplateService.getTemplate(
                resolveOccupation(projectId),
                com.fakemianshi.service.PromptTemplateService.Scene.HISTORY_ANALYSIS.name(),
                resolvePosition(projectId));
        JsonNode node = callLlm(systemPrompt, buildHistoryUserPrompt(projectId, analyses));

        HistoricalAnalysis historical = new HistoricalAnalysis();
        historical.setProjectId(projectId);
        historical.setAnalysisData(node.toString());
        historicalAnalysisRepository.insert(historical);
        return historical;
    }

    @Override
    @Transactional(readOnly = true)
    public com.fakemianshi.dto.HistoryStatusDTO getHistoryStatus(Long projectId) {
        List<InterviewSession> sessions = sessionRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        List<HistoricalAnalysis> snapshots =
                historicalAnalysisRepository.findByProjectIdOrderByGeneratedAtDesc(projectId);
        LocalDateTime latestHistoryAt = snapshots.isEmpty() ? null : snapshots.get(0).getGeneratedAt();

        int completed = 0;
        int unanalyzed = 0;
        LocalDateTime latestAnalyzedAt = null;
        for (InterviewSession s : sessions) {
            if (!"COMPLETED".equals(s.getStatus())) {
                continue;
            }
            completed++;
            List<SessionAnalysis> analyses = sessionAnalysisRepository.findBySessionId(s.getId());
            if (analyses.isEmpty()) {
                unanalyzed++;
            } else {
                LocalDateTime at = analyses.get(0).getCreatedAt();
                if (at != null && (latestAnalyzedAt == null || at.isAfter(latestAnalyzedAt))) {
                    latestAnalyzedAt = at;
                }
            }
        }

        com.fakemianshi.dto.HistoryStatusDTO dto = new com.fakemianshi.dto.HistoryStatusDTO();
        dto.setTotalSessions(sessions.size());
        dto.setCompletedSessions(completed);
        dto.setUnanalyzedCount(unanalyzed);
        dto.setLatestHistoryAt(latestHistoryAt);
        dto.setLatestAnalyzedAt(latestAnalyzedAt);
        // 有分析结果比快照新，或尚无快照但已有分析结果 → 需要（重新）综合分析
        boolean hasNew = latestAnalyzedAt != null
                && (latestHistoryAt == null || latestAnalyzedAt.isAfter(latestHistoryAt));
        dto.setHasNewAnalysis(hasNew);
        return dto;
    }

    @Override
    @Transactional
    public HistoricalAnalysis refreshHistory(Long projectId) {
        historicalAnalysisRepository.findByProjectIdOrderByGeneratedAtDesc(projectId)
                .forEach(h -> historicalAnalysisRepository.deleteById(h.getId()));
        return analyzeHistory(projectId);
    }

    @Override
    @Transactional
    public void updateWeaknessTags(Long projectId, Long sessionId) {
        List<SessionAnalysis> analyses = sessionAnalysisRepository.findBySessionId(sessionId);
        if (analyses.isEmpty()) {
            return;
        }
        List<String> knowledgeGaps = parseKnowledgePoints(analyses.get(0).getKnowledgeGaps());
        if (knowledgeGaps.isEmpty()) {
            return;
        }

        List<WeaknessTag> tags = new ArrayList<>(weaknessTagRepository.findByProjectId(projectId));
        for (String gap : knowledgeGaps) {
            if (gap == null || gap.isBlank()) {
                continue;
            }
            String point = gap.trim();
            WeaknessTag tag = findTag(tags, point);
            if (tag != null) {
                int count = (tag.getOccurrenceCount() == null ? 0 : tag.getOccurrenceCount()) + 1;
                tag.setOccurrenceCount(count);
                tag.setMasteryLevel(resolveMasteryLevel(count));
                tag.setLastSessionId(sessionId);
                tag.setUpdatedAt(LocalDateTime.now());
                weaknessTagRepository.updateById(tag);
            } else {
                WeaknessTag newTag = new WeaknessTag();
                newTag.setProjectId(projectId);
                newTag.setKnowledgePoint(point);
                newTag.setMasteryLevel(resolveMasteryLevel(1)); // 首次出现 = 轻微(FAIR)
                newTag.setOccurrenceCount(1);
                newTag.setLastSessionId(sessionId);
                newTag.setUpdatedAt(LocalDateTime.now());
                weaknessTagRepository.insert(newTag);
                tags.add(newTag); // 供同批后续知识点匹配
            }
        }
    }

    /** 笔试简答题评分回流：把 LLM 分析出的 accuracy(0-10) 折算成简答题得分（满分 5 分）回写 written_test_answer */
    private void writeBackShortAnswerScores(Long sessionId, JsonNode analysisNode) {
        JsonNode arr = analysisNode.get("questionAnalyses");
        if (arr == null || !arr.isArray()) {
            return;
        }
        List<WrittenTestQuestion> questions = writtenTestQuestionRepository.findBySessionIdOrderByOrderNum(sessionId);
        Map<Long, WrittenTestQuestion> questionById = questions.stream()
                .collect(Collectors.toMap(WrittenTestQuestion::getId, Function.identity(), (a, b) -> a));
        Map<Long, WrittenTestAnswer> answerByQuestion = writtenTestAnswerRepository.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(WrittenTestAnswer::getQuestionId, Function.identity(), (a, b) -> a));

        for (JsonNode item : arr) {
            Long qid = resolveQuestionRefId(item, TYPE_WRITTEN, questions, List.of());
            if (qid == null) {
                continue;
            }
            WrittenTestQuestion q = questionById.get(qid);
            if (q == null || !"SHORT_ANSWER".equals(q.getType())) {
                continue;
            }
            Double accuracy = numericOrNull(item, "accuracy");
            if (accuracy == null) {
                continue;
            }
            WrittenTestAnswer answer = answerByQuestion.get(qid);
            if (answer == null) {
                continue;
            }
            double score = BigDecimal.valueOf(5.0 * accuracy / 10.0)
                    .setScale(1, RoundingMode.HALF_UP).doubleValue();
            answer.setScore(score);
            writtenTestAnswerRepository.updateById(answer);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AnalysisResultDTO getSessionAnalysis(Long sessionId) {
        List<SessionAnalysis> analyses = sessionAnalysisRepository.findBySessionId(sessionId);
        if (analyses.isEmpty()) {
            throw new ResourceNotFoundException("该会话还没有分析报告");
        }
        SessionAnalysis analysis = analyses.get(0);

        AnalysisResultDTO dto = new AnalysisResultDTO();
        dto.setSessionId(sessionId);
        dto.setOverallScore(analysis.getOverallScore());
        dto.setStrengths(parseStringList(analysis.getStrengths()));
        dto.setWeaknesses(parseStringList(analysis.getWeaknesses()));
        dto.setKnowledgeGaps(parseJsonNodeList(analysis.getKnowledgeGaps()));
        dto.setOverallLevel(analysis.getOverallLevel());
        dto.setExpectedSalaryRange(analysis.getExpectedSalaryRange());
        dto.setPersonalitySummary(analysis.getPersonalitySummary());
        dto.setCharacterTraits(parseStringList(analysis.getCharacterTraits()));
        dto.setCharacterDefects(parseJsonNodeList(analysis.getCharacterDefects()));
        dto.setCommunicationEvaluation(analysis.getCommunicationEvaluation());
        dto.setHumorSummary(analysis.getHumorSummary());
        dto.setImprovementPlan(parseMap(analysis.getImprovementPlan()));

        List<Map<String, Object>> qaMaps = new ArrayList<>();
        for (QuestionAnalysis qa : questionAnalysisRepository.findBySessionId(sessionId)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("questionRefId", qa.getQuestionRefId());
            map.put("questionContent", qa.getQuestionContent());
            map.put("answerContent", qa.getAnswerContent());
            map.put("accuracy", qa.getAccuracy());
            map.put("accuracyReason", qa.getAccuracyReason());
            map.put("depth", qa.getDepth());
            map.put("depthReason", qa.getDepthReason());
            map.put("clarity", qa.getClarity());
            map.put("clarityReason", qa.getClarityReason());
            map.put("fluency", qa.getFluency());
            map.put("fluencyReason", qa.getFluencyReason());
            map.put("tone", qa.getToneEvaluation());
            map.put("category", qa.getCategory());
            map.put("difficulty", qa.getDifficulty());
            map.put("focusPoint", qa.getFocusPoint());
            map.put("answerApproach", qa.getAnswerApproach());
            map.put("example", qa.getExample());
            map.put("improvementSuggestion", qa.getImprovementSuggestion());
            qaMaps.add(map);
        }
        dto.setQuestionAnalyses(qaMaps);
        return dto;
    }

    @Override
    @Transactional
    public HistoricalAnalysis getLatestHistory(Long projectId) {
        List<HistoricalAnalysis> existing = historicalAnalysisRepository.findByProjectIdOrderByGeneratedAtDesc(projectId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        return analyzeHistory(projectId);
    }

    @Override
    @Transactional
    public AnalysisResultDTO refreshAnalysis(Long sessionId) {
        deleteAnalysis(sessionId);
        analyzeSession(sessionId);
        return getSessionAnalysis(sessionId);
    }

    @Override
    @Transactional
    public void deleteAnalysis(Long sessionId) {
        // 先删除旧分析（单题分析 + 会话分析）
        questionAnalysisRepository.deleteByIds(questionAnalysisRepository.findBySessionId(sessionId).stream().map(QuestionAnalysis::getId).toList());
        sessionAnalysisRepository.findBySessionId(sessionId)
                .forEach(a -> sessionAnalysisRepository.deleteById(a.getId()));
    }

    // ---------------- 内部方法 ----------------

    /** 调用 LLM 并将返回内容提取、解析为 JSON 对象，带分层容错；分析报告输出较长，使用大 max_tokens */
    private JsonNode callLlm(String systemPrompt, String userPrompt) {
        LlmResponse response = llmService.chat(systemPrompt, userPrompt, LlmServiceImpl.LONG_TASK_MAX_TOKENS);
        return parseAnalysisJson(response.getContent());
    }

    /** 流式调用 LLM：内容增量经 onDelta、思考过程经 onReasoning 推送，返回解析后的 JSON 节点 */
    private JsonNode callLlmStream(String systemPrompt, String userPrompt,
                                   java.util.function.Consumer<String> onDelta,
                                   java.util.function.Consumer<String> onReasoning) {
        String raw = llmService.chatStream(systemPrompt, userPrompt,
                LlmServiceImpl.LONG_TASK_MAX_TOKENS, onDelta, onReasoning);
        return parseAnalysisJson(raw);
    }

    /** 提取并解析 LLM 输出的 JSON 对象：容错处理 ```json 代码块围栏、==JSON_START== 标记及前后缀杂文本 */
    private JsonNode parseAnalysisJson(String raw) {
        String json = raw == null ? "" : raw.trim();
        // 优先取 ==JSON_START== 标记之后的部分（历史双输出格式）
        int markIdx = json.indexOf("==JSON_START==");
        if (markIdx >= 0) {
            json = json.substring(markIdx + "==JSON_START==".length()).trim();
        }
        // 提取首个 { 到最后一个 } 之间的 JSON 主体：跳过 ```json 围栏与前后缀文本
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (node == null || !node.isObject()) {
                log.warn("AI分析结果非JSON对象，原始输出前300字: {}", truncate(raw, 300));
                throw new BusinessException("AI分析结果格式异常，请重试");
            }
            return node;
        } catch (JacksonException e) {
            log.warn("AI分析结果JSON解析失败，原始输出前300字: {}", truncate(raw, 300), e);
            throw new BusinessException("AI分析结果格式异常，请重试", e);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    /** 组装笔试上下文：逐题列出题目、参考答案、用户答案与自动判分结果 */
    private String buildWrittenUserPrompt(Long sessionId) {
        List<WrittenTestQuestion> questions = writtenTestQuestionRepository.findBySessionIdOrderByOrderNum(sessionId);
        Map<Long, WrittenTestAnswer> answerByQuestion = writtenTestAnswerRepository.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(WrittenTestAnswer::getQuestionId, Function.identity(), (a, b) -> a));

        StringBuilder sb = new StringBuilder("【笔试记录】\n");
        for (WrittenTestQuestion q : questions) {
            WrittenTestAnswer answer = answerByQuestion.get(q.getId());
            sb.append("- 题目").append(q.getOrderNum() == null ? "" : q.getOrderNum())
                    .append("（题型：").append(valueOrDash(q.getType())).append("）\n");
            sb.append("  题目内容：").append(valueOrDash(q.getContent())).append('\n');
            sb.append("  参考答案：").append(valueOrDash(q.getAnswer())).append('\n');
            sb.append("  用户答案：").append(valueOrDash(answer == null ? null : answer.getUserAnswer())).append('\n');
            if (answer != null && answer.getIsCorrect() != null) {
                sb.append("  自动判分：").append(Boolean.TRUE.equals(answer.getIsCorrect()) ? "正确" : "错误").append('\n');
            } else {
                sb.append("  自动判分：简答题，待你评分\n");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 组装模拟面试上下文：按时间顺序的完整对话记录 */
    private String buildMockUserPrompt(Long sessionId) {
        List<MockInterviewMessage> messages = mockInterviewMessageRepository.findBySessionIdOrderByCreatedAt(sessionId);
        StringBuilder sb = new StringBuilder("【模拟面试对话记录】\n");
        for (MockInterviewMessage m : messages) {
            String role = "INTERVIEWER".equals(m.getRole()) ? "面试官" : "候选人";
            sb.append(role).append("：").append(valueOrDash(m.getContent())).append('\n');
        }
        return sb.toString();
    }

    /** 组装历史分析上下文：历次会话分析结果摘要 */
    private String buildHistoryUserPrompt(Long projectId, List<SessionAnalysis> analyses) {
        StringBuilder sb = new StringBuilder("【历次面试分析结果】\n");
        sb.append("项目ID：").append(projectId).append('\n');
        int index = 1;
        for (SessionAnalysis a : analyses) {
            sb.append("第").append(index++).append("次分析：\n");
            sb.append("- 综合得分：").append(a.getOverallScore() == null ? "-" : a.getOverallScore()).append('\n');
            sb.append("- 优势：").append(valueOrDash(a.getStrengths())).append('\n');
            sb.append("- 不足：").append(valueOrDash(a.getWeaknesses())).append('\n');
            sb.append("- 知识盲区：").append(valueOrDash(a.getKnowledgeGaps())).append('\n');
            if (a.getCommunicationEvaluation() != null && !a.getCommunicationEvaluation().isBlank()) {
                sb.append("- 沟通表达：").append(a.getCommunicationEvaluation()).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 收集项目下全部会话的分析结果（按会话创建时间倒序） */
    private List<SessionAnalysis> collectProjectAnalyses(Long projectId) {
        List<SessionAnalysis> analyses = new ArrayList<>();
        for (InterviewSession session : sessionRepository.findByProjectIdOrderByCreatedAtDesc(projectId)) {
            analyses.addAll(sessionAnalysisRepository.findBySessionId(session.getId()));
        }
        return analyses;
    }

    /** 将 LLM 返回的会话级 JSON 节点转换为 SessionAnalysis 实体 */
    private SessionAnalysis toSessionAnalysis(Long sessionId, JsonNode node) {
        SessionAnalysis analysis = new SessionAnalysis();
        analysis.setSessionId(sessionId);
        analysis.setOverallScore(node.path("overallScore").asDouble(0));
        analysis.setStrengths(toJsonArrayString(node.get("strengths")));
        analysis.setWeaknesses(toJsonArrayString(node.get("weaknesses")));
        analysis.setKnowledgeGaps(toJsonArrayString(node.get("knowledgeGaps")));
        analysis.setOverallLevel(textOrNull(node, "overallLevel"));
        analysis.setExpectedSalaryRange(textOrNull(node, "expectedSalaryRange"));
        analysis.setPersonalitySummary(textOrNull(node, "personalitySummary"));
        analysis.setCharacterTraits(toJsonArrayString(node.get("characterTraits")));
        analysis.setCharacterDefects(toJsonArrayString(node.get("characterDefects")));
        analysis.setCommunicationEvaluation(textOrNull(node, "communicationEvaluation"));
        analysis.setHumorSummary(textOrNull(node, "humorSummary"));
        analysis.setImprovementPlan(toJsonString(node.get("improvementPlan")));
        return analysis;
    }

    /** 解析 LLM 返回的 questionAnalyses 数组为 QuestionAnalysis 列表，并尽力回填 questionRefId */
    private List<QuestionAnalysis> parseQuestionAnalyses(JsonNode analysisNode, Long sessionId, String type) {
        List<QuestionAnalysis> list = new ArrayList<>();
        JsonNode arr = analysisNode.get("questionAnalyses");
        if (arr == null || !arr.isArray()) {
            return list;
        }
        List<WrittenTestQuestion> questions = TYPE_WRITTEN.equals(type)
                ? writtenTestQuestionRepository.findBySessionIdOrderByOrderNum(sessionId)
                : List.of();
        List<MockInterviewMessage> messages = TYPE_MOCK.equals(type)
                ? mockInterviewMessageRepository.findBySessionIdOrderByCreatedAt(sessionId)
                : List.of();

        for (JsonNode item : arr) {
            QuestionAnalysis qa = new QuestionAnalysis();
            qa.setQuestionRefId(resolveQuestionRefId(item, type, questions, messages));
            qa.setQuestionContent(item.path("questionContent").asText());
            qa.setAnswerContent(item.path("answerContent").asText());
            qa.setAccuracy(numericOrNull(item, "accuracy"));
            qa.setAccuracyReason(textOrNull(item, "accuracyReason"));
            qa.setDepth(numericOrNull(item, "depth"));
            qa.setDepthReason(textOrNull(item, "depthReason"));
            qa.setClarity(numericOrNull(item, "clarity"));
            qa.setClarityReason(textOrNull(item, "clarityReason"));
            qa.setFluency(numericOrNull(item, "fluency"));
            qa.setFluencyReason(textOrNull(item, "fluencyReason"));
            qa.setToneEvaluation(textOrNull(item, "tone"));
            qa.setCategory(textOrNull(item, "category"));
            qa.setDifficulty(textOrNull(item, "difficulty"));
            qa.setFocusPoint(textOrNull(item, "focusPoint"));
            qa.setAnswerApproach(textOrNull(item, "answerApproach"));
            qa.setExample(textOrNull(item, "example"));
            qa.setImprovementSuggestion(textOrNull(item, "improvementSuggestion"));
            list.add(qa);
        }
        return list;
    }

    /** 尽力将单题分析回填到原题目/面试官消息 id：按内容匹配 */
    private Long resolveQuestionRefId(JsonNode item, String type,
                                     List<WrittenTestQuestion> questions,
                                     List<MockInterviewMessage> messages) {
        String content = item.path("questionContent").asText("");
        if (content.isBlank()) {
            return null;
        }
        if (TYPE_WRITTEN.equals(type)) {
            for (WrittenTestQuestion q : questions) {
                if (similarText(q.getContent(), content)) {
                    return q.getId();
                }
            }
        } else {
            for (MockInterviewMessage m : messages) {
                if ("INTERVIEWER".equals(m.getRole()) && similarText(m.getContent(), content)) {
                    return m.getId();
                }
            }
        }
        return null;
    }

    /** 内容相似度判断：完全相等，或长度足够时互相包含（防 LLM 截断/改写） */
    private boolean similarText(String source, String target) {
        if (source == null || target == null) {
            return false;
        }
        String a = source.trim();
        String b = target.trim();
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        return a.length() >= 8 && b.length() >= 8 && (a.contains(b) || b.contains(a));
    }

    /** 在已有标签列表中按知识点匹配（忽略大小写或互相包含） */
    private WeaknessTag findTag(List<WeaknessTag> tags, String knowledgePoint) {
        for (WeaknessTag tag : tags) {
            String point = tag.getKnowledgePoint();
            if (point == null) {
                continue;
            }
            if (point.equalsIgnoreCase(knowledgePoint)) {
                return tag;
            }
            String a = point.trim();
            String b = knowledgePoint.trim();
            if (a.length() >= 4 && b.length() >= 4 && (a.contains(b) || b.contains(a))) {
                return tag;
            }
        }
        return null;
    }

    /**
     * 按累计出现次数判断薄弱程度：出现 >=2 次为 WEAK（顽固弱点），1 次为 FAIR（轻微）。
     * 弱点标签仅记录"曾经薄弱"的知识点，故取值限定 WEAK/FAIR；GOOD/STRONG 预留给未来"已掌握/提升"判定。
     */
    private String resolveMasteryLevel(int occurrenceCount) {
        return occurrenceCount >= 2 ? "WEAK" : "FAIR";
    }

    /**
     * 解析 JSON 数组字符串为 List&lt;Object&gt;（保留对象与字符串原样，供前端展示）；
     * 解析失败返回空列表。
     */
    private List<Object> parseJsonNodeList(String json) {
        List<Object> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (node.isArray()) {
                node.forEach(n -> {
                    if (n.isTextual()) {
                        result.add(n.asText());
                    } else if (n.isObject()) {
                        result.add(OBJECT_MAPPER.convertValue(n, Map.class));
                    }
                });
            }
        } catch (JacksonException ignored) {
            // 忽略解析失败，返回空列表
        }
        return result;
    }

    /** 解析 JSON 数组字符串为字符串列表；解析失败返回空列表 */
    private List<String> parseStringList(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (node.isArray()) {
                node.forEach(n -> {
                    if (n.isTextual()) {
                        result.add(n.asText());
                    }
                });
            }
        } catch (JacksonException ignored) {
            // 忽略解析失败，返回空列表
        }
        return result;
    }

    /**
     * 从 knowledgeGaps JSON 数组中提取知识点名称，兼容两种格式：
     * 新格式对象数组 [{"point":"JVM垃圾回收","explanation":"..."}] 取 point 字段；
     * 旧格式纯字符串数组 ["JVM垃圾回收"] 直接取文本。
     */
    private List<String> parseKnowledgePoints(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (!node.isArray()) {
                return result;
            }
            for (JsonNode n : node) {
                if (n.isObject()) {
                    String point = n.path("point").asText("");
                    if (!point.isBlank()) {
                        result.add(point.trim());
                    }
                } else if (n.isTextual() && !n.asText().isBlank()) {
                    result.add(n.asText().trim());
                }
            }
        } catch (JacksonException ignored) {
            // 忽略解析失败，返回空列表
        }
        return result;
    }

    /** 解析 JSON 对象字符串为 Map；解析失败返回空 Map */
    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (node == null || !node.isObject()) {
                return new HashMap<>();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = OBJECT_MAPPER.treeToValue(node, Map.class);
            return map == null ? new HashMap<>() : map;
        } catch (JacksonException e) {
            return new HashMap<>();
        }
    }

    /** 数组节点序列化为 JSON 字符串；缺失/非数组返回 null */
    private String toJsonArrayString(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return null;
        }
        return node.toString();
    }

    /** 对象节点序列化为 JSON 字符串；缺失/null 返回 null */
    private String toJsonString(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.toString();
    }

    /** 取文本字段；缺失/空白返回 null */
    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    /** 取数字字段；缺失/非数字返回 null */
    private Double numericOrNull(JsonNode item, String field) {
        JsonNode node = item.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asDouble();
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
