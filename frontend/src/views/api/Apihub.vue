<template>
  <div class="apihub-container">
    <!-- 左侧部门树 -->
    <div class="left-tree">
      <div class="tree-header">
        <h3>部门</h3>
      </div>
      <div class="tree-content">
        <el-tree
          ref="treeRef"
          :data="treeNodes"
          :props="defaultProps"
          node-key="id"
          :default-expand-all="true"
          :highlight-current="true"
          :expand-on-click-node="false"
          @node-click="onTreeNodeClick"
        >
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
          <h3>{{ currentAppName || 'ApiHub' }}</h3>
        </div>
        <div class="action-buttons">
          <el-button type="primary" @click="handleImportCurl">
            导入curl
          </el-button>
          <el-button type="success" @click="handleBatchAutoTest" :disabled="selectedRows.length === 0">
            自动化测试
          </el-button>
          <el-input
            v-model="searchKeyword"
            placeholder="搜索接口名称 / 地址..."
            clearable
            style="width: 200px"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
        </div>
      </div>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-select v-model="filterMethod" placeholder="请求方法" clearable size="small" style="width: 120px" @change="handleSearch">
          <el-option label="GET" value="GET" />
          <el-option label="POST" value="POST" />
          <el-option label="PUT" value="PUT" />
          <el-option label="DELETE" value="DELETE" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="自动化状态" clearable size="small" style="width: 120px" @change="handleSearch">
          <el-option label="已启用" :value="1" />
          <el-option label="未启用" :value="0" />
        </el-select>
        <el-select v-model="filterOwner" placeholder="负责人" clearable filterable size="small" style="width: 140px" @change="handleSearch">
          <el-option v-for="(name, id) in userMap" :key="id" :label="name" :value="Number(id)" />
        </el-select>
        <el-date-picker v-model="filterDateRange" type="daterange" range-separator="至" start-placeholder="创建开始" end-placeholder="创建结束" size="small" style="width: 240px" value-format="YYYY-MM-DD" @change="handleSearch" />
        <el-button size="small" @click="resetFilters">重置筛选</el-button>
      </div>

      <!-- 批量操作工具栏 -->
      <div class="batch-toolbar" v-if="selectedRows.length > 0">
        <span class="batch-info">已选 {{ selectedRows.length }} 条</span>
        <el-button type="danger" size="small" @click="handleBatchDelete">批量删除</el-button>
        <el-button type="info" size="small" @click="handleClearSelection">清空选择</el-button>
      </div>

      <!-- 接口列表表格 -->
      <div class="table-container">
        <el-table
          :data="serviceList"
          v-loading="loading"
          stripe
          border
          style="width: 100%"
          height="100%"
          :resizable="true"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="45" align="center" />
          <el-table-column type="index" label="序号" width="55" align="center" :index="getIndex" />
          <el-table-column prop="name" label="接口名称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="address" label="接口地址" min-width="140" show-overflow-tooltip />
          <el-table-column prop="requestType" label="方法" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="getMethodTagType(row.requestType)" size="small">{{ row.requestType || 'GET' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="isAutomationEnabled" label="自动化" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isAutomationEnabled ? 'success' : 'info'" size="small">
                {{ row.isAutomationEnabled ? '已启用' : '未启用' }}
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
          <el-table-column label="操作" width="170" fixed="right" align="center">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleDebug(row)">调试</el-button>
              <el-button type="warning" link size="small" @click="handleExportCurl(row)">导出curl</el-button>
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

    <!-- 导入 cURL 弹窗 -->
    <el-dialog v-model="curlImportVisible" title="导入 cURL 命令" width="650px">
      <el-form :model="curlImportForm" ref="curlImportFormRef" label-width="100px">
        <el-form-item label="* curl 命令" prop="curlCommand">
          <el-input type="textarea" v-model="curlImportForm.curlCommand" :rows="8"
            placeholder="curl -X POST https://api.example.com/users -H 'Content-Type: application/json'" />
        </el-form-item>
        <el-form-item label="用例名称">
          <el-input v-model="curlImportForm.name" placeholder="选填，留空则自动从 URL 生成" />
        </el-form-item>
        <el-form-item label="* 所属应用" prop="applicationId">
          <el-select v-model="curlImportForm.applicationId" placeholder="请选择应用" style="width: 100%">
            <el-option v-for="app in allApplications" :key="app.id" :label="app.name" :value="app.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="curlImportVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCurlImport" :loading="curlImportLoading">导入</el-button>
      </template>
    </el-dialog>

    <!-- 导出 cURL 弹窗 -->
    <el-dialog v-model="curlExportVisible" title="导出 curl 命令" width="650px">
      <div style="margin-bottom: 8px; color: #606266; font-size: 14px;">curl 命令：</div>
      <div style="background: #f5f7fa; padding: 16px; border-radius: 6px; overflow-x: auto;">
        <pre style="margin: 0; white-space: pre-wrap; word-break: break-all; font-family: Consolas, Monaco, monospace; font-size: 13px; line-height: 1.6; color: #303133;">{{ curlExportText }}</pre>
      </div>
      <template #footer>
        <el-button type="primary" @click="copyCurlText">复制</el-button>
        <el-button @click="curlExportVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import {
  getHttpCaseList,
  deleteHttpCase,
  batchDeleteHttpCases,
  importCurl,
  exportCurl,
  runAutomationTask
} from '@/api/admin'
import { loadAllApps, loadUserNameMap, loadDepartmentTree } from '@/utils/cache'
import { useUserStore } from '@/composables/useUserStore'

const router = useRouter()
const { userInfo } = useUserStore()

// ========== 用户映射 ==========
const userMap = ref({})
const userList = ref([])

const getUserName = (userId) => {
  if (!userId) return '-'
  return userMap.value[userId] || String(userId)
}

const fetchUserList = async () => {
  try {
    // 使用缓存加载用户名称映射
    const map = await loadUserNameMap()
    userMap.value = map
  } catch (e) {
    console.error('获取用户列表失败', e)
  }
}

// ========== cURL 导入/导出相关 ==========
const curlImportVisible = ref(false)
const curlImportLoading = ref(false)
const curlImportFormRef = ref(null)
const curlImportForm = reactive({
  curlCommand: '',
  name: '',
  applicationId: ''
})

const curlExportVisible = ref(false)
const curlExportText = ref('')

// 所有应用列表（用于cURL导入）
const allApplications = ref([])

const loadAllApplications = async () => {
  try {
    // 使用缓存加载所有应用
    allApplications.value = await loadAllApps()
  } catch (e) {
    console.error('加载应用列表失败', e)
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
    // 使用缓存获取部门树
    const treeData = await loadDepartmentTree()
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
const filterMethod = ref('')
const filterStatus = ref(null)
const filterOwner = ref(null)
const filterDateRange = ref(null)

const getIndex = (index) => (pageNum.value - 1) * pageSize.value + index + 1

const resetFilters = () => {
  filterMethod.value = ''
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
    if (filterMethod.value) {
      params.requestType = filterMethod.value
    }
    if (filterStatus.value !== null && filterStatus.value !== '') {
      params.isAutomationEnabled = filterStatus.value === 1
    }
    if (filterOwner.value) {
      params.ownerId = filterOwner.value
    }
    if (filterDateRange.value && filterDateRange.value.length === 2) {
      params.startDate = filterDateRange.value[0]
      params.endDate = filterDateRange.value[1]
    }
    const res = await getHttpCaseList(params)
    const data = res.data || res
    let records = data.records || data.list || []

    // 前端二次筛选（兼容后端未支持的条件）
    if (filterMethod.value) {
      records = records.filter(r => (r.requestType || 'GET').toUpperCase() === filterMethod.value.toUpperCase())
    }
    if (filterStatus.value !== null && filterStatus.value !== '') {
      records = records.filter(r => Boolean(r.isAutomationEnabled) === (filterStatus.value === 1))
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
    console.error('获取用例列表失败', error)
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

// ========== 删除用例 ==========
const handleDelete = (row) => {
  ElMessageBox.confirm(`确定删除「${row.name}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteHttpCase(row.id)
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
  ElMessageBox.confirm(`确定批量删除选中的 ${ids.length} 条吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await batchDeleteHttpCases(ids)
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

// ========== cURL 导入/导出方法 ==========

// 打开导入 cURL 弹窗
const handleImportCurl = () => {
  if (!allApplications.value.length) {
    ElMessage.warning('请先创建应用')
    return
  }
  curlImportForm.curlCommand = ''
  curlImportForm.name = ''
  curlImportForm.applicationId = currentAppId.value || ''
  curlImportVisible.value = true
}

// 提交 cURL 导入
const submitCurlImport = async () => {
  if (!curlImportForm.curlCommand || !curlImportForm.curlCommand.trim()) {
    ElMessage.warning('请输入 cURL 命令')
    return
  }
  if (!curlImportForm.applicationId) {
    ElMessage.warning('请选择所属应用')
    return
  }

  try {
    curlImportLoading.value = true
    const res = await importCurl({
      curlCommand: curlImportForm.curlCommand.trim(),
      applicationId: String(curlImportForm.applicationId),
      name: curlImportForm.name || null
    })
    // 兼容拦截器返回 response.data 的情况：res 可能是 {code,message,data,success,timestamp}
    const payload = res && typeof res === 'object' && 'code' in res ? res : (res?.data || res)
    if (payload && typeof payload.message === 'string' && payload.message.includes('已存在相同配置')) {
      ElMessage.success('导入成功（该用例已存在，已复用）')
    } else {
      ElMessage.success('导入成功')
    }
    curlImportVisible.value = false
    // 刷新列表（重置到第一页，确保新导入数据可见）
    pageNum.value = 1
    fetchServiceList()
  } catch (error) {
    const msg = error.response?.data?.message || error.message || '导入失败'
    ElMessage.error(msg)
  } finally {
    curlImportLoading.value = false
  }
}

// 解析 URL 中的占位符（去除反引号、`{{...}}`、`${...}` 等模板语法，得到可用的真实 URL）
const cleanUrl = (raw) => {
  if (!raw) return ''
  let url = String(raw).trim()

  // 去除最外层的花括号（{...} 或 {{...}}）
  while (/^\{[\s\S]*\}$/.test(url)) {
    url = url.substring(1, url.length - 1).trim()
  }

  // 去除反引号（用于代码字面量）
  url = url.replace(/`/g, '')

  // 替换常见占位符为可用的占位字符串（保留语义但避免出现奇怪字符）
  // 1) {{xxx}} -> xxx
  url = url.replace(/\{\{([\s\S]+?)\}\}/g, (_m, p1) => String(p1).trim())
  // 2) ${xxx} -> xxx
  url = url.replace(/\$\{([\s\S]+?)\}/g, (_m, p1) => String(p1).trim())
  // 3) 剩余单独的 { 或 } 字符去除
  url = url.replace(/[{}]/g, '')

  // 去除 URL 中可能存在的多余空白
  url = url.replace(/\s+/g, '')

  return url
}

// 转义 shell 单引号（将 ' 替换为 '\''）
const escapeSingleQuote = (s) => {
  if (s == null) return ''
  return String(s).replace(/'/g, `'\\''`)
}

// 导出 cURL：优先调用后端接口（数据完整），失败时用 row 现有字段拼接
const handleExportCurl = async (row) => {
  try {
    // 优先：调用后端 /api-case/{id}/export-curl 获取完整 cURL（用例本身即 case）
    if (row.id) {
      try {
        const res = await exportCurl(row.id)
        const payload = res && typeof res === 'object' && 'code' in res ? res : (res?.data || res)
        const data = payload?.data ?? payload
        if (typeof data === 'string' && data.trim().length > 0) {
          curlExportText.value = data
          curlExportVisible.value = true
          return
        }
      } catch (e) {
        console.warn('后端导出cURL失败，回退到前端拼装', e)
      }
    }

    // 回退：用 row 现有字段拼装（手动创建的数据可能不完整）
    const isDubbo = String(row.protocolType).toUpperCase() === 'DUBBO' || row.protocolType === 2
    const method = isDubbo ? 'DUBBO' : (row.requestType || 'GET')
    const rawUrl = row.address || ''

    if (!rawUrl) {
      ElMessage.warning('该用例没有接口地址')
      return
    }

    const url = cleanUrl(rawUrl)
    if (!url) {
      ElMessage.warning('用例地址无效，无法生成 cURL')
      return
    }

    const urlPart = `'${escapeSingleQuote(url)}'`
    let parts = [`curl -X ${method} \\`, `  ${urlPart}`]

    // 解析请求头（Apihub 列表来自 original_service 表，字段是 requestHeaders 字符串）
    let headers = row.requestHeaders
    if (typeof headers === 'string' && headers.trim()) {
      try {
        headers = JSON.parse(headers)
      } catch (e) {
        headers = {}
      }
    } else if (!headers || typeof headers !== 'object') {
      headers = {}
    }

    Object.entries(headers).forEach(([key, value]) => {
      parts.push(`  -H '${escapeSingleQuote(key)}: ${escapeSingleQuote(value)}'`)
    })

    // 解析入参（用例字段名是 requestParams，对象或 JSON 字符串）
    let queryParams = row.requestParams
    if (typeof queryParams === 'string' && queryParams.trim()) {
      try {
        queryParams = JSON.parse(queryParams)
      } catch (e) {
        queryParams = {}
      }
    } else if (!queryParams || typeof queryParams !== 'object') {
      queryParams = {}
    }
    Object.entries(queryParams).forEach(([key, value]) => {
      // 兼容 JSON Schema 格式：{type, description, ...} 提取默认示例值
      let v
      if (value && typeof value === 'object' && !Array.isArray(value)) {
        v = value.example ?? value.default ?? value.value ?? ''
        if (v === '' && value.type) v = `<${value.type}>`
      } else {
        v = value == null ? '' : String(value)
      }
      parts.push(`  -G -d '${escapeSingleQuote(key)}=${escapeSingleQuote(String(v))}'`)
    })

    // 请求体（字段名 requestBody）
    if (row.requestBody) {
      let body = row.requestBody
      if (typeof body === 'object') {
        body = JSON.stringify(body)
      }
      parts.push(`  -d '${escapeSingleQuote(body)}'`)
    }

    // 用例 VO 没有 timeout 字段，跳过

    // 断言信息（用例字段名是 responseAssertions，数组或 JSON 字符串）
    let assertions = row.responseAssertions || row.assertions
    if (typeof assertions === 'string' && assertions.trim()) {
      try {
        assertions = JSON.parse(assertions)
      } catch (e) {
        assertions = []
      }
    } else if (!Array.isArray(assertions)) {
      assertions = []
    }
    if (assertions.length > 0) {
      parts.push(`  \\`)
      parts.push(`  # ===== 断言（${assertions.length}条） =====`)
      assertions.forEach((rule, idx) => {
        const type = rule.type || ''
        const desc = rule.description || ''
        const expr = rule.expression || ''
        const op = rule.operator || 'EQUALS'
        const expected = rule.expectedValue ?? rule.expected ?? ''
        parts.push(`  # 断言${idx + 1} [${type}] 描述: ${desc} 表达式: ${expr} 操作符: ${op} 期望值: ${expected}`)
      })
    }

    curlExportText.value = parts.join('\n')
    curlExportVisible.value = true
  } catch (error) {
    console.error('生成cURL命令失败', error)
    ElMessage.error('生成cURL命令失败')
  }
}

// 复制 cURL 文本
const copyCurlText = async () => {
  try {
    await navigator.clipboard.writeText(curlExportText.value)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    // 备用方案
    const textarea = document.createElement('textarea')
    textarea.value = curlExportText.value
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('已复制到剪贴板')
  }
}

// ========== 调试 ==========

// ========== 自动化测试 ==========
// 点击"自动化测试"按钮：基于该原服务关联的用例启动一次自动化测试任务
// 后端接口 POST /task/automation/run，请求体 AutomationRunRequestDTO
const getExecutorId = () => {
  // 优先使用响应式 userStore，回退到 sessionStorage
  // 后端 executorId 为 Long 类型，必须返回数字
  const raw = userInfo.value?.userId || userInfo.value?.id
    || (() => {
      try {
        const s = sessionStorage.getItem('userInfo')
        if (s) {
          const p = JSON.parse(s)
          return p?.userId || p?.id
        }
      } catch (e) { /* 忽略解析错误 */ }
      return null
    })()
  if (raw == null) return null
  const id = parseInt(raw)
  return isNaN(id) ? null : id
}

const handleBatchAutoTest = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择需要测试的用例')
    return
  }

  // 用例本身就是 case，直接使用 id
  const validRows = selectedRows.value.filter(r => r.id)
  if (validRows.length === 0) {
    ElMessage.warning('选中的用例无效，无法执行自动化测试')
    return
  }

  const executorId = getExecutorId()
  if (!executorId) {
    ElMessage.warning('未获取到执行人信息，请重新登录后再试')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定对选中的 ${validRows.length} 个用例执行自动化测试吗？`,
      '批量自动化测试确认',
      { confirmButtonText: '执行', cancelButtonText: '取消', type: 'info' }
    )
  } catch (e) {
    return // 用户取消
  }

  try {
    const payload = {
      caseIds: validRows.map(r => String(r.id)),
      executorId: executorId,
      testSuiteName: `ApiHub批量自动化测试-${new Date().toLocaleString()}`,
      env: 'default',
      timeout: 30000
    }
    const res = await runAutomationTask(payload)
    const task = res.data || res
    ElMessage.success(`批量自动化测试已启动（任务ID: ${task.id || task.taskId || '-'}）`)
    router.push('/layout/automated')
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '批量自动化测试启动失败'
    ElMessage.error(msg)
  }
}

const handleDebug = (row) => {
  // 通过 query 传递用例基本信息，ApiTester 据此回填地址/方法
  const query = {
    from: 'apihub',
    serviceName: row.name || '',
    servicePath: row.address || '',
    method: row.requestType || 'GET',
    applicationId: row.applicationId || ''
  }
  // 通过 sessionStorage 传递完整调试数据（headers/body/assertions 等），避免 URL 长度限制
  const debugPayload = {
    requestHeaders: row.requestHeaders || '',
    requestBody: row.requestBody || '',
    assertions: row.responseAssertions || row.assertions || '',
    timeout: row.timeout || 30000,
    protocolType: row.protocolType,
    caseId: row.id || ''
  }
  try {
    sessionStorage.setItem('apihub_debug_payload', JSON.stringify(debugPayload))
  } catch (e) {
    console.warn('[Apihub] 存储调试数据失败', e)
  }
  // 根据协议类型跳转到不同的调试页面
  if (row.protocolType === 2) {
    router.push({ path: '/layout/DubboTester', query })
  } else {
    router.push({ path: '/layout/ApiTester', query })
  }
}

// ========== 工具函数 ==========
const getMethodTagType = (method) => {
  const m = (method || 'GET').toUpperCase()
  if (m === 'POST') return ''
  if (m === 'DELETE') return 'danger'
  if (m === 'PUT') return 'warning'
  return 'info'
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`
}

// ========== 初始化 ==========
onMounted(async () => {
  // initTree 和 loadAllApplications 共享部门树缓存，并行加载
  await Promise.all([initTree(), fetchUserList(), loadAllApplications()])
  // initTree 已通过 selectFirstApp 自动加载列表，无需重复调用
})
</script>

<style lang="scss" scoped>
.apihub-container {
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

.custom-tree-node {
  font-size: 12px;
  color: var(--ink, #0a0a0a);
}

:deep(.el-tree-node__content:hover) {
  background-color: rgba(0, 0, 0, 0.015);
}

:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: rgba(0, 0, 0, 0.02);
  font-weight: 500;
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
