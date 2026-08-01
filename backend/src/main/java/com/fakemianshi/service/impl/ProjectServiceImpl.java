package com.fakemianshi.service.impl;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.entity.InterviewProject;
import com.fakemianshi.entity.InterviewSession;
import com.fakemianshi.entity.Resume;
import com.fakemianshi.entity.WeaknessTag;
import com.fakemianshi.entity.HistoricalAnalysis;
import com.fakemianshi.entity.PositionRequirement;
import com.fakemianshi.repository.HistoricalAnalysisRepository;
import com.fakemianshi.repository.InterviewProjectRepository;
import com.fakemianshi.repository.InterviewSessionRepository;
import com.fakemianshi.repository.PositionRequirementRepository;
import com.fakemianshi.repository.ResumeRepository;
import com.fakemianshi.repository.WeaknessTagRepository;
import com.fakemianshi.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
    private final WeaknessTagRepository weaknessTagRepository;
    private final HistoricalAnalysisRepository historicalAnalysisRepository;
    private final PositionRequirementRepository positionRequirementRepository;
    private final SessionCleanupService sessionCleanupService;

    @Override
    @Transactional(readOnly = true)
    public List<InterviewProject> findAll() {
        return projectRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewProject findById(Long id) {
        return Optional.ofNullable(projectRepository.selectById(id))
                .orElseThrow(() -> new ResourceNotFoundException("面试项目不存在: id=" + id));
    }

    @Override
    @Transactional
    public InterviewProject create(InterviewProject project) {
        if (project.getName() == null || project.getName().isBlank()) {
            throw new BusinessException("项目名称不能为空");
        }
        project.setId(null);
        projectRepository.insert(project);
        return project;
    }

    @Override
    @Transactional
    public InterviewProject update(Long id, InterviewProject project) {
        InterviewProject existing = findById(id);
        existing.setName(project.getName());
        existing.setDescription(project.getDescription());
        projectRepository.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        InterviewProject project = findById(id);

        // 先删除会话及其子数据、录音文件，再删除项目
        List<InterviewSession> sessions = sessionRepository.findByProjectIdOrderByCreatedAtDesc(id);
        for (InterviewSession session : sessions) {
            sessionCleanupService.deleteSession(session.getId());
        }

        resumeRepository.deleteByIds(resumeRepository.findByProjectId(id).stream().map(Resume::getId).toList());
        weaknessTagRepository.deleteByIds(weaknessTagRepository.findByProjectId(id).stream().map(WeaknessTag::getId).toList());
        historicalAnalysisRepository.deleteByIds(historicalAnalysisRepository.findByProjectIdOrderByGeneratedAtDesc(id).stream().map(HistoricalAnalysis::getId).toList());
        positionRequirementRepository.deleteByIds(positionRequirementRepository.findByProjectId(id).stream().map(PositionRequirement::getId).toList());

        projectRepository.deleteById(project.getId());
    }
}
