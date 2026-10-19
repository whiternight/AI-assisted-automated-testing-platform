<template>
  <div class="service-detail-container">
    <!-- 顶部栏 -->
    <div class="top-bar">
      <div class="input-area">
        <el-input v-model="form.name" placeholder="请输入服务名称" class="name-input" clearable size="small" />
        <el-select v-model="form.method" placeholder="请求方法" class="method-select" size="small">
          <el-option label="GET" value="GET" />
          <el-option label="POST" value="POST" />
          <el-option label="PUT" value="PUT" />
          <el-option label="DELETE" value="DELETE" />
        </el-select>
        <el-input v-model="form.url" placeholder="输入URL路径或完整地址" class="url-input" clearable />
      </div>
      <div class="action-group">
        <el-button type="primary" @click="sendRequest" :loading="isSending">发送</el-button>
        <el-button @click="handleSave" :loading="saveLoading">保存</el-button>
        <el-button @click="handleBack" :icon="ArrowLeft" circle />
      </div>
    </div>

    <!-- 元信息栏 -->
    <div class="meta-bar">
      <div class="meta-item">
        <span class="meta-label">原服务类型</span>
        <el-select v-if="!isEditMode" v-model="form.serviceType" placeholder="请选择服务类型" size="small"
          style="width: 160px" filterable allow-create>
          <el-option v-for="item in serviceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <span v-else class="meta-value">{{ serviceTypeName }}</span>
      </div>
      <div class="meta-item">
        <span class="meta-label">可用状态</span>
        <el-radio-group v-model="form.isAvailable" size="small">
          <el-radio :value="1">可用</el-radio>
          <el-radio :value="0">不可用</el-radio>
        </el-radio-group>
      </div>
    </div>

    <!-- Tabs 区域 -->
    <el-card class="tester-card">
      <el-tabs v-model="activeTab" type="card" class="api-tabs-wrap">
        <!-- Params -->
        <el-tab-pane label="Params" name="params">
          <div class="tab-scroll-wrap">
            <div class="params-area">
              <div class="params-header">Query 参数</div>
              <div class="params-table">
                <div class="param-row header">
                  <div class="col name">参数名</div>
                  <div class="col value">参数值</div>
                  <div class="col type">数据类型</div>
                  <div class="col desc">描述</div>
                  <div class="col actions">操作</div>
                </div>
                <div v-for="(p, i) in params" :key="p.id" class="param-row">
                  <div class="col name"><el-input v-model="p.name" size="small" /></div>
                  <div class="col value"><el-input v-model="p.value" size="small" /></div>
                  <div class="col type">
                    <el-select v-model="p.type" size="small">
                      <el-option label="string" value="string" />
                      <el-option label="number" value="number" />
                      <el-option label="boolean" value="boolean" />
                      <el-option label="integer" value="integer" />
                      <el-option label="array" value="array" />
                    </el-select>
                  </div>
                  <div class="col desc"><el-input v-model="p.desc" size="small" /></div>
                  <div class="col actions"><el-button type="text" size="small" @click="removeParam(i)">删除</el-button></div>
                </div>
                <div style="margin-top:8px"><el-button size="small" @click="addParam">+添加Query参数</el-button></div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- Body -->
        <el-tab-pane label="Body" name="body">
          <div class="tab-scroll-wrap">
            <ApiBody v-model="form.paramsJson" v-model:bodyType="form.bodyType" />
          </div>
        </el-tab-pane>

        <!-- Headers -->
        <el-tab-pane label="Headers" name="headers">
          <div class="tab-scroll-wrap">
            <div class="headers-header">
              <span>Headers</span>
            </div>
            <div class="params-table">
              <div class="param-row header">
                <div class="col name">Header名</div>
                <div class="col value">Header值</div>
                <div class="col type">数据类型</div>
                <div class="col desc">描述</div>
                <div class="col actions">操作</div>
              </div>
              <div v-for="(h, i) in headers" :key="h.id" class="param-row">
                <div class="col name"><el-input v-model="h.name" size="small" /></div>
                <div class="col value"><el-input v-model="h.value" size="small" /></div>
                <div class="col type">
                  <el-select v-model="h.type" size="small">
                    <el-option label="string" value="string" />
                    <el-option label="number" value="number" />
                    <el-option label="boolean" value="boolean" />
                  </el-select>
                </div>
                <div class="col desc"><el-input v-model="h.desc" size="small" /></div>
                <div class="col actions"><el-button type="text" size="small" @click="removeHeader(i)">删除</el-button></div>
              </div>
              <div style="margin-top:8px"><el-button size="small" @click="addHeader">+添加Header</el-button></div>
            </div>
          </div>
        </el-tab-pane>

        <!-- Response -->
        <el-tab-pane label="Response" name="response">
          <div class="tab-scroll-wrap">
            <div v-if="responseResult" class="response-area">
              <div class="response-header">
                <span>响应结果</span>
                <span class="response-status" :class="{ success: responseResult.success, error: !responseResult.success }">
                  {{ responseResult.success ? '成功' : '失败' }}
                </span>
                <span class="response-duration">耗时: {{ responseResult.duration }}ms</span>
              </div>
              <pre class="response-body">{{ formatJson(responseResult.data) }}</pre>
            </div>
            <div v-else class="empty-response">
              <p>点击"发送"按钮查看响应结果</p>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getOriginalServiceDetail, updateOriginalService, createOriginalService, directTest } from '@/api/admin'

