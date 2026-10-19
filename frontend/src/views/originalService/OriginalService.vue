<template>
  <div class="original-service-container">
    <!-- 左侧部门树 -->
    <div class="left-tree">
      <div class="tree-header">
        <h3>部门</h3>
      </div>
      <div class="tree-content">
        <el-tree ref="treeRef" :data="treeNodes" :props="defaultProps" node-key="id" :default-expand-all="true"
          :highlight-current="true" :expand-on-click-node="false" @node-click="onTreeNodeClick">
          <template #default="{ node, data }">
            <span class="custom-tree-node">
              <span>{{ node.label }}</span>
            </span>
          </template>
        </el-tree>
      </div>
    </div>

    <!-- 右侧内容区 -->
    <div class="right-content">
      <!-- 顶部栏 -->
      <div class="top-bar">
        <div class="app-title">
          <h3>{{ currentAppName || '原服务' }}</h3>
          <el-button type="primary" link size="small" class="add-btn" :disabled="!currentAppId" @click="handleAddCases">
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
        <div class="action-buttons">
          <el-input v-model="searchKeyword" placeholder="搜索原服务名称" clearable style="width: 200px"
            @clear="handleSearch" @keyup.enter="handleSearch">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
        </div>
      </div>

      <!-- 批量操作工具栏 -->
      <div class="batch-toolbar" v-if="selectedRows.length > 0">
        <span class="batch-info">已选 {{ selectedRows.length }} 条</span>
        <el-button type="danger" size="small" @click="handleBatchDelete">批量删除</el-button>
        <el-button type="info" size="small" @click="handleClearSelection">清空选择</el-button>
      </div>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-select v-model="filterStatus" placeholder="可用状态" clearable size="small" style="width: 120px" @change="handleSearch">
          <el-option label="可用" :value="1" />
          <el-option label="不可用" :value="0" />
        </el-select>
        <el-select v-model="filterOwner" placeholder="负责人" clearable filterable size="small" style="width: 140px" @change="handleSearch">
          <el-option v-for="u in userList" :key="u.id" :label="u.realName || u.username" :value="u.id" />
        </el-select>
        <el-date-picker v-model="filterDateRange" type="daterange" range-separator="至" start-placeholder="创建开始" end-placeholder="创建结束" size="small" style="width: 240px" value-format="YYYY-MM-DD" @change="handleSearch" />
        <el-button size="small" @click="resetFilters">重置筛选</el-button>
      </div>

      <!-- 原服务表格 -->
      <div class="table-container">
        <el-table :data="serviceList" v-loading="loading" stripe border style="width: 100%" height="100%" :resizable="true"
          @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="45" align="center" />
          <el-table-column type="index" label="序号" width="55" align="center" :index="getIndex" />
          <el-table-column prop="name" label="原服务名称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="isAvailable" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isAvailable === 1 ? 'success' : 'danger'" size="small">
                {{ row.isAvailable === 1 ? '可用' : '不可用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建人" width="90" align="center">
            <template #default="{ row }">
              {{ getUserName(row.creatorId) }}
            </template>
          </el-table-column>
          <el-table-column label="负责人" width="90" align="center">
            <template #default="{ row }">
              {{ getUserName(row.ownerId) }}
            </template>
          </el-table-column>
          <el-table-column label="修改人" width="90" align="center">
            <template #default="{ row }">
              {{ getUserName(row.updaterId) }}
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="140" align="center">
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="140" align="center">
            <template #default="{ row }">
              {{ formatDate(row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right" align="center">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleDebug(row)">调试</el-button>
              <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination">
          <el-pagination
            v-if="total > 0"
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSearch"
            @current-change="handlePageChange"
            :pager-count="7"
          />
        </div>
      </div>
    </div>

    <!-- 添加用例到原服务弹窗 -->
    <el-dialog v-model="addCasesDialogVisible" title="添加原服务" width="850px" @close="handleAddCasesClose">
      <el-tabs v-model="addTabActive" @tab-change="handleTabChange">
        <!-- Tab 1: 从用例选取 -->
        <el-tab-pane label="从用例选取" name="cases">
          <div class="add-cases-toolbar">
            <el-input v-model="caseSearchKeyword" placeholder="搜索用例名称" clearable style="width: 220px"
              @clear="fetchAvailableCases" @keyup.enter="fetchAvailableCases" />
            <el-select v-model="caseProtocolFilter" placeholder="协议类型" clearable style="width: 120px"
              @change="fetchAvailableCases">
              <el-option label="HTTP" :value="1" />
              <el-option label="Dubbo" :value="2" />
            </el-select>
            <el-button type="primary" @click="fetchAvailableCases">搜索</el-button>
          </div>
          <el-table :data="availableCaseList" v-loading="casesLoading" stripe border style="width: 100%; margin-top: 12px"
            @selection-change="handleCaseSelectionChange" max-height="400" ref="caseTableRef">
            <el-table-column type="selection" width="50" align="center" />
            <el-table-column prop="name" label="用例名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="address" label="请求地址" min-width="180" show-overflow-tooltip />
            <el-table-column prop="protocolType" label="协议类型" width="90" align="center" />
            <el-table-column prop="requestType" label="请求方式" width="90" align="center" />
            <el-table-column prop="applicationName" label="所属应用" width="120" show-overflow-tooltip />
          </el-table>
          <div class="cases-pagination" style="display: flex; justify-content: flex-end; margin-top: 8px;">
            <el-pagination v-model:current-page="casePageNum" v-model:page-size="casePageSize"
              :page-sizes="[10, 20, 50]" :total="caseTotal"
              layout="total, sizes, prev, pager, next"
              @size-change="fetchAvailableCases" @current-change="fetchAvailableCases" :pager-count="5" />
          </div>
        </el-tab-pane>

        <!-- Tab 2: 手动新增 -->
        <el-tab-pane label="手动新增" name="manual">
          <el-form ref="manualFormRef" :model="manualForm" :rules="manualFormRules" label-width="100px"
            style="margin-top: 12px;">
            <el-form-item label="服务名称" prop="name">
              <el-input v-model="manualForm.name" placeholder="请输入原服务名称" />
            </el-form-item>
            <el-form-item label="协议类型" prop="protocolType">
              <el-select v-model="manualForm.protocolType" placeholder="请选择协议类型" style="width: 100%">
                <el-option label="HTTP" :value="1" />
                <el-option label="Dubbo" :value="2" />
              </el-select>
            </el-form-item>
            <el-form-item label="请求方式" prop="requestType">
              <el-select v-model="manualForm.requestType" placeholder="请选择请求方式" style="width: 100%"
                :disabled="manualForm.protocolType === 2">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
                <el-option label="DELETE" value="DELETE" />
              </el-select>
            </el-form-item>
            <el-form-item label="请求地址" prop="address">
              <el-input v-model="manualForm.address" placeholder="请输入请求地址" />
            </el-form-item>
            <el-form-item label="请求头" prop="requestHeaders">
              <el-input v-model="manualForm.requestHeaders" type="textarea" :rows="3"
                placeholder='请求头配置（JSON），如 {"Content-Type":"application/json"}' />
            </el-form-item>
            <el-form-item label="请求参数" prop="requestParams">
              <el-input v-model="manualForm.requestParams" type="textarea" :rows="3"
                placeholder='请求参数（JSON），如 {"key":"value"}' />
            </el-form-item>
            <el-form-item label="请求体" prop="requestBody">
              <el-input v-model="manualForm.requestBody" type="textarea" :rows="3"
                placeholder="请求体（JSON 字符串）" />
            </el-form-item>
            <el-form-item label="出参映射" prop="outputParams">
              <el-input v-model="manualForm.outputParams" type="textarea" :rows="3"
                placeholder="出参映射规则（JSON）" />
            </el-form-item>
            <el-form-item label="断言配置">
              <div class="manual-assertions">
                <div class="manual-assert-row" v-for="(rule, idx) in manualAssertions" :key="idx">
                  <el-select v-model="rule.type" placeholder="断言类型" size="small" style="width: 150px">
                    <el-option label="状态码 (STATUS_CODE)" value="STATUS_CODE" />
                    <el-option label="响应头 (HEADER)" value="HEADER" />
                    <el-option label="JSON路径 (JSON_PATH)" value="JSON_PATH" />
                    <el-option label="正则 (REGEX)" value="REGEX" />
                    <el-option label="响应时间 (RESPONSE_TIME)" value="RESPONSE_TIME" />
                  </el-select>
                  <el-select v-model="rule.operator" placeholder="运算符" size="small" style="width: 140px">
                    <el-option v-for="opt in assertionOperatorOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                  </el-select>
                  <el-input v-model="rule.expression" placeholder="表达式（如 $.code）" size="small" style="flex: 1" />
                  <el-input v-model="rule.expectedValue" placeholder="期望值" size="small" style="width: 140px" />
                  <el-button type="danger" link size="small" @click="manualAssertions.splice(idx, 1)">删除</el-button>
                </div>
                <el-button type="primary" link size="small" @click="addManualAssertion">
                  <el-icon><Plus /></el-icon> 添加断言
                </el-button>
              </div>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <div v-if="addTabActive === 'cases'" style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
          <span style="font-size: 13px; color: #606266;">
            已选 <b>{{ selectedCases.length }}</b> 条用例
          </span>
          <div>
            <el-button @click="addCasesDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitAddCases" :loading="addCasesLoading"
              :disabled="selectedCases.length === 0">
              保存（{{ selectedCases.length }}条）
            </el-button>
          </div>
        </div>
        <div v-else style="display: flex; justify-content: flex-end; width: 100%;">
          <el-button @click="addCasesDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitManualCreate" :loading="manualCreateLoading">创建</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import {
  getDepartmentTree,
  getUserList,
  listOriginalServices,
  deleteOriginalService,
  batchDeleteOriginalServices,
  listAvailableCases,
  addCasesToOriginalService,
  createOriginalService
} from '@/api/admin'

const router = useRouter()

// ========== 用户映射 ==========
const userMap = ref({})
const userList = ref([])

const getUserName = (userId) => {
  if (!userId) return '-'
  return userMap.value[userId] || String(userId)
}

const fetchUserList = async () => {
  try {
    const res = await getUserList({ pageNum: 1, pageSize: 200 })
    const data = res.data || res
    const records = data.records || data.list || []
    userList.value = records
    const map = {}
    records.forEach(u => {
      map[u.id] = u.realName || u.username || String(u.id)
    })
    userMap.value = map
  } catch (e) {
    console.error('获取用户列表失败', e)
  }
}

// ========== 部门树 ==========
const treeNodes = ref([])
const treeRef = ref(null)
const defaultProps = { children: 'children', label: 'name' }
const currentAppName = ref('')
const currentAppId = ref('')

const initTree = async () => {
  try {
    const res = await getDepartmentTree()
    let treeData = []
    if (res.code === 200 && Array.isArray(res.data)) {
      treeData = res.data
    }
    const formatTree = (list) => {
      return list.map(dept => {
        const children = []
        if (Array.isArray(dept.applications)) {
          dept.applications.forEach(app => {
            children.push({
              id: app.id,
              name: app.name,
              isApp: true,
              parentId: dept.departmentId,
              departmentId: dept.departmentId
            })
          })
        }
        return {
          id: dept.departmentId,
          name: dept.departmentName,
          isApp: false,
          children
        }
      })
    }
    treeNodes.value = formatTree(treeData)

    // 默认选中第一个部门的第一个应用
    selectFirstApp()
  } catch (error) {
    console.error('加载部门树失败', error)
    ElMessage.error('加载部门树失败')
  }
}

/**
 * 默认选中第一个部门的第一个应用，高亮节点并加载数据
 */
const selectFirstApp = () => {
  const firstDept = treeNodes.value[0]
  if (!firstDept || !Array.isArray(firstDept.children) || firstDept.children.length === 0) {
    return
  }
  const firstApp = firstDept.children[0]
  currentAppName.value = firstApp.name
  currentAppId.value = firstApp.id
  // 高亮树节点（nextTick 确保 DOM 已渲染）
  nextTick(() => {
    treeRef.value && treeRef.value.setCurrentKey(firstApp.id)
  })
  handleSearch()
}

const onTreeNodeClick = (data) => {
  if (data.isApp === true) {
    currentAppName.value = data.name
    currentAppId.value = data.id
    handleSearch()
  }
}

// ========== 原服务列表 ==========
const serviceList = ref([])
const selectedRows = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchKeyword = ref('')

// 筛选条件
const filterStatus = ref(null)
const filterOwner = ref(null)
const filterDateRange = ref(null)

const getIndex = (index) => (pageNum.value - 1) * pageSize.value + index + 1

const resetFilters = () => {
  filterStatus.value = null
  filterOwner.value = null
  filterDateRange.value = null
  searchKeyword.value = ''
  handleSearch()
}

const fetchServiceList = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    if (currentAppId.value) {
      params.applicationId = currentAppId.value
    }
    if (searchKeyword.value && searchKeyword.value.trim()) {
      params.keyword = searchKeyword.value.trim()
    }
    if (filterStatus.value !== null && filterStatus.value !== '') {
      params.isAvailable = filterStatus.value
    }
    if (filterOwner.value) {
      params.ownerId = filterOwner.value
    }
    if (filterDateRange.value && filterDateRange.value.length === 2) {
      params.startDate = filterDateRange.value[0]
      params.endDate = filterDateRange.value[1]
    }
    const res = await listOriginalServices(params)
    const data = res.data || res
    let records = data.records || data.list || []

    // 前端二次筛选（兼容后端未支持的条件）
    if (filterStatus.value !== null && filterStatus.value !== '') {
      records = records.filter(r => r.isAvailable === filterStatus.value)
    }
    if (filterOwner.value) {
      records = records.filter(r => r.ownerId === filterOwner.value)
    }
    if (filterDateRange.value && filterDateRange.value.length === 2) {
      const [start, end] = filterDateRange.value
      records = records.filter(r => {
        if (!r.createdAt) return true
        const created = formatDate(r.createdAt).substring(0, 10)
        return created >= start && created <= end
      })
    }

    serviceList.value = records
    total.value = Number(data.total) || records.length
  } catch (error) {
    console.error('获取原服务列表失败', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pageNum.value = 1
  fetchServiceList()
}

const handlePageChange = () => {
  fetchServiceList()
}

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

const handleClearSelection = () => {
  selectedRows.value = []
}

// ========== 删除原服务 ==========
const handleDelete = (row) => {
  ElMessageBox.confirm(`确定删除原服务「${row.name}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteOriginalService(row.id)
      ElMessage.success('删除成功')
      pageNum.value = 1
      fetchServiceList()
    } catch (error) {
      const msg = error.response?.data?.message || error.message || '删除失败'
      ElMessage.error(msg)
    }
  }).catch(() => {})
}

// ========== 批量删除 ==========
const handleBatchDelete = () => {
  if (selectedRows.value.length === 0) return
  const ids = selectedRows.value.map(r => r.id)
  ElMessageBox.confirm(`确定批量删除选中的 ${ids.length} 条原服务吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await batchDeleteOriginalServices(ids)
      ElMessage.success('批量删除成功')
      selectedRows.value = []
      pageNum.value = 1
      fetchServiceList()
    } catch (error) {
      const msg = error.response?.data?.message || error.message || '批量删除失败'
      ElMessage.error(msg)
    }
  }).catch(() => {})
}

// ========== 调试（复用接口测试页面）==========
// 点击"调试"按钮：携带原服务基本信息跳转到接口测试页面（ApiTester/DubboTester）
// 与 Apihub 的调试逻辑保持一致：query 传基础信息，sessionStorage 传完整调试数据
const handleDebug = (row) => {
  const query = {
    from: 'originalService',
    serviceName: row.name || '',
    servicePath: row.address || row.servicePath || '',
    method: row.requestType || 'GET',
    applicationId: row.applicationId || ''
  }
  const debugPayload = {
    requestHeaders: row.requestHeaders || '',
    requestBody: row.requestBody || '',
    assertions: row.assertions || row.responseAssertions || '',
    timeout: row.timeout || 30000,
    protocolType: row.protocolType,
    originalServiceId: row.id,
    caseId: row.caseId || ''
  }
  try {
    sessionStorage.setItem('apihub_debug_payload', JSON.stringify(debugPayload))
  } catch (e) {
    console.warn('[OriginalService] 存储调试数据失败', e)
  }
  // 根据协议类型跳转：2=Dubbo，其他=HTTP
  if (row.protocolType === 2) {
    router.push({ path: '/layout/DubboTester', query })
  } else {
    router.push({ path: '/layout/ApiTester', query })
  }
}

// ========== 添加原服务弹窗 ==========
const addCasesDialogVisible = ref(false)
const casesLoading = ref(false)
const addCasesLoading = ref(false)
const availableCaseList = ref([])
const selectedCases = ref([])
const casePageNum = ref(1)
const casePageSize = ref(10)
const caseTotal = ref(0)
const caseSearchKeyword = ref('')
const caseProtocolFilter = ref('')
const caseTableRef = ref(null)
const addTabActive = ref('cases')

// ========== 手动新增原服务 ==========
const manualFormRef = ref(null)
const manualCreateLoading = ref(false)
const manualForm = reactive({
  name: '',
  protocolType: 1,
  requestType: '',
  address: '',
  requestHeaders: '',
  requestParams: '',
  requestBody: '',
  outputParams: ''
})
const assertionOperatorOptions = [
  { label: '等于 (EQUALS)', value: 'EQUALS' },
  { label: '包含 (CONTAINS)', value: 'CONTAINS' },
  { label: '正则匹配 (MATCHES)', value: 'MATCHES' },
  { label: '大于 (GT)', value: 'GT' },
  { label: '小于 (LT)', value: 'LT' },
  { label: '大于等于 (GTE)', value: 'GTE' },
  { label: '小于等于 (LTE)', value: 'LTE' }
]
const manualAssertions = ref([])
const manualFormRules = {
  name: [{ required: true, message: '请输入原服务名称', trigger: 'blur' }],
  protocolType: [{ required: true, message: '请选择协议类型', trigger: 'change' }],
  address: [{ required: true, message: '请输入请求地址', trigger: 'blur' }]
}

const handleAddCases = () => {
  if (!currentAppId.value) {
    ElMessage.warning('请先选择一个应用')
    return
  }
  caseSearchKeyword.value = ''
  caseProtocolFilter.value = ''
  casePageNum.value = 1
  selectedCases.value = []
  addTabActive.value = 'cases'
  resetManualForm()
  addCasesDialogVisible.value = true
  fetchAvailableCases()
}

const handleTabChange = () => {
  if (addTabActive.value === 'manual') {
    resetManualForm()
  }
}

const resetManualForm = () => {
  manualForm.name = ''
  manualForm.protocolType = 1
  manualForm.requestType = ''
  manualForm.address = ''
  manualForm.requestHeaders = ''
  manualForm.requestParams = ''
  manualForm.requestBody = ''
  manualForm.outputParams = ''
  manualAssertions.value = []
  manualFormRef.value?.clearValidate()
}

const addManualAssertion = () => {
  manualAssertions.value.push({
    type: '',
    operator: 'EQUALS',
    expression: '',
    expectedValue: ''
  })
}

const submitManualCreate = async () => {
  if (!manualFormRef.value) return
  await manualFormRef.value.validate(async (valid) => {
    if (!valid) return
    manualCreateLoading.value = true
    try {
      const payload = {
        name: manualForm.name,
        protocolType: manualForm.protocolType,
        requestType: manualForm.protocolType === 2 ? null : (manualForm.requestType || null),
        address: manualForm.address,
        applicationId: currentAppId.value
      }
      if (manualForm.requestHeaders) payload.requestHeaders = manualForm.requestHeaders
      if (manualForm.requestParams) payload.requestParams = manualForm.requestParams
      if (manualForm.requestBody) payload.requestBody = manualForm.requestBody
      if (manualForm.outputParams) payload.outputParams = manualForm.outputParams
      const validAssertions = manualAssertions.value.filter(a => a.type && a.expression && a.expectedValue)
      if (validAssertions.length > 0) {
        payload.assertions = JSON.stringify(validAssertions.map(a => ({
          type: a.type,
          operator: a.operator || 'EQUALS',
          expression: a.expression,
          expectedValue: a.expectedValue
        })))
      }
      await createOriginalService(payload)
      ElMessage.success('原服务创建成功')
      addCasesDialogVisible.value = false
      fetchServiceList()
    } catch (error) {
      const msg = error.response?.data?.message || error.message || '创建失败'
      ElMessage.error(msg)
    } finally {
      manualCreateLoading.value = false
    }
  })
}

const fetchAvailableCases = async () => {
  casesLoading.value = true
  try {
    const params = {
      pageNum: casePageNum.value,
      pageSize: casePageSize.value,
      applicationId: currentAppId.value
    }
    if (caseSearchKeyword.value && caseSearchKeyword.value.trim()) {
      params.keyword = caseSearchKeyword.value.trim()
    }
    if (caseProtocolFilter.value !== '' && caseProtocolFilter.value !== null) {
      params.protocolType = caseProtocolFilter.value
    }
    const res = await listAvailableCases(params)
    const data = res.data || res
    availableCaseList.value = data.records || data.list || []
    caseTotal.value = Number(data.total) || 0
  } catch (error) {
    console.error('获取可选用例列表失败', error)
    ElMessage.error('获取可选用例列表失败')
  } finally {
    casesLoading.value = false
  }
}

const handleCaseSelectionChange = (rows) => {
  selectedCases.value = rows
}

const handleAddCasesClose = () => {
  selectedCases.value = []
  availableCaseList.value = []
  addTabActive.value = 'cases'
  resetManualForm()
}

const submitAddCases = async () => {
  if (selectedCases.value.length === 0) {
    ElMessage.warning('请至少选择一条用例')
    return
  }
  addCasesLoading.value = true
  try {
    const caseIds = selectedCases.value.map(c => c.id)
    await addCasesToOriginalService({
      caseIds: caseIds,
      applicationId: currentAppId.value
    })
    ElMessage.success(`成功添加 ${caseIds.length} 条用例到原服务`)
    addCasesDialogVisible.value = false
    fetchServiceList()
  } catch (error) {
    const msg = error.response?.data?.message || error.message || '添加失败'
    ElMessage.error(msg)
  } finally {
    addCasesLoading.value = false
  }
}

// ========== 工具函数 ==========
const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`
}

// ========== 初始化 ==========
onMounted(async () => {
  await Promise.all([initTree(), fetchUserList()])
  // initTree 已通过 selectFirstApp 自动加载列表，无需重复调用
})
</script>

<style lang="scss" scoped>
.original-service-container {
  height: 100%;
  display: flex;
  gap: 0px;
  padding: 0px;
  background: var(--paper, #fafafa);
  overflow: hidden;
  box-sizing: border-box;

  .left-tree {
    width: 260px;
    min-width: 260px;
    background: var(--paper, #fafafa);
    border: 1px solid var(--mute, #e5e5e5);
    display: flex;
    flex-direction: column;
    overflow: hidden;

    .tree-header {
      padding: 12px 16px;
      border-bottom: 1px solid var(--mute, #e5e5e5);
      flex-shrink: 0;
      h3 { margin: 0; font-size: 13px; font-weight: 500; color: var(--ink, #0a0a0a); }
    }

    .tree-content {
      flex: 1;
      overflow: auto;
      padding: 8px 12px;
    }
  }

  .right-content {
    flex: 1;
    min-width: 0;
    background: var(--paper, #fafafa);
    border: 1px solid var(--mute, #e5e5e5);
    display: flex;
    flex-direction: column;
    overflow: hidden;
    padding-bottom: 12px;
    height: 100%;
    box-sizing: border-box;

    .top-bar {
      padding: 12px 16px;
      border-bottom: 1px solid var(--mute, #e5e5e5);
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-shrink: 0;

      .app-title {
        display: flex;
        align-items: center;
        gap: 6px;
        h3 { margin: 0; font-size: 13px; font-weight: 500; color: var(--ink, #0a0a0a); }
      }

      .action-buttons {
        display: flex;
        gap: 8px;
        align-items: center;
      }
    }

    .batch-toolbar {
      padding: 8px 16px;
      background: var(--smoke, #f5f5f5);
      border-bottom: 1px solid var(--mute, #e5e5e5);
      display: flex;
      align-items: center;
      gap: 10px;
      flex-shrink: 0;

      .batch-info { font-size: 11px; color: var(--ghost, #a3a3a3); }
    }

    .filter-bar {
      padding: 8px 16px;
      border-bottom: 1px solid var(--mute, #e5e5e5);
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      flex-shrink: 0;
      background: var(--paper, #fafafa);
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
        flex-shrink: 0;
        display: flex;
        justify-content: center;
        min-height: 32px;

        :deep(.el-pagination) {
          font-size: 12px;
          .el-pagination__total, .el-pagination__sizes, .el-pagination__jump { font-size: 11px; }
        }
      }
    }
  }
}

.add-cases-toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
}

.manual-assertions {
  width: 100%;
}
.manual-assert-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

:deep(.el-dialog) {
  border-radius: 0;
  border: 1px solid var(--mute, #e5e5e5);
}

:deep(.el-table__fixed-right),
:deep(.el-table__fixed) {
  z-index: 10;
}
</style>
