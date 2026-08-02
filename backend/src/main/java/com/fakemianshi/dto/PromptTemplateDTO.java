package com.fakemianshi.dto;

import java.time.LocalDateTime;

/**
 * 提示词模板 DTO：管理页展示/编辑用。
 */
public class PromptTemplateDTO {

    private Long id;
    private String occupation;
    private String scene;
    private String sceneLabel;
    private String content;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getSceneLabel() {
        return sceneLabel;
    }

    public void setSceneLabel(String sceneLabel) {
        this.sceneLabel = sceneLabel;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
