package com.fakemianshi.controller;

import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.dto.WeaknessConsolidateResult;
import com.fakemianshi.entity.WeaknessTag;
import com.fakemianshi.service.WeaknessTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 薄弱知识点标签接口。
 *
 * <p>注意：服务器 context-path 为 /api，因此这里的 /weakness-tag 实际暴露为 /api/weakness-tag。
 */
@RestController
@RequestMapping("/weakness-tag")
@RequiredArgsConstructor
public class WeaknessTagController {

    private final WeaknessTagService weaknessTagService;

    /** 查询某项目下的全部弱点标签（同义写法已归并，别名在 aliasList 里） */
    @GetMapping("/project/{projectId}")
    public ApiResponse<List<WeaknessTag>> listByProject(@PathVariable Long projectId) {
        return ApiResponse.success(weaknessTagService.listByProject(projectId));
    }

    /**
     * 手动归并某项目下的同义标签（幂等）。
     *
     * <p>启动时已经自动跑过一次，这个接口留给「刚分析完想立刻看到干净列表」的场景。
     */
    @PostMapping("/project/{projectId}/consolidate")
    public ApiResponse<WeaknessConsolidateResult> consolidate(@PathVariable Long projectId) {
        return ApiResponse.success(weaknessTagService.consolidate(projectId));
    }
}
