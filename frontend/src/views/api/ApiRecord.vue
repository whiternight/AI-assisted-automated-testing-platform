<template>
  <el-card class="section-card" shadow="hover">
    <div class="section-header">
        <div class="title-left">
          <el-button type="text" class="back-btn" @click="goBack">
            <el-icon>
              <ArrowLeft />
            </el-icon>
          </el-button>
          <div>接口元数据管理</div>
        </div>
      <div class="meta-actions">
        <el-button type="primary" @click="showMetaDialog = true">新增接口</el-button>
        <el-button type="success" @click="batchGenerateAutomation" :disabled="selectedMetaRows.length === 0">
          执行自动化
        </el-button>
        <el-button v-if="isAdmin" type="warning" @click="showOwnerDialog = true" :disabled="selectedMetaRows.length === 0">
          批量修改负责人
        </el-button>
        <el-button v-if="isAdmin" type="danger" @click="batchDeleteMetadata" :disabled="selectedMetaRows.length === 0">
          批量逻辑删除
        </el-button>
      </div>
    </div>

    <div class="filter-row">
      <el-input v-model="metaFilters.address" placeholder="地址模糊搜索" clearable />
      <el-select v-model="metaFilters.appName" placeholder="应用名称" clearable style="width: 180px;">
        <el-option v-for="item in uniqueAppNames" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select v-model="metaFilters.bizDomain" placeholder="业务域" clearable style="width: 180px;">
        <el-option v-for="item in uniqueBizDomains" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select v-model="metaFilters.protocol" placeholder="协议" clearable style="width: 180px;">
        <el-option label="HTTP" value="HTTP" />
        <el-option label="HTTPS" value="HTTPS" />
      </el-select>
      <el-select v-model="metaFilters.createdBy" placeholder="创建人" clearable style="width: 180px;">
        <el-option v-for="item in uniqueCreators" :key="item" :label="item" :value="item" />
      </el-select>
      <el-button type="primary" @click="resetMetaFilters">重置</el-button>
    </div>

    <el-table
      ref="metaTable"
      :data="pagedMetaItems"
      @selection-change="handleSelectionChange"
      style="width: 100%"
      :row-class-name="metaRowClass"
    >
      <el-table-column
        type="selection"
        width="55"
        :selectable="selectionSelectable"
      />
      <el-table-column prop="appName" label="应用名称" width="140" />
      <el-table-column prop="address" label="接口地址" min-width="260" />
      <el-table-column prop="type" label="类型" width="100" />
      <el-table-column prop="protocol" label="协议" width="100" />
      <el-table-column prop="bizDomain" label="业务域" width="120" />
      <el-table-column prop="owner" label="负责人" width="120" />
      <el-table-column prop="createdBy" label="创建人" width="120" />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column prop="isAutomatedEnabled" label="自动化候选" width="120">
        <template #default="{ row }">
          <el-tag type="success" v-if="row.isAutomatedEnabled">已启用</el-tag>
          <el-tag type="info" v-else>关闭</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        background
        layout="prev, pager, next, sizes, total"
        :page-size="metaPagination.size"
        :current-page="metaPagination.current"
        :page-sizes="[10, 20, 50]"
        :total="filteredMetaItems.length"
        @size-change="handleMetaPageSizeChange"
        @current-change="handleMetaPageChange"
      />
    </div>
  </el-card>

  <el-dialog title="新增接口元数据" :visible.sync="showMetaDialog" width="700px">
    <el-form :model="newMeta" label-position="top">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="地址">
            <el-input v-model="newMeta.address" placeholder="请输入接口地址" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="应用名称">
            <el-input v-model="newMeta.appName" placeholder="请输入应用名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="接口类型">
            <el-select v-model="newMeta.type" placeholder="请选择类型">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
              <el-option label="DELETE" value="DELETE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="协议">
            <el-select v-model="newMeta.protocol" placeholder="请选择协议">
              <el-option label="HTTP" value="HTTP" />
              <el-option label="HTTPS" value="HTTPS" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="业务域">
            <el-select v-model="newMeta.bizDomain" placeholder="请选择业务域">
              <el-option label="营销" value="营销" />
              <el-option label="交易" value="交易" />
              <el-option label="AI 数据" value="AI 数据" />
              <el-option label="财务" value="财务" />
              <el-option label="测试" value="测试" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="负责人">
            <el-input v-model="newMeta.owner" placeholder="请输入负责人" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="自动化候选">
            <el-switch v-model="newMeta.isAutomatedEnabled" active-text="启用" inactive-text="关闭" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="入参模板">
            <el-input type="textarea" :rows="4" v-model="newMeta.latestParams" placeholder='{"id":123}' />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="showMetaDialog = false">取消</el-button>
      <el-button type="primary" @click="saveMeta">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog title="批量修改负责人" :visible.sync="showOwnerDialog" width="500px">
    <el-form label-position="top">
      <el-form-item label="新的负责人">
        <el-select v-model="ownerToAssign" placeholder="请选择负责人">
          <el-option v-for="item in ownerOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showOwnerDialog = false">取消</el-button>
      <el-button type="primary" @click="confirmBatchOwner">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const STORAGE_KEY_META_ITEMS = 'meta_items'
