import io

p = 'src/main/java/com/fakemianshi/service/impl/PromptTemplateServiceImpl.java'
src = io.open(p, encoding='utf-8').read()

old = '''        String sysPrompt = """
                你是 AI 提示词工程师，擅长为不同职业定制面试系统的 AI 提示词。
                请参考下面的通用提示词模板，为「%s」职业改写一份专属提示词。
                要求：
                1. 保持整体结构和输出格式（JSON 结构、字段、评分规则）完全不变；
                2. 把"岗位相关"的具体内容（题目场景、技术/技能点示例、知识盲区方向、考察维度）替换为该职业特有的；
                3. 语气保持专业面试官风格；不要输出任何额外解释，直接输出改写后的提示词全文。
                """.formatted(occ);

        String userPrompt = "【通用提示词模板】\\n" + reference;
        com.fakemianshi.dto.LlmResponse resp = llmService.chat(sysPrompt, userPrompt,
                LlmServiceImpl.LONG_TASK_MAX_TOKENS);
        String generated = resp.getContent() == null ? "" : resp.getContent().trim();
        if (generated.isBlank()) {
            throw new BusinessException("AI 生成提示词为空，请重试");
        }
        saveTemplate(occ, sceneEnum.name(), generated);
        return generated;'''
new = '''        String sysPrompt = """
                你是 AI 提示词工程师，擅长为不同职业定制面试系统的 AI 提示词。
                请参考下面的通用提示词模板，为「%s」职业改写一份专属提示词。

                重要理解：
                - 你要输出的是【提示词指令文本】（即系统提示词正文）：以"你是..."开头，
                  包含角色设定、行为要求、输出格式说明的一段指令。
                - 通用模板中"请严格返回如下 JSON 结构"之后的大括号/方括号内容是【给 AI 的输出格式要求】，
                  你要保留这段格式说明（只调整其中与岗位相关的内容），而不是把它当作输出样例照抄。
                - 严禁输出：JSON 数据本身、实际题目/实际分析结果、示例输出内容、任何以 { 或 [ 开头的数据文本。

                改写要求：
                1. 保持整体结构和输出格式要求（JSON 结构、字段、评分规则）不变；
                2. 把"岗位相关"的具体内容（题目场景、技术/技能点示例、知识盲区方向、考察维度）替换为该职业特有的；
                3. 语气保持专业面试官风格；直接输出改写后的提示词全文，不要任何额外解释。
                """.formatted(occ);

        String userPrompt = "【通用提示词模板】\\n" + reference;
        String generated = generateTemplateWithRetry(sysPrompt, userPrompt);
        saveTemplate(occ, sceneEnum.name(), generated);
        return generated;'''
if old in src:
    src = src.replace(old, new, 1)
    print('regenerate prompt patched')
else:
    print('[WARN] regenerate anchor not found')

old2 = '''    @Override
    @Transactional(readOnly = true)
    public List<PromptTemplateDTO> listTemplates(String occupation) {'''
new2 = '''    /** 生成模板并校验：输出若为 JSON 数据（以 {/[ 开头）说明模型误把示例当输出，重试一次 */
    private String generateTemplateWithRetry(String sysPrompt, String userPrompt) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            com.fakemianshi.dto.LlmResponse resp = llmService.chat(sysPrompt, userPrompt,
                    LlmServiceImpl.LONG_TASK_MAX_TOKENS);
            String generated = resp.getContent() == null ? "" : resp.getContent().trim();
            if (generated.isBlank()) {
                throw new BusinessException("AI 生成提示词为空，请重试");
            }
            if (generated.startsWith("{") || generated.startsWith("[")) {
                if (attempt == 1) {
                    // 重试：强调输出的是指令文本而非数据
                    String retry = sysPrompt + "\\n\\n【重要】上一次输出是 JSON 数据，不是提示词。请输出以\\"你是...\\"开头的提示词指令文本。";
                    resp = llmService.chat(retry, userPrompt, LlmServiceImpl.LONG_TASK_MAX_TOKENS);
                    generated = resp.getContent() == null ? "" : resp.getContent().trim();
                    if (!generated.isBlank() && !generated.startsWith("{") && !generated.startsWith("[")) {
                        return generated;
                    }
                }
                throw new BusinessException("AI 生成结果不是提示词文本（疑似输出了示例数据），请重试");
            }
            return generated;
        }
        throw new BusinessException("AI 生成提示词失败，请重试");
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptTemplateDTO> listTemplates(String occupation) {'''
if old2 in src:
    src = src.replace(old2, new2, 1)
    print('retry helper added')
else:
    print('[WARN] listTemplates anchor not found')

io.open(p, 'w', encoding='utf-8', newline='').write(src)
print('done')