// ApiBody 组件（需要创建）
import ApiBody from './components/ApiBody.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const saveLoading = ref(false)
const isSending = ref(false)
const activeTab = ref('params')

// 表单数据
const form = reactive({
  id: '',
  name: '',
  method: 'GET',
  url: '',
  serviceType: null,
  isAvailable: 1,
  paramsJson: '',
  bodyType: 'json',
  params: [],
  headers: []
})

// 参数列表
const params = ref([])
const headers = ref([])

// 响应结果
const responseResult = ref(null)

// 服务类型选项
const serviceTypeOptions = [
  { value: 1, label: 'HTTP接口' },
  { value: 2, label: 'RPC接口' },
  { value: 3, label: '消息队列' },
  { value: 4, label: '定时任务' },
  { value: 5, label: '数据库' },
  { value: 6, label: '文件存储' },
  { value: 7, label: '缓存服务' },
  { value: 99, label: '其他' }
]

// 计算属性
const isEditMode = computed(() => !!route.query.id)
const serviceTypeName = computed(() => {
  const option = serviceTypeOptions.find(opt => opt.value === form.serviceType)
  return option ? option.label : '-'
})

// 添加参数
const addParam = () => {
  params.value.push({
    id: Date.now(),
    name: '',
    value: '',
    type: 'string',
    desc: ''
  })
}

const removeParam = (index) => {
  params.value.splice(index, 1)
}

// 添加Header
const addHeader = () => {
  headers.value.push({
    id: Date.now(),
    name: '',
    value: '',
    type: 'string',
    desc: ''
  })
}

const removeHeader = (index) => {
  headers.value.splice(index, 1)
}

// 格式化JSON
const formatJson = (data) => {
  if (!data) return ''
  try {
    return JSON.stringify(data, null, 2)
  } catch (e) {
    return String(data)
  }
}

// 发送请求
const sendRequest = async () => {
  if (!form.url || !form.url.trim()) {
    ElMessage.warning('请输入请求地址')
    return
  }

  isSending.value = true
  try {
    // 构建直连测试请求（后端 DirectTestRequestDTO：protocolType/responseFormat 为枚举名，requestBody 为 String）
    const directTestPayload = {
      protocolType: 'HTTP',
      requestType: form.method,
      address: form.url,
      requestHeaders: buildHeadersMap(),
      requestParams: buildParamsMap(),
      requestBody: form.bodyType === 'json' && form.paramsJson && form.paramsJson.trim()
        ? form.paramsJson.trim()
        : null,
      responseFormat: 'JSON',
      timeout: 30000
    }

    const res = await directTest(directTestPayload)
    responseResult.value = res.data || res
    ElMessage.success('请求发送成功')
  } catch (error) {
    console.error('请求失败', error)
    responseResult.value = {
      success: false,
      duration: 0,
      data: null,
      errorMsg: error.message || '请求失败'
    }
    ElMessage.error(error.message || '请求失败')
  } finally {
    isSending.value = false
  }
}

