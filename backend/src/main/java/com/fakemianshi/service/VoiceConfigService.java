package com.fakemianshi.service;

import com.fakemianshi.dto.VoiceConfigDTO;
import com.fakemianshi.entity.VoiceConfig;

/**
 * 腾讯云语音配置服务：页面保存配置、激活生效（打包分发后无需改环境变量）。
 */
public interface VoiceConfigService {

    /**
     * 保存语音配置：先停用旧配置，再写入新的启用配置（密钥留空表示保留原值）。
     */
    VoiceConfig saveConfig(VoiceConfigDTO dto);

    /**
     * 查询当前生效的语音配置（脱敏，不返回完整密钥）。
     */
    VoiceConfigDTO getActiveConfig();

    /**
     * 读取当前生效的语音配置实体；无数据库配置时回退到环境变量。
     */
    VoiceConfig resolveActive();

    /**
     * 当前生效的**识别热词**（system prompt 偏置）。
     *
     * <p>优先级：页面保存的值 &gt; 配置文件/环境变量里的默认值。页面留空表示"用内置默认"，
     * 而不是"不要热词"——内置表是实测有效的（不加时 "JVM" 会被识别成 "JYEM"）。
     */
    String effectiveAsrPrompt();
}
