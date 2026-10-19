package com.fakemianshi.service;

import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.dto.RetryAnswerResult;
import com.fakemianshi.dto.ReviewOverview;
import com.fakemianshi.dto.WrongQuestionItem;
import com.fakemianshi.entity.InterviewSession;
import com.fakemianshi.entity.WeaknessTag;
import com.fakemianshi.entity.WrittenTestAnswer;
import com.fakemianshi.entity.WrittenTestQuestion;
import com.fakemianshi.repository.InterviewSessionRepository;
import com.fakemianshi.repository.WeaknessTagRepository;
import com.fakemianshi.repository.WrittenTestAnswerRepository;
import com.fakemianshi.repository.WrittenTestQuestionRepository;
import com.fakemianshi.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 错题本测试：用内存 map 模拟仓储，不碰数据库。
 *
 * <p>覆盖：统计口径、状态/知识点过滤、重练判分与订正落库、简答题不可自动判分、
 * 掌握程度「只升不降」的回写规则，以及脏数据不炸页面。
 */
class ReviewServiceTest {

    private WrittenTestAnswerRepository answerRepository;
    private WrittenTestQuestionRepository questionRepository;
    private InterviewSessionRepository sessionRepository;
    private WeaknessTagRepository weaknessTagRepository;
    private ReviewService service;

    private final Map<Long, WrittenTestAnswer> answers = new HashMap<>();
    private final Map<Long, WrittenTestQuestion> questions = new HashMap<>();
    private final Map<Long, InterviewSession> sessions = new HashMap<>();
    private final List<WeaknessTag> tags = new ArrayList<>();

    @BeforeEach
    void setUp() {
        answerRepository = mock(WrittenTestAnswerRepository.class);
        questionRepository = mock(WrittenTestQuestionRepository.class);
        sessionRepository = mock(InterviewSessionRepository.class);
        weaknessTagRepository = mock(WeaknessTagRepository.class);
        // 真实实现：知识点同义匹配（findSameTopic / 别名解析）就是被测行为
        service = new ReviewServiceImpl(answerRepository, questionRepository,
                sessionRepository, weaknessTagRepository,
                new com.fakemianshi.service.impl.WeaknessTagServiceImpl(weaknessTagRepository));

        when(questionRepository.selectById(anyLong()))
                .thenAnswer(inv -> questions.get(inv.<Long>getArgument(0)));
        when(answerRepository.findByQuestionId(anyLong()))
                .thenAnswer(inv -> answers.values().stream()
                        .filter(a -> a.getQuestionId().equals(inv.<Long>getArgument(0)))
                        .findFirst().orElse(null));
        when(answerRepository.findWrongByProjectId(anyLong()))
                .thenAnswer(inv -> answers.values().stream()
                        .filter(a -> Boolean.FALSE.equals(a.getIsCorrect()))
                        .toList());
        when(answerRepository.updateById(any(WrittenTestAnswer.class)))
                .thenAnswer(inv -> {
                    WrittenTestAnswer a = inv.getArgument(0);
                    answers.put(a.getId(), a);
                    return 1;
                });
        when(sessionRepository.selectById(anyLong()))
                .thenAnswer(inv -> sessions.get(inv.<Long>getArgument(0)));
        when(weaknessTagRepository.findByProjectId(anyLong())).thenAnswer(inv -> new ArrayList<>(tags));
        when(weaknessTagRepository.updateById(any(WeaknessTag.class))).thenReturn(1);
        when(weaknessTagRepository.selectById(anyLong()))
                .thenAnswer(inv -> tags.stream()
                        .filter(t -> t.getId().equals(inv.<Long>getArgument(0)))
                        .findFirst().orElse(null));

        // 会话：session 10 属于 project 1
        InterviewSession session = new InterviewSession();
        session.setId(10L);
        session.setProjectId(1L);
        sessions.put(10L, session);
    }

    private void addQuestion(long id, String type, String answer, String pointsJson) {
        WrittenTestQuestion q = new WrittenTestQuestion();
        q.setId(id);
        q.setSessionId(10L);
        q.setType(type);
        q.setContent("题目 " + id);
        q.setAnswer(answer);
        q.setExplanation("解析 " + id);
        q.setKnowledgePoints(pointsJson);
        questions.put(id, q);
    }

    private void addAnswer(long id, long questionId, boolean correct, String userAnswer) {
        WrittenTestAnswer a = new WrittenTestAnswer();
        a.setId(id);
        a.setQuestionId(questionId);
        a.setSessionId(10L);
        a.setIsCorrect(correct);
        a.setUserAnswer(userAnswer);
        answers.put(id, a);
    }

