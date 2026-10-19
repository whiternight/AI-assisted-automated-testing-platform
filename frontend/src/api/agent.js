import { ElMessage } from 'element-plus'

const AGENT_BASE = '/agent'

/**
 * 读取当前登录用户的 JWT token（与 后端服务共用同一套凭证）。
 * agent 服务通过该 token 以当前用户身份调用 test-backend 接口。
 */
function getToken() {
  return sessionStorage.getItem('token') || ''
}

/**
 * 解析 SSE 流中的一条或多条事件。
 * 返回 { event, data } 数组；data 为后端返回的 JSON 对象。
 *
 * 兼容三种格式：
 *  1) 标准多行：event: x\ndata: y\n\n
 *  2) 单行合并：event:x data:y\n  （部分代理会折叠换行）
 *  3) 末尾无空行：以 \n 结尾的最后一帧
 */
function parseSseEvents(chunk) {
  const events = []
  if (!chunk) return events

  // 先按空行分块，处理标准多行格式
  const blocks = chunk.split(/\n\n+/)
  for (const block of blocks) {
    const trimmed = block.replace(/\n+$/, '')
    if (!trimmed) continue

    let currentEvent = ''
    let currentData = ''

    for (const rawLine of trimmed.split('\n')) {
      const line = rawLine.replace(/\r$/, '')
      if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        currentData = line.slice(5).trim()
      } else {
        // 同一行内可能有多个字段，例如 "event:done data:{...}"
        const evtMatch = line.match(/event:\s*(\S+)/)
        const dataMatch = line.match(/data:\s*(.+)$/)
        if (evtMatch) currentEvent = evtMatch[1].trim()
        if (dataMatch) currentData = dataMatch[1].trim()
      }
    }

    if (!currentEvent && !currentData) continue

    try {
      events.push({
        event: currentEvent || 'message',
        data: currentData ? JSON.parse(currentData) : {},
      })
    } catch (e) {
      events.push({ event: currentEvent || 'message', data: { raw: currentData } })
    }
  }

  return events
}

/**
 * 调用 agent 流式对话接口。
 *
 * @param {Object} options
 * @param {string} options.threadId 会话 ID，为空则服务端新建
 * @param {string} options.message 用户输入
 * @param {boolean} [options.debug=false] 是否输出更详细的思考过程
 * @param {Function} options.onEvent 每条 SSE 事件的回调：onEvent(event, data)
 * @param {Function} options.onError 错误回调：onError(error)
 * @param {Function} options.onDone 完成回调：onDone(threadId)
 * @param {AbortSignal} [options.abortSignal] 用于中断请求
 */
export async function chatStream(options) {
  const { threadId, message, debug = false, onEvent, onError, onDone, abortSignal } = options
  const token = getToken()

  if (!token) {
    const err = new Error('用户未登录，无法启动 AI 助手')
    ElMessage.error(err.message)
    onError?.(err)
    return
  }

  // 超时保护：若 90 秒内没有任何事件，主动放弃并结束流，
  // 避免后端卡住时前端无限转圈。
  const STREAM_TIMEOUT_MS = 90 * 1000
  let watchdog = setTimeout(() => {
    const err = new Error('AI 助手响应超时（90s 内未收到任何事件）')
    onError?.(err)
  }, STREAM_TIMEOUT_MS)
  const resetWatchdog = () => {
    clearTimeout(watchdog)
    watchdog = setTimeout(() => {
      const err = new Error('AI 助手响应超时（90s 内未收到任何事件）')
      onError?.(err)
    }, STREAM_TIMEOUT_MS)
  }

  try {
    const response = await fetch(`${AGENT_BASE}/api/agent/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ thread_id: threadId || undefined, message, debug }),
      signal: abortSignal,
    })

    if (!response.ok) {
      let detail = `请求失败：${response.status}`
      try {
        const body = await response.json()
        detail = body.detail || detail
      } catch {
        // ignore
      }
      throw new Error(detail)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let finalThreadId = threadId
    let doneReceived = false

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      resetWatchdog()
      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || ''

      for (const part of parts) {
        const events = parseSseEvents(part)
        for (const { event, data } of events) {
          const serverTs = data?._server_ts
          const latency = serverTs ? Date.now() - serverTs : null
          console.log(`[SSE recv] event=${event} client_ts=${Date.now()} server_ts=${serverTs ?? 'N/A'} latency=${latency ?? 'N/A'}ms`, data)
          if (event === 'done') {
            doneReceived = true
            if (data?.thread_id) {
              finalThreadId = data.thread_id
            }
          }
          onEvent?.(event, data)
        }
      }
    }

    // 处理缓冲区中剩余内容
    if (buffer.trim()) {
      const events = parseSseEvents(buffer)
      for (const { event, data } of events) {
        const serverTs = data?._server_ts
        const latency = serverTs ? Date.now() - serverTs : null
        console.log(`[SSE recv] event=${event} client_ts=${Date.now()} server_ts=${serverTs ?? 'N/A'} latency=${latency ?? 'N/A'}ms`, data)
        if (event === 'done') {
          doneReceived = true
          if (data?.thread_id) {
            finalThreadId = data.thread_id
          }
        }
        onEvent?.(event, data)
      }
    }

    // 若服务端未发出 done 事件，主动补一个，确保前端能退出加载态
    if (!doneReceived) {
      onEvent?.('done', { thread_id: finalThreadId })
    }

    onDone?.(finalThreadId)
  } catch (error) {
    if (error.name === 'AbortError') return
    const msg = error.message || 'AI 助手通信异常'
    ElMessage.error(msg)
    onError?.(error)
  } finally {
    clearTimeout(watchdog)
  }
}

/**
 * 健康检查：确认 agent 服务可用。
 */
export async function checkAgentHealth() {
  try {
    const res = await fetch(`${AGENT_BASE}/api/agent/health`, { method: 'GET' })
    return res.ok
  } catch {
    return false
  }
}
