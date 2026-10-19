<template>
    <div class="history-page-container">
        <!-- 页面标题 -->
        <div class="page-header">
            <h2>历史记录</h2>
        </div>

        <!-- 搜索与筛选区域 -->
        <div class="search-wrapper">
            <el-form :inline="true" :model="searchForm" class="search-form">
                <el-form-item label="操作人">
                    <el-input v-model="searchForm.username" placeholder="请输入操作人" clearable style="width: 200px" />
                </el-form-item>

                <el-form-item label="操作类型">
                    <el-select v-model="searchForm.type" placeholder="请选择操作类型" clearable style="width: 180px">
                        <el-option label="新增" value="add" />
                        <el-option label="编辑" value="edit" />
                        <el-option label="删除" value="delete" />
                        <el-option label="查询" value="query" />
                        <el-option label="登录" value="login" />
                        <el-option label="登出" value="logout" />
                    </el-select>
                </el-form-item>

                <el-form-item label="操作时间">
                    <el-date-picker v-model="searchForm.timeRange" type="daterange" range-separator="至"
                        start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD HH:mm:ss" />
                </el-form-item>

                <el-form-item>
                    <el-button type="primary" @click="getHistoryList">
                        <el-icon>
                            <Search />
                        </el-icon> 查询
                    </el-button>
                    <el-button @click="resetSearch">
                        <el-icon>
                            <Refresh />
                        </el-icon> 重置
                    </el-button>
                </el-form-item>
            </el-form>
        </div>

        <!-- 表格区域 -->
        <div class="table-wrapper">
            <el-table v-loading="loading" :data="historyList" border stripe style="width: 100%" empty-text="暂无历史记录">
                <el-table-column label="序号" type="index" width="80" align="center" />

                <el-table-column prop="username" label="操作人" align="center" min-width="120" />

                <el-table-column prop="ip" label="操作IP" align="center" min-width="150" />

                <el-table-column prop="type" label="操作类型" align="center" width="120">
                    <template #default="scope">
                        <el-tag :type="getTypeTagType(scope.row.type)">
                            {{ getTypeText(scope.row.type) }}
                        </el-tag>
                    </template>
                </el-table-column>

                <el-table-column prop="content" label="操作内容" align="left" min-width="250" />

                <el-table-column prop="createTime" label="操作时间" align="center" width="200" />

                <el-table-column label="操作" align="center" width="120">
                    <template #default="scope">
                        <el-button type="text" icon="view" @click="viewDetail(scope.row)">
                            查看
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>

            <!-- 分页 -->
            <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
                :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
                @size-change="getHistoryList" @current-change="getHistoryList"
                style="text-align: right; margin-top: 20px" />
        </div>

        <!-- 查看详情弹窗 -->
        <el-dialog v-model="detailVisible" title="历史记录详情" width="600px" append-to-body>
            <div class="detail-content">
                <p><span class="label">操作人：</span>{{ detailInfo.username }}</p>
                <p><span class="label">操作IP：</span>{{ detailInfo.ip }}</p>
                <p><span class="label">操作类型：</span>{{ getTypeText(detailInfo.type) }}</p>
                <p><span class="label">操作时间：</span>{{ detailInfo.createTime }}</p>
                <p><span class="label">操作内容：</span></p>
                <div class="content-box">{{ detailInfo.content }}</div>
            </div>
            <template #footer>
                <el-button @click="detailVisible = false">关闭</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, View } from '@element-plus/icons-vue'

// 加载状态
const loading = ref(false)

// 搜索条件
const searchForm = reactive({
    username: '',
    type: '',
    timeRange: []
})

// 分页
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 列表数据
const historyList = ref([])

// 详情弹窗
const detailVisible = ref(false)
const detailInfo = ref({})

// 模拟数据 - 实际项目替换为接口
const mockData = [
    { id: 1, username: '张三', ip: '192.168.1.10', type: 'add', content: '新增用户：李四，ID：1001', createTime: '2025-05-20 14:30:22' },
    { id: 2, username: '管理员', ip: '127.0.0.1', type: 'edit', content: '编辑系统配置：修改超时时间为30分钟', createTime: '2025-05-20 10:15:33' },
    { id: 3, username: '李四', ip: '192.168.1.11', type: 'delete', content: '删除订单：订单号NO20250520001', createTime: '2025-05-19 16:40:10' },
    { id: 4, username: '张三', ip: '192.168.1.10', type: 'login', content: '用户登录系统，登录成功', createTime: '2025-05-19 09:20:15' },
    { id: 5, username: '管理员', ip: '127.0.0.1', type: 'logout', content: '用户安全退出系统', createTime: '2025-05-18 18:00:00' },
]

// 获取列表
const getHistoryList = () => {
    loading.value = true
    // 这里替换成你的接口请求
    setTimeout(() => {
        historyList.value = mockData
        total.value = mockData.length
        loading.value = false
    }, 300)
}

// 重置搜索
const resetSearch = () => {
    searchForm.username = ''
    searchForm.type = ''
    searchForm.timeRange = []
    pageNum.value = 1
    getHistoryList()
}

// 操作类型标签颜色
const getTypeTagType = (type) => {
    const map = {
        add: 'success',
        edit: 'warning',
        delete: 'danger',
        query: 'info',
        login: 'primary',
        logout: ''
    }
    return map[type] || ''
}

// 操作类型文字
const getTypeText = (type) => {
    const map = {
        add: '新增',
        edit: '编辑',
        delete: '删除',
        query: '查询',
        login: '登录',
        logout: '登出'
    }
    return map[type] || type
}

// 查看详情
const viewDetail = (row) => {
    detailInfo.value = { ...row }
    detailVisible.value = true
}

// 初始化
onMounted(() => {
    getHistoryList()
})
</script>

<style scoped>
.history-page-container {
    padding: 0;
    background: var(--paper, #fafafa);
    min-height: calc(100vh - 60px);
}

.page-header {
    margin-bottom: 20px;
    border-bottom: 1px solid var(--mute, #e5e5e5);
    padding-bottom: 16px;
}

.page-header h2 {
    margin: 0;
    font-size: 13px;
    font-weight: 500;
    color: var(--ink, #0a0a0a);
}

.search-wrapper {
    margin-bottom: 20px;
    padding: 12px 16px;
    background: var(--paper, #fafafa);
    border: 1px solid var(--mute, #e5e5e5);
}

.table-wrapper {
    background: var(--paper, #fafafa);
    padding: 10px 0;
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

/* 详情样式 */
.detail-content {
    padding: 10px;
    line-height: 2;
}

.label {
    display: inline-block;
    width: 100px;
    color: var(--ghost, #a3a3a3);
    font-weight: 500;
    font-size: 10px;
    text-transform: uppercase;
    letter-spacing: 0.06em;
}

.content-box {
    padding: 12px;
    background: var(--smoke, #f5f5f5);
    border: 1px solid var(--mute, #e5e5e5);
    min-height: 80px;
    white-space: pre-wrap;
    font-family: 'JetBrains Mono', monospace;
    font-size: 11px;
    color: var(--ink, #0a0a0a);
}
</style>