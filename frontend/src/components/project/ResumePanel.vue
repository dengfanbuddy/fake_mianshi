<script setup>
import { ref } from 'vue'
import { uploadResume, analyzeResume } from '../../api/resume'
import ResumeAnalysisResult from './ResumeAnalysisResult.vue'

const props = defineProps({
  // 项目 id：上传/分析简历接口需要
  projectId: {
    type: Number,
    required: true,
  },
  resume: {
    type: Object,
    default: null,
  },
  // 父组件解析好的 AI 分析结果（resume.analysisResult 的 JSON）
  analysis: {
    type: Object,
    default: null,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  generatingPosition: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:resume', 'generate-position'])

const uploading = ref(false)
const analyzing = ref(false)
const showUpload = ref(false)

// 上传前校验：仅 PDF、大小 ≤ 10MB
function beforePdfUpload(file) {
  const isPdf = file.type === 'application/pdf' || (file.name || '').toLowerCase().endsWith('.pdf')
  if (!isPdf) {
    ElMessage.warning('仅支持 PDF 格式的简历')
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('简历文件不能超过 10MB')
    return false
  }
  return true
}

async function handleUpload(options) {
  uploading.value = true
  try {
    const res = await uploadResume(props.projectId, options.file)
    if (res.code === 200) {
      emit('update:resume', res.data)
      ElMessage.success('简历上传并解析成功，正在调用 AI 分析…')
      options.onSuccess && options.onSuccess(res)
      await runAnalyze(res.data.id)
    } else {
      ElMessage.error(res.message || '上传失败')
      options.onError && options.onError(res)
    }
  } catch (e) {
    options.onError && options.onError(e)
  } finally {
    uploading.value = false
  }
}

async function runAnalyze(resumeId) {
  const id = resumeId ?? props.resume?.id
  if (!id) return
  analyzing.value = true
  try {
    const res = await analyzeResume(id)
    if (res.code === 200) {
      emit('update:resume', res.data)
      ElMessage.success('简历 AI 分析完成')
    } else {
      ElMessage.error(res.message || 'AI 分析失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    analyzing.value = false
  }
}
</script>

<template>
  <el-card class="section" shadow="never">
    <template #header><span>简历</span></template>

    <div v-loading="loading" class="section-body">
      <!-- 已上传：文件名 + 重新上传 + AI 分析 -->
      <template v-if="resume && !showUpload">
        <div class="resume-info">
          <el-icon class="file-icon">📄</el-icon>
          <span class="filename">{{ resume.originalFilename }}</span>
          <el-button type="primary" link @click="showUpload = true">重新上传</el-button>
        </div>
        <div class="resume-actions">
          <el-button
            type="primary"
            :loading="analyzing"
            @click="runAnalyze(resume.id)"
          >
            {{ analyzing ? 'AI 分析中，约需10-30秒…' : 'AI 分析简历' }}
          </el-button>
          <el-button v-if="analysis" type="warning" plain :loading="generatingPosition" @click="emit('generate-position')">
            从简历生成职位需求
          </el-button>
        </div>
      </template>

      <!-- 未上传 / 重新上传状态 -->
      <el-upload
        v-else
        drag
        action="#"
        accept=".pdf"
        :show-file-list="false"
        :http-request="handleUpload"
        :before-upload="beforePdfUpload"
        :disabled="uploading || analyzing"
      >
        <div class="upload-inner">
          <div class="upload-icon">⬆️</div>
          <div class="upload-text">将简历 PDF 拖到此处，或<em>点击上传</em></div>
          <div class="upload-hint">支持 .pdf 格式；上传成功后自动解析并调用 AI 分析</div>
        </div>
      </el-upload>
      <div v-if="resume && showUpload" class="reupload-cancel">
        <el-button type="primary" link @click="showUpload = false">取消重新上传</el-button>
      </div>

      <!-- AI 分析结果 -->
      <ResumeAnalysisResult v-if="analysis" :analysis="analysis" />
      <el-empty
        v-else-if="!loading && resume && !showUpload"
        description="尚未进行 AI 分析，点击上方按钮生成简历分析"
      />
    </div>
  </el-card>
</template>

<style scoped>
.section {
  margin-bottom: 20px;
}

/* 简历 */
.resume-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.file-icon {
  font-size: 20px;
}
.filename {
  font-size: 15px;
  font-weight: 600;
}
.resume-actions {
  display: flex;
  gap: 12px;
}
.upload-inner {
  padding: 10px 0;
}
.upload-icon {
  font-size: 40px;
  margin-bottom: 8px;
}
.upload-text {
  font-size: 14px;
  color: #606266;
}
.upload-text em {
  color: #409eff;
  font-style: normal;
}
.upload-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}
.reupload-cancel {
  margin-top: 8px;
}
</style>
