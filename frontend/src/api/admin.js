import request from '@/utils/request'

// ========== 认证管理接口（/auth）==========
// 文档路径: /auth/login  → 前端写: /api/auth/login
export const login = (data) => {
  return request.post('/api/auth/login', data, { skipToken: true })
}

// 文档路径: /auth/register  → 前端写: /api/auth/register
export const register = (data) => {
  return request.post('/api/auth/register', data, { skipToken: true })
}

// 文档路径: /auth/logout  → 前端写: /api/auth/logout
export const logout = () => {
  return request.post('/api/auth/logout')
}

// 文档路径: /auth/getUser  → 前端写: /api/auth/getUser (注意文档是GET方法)
export const getUserDetail = () => {
  return request.get('/api/auth/getUser')
}

// 文档路径: /auth/change-password  → 前端写: /api/auth/change-password
export const changePassword = (data) => {
  return request.post('/api/auth/change-password', data)
}

// ========== 角色管理接口（/role）==========
// 文档路径: /role/list  → 前端写: /api/role/list
export const getRoleList = (config) => {
  return request.get('/api/role/list', { skipToken: true, silentError: true, ...config })
}

// ========== 部门管理接口（/department）==========
// 文档路径: /department/list  → 前端写: /api/department/list
export const getDeptList = (config) => {
  return request.get('/api/department/list', { skipToken: true, silentError: true, ...config })
}

// 获取部门-应用树状结构
export const getDepartmentTree = () => {
  return request.get('/api/application/department-tree')
}

// ========== 应用管理接口（/application）==========
// 根据部门批量查询应用列表
export const getApplicationsByDept = (data) => {
  return request.post('/api/application/listByDepartment', data)
}

// ========== 管理员审批接口（/api-admin）==========
// 文档路径: /api-admin/pending-tasks  → 前端写: /api/api-admin/pending-tasks
export const getPendingTasks = () => {
  return request.get('/api/api-admin/pending-tasks')
}

// 文档路径: /api-admin/approve  → 前端写: /api/api-admin/approve
export const approveTask = (data) => {
  return request.post('/api/api-admin/approve', data)
}

// 文档路径: /api-admin/reject  → 前端写: /api/api-admin/reject
export const rejectTask = (data) => {
  return request.post('/api/api-admin/reject', data)
}

// ========== 用户管理接口（/user）==========
// 文档路径: /user/list  → 前端写: /api/user/list
export const getUserList = (params) => {
  return request.post('/api/user/list', params)
}

// 文档路径: /user/detail  → 前端写: /api/user/detail
export const getUserDetailById = (data) => {
  return request.post('/api/user/detail', data)
}

// 文档路径: /user/update-roles  → 前端写: /api/user/update-roles
export const updateUserRoles = (data) => {
  return request.post('/api/user/update-roles', data)
}

// ========== 用例管理接口（/api-case）==========
// 文档路径: /api-case/http  → 前端写: /api/api-case/http
export const createHttpCase = (data) => {
  return request.post('/api/api-case/http', data)
}

// 文档路径: /api-case/dubbo  → 前端写: /api/api-case/dubbo
export const createDubboCase = (data) => {
  return request.post('/api/api-case/dubbo', data)
}

// 文档路径: /api-case/http/{id}  → 前端写: /api/api-case/http/${id}
export const updateHttpCase = (id, data) => {
  return request.put(`/api/api-case/http/${id}`, data)
}

// 文档路径: /api-case/dubbo/{id}  → 前端写: /api/api-case/dubbo/${id}
export const updateDubboCase = (id, data) => {
  return request.put(`/api/api-case/dubbo/${id}`, data)
}

// 文档路径: /api-case/{id}  → 前端写: /api/api-case/${id}
export const getCaseDetail = (id) => {
  return request.get(`/api/api-case/${id}`)
}

// 文档路径: /api-case/list  → 前端写: /api/api-case/list
export const getCaseList = (params) => {
  return request.get('/api/api-case/list', { params })
}

// 文档路径: /api-case/{id} (DELETE)  → 前端写: /api/api-case/${id}
export const deleteCase = (id) => {
  return request.delete(`/api/api-case/${id}`)
}

// 文档路径: /api-case/batch (DELETE)  → 前端写: /api/api-case/batch
export const batchDeleteCases = (ids) => {
  return request.delete('/api/api-case/batch', { data: { ids } })
}

// 文档路径: /api-case/{id}/debug  → 前端写: /api/api-case/${id}/debug
export const debugCase = (id, data) => {
  return request.post(`/api/api-case/${id}/debug`, data)
}

