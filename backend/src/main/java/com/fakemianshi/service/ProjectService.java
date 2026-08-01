package com.fakemianshi.service;

import com.fakemianshi.entity.InterviewProject;

import java.util.List;

/**
 * 面试项目服务。
 */
public interface ProjectService {

    /** 查询全部项目，按创建时间倒序 */
    List<InterviewProject> findAll();

    /** 按 id 查询，不存在抛 ResourceNotFoundException */
    InterviewProject findById(Long id);

    /** 新建项目（name 必填） */
    InterviewProject create(InterviewProject project);

    /** 更新项目（name/description），保留 createdAt */
    InterviewProject update(Long id, InterviewProject project);

    /** 删除项目及其全部关联数据（简历、会话、标签等） */
    void delete(Long id);
}
