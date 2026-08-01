package com.fakemianshi.config;

import com.fakemianshi.entity.InterviewerPersona;
import com.fakemianshi.repository.InterviewerPersonaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 预设面试官风格种子数据：启动时若 interviewer_persona 表为空，批量插入 5 种预设风格。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonaSeeder implements CommandLineRunner {

    private final InterviewerPersonaRepository personaRepository;

    @Override
    public void run(String... args) {
        if (personaRepository.selectCount(null) > 0) {
            return;
        }
        for (InterviewerPersona p : List.of(
                preset("技术深挖型",
                        "不断追问底层原理和实现细节，刨根问底，要求解释到源码级别",
                        "{\"tone\":\"serious\",\"speed\":\"medium\",\"aggressiveness\":0.8,\"followupStrategy\":\"deep_dive\"}"),
                preset("压力面试型",
                        "质疑回答，施加压力，指出不足，考察抗压能力",
                        "{\"tone\":\"stern\",\"speed\":\"fast\",\"aggressiveness\":0.9,\"followupStrategy\":\"challenge\"}"),
                preset("温和引导型",
                        "友好亲切，答不上来会给提示，换角度引导",
                        "{\"tone\":\"warm\",\"speed\":\"slow\",\"aggressiveness\":0.3,\"followupStrategy\":\"guide\"}"),
                preset("项目实战型",
                        "围绕简历项目展开，问架构决策、踩坑经验、技术选型理由",
                        "{\"tone\":\"professional\",\"speed\":\"medium\",\"aggressiveness\":0.5,\"followupStrategy\":\"project_drill\"}"),
                preset("八股文型",
                        "按知识点清单逐个问，标准化考察，不追问太深",
                        "{\"tone\":\"neutral\",\"speed\":\"medium\",\"aggressiveness\":0.4,\"followupStrategy\":\"checklist\"}")
        )) {
            personaRepository.insert(p);
        }
        log.info("PersonaSeeder: 已插入 5 种预设面试官风格");
    }

    private InterviewerPersona preset(String name, String description, String styleConfig) {
        InterviewerPersona persona = new InterviewerPersona();
        persona.setName(name);
        persona.setDescription(description);
        persona.setStyleConfig(styleConfig);
        persona.setIsPreset(true);
        return persona;
    }
}
