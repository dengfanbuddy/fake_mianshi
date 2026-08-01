package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟面试消息：模拟面试中的一轮对话消息。
 */
@Data
@Entity
@Table(name = "mock_interview_message")
public class MockInterviewMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属面试会话 ID */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 角色：INTERVIEWER（面试官）/ CANDIDATE（候选人） */
    @Column(length = 20)
    private String role;

    /** 消息内容 */
    @Column(length = 10000)
    private String content;

    /** 音频文件路径（若有） */
    @Column(name = "audio_path", length = 500)
    private String audioPath;

    /** 使用的面试官人设 ID */
    @Column(name = "persona_id")
    private Long personaId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
