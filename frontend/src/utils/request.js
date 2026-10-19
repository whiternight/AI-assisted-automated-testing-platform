import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// baseURL 设为空，路径完全由接口文件自己控制
const baseURL = ''

const service = axios.create({
  baseURL: baseURL,
  timeout: 60000  // 增加超时时间到60秒，支持大文件上传
})

// 请求拦截器：增加skipToken判断
service.interceptors.request.use(
  config => {
    // 标记skipToken=true时，不携带token
    if (!config.skipToken) {
      const token = sessionStorage.getItem('token')
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
    }
    
    // 特殊处理BPMN XML部署请求 - 根据API文档，Content-Type应为application/json
    if (config.url.includes('/api-flow-definition/deploy')) {
      // 设置正确的Content-Type为application/json
      config.headers['Content-Type'] = 'application/json; charset=utf-8'
    }
    
    // 特殊处理前端部署请求 - 需要multipart/form-data格式
    if (config.url.includes('/deploy-api/deploy/frontend')) {
      // 对于multipart/form-data请求，让浏览器自动设置Content-Type（包含boundary）
      delete config.headers['Content-Type']
    }
    
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器
service.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    const { response, config } = error
    // 如果请求配置了 silentError，不弹全局错误提示，由调用方自行处理
    const silent = config?.silentError === true
    
    if (response) {
      // 优先使用后端返回的具体错误信息
      const backendMsg = response.data?.message || response.data?.msg
      
      if (!silent) {
        switch (response.status) {
          case 401:
            ElMessage.error(backendMsg || '认证失败，请重新登录')
            sessionStorage.removeItem('token')
            sessionStorage.removeItem('userInfo')
            break
          case 403:
            ElMessage.error(backendMsg || '没有权限访问')
            break
          default:
            ElMessage.error(backendMsg || `请求错误：${response.status}`)
        }
      }
    } else {
      if (!silent) {
        ElMessage.error('网络连接异常')
      }
    }
    return Promise.reject(error)
  }
)

export default service