import io

def patch(path, pairs):
    src = io.open(path, encoding='utf-8').read()
    orig = src
    for old, new in pairs:
        if old not in src:
            print('  [WARN] ' + path + ': ' + old[:60].replace('\n', '\\n'))
            continue
        src = src.replace(old, new, 1)
    if src != orig:
        io.open(path, 'w', encoding='utf-8', newline='').write(src)
        print('patched: ' + path)
    else:
        print('unchanged: ' + path)

# ============ AnalysisServiceImpl ============
p = 'src/main/java/com/fakemianshi/service/impl/AnalysisServiceImpl.java'
src = io.open(p, encoding='utf-8').read()

# 1. 注入 PromptTemplateService + InterviewProjectRepository
old = '''    private final HistoricalAnalysisRepository historicalAnalysisRepository;
    private final LlmService llmService;'''
new = '''    private final HistoricalAnalysisRepository historicalAnalysisRepository;
    private final LlmService llmService;
    private final com.fakemianshi.service.PromptTemplateService promptTemplateService;
    private final com.fakemianshi.repository.InterviewProjectRepository interviewProjectRepository;'''
if old in src:
    src = src.replace(old, new, 1)
    print('analysis: injection ok')
else:
    print('[WARN] analysis injection anchor')

# 2. analyzeSessionInternal 的 LLM 调用改模板
old = '''        String type = session.getType();
        JsonNode analysisNode;
        if (TYPE_WRITTEN.equals(type)) {
            analysisNode = onDelta == null
                    ? callLlm(WRITTEN_SYSTEM_PROMPT, buildWrittenUserPrompt(sessionId))
                    : callLlmStream(WRITTEN_SYSTEM_PROMPT, buildWrittenUserPrompt(sessionId), onDelta, onReasoning);
        } else if (TYPE_MOCK.equals(type)) {
            analysisNode = onDelta == null
                    ? callLlm(MOCK_SYSTEM_PROMPT, buildMockUserPrompt(sessionId))
                    : callLlmStream(MOCK_SYSTEM_PROMPT, buildMockUserPrompt(sessionId), onDelta, onReasoning);
        } else {
            throw new BusinessException("不支持的会话类型: " + type);
        }'''
new = '''        String type = session.getType();
        JsonNode analysisNode;
        if (TYPE_WRITTEN.equals(type)) {
            String systemPrompt = promptTemplateService.getTemplate(
                    resolveOccupation(session.getProjectId()),
                    com.fakemianshi.service.PromptTemplateService.Scene.WRITTEN_ANALYSIS.name(),
                    resolvePosition(session.getProjectId()));
            analysisNode = onDelta == null
                    ? callLlm(systemPrompt, buildWrittenUserPrompt(sessionId))
                    : callLlmStream(systemPrompt, buildWrittenUserPrompt(sessionId), onDelta, onReasoning);
        } else if (TYPE_MOCK.equals(type)) {
            String systemPrompt = promptTemplateService.getTemplate(
                    resolveOccupation(session.getProjectId()),
                    com.fakemianshi.service.PromptTemplateService.Scene.MOCK_ANALYSIS.name(),
                    resolvePosition(session.getProjectId()));
            analysisNode = onDelta == null
                    ? callLlm(systemPrompt, buildMockUserPrompt(sessionId))
                    : callLlmStream(systemPrompt, buildMockUserPrompt(sessionId), onDelta, onReasoning);
        } else {
            throw new BusinessException("不支持的会话类型: " + type);
        }'''
if old in src:
    src = src.replace(old, new, 1)
    print('analysis: session prompt ok')
else:
    print('[WARN] analysis session prompt anchor')

# 3. analyzeHistory 改模板
old = '''        JsonNode node = callLlm(HISTORY_SYSTEM_PROMPT, buildHistoryUserPrompt(projectId, analyses));'''
new = '''        String systemPrompt = promptTemplateService.getTemplate(
                resolveOccupation(projectId),
                com.fakemianshi.service.PromptTemplateService.Scene.HISTORY_ANALYSIS.name(),
                resolvePosition(projectId));
        JsonNode node = callLlm(systemPrompt, buildHistoryUserPrompt(projectId, analyses));'''
if old in src:
    src = src.replace(old, new, 1)
    print('analysis: history prompt ok')
else:
    print('[WARN] analysis history prompt anchor')

# 4. 加 resolveOccupation / resolvePosition 方法（放在 analyzeQuestions 前）
old = '''    @Override
    @Transactional(readOnly = true)
    public List<QuestionAnalysis> analyzeQuestions(Long sessionId) {'''
