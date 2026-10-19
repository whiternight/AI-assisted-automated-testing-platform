<template>
  <div class="bpmn-list-container">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">流程设计</h2>
      </div>
      <div class="toolbar-right">
        <el-input v-model="searchName" placeholder="搜索流程名称" clearable style="width: 200px" @clear="fetchList"
          @keyup.enter="fetchList">
          <template #prefix>
            <el-icon>
              <Search />
            </el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleCreate">
          <el-icon>
            <Plus />
          </el-icon>
          新建流程
        </el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="filterStatus" placeholder="流程状态" clearable size="small" style="width: 130px" @change="fetchList">
        <el-option label="草稿" :value="0" />
        <el-option label="未部署" :value="1" />
        <el-option label="已发布" :value="2" />
        <el-option label="已停用" :value="3" />
      </el-select>
      <el-select v-model="filterAppId" placeholder="所属应用" clearable filterable size="small" style="width: 160px" @change="fetchList">
        <el-option v-for="app in allApps" :key="app.id" :label="app.name" :value="app.id" />
      </el-select>
      <el-input v-model="filterCreator" placeholder="创建人" clearable size="small" style="width: 140px" @clear="fetchList" @keyup.enter="fetchList" />
      <el-input v-model="filterOwner" placeholder="负责人" clearable size="small" style="width: 140px" @clear="fetchList" @keyup.enter="fetchList" />
      <el-date-picker v-model="filterDateRange" type="daterange" range-separator="至" start-placeholder="更新开始" end-placeholder="更新结束" size="small" style="width: 240px" value-format="YYYY-MM-DD" @change="fetchList" />
      <el-button size="small" @click="resetFilters">重置筛选</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-wrapper">
      <el-table :data="flowList" v-loading="loading" stripe border style="width: 100%" height="100%">
        <el-table-column type="index" label="序号" width="55" align="center"
          :index="(i) => (pageNum - 1) * pageSize + i + 1" fixed="left" />
        <el-table-column prop="name" label="流程名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="applicationName" label="所属应用" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.applicationName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="creatorName" label="创建人" width="100" align="center">
          <template #default="{ row }">{{ row.creatorName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" width="100" align="center">
          <template #default="{ row }">{{ row.ownerName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="90" align="center">
          <template #default="{ row }">{{ formatVersion(row.version) }}</template>
        </el-table-column>
        <el-table-column prop="nodeCount" label="节点数" width="75" align="center">
          <template #default="{ row }">{{ row.nodeCount || '-' }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ formatTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="warning" link size="small" @click="handleExecute(row)" :disabled="row.status !== 2">执行</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination v-if="total > 0" v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="[10, 20, 50]"
          :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange"
          @current-change="handlePageChange" :pager-count="7" />
      </div>
    </div>

    <!-- 执行弹窗 -->
    <el-dialog v-model="executeDialogVisible" title="执行流程" width="500px">
      <el-form :model="executeForm" label-width="100px">
        <el-form-item label="流程名称">
          <el-input :value="currentFlow?.name" disabled />
        </el-form-item>
        <el-form-item label="执行环境">
          <el-select v-model="executeForm.env" style="width: 100%">
            <el-option label="默认环境" value="default" />
            <el-option label="测试环境" value="test" />
            <el-option label="生产环境" value="production" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行模式">
          <el-radio-group v-model="executeForm.executeMode">
            <el-radio :value="1">同步</el-radio>
            <el-radio :value="2">异步</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="超时时间(ms)">
          <el-input-number v-model="executeForm.timeout" :min="5000" :max="120000" :step="5000" />
        </el-form-item>
        <el-form-item label="入参(JSON)">
          <el-input v-model="executeForm.inputParamsStr" type="textarea" :rows="4"
            placeholder='{"key": "value"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="executeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitExecute" :loading="executeLoading">执行</el-button>
      </template>
    </el-dialog>

    <!-- 执行结果弹窗 -->
    <el-dialog v-model="resultDialogVisible" title="执行结果" width="700px">
      <div v-loading="resultLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="执行编号">{{ executionResult.taskNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getExecStatusType(executionResult.status)">
              {{ getExecStatusText(executionResult.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(executionResult.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatTime(executionResult.endTime) }}</el-descriptions-item>
          <el-descriptions-item label="总耗时">{{ executionResult.totalDuration }}ms</el-descriptions-item>
        </el-descriptions>
        <div v-if="executionResult.nodeTraces && executionResult.nodeTraces.length > 0" style="margin-top: 16px;">
          <h4 style="margin-bottom: 8px;">节点执行轨迹</h4>
          <el-table :data="executionResult.nodeTraces" size="small" border>
            <el-table-column prop="activityName" label="节点名称" />
            <el-table-column prop="activityType" label="类型" width="120" />
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="{
                  COMPLETED: 'success',
                  FAILED: 'danger',
                  ACTIVE: 'primary',
                  NOT_EXECUTED: 'info',
                  SKIPPED: 'info'
                }[row.status] || 'warning'">{{ row.status === 'NOT_EXECUTED' ? '未执行' : row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="duration" label="耗时(ms)" width="100" />
          </el-table>
        </div>
        <div v-if="executionResult.errorDetail" style="margin-top: 12px;">
          <el-alert type="error" :closable="false">
            <template #title>错误详情</template>
            <pre style="white-space: pre-wrap; font-size: 12px;">{{ JSON.stringify(executionResult.errorDetail, null, 2) }}</pre>
          </el-alert>
        </div>
      </div>
      <template #footer>
        <el-button @click="resultDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新建流程弹窗（第一步：基本信息 → 草稿） -->
    <el-dialog v-model="createDialogVisible" title="新建流程" width="480px" :close-on-click-modal="false">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="90px">
        <el-form-item label="流程名称" prop="name">
          <el-input v-model="createForm.name" placeholder="请输入流程名称" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="所属应用" prop="applicationId">
          <el-select v-model="createForm.applicationId" placeholder="请选择应用" filterable style="width: 100%">
            <el-option v-for="app in allApps" :key="app.id" :label="app.name" :value="app.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreateDraft" :loading="createLoading">保存草稿并编辑</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import {
  getFlowDefinitionList,
  deleteFlowDefinition,
  updateFlowStatus,
  executeFlow,
  getFlowExecutionResult,
  saveDraft
} from '@/api/admin'
import { loadAllApps } from '@/utils/cache'

const router = useRouter()

// 状态
const loading = ref(false)
const flowList = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchName = ref('')

// 筛选条件
const filterStatus = ref(null)
const filterAppId = ref(null)
const filterCreator = ref('')
const filterOwner = ref('')
const filterDateRange = ref(null)

// 执行弹窗
const executeDialogVisible = ref(false)
const executeLoading = ref(false)
const currentFlow = ref(null)
const executeForm = reactive({
  env: 'default',
  executeMode: 1,
  timeout: 30000,
  inputParamsStr: ''
})

// 执行结果弹窗
const resultDialogVisible = ref(false)
const resultLoading = ref(false)
const executionResult = ref({})

// 应用列表
const allApps = ref([])

// 状态映射（与后端 FlowStatusEnum 对齐：0=草稿 1=未部署 2=已发布 3=已停用）
const getStatusText = (s) => ({ 0: '草稿', 1: '未部署', 2: '已发布', 3: '已停用' }[s] || '未知')
const getStatusType = (s) => ({ 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }[s] || 'info')
const getExecStatusText = (s) => ({ 0: '执行中', 1: '成功', 2: '失败', 3: '部分成功' }[s] || '未知')
const getExecStatusType = (s) => ({ 0: 'warning', 1: 'success', 2: 'danger', 3: 'warning' }[s] || 'info')

const formatTime = (t) => {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

// 版本号格式化：统一显示为语义化版本（0.0.x）
// 新流程已是 "0.0.1" 格式；历史遗留的纯整数版本（如 "2"）归一化为 "0.0.2" 显示
const formatVersion = (v) => {
  if (!v) return '0.0.1'
  const s = String(v).trim()
  // 已是三段语义化版本，直接返回
  if (/^\d+\.\d+\.\d+$/.test(s)) return s
  // 纯整数（历史遗留），归一化为 0.0.N
  if (/^\d+$/.test(s)) return `0.0.${s}`
  // 其他格式，原样返回
  return s
}

// 获取流程列表
const fetchList = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    if (searchName.value) params.name = searchName.value
    if (filterStatus.value !== null && filterStatus.value !== '') {
      params.status = filterStatus.value
    }
    if (filterAppId.value) {
      params.applicationId = filterAppId.value
    }
    if (filterCreator.value && filterCreator.value.trim()) {
      params.creatorName = filterCreator.value.trim()
    }
    if (filterOwner.value && filterOwner.value.trim()) {
      params.ownerName = filterOwner.value.trim()
    }
    if (filterDateRange.value && filterDateRange.value.length === 2) {
      params.startDate = filterDateRange.value[0]
      params.endDate = filterDateRange.value[1]
    }
    const res = await getFlowDefinitionList(params)
    console.log('[BpmnDesign] 列表响应:', res)
    // 检查后端业务状态码
    if (res && res.success === false) {
      ElMessage.warning(res.message || '后端返回错误')
      flowList.value = []
      total.value = 0
      return
    }
    // 兼容多种响应格式
    const pageData = res?.data || res || {}
    let records = pageData.records || pageData.list || (Array.isArray(pageData) ? pageData : [])

    // 前端二次筛选（兼容后端未支持的条件）
    if (filterStatus.value !== null && filterStatus.value !== '') {
      records = records.filter(r => r.status === filterStatus.value)
    }
    if (filterAppId.value) {
      records = records.filter(r => String(r.applicationId) === String(filterAppId.value))
    }
    if (filterCreator.value && filterCreator.value.trim()) {
      const kw = filterCreator.value.trim().toLowerCase()
      records = records.filter(r => (r.creatorName || '').toLowerCase().includes(kw))
    }
    if (filterOwner.value && filterOwner.value.trim()) {
      const kw = filterOwner.value.trim().toLowerCase()
      records = records.filter(r => (r.ownerName || '').toLowerCase().includes(kw))
    }
    if (filterDateRange.value && filterDateRange.value.length === 2) {
      const [start, end] = filterDateRange.value
      records = records.filter(r => {
        const t = r.updatedAt || r.createdAt
        if (!t) return true
        const dateStr = formatTime(t).substring(0, 10).replace(/\//g, '-')
        return dateStr >= start && dateStr <= end
      })
    }

    flowList.value = records
    total.value = Number(pageData.total) || records.length
  } catch (e) {
    console.error('[BpmnDesign] 获取流程列表失败:', e)
    // 不弹错误提示，避免与拦截器重复
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filterStatus.value = null
  filterAppId.value = null
  filterCreator.value = ''
  filterOwner.value = ''
  filterDateRange.value = null
  searchName.value = ''
  pageNum.value = 1
  fetchList()
}

// 获取应用列表（用于筛选，使用缓存）
const initApps = async () => {
  try {
    allApps.value = await loadAllApps()
  } catch (e) { /* ignore */ }
}

// 新建流程（第一步：弹窗收集基本信息）
const createDialogVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref(null)
const createForm = reactive({
  name: '',
  applicationId: ''
})
const createRules = {
  name: [{ required: true, message: '请输入流程名称', trigger: 'blur' }],
  applicationId: [{ required: true, message: '请选择应用', trigger: 'change' }]
}

// 新建流程
const handleCreate = () => {
  createForm.name = ''
  createForm.applicationId = ''
  createDialogVisible.value = true
}

// 提交草稿创建 → 跳转到设计器
const submitCreateDraft = async () => {
  if (!createFormRef.value) return
  try {
    await createFormRef.value.validate()
  } catch { return }

  createLoading.value = true
  try {
    const res = await saveDraft({
      name: createForm.name,
      applicationId: createForm.applicationId
    })
    const result = res.data || res
    const newId = result.id
    createDialogVisible.value = false
    ElMessage.success('草稿已创建，进入流程设计')
    router.push(`/bpmn/designer/${newId}`)
  } catch (e) {
    ElMessage.error('创建草稿失败: ' + (e.response?.data?.message || e.message))
  } finally {
    createLoading.value = false
  }
}

// 编辑流程
const handleEdit = (row) => {
  router.push(`/bpmn/designer/${row.id}`)
}


// 执行流程
const handleExecute = (row) => {
  currentFlow.value = row
  executeForm.env = 'default'
  executeForm.executeMode = 1
  executeForm.timeout = 30000
  executeForm.inputParamsStr = ''
  executeDialogVisible.value = true
}

// 提交执行
const submitExecute = async () => {
  if (!currentFlow.value?.id) return
  executeLoading.value = true
  try {
    const data = {
      flowDefinitionId: currentFlow.value.id,
      env: executeForm.env,
      executeMode: executeForm.executeMode,
      timeout: executeForm.timeout
    }
    if (executeForm.inputParamsStr) {
      try { data.inputParams = JSON.parse(executeForm.inputParamsStr) }
      catch { ElMessage.warning('入参JSON格式不正确'); executeLoading.value = false; return }
    }
    const res = await executeFlow(currentFlow.value.id, data)
    const result = res.data || res
    executeDialogVisible.value = false
    ElMessage.success('流程已启动')
    resultLoading.value = true
    resultDialogVisible.value = true
    executionResult.value = result
    if (result.processInstanceId) {
      // 同步模式直接获取结果，异步模式轮询
      if (executeForm.executeMode === 2) {
        pollExecutionResult(result.processInstanceId)
      } else {
        await fetchExecutionResult(result.processInstanceId)
      }
    }
    resultLoading.value = false
  } catch (e) {
    ElMessage.error('执行失败')
  } finally {
    executeLoading.value = false
  }
}

const fetchExecutionResult = async (pid) => {
  try {
    const res = await getFlowExecutionResult(pid)
    executionResult.value = res.data || res
  } catch { /* ignore */ }
}

const pollExecutionResult = (pid) => {
  let attempts = 0
  const timer = setInterval(async () => {
    attempts++
    if (attempts >= 20) { clearInterval(timer); return }
    try {
      const res = await getFlowExecutionResult(pid)
      const data = res.data || res
      executionResult.value = data
      if (data.status !== 0) clearInterval(timer)
    } catch { /* ignore */ }
  }, 3000)
}

// 删除流程
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除流程「${row.name}」吗？`, '确认删除', {
      type: 'warning'
    })
    await deleteFlowDefinition(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

// 分页
const handleSizeChange = (val) => {
  pageSize.value = val
  pageNum.value = 1
  fetchList()
}

const handlePageChange = (val) => {
  pageNum.value = val
  fetchList()
}

onMounted(() => {
  initApps()
  fetchList()
})
</script>

<style scoped lang="scss">
.bpmn-list-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  background: var(--paper, #fafafa);
  box-sizing: border-box;

  .toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 0;
    background: var(--paper, #fafafa);
    padding: 16px;
    border: 1px solid var(--mute, #e5e5e5);

    .page-title {
      margin: 0;
      font-size: 13px;
      font-weight: 500;
      color: var(--ink, #0a0a0a);
    }
  }

  .filter-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    padding: 8px 16px;
    background: var(--paper, #fafafa);
    border: 1px solid var(--mute, #e5e5e5);
    border-top: none;
  }

  .table-wrapper {
    flex: 1;
    background: var(--paper, #fafafa);
    border: 1px solid var(--mute, #e5e5e5);
    border-top: none;
    padding: 0 16px;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    min-height: 0;

    :deep(.el-table) {
      width: 100%;
      border: 1px solid var(--mute, #e5e5e5);
      margin-top: 8px;
      .el-table__body-wrapper { overflow: auto !important; min-height: 120px; }
      .el-table__cell { padding: 4px 0 !important; }
      .cell { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
      .el-table__header th {
        background-color: var(--paper, #fafafa); font-weight: 500; color: var(--ghost, #a3a3a3);
        padding: 6px 0 !important; font-size: 10px;
        text-transform: uppercase; letter-spacing: 0.06em;
      }
    }
  }

  .pagination {
    margin: 12px 0 8px;
    display: flex;
    justify-content: center;
    flex-shrink: 0;
    min-height: 32px;

    :deep(.el-pagination) {
      font-size: 12px;
      .el-pagination__total, .el-pagination__sizes, .el-pagination__jump { font-size: 11px; }
    }
  }

  :deep(.el-dialog) {
    border-radius: 0;
    border: 1px solid var(--mute, #e5e5e5);
  }
}
</style>
