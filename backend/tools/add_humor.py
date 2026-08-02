import io

p = 'src/main/java/com/fakemianshi/service/impl/PromptTemplateServiceImpl.java'
src = io.open(p, encoding='utf-8').read()

# 通用幽默总结规则说明（插到三个分析模板的要求部分）
HUMOR_RULE = """            9. humorSummary：给候选人一句幽默化的总结（40 字以内，带点调侃味但善意、不攻击人格）：
               - 整体评分高（>=80）：用"谄媚捧脚"风格夸赞（如"大佬，收徒吗？这水平已经可以横着走了"）；
               - 整体评分低（<55）：用"接地气吐槽"风格善意调侃（如"菜的扣脚，回家种红薯去吧，下个周期再战"）；
               - 中等分数：鼓励中带幽默（如"比上不足比下有余，再练三套题就起飞"）。"""

# 1. WRITTEN_ANALYSIS：加 humorSummary 到 JSON 结构 + 规则
old1 = '''            {"overallScore":0-100,"overallLevel":"...","expectedSalaryRange":"...","strengths":["..."],"weaknesses":["..."],"knowledgeGaps":[{"point":"知识点","explanation":"答案要点"}],"personalitySummary":"...","characterTraits":["..."],"characterDefects":[{"defect":"缺陷","improvement":"改进方法"}],"improvementPlan":{"topics":[{"topic":"学习方向","action":"具体做法","example":"实例"}],"suggestions":["建议"]},"questionAnalyses":[{"questionContent":"...","answerContent":"...","category":"技术分类","difficulty":"难度","focusPoint":"考察方向","accuracy":0-10,"accuracyReason":"依据","depth":0-10,"depthReason":"依据","clarity":0-10,"clarityReason":"依据","fluency":0-10,"fluencyReason":"依据","tone":"","answerApproach":"回答思路","example":"优秀示例","improvementSuggestion":"..."}]}'''
new1 = '''            {"overallScore":0-100,"overallLevel":"...","expectedSalaryRange":"...","humorSummary":"幽默总结","strengths":["..."],"weaknesses":["..."],"knowledgeGaps":[{"point":"知识点","explanation":"答案要点"}],"personalitySummary":"...","characterTraits":["..."],"characterDefects":[{"defect":"缺陷","improvement":"改进方法"}],"improvementPlan":{"topics":[{"topic":"学习方向","action":"具体做法","example":"实例"}],"suggestions":["建议"]},"questionAnalyses":[{"questionContent":"...","answerContent":"...","category":"技术分类","difficulty":"难度","focusPoint":"考察方向","accuracy":0-10,"accuracyReason":"依据","depth":0-10,"depthReason":"依据","clarity":0-10,"clarityReason":"依据","fluency":0-10,"fluencyReason":"依据","tone":"","answerApproach":"回答思路","example":"优秀示例","improvementSuggestion":"..."}]}'''
if old1 in src:
    src = src.replace(old1, new1, 1)
    # 在 WRITTEN_ANALYSIS 的说明前加 humor 规则（插到"- 客观题 fluency 填 0"前）
    src = src.replace('''            说明：
            - questionAnalyses 每项对应一道笔试题，questionContent 请原样引用题目内容；''',
'''            说明：
''' + HUMOR_RULE + '''
            - questionAnalyses 每项对应一道笔试题，questionContent 请原样引用题目内容；''', 1)
    print('WRITTEN_ANALYSIS patched')
else:
    print('[WARN] written analysis anchor')

# 2. MOCK_ANALYSIS
old2 = '''            {"overallScore":0-100,"overallLevel":"...","expectedSalaryRange":"...","strengths":["..."],"weaknesses":["..."],"knowledgeGaps":[{"point":"知识点","explanation":"答案要点"}],"personalitySummary":"...","characterTraits":["..."],"characterDefects":[{"defect":"缺陷","improvement":"改进方法"}],"communicationEvaluation":"...","improvementPlan":{"topics":[{"topic":"学习方向","action":"具体做法","example":"实例"}],"suggestions":["建议"]},"questionAnalyses":[{"questionContent":"...","answerContent":"...","category":"技术分类","difficulty":"难度","focusPoint":"考察方向","accuracy":0-10,"accuracyReason":"依据","depth":0-10,"depthReason":"依据","clarity":0-10,"clarityReason":"依据","fluency":0-10,"fluencyReason":"依据","tone":"","answerApproach":"回答思路","example":"优秀示例","improvementSuggestion":"..."}]}'''
new2 = '''            {"overallScore":0-100,"overallLevel":"...","expectedSalaryRange":"...","humorSummary":"幽默总结","strengths":["..."],"weaknesses":["..."],"knowledgeGaps":[{"point":"知识点","explanation":"答案要点"}],"personalitySummary":"...","characterTraits":["..."],"characterDefects":[{"defect":"缺陷","improvement":"改进方法"}],"communicationEvaluation":"...","improvementPlan":{"topics":[{"topic":"学习方向","action":"具体做法","example":"实例"}],"suggestions":["建议"]},"questionAnalyses":[{"questionContent":"...","answerContent":"...","category":"技术分类","difficulty":"难度","focusPoint":"考察方向","accuracy":0-10,"accuracyReason":"依据","depth":0-10,"depthReason":"依据","clarity":0-10,"clarityReason":"依据","fluency":0-10,"fluencyReason":"依据","tone":"","answerApproach":"回答思路","example":"优秀示例","improvementSuggestion":"..."}]}'''
if old2 in src:
    src = src.replace(old2, new2, 1)
    src = src.replace('''            说明：
            - questionAnalyses 每项对应一轮问答，questionContent 请引用面试官的原问题；''',
'''            说明：
''' + HUMOR_RULE + '''
            - questionAnalyses 每项对应一轮问答，questionContent 请引用面试官的原问题；''', 1)
    print('MOCK_ANALYSIS patched')
