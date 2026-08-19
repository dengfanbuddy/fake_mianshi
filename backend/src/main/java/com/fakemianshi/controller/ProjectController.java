package com.fakemianshi.controller;

import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.entity.InterviewProject;
import com.fakemianshi.service.ProjectService;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.PreDestroy;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 面试项目接口。
 *
 * <p>注意：服务器 context-path 为 /api，因此这里的 /project 实际暴露为 /api/project。
 */
@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final com.fakemianshi.service.PromptTemplateService promptTemplateService;

    /** 项目列表（按创建时间倒序） */
    @GetMapping
    public ApiResponse<List<InterviewProject>> list() {
        return ApiResponse.success(projectService.findAll());
    }

    /** 项目详情 */
    @GetMapping("/{id}")
    public ApiResponse<InterviewProject> get(@PathVariable Long id) {
        return ApiResponse.success(projectService.findById(id));
    }

    /** 新建项目 */
    @PostMapping
    public ApiResponse<InterviewProject> create(@RequestBody InterviewProject project) {
        InterviewProject saved = projectService.create(project);
        ensureTemplates(saved);
        return ApiResponse.success(saved);
    }

    /** 更新项目 */
    @PutMapping("/{id}")
    public ApiResponse<InterviewProject> update(@PathVariable Long id,
                                                @RequestBody InterviewProject project) {
        InterviewProject saved = projectService.update(id, project);
        ensureTemplates(saved);
        return ApiResponse.success(saved);
    }

    /** 异步线程池：新职业提示词生成不阻塞项目创建/更新（单线程串行，避免并发写库锁冲突） */
    private final java.util.concurrent.ExecutorService templateExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    @PreDestroy
    public void shutdown() {
        templateExecutor.shutdownNow();
    }

    /** 新职业检测：目标岗位没有提示词模板时，后台由 AI 生成全部场景并保存 */
    private void ensureTemplates(InterviewProject project) {
        String position = project.getTargetPosition();
        if (position == null || position.isBlank()) {
            return;
        }
        templateExecutor.execute(() -> {
            try {
                List<String> generated = promptTemplateService.ensureOccupationTemplates(position);
                if (!generated.isEmpty()) {
                    org.slf4j.LoggerFactory.getLogger(ProjectController.class)
                            .info("已为新职业生成提示词模板: position={}, scenes={}", position, generated);
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ProjectController.class)
                        .warn("新职业提示词生成失败: position={}, cause={}", position, e.getMessage());
            }
        });
    }

    /** 删除项目（级联删除关联数据） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
