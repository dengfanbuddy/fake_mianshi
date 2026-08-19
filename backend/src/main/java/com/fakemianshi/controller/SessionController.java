package com.fakemianshi.controller;

import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.entity.InterviewSession;
import com.fakemianshi.repository.InterviewProjectRepository;
import com.fakemianshi.repository.InterviewSessionRepository;
import com.fakemianshi.service.impl.SessionCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.Map;

/**
 * 面试会话接口（列表查询）。
 *
 * <p>注意：服务器 context-path 为 /api，因此这里的 /session 实际暴露为 /api/session。
 */
@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
public class SessionController {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewProjectRepository projectRepository;
    private final SessionCleanupService sessionCleanupService;

    /** 查询某项目下的全部会话，按创建时间倒序 */
    @GetMapping("/project/{projectId}")
    public ApiResponse<List<InterviewSession>> listByProject(@PathVariable Long projectId) {
        return ApiResponse.success(
                sessionRepository.findByProjectIdOrderByCreatedAtDesc(projectId));
    }

    /** 查询单个会话信息（含 projectId，供报告页返回所属项目） */
    @GetMapping("/{sessionId}")
    public ApiResponse<InterviewSession> get(@PathVariable Long sessionId) {
        return ApiResponse.success(Optional.ofNullable(sessionRepository.selectById(sessionId))
                .orElseThrow(() -> new com.fakemianshi.config.ResourceNotFoundException("会话不存在: id=" + sessionId)));
    }

    /** 删除单个会话及其子数据、录音文件 */
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> delete(@PathVariable Long sessionId) {
        sessionCleanupService.deleteSession(sessionId);
        return ApiResponse.success(null);
    }

    /** 看板统计：项目数、会话数（笔试/面试） */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        List<InterviewSession> all = sessionRepository.selectList(null);
        long written = all.stream().filter(s -> "WRITTEN".equals(s.getType())).count();
        long mock = all.stream().filter(s -> "MOCK".equals(s.getType())).count();
        return ApiResponse.success(Map.of(
                "projectCount", projectRepository.selectCount(null),
                "sessionCount", all.size(),
                "writtenCount", written,
                "mockCount", mock));
    }
}
