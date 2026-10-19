<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  position: {
    type: Object,
    default: null,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  saving: {
    type: Boolean,
    default: false,
  },
  generating: {
    type: Boolean,
    default: false,
  },
  // 由父组件控制的“编辑中”状态（v-model:editing）
  editing: {
    type: Boolean,
    default: false,
  },
  // 是否存在已分析的简历（决定是否展示“从简历生成”）
  hasResumeAnalysis: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:editing', 'save', 'generate'])

const seniorityOptions = ['初级', '中级', '高级']
const companyTypeOptions = ['大厂', '创业公司', '外企', '其他']

const positionForm = ref({
  id: null,
  jobTitle: '',
  seniority: '',
  companyType: '',
  experience: '',
  techStack: [],
  jobDescription: '',
})

const techStackList = computed(() => {
  if (!props.position?.techStack) return []
  try {
    const arr = JSON.parse(props.position.techStack)
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
})

function parsePositionToForm(p) {
  let stack = []
  if (p?.techStack) {
    try {
      const arr = JSON.parse(p.techStack)
      stack = Array.isArray(arr) ? arr : []
    } catch (e) {
      stack = []
    }
  }
  positionForm.value = {
    id: p?.id ?? null,
    jobTitle: p?.jobTitle || '',
    seniority: p?.seniority || '',
    companyType: p?.companyType || '',
    experience: p?.experience || '',
    techStack: stack,
    jobDescription: p?.jobDescription || '',
  }
}

// 职位数据变化（加载/保存/生成）或进入/退出编辑态时，用最新数据回填表单
watch(
  () => [props.position, props.editing],
  () => {
    parsePositionToForm(props.position)
  }
)

function startEditPosition() {
  emit('update:editing', true)
}

function cancelEditPosition() {
  emit('update:editing', false)
}

// 保存：校验后把表单转换成接口所需的字段形状（技术栈序列化为 JSON 字符串）
function handleSave() {
  const form = positionForm.value
  if (!form.jobTitle.trim()) {
    ElMessage.warning('请填写岗位名称')
    return
  }
  emit('save', {
    id: form.id,
    jobTitle: form.jobTitle.trim(),
    seniority: form.seniority,
    companyType: form.companyType,
    experience: form.experience.trim(),
    techStack: JSON.stringify(form.techStack),
    jobDescription: form.jobDescription.trim(),
  })
}
</script>

<template>
  <el-card class="section" shadow="never">
    <template #header><span>职位需求</span></template>

    <div v-loading="loading" class="section-body">
      <!-- 已有职位：展示 -->
      <template v-if="position && !editing">
        <div class="position-summary">
          <div class="pos-title">
            <span class="job-title">{{ position.jobTitle }}</span>
            <el-tag size="small" type="info" effect="plain">{{ position.source === 'RESUME' ? '来自简历' : '手动填写' }}</el-tag>
            <el-button class="edit-btn" type="primary" link @click="startEditPosition">编辑</el-button>
          </div>
          <div class="pos-meta">
            <el-tag v-if="position.seniority" size="small">{{ position.seniority }}</el-tag>
            <el-tag v-if="position.companyType" size="small" type="success">{{ position.companyType }}</el-tag>
          </div>
          <div v-if="position.experience" class="pos-line"><span class="label">经验要求：</span>{{ position.experience }}</div>
          <div class="pos-line"><span class="label">技术栈：</span>
            <template v-if="techStackList.length">
              <el-tag v-for="(t, i) in techStackList" :key="i" size="small" class="tag-item">{{ t }}</el-tag>
            </template>
            <span v-else class="muted">—</span>
          </div>
          <div v-if="position.jobDescription" class="pos-line">
            <span class="label">岗位职责：</span>
            <span class="job-desc">{{ position.jobDescription }}</span>
          </div>
        </div>
      </template>

      <!-- 编辑 / 手动填写表单 -->
      <template v-else>
        <el-form label-width="90px" class="position-form">
          <el-form-item label="岗位名称" required>
            <el-input v-model="positionForm.jobTitle" placeholder="例如：后端开发工程师 / 产品经理" />
          </el-form-item>
          <el-form-item label="职级">
            <el-select v-model="positionForm.seniority" placeholder="选择职级" clearable class="form-select">
              <el-option v-for="s in seniorityOptions" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
          <el-form-item label="公司类型">
            <el-select v-model="positionForm.companyType" placeholder="选择公司类型" clearable class="form-select">
              <el-option v-for="c in companyTypeOptions" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item label="经验要求">
            <el-input v-model="positionForm.experience" placeholder="例如：3-5 年相关经验" />
          </el-form-item>
          <el-form-item label="技术栈">
            <el-select
              v-model="positionForm.techStack"
              multiple
              filterable
              allow-create
              default-first-option
              :reserve-keyword="false"
              placeholder="输入后回车添加，例如：数据库、项目架构"
              class="form-select"
            >
              <el-option v-for="t in positionForm.techStack" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="岗位职责">
            <el-input
              v-model="positionForm.jobDescription"
              type="textarea"
              :rows="4"
              placeholder="描述岗位职责与能力要求"
            />
          </el-form-item>
        </el-form>
        <div class="form-actions">
          <el-button v-if="editing" @click="cancelEditPosition">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          <el-button
            v-if="hasResumeAnalysis"
            type="warning"
            plain
            :loading="generating"
            @click="emit('generate')"
          >从简历生成</el-button>
        </div>
      </template>
    </div>
  </el-card>
</template>

<style scoped>
.section {
  margin-bottom: 20px;
}

/* 职位 */
.position-summary {
  line-height: 1.9;
}
.pos-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.job-title {
  font-size: 18px;
  font-weight: 700;
}
.edit-btn {
  margin-left: auto;
}
.pos-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}
.pos-line {
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
}
.pos-line .label {
  color: #909399;
}
.job-desc {
  white-space: pre-wrap;
  word-break: break-word;
}
.tag-item {
  margin-right: 4px;
}
.muted {
  color: #c0c4cc;
}
.form-select {
  width: 100%;
}
.form-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