// 构建Headers Map
const buildHeadersMap = () => {
  const map = {}
  headers.value.forEach(h => {
    if (h.name && h.name.trim()) {
      map[h.name.trim()] = h.value || ''
    }
  })
  return map
}

// 构建Params Map
const buildParamsMap = () => {
  const map = {}
  params.value.forEach(p => {
    if (p.name && p.name.trim()) {
      map[p.name.trim()] = p.value || ''
    }
  })
  return map
}

// 保存
const handleSave = async () => {
  if (!form.name || !form.name.trim()) {
    ElMessage.warning('请输入服务名称')
    return
  }
  if (!form.url || !form.url.trim()) {
    ElMessage.warning('请输入请求地址')
    return
  }

  saveLoading.value = true
  try {
    const saveData = {
      name: form.name.trim(),
      protocolType: 1, // HTTP
      requestType: form.method,
      address: form.url.trim(),
      requestHeaders: JSON.stringify(buildHeadersMap()),
      requestParams: JSON.stringify(buildParamsMap()),
      requestBody: form.paramsJson || null,
      outputParams: form.outputParams || null,
      assertions: form.assertions || null,
      serviceType: form.serviceType,
      applicationId: route.query.applicationId,
      isAvailable: form.isAvailable
    }

    let res
    if (isEditMode.value) {
      // 更新
      res = await updateOriginalService(form.id, saveData)
    } else {
      // 新建
      res = await createOriginalService(saveData)
      form.id = res.data?.id || res.data
    }

    ElMessage.success(isEditMode.value ? '保存成功' : '创建成功')
    // 如果是新建，跳转到编辑模式
    if (!isEditMode.value && form.id) {
      router.replace({
        path: '/layout/originalservicedetail',
        query: { id: form.id, applicationId: route.query.applicationId }
      })
    }
  } catch (error) {
    console.error('保存失败', error)
    ElMessage.error(error.response?.data?.message || error.message || '保存失败')
  } finally {
    saveLoading.value = false
  }
}

// 返回
const handleBack = () => {
  router.back()
}

// 加载详情
const loadDetail = async () => {
  const id = route.query.id
  if (!id) return

  loading.value = true
  try {
    const res = await getOriginalServiceDetail(id)
    const data = res.data || res

    form.id = data.id
    form.name = data.name
    form.method = data.requestType || 'GET'
    form.url = data.address
    form.serviceType = data.serviceType
    form.isAvailable = data.isAvailable ?? 1
    form.paramsJson = data.requestBody || ''
    form.outputParams = data.outputParams || ''
    form.assertions = data.assertions || ''

    // 解析Headers
    if (data.requestHeaders) {
      try {
        const headersObj = typeof data.requestHeaders === 'string' 
          ? JSON.parse(data.requestHeaders) 
          : data.requestHeaders
        headers.value = Object.entries(headersObj).map(([name, value], idx) => ({
          id: Date.now() + idx,
          name,
          value: String(value),
          type: 'string',
          desc: ''
        }))
      } catch (e) {
        console.error('解析Headers失败', e)
      }
    }

    // 解析Params
    if (data.requestParams) {
      try {
        const paramsObj = typeof data.requestParams === 'string'
          ? JSON.parse(data.requestParams)
          : data.requestParams
        params.value = Object.entries(paramsObj).map(([name, value], idx) => ({
          id: Date.now() + idx,
          name,
          value: String(value),
          type: 'string',
          desc: ''
        }))
      } catch (e) {
        console.error('解析Params失败', e)
      }
    }
  } catch (error) {
    console.error('加载详情失败', error)
    ElMessage.error('加载详情失败')
  } finally {
    loading.value = false
  }
}

