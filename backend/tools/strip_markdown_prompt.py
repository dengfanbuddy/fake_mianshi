import io

def patch(path, pairs):
    src = io.open(path, encoding='utf-8').read()
    orig = src
    for old, new in pairs:
        if old not in src:
            print('  [WARN] ' + path + ': ' + old[:70])
            continue
        src = src.replace(old, new, 1)
    if src != orig:
        io.open(path, 'w', encoding='utf-8', newline='').write(src)
        print('patched: ' + path)

# ---------- 分析 prompt：去掉 Markdown 展示部分，只输出严格 JSON ----------
PROMOT_OLD = """
            【输出要求】分两部分输出：
            第一部分（Markdown 展示）：先用 Markdown 格式输出一份完整可读的分析报告，包含以下小节：
            ## 总体评价（overallScore 得分、能力等级、期望薪资区间）
            ## 优点
            ## 不足
            ## 知识盲区与学习要点（每个盲区列出知识点与核心答案要点，便于直接学习）
            ## 逐题点评（每题：题目、各维度得分、点评、改进建议；简答题附参考答案要点）
            ## 改进计划
            内容需与第二部分 JSON 保持一致，供候选人边生成边阅读。

            第二部分（结构化数据）：展示部分结束后，单独一行输出 ==JSON_START==，
            其后输出严格 JSON（不要任何其他文字、Markdown 代码块围栏或注释），结构如下："""

PROMOT_NEW = """
            请严格返回如下 JSON 结构，不要输出任何额外文字或 Markdown 代码块："""

patch('src/main/java/com/fakemianshi/service/impl/AnalysisServiceImpl.java', [
    (PROMOT_OLD, PROMOT_NEW),
    ("""            其后输出严格 JSON（不要任何其他文字、Markdown 代码块围栏或注释），结构如下：
            {"overallScore":0-100,"overallLevel":"...","expectedSalaryRange":"...","strengths":["..."],"weaknesses":["..."],"knowledgeGaps":[{"point":"知识点","explanation":"答案要点"}],"personalitySummary":"...","characterTraits":["..."],"characterDefects":[{"defect":"缺陷","improvement":"改进方法"}],"improvementPlan":{"topics":[{"topic":"学习方向","action":"具体做法","example":"实例"}],"suggestions":["建议"]},"questionAnalyses":[{"questionContent":"...","answerContent":"...","category":"技术分类","difficulty":"难度","focusPoint":"考察方向","accuracy":0-10,"accuracyReason":"依据","depth":0-10,"depthReason":"依据","clarity":0-10,"clarityReason":"依据","fluency":0-10,"fluencyReason":"依据","tone":"","answerApproach":"回答思路","example":"优秀示例","improvementSuggestion":"..."}]}""",
     """            {"overallScore":0-100,"overallLevel":"...","expectedSalaryRange":"...","strengths":["..."],"weaknesses":["..."],"knowledgeGaps":[{"point":"知识点","explanation":"答案要点"}],"personalitySummary":"...","characterTraits":["..."],"characterDefects":[{"defect":"缺陷","improvement":"改进方法"}],"improvementPlan":{"topics":[{"topic":"学习方向","action":"具体做法","example":"实例"}],"suggestions":["建议"]},"questionAnalyses":[{"questionContent":"...","answerContent":"...","category":"技术分类","difficulty":"难度","focusPoint":"考察方向","accuracy":0-10,"accuracyReason":"依据","depth":0-10,"depthReason":"依据","clarity":0-10,"clarityReason":"依据","fluency":0-10,"fluencyReason":"依据","tone":"","answerApproach":"回答思路","example":"优秀示例","improvementSuggestion":"..."}]}"""),
    ("""            其后输出严格 JSON（不要任何其他文字、Markdown 代码块围栏或注释），结构如下：
            {"overallScore":0-100,"overallLevel":"...","expectedSalaryRange":"...","strengths":["..."],"weaknesses":["..."],"knowledgeGaps":[{"point":"知识点","explanation":"答案要点"}],"personalitySummary":"...","characterTraits":["..."],"characterDefects":[{"defect":"缺陷","improvement":"改进方法"}],"communicationEvaluation":"...","improvementPlan":{"topics":[{"topic":"学习方向","action":"具体做法","example":"实例"}],"suggestions":["建议"]},"questionAnalyses":[{"questionContent":"...","answerContent":"...","category":"技术分类","difficulty":"难度","focusPoint":"考察方向","accuracy":0-10,"accuracyReason":"依据","depth":0-10,"depthReason":"依据","clarity":0-10,"clarityReason":"依据","fluency":0-10,"fluencyReason":"依据","tone":"","answerApproach":"回答思路","example":"优秀示例","improvementSuggestion":"..."}]}""",
     """            {"overallScore":0-100,"overallLevel":"...","expectedSalaryRange":"...","strengths":["..."],"weaknesses":["..."],"knowledgeGaps":[{"point":"知识点","explanation":"答案要点"}],"personalitySummary":"...","characterTraits":["..."],"characterDefects":[{"defect":"缺陷","improvement":"改进方法"}],"communicationEvaluation":"...","improvementPlan":{"topics":[{"topic":"学习方向","action":"具体做法","example":"实例"}],"suggestions":["建议"]},"questionAnalyses":[{"questionContent":"...","answerContent":"...","category":"技术分类","difficulty":"难度","focusPoint":"考察方向","accuracy":0-10,"accuracyReason":"依据","depth":0-10,"depthReason":"依据","clarity":0-10,"clarityReason":"依据","fluency":0-10,"fluencyReason":"依据","tone":"","answerApproach":"回答思路","example":"优秀示例","improvementSuggestion":"..."}]}"""),
])

# ---------- 笔试出题 prompt：去掉 Markdown 展示部分 ----------
patch('src/main/java/com/fakemianshi/service/impl/QuestionGenerationServiceImpl.java', [
    ("""
                【输出要求】
                第一部分（Markdown 展示）：用 Markdown 格式输出全部题目，每道题格式如下：
                ### 1. [单选题] 题目内容
                - A. 选项一
                - B. 选项二
                - C. 选项三
                - D. 选项四
                （注意：展示部分不要写出答案和解析）

                第二部分（结构化数据）：展示部分结束后，单独一行输出 ==JSON_START==，
                其后输出一个严格 JSON 数组（不要输出任何其他文字、代码块围栏或注释），数组每项格式：""",
     """
                请严格返回如下 JSON 数组，不要输出任何额外文字或 Markdown 代码块，数组每项格式："""),
])

print('done')