const STORAGE_KEY_AUTOMATED_CASES = 'automated_cases'
const STORAGE_KEY_META_OP_LOGS = 'meta_operation_logs'

const router = useRouter()
const currentUser = reactive({ username: 'guest', userType: '1' })
const isAdmin = computed(() => currentUser.userType === '2')

const metaFilters = reactive({ address: '', appName: '', bizDomain: '', protocol: '', createdBy: '' })
const metaPagination = reactive({ current: 1, size: 10 })
const selectedMetaRows = ref([])
const showMetaDialog = ref(false)
const showOwnerDialog = ref(false)
const ownerToAssign = ref('')
const ownerOptions = ['张三', '李四', '王五', '负责人A']
const opLogs = ref([])
const historyVisible = ref(false)
const historyFilters = reactive({ address: '', result: '', statusCode: '' })
const historyPagination = reactive({ current: 1, size: 10 })
const metaTable = ref(null)

const newMeta = reactive({
  address: '',
  appName: '',
  type: 'GET',
  protocol: 'HTTP',
  bizDomain: '营销',
  owner: '',
  isAutomatedEnabled: true,
  latestParams: '{\n  "id": 123\n}'
})

const defaultMetaItems = [
  {
    id: 'm1',
    appName: '营销系统',
    address: '/api/marketing/list',
    type: 'GET',
    protocol: 'HTTP',
    bizDomain: '营销',
    owner: '张三',
    createdBy: 'user1',
    createdAt: '2026-06-10 10:00:00',
    isAutomatedEnabled: true,
    latestParams: '{"page":1,"size":20}',
    deletedAt: null
  },
  {
    id: 'm2',
    appName: '交易系统',
    address: '/api/trade/create',
    type: 'POST',
    protocol: 'HTTPS',
    bizDomain: '交易',
    owner: '李四',
    createdBy: 'user2',
    createdAt: '2026-06-10 11:20:00',
    isAutomatedEnabled: false,
    latestParams: '{"amount":100,"currency":"CNY"}',
    deletedAt: null
  },
  {
    id: 'm3',
    appName: 'AI 数据',
    address: '/api/ai/predict',
    type: 'POST',
    protocol: 'HTTP',
    bizDomain: 'AI 数据',
    owner: '王五',
    createdBy: 'admin',
    createdAt: '2026-06-09 17:40:00',
    isAutomatedEnabled: true,
    latestParams: '{"text":"hello"}',
    deletedAt: null
  },
  {
    id: 'm4',
    appName: '财务系统',
    address: '/api/account/balance',
    type: 'GET',
    protocol: 'HTTPS',
    bizDomain: '财务',
    owner: '张三',
    createdBy: 'user1',
    createdAt: '2026-06-08 14:05:00',
    isAutomatedEnabled: true,
    latestParams: '{"accountId":12345}',
    deletedAt: null
  },
  {
    id: 'm5',
    appName: '测试平台',
    address: '/api/test/status',
    type: 'GET',
    protocol: 'HTTP',
    bizDomain: '测试',
    owner: '李四',
    createdBy: 'user2',
    createdAt: '2026-06-07 12:30:00',
    isAutomatedEnabled: false,
    latestParams: '{"caseId":5678}',
    deletedAt: null
  }
]

const metaItems = ref([])
const historyRecords = ref([
  {
    createdAt: '2026-06-10 15:00:00',
    url: '/api/marketing/list',
    statusCode: 200,
    result: '成功',
    duration: 120,
    requestSnapshot: { page: 1, size: 20 },
    responseSnapshot: { code: 0, data: [] }
  }
])

const loadUserInfo = () => {
  try {
    const raw = localStorage.getItem('userInfo')
    if (raw) {
      const info = JSON.parse(raw)
      currentUser.username = info.username || info.name || 'guest'
      currentUser.userType = String(info.userType || '1')
    }
  } catch {
    currentUser.username = 'guest'
    currentUser.userType = '1'
  }
}

