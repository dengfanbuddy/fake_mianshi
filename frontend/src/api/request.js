import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({ baseURL: '/api', timeout: 120000 })

request.interceptors.response.use(
  response => {
    const data = response.data
    // 后端 ApiResponse 成功码为 0，前端统一按 200 语义判断；归一化避免各页面 code===200 判断失效
    if (data && typeof data === 'object' && data.code === 0) {
      data.code = 200
    }
    return data
  },
  error => {
    ElMessage.error(error.response?.data?.message || '请求失败')
    return Promise.reject(error)
  }
)
export default request
