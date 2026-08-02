import io

p = 'src/views/analysis/SessionReport.vue'
src = io.open(p, encoding='utf-8').read()

src = src.replace("const overallLevel = computed(() => analysis.value?.overallLevel || '')",
                  "const overallLevel = computed(() => analysis.value?.overallLevel || '')\nconst humorSummary = computed(() => analysis.value?.humorSummary || '')")

old2 = '''        <!-- 2. 能力雷达 -->
        <section v-if="radarDimensions.length" class="card radar-card">'''
new2 = '''        <!-- 幽默总结卡 -->
        <section v-if="humorSummary" class="card humor-card">
          <div class="humor-emoji">😄</div>
          <div class="humor-content">
            <div class="humor-title">面试官大实话</div>
            <div class="humor-text">{{ humorSummary }}</div>
          </div>
        </section>

        <!-- 2. 能力雷达 -->
        <section v-if="radarDimensions.length" class="card radar-card">'''
if old2 in src:
    src = src.replace(old2, new2, 1)
    print('humor card inserted')
else:
    old3 = '<!-- 2. 能力雷达 -->'
    if old3 in src:
        src = src.replace(old3, '''<!-- 幽默总结卡 -->
        <section v-if="humorSummary" class="card humor-card">
          <div class="humor-emoji">😄</div>
          <div class="humor-content">
            <div class="humor-title">面试官大实话</div>
            <div class="humor-text">{{ humorSummary }}</div>
          </div>
        </section>

        <!-- 2. 能力雷达 -->''', 1)
        print('humor card inserted (alt)')
    else:
        print('[WARN] no anchor')

src = src.replace('''.overview-card {''', '''.humor-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: linear-gradient(135deg, #fdf6ec 0%, #fff7e6 100%);
  border: 1px solid #f3d19e;
  border-radius: 12px;
  padding: 16px 20px;
}
.humor-emoji {
  font-size: 34px;
  line-height: 1;
}
.humor-title {
  font-size: 13px;
  font-weight: 600;
  color: #b88230;
  margin-bottom: 4px;
}
.humor-text {
  font-size: 15px;
  color: #7a5c1e;
  line-height: 1.6;
}
.overview-card {''', 1)

io.open(p, 'w', encoding='utf-8', newline='').write(src)
print('done')