const loadMetaItems = () => {
  try {
    const saved = localStorage.getItem(STORAGE_KEY_META_ITEMS)
    metaItems.value = saved ? JSON.parse(saved) : defaultMetaItems
  } catch {
    metaItems.value = defaultMetaItems
  }
}

const saveMetaItems = () => {
  localStorage.setItem(STORAGE_KEY_META_ITEMS, JSON.stringify(metaItems.value))
}

const loadOpLogs = () => {
  try {
    const saved = localStorage.getItem(STORAGE_KEY_META_OP_LOGS)
    opLogs.value = saved ? JSON.parse(saved) : []
  } catch {
    opLogs.value = []
  }
}

const goBack = () => {
  router.push('/layout/Apihub')
}

const saveOpLogs = () => {
  localStorage.setItem(STORAGE_KEY_META_OP_LOGS, JSON.stringify(opLogs.value))
}

const addOpLog = (action, details) => {
  const record = {
    id: `log_${Date.now()}`,
    action,
    details,
    operator: currentUser.username,
    time: new Date().toISOString()
  }
  opLogs.value.unshift(record)
  saveOpLogs()
}

const filteredMetaItems = computed(() => {
  return metaItems.value.filter((item) => {
    if (item.deletedAt) {
      return false
    }
    const matchesAddress = !metaFilters.address || item.address.toLowerCase().includes(metaFilters.address.toLowerCase())
    const matchesApp = !metaFilters.appName || item.appName === metaFilters.appName
    const matchesBiz = !metaFilters.bizDomain || item.bizDomain === metaFilters.bizDomain
    const matchesProtocol = !metaFilters.protocol || item.protocol === metaFilters.protocol
    const matchesCreatedBy = !metaFilters.createdBy || item.createdBy === metaFilters.createdBy
    return matchesAddress && matchesApp && matchesBiz && matchesProtocol && matchesCreatedBy
  })
})

const pagedMetaItems = computed(() => {
  const start = (metaPagination.current - 1) * metaPagination.size
  return filteredMetaItems.value.slice(start, start + metaPagination.size)
})

const uniqueAppNames = computed(() => [...new Set(metaItems.value.map((item) => item.appName))])
const uniqueBizDomains = computed(() => [...new Set(metaItems.value.map((item) => item.bizDomain))])
const uniqueCreators = computed(() => [...new Set(metaItems.value.map((item) => item.createdBy))])

const handleSelectionChange = async (val) => {
  if (val.length > 20) {
    ElMessage.warning('最多只能选择 20 个接口进行自动化')
    await nextTick()
    selectedMetaRows.value = val.slice(0, 20)
    if (metaTable.value) {
      metaTable.value.clearSelection()
      selectedMetaRows.value.forEach((row) => metaTable.value.toggleRowSelection(row, true))
    }
    return
  }
  selectedMetaRows.value = val
}

const isRowSelectable = (row) => {
  return row.isAutomatedEnabled && (isAdmin.value || row.createdBy === currentUser.username)
}

const selectionSelectable = (row) => {
  if (!isRowSelectable(row)) {
    return false
  }
  if (selectedMetaRows.value.length >= 20) {
    return selectedMetaRows.value.some((item) => item.id === row.id)
  }
  return true
}

const metaRowClass = ({ row }) => {
  if (!isRowSelectable(row)) {
    return 'disabled-row'
  }
  return ''
}

const batchGenerateAutomation = () => {
  if (!selectedMetaRows.value.length) {
    ElMessage.warning('请先选择要执行自动化的接口')
    return
  }

  const invalid = selectedMetaRows.value.find((row) => !row.isAutomatedEnabled)
  if (invalid) {
    ElMessage.warning('请选择已开启自动化候选的接口')
    return
  }

  const forbidden = selectedMetaRows.value.find((row) => !isRowSelectable(row))
  if (forbidden) {
    ElMessage.warning('只能选择您权限范围内的接口进行自动化')
    return
  }

  const generatedCases = selectedMetaRows.value.map((row) => ({
    caseId: `case_${Date.now()}_${row.id}`,
    name: `${row.appName} ${row.address}`,
    appName: row.appName,
    url: row.address,
    type: row.type,
    owner: row.owner,
    requestTemplate: row.latestParams || '{}',
    createdBy: currentUser.username,
    createTime: new Date().toISOString(),
    status: 'wait',
    statusText: '未执行'
  }))

  localStorage.setItem(STORAGE_KEY_AUTOMATED_CASES, JSON.stringify(generatedCases))
  ElMessage.success('自动化用例已生成，正在跳转自动化页面')
  router.push('/layout/automated')
}

