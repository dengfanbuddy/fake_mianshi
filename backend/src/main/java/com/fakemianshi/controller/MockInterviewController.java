package com.fakemianshi.controller;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.dto.ApiResponse;
import com.fakemianshi.dto.MockInterviewRespondRequest;
import com.fakemianshi.dto.MockInterviewRespondResponse;
import com.fakemianshi.dto.MockInterviewStartRequest;
import com.fakemianshi.dto.MockInterviewStartResponse;
import com.fakemianshi.entity.MockInterviewMessage;
import com.fakemianshi.service.MockInterviewService;
import com.fakemianshi.util.AudioStorageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 模拟面试接口。
 *
 * <p>注意：服务器 context-path 为 /api，因此这里的 /mock-interview 实际暴露为 /api/mock-interview。
 */
@RestController
@RequestMapping("/mock-interview")
@RequiredArgsConstructor
public class MockInterviewController {

    private final MockInterviewService mockInterviewService;
    private final AudioStorageUtil audioStorageUtil;

    /** 开始模拟面试：创建会话、生成开场白与大纲 */
    @PostMapping("/start/{projectId}")
    public ApiResponse<MockInterviewStartResponse> start(@PathVariable Long projectId,
                                                         @RequestBody(required = false) MockInterviewStartRequest req) {
        return ApiResponse.success(mockInterviewService.start(projectId, req));
    }

    /** 候选人回复一轮，AI 面试官实时生成下一问/追问 */
    @PostMapping("/respond/{sessionId}")
    public ApiResponse<MockInterviewRespondResponse> respond(@PathVariable Long sessionId,
                                                             @RequestBody(required = false) MockInterviewRespondRequest req) {
        return ApiResponse.success(mockInterviewService.respond(sessionId, req));
    }

    /** 主动收尾面试：生成总结并将会话置为 COMPLETED */
    @PostMapping("/conclude/{sessionId}")
    public ApiResponse<MockInterviewMessage> conclude(@PathVariable Long sessionId) {
        return ApiResponse.success(mockInterviewService.concludeInterview(sessionId));
    }

    /** 获取某会话全部消息（按时间排序） */
    @GetMapping("/messages/{sessionId}")
    public ApiResponse<List<MockInterviewMessage>> messages(@PathVariable Long sessionId) {
        return ApiResponse.success(mockInterviewService.getMessages(sessionId));
    }

    /** 中途切换面试官人设 */
    @PutMapping("/switch-persona/{sessionId}")
    public ApiResponse<Void> switchPersona(@PathVariable Long sessionId,
                                           @RequestBody SwitchPersonaRequest req) {
        mockInterviewService.switchPersona(sessionId, req.newPersonaId());
        return ApiResponse.success(null);
    }

    /** 上传候选人录音（wav），保存后返回可回放的相对路径（供 respond 写入消息 audioPath） */
    @PostMapping("/{sessionId}/audio")
    public ApiResponse<String> uploadAudio(@PathVariable Long sessionId,
                                           @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("录音文件为空");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("录音读取失败: " + e.getMessage(), e);
        }
        String absolutePath = audioStorageUtil.saveAudio(data, sessionId, "candidate");
        return ApiResponse.success(audioStorageUtil.toPlayablePath(absolutePath));
    }

    /** 切换人设请求体 */
    record SwitchPersonaRequest(Long newPersonaId) {}
}
