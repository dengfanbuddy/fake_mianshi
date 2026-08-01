package com.fakemianshi.service;

import com.fakemianshi.entity.AIModelConfig;

import java.util.List;

/**
 * AI 模型配置服务。
 */
public interface AIModelConfigService {

    /** 查询全部配置 */
    List<AIModelConfig> findAll();

    /**
     * 新增或更新配置。
     * 保存前：isActive 为 null 时默认设为 false；若设为 true，则自动取消其他所有配置的 active。
     */
    AIModelConfig save(AIModelConfig config);

    /** 将指定 id 的配置设为 active，并取消其他所有配置的 active */
    AIModelConfig setActive(Long id);
}