new = '''    /** 项目目标岗位（职业）；无则 null（走 default 模板） */
    private String resolveOccupation(Long projectId) {
        if (projectId == null) {
            return null;
        }
        com.fakemianshi.entity.InterviewProject project = interviewProjectRepository.selectById(projectId);
        return project == null ? null : project.getTargetPosition();
    }

    /** 项目目标岗位文本（用于 {position} 占位替换） */
    private String resolvePosition(Long projectId) {
        String occupation = resolveOccupation(projectId);
        return occupation == null ? "" : occupation;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionAnalysis> analyzeQuestions(Long sessionId) {'''
if old in src:
    src = src.replace(old, new, 1)
    print('analysis: helpers ok')
else:
    print('[WARN] analysis helpers anchor')

io.open(p, 'w', encoding='utf-8', newline='').write(src)

# ============ QuestionGenerationServiceImpl ============
p = 'src/main/java/com/fakemianshi/service/impl/QuestionGenerationServiceImpl.java'
src = io.open(p, encoding='utf-8').read()

# 1. 注入
old = '''    private final InterviewerPersonaRepository interviewerPersonaRepository;'''
new = '''    private final InterviewerPersonaRepository interviewerPersonaRepository;
    private final com.fakemianshi.service.PromptTemplateService promptTemplateService;
    private final com.fakemianshi.repository.InterviewProjectRepository interviewProjectRepository;'''
if old in src:
    src = src.replace(old, new, 1)
    print('qg: injection ok')
else:
    print('[WARN] qg injection anchor')

# 2. buildWrittenTestPrompt 签名 + 模板
old = '''    private String buildWrittenTestPrompt(int questionCount) {'''
new = '''    private String buildWrittenTestPrompt(Long projectId, int questionCount) {
        return promptTemplateService.getTemplate(
                resolveOccupation(projectId),
                com.fakemianshi.service.PromptTemplateService.Scene.WRITTEN_QUESTION.name(),
                resolvePosition(projectId));
    }

    /* 预留：原静态出题提示词已迁移至 prompt_template 默认模板 */
    private String buildWrittenTestPromptLegacy(int questionCount) {'''
if old in src:
    src = src.replace(old, new, 1)
    print('qg: buildWrittenTestPrompt ok')
else:
    print('[WARN] qg buildWrittenTestPrompt anchor')

# 3. 调用处传 projectId
src = src.replace('String systemPrompt = buildWrittenTestPrompt(questionCount);',
                  'String systemPrompt = buildWrittenTestPrompt(projectId, questionCount);')
print('qg: call sites replaced (count=%d)' % src.count('buildWrittenTestPrompt(projectId, questionCount)'))

# 4. 面试大纲 prompt 改模板（找 generateMockInterviewOutline 内的 system prompt 构造）
old = '''    public String generateMockInterviewOutline(Long projectId) {'''
new = '''    public String generateMockInterviewOutline(Long projectId) {
        String systemPrompt = promptTemplateService.getTemplate(
                resolveOccupation(projectId),
                com.fakemianshi.service.PromptTemplateService.Scene.MOCK_OUTLINE.name(),
                resolvePosition(projectId));
        return generateMockInterviewOutline(projectId, systemPrompt);
    }

    /** 带外部系统提示的面试大纲生成（保留旧实现逻辑） */
    public String generateMockInterviewOutline(Long projectId, String systemPrompt) {'''
if old in src:
    src = src.replace(old, new, 1)
    print('qg: outline ok')
else:
    print('[WARN] qg outline anchor')

# 5. 加 resolve helpers（放在 buildWrittenTestPromptLegacy 前）
old = '''    /* 预留：原静态出题提示词已迁移至 prompt_template 默认模板 */'''
new = '''    /** 项目目标岗位（职业）；无则 null（走 default 模板） */
    private String resolveOccupation(Long projectId) {
        if (projectId == null) {
            return null;
        }
        com.fakemianshi.entity.InterviewProject project = interviewProjectRepository.selectById(projectId);
        return project == null ? null : project.getTargetPosition();
    }

    /** 项目目标岗位文本（用于 {position} 占位替换） */
    private String resolvePosition(Long projectId) {
        String occupation = resolveOccupation(projectId);
        return occupation == null ? "" : occupation;
    }

    /* 预留：原静态出题提示词已迁移至 prompt_template 默认模板 */'''
if old in src:
    src = src.replace(old, new, 1)
    print('qg: helpers ok')
else:
    print('[WARN] qg helpers anchor')

io.open(p, 'w', encoding='utf-8', newline='').write(src)
print('done')
