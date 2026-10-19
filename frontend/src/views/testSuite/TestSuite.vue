<template>
  <div class="test-suite-container">
    <!-- 页面标题栏 -->
    <div class="page-header">
      <h2 class="page-title">测试集管理</h2>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-form :inline="true" :model="filterForm" class="filter-form">
        <el-form-item label="测试集名称">
          <el-input v-model="filterForm.keyword" placeholder="请输入名称关键字" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="所属应用">
          <el-select v-model="filterForm.applicationId" placeholder="请选择应用" clearable filterable style="width: 180px">
            <el-option v-for="app in allApps" :key="app.id" :label="app.name" :value="app.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterForm.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="草稿" :value="0" />
            <el-option label="保存" :value="1" />
            <el-option label="已发布" :value="2" />
            <el-option label="已禁用" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="环境">
          <el-input v-model="filterForm.env" placeholder="如 default" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :loading="searchLoading">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="filter-actions">
        <el-button type="primary" @click="openCreateDialog">新增测试集</el-button>
        <el-button type="danger" @click="handleBatchDelete" :disabled="selectedRows.length === 0">批量删除</el-button>
      </div>
    </div>

    <!-- 表格区 -->
    <div class="table-wrapper">
      <el-table
        ref="tableRef"
        :data="suiteList"
        v-loading="loading"
        stripe
        border
        style="width: 100%"
        height="100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" align="center" fixed="left" />
        <el-table-column prop="name" label="测试集名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="applicationName" label="所属应用" width="120" show-overflow-tooltip />
        <el-table-column prop="statusDesc" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getStatusType(row.status)">
              {{ row.statusDesc || getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="env" label="环境" width="90" align="center" />
        <el-table-column prop="creatorName" label="创建人" width="90" align="center" />
        <el-table-column prop="ownerName" label="负责人" width="90" align="center" />
        <el-table-column prop="flowCount" label="流程数" width="75" align="center">
          <template #default="{ row }">{{ row.flowCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="150" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="150" align="center">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="openFlowDialog(row)">流程</el-button>
            <el-button type="warning" link size="small" @click="openExecuteDialog(row)">执行</el-button>
            <el-button type="info" link size="small" @click="openHistoryDialog(row)">历史</el-button>
            <el-button
              v-if="row.status === 0 || row.status === 1"
              type="primary"
              link
              size="small"
              @click="handlePublish(row)"
              :loading="row.publishing"
            >发布</el-button>
            <el-button
              v-if="row.status === 2"
              type="danger"
              link
              size="small"
              @click="handleDisable(row)"
              :loading="row.disabling"
            >禁用</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 新增/编辑测试集弹窗 -->
    <el-dialog v-model="suiteDialogVisible" :title="isEdit ? '编辑测试集' : '新增测试集'" width="560px" :close-on-click-modal="false">
      <el-form :model="suiteForm" :rules="suiteRules" ref="suiteFormRef" label-width="90px">
        <el-form-item label="测试集名称" prop="name">
          <el-input v-model="suiteForm.name" placeholder="请输入测试集名称" maxlength="256" show-word-limit />
        </el-form-item>
        <el-form-item label="所属应用" prop="applicationId">
          <el-select v-model="suiteForm.applicationId" placeholder="请选择应用" filterable style="width: 100%">
            <el-option v-for="app in allApps" :key="app.id" :label="app.name" :value="app.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="环境">
          <el-input v-model="suiteForm.env" placeholder="默认 default" />
        </el-form-item>
        <el-form-item v-if="isEdit" label="状态">
          <el-select v-model="suiteForm.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="草稿" :value="0" />
            <el-option label="保存" :value="1" />
            <el-option label="已发布" :value="2" />
            <el-option label="已禁用" :value="3" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="suiteDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSuite" :loading="saveLoading">保存</el-button>
      </template>
    </el-dialog>

    <!-- 流程管理弹窗 -->
    <el-dialog v-model="flowDialogVisible" title="管理测试集流程" width="900px" :close-on-click-modal="false" @closed="destroySortable">
      <div class="flow-manage-header">
        <span class="suite-name">{{ currentSuite.name }}</span>
        <span class="suite-app">{{ currentSuite.applicationName }}</span>
      </div>

      <!-- 已绑定流程 -->
      <div class="flow-section">
        <div class="section-title">已绑定流程（拖拽行可调整顺序）</div>
        <el-table ref="boundFlowTableRef" :data="boundFlows" v-loading="flowLoading" stripe border size="small" style="width: 100%" row-class-name="drag-row" row-key="id">
          <el-table-column width="50" align="center">
            <template #default>
              <el-icon class="drag-handle"><Rank /></el-icon>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="序号" width="60" align="center">
            <template #default="{ row }">{{ row.sortOrder }}</template>
          </el-table-column>
          <el-table-column prop="flowName" label="流程名称" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleViewFlow(row)">{{ row.flowName }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="flowVersion" label="版本" width="90" align="center">
            <template #default="{ row }">{{ formatVersion(row.flowVersion) }}</template>
          </el-table-column>
          <el-table-column prop="preFlowIds" label="前置流程" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ formatPreFlowIds(row.preFlowIds) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" align="center" />
          <el-table-column label="操作" width="120" align="center">
            <template #default="{ row }">
              <el-button type="danger" link size="small" @click="handleRemoveFlow(row)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="flow-actions">
          <el-button type="primary" size="small" @click="saveSortOrder" :loading="sortLoading">保存排序</el-button>
          <el-button type="danger" size="small" @click="handleClearFlows" :disabled="boundFlows.length === 0">清空</el-button>
        </div>
      </div>

      <!-- 新增绑定 -->
      <div class="flow-section">
        <div class="section-title">新增绑定</div>
        <el-form :inline="true" class="bind-form">
          <el-form-item label="应用筛选" style="margin-right: 16px;">
            <el-select v-model="flowAppFilter" placeholder="全部应用" clearable filterable style="width: 180px">
              <el-option v-for="app in allApps" :key="app.id" :label="app.name" :value="app.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="搜索流程">
            <el-input v-model="flowSearchKeyword" placeholder="流程名称 / 应用名称" clearable style="width: 220px" />
          </el-form-item>
        </el-form>
        <el-form class="bind-form">
          <el-form-item label="选择流程" style="width: 100%">
            <el-select
              v-model="flowsToBind"
              multiple
              filterable
              reserve-keyword
              placeholder="跨部门、跨应用选择流程"
              style="width: 100%"
              :loading="availableFlowLoading"
            >
              <el-option
                v-for="flow in filteredAvailableFlows"
                :key="flow.id"
                :label="`[${flow.applicationName}] ${flow.name} (${formatVersion(flow.version)})`"
                :value="flow.id"
                :disabled="isFlowBound(flow.id)"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="flow-actions">
          <el-button type="primary" size="small" @click="handleBindFlows" :loading="bindLoading" :disabled="flowsToBind.length === 0">绑定流程</el-button>
        </div>
      </div>

      <template #footer>
        <el-button @click="flowDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 执行测试集弹窗 -->
    <el-dialog v-model="executeDialogVisible" title="执行测试集" width="560px" :close-on-click-modal="false">
      <el-form :model="executeForm" label-width="100px">
        <el-form-item label="测试集">
          <span class="form-static-text">{{ currentSuite.name }}</span>
        </el-form-item>
        <el-form-item label="执行环境">
          <el-input v-model="executeForm.env" placeholder="默认 default" />
        </el-form-item>
        <el-form-item label="执行模式">
          <el-radio-group v-model="executeForm.executeMode">
            <el-radio :label="1">同步串行</el-radio>
            <el-radio :label="2">异步并发</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="单流程超时">
          <el-input-number v-model="executeForm.timeout" :min="1000" :max="300000" :step="1000" style="width: 160px" />
          <span class="unit-text">ms</span>
        </el-form-item>
        <el-form-item label="全局变量">
          <el-input
            v-model="executeForm.globalVariablesText"
            type="textarea"
            :rows="4"
            placeholder='{"key":"value"}，JSON 格式，可选'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="executeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleExecute" :loading="executeLoading">开始执行</el-button>
      </template>
    </el-dialog>

    <!-- 执行结果弹窗 -->
    <el-dialog v-model="resultDialogVisible" title="执行结果" width="860px" :close-on-click-modal="false">
      <div v-loading="resultPolling" class="result-content">
        <el-descriptions :column="3" border class="result-info">
          <el-descriptions-item label="任务编号">{{ executeResult.taskNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="测试集">{{ executeResult.testSuiteName || currentSuite.name }}</el-descriptions-item>
          <el-descriptions-item label="任务状态">
            <el-tag :type="getTaskStatusType(executeResult.status)">
              {{ executeResult.statusDesc || getTaskStatusText(executeResult.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总流程数">{{ executeResult.totalFlows || 0 }}</el-descriptions-item>
          <el-descriptions-item label="成功数">{{ executeResult.successCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="失败数">{{ executeResult.failCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="阻塞数">{{ executeResult.blockedCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="通过率">
            <el-tag :type="getPassRateType(executeResult.passRate)">
              {{ executeResult.passRate ? executeResult.passRate.toFixed(2) + '%' : '0.00%' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总耗时">{{ executeResult.duration != null ? executeResult.duration + ' ms' : '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="flow-results" v-if="executeResult.flowResults && executeResult.flowResults.length > 0">
          <div class="section-title">流程执行详情</div>
          <el-table :data="executeResult.flowResults" stripe border size="small" style="width: 100%">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="flowName" label="流程名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="processInstanceId" label="实例 ID" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="getFlowResultType(row.status)">
                  {{ getFlowResultText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="duration" label="耗时" width="100" align="center">
              <template #default="{ row }">{{ row.duration != null ? row.duration + ' ms' : '-' }}</template>
            </el-table-column>
            <el-table-column prop="errorType" label="错误类型" width="120" show-overflow-tooltip />
            <el-table-column prop="errorMsg" label="错误信息" min-width="160" show-overflow-tooltip />
          </el-table>
        </div>

        <div v-if="currentExecuteMode === 2 && executeResult.status === 1" class="polling-tip">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>异步执行中，正在轮询结果...</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="closeResultDialog">关闭</el-button>
        <el-button type="primary" @click="refreshExecuteStatus" :loading="resultPolling" :disabled="!executeResult.taskId">刷新</el-button>
      </template>
    </el-dialog>

    <!-- 执行历史弹窗 -->
    <el-dialog v-model="historyDialogVisible" title="执行历史" width="900px" :close-on-click-modal="false">
      <div class="flow-manage-header">
        <span class="suite-name">{{ currentSuite.name }}</span>
        <span class="suite-app">执行历史记录</span>
      </div>
      <el-table :data="historyList" v-loading="historyLoading" stripe border size="small" style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="taskNo" label="任务编号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="testSuiteName" label="测试集" min-width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <span class="status-badge" :class="getTaskStatusBadgeClass(row)">
              {{ row.statusDesc || getTaskStatusText(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="totalFlows" label="流程数" width="70" align="center" />
        <el-table-column prop="successCount" label="成功" width="60" align="center" />
        <el-table-column prop="failCount" label="失败" width="60" align="center" />
        <el-table-column label="通过率" width="90" align="center">
          <template #default="{ row }">{{ row.passRate != null ? row.passRate.toFixed(1) + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="duration" label="耗时" width="100" align="center">
          <template #default="{ row }">{{ formatDuration(row) }}</template>
        </el-table-column>
        <el-table-column prop="startTime" label="执行时间" width="160" align="center">
          <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openHistoryDetailDialog(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination" v-if="historyTotal > 0">
        <el-pagination
          v-model:current-page="historyPageNum"
          v-model:page-size="historyPageSize"
          :page-sizes="[10, 20, 50]"
          :total="historyTotal"
          layout="total, prev, pager, next"
          @current-change="handleHistoryPageChange"
        />
      </div>
      <template #footer>
        <el-button @click="historyDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 第二层：流程列表弹窗 -->
    <el-dialog v-model="flowListDialogVisible" title="流程执行列表" width="900px" append-to-body :close-on-click-modal="false">
      <div v-loading="flowListLoading">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 12px;">
          <el-descriptions-item label="任务编号">{{ historyDetailRow.taskNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <span class="status-badge" :class="getTaskStatusBadgeClass(historyDetailRow)">
              {{ historyDetailRow.statusDesc || getTaskStatusText(historyDetailRow.status) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="总数">{{ historyDetailRow.totalFlows }}</el-descriptions-item>
          <el-descriptions-item label="成功/失败">
            <span style="color: #67c23a">{{ historyDetailRow.successCount || 0 }}</span>
            /
            <span style="color: #f56c6c">{{ historyDetailRow.failCount || 0 }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="historyFlowList" stripe border size="small" style="width: 100%">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="flowName" label="流程名称" min-width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="getFlowStatusType(row.status)">
                {{ getFlowStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="耗时" width="90" align="center">
            <template #default="{ row }">{{ formatDuration(row) }}</template>
          </el-table-column>
          <el-table-column prop="errorType" label="错误类型" width="120" align="center" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorType || '-' }}</template>
          </el-table-column>
          <el-table-column prop="errorMsg" label="错误信息" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMsg || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                type="primary"
                link
                size="small"
                :disabled="!row.flowDefinitionId"
                @click="openFlowDesignPage(row)"
              >详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="flowListDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Rank } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import {
  getTestSuiteList,
  createTestSuite,
  updateTestSuite,
  deleteTestSuite,
  batchDeleteTestSuites,
  publishTestSuite,
  disableTestSuite,
  getTestSuiteDetail,
  getTestSuiteFlows,
  bindTestSuiteFlows,
  sortTestSuiteFlows,
  removeTestSuiteFlow,
  clearTestSuiteFlows,
  executeTestSuite,
  getTestSuiteExecuteStatus,
  getTestSuiteExecuteHistory,
  getFlowDefinitionList,
  getFlowExecutionResult
} from '@/api/admin'
import { loadAllApps } from '@/utils/cache'

const router = useRouter()

// ========== 应用列表 ==========
const allApps = ref([])

const initApps = async () => {
  try {
    allApps.value = await loadAllApps()
  } catch (error) {
    console.error('加载应用列表失败', error)
  }
}

// ========== 筛选与表格 ==========
const filterForm = reactive({
  keyword: '',
  applicationId: '',
  status: null,
  env: ''
})

const suiteList = ref([])
const loading = ref(false)
const searchLoading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableRef = ref(null)
const selectedRows = ref([])

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

const fetchSuiteList = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      orderBy: 'updated_at',
      isAsc: false
    }
    if (filterForm.keyword) params.keyword = filterForm.keyword
    if (filterForm.applicationId) params.applicationId = filterForm.applicationId
    if (filterForm.status !== null && filterForm.status !== '') params.status = filterForm.status
    if (filterForm.env) params.env = filterForm.env

    const res = await getTestSuiteList(params)
    const data = res.data || res
    suiteList.value = data.records || data.list || []
    total.value = Number(data.total) || 0
  } catch (error) {
    console.error('获取测试集列表失败', error)
    ElMessage.error(error.response?.data?.message || error.message || '获取测试集列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pageNum.value = 1
  searchLoading.value = true
  fetchSuiteList().finally(() => { searchLoading.value = false })
}

const resetFilter = () => {
  filterForm.keyword = ''
  filterForm.applicationId = ''
  filterForm.status = null
  filterForm.env = ''
  handleSearch()
}

const handlePageChange = () => fetchSuiteList()
const handleSizeChange = () => {
  pageNum.value = 1
  fetchSuiteList()
}

// ========== 新增/编辑弹窗 ==========
const suiteDialogVisible = ref(false)
const isEdit = ref(false)
const saveLoading = ref(false)
const suiteFormRef = ref(null)
const suiteForm = reactive({
  id: '',
  name: '',
  applicationId: '',
  env: 'default',
  status: 0
})

const suiteRules = {
  name: [{ required: true, message: '请输入测试集名称', trigger: 'blur' }],
  applicationId: [{ required: true, message: '请选择所属应用', trigger: 'change' }]
}

const resetSuiteForm = () => {
  suiteForm.id = ''
  suiteForm.name = ''
  suiteForm.applicationId = ''
  suiteForm.env = 'default'
  suiteForm.status = 0
}

const openCreateDialog = () => {
  isEdit.value = false
  resetSuiteForm()
  suiteDialogVisible.value = true
  nextTick(() => {
    suiteFormRef.value?.clearValidate()
  })
}

const openEditDialog = async (row) => {
  isEdit.value = true
  resetSuiteForm()
  try {
    const res = await getTestSuiteDetail(row.id)
    const data = res.data || res
    Object.assign(suiteForm, {
      id: data.id,
      name: data.name,
      applicationId: data.applicationId,
      env: data.env || 'default',
      status: data.status ?? 0
    })
    suiteDialogVisible.value = true
    nextTick(() => {
      suiteFormRef.value?.clearValidate()
    })
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '获取测试集详情失败')
  }
}

const saveSuite = async () => {
  try {
    await suiteFormRef.value.validate()
  } catch {
    return
  }

  saveLoading.value = true
  try {
    const payload = {
      name: suiteForm.name,
      applicationId: suiteForm.applicationId,
      env: suiteForm.env || 'default'
    }
    if (isEdit.value) {
      payload.id = suiteForm.id
      if (suiteForm.status !== null && suiteForm.status !== undefined) {
        payload.status = suiteForm.status
      }
      const res = await updateTestSuite(payload)
      if (res.code === 200 || res.code === 20000) {
        ElMessage.success('更新成功')
        suiteDialogVisible.value = false
        fetchSuiteList()
      } else {
        ElMessage.error(res.message || '更新失败')
      }
    } else {
      const res = await createTestSuite(payload)
      if (res.code === 200 || res.code === 20000) {
        ElMessage.success('创建成功')
        suiteDialogVisible.value = false
        fetchSuiteList()
      } else {
        ElMessage.error(res.message || '创建失败')
      }
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '保存失败')
  } finally {
    saveLoading.value = false
  }
}

// ========== 发布/禁用/删除 ==========
const handlePublish = async (row) => {
  try {
    await ElMessageBox.confirm(`确定发布测试集「${row.name}」吗？`, '确认发布', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    })
  } catch {
    return
  }
  row.publishing = true
  try {
    const res = await publishTestSuite(row.id)
    if (res.code === 200 || res.code === 20000) {
      ElMessage.success('发布成功')
      fetchSuiteList()
    } else {
      ElMessage.error(res.message || '发布失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '发布失败')
  } finally {
    row.publishing = false
  }
}

const handleDisable = async (row) => {
  try {
    await ElMessageBox.confirm(`确定禁用测试集「${row.name}」吗？`, '确认禁用', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  row.disabling = true
  try {
    const res = await disableTestSuite(row.id)
    if (res.code === 200 || res.code === 20000) {
      ElMessage.success('禁用成功')
      fetchSuiteList()
    } else {
      ElMessage.error(res.message || '禁用失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '禁用失败')
  } finally {
    row.disabling = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除测试集「${row.name}」吗？删除后不可恢复。`, '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    const res = await deleteTestSuite(row.id)
    if (res.code === 200 || res.code === 20000) {
      ElMessage.success('删除成功')
      await fetchSuiteList()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '删除失败')
  }
}

const handleBatchDelete = async () => {
  const ids = selectedRows.value.map(row => row.id)
  if (ids.length === 0) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${ids.length} 个测试集吗？`, '确认批量删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    const res = await batchDeleteTestSuites(ids)
    if (res.code === 200 || res.code === 20000) {
      ElMessage.success('批量删除成功')
      selectedRows.value = []
      await fetchSuiteList()
    } else {
      ElMessage.error(res.message || '批量删除失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '批量删除失败')
  }
}

// ========== 流程管理弹窗 ==========
const flowDialogVisible = ref(false)
const flowLoading = ref(false)
const boundFlowTableRef = ref(null)
const currentSuite = reactive({ id: '', name: '', applicationId: '', applicationName: '' })
const boundFlows = ref([])
const availableFlows = ref([])
const availableFlowLoading = ref(false)
const flowSearchKeyword = ref('')
const flowAppFilter = ref('')
const flowsToBind = ref([])
const bindLoading = ref(false)
const sortLoading = ref(false)
let sortableInstance = null

const openFlowDialog = async (row) => {
  Object.assign(currentSuite, {
    id: row.id,
    name: row.name,
    applicationId: row.applicationId,
    applicationName: row.applicationName
  })
  flowSearchKeyword.value = ''
  flowAppFilter.value = ''
  flowsToBind.value = []
  flowDialogVisible.value = true
  await loadBoundFlows()
  await loadAvailableFlows()
  nextTick(() => initSortable())
}

const loadBoundFlows = async () => {
  flowLoading.value = true
  try {
    const res = await getTestSuiteFlows(currentSuite.id)
    const data = res.data || res
    const list = Array.isArray(data) ? data : []
    boundFlows.value = list.map((flow, index) => ({ ...flow, sortOrder: index + 1 }))
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '获取已绑定流程失败')
  } finally {
    flowLoading.value = false
  }
}

const initSortable = () => {
  destroySortable()
  const tbody = boundFlowTableRef.value?.$el.querySelector('.el-table__body tbody')
  if (!tbody || boundFlows.value.length < 2) return
  sortableInstance = new Sortable(tbody, {
    handle: '.drag-handle',
    animation: 150,
    onEnd: (evt) => {
      const { newIndex, oldIndex } = evt
      if (newIndex === oldIndex) return
      const item = boundFlows.value.splice(oldIndex, 1)[0]
      boundFlows.value.splice(newIndex, 0, item)
      // 重新构建新数组（生成新的对象引用），强制 Vue 重新渲染表格
      boundFlows.value = boundFlows.value.map((flow, index) => ({
        ...flow,
        sortOrder: index + 1
      }))
    }
  })
}

const destroySortable = () => {
  if (sortableInstance) {
    sortableInstance.destroy()
    sortableInstance = null
  }
}

const loadAvailableFlows = async () => {
  availableFlowLoading.value = true
  try {
    // 跨部门、跨应用加载全部流程，不限制 applicationId
    const params = {
      pageNum: 1,
      pageSize: 500
    }
    const res = await getFlowDefinitionList(params)
    const data = res.data || res
    const records = data.records || data.list || []
    availableFlows.value = records.map(item => ({
      id: item.id,
      name: item.name,
      version: item.version,
      applicationId: item.applicationId ? String(item.applicationId) : '',
      applicationName: item.applicationName || '未分配应用'
    }))
  } catch (error) {
    console.error('加载流程列表失败', error)
  } finally {
    availableFlowLoading.value = false
  }
}

const filteredAvailableFlows = computed(() => {
  const keyword = flowSearchKeyword.value.trim().toLowerCase()
  const appId = flowAppFilter.value
  return availableFlows.value.filter(flow => {
    // 应用筛选
    if (appId && flow.applicationId !== String(appId)) return false
    // 关键字搜索（流程名称 / 应用名称）
    if (keyword) {
      const matchName = flow.name.toLowerCase().includes(keyword)
      const matchApp = (flow.applicationName || '').toLowerCase().includes(keyword)
      if (!matchName && !matchApp) return false
    }
    return true
  })
})

const isFlowBound = (flowId) => {
  return boundFlows.value.some(f => f.flowDefinitionId === flowId)
}

const handleViewFlow = (row) => {
  if (!row.flowDefinitionId) {
    ElMessage.warning('流程定义 ID 不存在')
    return
  }
  router.push(`/bpmn/designer/${row.flowDefinitionId}`)
}

const formatPreFlowIds = (ids) => {
  if (!Array.isArray(ids) || ids.length === 0) return '-'
  return ids.join(', ')
}

// 版本号格式化：统一显示为语义化版本（0.0.x）
const formatVersion = (v) => {
  if (!v) return '0.0.1'
  const s = String(v).trim()
  if (/^\d+\.\d+\.\d+$/.test(s)) return s
  if (/^\d+$/.test(s)) return `0.0.${s}`
  return s
}

const handleBindFlows = async () => {
  if (flowsToBind.value.length === 0) return
  bindLoading.value = true
  try {
    const payload = {
      testSuiteId: currentSuite.id,
      flows: flowsToBind.value.map(id => ({ flowDefinitionId: id }))
    }
    const res = await bindTestSuiteFlows(payload)
    if (res.code === 200 || res.code === 20000) {
      ElMessage.success(`成功绑定 ${res.data || flowsToBind.value.length} 个流程`)
      flowsToBind.value = []
      await loadBoundFlows()
      nextTick(() => initSortable())
      await fetchSuiteList()
    } else {
      ElMessage.error(res.message || '绑定失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '绑定失败')
  } finally {
    bindLoading.value = false
  }
}

const handleRemoveFlow = async (row) => {
  try {
    await ElMessageBox.confirm(`确定从测试集中移除流程「${row.flowName}」吗？`, '确认移除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    const res = await removeTestSuiteFlow(currentSuite.id, row.id)
    if (res.code === 200 || res.code === 20000) {
      ElMessage.success('移除成功')
      await loadBoundFlows()
      nextTick(() => initSortable())
      await fetchSuiteList()
    } else {
      ElMessage.error(res.message || '移除失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '移除失败')
  }
}

const saveSortOrder = async () => {
  sortLoading.value = true
  try {
    const payload = {
      testSuiteId: currentSuite.id,
      sorts: boundFlows.value.map(item => ({
        id: item.id,
        sortOrder: item.sortOrder
      }))
    }
    const res = await sortTestSuiteFlows(payload)
    if (res.code === 200 || res.code === 20000) {
      ElMessage.success('排序保存成功')
      await loadBoundFlows()
    } else {
      ElMessage.error(res.message || '排序保存失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '排序保存失败')
  } finally {
    sortLoading.value = false
  }
}

const handleClearFlows = async () => {
  try {
    await ElMessageBox.confirm('确定清空该测试集关联的所有流程吗？', '确认清空', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    const res = await clearTestSuiteFlows(currentSuite.id)
    if (res.code === 200 || res.code === 20000) {
      ElMessage.success('清空成功')
      await loadBoundFlows()
      destroySortable()
      await fetchSuiteList()
    } else {
      ElMessage.error(res.message || '清空失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '清空失败')
  }
}

// ========== 执行测试集弹窗 ==========
const executeDialogVisible = ref(false)
const executeLoading = ref(false)
const executeForm = reactive({
  env: 'default',
  executeMode: 1,
  timeout: 30000,
  globalVariablesText: ''
})

const openExecuteDialog = (row) => {
  Object.assign(currentSuite, {
    id: row.id,
    name: row.name,
    applicationId: row.applicationId,
    applicationName: row.applicationName
  })
  executeForm.env = row.env || 'default'
  executeForm.executeMode = 1
  executeForm.timeout = 30000
  executeForm.globalVariablesText = ''
  executeDialogVisible.value = true
}

const handleExecute = async () => {
  let globalVariables = {}
  if (executeForm.globalVariablesText.trim()) {
    try {
      globalVariables = JSON.parse(executeForm.globalVariablesText)
    } catch {
      ElMessage.error('全局变量 JSON 格式不正确')
      return
    }
  }

  executeLoading.value = true
  try {
    const payload = {
      testSuiteId: currentSuite.id,
      env: executeForm.env || 'default',
      executeMode: executeForm.executeMode,
      timeout: executeForm.timeout,
      globalVariables
    }
    const res = await executeTestSuite(payload)
    if (res.code === 200 || res.code === 20000) {
      ElMessage.success('测试集执行已启动')
      executeDialogVisible.value = false
      const data = res.data || res
      showExecuteResult(data)
    } else {
      ElMessage.error(res.message || '执行失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '执行失败')
  } finally {
    executeLoading.value = false
  }
}

// ========== 执行结果弹窗与轮询 ==========
const resultDialogVisible = ref(false)
const resultPolling = ref(false)
const currentExecuteMode = ref(1)
const executeResult = reactive({
  taskId: '',
  taskNo: '',
  testSuiteId: '',
  testSuiteName: '',
  status: 0,
  statusDesc: '',
  totalFlows: 0,
  successCount: 0,
  failCount: 0,
  blockedCount: 0,
  passRate: 0,
  duration: 0,
  flowResults: []
})

let pollTimer = null

const showExecuteResult = (data) => {
  currentExecuteMode.value = executeForm.executeMode
  Object.assign(executeResult, {
    taskId: data.taskId || '',
    taskNo: data.taskNo || '',
    testSuiteId: data.testSuiteId || '',
    testSuiteName: data.testSuiteName || '',
    status: data.status ?? 0,
    statusDesc: data.statusDesc || '',
    totalFlows: data.totalFlows || 0,
    successCount: data.successCount || 0,
    failCount: data.failCount || 0,
    blockedCount: data.blockedCount || 0,
    passRate: data.passRate || 0,
    duration: data.duration || 0,
    flowResults: data.flowResults || []
  })
  resultDialogVisible.value = true
  if (currentExecuteMode.value === 2 && executeResult.status === 1 && executeResult.taskId) {
    startPolling()
  }
}

const startPolling = () => {
  stopPolling()
  const poll = async () => {
    if (!executeResult.taskId || !resultDialogVisible.value) return
    resultPolling.value = true
    try {
      const res = await getTestSuiteExecuteStatus(executeResult.taskId)
      if (res.code === 200 || res.code === 20000) {
        const data = res.data || res
        Object.assign(executeResult, {
          status: data.status ?? executeResult.status,
          statusDesc: data.statusDesc || executeResult.statusDesc,
          totalFlows: data.totalFlows || 0,
          successCount: data.successCount || 0,
          failCount: data.failCount || 0,
          blockedCount: data.blockedCount || 0,
          passRate: data.passRate || 0,
          duration: data.duration || 0,
          flowResults: data.flowResults || []
        })
        if (data.status === 1) {
          pollTimer = setTimeout(poll, 3000)
        }
      }
    } catch (error) {
      console.error('轮询执行状态失败', error)
    } finally {
      resultPolling.value = false
    }
  }
  pollTimer = setTimeout(poll, 3000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

const closeResultDialog = () => {
  stopPolling()
  resultDialogVisible.value = false
}

const refreshExecuteStatus = async () => {
  if (!executeResult.taskId) return
  resultPolling.value = true
  try {
    const res = await getTestSuiteExecuteStatus(executeResult.taskId)
    if (res.code === 200 || res.code === 20000) {
      const data = res.data || res
      Object.assign(executeResult, {
        status: data.status ?? executeResult.status,
        statusDesc: data.statusDesc || executeResult.statusDesc,
        totalFlows: data.totalFlows || 0,
        successCount: data.successCount || 0,
        failCount: data.failCount || 0,
        blockedCount: data.blockedCount || 0,
        passRate: data.passRate || 0,
        duration: data.duration || 0,
        flowResults: data.flowResults || []
      })
      if (currentExecuteMode.value === 2 && executeResult.status === 1) {
        startPolling()
      }
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '刷新失败')
  } finally {
    resultPolling.value = false
  }
}

// ========== 执行历史 ==========
const historyDialogVisible = ref(false)
const historyList = ref([])
const historyLoading = ref(false)
const historyPageNum = ref(1)
const historyPageSize = ref(10)
const historyTotal = ref(0)

const openHistoryDialog = (row) => {
  currentSuite.value = row
  historyDialogVisible.value = true
  historyPageNum.value = 1
  fetchHistoryList()
}

// 所有测试集批次（聚合后）
const historyAllBatches = ref([])

const fetchHistoryList = async () => {
  historyLoading.value = true
  try {
    // 1. 获取所有流程记录（一次性获取，不分页）
    const params = {
      pageNum: 1,
      pageSize: 10000 // 一次性获取所有记录
    }
    if (currentSuite.value?.id) params.testSuiteId = currentSuite.value.id

    const res = await getTestSuiteExecuteHistory(params)
    const data = res.data || res
    const allRecords = data.records || data.list || []

    // 2. 按 testSuiteId + 时间窗口（30秒）聚合成测试集批次
    historyAllBatches.value = aggregateIntoBatches(allRecords)

    // 3. 前端分页
    historyTotal.value = historyAllBatches.value.length
    const start = (historyPageNum.value - 1) * historyPageSize.value
    historyList.value = historyAllBatches.value.slice(start, start + historyPageSize.value)
  } catch (error) {
    console.error('获取执行历史失败', error)
    ElMessage.error(error.response?.data?.message || '获取执行历史失败')
  } finally {
    historyLoading.value = false
  }
}

// 前端分页切换
const handleHistoryPageChange = (page) => {
  historyPageNum.value = page
  const start = (page - 1) * historyPageSize.value
  historyList.value = historyAllBatches.value.slice(start, start + historyPageSize.value)
}

// 按时间窗口将流程执行记录聚合为测试集执行批次
const aggregateIntoBatches = (records) => {
  if (!records || records.length === 0) return []

  // 按 startTime 降序排列（最新的在前）
  const sorted = [...records].sort((a, b) => {
    const ta = new Date(a.startTime || '').getTime() || 0
    const tb = new Date(b.startTime || '').getTime() || 0
    return tb - ta
  })

  const batches = []
  let currentBatch = []
  let lastStartTime = null
  const BATCH_INTERVAL_MS = 15 * 1000 // 15 秒阈值

  for (const record of sorted) {
    const startTime = new Date(record.startTime || '').getTime() || 0
    if (lastStartTime) {
      // 相邻记录 startTime 间隔超过阈值，开启新批次
      if (Math.abs(startTime - lastStartTime) > BATCH_INTERVAL_MS) {
        if (currentBatch.length > 0) batches.push(buildBatchFromRecords(currentBatch))
        currentBatch = []
      }
    }
    currentBatch.push(record)
    lastStartTime = startTime
  }
  if (currentBatch.length > 0) batches.push(buildBatchFromRecords(currentBatch))

  return batches
}

// 将一组流程执行记录聚合成一条测试集批次记录
const buildBatchFromRecords = (records) => {
  const totalFlows = records.length
  const successCount = records.filter(f => f.status === 1).length
  const failCount = records.filter(f => f.status === 2).length
  const blockedCount = records.filter(f => f.status === 3).length

  // 测试集状态：统一显示"成功"（因为每个流程都执行了）
  let overallStatusDesc = '成功'

  const firstFlow = records[0]
  const startTime = firstFlow?.startTime

  // 耗时 = 所有流程 duration 之和
  let duration = null
  const sum = records.reduce((acc, f) => {
    // 从 flowResults[0] 或 f 本身获取 duration
    const flowDuration = f.flowResults?.[0]?.duration || f.duration
    return acc + (Number(flowDuration) || 0)
  }, 0)
  if (sum > 0) duration = sum

  // endTime：取所有流程中最晚的 endTime（仅用于展示）
  const maxEndTime = records.reduce((max, f) => {
    const t = new Date(f.endTime || f.startTime || '').getTime() || 0
    return t > max ? t : max
  }, 0)
  const endTime = maxEndTime > 0 ? new Date(maxEndTime).toISOString() : null

  const passRate = totalFlows > 0 ? (successCount / totalFlows * 100) : 0

  // 构造 flowResults（第二层详情用）
  // 每条记录的 flowResults 数组包含一个流程的信息
  const flowResults = records.map(f => {
    const flowInfo = f.flowResults?.[0] || {}
    return {
      flowDefinitionId: flowInfo.flowDefinitionId || f.flowDefinitionId,
      flowName: flowInfo.flowName || f.flowName,
      processInstanceId: flowInfo.processInstanceId,
      status: flowInfo.status || f.status,
      statusDesc: flowInfo.statusDesc || f.statusDesc,
      duration: flowInfo.duration || f.duration,
      startTime: flowInfo.startTime || f.startTime,
      endTime: flowInfo.endTime || f.endTime,
      errorMsg: flowInfo.errorMsg
    }
  })

  return {
    taskId: firstFlow?.taskId,
    taskNo: firstFlow?.taskNo,
    testSuiteId: firstFlow?.testSuiteId,
    testSuiteName: firstFlow?.testSuiteName || currentSuite.value?.name,
    status: firstFlow?.status,
    statusDesc: overallStatusDesc,
    totalFlows,
    successCount,
    failCount,
    blockedCount,
    passRate,
    duration,
    startTime,
    endTime,
    flowResults
  }
}

// ========== 历史详情：流程列表（第二层）==========
const flowListDialogVisible = ref(false)
const flowListLoading = ref(false)
const historyDetailRow = ref({})
const historyFlowList = ref([])

const getFlowStatusType = (status) => {
  // 0=执行中，1=成功，2=失败，3=阻塞
  const map = { 0: 'primary', 1: 'success', 2: 'danger', 3: 'warning' }
  return map[status] || 'info'
}
const getFlowStatusText = (status) => {
  const map = { 0: '执行中', 1: '成功', 2: '失败', 3: '阻塞' }
  return map[status] ?? (status != null ? status : '-')
}

const openHistoryDetailDialog = async (row) => {
  historyDetailRow.value = row
  historyFlowList.value = []
  flowListDialogVisible.value = true
  // 历史接口返回的 TestSuiteExecuteResultVO 已包含 flowResults
  if (row.flowResults && row.flowResults.length > 0) {
    historyFlowList.value = row.flowResults
    await enrichFlowResultsWithTiming(historyFlowList.value)
    return
  }
  // 兜底：若历史记录里 flowResults 为空，则按 taskId 查询执行状态
  flowListLoading.value = true
  try {
    const res = await getTestSuiteExecuteStatus(row.taskId)
    const data = res.data || res
    historyFlowList.value = data.flowResults || []
    // 回填到 historyDetailRow 以便头部信息显示
    historyDetailRow.value = { ...row, ...data }
    await enrichFlowResultsWithTiming(historyFlowList.value)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '获取流程列表失败')
  } finally {
    flowListLoading.value = false
  }
}

// 为流程列表补充 startTime/endTime（从流程执行详情接口获取）
const enrichFlowResultsWithTiming = async (flowList) => {
  if (!flowList || flowList.length === 0) return
  flowListLoading.value = true
  try {
    const promises = flowList.map(async (flow) => {
      if (!flow.processInstanceId) return flow
      try {
        const res = await getFlowExecutionResult(flow.processInstanceId)
        const detail = res.data || res
        // 补充 startTime/endTime 以便兜底计算耗时
        return {
          ...flow,
          startTime: detail.startTime,
          endTime: detail.endTime,
          duration: flow.duration ?? detail.duration
        }
      } catch {
        return flow
      }
    })
    historyFlowList.value = await Promise.all(promises)
  } catch (error) {
    console.error('补充流程时间信息失败', error)
  } finally {
    flowListLoading.value = false
  }
}

// 点击流程"详情"：跳转到该流程的编排页面，并标记来源为测试集
const openFlowDesignPage = (row) => {
  if (!row.flowDefinitionId) {
    ElMessage.warning('该流程缺少流程定义 ID，无法跳转')
    return
  }
  // 关闭弹窗，避免返回时遮挡
  flowListDialogVisible.value = false
  router.push({
    path: `/bpmn/designer/${row.flowDefinitionId}`,
    query: { from: 'testSuite' }
  })
}

// ========== 格式化工具 ==========
const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

// 解析 LocalDateTime 字符串（兼容 ISO 和空格分隔格式）
const parseDateTime = (s) => {
  if (!s) return null
  if (s instanceof Date) return isNaN(s.getTime()) ? null : s
  const d = new Date(String(s).replace(' ', 'T'))
  return isNaN(d.getTime()) ? null : d
}

// 耗时格式化：优先用后端 duration，否则用 startTime/endTime 兜底计算
const formatDuration = (row) => {
  if (row == null) return '-'
  if (row.duration != null && row.duration !== '' && !isNaN(Number(row.duration))) {
    return Number(row.duration) + ' ms'
  }
  const start = parseDateTime(row.startTime)
  const end = parseDateTime(row.endTime)
  if (start && end) {
    const diff = end.getTime() - start.getTime()
    if (diff >= 0) return diff + ' ms'
  }
  return '-'
}

const getStatusText = (status) => {
  const map = { 0: '草稿', 1: '保存', 2: '已发布', 3: '已禁用' }
  return map[status] || '未知'
}

const getStatusType = (status) => {
  const map = { 0: 'info', 1: '', 2: 'success', 3: 'danger' }
  return map[status] || 'info'
}

const getTaskStatusText = (status) => {
  const map = { 0: '待执行', 1: '执行中', 2: '成功', 3: '部分成功', 4: '失败', 5: '阻塞' }
  return map[status] || '未知'
}

const getTaskStatusType = (status) => {
  const map = { 0: 'info', 1: 'warning', 2: 'success', 3: 'warning', 4: 'danger', 5: 'info' }
  return map[status] || 'info'
}

// 状态徽章 class：根据 statusDesc 判断颜色
const getTaskStatusBadgeClass = (row) => {
  const statusDesc = row?.statusDesc || ''
  if (statusDesc === '成功') return 'status-badge--success'  // 绿色
  if (statusDesc === '失败') return 'status-badge--danger'   // 红色
  if (statusDesc === '执行中') return 'status-badge--running' // 蓝色
  if (statusDesc === '部分成功') return 'status-badge--warning' // 橙色
  if (statusDesc === '阻塞') return 'status-badge--default'  // 灰色
  return 'status-badge--default' // 默认灰色
}

const getFlowResultText = (status) => {
  const map = { 0: '执行中', 1: '成功', 2: '失败', 3: '阻塞' }
  return map[status] || '未知'
}

const getFlowResultType = (status) => {
  const map = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info' }
  return map[status] || 'info'
}

const getPassRateType = (rate) => {
  if (!rate) return 'info'
  if (rate >= 90) return 'success'
  if (rate >= 70) return 'warning'
  return 'danger'
}

onMounted(() => {
  initApps()
  fetchSuiteList()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style lang="scss" scoped>
.test-suite-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  background: var(--paper, #fafafa);
  box-sizing: border-box;
}

// 状态徽章：替代 el-tag，硬编码颜色完全脱离项目主题影响
.status-badge {
  display: inline-block;
  padding: 0 8px;
  height: 22px;
  line-height: 20px;
  font-size: 12px;
  border-radius: 4px;
  border: 1px solid;
  background: #ffffff;
  box-sizing: border-box;
}
.status-badge--success {
  color: #67c23a;
  border-color: #c2e7b0;
  background: #f0f9eb;
}
.status-badge--danger {
  color: #f56c6c;
  border-color: #fbc4c4;
  background: #fef0f0;
}
.status-badge--warning {
  color: #e6a23c;
  border-color: #f5dab1;
  background: #fdf6ec;
}
.status-badge--running {
  color: #409eff;
  border-color: #b3d8ff;
  background: #ecf5ff;
}
.status-badge--default {
  color: #909399;
  border-color: #d3d4d6;
  background: #f4f4f5;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border: 1px solid var(--mute, #e5e5e5);
  border-bottom: none;
  background: var(--paper, #fafafa);

  .page-title {
    margin: 0;
    font-size: 14px;
    font-weight: 500;
    color: var(--ink, #0a0a0a);
  }
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-start;
  padding: 12px 16px;
  border: 1px solid var(--mute, #e5e5e5);
  background: var(--paper, #fafafa);
  gap: 12px;

  .filter-form {
    margin-bottom: 0;
  }

  .filter-actions {
    display: flex;
    gap: 8px;
    flex-shrink: 0;
  }
}

.table-wrapper {
  flex: 1;
  background: var(--paper, #fafafa);
  padding: 0 16px;
  border: 1px solid var(--mute, #e5e5e5);
  border-top: none;
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
}

:deep(.el-dialog) {
  border-radius: 0;
  border: 1px solid var(--mute, #e5e5e5);
}

.flow-manage-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;

  .suite-name {
    font-weight: 500;
    color: var(--ink, #0a0a0a);
  }

  .suite-app {
    color: var(--ghost, #a3a3a3);
    font-size: 12px;
  }
}

.flow-section {
  margin-bottom: 20px;

  .section-title {
    font-size: 12px;
    font-weight: 500;
    color: var(--ghost, #a3a3a3);
    text-transform: uppercase;
    letter-spacing: 0.06em;
    margin-bottom: 10px;
  }
}

.drag-handle {
  cursor: move;
  color: var(--ghost, #a3a3a3);

  &:hover {
    color: var(--ink, #0a0a0a);
  }
}

:deep(.drag-row) {
  cursor: default;
}

.flow-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.bind-form {
  :deep(.el-form-item) {
    margin-bottom: 0;
    width: 100%;
  }
}

.form-static-text {
  font-weight: 500;
  color: var(--ink, #0a0a0a);
}

.unit-text {
  margin-left: 8px;
  color: var(--ghost, #a3a3a3);
  font-size: 12px;
}

.result-info {
  margin-bottom: 16px;
}

.flow-results {
  .section-title {
    font-size: 12px;
    font-weight: 500;
    color: var(--ghost, #a3a3a3);
    text-transform: uppercase;
    letter-spacing: 0.06em;
    margin-bottom: 10px;
  }
}

.polling-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
  color: var(--ghost, #a3a3a3);
  font-size: 12px;
}
</style>