<template>
  <div class="frontend-deploy-container">
    <el-card class="deploy-card">
      <template #header>
        <div class="card-header">
          <span>前端部署</span>
        </div>
      </template>
      
      <el-form 
        :model="deployForm" 
        :rules="deployRules" 
        ref="deployFormRef"
        label-width="120px"
        class="deploy-form"
      >
        <el-form-item label="ZIP文件" prop="file">
          <el-upload
            ref="uploadRef"
            v-model:file-list="fileList"
            :auto-upload="false"
            :on-change="handleFileChange"
            :limit="1"
            accept=".zip"
            drag
          >
            <el-icon class="upload-icon"><Upload /></el-icon>
            <div class="el-upload__text">拖拽文件到此处或<em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">只能上传ZIP文件，且不超过50MB</div>
            </template>
          </el-upload>
        </el-form-item>
        
        <el-form-item label="版本号" prop="version">
          <el-input 
            v-model="deployForm.version" 
            placeholder="请输入版本号，如：3.1.0"
            clearable
          />
        </el-form-item>
        
        <el-form-item label="上传人" prop="uploader">
          <el-input 
            v-model="deployForm.uploader" 
            placeholder="请输入上传人姓名，如：前端-小王"
            clearable
          />
        </el-form-item>
        
        <el-form-item>
          <el-button 
            type="primary" 
            @click="submitDeploy"
            :loading="deployLoading"
            :disabled="!canSubmit"
          >
            {{ deployLoading ? '部署中...' : '开始部署' }}
          </el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { deployFrontend } from '@/api/admin'

// 表单数据
const deployForm = reactive({
  file: null,
  version: '',
  uploader: ''
})

// 文件列表
const fileList = ref([])

// 表单验证规则
const deployRules = {
  file: [
    { required: true, message: '请选择ZIP文件', trigger: 'change' }
  ],
  version: [
    { required: true, message: '请输入版本号', trigger: 'blur' },
    { pattern: /^\d+\.\d+\.\d+$/, message: '版本号格式应为 x.y.z', trigger: 'blur' }
  ],
  uploader: [
    { required: true, message: '请输入上传人', trigger: 'blur' }
  ]
}

// 表单引用
const deployFormRef = ref(null)
const uploadRef = ref(null)

// 加载状态
const deployLoading = ref(false)

// 是否可以提交
const canSubmit = computed(() => {
  return fileList.value.length > 0 && deployForm.version && deployForm.uploader
})

// 文件变化处理
const handleFileChange = (file, fileList) => {
  if (file.raw) {
    // 验证文件类型
    if (!file.raw.type.includes('zip')) {
      ElMessage.error('只能上传ZIP文件')
      uploadRef.value?.clearFiles()
      return
    }
    
    // 验证文件大小（50MB限制）
    if (file.raw.size > 50 * 1024 * 1024) {
      ElMessage.error('文件大小不能超过50MB')
      uploadRef.value?.clearFiles()
      return
    }
    
    deployForm.file = file.raw
  }
}

// 提交部署
const submitDeploy = async () => {
  if (!deployFormRef.value) return
  
  // 验证表单
  await deployFormRef.value.validate((valid) => {
    if (!valid) {
      ElMessage.error('请填写完整信息')
      return
    }
  })
  
  // 确认部署
  try {
    await ElMessageBox.confirm(
      `确定要部署版本 ${deployForm.version} 吗？`,
      '确认部署',
      { type: 'warning' }
    )
  } catch {
    return
  }
  
  deployLoading.value = true
  try {
    // 创建FormData对象
    const formData = new FormData()
    formData.append('file', deployForm.file)
    formData.append('version', deployForm.version)
    formData.append('uploader', deployForm.uploader)
    
    // 发送部署请求
    const response = await deployFrontend(formData)
    
    ElMessage.success('部署请求已发送')
    
    // 添加到部署历史
    deploymentHistory.value.unshift({
      version: deployForm.version,
      uploader: deployForm.uploader,
      fileName: deployForm.file.name,
      uploadTime: new Date().toLocaleString('zh-CN'),
      status: '部署中'
    })
    
    // 清空表单
    resetForm()
  } catch (error) {
    console.error('部署失败:', error)
    ElMessage.error('部署失败: ' + (error.message || '未知错误'))
  } finally {
    deployLoading.value = false
  }
}

// 重置表单
const resetForm = () => {
  if (deployFormRef.value) {
    deployFormRef.value.resetFields()
  }
  uploadRef.value?.clearFiles()
  Object.assign(deployForm, {
    file: null,
    version: '',
    uploader: ''
  })
  fileList.value = []
}

// 获取部署历史
const loadDeploymentHistory = async () => {
  historyLoading.value = true
  try {
    // 这里应该调用获取部署历史的API
    // const response = await getDeployHistory()
    // deploymentHistory.value = response.data
    
    // 暂时使用模拟数据
    deploymentHistory.value = [
      { version: '3.0.0', uploader: '前端-小李', fileName: 'dist-v3.0.0.zip', uploadTime: '2024-01-15 10:30:00', status: '成功' },
      { version: '3.0.1', uploader: '前端-小张', fileName: 'dist-v3.0.1.zip', uploadTime: '2024-01-16 14:20:00', status: '成功' },
      { version: '3.0.2', uploader: '前端-小王', fileName: 'dist-v3.0.2.zip', uploadTime: '2024-01-17 09:15:00', status: '失败' }
    ]
  } catch (error) {
    console.error('获取部署历史失败:', error)
    ElMessage.error('获取部署历史失败')
  } finally {
    historyLoading.value = false
  }
}

</script>

<style scoped>
.frontend-deploy-container {
  padding: 0;
  background: var(--paper, #fafafa);
  min-height: calc(100vh - 100px);
}

.deploy-card {
  max-width: 700px;
  margin: 0 auto 20px;
  border: 1px solid var(--mute, #e5e5e5);
  border-radius: 0;
  box-shadow: none;
  background: var(--paper, #fafafa);
}

:deep(.el-card__header) {
  border-bottom: 1px solid var(--mute, #e5e5e5);
  padding: 14px 20px;
}

.card-header {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink, #0a0a0a);
}

:deep(.el-card__body) {
  padding: 20px;
}

.deploy-form {
  margin-top: 0;
}

:deep(.el-form-item__label) {
  font-size: 10px !important;
  font-weight: 500 !important;
  color: var(--ghost, #a3a3a3) !important;
  text-transform: uppercase !important;
  letter-spacing: 0.08em !important;
}

.upload-icon {
  font-size: 32px;
  color: var(--ghost, #a3a3a3);
  margin-bottom: 12px;
}

:deep(.el-upload-dragger) {
  border: 1px solid var(--mute, #e5e5e5);
  border-radius: 0;
  background: var(--paper, #fafafa);
  transition: border-color 0.2s;
}

:deep(.el-upload-dragger:hover) {
  border-color: var(--ink, #0a0a0a);
}
</style>
