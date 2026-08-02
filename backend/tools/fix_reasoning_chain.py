import io

def patch(path, pairs):
    src = io.open(path, encoding='utf-8').read()
    orig = src
    for old, new in pairs:
        if old not in src:
            print('  [WARN] ' + path + ': ' + old[:60])
            continue
        src = src.replace(old, new, 1)
    if src != orig:
        io.open(path, 'w', encoding='utf-8', newline='').write(src)
        print('patched: ' + path)

# QG 接口
patch('src/main/java/com/fakemianshi/service/QuestionGenerationService.java', [
    ('''    List<WrittenTestQuestion> generateWrittenTestQuestionsStream(
            Long sessionId, Long projectId, int questionCount,
            java.util.function.Consumer<String> onDelta);''',
     '''    List<WrittenTestQuestion> generateWrittenTestQuestionsStream(
            Long sessionId, Long projectId, int questionCount,
            java.util.function.Consumer<String> onDelta,
            java.util.function.Consumer<String> onReasoning);'''),
])

# QG 实现
patch('src/main/java/com/fakemianshi/service/impl/QuestionGenerationServiceImpl.java', [
    ('''    public List<WrittenTestQuestion> generateWrittenTestQuestionsStream(
            Long sessionId, Long projectId, int questionCount,
            java.util.function.Consumer<String> onDelta) {''',
     '''    public List<WrittenTestQuestion> generateWrittenTestQuestionsStream(
            Long sessionId, Long projectId, int questionCount,
            java.util.function.Consumer<String> onDelta,
            java.util.function.Consumer<String> onReasoning) {'''),
    ('''        String fullOutput = llmService.chatStream(systemPrompt, userPrompt,
                LlmServiceImpl.LONG_TASK_MAX_TOKENS, onDelta, null);''',
     '''        String fullOutput = llmService.chatStream(systemPrompt, userPrompt,
                LlmServiceImpl.LONG_TASK_MAX_TOKENS, onDelta, onReasoning);'''),
])

# WrittenTestService 接口
patch('src/main/java/com/fakemianshi/service/WrittenTestService.java', [
    ('''    WrittenTestStartResponse streamStart(Long projectId, WrittenTestStartRequest req,
                                         java.util.function.Consumer<String> onDelta);''',
     '''    WrittenTestStartResponse streamStart(Long projectId, WrittenTestStartRequest req,
                                         java.util.function.Consumer<String> onDelta,
                                         java.util.function.Consumer<String> onReasoning);'''),
])

# WrittenTestServiceImpl
patch('src/main/java/com/fakemianshi/service/impl/WrittenTestServiceImpl.java', [
    ('''    public WrittenTestStartResponse streamStart(Long projectId, WrittenTestStartRequest req,
                                                java.util.function.Consumer<String> onDelta) {''',
     '''    public WrittenTestStartResponse streamStart(Long projectId, WrittenTestStartRequest req,
                                                java.util.function.Consumer<String> onDelta,
                                                java.util.function.Consumer<String> onReasoning) {'''),
    ('''        List<WrittenTestQuestion> questions = questionGenerationService
                .generateWrittenTestQuestionsStream(session.getId(), projectId, questionCount, onDelta);''',
     '''        List<WrittenTestQuestion> questions = questionGenerationService
                .generateWrittenTestQuestionsStream(session.getId(), projectId, questionCount, onDelta, onReasoning);'''),
])

# WrittenTestController
patch('src/main/java/com/fakemianshi/controller/WrittenTestController.java', [
    ('''                WrittenTestStartResponse res = writtenTestService.streamStart(projectId, req, delta -> {
                    try {
                        emitter.send(SseEmitter.event().name("delta").data(delta));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE 推送中断", e);
                    }
                });''',
     '''                WrittenTestStartResponse res = writtenTestService.streamStart(projectId, req,
                        delta -> sendOrThrow(emitter, "delta", delta),
                        reasoning -> sendOrThrow(emitter, "reasoning", reasoning));'''),
    ('''        return emitter;
    }

    /** 提交笔试：判分并结束会话 */''',
     '''        return emitter;
    }

    /** SSE 推送工具：客户端断开时以 RuntimeException 中断生成任务 */
    private void sendOrThrow(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            throw new RuntimeException("SSE 推送中断", e);
        }
    }

    /** 提交笔试：判分并结束会话 */'''),
])

print('done')
