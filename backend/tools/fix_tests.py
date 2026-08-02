import io
import re

# AnalysisServiceTest
p = 'src/test/java/com/fakemianshi/service/AnalysisServiceTest.java'
src = io.open(p, encoding='utf-8').read()
old = '''        historicalAnalysisRepository = mock(HistoricalAnalysisRepository.class);
        llmService = mock(LlmService.class);

        service = new AnalysisServiceImpl(
                sessionRepository, writtenTestQuestionRepository, writtenTestAnswerRepository,
                mockInterviewMessageRepository, sessionAnalysisRepository, questionAnalysisRepository,
                weaknessTagRepository, historicalAnalysisRepository, llmService);'''
new = '''        historicalAnalysisRepository = mock(HistoricalAnalysisRepository.class);
        llmService = mock(LlmService.class);
        com.fakemianshi.service.PromptTemplateService promptTemplateService =
                mock(com.fakemianshi.service.PromptTemplateService.class);
        com.fakemianshi.repository.InterviewProjectRepository interviewProjectRepository =
                mock(com.fakemianshi.repository.InterviewProjectRepository.class);
        when(promptTemplateService.getTemplate(any(), any(), any())).thenReturn("你是面试官。请严格输出 JSON。");

        service = new AnalysisServiceImpl(
                sessionRepository, writtenTestQuestionRepository, writtenTestAnswerRepository,
                mockInterviewMessageRepository, sessionAnalysisRepository, questionAnalysisRepository,
                weaknessTagRepository, historicalAnalysisRepository, llmService,
                promptTemplateService, interviewProjectRepository);'''
if old in src:
    src = src.replace(old, new, 1)
    io.open(p, 'w', encoding='utf-8', newline='').write(src)
    print('AnalysisServiceTest patched')
else:
    print('[WARN] analysis test anchor')

# QuestionGenerationServiceTest
p = 'src/test/java/com/fakemianshi/service/QuestionGenerationServiceTest.java'
src = io.open(p, encoding='utf-8').read()
m = re.search(r'new QuestionGenerationServiceImpl\(([^;]*?)\);', src, re.S)
if m:
    args = m.group(1)
    print('qg test args head:', args.strip()[:100])
    src = src.replace(m.group(0), 'new QuestionGenerationServiceImpl(' + args + ',\n                promptTemplateService, interviewProjectRepository);')
    if 'interviewerPersonaRepository = mock' in src:
        src = src.replace('interviewerPersonaRepository = mock(InterviewerPersonaRepository.class);',
                          'interviewerPersonaRepository = mock(InterviewerPersonaRepository.class);\n        promptTemplateService = mock(com.fakemianshi.service.PromptTemplateService.class);\n        interviewProjectRepository = mock(com.fakemianshi.repository.InterviewProjectRepository.class);', 1)
    io.open(p, 'w', encoding='utf-8', newline='').write(src)
    print('QuestionGenerationServiceTest patched')
else:
    print('[WARN] qg test constructor not found')
print('done')
