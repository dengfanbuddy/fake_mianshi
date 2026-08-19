package com.fakemianshi.service.impl;

import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.entity.AIModelConfig;
import com.fakemianshi.repository.AIModelConfigRepository;
import com.fakemianshi.service.AIModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * AI 模型配置服务实现。
 *
 * <p>安全说明：MVP 阶段 GET 接口直接返回完整对象（含 apiKey），仅适用于本地个人项目。
 * 后续如需对外提供服务，建议在实体上对 apiKey 加 @JsonIgnore，并在 POST 保存时做
 * "未传 apiKey 则保留原值" 的逻辑。
 */
@Service
@RequiredArgsConstructor
public class AIModelConfigServiceImpl implements AIModelConfigService {

    private final AIModelConfigRepository repository;

    @Override
    public List<AIModelConfig> findAll() {
        List<AIModelConfig> list = repository.selectList(null);
        list.forEach(this::maskKey);
        return list;
    }

    @Override
    @Transactional
    public AIModelConfig save(AIModelConfig config) {
        if (config.getIsActive() == null) {
            config.setIsActive(false);
        }
        // 编辑场景（带 id）：保留服务端管理的 createdAt；密钥留空/脱敏值则保留原值
        if (config.getId() != null) {
            AIModelConfig existing = repository.selectById(config.getId());
            if (existing != null) {
                config.setCreatedAt(existing.getCreatedAt());
                config.setApiKey(resolveApiKey(config.getApiKey(), existing.getApiKey()));
            }
        }
        if (Boolean.TRUE.equals(config.getIsActive())) {
            deactivateOthers(config.getId());
        }
        if (config.getId() == null) {
            repository.insert(config);
        } else {
            repository.updateById(config);
        }
        return maskKey(config);
    }

    @Override
    @Transactional
    public AIModelConfig setActive(Long id) {
        AIModelConfig target = Optional.ofNullable(repository.selectById(id))
                .orElseThrow(() -> new ResourceNotFoundException("AI模型配置不存在: id=" + id));
        deactivateOthers(id);
        target.setIsActive(true);
        repository.updateById(target);
        return maskKey(target);
    }

    /** 入参为空或脱敏值（含 *）时保留原密钥，否则返回入参（trim 后） */
    private String resolveApiKey(String input, String original) {
        if (input == null || input.isBlank() || input.contains("*")) {
            return original;
        }
        return input.trim();
    }

    /** 脱敏返回：apiKey 只保留前 3 后 3 */
    private AIModelConfig maskKey(AIModelConfig config) {
        if (config != null && config.getApiKey() != null && !config.getApiKey().isBlank()) {
            config.setApiKey(mask(config.getApiKey()));
        }
        return config;
    }

    private String mask(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        if (s.length() <= 6) {
            return "******";
        }
        return s.substring(0, 3) + "****" + s.substring(s.length() - 3);
    }

    /** 将除 selfId 之外所有 active 的配置设为 false */
    private void deactivateOthers(Long selfId) {
        for (AIModelConfig c : repository.selectList(null)) {
            boolean isSelf = selfId != null && selfId.equals(c.getId());
            if (!isSelf && Boolean.TRUE.equals(c.getIsActive())) {
                c.setIsActive(false);
                repository.updateById(c);
            }
        }
    }
}
