package com.fakemianshi.controller;

import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.dto.PromptTemplateDTO;
import com.fakemianshi.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 面试提示词模板管理接口。
 */
@RestController
@RequestMapping("/prompt-template")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    /** 查询模板列表（occupation 可选；缺省返回全部） */
    @GetMapping
    public ApiResponse<List<PromptTemplateDTO>> list(@RequestParam(required = false) String occupation) {
        return ApiResponse.success(promptTemplateService.listTemplates(occupation));
    }

    /** 已配置职业列表 */
    @GetMapping("/occupations")
    public ApiResponse<List<String>> occupations() {
        return ApiResponse.success(promptTemplateService.listOccupations());
    }

    /** 保存/编辑模板 */
    @PutMapping
    public ApiResponse<Void> save(@RequestBody Map<String, String> body) {
        String occupation = body.getOrDefault("occupation", PromptTemplateService.DEFAULT_OCCUPATION);
        String scene = body.get("scene");
        String content = body.getOrDefault("content", "");
        promptTemplateService.saveTemplate(occupation, scene, content);
        return ApiResponse.success("已保存", null);
    }

    /** AI 针对职业重新生成某场景模板并保存 */
    @PostMapping("/regenerate")
    public ApiResponse<PromptTemplateDTO> regenerate(@RequestBody Map<String, String> body) {
        String occupation = body.getOrDefault("occupation", PromptTemplateService.DEFAULT_OCCUPATION);
        String scene = body.get("scene");
        promptTemplateService.regenerateByAI(occupation, scene);
        List<PromptTemplateDTO> list = promptTemplateService.listTemplates(occupation);
        return ApiResponse.success(list.stream()
                .filter(t -> t.getScene().equalsIgnoreCase(scene))
                .findFirst().orElse(null));
    }

    /** 检测/补齐某职业的提示词模板（新职业时 AI 生成全部场景并保存），返回新生成的场景列表 */
    @PostMapping("/ensure/{occupation}")
    public ApiResponse<Map<String, Object>> ensure(@PathVariable String occupation) {
        List<String> generated = promptTemplateService.ensureOccupationTemplates(occupation);
        return ApiResponse.success(Map.of(
                "occupation", occupation,
                "generatedScenes", generated,
                "isNew", !generated.isEmpty()));
    }
}
