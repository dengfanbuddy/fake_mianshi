package com.fakemianshi.service.impl;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.entity.PositionRequirement;
import com.fakemianshi.entity.Resume;
import com.fakemianshi.repository.PositionRequirementRepository;
import com.fakemianshi.service.PositionRequirementService;
import com.fakemianshi.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 职位需求服务实现。
 */
@Service
@RequiredArgsConstructor
public class PositionRequirementServiceImpl implements PositionRequirementService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PositionRequirementRepository positionRequirementRepository;
    private final ResumeService resumeService;

    @Override
    @Transactional(readOnly = true)
    public PositionRequirement getByProjectId(Long projectId) {
        List<PositionRequirement> list = positionRequirementRepository.findByProjectId(projectId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    @Transactional
    public PositionRequirement saveOrUpdate(Long projectId, PositionRequirement req) {
        PositionRequirement target = getByProjectId(projectId);
        if (target == null) {
            target = new PositionRequirement();
        }
        target.setProjectId(projectId);
        target.setJobTitle(req.getJobTitle());
        target.setExperience(req.getExperience());
        target.setSeniority(req.getSeniority());
        target.setCompanyType(req.getCompanyType());
        target.setTechStack(req.getTechStack());
        target.setJobDescription(req.getJobDescription());
        target.setSource(req.getSource());
        return positionRequirementRepository.save(target);
    }

    @Override
    @Transactional
    public PositionRequirement createFromResume(Long projectId) {
        PositionRequirement existing = getByProjectId(projectId);
        if (existing != null) {
            return existing;
        }

        Resume resume = resumeService.getLatestByProjectId(projectId);
        if (resume == null || resume.getAnalysisResult() == null || resume.getAnalysisResult().isBlank()) {
            throw new BusinessException("请先上传并分析简历");
        }

        String suggestedPosition = null;
        String suggestedSeniority = null;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(resume.getAnalysisResult());
            suggestedPosition = extractText(root, "suggestedPosition");
            suggestedSeniority = extractText(root, "suggestedSeniority");
        } catch (Exception e) {
            throw new BusinessException("简历分析结果解析失败: " + e.getMessage());
        }

        PositionRequirement req = new PositionRequirement();
        req.setProjectId(projectId);
        req.setJobTitle(suggestedPosition);
        req.setSeniority(suggestedSeniority);
        req.setSource("RESUME");
        return positionRequirementRepository.save(req);
    }

    /** 提取节点文本，字段缺失或为非文本值时返回 null */
    private String extractText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }
}
