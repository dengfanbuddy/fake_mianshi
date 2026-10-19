<script setup>
// 性格与特质总结：纯展示
defineProps({
  personalitySummary: { type: String, default: '' },
  // 性格优点标签
  characterTraits: { type: Array, default: () => [] },
  // 性格缺陷（对象 {defect, improvement} 或旧版纯字符串）
  characterDefects: { type: Array, default: () => [] },
})

// 性格缺陷：兼容对象 {defect, improvement} 与纯字符串
function defectText(d) {
  return typeof d === 'object' && d !== null ? d.defect || '' : d || ''
}
function defectImprovement(d) {
  return typeof d === 'object' && d !== null ? d.improvement || '' : ''
}
</script>

<template>
  <section
    v-if="personalitySummary || characterTraits.length || characterDefects.length"
    class="card"
  >
    <h3 class="card-title info-title">🧠 性格与特质总结</h3>
    <p v-if="personalitySummary" class="para-text personality-text">{{ personalitySummary }}</p>
    <div v-if="characterTraits.length" class="tag-list traits-tags">
      <el-tag v-for="(t, i) in characterTraits" :key="i" type="primary" effect="plain" class="item-tag">
        {{ t }}
      </el-tag>
    </div>
    <div v-if="characterDefects.length" class="defect-list">
      <div v-for="(d, i) in characterDefects" :key="i" class="defect-item">
        <div class="defect-head">
          <span class="defect-badge">缺陷 {{ i + 1 }}</span>
          <span class="defect-name">{{ defectText(d) }}</span>
        </div>
        <p v-if="defectImprovement(d)" class="defect-fix">💡 改进：{{ defectImprovement(d) }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.card {
  background: #fff;
  border-radius: 14px;
  padding: 24px 26px;
  box-shadow: 0 2px 12px rgba(31, 45, 61, 0.06);
  border: 1px solid #ebeef5;
}
.card-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 6px;
}
.card-title.info-title {
  color: #337ecc;
}
.para-text {
  margin: 0;
  padding: 14px 16px;
  background: #ecf5ff;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.8;
  color: #3a6ea5;
  white-space: pre-wrap;
}
.personality-text {
  background: #f0f9eb;
  color: #529b2e;
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.item-tag {
  font-size: 13px;
  line-height: 26px;
  height: auto;
  white-space: normal;
  padding: 2px 12px;
}
.traits-tags {
  margin-top: 12px;
}
.defect-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}
.defect-item {
  padding: 10px 14px;
  background: #fef0f0;
  border-radius: 8px;
  font-size: 14px;
  color: #d35050;
  line-height: 1.6;
}
.defect-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.defect-badge {
  flex-shrink: 0;
  font-size: 11px;
  background: #f56c6c;
  color: #fff;
  border-radius: 4px;
  padding: 1px 8px;
}
.defect-name {
  font-weight: 600;
}
.defect-fix {
  margin: 6px 0 0;
  color: #b88230;
  font-size: 13px;
}
</style>
