/**
 * 流程执行管理 API 模块
 * ----------------------------------------------------------------------------
 * 职责：流程启动、节点状态查询、流程终止、历史记录查询、执行详情查询
 * 错误码规范：
 *   前端错误码 1000-1999（数据格式、参数校验、渲染、超时归一化等）
 *   后端错误码 2000-2999（业务异常、权限、数据库、流程引擎等）
 * 接口风格：RESTful + JSON
 */
import request from '@/utils/request'
import {
  executeFlow,
  getFlowExecutionResult,
  getNodeExecutionDetail,
  getFlowExecutionHistory
} from './admin'

// ========== 错误码定义 ==========
// 前端错误码 1000-1999：前端可自行判断与定位的问题
export const FRONTEND_ERROR_CODES = {
  FE_PARAM_INVALID: 1001,        // 参数校验失败（如入参 JSON 非法）
  FE_DATA_FORMAT: 1002,          // 响应数据格式错误（无法解析）
  FE_RENDER: 1003,               // 渲染异常（如节点 ID 在画布中不存在）
  FE_TIMEOUT: 1004,              // 请求超时（前端侧归一化）
  FE_NETWORK: 1005,              // 网络连接异常
  FE_NO_FLOW: 1006,              // 未选择流程即执行
  FE_POLL_FAIL: 1007             // 轮询执行结果失败
}

// 后端错误码 2000-2999：后端业务/系统层面的问题
export const BACKEND_ERROR_CODES = {
  BE_AUTH: 2001,                 // 认证/权限错误
  BE_FLOW_NOT_FOUND: 2002,       // 流程定义不存在
  BE_FLOW_NOT_DEPLOYED: 2003,    // 流程未部署
  BE_EXEC_FAIL: 2004,            // 流程执行失败（业务异常）
  BE_NODE_FAIL: 2005,            // 节点执行失败
  BE_TERMINATE_FAIL: 2006,       // 流程终止失败
  BE_DB_ERROR: 2007,             // 数据库异常
  BE_ENGINE_ERROR: 2008          // 流程引擎异常
}

/**
 * 根据错误对象判断错误来源（前端 / 后端）并返回归一化错误信息
 * @param {Error|Object} err axios 错误对象
 * @returns {{ source: 'frontend'|'backend'|'network', code: number, message: string }}
 */
export const classifyError = (err) => {
  // 无 response：网络/超时类（归一化为前端错误码）
  if (!err || !err.response) {
    const isTimeout = err && err.code === 'ECONNABORTED'
    return {
      source: 'network',
      code: isTimeout ? FRONTEND_ERROR_CODES.FE_TIMEOUT : FRONTEND_ERROR_CODES.FE_NETWORK,
      message: isTimeout ? '请求超时，请检查网络或后端响应' : '网络连接异常，请稍后重试'
    }
  }

  const status = err.response.status
  const backendMsg = err.response.data?.message || err.response.data?.msg || '请求错误'
  // 后端错误码：优先读取后端返回的 code 字段
  const beCode = err.response.data?.code

  // 401/403 归为后端权限错误
  if (status === 401 || status === 403) {
    return { source: 'backend', code: beCode || BACKEND_ERROR_CODES.BE_AUTH, message: backendMsg }
  }
  // 5xx 归为后端系统错误
  if (status >= 500) {
    return { source: 'backend', code: beCode || BACKEND_ERROR_CODES.BE_ENGINE_ERROR, message: backendMsg }
  }
  // 其余（4xx 业务）归为后端业务错误
  return { source: 'backend', code: beCode || BACKEND_ERROR_CODES.BE_EXEC_FAIL, message: backendMsg }
}

// ========== 流程执行接口 ==========

// 启动流程执行（复用 admin.js）
export { executeFlow }

// 查询执行结果 / 节点详情 / 历史记录（复用 admin.js）
export { getFlowExecutionResult, getNodeExecutionDetail, getFlowExecutionHistory }

/**
 * 终止流程执行
 * 接口：POST /api/api-flow-execution/execution/{processInstanceId}/terminate
 * 触发场景：任意节点执行失败时，立即终止后续节点执行
 * @param {string|number} processInstanceId 流程实例ID
 * @returns {Promise}
 */
export const terminateFlow = (processInstanceId) => {
  return request.post(`/api/api-flow-execution/execution/${processInstanceId}/terminate`, {}, { timeout: 3000 })
}

/**
 * 查询执行详情（含每个节点开始/结束时间、耗时、状态、详细日志）
 * 接口：GET /api/api-flow-execution/execution/{processInstanceId}/detail
 * @param {string|number} processInstanceId 流程实例ID
 */
export const getExecutionDetail = (processInstanceId) => {
  return request.get(`/api/api-flow-execution/execution/${processInstanceId}/detail`, { timeout: 15000 })
}

/**
 * 重试流程执行（从失败节点重新执行）
 * 接口：POST /api/api-flow-execution/execution/{processInstanceId}/retry
 * @param {string|number} processInstanceId 流程实例ID
 */
export const retryFlowExecution = (processInstanceId) => {
  return request.post(`/api/api-flow-execution/execution/${processInstanceId}/retry`, {}, { timeout: 60000 })
}
