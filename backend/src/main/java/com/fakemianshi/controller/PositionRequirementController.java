package com.fakemianshi.controller;

import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.entity.PositionRequirement;
import com.fakemianshi.service.PositionRequirementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 职位需求接口。
 *
 * <p>注意：服务器 context-path 为 /api，因此这里的 /position 实际暴露为 /api/position。
 */
@RestController
@RequestMapping("/position")
@RequiredArgsConstructor
public class PositionRequirementController {

    private final PositionRequirementService positionRequirementService;

    /** 查询某项目的职位需求（无则返回 data=null） */
    @GetMapping("/{projectId}")
    public ApiResponse<PositionRequirement> get(@PathVariable Long projectId) {
        return ApiResponse.success(positionRequirementService.getByProjectId(projectId));
    }

    /** 保存或更新某项目的职位需求 */
    @PostMapping("/{projectId}")
    public ApiResponse<PositionRequirement> save(@PathVariable Long projectId,
                                                 @RequestBody PositionRequirement req) {
        return ApiResponse.success(positionRequirementService.saveOrUpdate(projectId, req));
    }

    /** 从最新简历分析结果生成职位需求 */
    @PostMapping("/{projectId}/from-resume")
    public ApiResponse<PositionRequirement> fromResume(@PathVariable Long projectId) {
        return ApiResponse.success(positionRequirementService.createFromResume(projectId));
    }
}