// 文档路径: /api-case/check-duplicate  → 前端写: /api/api-case/check-duplicate
export const checkDuplicate = (data) => {
  return request.post('/api/api-case/check-duplicate', data)
}

// 文档路径: /api-case/{id}/auto (PATCH)  → 前端写: /api/api-case/${id}/auto
export const updateAutoSwitch = (id, isAutomationEnabled) => {
  return request.patch(`/api/api-case/${id}/auto`, null, {
    params: {
      isAutomationEnabled
    },
    skipToken: false
  })
}

// 文档路径: /api-case/batch/auto (PATCH)  → 前端写: /api/api-case/batch/auto
export const batchUpdateAutoSwitch = (ids, isAutomationEnabled) => {
  return request.patch('/api/api-case/batch/auto', { ids }, {
    params: { isAutomationEnabled }
  })
}

// 文档路径: /api-case/batch/owner (PUT)  → 前端写: /api/api-case/batch/owner
export const batchChangeOwner = (ids, ownerId) => {
  return request.put('/api/api-case/batch/owner', { ids }, {
    params: { ownerId }
  })
}
// ========== 用例管理接口（/api-case）==========

// 文档路径: /api-case/statistics/overview  → 前端写: /api/api-case/statistics/overview
// 首页用例统计概览：总用例数、HTTP/Dubbo 用例数、成功率
export const getApiCaseStatistics = () => {
  return request.get('/api/api-case/statistics/overview')
}

// 分页查询用例列表 ← 缺失
export const getHttpCaseList = (params) => {
  return request.get('/api/api-case/list', { params })
}

// 查询用例详情 ← 缺失
export const getHttpCase = (id) => {
  return request.get(`/api/api-case/${id}`)
}


// 逻辑删除用例 ← 缺失
export const deleteHttpCase = (id) => {
  return request.delete(`/api/api-case/${id}`)
}

// 批量删除用例 ← 缺失
export const batchDeleteHttpCases = (ids) => {
  return request.delete('/api/api-case/batch', { data: { ids } })
}

// 单用例调试执行 ← 缺失
export const debugHttpCase = (id, data) => {
  return request.post(`/api/api-case/${id}/debug`, data)
}

// ========== cURL 导入/导出接口 ==========
// 导入 cURL 命令创建用例
export const importCurl = (data) => {
  return request.post('/api/api-case/import-curl', data)
}

// 导出用例为 cURL 命令
export const exportCurl = (id) => {
  return request.get(`/api/api-case/${id}/export-curl`)
}

// ========== 自动化测试任务接口（/task）==========
// 文档路径: /task/automation/run (POST) → 启动自动化测试
export const runAutomationTask = (data) => {
  return request.post('/api/task/automation/run', data)
}

// 文档路径: /api-case/{historyId}/retry  → 重试历史用例
export const retryCaseByHistory = (historyId) => {
  return request.post(`/api/api-case/${historyId}/retry`)
}

// 文档路径: /task/automation/{taskId}/retry (POST) → 重试失败用例
export const retryAutomationTask = (taskId) => {
  return request.post(`/api/task/automation/${taskId}/retry`)
}

// 文档路径: /task/automation/{taskId} (GET) → 查询任务详情
export const getAutomationTaskDetail = (taskId) => {
  return request.get(`/api/task/automation/${taskId}`)
}

// 文档路径: /task/automation/list (GET) → 分页查询任务列表
export const getAutomationTaskList = (params) => {
  return request.get('/api/task/automation/list', { params })
}

// 文档路径: /task/automation/{taskId} (DELETE) → 删除自动化测试任务
export const deleteAutomationTask = (taskId) => {
  return request.delete(`/api/task/automation/${taskId}`)
}

// ========== 用例测试历史接口（/api-case-history）==========
// 分页查询执行历史
export const getCaseHistoryPage = (params) => {
  return request.get('/api/api-case-history', { params })
}

// 查询历史记录详情
export const getCaseHistoryDetail = (id) => {
  return request.get(`/api/api-case-history/${id}`)
}

// 重试历史用例（根据历史记录ID）
export const retryHistoryCase = (historyId) => {
  return request.post(`/api/api-case/${historyId}/retry`)
}

// ========== 原服务管理接口（/original-service）==========
// 分页查询原服务列表
export const listOriginalServices = (params) => {
  return request.get('/api/original-service/list', { params })
}

// 查询原服务详情
export const getOriginalServiceDetail = (id) => {
  return request.get(`/api/original-service/${id}`)
}

