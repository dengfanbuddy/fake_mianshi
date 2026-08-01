package com.fakemianshi.service.impl;

import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.entity.AIModelConfig;
import com.fakemianshi.repository.AIModelConfigRepository;
import com.fakemianshi.service.AIModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        return repository.findAll();
    }

    @Override
    @Transactional
    public AIModelConfig save(AIModelConfig config) {
        if (config.getIsActive() == null) {
            config.setIsActive(false);
        }
        // 编辑场景（带 id）：保留服务端管理的 createdAt，避免 JPA merge 将其覆盖为 null
        if (config.getId() != null) {
            repository.findById(config.getId()).ifPresent(existing ->
                    config.setCreatedAt(existing.getCreatedAt()));
        }
        if (Boolean.TRUE.equals(config.getIsActive())) {
            deactivateOthers(config.getId());
        }
        return repository.save(config);
    }

    @Override
    @Transactional
    public AIModelConfig setActive(Long id) {
        AIModelConfig target = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AI模型配置不存在: id=" + id));
        deactivateOthers(id);
        target.setIsActive(true);
        return repository.save(target);
    }

    /** 将除 selfId 之外所有 active 的配置设为 false */
    private void deactivateOthers(Long selfId) {
        for (AIModelConfig c : repository.findAll()) {
            boolean isSelf = selfId != null && selfId.equals(c.getId());
            if (!isSelf && Boolean.TRUE.equals(c.getIsActive())) {
                c.setIsActive(false);
                repository.save(c);
            }
        }
    }
}
