package com.fakemianshi.service.impl;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.dto.ExamQuestion;
import com.fakemianshi.dto.QuestionResult;
import com.fakemianshi.dto.WrittenTestDetailResponse;
import com.fakemianshi.dto.WrittenTestStartRequest;
import com.fakemianshi.dto.WrittenTestStartResponse;
import com.fakemianshi.dto.WrittenTestSubmitRequest;
import com.fakemianshi.dto.WrittenTestSubmitResponse;
import com.fakemianshi.entity.InterviewSession;
import com.fakemianshi.entity.WrittenTestAnswer;
import com.fakemianshi.entity.WrittenTestQuestion;
import com.fakemianshi.repository.InterviewSessionRepository;
import com.fakemianshi.repository.WrittenTestAnswerRepository;
import com.fakemianshi.repository.WrittenTestQuestionRepository;
import com.fakemianshi.service.AnalysisService;
import com.fakemianshi.service.ProjectService;
import com.fakemianshi.service.QuestionGenerationService;
import com.fakemianshi.service.WrittenTestService;
import com.fakemianshi.util.WrittenTestGrader;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 笔试服务实现。
 *
 * <p>评分规则：单选/多选每题 3 分、填空每题 2 分、简答每题 5 分（暂不自动判分，留给 LLM 分析环节）；
 * 总分按 100 制归一化：实际得分 / 满分 * 100，保留 1 位小数。
 *
 * <p>计时规则（服务端权威）：出题完成后才写入 startedAt 并开始计时，
 * 截止时间 = startedAt + timeLimit，由服务端计算并通过 deadline 下发；
 * 交卷时以库里的 startedAt 判定是否超时（超出 {@link #SUBMIT_GRACE_SECONDS} 宽限），
 * 超时仍照常判分入库，仅在响应与报告中打上 expired 标记——绝不因超时销毁用户的作答。
 */
@Service
@RequiredArgsConstructor
public class WrittenTestServiceImpl implements WrittenTestService {

    private static final Logger log = LoggerFactory.getLogger(WrittenTestServiceImpl.class);

    private static final int DEFAULT_TIME_LIMIT = 60;
    private static final int DEFAULT_QUESTION_COUNT = 12;

    /**
     * 交卷宽限期（秒）：覆盖前端自动交卷时的网络与渲染延迟。
     * 实际用时超出「时长限制 + 宽限期」即判定为超时提交。
     */
    private static final long SUBMIT_GRACE_SECONDS = 30;

    private static final String TYPE_SINGLE = "SINGLE_CHOICE";
    private static final String TYPE_MULTIPLE = "MULTIPLE_CHOICE";
    private static final String TYPE_FILL = "FILL_BLANK";
    private static final String TYPE_SHORT = "SHORT_ANSWER";

    private static final String SHORT_ANSWER_PENDING_HINT = "待AI分析";

    private final ProjectService projectService;
    private final InterviewSessionRepository sessionRepository;
    private final WrittenTestQuestionRepository questionRepository;
    private final WrittenTestAnswerRepository answerRepository;
    private final QuestionGenerationService questionGenerationService;
    private final AnalysisService analysisService;

    @Override
    public WrittenTestStartResponse start(Long projectId, WrittenTestStartRequest req) {
        InterviewSession session = createSession(projectId, req);

        int questionCount = req != null && req.getQuestionCount() != null ? req.getQuestionCount() : DEFAULT_QUESTION_COUNT;
        List<WrittenTestQuestion> questions;
        try {
            questions = questionGenerationService.generateWrittenTestQuestions(session.getId(), projectId, questionCount);
        } catch (RuntimeException e) {
            discardSession(session.getId());
            throw e;
        }
        return buildStartResponse(startClock(session), questions);
    }

    @Override
    public WrittenTestStartResponse streamStart(Long projectId, WrittenTestStartRequest req,
                                                java.util.function.Consumer<String> onDelta,
                                                java.util.function.Consumer<String> onReasoning) {
        InterviewSession session = createSession(projectId, req);

        int questionCount = req != null && req.getQuestionCount() != null ? req.getQuestionCount() : DEFAULT_QUESTION_COUNT;
        List<WrittenTestQuestion> questions;
        try {
            questions = questionGenerationService
                    .generateWrittenTestQuestionsStream(session.getId(), projectId, questionCount, onDelta, onReasoning);
        } catch (RuntimeException e) {
            discardSession(session.getId());
            throw e;
        }
        return buildStartResponse(startClock(session), questions);
    }

    /** 校验项目并创建笔试会话（含默认时长/题数），此时尚未开始计时 */
    private InterviewSession createSession(Long projectId, WrittenTestStartRequest req) {
        // 校验项目存在
        projectService.findById(projectId);

        int timeLimit = req != null && req.getTimeLimit() != null ? req.getTimeLimit() : DEFAULT_TIME_LIMIT;
        InterviewSession session = new InterviewSession();
        session.setProjectId(projectId);
        session.setType("WRITTEN");
        session.setStatus("IN_PROGRESS");
        session.setTimeLimit(timeLimit);
        sessionRepository.insert(session);
        return session;
    }

    /**
     * 记录考试真正开始的时间并落库。
     *
     * <p>必须在出题（LLM）完成后调用：一次出题可能要几十秒，若在创建会话时就开始计时，
     * 用户还没看到第一道题就已经被扣掉了一两分钟。
     */
    private InterviewSession startClock(InterviewSession session) {
        session.setStartedAt(LocalDateTime.now());
        sessionRepository.updateById(session);
        return session;
    }

    /** 出题失败时清理孤儿会话，避免 IN_PROGRESS 的空考试在库里越积越多 */
    private void discardSession(Long sessionId) {
        try {
            sessionRepository.deleteById(sessionId);
        } catch (Exception e) {
            log.warn("清理出题失败的会话失败: sessionId={}, cause={}", sessionId, e.getMessage());
        }
    }

    private WrittenTestStartResponse buildStartResponse(InterviewSession session, List<WrittenTestQuestion> questions) {
        List<ExamQuestion> examQuestions = questions.stream().map(this::toExamQuestion).toList();

        WrittenTestStartResponse response = new WrittenTestStartResponse();
        response.setSessionId(session.getId());
        response.setTimeLimit(session.getTimeLimit());
        response.setStartedAt(toEpochMilli(session.getStartedAt()));
        response.setDeadline(toEpochMilli(deadlineOf(session)));
        response.setQuestions(examQuestions);
        return response;
    }

    @Override
    public WrittenTestStartResponse getExamInProgress(Long sessionId) {
        InterviewSession session = Optional.ofNullable(sessionRepository.selectById(sessionId))
                .orElseThrow(() -> new BusinessException("笔试会话不存在"));
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new BusinessException("该笔试已结束或状态异常，无法继续");
        }
        List<WrittenTestQuestion> questions =
                questionRepository.findBySessionIdOrderByOrderNum(sessionId);
        return buildStartResponse(session, questions);
    }

    @Override
    @Transactional
    public WrittenTestSubmitResponse submit(Long sessionId, WrittenTestSubmitRequest req) {
        InterviewSession session = Optional.ofNullable(sessionRepository.selectById(sessionId))
                .orElseThrow(() -> new BusinessException("笔试已提交或不存在"));
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new BusinessException("笔试已提交或不存在");
        }

        List<WrittenTestQuestion> questions = questionRepository.findBySessionIdOrderByOrderNum(sessionId);
        Map<Long, String> submitted = req != null && req.getAnswers() != null ? req.getAnswers() : Map.of();

        List<QuestionResult> results = new ArrayList<>();
        double earned = 0;
        double full = 0;
        int correctCount = 0;

        for (WrittenTestQuestion question : questions) {
            String type = question.getType();
            String userAnswer = submitted.get(question.getId());
            int weight = weightFor(type);
            full += weight;

            QuestionResult result = new QuestionResult();
            result.setQuestionId(question.getId());
            result.setType(type);
            result.setUserAnswer(userAnswer);
            result.setCorrectAnswer(question.getAnswer());

            WrittenTestAnswer answer = new WrittenTestAnswer();
            answer.setSessionId(sessionId);
            answer.setQuestionId(question.getId());
            answer.setUserAnswer(userAnswer);

            if (TYPE_SHORT.equals(type)) {
                // 简答题不做自动判分，isCorrect 置 null，留给 Task 19 的 LLM 分析环节评分
                result.setIsCorrect(null);
                result.setScore(0.0);
                result.setExplanation(SHORT_ANSWER_PENDING_HINT);
                answer.setIsCorrect(null);
                answer.setScore(0.0);
            } else {
                boolean correct = grade(question, userAnswer);
                result.setIsCorrect(correct);
                result.setScore(correct ? (double) weight : 0.0);
                result.setExplanation(question.getExplanation());
                answer.setIsCorrect(correct);
                answer.setScore(correct ? (double) weight : 0.0);
                if (correct) {
                    earned += weight;
                    correctCount++;
                }
            }
            answerRepository.insert(answer);
            results.add(result);
        }

        double totalScore = normalizeScore(earned, full);
        LocalDateTime now = LocalDateTime.now();
        // 服务端计时校验：以库里的 startedAt + timeLimit 判定是否超时，
        // 不信任前端传来的任何时长/剩余时间（前端只能影响自己看到的倒计时）。
        boolean expired = isExpired(session, now);
        session.setStatus("COMPLETED");
        session.setCompletedAt(now);
        sessionRepository.updateById(session);

        if (expired) {
            log.warn("笔试超时提交: sessionId={}, timeLimit={}min, elapsed={}s",
                    sessionId, session.getTimeLimit(), elapsedSecondsOf(session));
        }

        // 自动触发会话分析（在事务提交后执行，失败不阻断提交）
        triggerAnalysisAfterCommit(sessionId);

        WrittenTestSubmitResponse response = new WrittenTestSubmitResponse();
        response.setSessionId(sessionId);
        response.setTotalScore(totalScore);
        response.setTotalQuestions(questions.size());
        response.setCorrectCount(correctCount);
        response.setTimeLimit(session.getTimeLimit());
        response.setElapsedSeconds(elapsedSecondsOf(session));
        response.setExpired(expired);
        response.setResults(results);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public WrittenTestDetailResponse getDetail(Long sessionId) {
        InterviewSession session = Optional.ofNullable(sessionRepository.selectById(sessionId))
                .orElseThrow(() -> new ResourceNotFoundException("笔试会话不存在: id=" + sessionId));
        // 类型校验：模拟面试会话不应走笔试详情接口（前端以此判定会话类型）
        if (!"WRITTEN".equals(session.getType())) {
            throw new ResourceNotFoundException("该会话不是笔试会话");
        }

        List<WrittenTestQuestion> questions = questionRepository.findBySessionIdOrderByOrderNum(sessionId);
        Map<Long, WrittenTestAnswer> answerByQuestion = answerRepository.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(WrittenTestAnswer::getQuestionId, Function.identity(), (a, b) -> a));

        List<QuestionResult> results = new ArrayList<>();
        double earned = 0;
        double full = 0;

        for (WrittenTestQuestion question : questions) {
            int weight = weightFor(question.getType());
            full += weight;
            WrittenTestAnswer answer = answerByQuestion.get(question.getId());

            QuestionResult result = new QuestionResult();
            result.setQuestionId(question.getId());
            result.setType(question.getType());
            result.setUserAnswer(answer == null ? null : answer.getUserAnswer());
            result.setCorrectAnswer(question.getAnswer());
            result.setIsCorrect(answer == null ? null : answer.getIsCorrect());
            result.setScore(answer == null ? 0.0 : answer.getScore());
            result.setExplanation(question.getExplanation());

            if (answer != null && Boolean.TRUE.equals(answer.getIsCorrect())) {
                earned += answer.getScore() == null ? weight : answer.getScore();
            }
            results.add(result);
        }

        WrittenTestDetailResponse response = new WrittenTestDetailResponse();
        response.setSessionId(sessionId);
        response.setResults(results);
        response.setTotalScore(normalizeScore(earned, full));
        response.setTimeLimit(session.getTimeLimit());
        response.setElapsedSeconds(elapsedSecondsOf(session));
        // 用交卷时刻判定（而非当前时间），否则事后每看一次报告都会显示"超时"
        response.setExpired(isExpired(session, finishedAtOf(session)));
        return response;
    }

    /**
     * 在事务提交后自动触发会话分析：保证分析能读取本次提交的全部作答数据。
     *
     * <p>用 {@link AnalysisService#triggerAnalyze} 而不是直接 {@code analyzeSession}：
     * 后者会同步跑完整轮 LLM 分析（30-120 秒），把交卷接口一起拖住，
     * 用户点完"交卷"要干等一两分钟才跳转成绩页。triggerAnalyze 立即返回、后台生成，
     * 报告页自身有轮询/流式入口。
     */
    private void triggerAnalysisAfterCommit(Long sessionId) {
        Runnable task = () -> {
            try {
                analysisService.triggerAnalyze(sessionId);
            } catch (Exception e) {
                log.warn("笔试会话分析自动触发失败: sessionId={}, cause={}", sessionId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    // ---------------- 计时校验 ----------------

    /** 截止时间 = 开始时间 + 时长限制；未开始计时返回 null */
    private LocalDateTime deadlineOf(InterviewSession session) {
        if (session.getStartedAt() == null) {
            return null;
        }
        int limit = session.getTimeLimit() == null ? DEFAULT_TIME_LIMIT : session.getTimeLimit();
        return session.getStartedAt().plusMinutes(limit);
    }

    /** 会话结束时刻：已交卷用 completedAt，否则用当前时间 */
    private LocalDateTime finishedAtOf(InterviewSession session) {
        return session.getCompletedAt() != null ? session.getCompletedAt() : LocalDateTime.now();
    }

    /** 以给定时刻判定是否超出「时长限制 + 宽限期」 */
    private boolean isExpired(InterviewSession session, LocalDateTime at) {
        LocalDateTime deadline = deadlineOf(session);
        return deadline != null && at.isAfter(deadline.plusSeconds(SUBMIT_GRACE_SECONDS));
    }

    /** 实际用时（秒）；未开始计时返回 null */
    private Long elapsedSecondsOf(InterviewSession session) {
        if (session.getStartedAt() == null) {
            return null;
        }
        long seconds = Duration.between(session.getStartedAt(), finishedAtOf(session)).getSeconds();
        return Math.max(0, seconds);
    }

    /** LocalDateTime → epoch 毫秒（按服务器时区），null 安全 */
    private Long toEpochMilli(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 题型权重：单选/多选 3 分，填空 2 分，简答 5 分 */
    private int weightFor(String type) {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            case TYPE_SINGLE, TYPE_MULTIPLE -> 3;
            case TYPE_FILL -> 2;
            case TYPE_SHORT -> 5;
            default -> 0;
        };
    }

    /** 逐题判分（简答题不进入此流程）。实现见 {@link WrittenTestGrader}，错题本重练共用同一份 */
    private boolean grade(WrittenTestQuestion question, String userAnswer) {
        return WrittenTestGrader.grade(question.getType(), question.getAnswer(), userAnswer);
    }

    /** 多选答案规范化（委托给共用实现） */
    private String normalizeMultiple(String answer) {
        return WrittenTestGrader.normalizeMultiple(answer);
    }

    /** 100 制归一化并保留 1 位小数 */
    private double normalizeScore(double earned, double full) {
        if (full <= 0) {
            return 0.0;
        }
        double raw = earned / full * 100;
        return BigDecimal.valueOf(raw).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** 考试阶段的题目脱敏：不返回 answer / explanation */
    private ExamQuestion toExamQuestion(WrittenTestQuestion question) {
        ExamQuestion exam = new ExamQuestion();
        exam.setId(question.getId());
        exam.setType(question.getType());
        exam.setContent(question.getContent());
        exam.setOptions(question.getOptions());
        exam.setOrderNum(question.getOrderNum());
        return exam;
    }
}
