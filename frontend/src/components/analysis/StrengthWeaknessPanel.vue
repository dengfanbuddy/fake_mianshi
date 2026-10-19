<script setup>
// 优势亮点 / 待改进 双列卡片：纯展示
defineProps({
  strengths: { type: Array, default: () => [] },
  weaknesses: { type: Array, default: () => [] },
})
</script>

<template>
  <section v-if="strengths.length || weaknesses.length" class="grid-2">
    <div class="card">
      <h3 class="card-title good-title">✅ 优势亮点</h3>
      <div v-if="strengths.length" class="tag-list">
        <el-tag v-for="(s, i) in strengths" :key="i" type="success" class="item-tag" effect="light">
          {{ s }}
        </el-tag>
      </div>
      <el-empty v-else description="暂无优势分析" :image-size="60" />
    </div>
    <div class="card">
      <h3 class="card-title bad-title">⚠️ 待改进</h3>
      <div v-if="weaknesses.length" class="tag-list">
        <el-tag v-for="(w, i) in weaknesses" :key="i" type="danger" class="item-tag" effect="light">
          {{ w }}
        </el-tag>
      </div>
      <el-empty v-else description="暂无弱项分析" :image-size="60" />
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
.card-title.good-title {
  color: #529b2e;
}
.card-title.bad-title {
  color: #e05252;
}
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}
@media (max-width: 720px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
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
</style>
