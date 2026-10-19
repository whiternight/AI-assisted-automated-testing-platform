import { ref, computed, reactive } from 'vue'
import { chatStream } from '@/api/agent'

const STORAGE_KEY_SESSIONS = 'ai_assistant_sessions'
const STORAGE_KEY_CURRENT = 'ai_assistant_current_session_id'
const STORAGE_KEY_MESSAGES = (sessionId) => `ai_assistant_messages_${sessionId}`
const PROJECT_NAME = '智能测试平台'

const isOpen = ref(false)
const isLoading = ref(false)
const sessions = ref([])
const currentSessionId = ref('')
const messages = ref([])
const showHistory = ref(false)
let currentAbortController = null

const currentSession = computed(() => {
  return sessions.value.find((s) => s.id === currentSessionId.value) || null
})

const hasHistory = computed(() => sessions.value.length > 0)

function _loadSessions() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_SESSIONS)
    if (raw) {
      const list = JSON.parse(raw)
      if (Array.isArray(list)) {
        sessions.value = list
      }
    }
  } catch {
    sessions.value = []
  }
}

function _saveSessions() {
  localStorage.setItem(STORAGE_KEY_SESSIONS, JSON.stringify(sessions.value))
}

function _loadMessages(sessionId) {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_MESSAGES(sessionId))
    if (raw) {
      const list = JSON.parse(raw)
      if (Array.isArray(list)) {
        return list
      }
    }
  } catch {
    // ignore
  }
  return []
}

function _saveMessages(sessionId) {
  if (sessionId) {
    localStorage.setItem(STORAGE_KEY_MESSAGES(sessionId), JSON.stringify(messages.value))
  }
}

function _generateSessionId() {
  return `session_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`
}

function _generateSessionTitle(firstUserMessage) {
  const text = String(firstUserMessage || '新对话').trim()
  return text.length > 20 ? text.slice(0, 20) + '…' : text
}

function _updateSessionMeta() {
  const session = sessions.value.find((s) => s.id === currentSessionId.value)
  const firstUser = messages.value.find((m) => m.role === 'user')
  const title = _generateSessionTitle(firstUser?.content)
  if (session) {
    session.title = title
    session.updatedAt = Date.now()
  } else {
    sessions.value.unshift({
      id: currentSessionId.value,
      title,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    })
  }
  _saveSessions()
}

function _createSession(id = _generateSessionId()) {
  currentSessionId.value = id
  messages.value = []
  if (!sessions.value.some((s) => s.id === id)) {
    sessions.value.unshift({
      id,
      title: '新对话',
      createdAt: Date.now(),
      updatedAt: Date.now(),
    })
    _saveSessions()
  }
  localStorage.setItem(STORAGE_KEY_CURRENT, id)
  _saveMessages(id)
}

function initAssistant() {
  _loadSessions()
  const savedCurrent = localStorage.getItem(STORAGE_KEY_CURRENT)
  if (savedCurrent && sessions.value.some((s) => s.id === savedCurrent)) {
    // 如果当前会话已经在内存中且正在运行，不要从 localStorage 重新加载，
    // 避免覆盖实时流式状态（弹窗关闭/重新打开时状态保持一致）。
    if (currentSessionId.value !== savedCurrent) {
      switchSession(savedCurrent)
    }
  } else if (sessions.value.length) {
    if (currentSessionId.value !== sessions.value[0].id) {
      switchSession(sessions.value[0].id)
    }
  } else {
    _createSession()
  }
}

function switchSession(sessionId) {
  if (currentAbortController) {
    currentAbortController.abort()
    currentAbortController = null
  }
  currentSessionId.value = sessionId
  messages.value = _loadMessages(sessionId)
  localStorage.setItem(STORAGE_KEY_CURRENT, sessionId)
  showHistory.value = false
}

function newSession() {
  if (currentAbortController) {
    currentAbortController.abort()
    currentAbortController = null
  }
  _createSession()
}

function deleteSession(sessionId) {
  sessions.value = sessions.value.filter((s) => s.id !== sessionId)
  localStorage.removeItem(STORAGE_KEY_MESSAGES(sessionId))
  _saveSessions()
  if (currentSessionId.value === sessionId) {
    if (sessions.value.length) {
      switchSession(sessions.value[0].id)
    } else {
      _createSession()
    }
  }
}

function togglePanel() {
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    initAssistant()
  }
}

function openPanel() {
  isOpen.value = true
  initAssistant()
}

function closePanel() {
  isOpen.value = false
  showHistory.value = false
}

function toggleHistory() {
  showHistory.value = !showHistory.value
}