// 更新原服务
export const updateOriginalService = (id, data) => {
  return request.put(`/api/original-service/${id}`, data)
}

// 删除原服务（单条逻辑删除）
export const deleteOriginalService = (id) => {
  return request.delete(`/api/original-service/${id}`)
}

// 批量删除原服务
export const batchDeleteOriginalServices = (ids) => {
  return request.delete('/api/original-service/batch', { data: ids })
}

// 查询可选用例列表（未在原服务中的用例）
export const listAvailableCases = (params) => {
  return request.get('/api/original-service/available-cases', { params })
}

// 批量添加用例到原服务
export const addCasesToOriginalService = (data) => {
  return request.post('/api/original-service/add-cases', data)
}

// 手动创建原服务
export const createOriginalService = (data) => {
  return request.post('/api/original-service', data)
}


// ========== 流程定义管理接口（/api-flow-definition）==========
// 文档路径: /api-flow-definition/deploy (POST) → 部署流程定义（新建）
// 请求参数: name, applicationId, bpmnXml, businessDomainId(可选), nodes(可选), creatorId(可选,默认当前用户), ownerId(可选,默认当前用户)
// 响应数据: flowDefinitionId, deploymentId, procDefId, name, version, status, deployedAt
export const deployFlowDefinition = (data) => {
  return request.post('/api/api-flow-definition/deploy', data, { timeout: 30000 })
}
// 文档路径: /api-flow-definition/save (POST) → 保存流程设计（未部署）
// 15.8 API: 保存BPMN XML和节点配置，状态置为1=未部署
export const saveFlowDefinition = (data) => {
  return request.post('/api/api-flow-definition/save', data, { timeout: 30000 })
}

// 文档路径: /api-flow-definition/draft (POST) → 保存流程草稿
// 15.7 API: 仅保存基础信息，状态置为0=草稿
export const saveDraft = (data) => {
  return request.post('/api/api-flow-definition/draft', data, { timeout: 15000 })
}

// 文档路径: /api-flow-definition/update (PUT) → 更新流程定义
// 请求参数: id(必填), name(可选), applicationId(可选), edited(可选,true=版本号+1), ownerId(可选,变更负责人), nodes(可选,传了全量替换), bpmnXml(可选)
// 响应数据: flowDefinitionId, deploymentId, procDefId, name, version, status, deployedAt
// 注意: edited=true 时版本号+1，edited=false/null 时版本号不变
export const updateFlowDefinition = (data) => {
  return request.put('/api/api-flow-definition/update', data, { timeout: 30000 })
}

// 文档路径: /api-flow-definition/list (GET) → 分页查询流程列表
// 请求参数: pageNum, pageSize, name(可选)
// 响应数据: records[], total, size, current
// records 字段: id, name, applicationId, applicationName, businessDomainId, procDefId, deploymentId, version, status, creatorId, ownerId, nodeCount, createdAt, updatedAt
export const getFlowDefinitionList = (params) => {
  return request.get('/api/api-flow-definition/list', { params, timeout: 15000 })
}

// 文档路径: /api-flow-definition/{id} (GET) → 查询流程定义详情
// 响应数据: id, name, applicationId, applicationName, businessDomainId, procDefId, deploymentId, version, status, creatorId, ownerId, definitionXml, createdAt, updatedAt, nodes[], sequences[], recentExecutions[]
export const getFlowDefinitionDetail = (id) => {
  return request.get(`/api/api-flow-definition/${id}`, { timeout: 15000 })
}

// 文档路径: /api-flow-definition/{id} (DELETE) → 删除流程定义
export const deleteFlowDefinition = (id) => {
  return request.delete(`/api/api-flow-definition/${id}`)
}

// 文档路径: /api-flow-definition/{id}/status (PATCH) → 更新流程状态
export const updateFlowStatus = (id, status) => {
  return request.patch(`/api/api-flow-definition/${id}/status`, null, {
    params: { status }
  })
}

// ========== 流程执行管理接口（/api-flow-execution）==========
// 文档路径: /api-flow-execution/{flowDefinitionId}/execute (POST) → 启动流程执行
export const executeFlow = (flowDefinitionId, data) => {
  return request.post(`/api/api-flow-execution/${flowDefinitionId}/execute`, data, { timeout: 60000 })
}

// 文档路径: /api-flow-execution/execution/{processInstanceId} (GET) → 查询执行结果
export const getFlowExecutionResult = (processInstanceId) => {
  return request.get(`/api/api-flow-execution/execution/${processInstanceId}`, { timeout: 15000 })
}

