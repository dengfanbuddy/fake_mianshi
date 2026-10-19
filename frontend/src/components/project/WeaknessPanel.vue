<script setup>
import { useRouter } from 'vue-router'

const props = defineProps({
  tags: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  /** 有项目 ID 时才显示「去复习」入口（错题本按项目维度组织） */
  projectId: {
    type: [Number, String],
    default: null,
  },
})

const router = useRouter()

const masteryTagMap = {
  WEAK: { type: 'danger', label: '薄弱' },
  FAIR: { type: 'warning', label: '一般' },
  GOOD: { type: 'success', label: '良好' },
  STRONG: { type: 'primary', label: '优秀' },
}

function masteryMeta(level) {
  return masteryTagMap[level] || { type: 'info', label: level || '未知' }
}

/** 薄弱点 → 错题本：闭环的入口在这里，用户看完报告能直接去练 */
function goReview() {
  if (!props.projectId) return
  router.push(`/review/${props.projectId}`)
}
</script>

<template>
  <el-card class="section" shadow="never">
    <template #header>
      <div class="panel-head">
        <span>薄弱知识点</span>
        <el-button v-if="projectId" type="primary" size="small" text @click="goReview">
          去错题本复习 →
        </el-button>
      </div>
    </template>

    <div v-loading="loading" class="section-body">
      <div v-if="tags.length" class="weakness-list">
        <el-tooltip
          v-for="tag in tags"
          :key="tag.id"
          placement="top"
          :disabled="!tag.aliasList?.length"
        >
          <template #content>
            <div class="alias-tip">
              <div class="alias-tip-title">同一个知识点的 {{ tag.aliasList.length + 1 }} 种写法：</div>
              <div v-for="(alias, i) in tag.aliasList" :key="i">· {{ alias }}</div>
            </div>
          </template>
          <div class="weakness-item">
            <span class="weak-point">{{ tag.knowledgePoint }}</span>
            <el-tag v-if="tag.aliasList?.length" size="small" type="info" effect="plain">
              {{ tag.aliasList.length + 1 }} 种说法
            </el-tag>
            <el-tag :type="masteryMeta(tag.masteryLevel).type" size="small" effect="dark">
              {{ masteryMeta(tag.masteryLevel).label }}
            </el-tag>
            <el-badge
              :value="tag.occurrenceCount"
              :max="99"
              class="occurrence-badge"
            ><span class="occurrence-bg">出现次数</span></el-badge>
          </div>
        </el-tooltip>
      </div>
      <el-empty v-else-if="!loading" description="暂无弱点标签，完成面试分析后生成" />
    </div>
  </el-card>
</template>

<style scoped>
.section {
  margin-bottom: 20px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 弱点 */
.weakness-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.weakness-item {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f9fafb;
  border-radius: 8px;
  padding: 10px 14px;
}
.weak-point {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.occurrence-badge {
  margin-left: 4px;
}
.occurrence-bg {
  display: inline-block;
  font-size: 12px;
  color: #909399;
}
.alias-tip {
  font-size: 12px;
  line-height: 1.6;
  max-width: 360px;
}
.alias-tip-title {
  font-weight: 600;
  margin-bottom: 4px;
}
</style>