else:
    print('[WARN] mock analysis anchor')

# 3. HISTORY_ANALYSIS
old3 = '''            {"trends":[{"dimension":"专业技能","trend":"上升/平稳/下降","detail":"说明"}],"recurringWeaknesses":["反复出现的弱点知识点"],"recommendedFocus":["下次重点练习"],"overallProgress":"整体评价"}'''
new3 = '''            {"trends":[{"dimension":"专业技能","trend":"上升/平稳/下降","detail":"说明"}],"recurringWeaknesses":["反复出现的弱点知识点"],"recommendedFocus":["下次重点练习"],"overallProgress":"整体评价","humorSummary":"幽默总结"}'''
if old3 in src:
    src = src.replace(old3, new3, 1)
    src = src.replace('''            4. overallProgress 用一段话整体评价候选人的成长情况。''',
'''            4. overallProgress 用一段话整体评价候选人的成长情况。
''' + HUMOR_RULE, 1)
    print('HISTORY_ANALYSIS patched')
else:
    print('[WARN] history analysis anchor')

# 4. initDefaultTemplates：存量 default 模板缺少 humorSummary 时以代码为准更新（一次迁移）
old4 = '''    /** 启动时确保 default 模板入库（页面可见/可编辑） */
    @jakarta.annotation.PostConstruct
    public void initDefaultTemplates() {
        for (Map.Entry<String, String> entry : DEFAULT_TEMPLATES.entrySet()) {
            if (templateRepository.findByOccupationAndScene(DEFAULT_OCCUPATION, entry.getKey()).isEmpty()) {
                PromptTemplate t = new PromptTemplate();
                t.setOccupation(DEFAULT_OCCUPATION);
                t.setScene(entry.getKey());
                t.setContent(entry.getValue());
                t.setIsActive(true);
                t.setUpdatedAt(LocalDateTime.now());
                templateRepository.insert(t);
            }
        }
        log.info("提示词默认模板初始化完成（{} 个场景）", DEFAULT_TEMPLATES.size());
    }'''
new4 = '''    /** 启动时确保 default 模板入库（页面可见/可编辑）；旧版默认模板缺少新字段时自动升级 */
    @jakarta.annotation.PostConstruct
    public void initDefaultTemplates() {
        for (Map.Entry<String, String> entry : DEFAULT_TEMPLATES.entrySet()) {
            java.util.Optional<PromptTemplate> existing =
                    templateRepository.findByOccupationAndScene(DEFAULT_OCCUPATION, entry.getKey());
            if (existing.isEmpty()) {
                PromptTemplate t = new PromptTemplate();
                t.setOccupation(DEFAULT_OCCUPATION);
                t.setScene(entry.getKey());
                t.setContent(entry.getValue());
                t.setIsActive(true);
                t.setUpdatedAt(LocalDateTime.now());
                templateRepository.insert(t);
            } else if (!existing.get().getContent().contains("humorSummary")
                    && entry.getValue().contains("humorSummary")) {
                // 旧版默认模板升级：以代码内置版本为准
                PromptTemplate t = existing.get();
                t.setContent(entry.getValue());
                t.setUpdatedAt(LocalDateTime.now());
                templateRepository.updateById(t);
                log.info("已升级默认提示词模板: scene={}", entry.getKey());
            }
        }
        log.info("提示词默认模板初始化完成（{} 个场景）", DEFAULT_TEMPLATES.size());
    }'''
if old4 in src:
    src = src.replace(old4, new4, 1)
    print('init migration patched')
else:
    print('[WARN] init anchor')

io.open(p, 'w', encoding='utf-8', newline='').write(src)
print('done')
