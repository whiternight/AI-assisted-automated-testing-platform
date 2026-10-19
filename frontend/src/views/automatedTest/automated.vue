<template>
  <div class="automated-container">
    <!-- 内容区 -->
    <div class="right-content">
      <!-- 页面标题栏 -->
      <div class="page-header">
        <h2 class="page-title">自动化测试</h2>
        <div class="header-actions">
          <el-button type="primary" @click="handleRefresh" :loading="loading">刷新</el-button>
        </div>
      </div>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-form :inline="true" :model="filterForm" class="filter-form">
          <el-form-item label="应用名称">
            <el-select v-model="filterForm.applicationId" placeholder="全部应用" clearable filterable style="width:180px">
              <el-option v-for="app in allApps" :key="app.id" :label="app.name" :value="app.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="通过率">
            <el-select v-model="filterForm.passRateRange" placeholder="全部" clearable style="width:130px">
              <el-option label="全部" value="" />
              <el-option label="90% 以上" value="high" />
              <el-option label="70% ~ 90%" value="medium" />
              <el-option label="70% 以下" value="low" />
            </el-select>
          </el-form-item>
          <el-form-item label="执行人">
            <el-input v-model="filterForm.executorName" placeholder="执行人姓名" clearable style="width:140px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch" :loading="searchLoading">搜索</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 表格容器 - 用 flex:1 占满剩余空间 -->
      <div class="table-container">
        <el-table 
          ref="tableRef"
          :data="taskList" 
          v-loading="loading" 
          stripe 
          border 
          style="width:100%"
          height="100%"
        >
          <el-table-column type="index" label="序号" width="50" align="center" :index="getIndex" fixed="left" />
          <el-table-column prop="applicationName" label="应用名称" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.applicationName || '-' }}</template>
          </el-table-column>
          <el-table-column label="执行状态" width="100" align="center">
            <template #default="{ row }">
              <div class="exec-status" :class="getStatusClass(row.status)">
                <el-icon v-if="row.status === 1" class="is-loading">
                  <Loading />
                </el-icon>
                <el-icon v-else color="#67c23a">
                  <CircleCheckFilled />
                </el-icon>
                <span class="status-text">{{ getStatusText(row.status) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="totalCases" label="用例数量" width="90" align="center" />
          <el-table-column prop="passRate" label="通过率" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="getPassRateType(row.passRate)">
                {{ row.passRate ? row.passRate.toFixed(1) + '%' : '0%' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="executorName" label="执行人" width="100" align="center" />
          <el-table-column prop="startTime" label="执行时间" width="150" align="center">
            <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column prop="endTime" label="结束时间" width="150" align="center">
            <template #default="{ row }">{{ formatDateTime(row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="用时" width="85" align="center">
            <template #default="{ row }">{{ calculateDuration(row.startTime, row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right" align="center">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleViewDetail(row)">详情</el-button>
              <el-button type="danger" link size="small" @click="handleDeleteTask(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination">
          <el-pagination
            v-if="total > 0"
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            :page-sizes="[10, 20, 50, 100]"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
            :pager-count="7"
            popper-class="pagination-popper"
          />
        </div>
      </div>

    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="测试任务详情" width="900px" :close-on-click-modal="false">
      <div v-loading="detailLoading" class="detail-content">
        <el-descriptions :column="3" border class="task-info">
          <el-descriptions-item label="任务编号">{{ currentTask.taskNo }}</el-descriptions-item>
          <el-descriptions-item label="执行人">{{ currentTask.executorName }}</el-descriptions-item>
          <el-descriptions-item label="任务状态">
            <el-tag :type="getStatusType(currentTask.status)">
              {{ currentTask.statusDesc || getStatusText(currentTask.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总用例数">{{ currentTask.totalCases }}</el-descriptions-item>
          <el-descriptions-item label="成功数">{{ currentTask.successCount }}</el-descriptions-item>
          <el-descriptions-item label="失败数">{{ currentTask.failCount }}</el-descriptions-item>
          <el-descriptions-item label="通过率">
            <el-tag :type="getPassRateType(currentTask.passRate)">
              {{ currentTask.passRate ? currentTask.passRate.toFixed(2) + '%' : '0.00%' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="重试轮次">{{ currentTask.retryRound || 0 }}</el-descriptions-item>
          <el-descriptions-item label="执行环境">{{ currentTask.env || 'default' }}</el-descriptions-item>
        </el-descriptions>

        <div class="node-results" v-if="currentTask.nodeResults && currentTask.nodeResults.length > 0">
          <h4>用例执行结果</h4>
          <el-table :data="pagedNodeResults" stripe border>
            <el-table-column type="index" label="序号" width="60" align="center" :index="getNodeIndex" />
            <el-table-column prop="caseName" label="用例名称" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="link-text" @click="handleViewCaseDetailFromNode(row)">{{ row.caseName }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="address" label="URL" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.address || row.url || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag type="success">执行完成</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="success" label="结果" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.passed ? 'success' : 'danger'">
                  {{ row.passed ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="errorMsg" label="错误信息" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.errorMsg" class="error-text">{{ row.errorMsg }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="node-pagination">
            <el-pagination v-model:current-page="nodePage" v-model:page-size="nodePageSize" :page-sizes="[5, 10, 20]"
              :total="(currentTask.nodeResults || []).length" layout="total, sizes, prev, pager, next"
              @size-change="handleNodeSizeChange" @current-change="handleNodePageChange" />
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="detailDialogVisible = false">取消</el-button>
        <el-button type="warning" @click="handleRetryHistory" :loading="retryHistoryLoading">重试历史用例</el-button>
        <el-button type="primary" @click="handleRetryTask" :loading="retryLoading"
          :disabled="!canRetry">重试失败用例</el-button>
      </template>
    </el-dialog>

    <!-- 用例详情弹窗 -->
    <el-dialog v-model="caseDetailVisible" title="用例详情" width="700px" :close-on-click-modal="false" append-to-body>
      <el-descriptions :column="2" border v-loading="caseDetailLoading" v-if="!caseEditMode">
        <el-descriptions-item label="用例名称" :span="2">{{ currentCaseDetail.name }}</el-descriptions-item>
        <el-descriptions-item label="协议类型">{{ currentCaseDetail.protocolType }}</el-descriptions-item>
        <el-descriptions-item label="请求类型">{{ currentCaseDetail.requestType }}</el-descriptions-item>
        <el-descriptions-item label="请求地址" :span="2">{{ currentCaseDetail.address }}</el-descriptions-item>
        <el-descriptions-item label="所属应用">{{ currentCaseDetail.applicationName }}</el-descriptions-item>
        <el-descriptions-item label="业务域">{{ currentCaseDetail.businessDomainName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ currentCaseDetail.ownerName }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ currentCaseDetail.creatorName }}</el-descriptions-item>
        <el-descriptions-item label="环境">{{ currentCaseDetail.env || 'default' }}</el-descriptions-item>
        <el-descriptions-item label="超时时间">{{ currentCaseDetail.timeout }}ms</el-descriptions-item>
        <el-descriptions-item label="请求头" :span="2">
          <pre class="json-pre">{{ formatJson(currentCaseDetail.requestHeaders) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2">
          <pre class="json-pre">{{ formatJson(currentCaseDetail.requestParams) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="请求体" :span="2">
          <pre class="json-pre">{{ currentCaseDetail.requestBody || '-' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="响应体" :span="2">
          <pre class="json-pre">{{ formatJson(currentCaseDetail.responseBody) }}</pre>
        </el-descriptions-item>
      </el-descriptions>

      <el-form v-else :model="caseEditForm" label-width="100px" v-loading="caseDetailLoading">
        <el-form-item label="用例名称"><el-input v-model="caseEditForm.name" /></el-form-item>
        <el-form-item label="请求地址"><el-input v-model="caseEditForm.address" /></el-form-item>
        <el-form-item label="请求类型">
          <el-select v-model="caseEditForm.requestType" style="width:100%">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="请求头"><el-input v-model="caseEditForm.requestHeaders" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="请求参数"><el-input v-model="caseEditForm.requestParams" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="请求体"><el-input v-model="caseEditForm.requestBody" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="超时时间(ms)"><el-input-number v-model="caseEditForm.timeout" :min="1000" :max="120000" :step="1000" /></el-form-item>
      </el-form>

      <template #footer>
        <template v-if="!caseEditMode">
          <el-button @click="caseDetailVisible = false">关闭</el-button>
          <el-button type="primary" @click="caseEditMode = true">编辑</el-button>
        </template>
        <template v-else>
          <el-button @click="caseEditMode = false">取消</el-button>
          <el-button type="primary" @click="saveCaseEdit" :loading="caseEditLoading">保存</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, CircleCheckFilled } from '@element-plus/icons-vue'
import {
  getAutomationTaskList,
  getAutomationTaskDetail,
  retryAutomationTask,
  runAutomationTask,
  deleteAutomationTask,
  getCaseList,
  getCaseDetail,
  updateHttpCase
} from '@/api/admin'
import { loadAllApps, loadAllUsers } from '@/utils/cache'

// ========== 应用列表（下拉框） ==========
const allApps = ref([])
const appCaseMap = new Map()

const initApps = async () => {
  try {
    const apps = await loadAllApps()
    allApps.value = apps

    const caseMapResults = await Promise.allSettled(
      apps.map(app =>
        getCaseList({ applicationId: app.id, page: 1, size: 500 }).then(caseRes => {
          const data = caseRes.data || caseRes
          const records = data.records || data.list || (Array.isArray(data) ? data : [])
          const caseIds = new Set(
            records.map(c => String(c.id || c.caseId || '')).filter(v => v && v !== 'undefined' && v !== 'null')
          )
          return { appId: String(app.id), caseIds }
        })
      )
    )
    caseMapResults.forEach(result => {
      if (result.status === 'fulfilled') {
        appCaseMap.set(result.value.appId, result.value.caseIds)
      }
    })
  } catch (error) {
    console.error('加载应用列表失败', error)
    ElMessage.error('加载应用列表失败')
  }
}

// ========== 筛选表单 ==========
const filterForm = reactive({
  applicationId: '',
  passRateRange: '',
  executorName: ''
})

// ========== 表格状态 ==========
const taskList = ref([])
const loading = ref(false)
const searchLoading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableRef = ref(null)

const getIndex = (index) => (pageNum.value - 1) * pageSize.value + index + 1

// ========== 详情弹窗 ==========
const detailDialogVisible = ref(false)
const detailLoading = ref(false)
const currentTask = ref({})
const retryLoading = ref(false)
const retryHistoryLoading = ref(false)

const canRetry = computed(() => currentTask.value.failCount > 0)

// ========== 详情弹窗内用例分页 ==========
const nodePage = ref(1)
const nodePageSize = ref(5)

const pagedNodeResults = computed(() => {
  const all = currentTask.value.nodeResults || []
  const start = (nodePage.value - 1) * nodePageSize.value
  return all.slice(start, start + nodePageSize.value)
})

const getNodeIndex = (index) => (nodePage.value - 1) * nodePageSize.value + index + 1
const handleNodeSizeChange = () => { nodePage.value = 1 }
const handleNodePageChange = () => { }

// ========== 核心数据加载 ==========
const fetchTaskList = async (silent = false) => {
  if (!silent) loading.value = true
  try {
    const params = {
      page: pageNum.value,
      size: pageSize.value,
      applicationId: filterForm.applicationId || undefined,
      passRateRange: filterForm.passRateRange || undefined,
      executorName: filterForm.executorName || undefined
    }

    const res = await getAutomationTaskList(params)

    const data = res.data || res
    taskList.value = data.records || data.list || []
    total.value = Number(data.total) || 0

    await enrichTaskListWithExecutorNames()
  } catch (error) {
    if (!silent) {
      console.error('获取任务列表失败', error)
      ElMessage.error(error.response?.data?.message || error.message || '获取任务列表失败')
    }
  } finally {
    if (!silent) loading.value = false
    scheduleTaskCheck()
  }
}

const enrichTaskListWithExecutorNames = async () => {
  try {
    const userList = await loadAllUsers()
    taskList.value.forEach(task => {
      const user = userList.find(u => u.id === task.executorId)
      task.executorName = user ? (user.realName || user.username) : '未知'
    })
  } catch (error) {
    console.error('获取用户列表失败', error)
  }
}

// ========== 操作函数 ==========
const handleSearch = () => {
  pageNum.value = 1
  searchLoading.value = true
  fetchTaskList().finally(() => { searchLoading.value = false })
}

const handleReset = () => {
  filterForm.applicationId = ''
  filterForm.passRateRange = ''
  filterForm.executorName = ''
  pageNum.value = 1
  fetchTaskList()
}

const handleRefresh = () => {
  fetchTaskList()
}

const handlePageChange = () => {
  fetchTaskList()
}

const handleSizeChange = () => {
  pageNum.value = 1
  fetchTaskList()
}

// ========== 详情 & 重试 ==========
const handleViewDetail = async (row) => {
  nodePage.value = 1
  nodePageSize.value = 5
  detailDialogVisible.value = true
  detailLoading.value = true
  try {
    const res = await getAutomationTaskDetail(row.id)
    const data = res.data || res
    if (Array.isArray(data.nodeResults)) {
      const seen = new Map()
      data.nodeResults.forEach(node => seen.set(node.caseId, node))
      data.nodeResults = [...seen.values()]
    }

    if (Array.isArray(data.nodeResults) && data.nodeResults.length > 0) {
      const detailResults = await Promise.allSettled(
        data.nodeResults.map(node => getCaseDetail(node.caseId))
      )
      data.nodeResults = data.nodeResults.map((node, i) => {
        const detailRes = detailResults[i]
        let address = ''
        if (detailRes.status === 'fulfilled') {
          const caseData = detailRes.value.data || detailRes.value
          address = caseData.address || ''
        }
        return { ...node, address, url: address }
      })
    }

    currentTask.value = data

    if (!currentTask.value.executorName && currentTask.value.executorId) {
      try {
        const userList = await loadAllUsers()
        const user = userList.find(u => u.id === currentTask.value.executorId)
        if (user) currentTask.value.executorName = user.realName || user.username
      } catch (e) { /* ignore */ }
    }
  } catch (error) {
    console.error('获取任务详情失败', error)
    ElMessage.error(error.response?.data?.message || error.message || '获取任务详情失败')
  } finally {
    detailLoading.value = false
  }
}

const refreshCurrentTaskDetail = async (silent = false) => {
  const taskId = currentTask.value?.taskId || currentTask.value?.id
  if (!taskId) return

  try {
    const res = await getAutomationTaskDetail(taskId)
    const data = res.data || res
    if (!data) return

    // 保留已有用例详情中的 address/url，避免每次轮询都重新请求用例详情
    const existingNodes = new Map(
      (currentTask.value.nodeResults || []).map(node => [node.caseId, node])
    )
    if (Array.isArray(data.nodeResults)) {
      const seen = new Map()
      data.nodeResults.forEach(node => seen.set(node.caseId, node))
      data.nodeResults = [...seen.values()].map(node => {
        const existing = existingNodes.get(node.caseId)
        return existing
          ? { ...existing, ...node, address: node.address || existing.address || node.url || existing.url || '' }
          : node
      })
    }

    currentTask.value = { ...currentTask.value, ...data }

    if (!currentTask.value.executorName && currentTask.value.executorId) {
      try {
        const userList = await loadAllUsers()
        const user = userList.find(u => u.id === currentTask.value.executorId)
        if (user) currentTask.value.executorName = user.realName || user.username
      } catch (e) { /* ignore */ }
    }
  } catch (error) {
    if (!silent) {
      console.error('刷新任务详情失败', error)
      ElMessage.error(error.response?.data?.message || error.message || '刷新任务详情失败')
    }
  }
}

const scheduleDetailRefresh = () => {
  if (detailRefreshTimer) clearTimeout(detailRefreshTimer)
  if (!detailDialogVisible.value) return

  const isRunning = currentTask.value?.status === 0 || currentTask.value?.status === 1
  if (isRunning) {
    detailRefreshTimer = setTimeout(async () => {
      await refreshCurrentTaskDetail(true)
      scheduleDetailRefresh()
    }, DETAIL_REFRESH_INTERVAL)
  }
}

const handleRetryTask = async () => {
  if (!canRetry.value) { ElMessage.warning('没有失败的用例，无需重试'); return }
  retryLoading.value = true
  try {
    const res = await retryAutomationTask(currentTask.value.taskId || currentTask.value.id)
    if (res.code === 200 || res.code === 20000) {
      ElMessage.success('重试失败用例已启动')
      detailDialogVisible.value = false
      fetchTaskList()
    } else {
      ElMessage.error(res.message || '重试失败')
    }
  } catch (error) {
    console.error('重试失败用例失败', error)
    ElMessage.error(error.response?.data?.message || error.message || '重试失败用例失败')
  } finally {
    retryLoading.value = false
  }
}

const handleRetryHistory = async () => {
  const task = currentTask.value
  let caseIds = task.caseIds
  if (!Array.isArray(caseIds) || caseIds.length === 0) {
    caseIds = (task.nodeResults || []).map(node => node.caseId).filter(Boolean)
  }
  if (caseIds.length === 0) {
    ElMessage.warning('没有可执行的用例')
    return
  }

  let userInfo = sessionStorage.getItem('userInfo') || localStorage.getItem('userInfo')
  let executorId = null
  if (userInfo) {
    try {
      const user = JSON.parse(userInfo)
      executorId = parseInt(user.userId || user.id)
    } catch (e) { /* ignore */ }
  }
  if (!executorId || isNaN(executorId)) {
    ElMessage.error('无法获取当前用户ID，请重新登录')
    return
  }

  retryHistoryLoading.value = true
  try {
    const res = await runAutomationTask({
      caseIds,
      executorId,
      testSuiteName: `重试-${task.testSuiteName || new Date().toLocaleString()}`,
      env: task.env || 'default',
      timeout: 30000
    })
    if (res.code === 200 || res.code === 20000) {
      ElMessage.success('重试历史用例已启动')
      detailDialogVisible.value = false
      fetchTaskList()
    } else {
      ElMessage.error(res.message || '重试历史用例失败')
    }
  } catch (error) {
    console.error('重试历史用例失败', error)
    ElMessage.error(error.response?.data?.message || error.message || '重试历史用例失败')
  } finally {
    retryHistoryLoading.value = false
  }
}

const handleDeleteTask = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除任务「${row.testSuiteName || row.applicationName || ''}」吗？删除后不可恢复。`,
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  try {
    const res = await deleteAutomationTask(row.taskId || row.id)
    if (res.code === 200 || res.code === 20000) {
      ElMessage.success('删除成功')
      fetchTaskList()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    console.error('删除任务失败', error)
    ElMessage.error(error.response?.data?.message || error.message || '删除任务失败')
  }
}

// ========== 用例详情弹窗 ==========
const caseDetailVisible = ref(false)
const caseDetailLoading = ref(false)
const currentCaseDetail = ref({})
const caseEditMode = ref(false)
const caseEditLoading = ref(false)
const caseEditForm = reactive({
  name: '',
  address: '',
  requestType: '',
  requestHeaders: '',
  requestParams: '',
  requestBody: '',
  timeout: 30000
})

const handleViewCaseDetailFromNode = async (row) => {
  caseDetailLoading.value = true
  caseEditMode.value = false
  caseDetailVisible.value = true
  try {
    const res = await getCaseDetail(row.caseId)
    const data = res.data || res
    currentCaseDetail.value = data
  } catch (error) {
    console.error('获取用例详情失败', error)
    currentCaseDetail.value = { name: row.caseName, address: row.address || row.url || '' }
    ElMessage.error('获取用例详情失败')
  } finally {
    caseDetailLoading.value = false
  }
}

watch(caseEditMode, (val) => {
  if (val && currentCaseDetail.value) {
    const d = currentCaseDetail.value
    caseEditForm.name = d.name || ''
    caseEditForm.address = d.address || ''
    caseEditForm.requestType = d.requestType || ''
    caseEditForm.requestHeaders = typeof d.requestHeaders === 'string' ? d.requestHeaders : JSON.stringify(d.requestHeaders || '')
    caseEditForm.requestParams = typeof d.requestParams === 'string' ? d.requestParams : JSON.stringify(d.requestParams || '')
    caseEditForm.requestBody = d.requestBody || ''
    caseEditForm.timeout = d.timeout || 30000
  }
})

const saveCaseEdit = async () => {
  if (!currentCaseDetail.value.id) {
    ElMessage.error('用例ID不存在，无法保存')
    return
  }
  caseEditLoading.value = true
  try {
    let headers = caseEditForm.requestHeaders
    let params = caseEditForm.requestParams
    try { headers = JSON.parse(headers) } catch { }
    try { params = JSON.parse(params) } catch { }

    const updateData = {
      name: caseEditForm.name,
      address: caseEditForm.address,
      requestType: caseEditForm.requestType,
      requestHeaders: headers,
      requestParams: params,
      requestBody: caseEditForm.requestBody,
      timeout: caseEditForm.timeout
    }
    const res = await updateHttpCase(currentCaseDetail.value.id, updateData)
    if (res.code === 200 || res.code === 20000) {
      ElMessage.success('用例保存成功')
      caseEditMode.value = false
      const detailRes = await getCaseDetail(currentCaseDetail.value.id)
      currentCaseDetail.value = detailRes.data || detailRes
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (error) {
    console.error('保存用例失败', error)
    ElMessage.error(error.response?.data?.message || error.message || '保存失败')
  } finally {
    caseEditLoading.value = false
  }
}

// ========== 格式化工具 ==========
const formatJson = (val) => {
  if (!val) return '-'
  try {
    return typeof val === 'string' ? JSON.stringify(JSON.parse(val), null, 2) : JSON.stringify(val, null, 2)
  } catch {
    return String(val)
  }
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}

const calculateDuration = (startTime, endTime) => {
  if (!startTime || !endTime) return '-'
  const ms = new Date(endTime).getTime() - new Date(startTime).getTime()
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`
  return `${Math.floor(ms / 60000)}分${Math.round((ms % 60000) / 1000)}秒`
}

const getPassRateType = (r) => {
  if (!r) return 'info'
  if (r >= 90) return 'success'
  if (r >= 70) return 'warning'
  return 'danger'
}

const getStatusText = (s) => (s === 1 ? '执行中' : '执行成功')
const getStatusType = (s) => (s === 1 ? 'warning' : 'success')
const getStatusClass = (s) => (s === 1 ? 'status-running' : 'status-success')

// ========== 任务状态定时刷新 ==========
const TASK_CHECK_INTERVAL = 2000
let taskCheckTimer = null

// ========== 详情弹窗实时刷新 ==========
const DETAIL_REFRESH_INTERVAL = 2000
let detailRefreshTimer = null

const scheduleTaskCheck = () => {
  if (taskCheckTimer) clearTimeout(taskCheckTimer)
  const hasRunning = taskList.value.some(task => task.status === 0 || task.status === 1)
  if (hasRunning) {
    taskCheckTimer = setTimeout(() => {
      fetchTaskList(true)
    }, TASK_CHECK_INTERVAL)
  } else {
    taskCheckTimer = null
  }
}

// 监听 AI 助手任务完成事件，即时刷新列表与详情弹窗
const handleAiTaskCompleted = () => {
  fetchTaskList(true)
  if (detailDialogVisible.value) {
    refreshCurrentTaskDetail(true).then(scheduleDetailRefresh)
  }
}

// 监听 AI 助手创建任务事件，任务一旦创建立即刷新列表
const handleAiTaskCreated = (event) => {
  fetchTaskList(true)
}

watch(detailDialogVisible, (visible) => {
  if (visible) {
    scheduleDetailRefresh()
  } else if (detailRefreshTimer) {
    clearTimeout(detailRefreshTimer)
    detailRefreshTimer = null
  }
})

onMounted(() => {
  initApps()
  fetchTaskList()
  window.addEventListener('ai-assistant:task-completed', handleAiTaskCompleted)
  window.addEventListener('ai-assistant:task-created', handleAiTaskCreated)
})

onUnmounted(() => {
  if (taskCheckTimer) {
    clearTimeout(taskCheckTimer)
    taskCheckTimer = null
  }
  if (detailRefreshTimer) {
    clearTimeout(detailRefreshTimer)
    detailRefreshTimer = null
  }
  window.removeEventListener('ai-assistant:task-completed', handleAiTaskCompleted)
  window.removeEventListener('ai-assistant:task-created', handleAiTaskCreated)
})
</script>

<style lang="scss" scoped>
/* 完全按照 TestFlow 风格 */
.automated-container {
  height: 100%;
  overflow: hidden;
}

.right-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--paper, #fafafa);
  border: 1px solid var(--mute, #e5e5e5);
  overflow: hidden;
  padding-bottom: 12px;
  box-sizing: border-box;
}

.filter-bar {
  padding: 12px 16px;
  border-bottom: 1px solid var(--mute, #e5e5e5);
  flex-shrink: 0;
  background: var(--paper, #fafafa);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--mute, #e5e5e5);
  flex-shrink: 0;
  background: var(--paper, #fafafa);

  .page-title {
    margin: 0;
    font-size: 14px;
    font-weight: 500;
    color: var(--ink, #0a0a0a);
  }
}

.filter-form {
  margin-bottom: 0;
}

.table-container {
  flex: 1;
  padding: 8px 16px 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  :deep(.el-table) {
    width: 100%;
    border: 1px solid var(--mute, #e5e5e5);

    .el-table__inner-wrapper {
      width: 100%;
    }

    .el-table__header-wrapper {
      width: 100%;
    }

    .el-table__body-wrapper {
      overflow: auto !important;
      min-height: 120px;
    }

    .el-table__cell {
      padding: 4px 0 !important;
    }

    .cell {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 12px;
    }

    .el-table__header th {
      background-color: var(--paper, #fafafa);
      font-weight: 500;
      color: var(--ghost, #a3a3a3);
      padding: 6px 0 !important;
      font-size: 10px;
      text-transform: uppercase;
      letter-spacing: 0.06em;
    }
  }
}

.pagination {
  margin: 12px 0 8px;
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  min-height: 32px;
  width: 100%;

  :deep(.el-pagination) {
    font-size: 12px;

    .el-pagination__total,
    .el-pagination__sizes,
    .el-pagination__jump {
      font-size: 11px;
    }
  }
}

.exec-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  
  .status-text {
    margin-left: 2px;
  }
}

.status-running {
  color: var(--ghost, #a3a3a3);
}

.status-success {
  color: var(--ink, #0a0a0a);
}

.link-text {
  color: var(--ink, #0a0a0a);
  cursor: pointer;
  font-weight: 500;
  &:hover {
    color: var(--ghost, #a3a3a3);
  }
}

.error-text {
  color: #e53e3e;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
}

.json-pre {
  margin: 0;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: var(--ink, #0a0a0a);
  white-space: pre-wrap;
  word-break: break-all;
}

:deep(.el-table__fixed-right),
:deep(.el-table__fixed) {
  z-index: 10;
}

.detail-content {
  max-height: 60vh;
  overflow-y: auto;
}

.task-info {
  margin-bottom: 20px;
}

.node-results {
  h4 {
    margin: 16px 0 12px;
    font-size: 15px;
    font-weight: 600;
  }
  
  .node-pagination {
    margin-top: 12px;
    display: flex;
    justify-content: flex-end;
  }
}

:deep(.el-dialog) {
  border-radius: 0;
  border: 1px solid var(--mute, #e5e5e5);
}

:deep(.el-descriptions) {
  border: 1px solid var(--mute, #e5e5e5);
}

:deep(.el-descriptions__label) {
  font-size: 10px !important;
  font-weight: 500 !important;
  color: var(--ghost, #a3a3a3) !important;
  text-transform: uppercase !important;
  letter-spacing: 0.06em !important;
}
</style>