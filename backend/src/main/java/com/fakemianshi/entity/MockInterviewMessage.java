package com.fakemianshi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟面试消息：模拟面试中的一轮对话消息。
 */
@Data
@TableName("mock_interview_message")
public class MockInterviewMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属面试会话 ID */
    @TableField("session_id")
    private Long sessionId;

    /** 角色：INTERVIEWER（面试官）/ CANDIDATE（候选人） */
    private String role;

    /** 消息内容 */
    private String content;

    /** 音频文件路径（若有） */
    @TableField("audio_path")
    private String audioPath;

    /** 使用的面试官人设 ID */
    @TableField("persona_id")
    private Long personaId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    }
