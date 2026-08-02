import io

def patch(path, pairs):
    src = io.open(path, encoding='utf-8').read()
    orig = src
    for old, new in pairs:
        if old in src:
            src = src.replace(old, new, 1)
    if src != orig:
        io.open(path, 'w', encoding='utf-8', newline='').write(src)
        print('patched: ' + path)

patch('src/views/Home.vue', [
    ('<p class="hero-sub">面向 Java 开发者的求职面试训练：上传简历 → AI 出题 → 笔试 + 语音模拟面试 → 多维分析 → 自适应查漏补缺</p>',
     '<p class="hero-sub">面向任何职业的求职面试训练：上传简历 → AI 出题 → 笔试 + 语音模拟面试 → 多维分析 → 自适应查漏补缺</p>'),
    ('<div class="about-row"><span class="about-label">定位</span><span>个人求职面试训练工具，Java 后端方向</span></div>',
     '<div class="about-row"><span class="about-label">定位</span><span>个人求职面试训练工具，支持任意职业（按职业定制提示词）</span></div>'),
])

patch('src/views/Layout.vue', [
    ('<div class="brand-sub">Java 面试训练</div>',
     '<div class="brand-sub">AI 面试训练</div>'),
])

patch('src/views/Help.vue', [
    ("{ title: '1. 新建项目', desc: '在「项目管理」页新建面试项目，描述你的目标岗位（如 Java 后端高级岗）。', action: '去项目管理 →', path: '/projects' },",
     "{ title: '1. 新建项目', desc: '在「项目管理」页新建面试项目，填写目标岗位（如后端开发 / 产品经理），系统按职业匹配提示词。', action: '去项目管理 →', path: '/projects' },"),
])

patch('src/views/ProjectDetail.vue', [
    ('<el-input v-model="positionForm.jobTitle" placeholder="例如：Java 后端工程师" />',
     '<el-input v-model="positionForm.jobTitle" placeholder="例如：后端开发工程师 / 产品经理" />'),
    ('<el-input v-model="positionForm.experience" placeholder="例如：3-5 年 Java 开发经验" />',
     '<el-input v-model="positionForm.experience" placeholder="例如：3-5 年相关经验" />'),
    ('placeholder="输入后回车添加，例如 Java、Spring"',
     'placeholder="输入后回车添加，例如：数据库、项目架构"'),
])

patch('src/views/Projects.vue', [
    ("""    if (res.code === 200) {
      ElMessage.success('项目创建成功')
      dialogVisible.value = false""",
     """    if (res.code === 200) {
      ElMessage.success('项目创建成功')
      if (form.value.targetPosition?.trim()) {
        ElMessage.info('已按目标岗位匹配提示词；若为新职业，AI 正在后台生成专属提示词，可到「提示词管理」查看与编辑')
      }
      dialogVisible.value = false"""),
])

print('done')
