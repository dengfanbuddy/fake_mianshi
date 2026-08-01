package com.fakemianshi.controller;

import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.entity.InterviewerPersona;
import com.fakemianshi.service.PersonaService;
import lombok.RequiredArgsConstructor;
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
 * 面试官风格接口。
 *
 * <p>注意：服务器 context-path 为 /api，因此这里的 /persona 实际暴露为 /api/persona。
 */
@RestController
@RequestMapping("/persona")
@RequiredArgsConstructor
public class PersonaController {

    private final PersonaService personaService;

    /** 面试官风格列表 */
    @GetMapping
    public ApiResponse<List<InterviewerPersona>> list() {
        return ApiResponse.success(personaService.findAll());
    }

    /** 查询单个风格 */
    @GetMapping("/{id}")
    public ApiResponse<InterviewerPersona> get(@PathVariable Long id) {
        return ApiResponse.success(personaService.findById(id));
    }

    /** 创建自定义风格 */
    @PostMapping
    public ApiResponse<InterviewerPersona> create(@RequestBody InterviewerPersona persona) {
        return ApiResponse.success(personaService.create(persona));
    }

    /** 更新自定义风格 */
    @PutMapping("/{id}")
    public ApiResponse<InterviewerPersona> update(@PathVariable Long id,
                                                  @RequestBody InterviewerPersona persona) {
        return ApiResponse.success(personaService.update(id, persona));
    }

    /** 删除自定义风格 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        personaService.delete(id);
        return ApiResponse.success(null);
    }
}
