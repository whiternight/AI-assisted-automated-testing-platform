<template>
  <div class="page-wrap">
    <div class="page-card">
      <div class="card-header">
        <h2>用户激活待审批列表</h2>
        <span class="sub-desc">审核用户激活申请</span>
      </div>
      <div class="card-body">
        <!-- 搜索与筛选栏 -->
        <div class="filter-bar">
          <el-input v-model="searchKeyword" placeholder="搜索用户名 / 真实姓名 / 任务ID" clearable style="width: 280px" @keyup.enter="applyFilter">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="filterDept" placeholder="部门筛选" clearable filterable style="width: 180px">
            <el-option v-for="d in deptOptions" :key="d" :label="d" :value="d" />
          </el-select>
          <el-button type="primary" @click="applyFilter">筛选</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>
        <el-table :data="filteredData" border stripe v-loading="loading">
          <el-table-column label="任务ID" prop="taskId" />
          <el-table-column label="用户名" prop="username" />
          <el-table-column label="真实姓名" prop="realName" />
          <el-table-column label="部门" prop="departmentName" />
          <el-table-column label="申请角色" prop="roleName" />
          <el-table-column label="操作" width="180">
            <template #default="scope">
              <el-button type="success" size="small" @click="handleApprove(scope.row.taskId)">通过</el-button>
              <el-button type="danger" size="small" @click="handleReject(scope.row.taskId)">拒绝</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
// 补全导入待办接口
import { getPendingTasks, approveTask, rejectTask } from '@/api/admin'

const loading = ref(false)
const tableData = ref([])

// 搜索与筛选
const searchKeyword = ref('')
const filterDept = ref('')
const applyFilter = function() { /* 响应式自动计算 */ }
const resetFilter = function() {
  searchKeyword.value = ''
  filterDept.value = ''
}
// 部门选项由数据动态推导
const deptOptions = computed(function() {
  const set = new Set()
  tableData.value.forEach(function(r) {
    if (r.departmentName) set.add(r.departmentName)
  })
  return Array.from(set)
})
const filteredData = computed(function() {
  const kw = (searchKeyword.value || '').trim().toLowerCase()
  const dept = (filterDept.value || '').trim()
  return tableData.value.filter(function(row) {
    let ok = true
    if (kw) {
      const u = (row.username || '').toLowerCase()
      const rn = (row.realName || '').toLowerCase()
      const t = String(row.taskId || '').toLowerCase()
      ok = ok && (u.indexOf(kw) >= 0 || rn.indexOf(kw) >= 0 || t.indexOf(kw) >= 0)
    }
    if (dept) {
      ok = ok && (row.departmentName || '') === dept
    }
    return ok
  })
})

// 加载待审批列表
const loadList = async () => {
  loading.value = true
  try {
    const res = await getPendingTasks()
    console.log('待审批列表返回', res)
    // 适配后端标准格式 {code:200, data:[]}
    if (res.code === 200) {
      tableData.value = Array.isArray(res.data) ? res.data : []
    } else {
      ElMessage.warning(res.message || '获取待办列表失败')
      tableData.value = []
    }
  } catch (err) {
    console.error('加载待办异常', err)
    ElMessage.error('网络异常，拉取审批列表失败')
    tableData.value = []
  } finally {
    // 无论拿到空数组、报错、正常数据，都强制关闭loading转圈
    loading.value = false
  }
}

// 通过审批
const handleApprove = (taskId) => {
  ElMessageBox.confirm('确认批准该用户激活？', '提示', { type: 'success' })
    .then(async () => {
      try {
        const res = await approveTask({ taskId })
        if (res.code === 200) {
          ElMessage.success('审批通过')
          loadList() // 刷新列表
        } else {
          ElMessage.error(res.message || '审批操作失败')
        }
      } catch (err) {
        ElMessage.error('审批请求异常')
      }
    })
}

// 拒绝审批
const handleReject = (taskId) => {
  ElMessageBox.confirm('确认拒绝该用户激活？', '提示', { type: 'warning' })
    .then(async () => {
      try {
        const res = await rejectTask({ taskId })
        if (res.code === 200) {
          ElMessage.success('已拒绝申请')
          loadList() // 刷新列表
        } else {
          ElMessage.error(res.message || '拒绝操作失败')
        }
      } catch (err) {
        ElMessage.error('拒绝请求异常')
      }
    })
}

onMounted(() => loadList())
</script>

<style scoped>
.page-wrap {
  padding: 0;
  background: var(--paper, #fafafa);
  min-height: calc(100vh - 60px);
}

.filter-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.page-card {
  border: 1px solid var(--mute, #e5e5e5);
  background: var(--paper, #fafafa);
}

.card-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--mute, #e5e5e5);
}

.card-header h2 {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink, #0a0a0a);
  margin: 0;
}

.card-header .sub-desc {
  font-size: 11px;
  color: var(--ghost, #a3a3a3);
}

.card-body {
  padding: 16px 20px;
}

:deep(.el-table) {
  border: 1px solid var(--mute, #e5e5e5);
  margin-top: 0;
}

:deep(.el-table__header th) {
  background-color: var(--paper, #fafafa) !important;
  font-weight: 500;
  color: var(--ghost, #a3a3a3) !important;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}
</style>