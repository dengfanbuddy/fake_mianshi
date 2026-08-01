package com.fakemianshi.service;

import com.fakemianshi.entity.InterviewerPersona;

import java.util.List;

/**
 * 面试官风格服务。
 */
public interface PersonaService {

    /** 全部面试官风格 */
    List<InterviewerPersona> findAll();

    /** 按 id 查询，不存在抛 ResourceNotFoundException */
    InterviewerPersona findById(Long id);

    /** 创建自定义风格：name 必填，isPreset 强制为 false */
    InterviewerPersona create(InterviewerPersona persona);

    /** 更新自定义风格：仅允许修改 name/description/styleConfig，预设不可改 */
    InterviewerPersona update(Long id, InterviewerPersona persona);

    /** 删除自定义风格，预设不可删 */
    void delete(Long id);
}
