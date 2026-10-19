<template>
  <div class="audit-wrap">
    <div class="audit-card">
      <div class="card-header">
        <h2>待审批注册申请</h2>
        <span class="sub-desc">审核新用户的注册申请</span>
      </div>
      <div class="card-body">
        <!-- 搜索与筛选栏 -->
        <div class="filter-bar">
          <el-input v-model="searchKeyword" placeholder="搜索用户名 / 任务ID" clearable style="width: 260px" @keyup.enter="applyFilter">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-input v-model="filterDate" placeholder="申请时间(YYYY-MM-DD)" clearable style="width: 200px" />
          <el-button type="primary" @click="applyFilter">筛选</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>
        <el-table :data="filteredData" border style="width:100%" v-loading="tableLoading">
          <el-table-column label="任务ID" prop="taskId" width="220"></el-table-column>
          <el-table-column label="申请用户名" prop="username"></el-table-column>
          <el-table-column label="申请时间" prop="createTime"></el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="scope">
              <el-button type="success" size="small" @click="handlePass(scope.row.taskId)">通过审批</el-button>
              <el-button type="danger" size="small" @click="handleReject(scope.row.taskId)">拒绝申请</el-button>
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
import { getPendingTasks, approveTask, rejectTask } from '@/api/admin'

const tableLoading = ref(false)
const tableData = ref([])

// 搜索与筛选
const searchKeyword = ref('')
const filterDate = ref('')
const applyFilter = function() { /* 响应式 filteredData 自动计算，此处保留入口 */ }
const resetFilter = function() {
  searchKeyword.value = ''
  filterDate.value = ''
}
const filteredData = computed(function() {
  const kw = (searchKeyword.value || '').trim().toLowerCase()
  const date = (filterDate.value || '').trim()
  return tableData.value.filter(function(row) {
    let ok = true
    if (kw) {
      const u = (row.username || '').toLowerCase()
      const t = String(row.taskId || '').toLowerCase()
      ok = ok && (u.indexOf(kw) >= 0 || t.indexOf(kw) >= 0)
    }
    if (date) {
      ok = ok && String(row.createTime || '').indexOf(date) >= 0
    }
    return ok
  })
})

const loadTaskList = function() {
  tableLoading.value = true
  return getPendingTasks().then(function(res) {
    tableData.value = res.data
    tableLoading.value = false
  })
}

const handlePass = function(taskId) {
  ElMessageBox.confirm('确认通过该用户注册申请？', '操作确认', { type: 'success' })
  .then(function() {
    return approveTask({ taskId })
  })
  .then(function() {
    ElMessage.success('审批已通过')
    loadTaskList()
  })
}

const handleReject = function(taskId) {
  ElMessageBox.confirm('确认拒绝该用户注册申请？', '警告', { type: 'warning' })
  .then(function() {
    return rejectTask({ taskId })
  })
  .then(function() {
    ElMessage.success('已拒绝注册申请')
    loadTaskList()
  }).catch(function(){})
}

onMounted(function() {
  loadTaskList()
})
</script>

<style scoped>
.audit-wrap {
  width: 100%;
}

.filter-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.audit-card {
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