    @Test
    void overview_shouldCountOnlyWrongAnswers() {
        addQuestion(1, "SINGLE_CHOICE", "B", "[\"LongAdder\"]");
        addQuestion(2, "MULTIPLE_CHOICE", "AB", "[\"Redis\"]");
        addQuestion(3, "FILL_BLANK", "6379", "[\"Redis\"]");
        addAnswer(100, 1, false, "A");
        addAnswer(101, 2, false, "AC");
        addAnswer(102, 3, true, "6379");   // 答对的不进错题本

        ReviewOverview overview = service.overview(1L);

        assertEquals(2, overview.getWrongTotal());
        assertEquals(2, overview.getPendingTotal());
        assertEquals(0, overview.getCorrectedTotal());
        assertEquals(2, overview.getKnowledgePointCount(), "LongAdder + Redis 两个知识点");
    }

    @Test
    void listWrongQuestions_shouldReturnAnswerAndExplanation() {
        addQuestion(1, "SINGLE_CHOICE", "B", "[\"LongAdder\"]");
        addAnswer(100, 1, false, "A");

        List<WrongQuestionItem> items = service.listWrongQuestions(1L, null, null, null);

        assertEquals(1, items.size());
        WrongQuestionItem item = items.get(0);
        assertEquals("A", item.getMyAnswer());
        assertEquals("B", item.getCorrectAnswer(), "错题本必须给出正确答案，否则没法复盘");
        assertEquals("解析 1", item.getExplanation());
        assertTrue(item.isAutoGradable());
        assertFalse(item.isCorrected());
        assertEquals("LongAdder", item.getKnowledgePoints().get(0));
    }

    @Test
    void listWrongQuestions_shouldFilterByStatusAndKnowledgePoint() {
        addQuestion(1, "SINGLE_CHOICE", "B", "[\"LongAdder\"]");
        addQuestion(2, "MULTIPLE_CHOICE", "AB", "[\"Redis 原子性\"]");
        addAnswer(100, 1, false, "A");
        addAnswer(101, 2, false, "AC");

        // 两道错题都还没订正
        assertEquals(2, service.listWrongQuestions(1L, "pending", null, null).size());
        assertEquals(0, service.listWrongQuestions(1L, "corrected", null, null).size());
        assertEquals(2, service.listWrongQuestions(1L, null, null, null).size());

        // 知识点做包含匹配：用 "Redis" 能命中 "Redis 原子性"
        List<WrongQuestionItem> byPoint = service.listWrongQuestions(1L, null, "Redis", null);
        assertEquals(1, byPoint.size());
        assertEquals(2L, byPoint.get(0).getQuestionId());
    }

    @Test
    void retry_shouldGradeAndMarkCorrected() {
        addQuestion(1, "SINGLE_CHOICE", "B", "[\"LongAdder\"]");
        addAnswer(100, 1, false, "A");

        RetryAnswerResult wrong = service.retry(1L, "C");
        assertFalse(wrong.isCorrect());
        assertNull(wrong.getCorrectedAt(), "答错不应被标记为已订正");
        assertEquals(1, wrong.getRetryCount());

        RetryAnswerResult right = service.retry(1L, "b");
        assertTrue(right.isCorrect(), "大小写不应影响判分");
        assertTrue(right.isFirstCorrected());
        assertNotNull(right.getCorrectedAt());
        assertEquals(2, right.getRetryCount());

        // 再次答对不算「首次订正」，但次数继续累加
        RetryAnswerResult again = service.retry(1L, "B");
        assertFalse(again.isFirstCorrected());
        assertEquals(3, again.getRetryCount());
        assertNotNull(again.getCorrectedAt());

        // 订正后原题仍在错题本里（is_correct 保持 false，靠 corrected_at 标记）
        assertEquals(1, service.listWrongQuestions(1L, "corrected", null, null).size());
        assertEquals(0, service.listWrongQuestions(1L, "pending", null, null).size());
    }

    @Test
    void retry_shouldNormalizeMultipleChoiceAnswer() {
        addQuestion(2, "MULTIPLE_CHOICE", "ABC", "[\"Redis\"]");
        addAnswer(101, 2, false, "A");

        assertTrue(service.retry(2L, "c,a,b").isCorrect(), "多选应忽略顺序与分隔符");
    }

    @Test
    void retry_shouldNotAutoGradeShortAnswer() {
        addQuestion(3, "SHORT_ANSWER", "应该先看 jstat 再看 GC 日志", "[\"JVM 排查\"]");
        addAnswer(102, 3, false, "");

        RetryAnswerResult result = service.retry(3L, "我觉得应该先看日志");

        assertFalse(result.isAutoGradable(), "简答题不能自动判分");
        assertFalse(result.isCorrect());
        assertNotNull(result.getCorrectAnswer(), "即使不判分也要给出参考答案供自评");
        assertNull(result.getCorrectedAt());
    }

    @Test
    void retry_shouldUpgradeMasteryOnlyUpward() {
        addQuestion(1, "SINGLE_CHOICE", "B", "[\"LongAdder\"]");
        addAnswer(100, 1, false, "A");

        WeaknessTag tag = new WeaknessTag();
        tag.setId(1L);
        tag.setProjectId(1L);
        tag.setKnowledgePoint("LongAdder");
        tag.setMasteryLevel("WEAK");
        tags.add(tag);

        service.retry(1L, "B");

        // 该知识点只有这一道错题，订正率 100% → GOOD
        assertEquals("GOOD", tag.getMasteryLevel());
    }

