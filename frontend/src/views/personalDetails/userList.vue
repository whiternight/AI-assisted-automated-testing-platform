<template>
    <div class="page-wrap">
      <el-card shadow="hover">
        <template #header>
          <div class="card-header">
            <span>系统用户管理</span>
          </div>
        </template>
  
        <!-- 筛选栏 -->
        <el-row :gutter="20" class="filter-row">
          <el-col :span="6">
            <el-input v-model="filterParams.username" placeholder="用户名筛选" clearable />
          </el-col>
          <el-col :span="6">
            <el-select v-model="filterParams.status" placeholder="账号状态" clearable>
              <el-option label="待激活" :value="0" />
              <el-option label="已激活" :value="1" />
              <el-option label="已锁定" :value="2" />
            </el-select>
          </el-col>
          <el-col :span="6">
            <el-button type="primary" @click="loadList">查询</el-button>
            <el-button @click="resetFilter">重置</el-button>
          </el-col>
        </el-row>
  
        <el-table :data="tableData" border stripe v-loading="loading" class="mt-4">
          <el-table-column label="用户ID" prop="userId" />
          <el-table-column label="用户名" prop="username" />
          <el-table-column label="真实姓名" prop="realName" />
          <el-table-column label="部门" prop="departmentName" />
          <el-table-column label="账号状态" prop="status">
            <template #default="scope">
              <span v-if="scope.row.status === 0" class="text-orange">待激活</span>
              <span v-if="scope.row.status === 1" class="text-green">已激活</span>
              <span v-if="scope.row.status === 2" class="text-red">已锁定</span>
            </template>
          </el-table-column>
          <el-table-column label="角色" prop="roleName" />
          <el-table-column label="操作" width="200" v-if="canEdit">
            <template #default="scope">
              <el-button type="primary" size="small" @click="openEdit(scope.row)">编辑角色部门</el-button>
            </template>
          </el-table-column>
        </el-table>
  
        <!-- 分页 -->
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, sizes, prev, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
          class="mt-4"
        />
      </el-card>
  
      <!-- 编辑弹窗 -->
      <el-dialog v-model="dialogVisible" title="编辑用户角色部门" width="500px">
        <el-form :model="editForm" label-width="100px">
          <el-form-item label="所属部门">
            <el-select v-model="editForm.departmentId" placeholder="选择部门" style="width:100%">
              <el-option v-for="dept in deptList" :key="dept.id" :label="dept.name" :value="dept.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="分配角色">
            <el-select v-model="editForm.roleIds" multiple placeholder="选择角色" style="width:100%">
              <el-option v-for="role in roleList" :key="role.id" :label="role.name" :value="role.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitEdit" :loading="editLoading">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </template>
  
  <script setup>
  import { ref, reactive, onMounted, computed } from 'vue'
  import { ElMessage } from 'element-plus'
  import { getUserList, getUserDetail, updateUserRoles, getRoleList, getDeptList } from '@/api/admin'
  
  // 读取登录用户信息（统一替换为sessionStorage，和全局存储一致）
  const userInfoStr = sessionStorage.getItem('userInfo')
  const loginUser = userInfoStr ? JSON.parse(userInfoStr) : null
  
  // 权限控制：观察员不可编辑；管理员全量编辑；测试工程师仅可编辑同部门
  const canEdit = computed(() => {
    if (!loginUser) return false
    if (loginUser.userRole === 'observer') return false
    return loginUser.userRole === 'admin' || loginUser.userRole === 'tester'
  })
  
  const loading = ref(false)
  const editLoading = ref(false)
  const tableData = ref([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  
  // 筛选条件
  const filterParams = reactive({
    username: '',
    departmentId: '',
    roleId: '',
    status: ''
  })
  
  // 下拉数据源
  const roleList = ref([])
  const deptList = ref([])
  
  // 编辑弹窗
  const dialogVisible = ref(false)
  const editForm = reactive({
    userId: null,
    departmentId: '',
    roleIds: []
  })
  
  // 加载角色下拉
  const loadRole = async () => {
    try {
      const res = await getRoleList()
      if (res.code === 200) roleList.value = res.data || []
    } catch (err) {
      ElMessage.error('角色数据加载失败')
      console.error(err)
    }
  }
  
  // 加载部门下拉
  const loadDept = async () => {
    try {
      const res = await getDeptList()
      if (res.code === 200) deptList.value = res.data || []
    } catch (err) {
      ElMessage.error('部门数据加载失败')
      console.error(err)
    }
  }
  
  // 加载用户分页列表
  const loadList = async () => {
    loading.value = true
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...filterParams
    }
    try {
      const res = await getUserList(params)
      if (res.code === 200) {
        tableData.value = res.data.list || []
        total.value = res.data.total || 0
      } else {
        ElMessage.warning(res.message || '查询用户列表失败')
        tableData.value = []
        total.value = 0
      }
    } catch (err) {
      ElMessage.error('网络异常，拉取用户列表失败')
      console.error(err)
      tableData.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }
  
  // 重置筛选条件
  const resetFilter = () => {
    filterParams.username = ''
    filterParams.departmentId = ''
    filterParams.roleId = ''
    filterParams.status = ''
    pageNum.value = 1
    loadList()
  }
  
  // 打开编辑弹窗，回显详情
  const openEdit = async (row) => {
    // 测试工程师权限拦截：只能编辑本部门
    if (loginUser.userRole === 'tester' && row.departmentId !== loginUser.departmentId) {
      ElMessage.warning('仅可编辑本部门用户')
      return
    }
    try {
      const res = await getUserDetail(row.userId)
      if (res.code === 200) {
        const info = res.data
        editForm.userId = info.userId
        editForm.departmentId = info.departmentId
        editForm.roleIds = info.roleIds || []
        dialogVisible.value = true
      } else {
        ElMessage.warning(res.message || '获取用户详情失败')
      }
    } catch (err) {
      ElMessage.error('请求用户详情异常')
      console.error(err)
    }
  }
  
  // 提交编辑保存
  const submitEdit = async () => {
    editLoading.value = true
    try {
      const params = {
        userId: editForm.userId,
        departmentId: editForm.departmentId,
        roleIds: editForm.roleIds
      }
      const res = await updateUserRoles(params)
      if (res.code === 200) {
        ElMessage.success('用户信息修改成功')
        dialogVisible.value = false
        loadList() // 刷新列表
      } else {
        ElMessage.error(res.message || '修改失败')
      }
    } catch (err) {
      ElMessage.error('保存修改请求异常')
      console.error(err)
    } finally {
      editLoading.value = false
    }
  }
  
  // 页面初始化
  onMounted(async () => {
    await loadRole()
    await loadDept()
    loadList()
  })
  </script>
  
  <style scoped>
  .page-wrap {
    padding: 0;
    background: var(--paper, #fafafa);
    min-height: calc(100vh - 60px);
  }

  :deep(.el-card) {
    border: 1px solid var(--mute, #e5e5e5);
    border-radius: 0;
    box-shadow: none;
    background: var(--paper, #fafafa);
  }

  :deep(.el-card__header) {
    border-bottom: 1px solid var(--mute, #e5e5e5);
    padding: 14px 20px;
  }

  .card-header {
    font-size: 13px;
    font-weight: 500;
    color: var(--ink, #0a0a0a);
  }

  .filter-row {
    margin-bottom: 16px;
  }

  .mt-4 {
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

  .text-orange {
    color: var(--ghost, #a3a3a3);
  }
  .text-green {
    color: var(--ink, #0a0a0a);
  }
  .text-red {
    color: #e53e3e;
  }

  :deep(.el-dialog) {
    border-radius: 0;
    border: 1px solid var(--mute, #e5e5e5);
  }
  </style>