// 初始化
onMounted(() => {
  loadDetail()
})
</script>

<style lang="scss" scoped>
.service-detail-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--paper, #fafafa);
  overflow: hidden;

  .top-bar {
    padding: 12px 16px;
    border-bottom: 1px solid var(--mute, #e5e5e5);
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-shrink: 0;

    .input-area {
      display: flex;
      align-items: center;
      gap: 8px;
      flex: 1;

      .name-input {
        width: 200px;
      }

      .method-select {
        width: 100px;
      }

      .url-input {
        flex: 1;
      }
    }

    .action-group {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }

  .meta-bar {
    padding: 8px 16px;
    border-bottom: 1px solid var(--mute, #e5e5e5);
    display: flex;
    align-items: center;
    gap: 24px;
    flex-shrink: 0;
    background: var(--smoke, #f5f5f5);

    .meta-item {
      display: flex;
      align-items: center;
      gap: 8px;

      .meta-label {
        font-size: 13px;
        color: var(--ghost, #a3a3a3);
      }

      .meta-value {
        font-size: 13px;
        color: var(--ink, #0a0a0a);
      }
    }
  }

  .tester-card {
    flex: 1;
    margin: 16px;
    border-radius: 0;
    border: 1px solid var(--mute, #e5e5e5);
    overflow: hidden;

    :deep(.el-card__body) {
      padding: 0;
      height: 100%;
      display: flex;
      flex-direction: column;
    }

    .api-tabs-wrap {
      flex: 1;
      display: flex;
      flex-direction: column;

      :deep(.el-tabs__content) {
        flex: 1;
        overflow: hidden;
      }

      :deep(.el-tab-pane) {
        height: 100%;
      }
    }

    .tab-scroll-wrap {
      height: 100%;
      overflow-y: auto;
      padding: 16px;
    }

    .params-area {
      .params-header {
        font-size: 14px;
        font-weight: 500;
        color: var(--ink, #0a0a0a);
        margin-bottom: 12px;
      }
    }

    .params-table {
      .param-row {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 8px;

        &.header {
          font-size: 12px;
          color: var(--ghost, #a3a3a3);
          margin-bottom: 4px;

          .col {
            font-weight: 500;
          }
        }

        .col {
          &.name {
            width: 150px;
          }

          &.value {
            flex: 1;
          }

          &.type {
            width: 100px;
          }

          &.desc {
            width: 150px;
          }

          &.actions {
            width: 80px;
            text-align: right;
          }
        }
      }
    }

    .headers-header {
      font-size: 14px;
      font-weight: 500;
      color: var(--ink, #0a0a0a);
      margin-bottom: 12px;
    }

    .response-area {
      .response-header {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-bottom: 12px;
        padding-bottom: 8px;
        border-bottom: 1px solid var(--mute, #e5e5e5);

        .response-status {
          font-size: 12px;
          padding: 2px 8px;
          border-radius: 2px;

          &.success {
            background: #f0f9ff;
            color: #0284c7;
          }

          &.error {
            background: #fef2f2;
            color: #dc2626;
          }
        }

        .response-duration {
          font-size: 12px;
          color: var(--ghost, #a3a3a3);
          font-family: 'JetBrains Mono', monospace;
        }
      }

      .response-body {
        background: var(--smoke, #f5f5f5);
        padding: 12px;
        border-radius: 4px;
        font-family: 'JetBrains Mono', monospace;
        font-size: 13px;
        line-height: 1.6;
        overflow-x: auto;
        white-space: pre-wrap;
        word-break: break-all;
        margin: 0;
      }
    }

    .empty-response {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 200px;
      color: var(--ghost, #a3a3a3);
      font-size: 14px;
    }
  }
}
</style>