    @Test
    void retry_shouldNotDowngradeAlreadyHighMastery() {
        addQuestion(1, "SINGLE_CHOICE", "B", "[\"LongAdder\"]");
        addAnswer(100, 1, false, "A");

        WeaknessTag tag = new WeaknessTag();
        tag.setId(1L);
        tag.setProjectId(1L);
        tag.setKnowledgePoint("LongAdder");
        tag.setMasteryLevel("STRONG");
        tags.add(tag);

        service.retry(1L, "B");

        assertEquals("STRONG", tag.getMasteryLevel(), "掌握程度只升不降");
    }

    @Test
    void retry_shouldLeaveMasteryWhenRatioTooLow() {
        // 同一知识点两道错题，只订正一道 → 50%，按规则到 FAIR；再补一道不订正 → 33%，保持不变
        addQuestion(1, "SINGLE_CHOICE", "B", "[\"JVM\"]");
        addQuestion(2, "SINGLE_CHOICE", "C", "[\"JVM\"]");
        addQuestion(3, "SINGLE_CHOICE", "D", "[\"JVM\"]");
        addAnswer(100, 1, false, "A");
        addAnswer(101, 2, false, "A");
        addAnswer(102, 3, false, "A");

        WeaknessTag tag = new WeaknessTag();
        tag.setId(1L);
        tag.setProjectId(1L);
        tag.setKnowledgePoint("JVM");
        tag.setMasteryLevel("WEAK");
        tags.add(tag);

        service.retry(1L, "B"); // 1/3 = 33% < 40% → 不动
        assertEquals("WEAK", tag.getMasteryLevel());

        service.retry(2L, "C"); // 2/3 = 67% → FAIR
        assertEquals("FAIR", tag.getMasteryLevel());
    }

    @Test
    void retry_shouldThrowWhenQuestionMissing() {
        assertThrows(ResourceNotFoundException.class, () -> service.retry(999L, "A"));
    }

    @Test
    void retry_shouldThrowWhenNoAnswerRecord() {
        addQuestion(1, "SINGLE_CHOICE", "B", "[\"x\"]");
        assertThrows(ResourceNotFoundException.class, () -> service.retry(1L, "A"));
    }

    @Test
    void shouldSurviveDirtyKnowledgePoints() {
        addQuestion(1, "SINGLE_CHOICE", "B", "not-json");
        addAnswer(100, 1, false, "A");

        List<WrongQuestionItem> items = service.listWrongQuestions(1L, null, null, null);
        assertEquals(1, items.size());
        assertTrue(items.get(0).getKnowledgePoints().isEmpty(), "脏知识点按空处理，不能炸整页");
    }

    @Test
    void listWrongQuestions_shouldFilterByTagIdThroughAliases() {
        // 题目的知识点写法与标签代表写法毫无关系，只能靠别名搭桥
        addQuestion(1, "SINGLE_CHOICE", "B", "[\"LongAdder与AtomicLong的区别及适用场景\"]");
        addQuestion(2, "SINGLE_CHOICE", "B", "[\"线程池哈希分发与线程安全\"]");
        addAnswer(100, 1, false, "A");
        addAnswer(101, 2, false, "A");

        WeaknessTag tag = new WeaknessTag();
        tag.setId(7L);
        tag.setProjectId(1L);
        tag.setKnowledgePoint("线上故障排查方法论");
        tag.setAliases("[\"LongAdder与AtomicLong的区别及适用场景\"]");
        tag.setMasteryLevel("WEAK");
        tags.add(tag);

        List<WrongQuestionItem> items = service.listWrongQuestions(1L, null, null, 7L);
        assertEquals(1, items.size(), "按标签筛选要看该标签的全部同义写法");
        assertEquals(1L, items.get(0).getQuestionId());

        // 标签属于别的项目时不能串项目
        tag.setProjectId(2L);
        assertEquals(0, service.listWrongQuestions(1L, null, null, 7L).size());
    }

    @Test
    void retry_shouldUpgradeMasteryForSameTopicWithDifferentWording() {
        // 题目知识点与弱点标签的写法不同，但确实是同一个知识点。
        // 改前这里按名字精确匹配，静默失效：订正了掌握度却不动。
        addQuestion(1, "SINGLE_CHOICE", "B", "[\"LongAdder底层实现原理\"]");
        addAnswer(100, 1, false, "A");

        WeaknessTag tag = new WeaknessTag();
        tag.setId(1L);
        tag.setProjectId(1L);
        tag.setKnowledgePoint("LongAdder与AtomicLong的区别及适用场景");
        tag.setMasteryLevel("WEAK");
        tags.add(tag);

        assertTrue(service.retry(1L, "B").isCorrect());
        assertEquals("GOOD", tag.getMasteryLevel(), "同一知识点的不同写法也要能上调掌握度");
    }
}
