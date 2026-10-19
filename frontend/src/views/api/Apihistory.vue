<template>
  <div class="api-history-page">
    <div class="page-title">
      <div>
        <h2>测试记录</h2>
        <p>普通用户仅查看自身测试记录，管理员查看全部记录，支持筛选和分页。</p>
      </div>
    </div>

    <el-card class="history-card">
      <div class="history-filters">
        <el-input
          v-model="searchForm.keyword"
          placeholder="关键字：接口地址 / 结果 / 创建人"
          clearable
          style="width: 320px"
        />
        <el-select v-model="searchForm.status" placeholder="执行结果" clearable style="width: 200px">
          <el-option label="成功" value="success" />
          <el-option label="失败" value="fail" />
        </el-select>
        <el-button type="primary" @click="applyFilter">筛选</el-button>
        <el-button @click="resetFilter">重置</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="pagedRecords"
        border
        stripe
        style="width: 100%"
        empty-text="暂无测试记录"
      >
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="createdAt" label="测试时间" width="200" show-overflow-tooltip />
        <el-table-column prop="statusCode" label="状态码" width="120" align="center" />
        <el-table-column label="结果" width="120" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.result === '成功' ? 'success' : 'danger'">
              {{ scope.row.result }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="耗时(ms)" width="120" align="center" />
        <el-table-column type="expand">
          <template #default="props">
            <div class="expand-content">
              <div class="expand-block">
                <div class="expand-title">入参快照</div>
                <pre>{{ formatJson(props.row.requestSnapshot) }}</pre>
              </div>
              <div class="expand-block">
                <div class="expand-title">出参快照</div>
                <pre>{{ formatJson(props.row.outputSnapshot) }}</pre>
              </div>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="filteredRecords.length"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handlePageSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'

const STORAGE_KEY_API_TEST_RECORDS = 'api_test_records'

const loading = ref(false)
const searchForm = reactive({ keyword: '', status: '' })
const pageNum = ref(1)
const pageSize = ref(10)
const records = ref([])
const currentUser = reactive({ username: 'guest', userType: '1' })

const isAdmin = computed(() => currentUser.userType === '2')

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

const loadRecords = () => {
  loading.value = true
  try {
    const saved = localStorage.getItem(STORAGE_KEY_API_TEST_RECORDS)
    records.value = saved ? JSON.parse(saved) : []
    records.value.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}

const filteredRecords = computed(() => {
  let list = records.value.slice()

  if (!isAdmin.value) {
    list = list.filter((item) => item.creator === currentUser.username)
  }

  const keyword = searchForm.keyword.trim().toLowerCase()
  if (keyword) {
    list = list.filter((item) => {
      return [item.url, item.result, item.creator]
        .filter(Boolean)
        .some((field) => String(field).toLowerCase().includes(keyword))
    })
  }

  if (searchForm.status) {
    list = list.filter((item) => {
      return searchForm.status === 'success' ? item.result === '成功' : item.result === '失败'
    })
  }

  return list
})

const pagedRecords = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return filteredRecords.value.slice(start, start + pageSize.value)
})

const formatJson = (value) => {
  try {
    return JSON.stringify(typeof value === 'string' ? JSON.parse(value) : value, null, 2)
  } catch {
    return typeof value === 'string' ? value : JSON.stringify(value)
  }
}

const applyFilter = () => {
  pageNum.value = 1
}

const resetFilter = () => {
  searchForm.keyword = ''
  searchForm.status = ''
  pageNum.value = 1
}

const handlePageSizeChange = (size) => {
  pageSize.value = size
  pageNum.value = 1
}

const handlePageChange = (page) => {
  pageNum.value = page
}

onMounted(() => {
  loadUserInfo()
  loadRecords()
})
</script>

<style scoped>
.api-history-dialog .el-dialog__body {
  padding: 20px 24px 24px;
}

:deep(.el-dialog) {
  border-radius: 0;
  border: 1px solid var(--mute, #e5e5e5);
}

.history-dialog-description {
  margin-bottom: 18px;
  color: var(--ghost, #a3a3a3);
  font-size: 11px;
}

.history-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 20px;
}

.expand-content {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.expand-block {
  background: var(--smoke, #f5f5f5);
  border: 1px solid var(--mute, #e5e5e5);
  padding: 16px;
}

.expand-title {
  font-weight: 500;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--ink, #0a0a0a);
}

.pagination-wrapper {
  text-align: right;
  margin-top: 18px;
}
</style>
