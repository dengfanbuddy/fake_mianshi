package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.fakemianshi.entity.AIModelConfig;

import java.util.Optional;

/**
 * AI 模型配置 Repository。
 */
@Mapper
public interface AIModelConfigRepository extends BaseMapper<AIModelConfig> {

    /** 查询当前启用的模型配置 */
    @Select("SELECT * FROM ai_model_config WHERE is_active = 1 LIMIT 1")
    Optional<AIModelConfig> findByIsActiveTrue();
}
