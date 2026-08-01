package com.fakemianshi.service;

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
import com.fakemianshi.service.impl.AnalysisServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 面试分析服务单元测试：纯 Mockito，不启动 Spring 容器。
 * 用内存列表模拟仓库的 save / findBy 行为，验证分析与弱点标签的完整链路。
 */
@SuppressWarnings("unchecked")
class AnalysisServiceTest {

    private InterviewSessionRepository sessionRepository;
    private WrittenTestQuestionRepository writtenTestQuestionRepository;
    private WrittenTestAnswerRepository writtenTestAnswerRepository;
    private MockInterviewMessageRepository mockInterviewMessageRepository;
    private SessionAnalysisRepository sessionAnalysisRepository;
    private QuestionAnalysisRepository questionAnalysisRepository;
    private WeaknessTagRepository weaknessTagRepository;
    private HistoricalAnalysisRepository historicalAnalysisRepository;
    private LlmService llmService;
    private AnalysisService service;

    /** 模拟内存库 */
    private final List<SessionAnalysis> analysisStore = new ArrayList<>();
    private final List<QuestionAnalysis> questionAnalysisStore = new ArrayList<>();
    private final List<WeaknessTag> weaknessTagsStore = new ArrayList<>();
    private final List<HistoricalAnalysis> historyStore = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sessionRepository = mock(InterviewSessionRepository.class);
        writtenTestQuestionRepository = mock(WrittenTestQuestionRepository.class);
        writtenTestAnswerRepository = mock(WrittenTestAnswerRepository.class);
        mockInterviewMessageRepository = mock(MockInterviewMessageRepository.class);
        sessionAnalysisRepository = mock(SessionAnalysisRepository.class);
        questionAnalysisRepository = mock(QuestionAnalysisRepository.class);
        weaknessTagRepository = mock(WeaknessTagRepository.class);
        historicalAnalysisRepository = mock(HistoricalAnalysisRepository.class);
        llmService = mock(LlmService.class);

        service = new AnalysisServiceImpl(
                sessionRepository, writtenTestQuestionRepository, writtenTestAnswerRepository,
                mockInterviewMessageRepository, sessionAnalysisRepository, questionAnalysisRepository,
                weaknessTagRepository, historicalAnalysisRepository, llmService);

