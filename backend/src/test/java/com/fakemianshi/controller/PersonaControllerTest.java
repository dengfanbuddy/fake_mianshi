package com.fakemianshi.controller;

import com.fakemianshi.entity.InterviewerPersona;
import com.fakemianshi.repository.InterviewerPersonaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 面试官风格管理集成测试。
 *
 * <p>测试与其他测试共用 SQLite 数据库：@Transactional 保证各用例数据回滚；
 * PersonaSeeder 仅在表为空时插入预设，因此列表断言使用 size >= 5。
 */
@SpringBootTest(properties = "app.upload-dir=target/test-uploads")
@AutoConfigureMockMvc
@Transactional
class PersonaControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InterviewerPersonaRepository personaRepository;

    @Test
    void list_shouldContainPresets_whenSeeded() throws Exception {
        mockMvc.perform(get("/persona"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.data[0].name").exists());
    }

    @Test
    void create_shouldCreateCustomStyle() throws Exception {
        mockMvc.perform(post("/persona")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "自定义风格", "description": "我的自定义风格",
                                 "styleConfig": "{\\"tone\\":\\"warm\\",\\"speed\\":\\"slow\\"}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("自定义风格"))
                .andExpect(jsonPath("$.data.isPreset").value(false));
    }

    @Test
    void update_shouldModifyCustomStyle() throws Exception {
        Long id = createCustomPersona();

        mockMvc.perform(put("/persona/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "改后风格", "description": "更新后的描述",
                                 "styleConfig": "{\\"tone\\":\\"fast\\"}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("改后风格"))
                .andExpect(jsonPath("$.data.description").value("更新后的描述"));

        mockMvc.perform(get("/persona/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改后风格"));
    }

    @Test
    void delete_shouldRemoveCustomStyle() throws Exception {
        Long id = createCustomPersona();

        mockMvc.perform(delete("/persona/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/persona/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void updatePreset_shouldBeRejected() throws Exception {
        Long presetId = firstPresetId();

        mockMvc.perform(put("/persona/{id}", presetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "篡改预设"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("预设风格不可修改"));
    }

    @Test
    void deletePreset_shouldBeRejected() throws Exception {
        Long presetId = firstPresetId();

        mockMvc.perform(delete("/persona/{id}", presetId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("预设风格不可删除"));
    }

    @Test
    void create_withoutName_shouldBeRejected() throws Exception {
        mockMvc.perform(post("/persona")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "没有名字的风格"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("风格名称不能为空"));
    }

    private Long createCustomPersona() throws Exception {
        String resp = mockMvc.perform(post("/persona")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "临时风格", "description": "临时描述"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private Long firstPresetId() {
        List<InterviewerPersona> presets = personaRepository.findByIsPreset(true);
        if (presets.isEmpty()) {
            throw new IllegalStateException("预设风格种子数据未加载");
        }
        return presets.get(0).getId();
    }
}
