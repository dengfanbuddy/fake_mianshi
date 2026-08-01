package com.fakemianshi.service.impl;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.entity.InterviewProject;
import com.fakemianshi.entity.InterviewSession;
import com.fakemianshi.repository.HistoricalAnalysisRepository;
import com.fakemianshi.repository.InterviewProjectRepository;
import com.fakemianshi.repository.InterviewSessionRepository;
import com.fakemianshi.repository.MockInterviewMessageRepository;
import com.fakemianshi.repository.PositionRequirementRepository;
import com.fakemianshi.repository.QuestionAnalysisRepository;
import com.fakemianshi.repository.ResumeRepository;
import com.fakemianshi.repository.SessionAnalysisRepository;
import com.fakemianshi.repository.WeaknessTagRepository;
import com.fakemianshi.repository.WrittenTestAnswerRepository;
import com.fakemianshi.repository.WrittenTestQuestionRepository;
import com.fakemianshi.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 面试项目服务实现。
 *
 * <p>注意：SQLite 无外键级联约束，删除项目时必须手动按依赖顺序清理关联数据。
 */
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final InterviewProjectRepository projectRepository;
    private final ResumeRepository resumeRepository;
    private final InterviewSessionRepository sessionRepository;
    private final WrittenTestQuestionRepository writtenTestQuestionRepository;
    private final WrittenTestAnswerRepository writtenTestAnswerRepository;
    private final MockInterviewMessageRepository mockInterviewMessageRepository;
    private final SessionAnalysisRepository sessionAnalysisRepository;
    private final QuestionAnalysisRepository questionAnalysisRepository;
    private final WeaknessTagRepository weaknessTagRepository;
    private final HistoricalAnalysisRepository historicalAnalysisRepository;
    private final PositionRequirementRepository positionRequirementRepository;

    @Override
    @Transactional(readOnly = true)
    public List<InterviewProject> findAll() {
        return projectRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewProject findById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("面试项目不存在: id=" + id));
    }

    @Override
    @Transactional
    public InterviewProject create(InterviewProject project) {
        if (project.getName() == null || project.getName().isBlank()) {
            throw new BusinessException("项目名称不能为空");
        }
        project.setId(null);
        return projectRepository.save(project);
    }

    @Override
    @Transactional
    public InterviewProject update(Long id, InterviewProject project) {
        InterviewProject existing = findById(id);
        existing.setName(project.getName());
        existing.setDescription(project.getDescription());
        return projectRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        InterviewProject project = findById(id);

        // 先删除会话及其子数据，再删除项目
        List<InterviewSession> sessions = sessionRepository.findByProjectIdOrderByCreatedAtDesc(id);
        for (InterviewSession session : sessions) {
            Long sessionId = session.getId();
            writtenTestAnswerRepository.deleteAll(writtenTestAnswerRepository.findBySessionId(sessionId));
            writtenTestQuestionRepository.deleteAll(
                    writtenTestQuestionRepository.findBySessionIdOrderByOrderNum(sessionId));
            mockInterviewMessageRepository.deleteAll(
                    mockInterviewMessageRepository.findBySessionIdOrderByCreatedAt(sessionId));
            sessionAnalysisRepository.deleteAll(sessionAnalysisRepository.findBySessionId(sessionId));
            questionAnalysisRepository.deleteAll(questionAnalysisRepository.findBySessionId(sessionId));
            sessionRepository.delete(session);
        }

        resumeRepository.deleteAll(resumeRepository.findByProjectId(id));
        weaknessTagRepository.deleteAll(weaknessTagRepository.findByProjectId(id));
        historicalAnalysisRepository.deleteAll(historicalAnalysisRepository.findByProjectIdOrderByGeneratedAtDesc(id));
        positionRequirementRepository.deleteAll(positionRequirementRepository.findByProjectId(id));

        projectRepository.delete(project);
    }
}