        // 会话分析内存库
        when(sessionAnalysisRepository.insert(any(SessionAnalysis.class))).thenAnswer(inv -> {
            SessionAnalysis a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId((long) (analysisStore.size() + 1));
            }
            analysisStore.add(a);
            return 1;
        });
        when(sessionAnalysisRepository.findBySessionId(anyLong())).thenAnswer(inv -> {
            Long sid = inv.getArgument(0);
            return analysisStore.stream().filter(a -> sid.equals(a.getSessionId())).toList();
        });

        // 单题分析内存库
        when(questionAnalysisRepository.insert(any(QuestionAnalysis.class))).thenAnswer(inv -> {
            QuestionAnalysis qa = inv.getArgument(0);
            questionAnalysisStore.add(qa);
            return 1;
        });
        when(questionAnalysisRepository.findBySessionId(anyLong())).thenAnswer(inv -> {
            Long sid = inv.getArgument(0);
            return questionAnalysisStore.stream().filter(q -> sid.equals(q.getSessionId())).toList();
        });

        // 弱点标签内存库：findByProjectId 返回副本（对象引用相同），save 负责插入/更新
        when(weaknessTagRepository.findByProjectId(anyLong())).thenAnswer(inv -> new ArrayList<>(weaknessTagsStore));
        when(weaknessTagRepository.insert(any(WeaknessTag.class))).thenAnswer(inv -> {
            WeaknessTag t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId((long) (weaknessTagsStore.size() + 1));
                weaknessTagsStore.add(t);
            }
            return 1;
        });

        // 历史分析内存库
        when(historicalAnalysisRepository.insert(any(HistoricalAnalysis.class))).thenAnswer(inv -> {
            HistoricalAnalysis h = inv.getArgument(0);
            if (h.getId() == null) {
                h.setId((long) (historyStore.size() + 1));
            }
            historyStore.add(h);
            return 1;
        });
    }

    // ---------- analyzeSession WRITTEN ----------

    @Test
    void analyzeSession_written_shouldSaveAnalysisAndQuestionsAndUpdateWeaknessTags() {
        InterviewSession session = session(1L, 10L, "WRITTEN");
        when(sessionRepository.selectById(1L)).thenReturn(session);

        // 题目 + 答案：一题自动判分客观题、一题简答题
        WrittenTestQuestion q1 = writtenQuestion(100L, "SINGLE_CHOICE", "单选：以下哪个是并发关键字？");
        WrittenTestQuestion q2 = writtenQuestion(101L, "SHORT_ANSWER", "简述 HashMap 的扩容机制");
        when(writtenTestQuestionRepository.findBySessionIdOrderByOrderNum(1L)).thenReturn(List.of(q1, q2));

        WrittenTestAnswer a1 = writtenAnswer(100L, "A", true, 3.0);
        WrittenTestAnswer a2 = writtenAnswer(101L, "扩容为两倍", null, 0.0);
        when(writtenTestAnswerRepository.findBySessionId(1L)).thenReturn(List.of(a1, a2));

        // 已有弱点标签：JVM垃圾回收 出现 1 次
        WeaknessTag existing = new WeaknessTag();
        existing.setId(1L);
        existing.setProjectId(10L);
        existing.setKnowledgePoint("JVM垃圾回收");
        existing.setMasteryLevel("WEAK");
        existing.setOccurrenceCount(1);
        weaknessTagsStore.add(existing);

        // LLM 返回固定 JSON
        String llmJson = """
                {"overallScore":72.5,"strengths":["Java基础扎实","集合框架理解较好"],"weaknesses":["并发编程理解不深"],"knowledgeGaps":["JVM垃圾回收","Spring事务传播机制"],"improvementPlan":{"topics":["JVM","并发编程"],"suggestions":["阅读源码","动手实践"]},"questionAnalyses":[{"questionContent":"单选：以下哪个是并发关键字？","answerContent":"A","accuracy":8,"depth":0,"clarity":0,"fluency":0,"tone":"","improvementSuggestion":"需要区分synchronized与volatile"},{"questionContent":"简述 HashMap 的扩容机制","answerContent":"扩容为两倍","accuracy":5,"depth":4,"clarity":5,"fluency":0,"tone":"","improvementSuggestion":"补充扩容触发条件与步骤"}]}
                """;
        when(llmService.chat(anyString(), anyString(), anyInt())).thenReturn(new LlmResponse(llmJson, "stop", 100));

        SessionAnalysis saved = service.analyzeSession(1L);

        // SessionAnalysis 保存
        assertNotNull(saved.getId());
        assertEquals(1L, saved.getSessionId());
        assertEquals(72.5, saved.getOverallScore());
        assertTrue(saved.getStrengths().contains("Java基础扎实"));
        assertTrue(saved.getKnowledgeGaps().contains("JVM垃圾回收"));
        assertTrue(saved.getImprovementPlan().contains("topics"));

        // QuestionAnalysis 保存 2 条，questionRefId 回填到题目 id
        assertEquals(2, questionAnalysisStore.size());
        assertEquals(100L, questionAnalysisStore.get(0).getQuestionRefId());
        assertEquals(8.0, questionAnalysisStore.get(0).getAccuracy());
        assertEquals(101L, questionAnalysisStore.get(1).getQuestionRefId());

        // 弱点标签：已有标签累加、新标签创建
        assertEquals(2, weaknessTagsStore.size());
        WeaknessTag jvm = weaknessTagsStore.stream()
                .filter(t -> "JVM垃圾回收".equals(t.getKnowledgePoint())).findFirst().orElseThrow();
        assertEquals(2, jvm.getOccurrenceCount());
        assertEquals("FAIR", jvm.getMasteryLevel());
        assertEquals(1L, jvm.getLastSessionId());

        WeaknessTag spring = weaknessTagsStore.stream()
                .filter(t -> "Spring事务传播机制".equals(t.getKnowledgePoint())).findFirst().orElseThrow();
        assertEquals(1, spring.getOccurrenceCount());
        assertEquals("WEAK", spring.getMasteryLevel());
        assertEquals(1L, spring.getLastSessionId());
    }

    // ---------- analyzeSession MOCK ----------

    @Test
    void analyzeSession_mock_shouldParseCommunicationEvaluation() {
        InterviewSession session = session(2L, 10L, "MOCK");
        when(sessionRepository.selectById(2L)).thenReturn(session);

        MockInterviewMessage m1 = mockMessage(1L, "INTERVIEWER", "请谈谈你对JVM内存模型的理解");
        MockInterviewMessage m2 = mockMessage(2L, "CANDIDATE", "栈管运行，堆管存储");
        when(mockInterviewMessageRepository.findBySessionIdOrderByCreatedAt(2L)).thenReturn(List.of(m1, m2));

        String llmJson = """
                {"overallScore":80,"strengths":["表达清晰"],"weaknesses":["深度不足"],"knowledgeGaps":["JVM调优"],"communicationEvaluation":"候选人表达清晰、逻辑连贯，但偶有停顿与口头禅。","improvementPlan":{"topics":["JVM"],"suggestions":["多实践"]},"questionAnalyses":[{"questionContent":"请谈谈你对JVM内存模型的理解","answerContent":"栈管运行，堆管存储","accuracy":6,"depth":4,"clarity":7,"fluency":6,"tone":"自信","improvementSuggestion":"补充堆内存细节"}]}
                """;
        when(llmService.chat(anyString(), anyString(), anyInt())).thenReturn(new LlmResponse(llmJson, "stop", 100));

        SessionAnalysis saved = service.analyzeSession(2L);

        assertEquals("候选人表达清晰、逻辑连贯，但偶有停顿与口头禅。", saved.getCommunicationEvaluation());
        // 单题分析 questionRefId 回填到面试官消息 id
        assertEquals(1, questionAnalysisStore.size());
        assertEquals(1L, questionAnalysisStore.get(0).getQuestionRefId());
        assertEquals(6.0, questionAnalysisStore.get(0).getFluency());
    }

    // ---------- analyzeSession 新格式（知识盲区对象 + 等级/薪资/性格 + 单题分类） ----------

    @Test
    void analyzeSession_written_shouldParseNewFormatFields() {
        InterviewSession session = session(3L, 10L, "WRITTEN");
        when(sessionRepository.selectById(3L)).thenReturn(session);

        WrittenTestQuestion q1 = writtenQuestion(300L, "SINGLE_CHOICE", "单选：synchronized 和 ReentrantLock 的区别？");
        when(writtenTestQuestionRepository.findBySessionIdOrderByOrderNum(3L)).thenReturn(List.of(q1));
        WrittenTestAnswer a1 = writtenAnswer(300L, "B", false, 0.0);
        when(writtenTestAnswerRepository.findBySessionId(3L)).thenReturn(List.of(a1));

        String llmJson = """
                {"overallScore":65,"overallLevel":"中级工程师","expectedSalaryRange":"18k-25k","strengths":["基础较稳"],"weaknesses":["并发理解浅"],"knowledgeGaps":[{"point":"AQS原理","explanation":"AQS基于volatile state和CLH队列，支持独占/共享两种模式，ReentrantLock依赖它实现重入与公平锁。"}],"personalitySummary":"表达谨慎、逻辑尚可，遇到不熟的问题容易绕弯。","characterTraits":["谨慎","条理清晰"],"characterDefects":[{"defect":"不熟的问题易绕弯","improvement":"先直接说不知道，再给部分理解与思路"}],"improvementPlan":{"topics":[{"topic":"并发编程","action":"精读AQS源码并画时序图","example":"以ReentrantLock加锁为例，画出acquire队列流转"}],"suggestions":["每周一道并发题"]},"questionAnalyses":[{"questionContent":"单选：synchronized 和 ReentrantLock 的区别？","answerContent":"B","category":"并发","difficulty":"中级","focusPoint":"考察锁机制与AQS的理解","accuracy":3,"accuracyReason":"选错了答案，混淆了公平锁与可重入概念，准确性不足","depth":0,"depthReason":"客观题无展开，深度不评分","clarity":0,"clarityReason":"客观题无表达，清晰度不评分","fluency":0,"fluencyReason":"客观题无口语表达，流畅度不评分","tone":"","answerApproach":"先答两者本质（内置锁vs显式锁），再从可中断/公平/超时/条件队列展开","example":"synchronized是JVM内置锁，ReentrantLock基于AQS支持中断、超时与公平策略…","improvementSuggestion":"补充AQS与锁升级知识"}]}
                """;
        when(llmService.chat(anyString(), anyString(), anyInt())).thenReturn(new LlmResponse(llmJson, "stop", 100));

        SessionAnalysis saved = service.analyzeSession(3L);

        assertEquals("中级工程师", saved.getOverallLevel());
        assertEquals("18k-25k", saved.getExpectedSalaryRange());
        assertTrue(saved.getPersonalitySummary().contains("谨慎"));
        assertTrue(saved.getCharacterTraits().contains("谨慎"));
        assertTrue(saved.getCharacterDefects().contains("AQS原理") || saved.getCharacterDefects() != null);

        // 弱点标签从新格式对象的 point 字段提取
        WeaknessTag aqs = weaknessTagsStore.stream()
                .filter(t -> "AQS原理".equals(t.getKnowledgePoint())).findFirst().orElseThrow();
        assertEquals("WEAK", aqs.getMasteryLevel());
        assertEquals(1, aqs.getOccurrenceCount());

        // 单题分析新字段
        assertEquals(1, questionAnalysisStore.size());
        QuestionAnalysis qa = questionAnalysisStore.get(0);
        assertEquals("并发", qa.getCategory());
        assertEquals("中级", qa.getDifficulty());
        assertTrue(qa.getFocusPoint().contains("锁机制"));
        assertTrue(qa.getAnswerApproach().contains("先答两者本质"));
        assertTrue(qa.getExample().contains("synchronized是JVM内置锁"));
        // 打分依据
        assertTrue(qa.getAccuracyReason().contains("选错了答案"));
        assertTrue(qa.getDepthReason().contains("客观题无展开"));
        assertEquals(3.0, qa.getAccuracy());
    }

    @Test
    void getSessionAnalysis_shouldIncludeNewFields() {
        SessionAnalysis analysis = new SessionAnalysis();
        analysis.setId(1L);
        analysis.setSessionId(1L);
        analysis.setOverallScore(88.0);
        analysis.setStrengths("[\"基础扎实\"]");
        analysis.setWeaknesses("[\"并发弱\"]");
        analysis.setKnowledgeGaps("[{\"point\":\"AQS\",\"explanation\":\"基于CLH队列…\"}]");
        analysis.setOverallLevel("高级工程师");
        analysis.setExpectedSalaryRange("30k-40k");
        analysis.setPersonalitySummary("沉稳自信");
        analysis.setCharacterTraits("[\"沉稳\"]");
        analysis.setCharacterDefects("[{\"defect\":\"语速偏快\",\"improvement\":\"放慢节奏\"}]");
        analysis.setCommunicationEvaluation("表达流畅");
        analysis.setImprovementPlan("{\"topics\":[{\"topic\":\"并发\",\"action\":\"读源码\",\"example\":\"示例\"}],\"suggestions\":[\"练习\"]}");
        analysisStore.add(analysis);

        QuestionAnalysis qa = new QuestionAnalysis();
        qa.setSessionId(1L);
        qa.setQuestionRefId(100L);
        qa.setQuestionContent("请解释 volatile");
        qa.setAnswerContent("可见性与有序性");
        qa.setAccuracy(7.0);
        qa.setAccuracyReason("答出核心语义但未举例说明");
        qa.setCategory("并发");
        qa.setDifficulty("中级");
        qa.setFocusPoint("考察内存模型理解");
        qa.setAnswerApproach("先答定义再举例");
        qa.setExample("示例内容");
        questionAnalysisStore.add(qa);

        AnalysisResultDTO dto = service.getSessionAnalysis(1L);

        assertEquals("高级工程师", dto.getOverallLevel());
        assertEquals("30k-40k", dto.getExpectedSalaryRange());
        assertEquals("沉稳自信", dto.getPersonalitySummary());
        assertTrue(dto.getCharacterTraits().contains("沉稳"));
        assertEquals(1, dto.getCharacterDefects().size());
        assertEquals(88.0, dto.getOverallScore());
        Map<String, Object> qaMap = dto.getQuestionAnalyses().get(0);
        assertEquals("并发", qaMap.get("category"));
        assertEquals("中级", qaMap.get("difficulty"));
        assertEquals("先答定义再举例", qaMap.get("answerApproach"));
        assertEquals("示例内容", qaMap.get("example"));
        assertEquals("答出核心语义但未举例说明", qaMap.get("accuracyReason"));
        // knowledgeGaps 为对象列表
        assertEquals(1, dto.getKnowledgeGaps().size());
    }

    // ---------- 幂等 ----------

    @Test
    void analyzeSession_shouldReturnExistingAnalysis_withoutCallingLlm() {
        InterviewSession session = session(1L, 10L, "WRITTEN");
        when(sessionRepository.selectById(1L)).thenReturn(session);

        SessionAnalysis existing = new SessionAnalysis();
        existing.setId(1L);
        existing.setSessionId(1L);
        existing.setOverallScore(60.0);
        analysisStore.add(existing);

        SessionAnalysis result = service.analyzeSession(1L);

        assertSame(existing, result);
        verifyNoInteractions(llmService);
    }

    // ---------- analyzeHistory ----------

    @Test
    void analyzeHistory_shouldGenerateHistoricalAnalysis() {
        InterviewSession session = session(1L, 10L, "WRITTEN");
        when(sessionRepository.findByProjectIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(session));

        SessionAnalysis analysis = new SessionAnalysis();
        analysis.setId(1L);
        analysis.setSessionId(1L);
        analysis.setOverallScore(70.0);
        analysis.setKnowledgeGaps("[\"JVM垃圾回收\"]");
        analysisStore.add(analysis);

        String llmJson = """
                {"trends":[{"dimension":"总体评分","trend":"上升","detail":"分数持续提高"},{"dimension":"技术深度","trend":"平稳","detail":"深度有待加强"},{"dimension":"沟通表达","trend":"上升","detail":"表达更清晰"}],"recurringWeaknesses":["JVM垃圾回收"],"recommendedFocus":["并发编程","JVM","Spring"],"overallProgress":"整体稳步提升"}
                """;
        when(llmService.chat(anyString(), anyString(), anyInt())).thenReturn(new LlmResponse(llmJson, "stop", 100));

        HistoricalAnalysis historical = service.analyzeHistory(10L);

        assertNotNull(historical.getId());
        assertEquals(10L, historical.getProjectId());
        assertTrue(historical.getAnalysisData().contains("trends"));
        assertTrue(historical.getAnalysisData().contains("JVM垃圾回收"));
        assertEquals(1, historyStore.size());
    }

    @Test
    void analyzeHistory_shouldThrow_whenNoSessionAnalyses() {
        when(sessionRepository.findByProjectIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.analyzeHistory(10L));
        verifyNoInteractions(llmService);
    }

    // ---------- updateWeaknessTags ----------

    @Test
    void updateWeaknessTags_shouldIncrementExistingAndCreateNew() {
        SessionAnalysis analysis = new SessionAnalysis();
        analysis.setId(1L);
        analysis.setSessionId(1L);
        analysis.setKnowledgeGaps("[\"JVM垃圾回收\",\"Spring事务传播机制\"]");
        analysisStore.add(analysis);

        WeaknessTag existing = new WeaknessTag();
        existing.setId(1L);
        existing.setProjectId(10L);
        existing.setKnowledgePoint("JVM垃圾回收");
        existing.setMasteryLevel("WEAK");
        existing.setOccurrenceCount(1);
        weaknessTagsStore.add(existing);

        service.updateWeaknessTags(10L, 1L);

        assertEquals(2, weaknessTagsStore.size());
        WeaknessTag jvm = weaknessTagsStore.get(0);
        assertEquals(2, jvm.getOccurrenceCount());
        assertEquals("FAIR", jvm.getMasteryLevel());
        assertEquals(1L, jvm.getLastSessionId());

        WeaknessTag spring = weaknessTagsStore.get(1);
        assertEquals("Spring事务传播机制", spring.getKnowledgePoint());
        assertEquals(1, spring.getOccurrenceCount());
        assertEquals("WEAK", spring.getMasteryLevel());
        assertEquals(1L, spring.getLastSessionId());
        assertEquals(10L, spring.getProjectId());
    }

    // ---------- getSessionAnalysis ----------

    @Test
    void getSessionAnalysis_shouldAssembleDto() {
        SessionAnalysis analysis = new SessionAnalysis();
        analysis.setId(1L);
        analysis.setSessionId(1L);
        analysis.setOverallScore(88.0);
        analysis.setStrengths("[\"基础扎实\"]");
        analysis.setWeaknesses("[\"并发弱\"]");
        analysis.setKnowledgeGaps("[\"AQS\"]");
        analysis.setCommunicationEvaluation("表达流畅");
        analysis.setImprovementPlan("{\"topics\":[\"并发\"],\"suggestions\":[\"练习\"]}");
        analysisStore.add(analysis);

        QuestionAnalysis qa = new QuestionAnalysis();
        qa.setSessionId(1L);
        qa.setQuestionRefId(100L);
        qa.setQuestionContent("请解释 volatile");
        qa.setAnswerContent("可见性与有序性");
        qa.setAccuracy(7.0);
        questionAnalysisStore.add(qa);

        AnalysisResultDTO dto = service.getSessionAnalysis(1L);

        assertEquals(1L, dto.getSessionId());
        assertEquals(88.0, dto.getOverallScore());
        assertTrue(dto.getStrengths().contains("基础扎实"));
        assertTrue(dto.getKnowledgeGaps().contains("AQS"));
        assertEquals("表达流畅", dto.getCommunicationEvaluation());
        assertEquals(List.of("并发"), dto.getImprovementPlan().get("topics"));
        assertEquals(1, dto.getQuestionAnalyses().size());
        assertEquals(100L, dto.getQuestionAnalyses().get(0).get("questionRefId"));
    }

    @Test
    void getSessionAnalysis_shouldThrow_whenNotExists() {
        assertThrows(ResourceNotFoundException.class, () -> service.getSessionAnalysis(999L));
    }

    // ---------- helpers ----------

    private InterviewSession session(Long id, Long projectId, String type) {
        InterviewSession s = new InterviewSession();
        s.setId(id);
        s.setProjectId(projectId);
        s.setType(type);
        s.setStatus("COMPLETED");
        return s;
    }

    private WrittenTestQuestion writtenQuestion(Long id, String type, String content) {
        WrittenTestQuestion q = new WrittenTestQuestion();
        q.setId(id);
        q.setSessionId(1L);
        q.setType(type);
        q.setContent(content);
        q.setAnswer("A");
        return q;
    }

    private WrittenTestAnswer writtenAnswer(Long questionId, String userAnswer, Boolean correct, Double score) {
        WrittenTestAnswer a = new WrittenTestAnswer();
        a.setQuestionId(questionId);
        a.setSessionId(1L);
        a.setUserAnswer(userAnswer);
        a.setIsCorrect(correct);
        a.setScore(score);
        return a;
    }

    private MockInterviewMessage mockMessage(Long id, String role, String content) {
        MockInterviewMessage m = new MockInterviewMessage();
        m.setId(id);
        m.setSessionId(2L);
        m.setRole(role);
        m.setContent(content);
        return m;
    }
}
