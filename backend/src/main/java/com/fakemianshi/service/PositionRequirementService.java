package com.fakemianshi.service;

import com.fakemianshi.entity.PositionRequirement;

/**
 * 职位需求服务。
 */
public interface PositionRequirementService {

    /** 按项目查询职位需求，无则返回 null */
    PositionRequirement getByProjectId(Long projectId);

    /** 有则更新、无则新建，projectId 以路径参数为准 */
    PositionRequirement saveOrUpdate(Long projectId, PositionRequirement req);

    /** 从最新简历分析结果中提取岗位与职级生成职位需求（不覆盖已存在的） */
    PositionRequirement createFromResume(Long projectId);
}