async function sendMessage(text) {
  const userText = typeof text === 'string' ? text.trim() : String(text ?? '').trim()
  if (!userText || isLoading.value) return

  messages.value.push({ role: 'user', content: userText })
  _saveMessages(currentSessionId.value)
  _updateSessionMeta()

  isLoading.value = true
  currentAbortController = new AbortController()

  const assistantMsg = reactive({
    role: 'assistant',
    content: '',
    thinking: '',
    toolEvents: [],
    progressList: [],
    currentProgress: '',
    isStreaming: true,
    expanded: true,
    status: 'running',
    currentTool: '',
  })
  messages.value.push(assistantMsg)

  let lastType = ''

  await chatStream({
    threadId: currentSessionId.value,
    message: userText,
    abortSignal: currentAbortController.signal,
    onEvent: (event, data) => {
      if (!data) return
      if (event === 'progress') {
        const text = data.message || ''
        assistantMsg.currentProgress = text
        if (text && !assistantMsg.progressList.includes(text)) {
          assistantMsg.progressList.push(text)
        }
      } else if (event === 'thinking') {
        lastType = 'thinking'
        assistantMsg.thinking += data.content || ''
      } else if (event === 'tool_start') {
        lastType = 'thinking'
        assistantMsg.currentTool = data.name || ''
        assistantMsg.currentProgress = data.name ? `正在调用工具：${data.name}…` : '正在调用工具…'
        assistantMsg.toolEvents.push({ type: 'start', name: data.name, input: data.input })
      } else if (event === 'tool_end') {
        lastType = 'thinking'
        assistantMsg.currentTool = ''
        assistantMsg.currentProgress = '工具执行完成，继续下一步…'
        assistantMsg.toolEvents.push({ type: 'end', output: data.output })
      } else if (event === 'answer') {
        lastType = 'answer'
        assistantMsg.content += data.content || ''
        assistantMsg.currentProgress = '正在生成最终回答…'
      } else if (event === 'task_created') {
        // Agent 创建了自动化测试任务，通知列表刷新
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('ai-assistant:task-created', { detail: data }))
        }
      } else if (event === 'error') {
        assistantMsg.content += `\n[出错] ${data.message || 'Agent 执行异常'}`
        assistantMsg.status = 'error'
        assistantMsg.isStreaming = false
      } else if (event === 'done' && data?.thread_id) {
        currentSessionId.value = data.thread_id
        localStorage.setItem(STORAGE_KEY_CURRENT, data.thread_id)
      }

      // 只要有内容/进度/工具事件产生，就保持结果卡片展开，确保用户实时可见
      if (
        assistantMsg.status === 'running' ||
        lastType === 'answer' ||
        assistantMsg.content ||
        assistantMsg.thinking ||
        assistantMsg.toolEvents.length ||
        assistantMsg.progressList.length
      ) {
        assistantMsg.expanded = true
      }
    },
    onError: () => {
      assistantMsg.status = 'error'
      assistantMsg.isStreaming = false
      assistantMsg.currentTool = ''
      assistantMsg.expanded = true
      isLoading.value = false
      _saveMessages(currentSessionId.value)
      _updateSessionMeta()
    },
    onDone: (finalThreadId) => {
      if (finalThreadId) {
        currentSessionId.value = finalThreadId
        localStorage.setItem(STORAGE_KEY_CURRENT, finalThreadId)
      }
      assistantMsg.status = assistantMsg.status === 'error' ? 'error' : 'done'
      assistantMsg.isStreaming = false
      assistantMsg.currentTool = ''
      assistantMsg.expanded = true
      isLoading.value = false
      _saveMessages(currentSessionId.value)
      _updateSessionMeta()

      // 通知自动化测试列表等关心任务完成的组件刷新数据
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('ai-assistant:task-completed'))
      }
    },
  })
}

function stopStream() {
  if (currentAbortController) {
    currentAbortController.abort()
    currentAbortController = null
  }
}

function clearAllSessions() {
  for (const s of sessions.value) {
    localStorage.removeItem(STORAGE_KEY_MESSAGES(s.id))
  }
  sessions.value = []
  _saveSessions()
  _createSession()
}

export function useAiAssistant() {
  return {
    isOpen,
    isLoading,
    sessions,
    currentSessionId,
    currentSession,
    messages,
    showHistory,
    hasHistory,
    PROJECT_NAME,
    initAssistant,
    togglePanel,
    openPanel,
    closePanel,
    toggleHistory,
    switchSession,
    newSession,
    deleteSession,
    sendMessage,
    stopStream,
    clearAllSessions,
  }
}