// 文档路径: /api-flow-execution/execution/{processInstanceId}/node/{activityId} (GET) → 查询节点执行详情
export const getNodeExecutionDetail = (processInstanceId, activityId) => {
  return request.get(`/api/api-flow-execution/execution/${processInstanceId}/node/${activityId}`, { timeout: 15000 })
}

// 文档路径: /api-flow-execution/history (GET) → 分页查询流程执行历史
export const getFlowExecutionHistory = (params) => {
  return request.get('/api/api-flow-execution/history', { params })
}

// 部署相关API
// 部署前端应用
export const deployFrontend = (formData) => request({
  url: '/deploy-api/deploy/frontend',
  method: 'post',
  data: formData,
  headers: {
    'Content-Type': 'multipart/form-data'
  }
})

// 获取部署历史
export const getDeployHistory = () => request({
  url: '/deploy-api/history',
  method: 'get'
})

// 文档路径: /test/direct (POST) → 直接测试（免登录）
export const directTest = (data) => {
  return request.post('/api/test/direct', data, { skipToken: true, timeout: 60000 })
}

// ========== 测试集管理接口（/test-suite）==========
// 文档路径: /test-suite (POST) → 创建测试集
export const createTestSuite = (data) => {
  return request.post('/api/test-suite', data)
}

// 文档路径: /test-suite (PUT) → 更新测试集
export const updateTestSuite = (data) => {
  return request.put('/api/test-suite', data)
}

// 文档路径: /test-suite/{id} (GET) → 查询测试集详情
export const getTestSuiteDetail = (id) => {
  return request.get(`/api/test-suite/${id}`)
}

// 文档路径: /test-suite/list (GET) → 分页查询测试集列表
export const getTestSuiteList = (params) => {
  return request.get('/api/test-suite/list', { params })
}

// 文档路径: /test-suite/{id} (DELETE) → 删除测试集
export const deleteTestSuite = (id) => {
  return request.delete(`/api/test-suite/${id}`)
}

// 文档路径: /test-suite/batch (DELETE) → 批量删除测试集
export const batchDeleteTestSuites = (ids) => {
  return request.delete('/api/test-suite/batch', { data: ids })
}

// 文档路径: /test-suite/{id}/publish (PATCH) → 发布测试集
export const publishTestSuite = (id) => {
  return request.patch(`/api/test-suite/${id}/publish`)
}

// 文档路径: /test-suite/{id}/disable (PATCH) → 禁用测试集
export const disableTestSuite = (id) => {
  return request.patch(`/api/test-suite/${id}/disable`)
}

// 文档路径: /test-suite/bind-flows (POST) → 绑定流程到测试集
export const bindTestSuiteFlows = (data) => {
  return request.post('/api/test-suite/bind-flows', data)
}

// 文档路径: /test-suite/{suiteId}/flows (GET) → 查询测试集关联的流程列表
export const getTestSuiteFlows = (suiteId) => {
  return request.get(`/api/test-suite/${suiteId}/flows`)
}

// 文档路径: /test-suite/sort-flows (PUT) → 更新流程排序
export const sortTestSuiteFlows = (data) => {
  return request.put('/api/test-suite/sort-flows', data)
}

// 文档路径: /test-suite/{suiteId}/flow/{flowId} (DELETE) → 移除流程关联
export const removeTestSuiteFlow = (suiteId, flowId) => {
  return request.delete(`/api/test-suite/${suiteId}/flow/${flowId}`)
}

// 文档路径: /test-suite/{suiteId}/flows (DELETE) → 清空测试集流程关联
export const clearTestSuiteFlows = (suiteId) => {
  return request.delete(`/api/test-suite/${suiteId}/flows`)
}

// 文档路径: /test-suite/execute (POST) → 执行测试集
export const executeTestSuite = (data) => {
  return request.post('/api/test-suite/execute', data, { timeout: 120000 })
}

// 文档路径: /test-suite/execute/{taskId} (GET) → 查询执行状态
export const getTestSuiteExecuteStatus = (taskId) => {
  return request.get(`/api/test-suite/execute/${taskId}`, { timeout: 15000 })
}

// 文档路径: /test-suite/execute/history (GET) → 查询执行历史
export const getTestSuiteExecuteHistory = (params) => {
  return request.get('/api/test-suite/execute/history', { params })
}

// 文档路径: /test-suite/pools/monitor (GET) → 线程池监控
export const getTestSuitePoolMonitor = () => {
  return request.get('/api/test-suite/pools/monitor')
}
