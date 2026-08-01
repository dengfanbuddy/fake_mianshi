package com.fakemianshi.config;

import com.fakemianshi.entity.AIModelConfig;
import com.fakemianshi.repository.AIModelConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * AI 模型配置种子数据：启动时若表为空，插入一条 DeepSeek 默认配置（key 为空，用户在设置页填入）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelSeeder implements CommandLineRunner {

    private final AIModelConfigRepository configRepository;

    @Override
    public void run(String... args) {
        if (configRepository.count() > 0) {
            return;
        }
        AIModelConfig config = new AIModelConfig();
        config.setProvider("deepseek");
        config.setApiUrl("https://api.deepseek.com/v1/chat/completions");
        config.setApiKey("");
        config.setModelName("deepseek-chat");
        config.setIsActive(true);
        configRepository.save(config);
        log.info("AiModelSeeder: 已插入默认 DeepSeek 配置（请在设置页填写 API Key）");
    }
}
