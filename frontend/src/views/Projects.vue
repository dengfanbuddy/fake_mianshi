<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProjects, createProject } from '../api/project'

const router = useRouter()

const projects = ref([])
const loading = ref(false)

// 新建项目弹窗
const dialogVisible = ref(false)
const creating = ref(false)
const form = ref({ name: '', description: '' })

async function fetchProjects() {
  loading.value = true
  try {
    const res = await getProjects()
    if (res.code === 200) {
      projects.value = res.data || []
    } else {
      ElMessage.error(res.message || '加载项目列表失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = { name: '', description: '' }
  dialogVisible.value = true
}

async function handleCreate() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入项目名称')
    return
  }
  creating.value = true
  try {
    const res = await createProject({
      name: form.value.name.trim(),
      description: form.value.description.trim(),
    })
    if (res.code === 200) {
      ElMessage.success('项目创建成功')
      dialogVisible.value = false
      await fetchProjects()
    } else {
      ElMessage.error(res.message || '创建项目失败')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    creating.value = false
  }
}

function enterProject(id) {
  router.push(`/project/${id}`)
}

function formatDate(s) {
  if (!s) return ''
  return new Date(s).toLocaleString()
}

onMounted(fetchProjects)
</script>

<template>
  <div class="home">
    <div class="page-header">
      <h2 class="page-title">项目管理</h2>
      <el-button type="primary" @click="openCreate">新建项目</el-button>
    </div>

    <div v-loading="loading" class="project-area">
      <el-empty v-if="!loading && !projects.length" description="还没有项目，点击右上角「新建项目」开始吧">
        <el-button type="primary" @click="openCreate">新建项目</el-button>
      </el-empty>

      <el-row v-else :gutter="20">
        <el-col
          v-for="p in projects"
          :key="p.id"
          :xs="24"
          :sm="12"
          :md="8"
          :lg="6"
          class="card-col"
        >
          <el-card shadow="hover" class="project-card">
            <div class="card-name">{{ p.name }}</div>
            <div class="card-desc">{{ p.description || '暂无描述' }}</div>
            <div class="card-meta">创建于 {{ formatDate(p.createdAt) }}</div>
            <div class="card-actions">
              <el-button type="primary" size="small" @click="enterProject(p.id)">进入</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <el-dialog v-model="dialogVisible" title="新建项目" width="480px">
      <el-form label-width="80px">
        <el-form-item label="项目名称" required>
          <el-input v-model="form.name" placeholder="例如：Java 后端岗求职训练" maxlength="100" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="简要描述目标岗位或学习计划（可选）"
            maxlength="500"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.page-title {
  margin: 0;
  font-size: 20px;
}
.project-area {
  min-height: 240px;
}
.card-col {
  margin-bottom: 20px;
}
.project-card {
  height: 100%;
}
.card-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.card-desc {
  font-size: 13px;
  color: #606266;
  min-height: 40px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: 8px;
}
.card-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
}
.card-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
