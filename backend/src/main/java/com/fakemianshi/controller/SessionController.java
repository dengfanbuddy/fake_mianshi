package com.fakemianshi.controller;

import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.entity.InterviewSession;
import com.fakemianshi.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /** 查询某项目下的全部会话，按创建时间倒序 */
    @GetMapping("/project/{projectId}")
    public ApiResponse<List<InterviewSession>> listByProject(@PathVariable Long projectId) {
        return ApiResponse.success(
                sessionRepository.findByProjectIdOrderByCreatedAtDesc(projectId));
    }
}