const resetMetaFilters = () => {
  metaFilters.address = ''
  metaFilters.appName = ''
  metaFilters.bizDomain = ''
  metaFilters.protocol = ''
  metaFilters.createdBy = ''
  metaPagination.current = 1
}

const handleMetaPageSizeChange = (size) => {
  metaPagination.size = size
  metaPagination.current = 1
}

const handleMetaPageChange = (page) => {
  metaPagination.current = page
}

const saveMeta = () => {
  const newRecord = {
    id: `m_${Date.now()}`,
    address: newMeta.address,
    appName: newMeta.appName,
    type: newMeta.type,
    protocol: newMeta.protocol,
    bizDomain: newMeta.bizDomain,
    owner: newMeta.owner,
    createdBy: currentUser.username,
    createdAt: new Date().toISOString(),
    isAutomatedEnabled: newMeta.isAutomatedEnabled,
    latestParams: newMeta.latestParams,
    deletedAt: null
  }
  metaItems.value.unshift(newRecord)
  saveMetaItems()
  addOpLog('新增接口', `新增接口 ${newRecord.address}，应用：${newRecord.appName}`)
  showMetaDialog.value = false
  ElMessage.success('接口元数据已保存')
}

const confirmBatchOwner = () => {
  if (!ownerToAssign.value) {
    ElMessage.warning('请选择新的负责人')
    return
  }
  selectedMetaRows.value.forEach((row) => {
    const record = metaItems.value.find((item) => item.id === row.id)
    if (record) {
      record.owner = ownerToAssign.value
    }
  })
  saveMetaItems()
  addOpLog('批量修改负责人', `将 ${selectedMetaRows.value.length} 个接口负责人修改为 ${ownerToAssign.value}`)
  showOwnerDialog.value = false
  ElMessage.success('负责人已更新')
}

const batchDeleteMetadata = () => {
  selectedMetaRows.value.forEach((row) => {
    const record = metaItems.value.find((item) => item.id === row.id)
    if (record) {
      record.deletedAt = new Date().toISOString()
    }
  })
  saveMetaItems()
  addOpLog('批量逻辑删除', `逻辑删除 ${selectedMetaRows.value.length} 个接口，30 天内可恢复`)
  selectedMetaRows.value = []
  ElMessage.success('选中接口已标记为删除')
}

const formatJson = (value) => {
  try {
    return JSON.stringify(typeof value === 'string' ? JSON.parse(value) : value, null, 2)
  } catch {
    return typeof value === 'string' ? value : JSON.stringify(value)
  }
}

const filteredHistoryRecords = computed(() => {
  return historyRecords.value.filter((item) => {
    const matchesAddress = !historyFilters.address || item.url?.toLowerCase().includes(historyFilters.address.toLowerCase())
    const matchesResult = !historyFilters.result || item.result === historyFilters.result
    const matchesStatusCode = !historyFilters.statusCode || String(item.statusCode) === String(historyFilters.statusCode)
    return matchesAddress && matchesResult && matchesStatusCode
  })
})

const pagedHistoryRecords = computed(() => {
  const start = (historyPagination.current - 1) * historyPagination.size
  return filteredHistoryRecords.value.slice(start, start + historyPagination.size)
})


const handleHistorySizeChange = (size) => {
  historyPagination.size = size
  historyPagination.current = 1
}

const handleHistoryPageChange = (page) => {
  historyPagination.current = page
}

onMounted(() => {
  loadUserInfo()
  loadMetaItems()
  loadOpLogs()
})
</script>

<style scoped>
.section-card {
  padding: 24px;
  background: var(--paper, #fafafa);
  border: 1px solid var(--mute, #e5e5e5);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.title-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.back-btn {
  padding: 6px 8px;
}

.meta-actions {
  display: flex;
  gap: 12px;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.pagination-wrapper {
  text-align: right;
  margin-top: 16px;
}

:deep(.el-table) {
  border: 1px solid var(--mute, #e5e5e5);
}

:deep(.el-table__header th) {
  background-color: var(--paper, #fafafa) !important;
  font-weight: 500;
  color: var(--ghost, #a3a3a3) !important;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

:deep(.el-dialog) {
  border-radius: 0;
  border: 1px solid var(--mute, #e5e5e5);
}

.disabled-row {
  color: var(--ghost, #a3a3a3);
  background: var(--smoke, #f5f5f5);
}
</style>
