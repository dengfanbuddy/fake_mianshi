package com.fakemianshi.controller;

import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.entity.WeaknessTag;
import com.fakemianshi.repository.WeaknessTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    private final WeaknessTagRepository weaknessTagRepository;

    /** 查询某项目下的全部弱点标签 */
    @GetMapping("/project/{projectId}")
    public ApiResponse<List<WeaknessTag>> listByProject(@PathVariable Long projectId) {
        return ApiResponse.success(weaknessTagRepository.findByProjectId(projectId));
    }
}
