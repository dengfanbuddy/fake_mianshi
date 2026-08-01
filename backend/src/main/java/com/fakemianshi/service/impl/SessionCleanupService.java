package com.fakemianshi.service.impl;

import com.fakemianshi.entity.WrittenTestAnswer;
import com.fakemianshi.entity.WrittenTestQuestion;
import com.fakemianshi.entity.MockInterviewMessage;
import com.fakemianshi.entity.SessionAnalysis;
import com.fakemianshi.entity.QuestionAnalysis;
import com.fakemianshi.repository.InterviewSessionRepository;
import com.fakemianshi.repository.MockInterviewMessageRepository;
import com.fakemianshi.repository.QuestionAnalysisRepository;
import com.fakemianshi.repository.SessionAnalysisRepository;
import com.fakemianshi.repository.WrittenTestAnswerRepository;
import com.fakemianshi.repository.WrittenTestQuestionRepository;
import com.fakemianshi.util.AudioStorageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话级联清理服务：删除会话时同步清理其子数据（笔试题目/答案、对话消息、
 * 分析报告、逐题分析）与磁盘上的录音文件。
 */
@Service
@RequiredArgsConstructor
public class SessionCleanupService {

    private final InterviewSessionRepository sessionRepository;
    private final WrittenTestAnswerRepository writtenTestAnswerRepository;
    private final WrittenTestQuestionRepository writtenTestQuestionRepository;
    private final MockInterviewMessageRepository mockInterviewMessageRepository;
    private final SessionAnalysisRepository sessionAnalysisRepository;
    private final QuestionAnalysisRepository questionAnalysisRepository;
    private final AudioStorageUtil audioStorageUtil;

    /** 删除单个会话及其全部关联数据与录音文件 */
    @Transactional
    public void deleteSession(Long sessionId) {
        writtenTestAnswerRepository.deleteByIds(writtenTestAnswerRepository.findBySessionId(sessionId).stream().map(WrittenTestAnswer::getId).toList());
        writtenTestQuestionRepository.deleteByIds(
                writtenTestQuestionRepository.findBySessionIdOrderByOrderNum(sessionId).stream().map(WrittenTestQuestion::getId).toList());
        mockInterviewMessageRepository.deleteByIds(
                mockInterviewMessageRepository.findBySessionIdOrderByCreatedAt(sessionId).stream().map(MockInterviewMessage::getId).toList());
        sessionAnalysisRepository.deleteByIds(sessionAnalysisRepository.findBySessionId(sessionId).stream().map(SessionAnalysis::getId).toList());
        questionAnalysisRepository.deleteByIds(questionAnalysisRepository.findBySessionId(sessionId).stream().map(QuestionAnalysis::getId).toList());
        sessionRepository.deleteById(sessionId);
        // 清理磁盘录音
        audioStorageUtil.deleteSessionAudio(sessionId);
    }
}
