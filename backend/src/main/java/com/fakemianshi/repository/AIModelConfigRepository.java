package com.fakemianshi.repository;

import com.fakemianshi.entity.AIModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * AI 模型配置 Repository。
 */
public interface AIModelConfigRepository extends JpaRepository<AIModelConfig, Long> {

    /** 查询当前启用的模型配置 */
    Optional<AIModelConfig> findByIsActiveTrue();
}
