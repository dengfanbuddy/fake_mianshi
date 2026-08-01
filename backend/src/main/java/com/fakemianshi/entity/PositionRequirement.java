package com.fakemianshi.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位要求：目标岗位的能力要求信息。
 */
@Data
@Entity
@Table(name = "position_requirement")
public class PositionRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属项目 ID */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 岗位名称 */
    @Column(name = "job_title", length = 200)
    private String jobTitle;

    /** 工作经验要求 */
    @Column(length = 500)
    private String experience;

    /** 职级要求 */
    @Column(length = 500)
    private String seniority;

    /** 公司类型要求 */
    @Column(name = "company_type", length = 500)
    private String companyType;

    /** 技术栈要求（JSON 数组） */
    @Column(name = "tech_stack", length = 5000)
    private String techStack;

    /** 岗位职责描述 */
    @Column(name = "job_description", length = 10000)
    private String jobDescription;

    /** 来源：RESUME（从简历提取）/ MANUAL（手动填写） */
    @Column(length = 20)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
