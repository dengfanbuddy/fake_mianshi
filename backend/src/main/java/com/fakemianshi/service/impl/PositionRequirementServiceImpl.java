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

import java.util.ArrayList;
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
        if (target.getId() == null) {
            positionRequirementRepository.insert(target);
        } else {
            positionRequirementRepository.updateById(target);
        }
        return target;
    }

    @Override
    @Transactional
    public PositionRequirement createFromResume(Long projectId) {
        PositionRequirement existing = getByProjectId(projectId);
        // 手动填写的职位需求不被自动覆盖；简历来源的可刷新
        if (existing != null && !"RESUME".equals(existing.getSource())) {
            return existing;
        }

        Resume resume = resumeService.getLatestByProjectId(projectId);
        if (resume == null || resume.getAnalysisResult() == null || resume.getAnalysisResult().isBlank()) {
            throw new BusinessException("请先上传并分析简历");
        }

        PositionRequirement req = existing != null ? existing : new PositionRequirement();
        req.setProjectId(projectId);
        req.setSource("RESUME");
        try {
            JsonNode root = OBJECT_MAPPER.readTree(resume.getAnalysisResult());
            req.setJobTitle(extractText(root, "suggestedPosition"));
            req.setSeniority(extractText(root, "suggestedSeniority"));
            req.setCompanyType(extractText(root, "companyType"));

            // 经验年限：数字 → "X年"
            JsonNode expNode = root.get("experienceYears");
            if (expNode != null && expNode.isNumber() && expNode.asInt() > 0) {
                req.setExperience(expNode.asInt() + "年");
            }

            // 技术栈：数组 → JSON 字符串（前端解析为标签）
            JsonNode techStackNode = root.get("techStack");
            if (techStackNode != null && techStackNode.isArray() && !techStackNode.isEmpty()) {
                List<String> techs = new ArrayList<>();
                techStackNode.forEach(n -> {
                    if (n.isTextual() && !n.asText().isBlank()) {
                        techs.add(n.asText().trim());
                    }
                });
                if (!techs.isEmpty()) {
                    req.setTechStack(OBJECT_MAPPER.writeValueAsString(techs));
                }
            }

            // 职位描述：由当前职位 + 教育背景 + 项目经历拼接
            req.setJobDescription(buildDescription(root));
        } catch (Exception e) {
            throw new BusinessException("简历分析结果解析失败: " + e.getMessage());
        }
        if (req.getId() == null) {
            positionRequirementRepository.insert(req);
        } else {
            positionRequirementRepository.updateById(req);
        }
        return req;
    }

    /** 由简历分析拼装职位描述文本，无可用信息时返回 null */
    private String buildDescription(JsonNode root) {
        StringBuilder sb = new StringBuilder();
        String current = extractText(root, "currentPosition");
        String education = extractText(root, "education");
        if (current != null && !current.isBlank()) {
            sb.append("当前职位：").append(current);
        }
        if (education != null && !education.isBlank()) {
            if (sb.length() > 0) sb.append("；");
            sb.append("教育背景：").append(education);
        }
        JsonNode projects = root.get("projects");
        if (projects != null && projects.isArray() && !projects.isEmpty()) {
            List<String> list = new ArrayList<>();
            projects.forEach(n -> {
                String t = n.isTextual() ? n.asText().trim() : n.toString().trim();
                if (!t.isBlank()) list.add(t);
            });
            if (!list.isEmpty()) {
                if (sb.length() > 0) sb.append("；");
                sb.append("项目经历：").append(String.join("；", list));
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /** 提取节点文本，字段缺失或为非文本值时返回 null */
    private String extractText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }
}
