package com.fakemianshi.service;

import com.fakemianshi.config.ResourceNotFoundException;
import com.fakemianshi.entity.AIModelConfig;
import com.fakemianshi.repository.AIModelConfigRepository;
import com.fakemianshi.service.impl.AIModelConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 模型配置服务单元测试。
 */
class AIModelConfigServiceTest {

    private AIModelConfigRepository repository;
    private AIModelConfigService service;

    @BeforeEach
    void setUp() {
        repository = mock(AIModelConfigRepository.class);
        service = new AIModelConfigServiceImpl(repository);
        // save 直接返回传入对象，模拟 JPA 行为
        when(repository.insert(any(AIModelConfig.class)))
                .thenAnswer(invocation -> 1);
    }

    @Test
    void save_shouldDefaultIsActiveToFalse_whenNull() {
        AIModelConfig config = new AIModelConfig();
        config.setIsActive(null);

        AIModelConfig saved = service.save(config);

        assertFalse(saved.getIsActive());
    }

    @Test
    void save_shouldDeactivateOthers_whenActivatingNewConfig() {
        // 已存在的 active 配置
        AIModelConfig existing = new AIModelConfig();
        existing.setId(1L);
        existing.setIsActive(true);

        // 新配置设为 active
        AIModelConfig newConfig = new AIModelConfig();
        newConfig.setId(2L);
        newConfig.setIsActive(true);

        when(repository.selectList(null)).thenReturn(new ArrayList<>(List.of(existing, newConfig)));

        AIModelConfig saved = service.save(newConfig);

        assertTrue(saved.getIsActive());
        assertFalse(existing.getIsActive(), "其他配置应被取消激活");
    }

    @Test
    void save_shouldNotDeactivateOthers_whenSavingInactiveConfig() {
        AIModelConfig existing = new AIModelConfig();
        existing.setId(1L);
        existing.setIsActive(true);

        AIModelConfig newConfig = new AIModelConfig();
        newConfig.setId(2L);
        newConfig.setIsActive(false);

        when(repository.selectList(null)).thenReturn(new ArrayList<>(List.of(existing, newConfig)));

        service.save(newConfig);

        assertTrue(existing.getIsActive(), "保存非 active 配置时不应影响其他配置");
    }

    @Test
    void setActive_shouldSwitchActiveToTarget() {
        AIModelConfig c1 = new AIModelConfig();
        c1.setId(1L);
        c1.setIsActive(true);

        AIModelConfig c2 = new AIModelConfig();
        c2.setId(2L);
        c2.setIsActive(false);

        when(repository.selectList(null)).thenReturn(new ArrayList<>(List.of(c1, c2)));
        when(repository.selectById(2L)).thenReturn(c2);

        AIModelConfig result = service.setActive(2L);

        assertTrue(result.getIsActive());
        assertTrue(c2.getIsActive());
        assertFalse(c1.getIsActive(), "原 active 配置应被取消");
    }

    @Test
    void setActive_shouldKeepTargetActive_whenAlreadyActive() {
        AIModelConfig c1 = new AIModelConfig();
        c1.setId(1L);
        c1.setIsActive(true);

        when(repository.selectList(null)).thenReturn(new ArrayList<>(List.of(c1)));
        when(repository.selectById(1L)).thenReturn(c1);

        AIModelConfig result = service.setActive(1L);

        assertTrue(result.getIsActive());
    }

    @Test
    void setActive_shouldThrow_whenIdNotFound() {
        when(repository.selectById(999L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.setActive(999L));
    }

    @Test
    void save_shouldPreserveCreatedAt_whenEditingExistingConfig() {
        // 已存在的配置，createdAt 由服务端管理
        AIModelConfig existing = new AIModelConfig();
        existing.setId(1L);
        existing.setIsActive(true);
        existing.setCreatedAt(java.time.LocalDateTime.of(2026, 8, 1, 10, 0));

        // 编辑请求：带 id，但不带 createdAt（前端不会传）
        AIModelConfig editRequest = new AIModelConfig();
        editRequest.setId(1L);
        editRequest.setProvider("deepseek");
        editRequest.setApiUrl("https://api.deepseek.com/v1/chat/completions");
        editRequest.setApiKey("sk-xxx");
        editRequest.setModelName("deepseek-chat");
        editRequest.setIsActive(true);

        when(repository.selectById(1L)).thenReturn(existing);
        when(repository.selectList(null)).thenReturn(new ArrayList<>(List.of(existing)));

        AIModelConfig saved = service.save(editRequest);

        assertEquals(existing.getCreatedAt(), saved.getCreatedAt(),
                "编辑时 createdAt 应保留原值，不能被 null 覆盖");
    }

    @Test
    void findAll_shouldReturnAllConfigs() {
        AIModelConfig c1 = new AIModelConfig();
        c1.setId(1L);
        when(repository.selectList(null)).thenReturn(List.of(c1));

        assertEquals(1, service.findAll().size());
    }
}
