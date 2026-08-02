package com.fakemianshi.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fakemianshi.entity.VoiceConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * 腾讯云语音配置 Repository。
 */
@Mapper
public interface VoiceConfigRepository extends BaseMapper<VoiceConfig> {

    /** 查询当前启用的语音配置 */
    @Select("SELECT * FROM voice_config WHERE is_active = 1 ORDER BY id DESC LIMIT 1")
    Optional<VoiceConfig> findByIsActiveTrue();

    /** 按提供商查询 */
    @Select("SELECT * FROM voice_config WHERE provider = #{provider} ORDER BY id DESC LIMIT 1")
    Optional<VoiceConfig> findByProvider(String provider);
}
