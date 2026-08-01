package com.fakemianshi.controller;

import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.entity.AIModelConfig;
import com.fakemianshi.service.AIModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 模型配置接口。
 *
 * <p>注意：服务器 context-path 为 /api，因此这里的 /ai-config 实际暴露为 /api/ai-config。
 *
 * <p>安全说明：GET 列表接口会返回含 apiKey 的完整对象，仅适用于本地个人项目（MVP）。
 * 后续如需对外提供服务，建议在 AIModelConfig 实体 apiKey 字段上添加 @JsonIgnore，
 * 并在 POST 保存时做"未传 apiKey 则保留原值"的处理。
 */
@RestController
@RequestMapping("/ai-config")
@RequiredArgsConstructor
public class AIModelConfigController {

    private final AIModelConfigService configService;

    /** 查询全部配置 */
    @GetMapping
    public ApiResponse<List<AIModelConfig>> list() {
        return ApiResponse.success(configService.findAll());
    }

    /** 新增或更新配置 */
    @PostMapping
    public ApiResponse<AIModelConfig> save(@RequestBody AIModelConfig config) {
        return ApiResponse.success(configService.save(config));
    }

    /** 将指定配置设为启用 */
    @PutMapping("/{id}/active")
    public ApiResponse<AIModelConfig> setActive(@PathVariable Long id) {
        return ApiResponse.success(configService.setActive(id));
    }
}
