<template>
  <div class="ai-assistant">
    <!-- 顶栏触发按钮 -->
    <button
      class="ai-trigger"
      :class="{ active: isOpen }"
      @click="togglePanel"
      title="灵析 AI 助手"
    >
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 2a3 3 0 0 0-3 3v1H6a2 2 0 0 0-2 2v3a2 2 0 0 0 2 2h.5a.5.5 0 0 1 .5.5v.2a7 7 0 0 0 1.7 4.6l.3.4v1.7a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2v-1.7l.3-.4A7 7 0 0 0 16.5 13.7v-.2a.5.5 0 0 1 .5-.5h.5a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-3V5a3 3 0 0 0-3-3z"/>
      </svg>
      <span class="ai-trigger-text">AI 助手</span>
    </button>

    <!-- 弹窗面板 -->
    <Transition name="ai-panel">
      <div v-if="isOpen" class="ai-panel" :class="{ dark: isDark }">
        <!-- 面板头部 -->
        <div class="ai-panel-header">
          <div class="ai-title">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2a3 3 0 0 0-3 3v1H6a2 2 0 0 0-2 2v3a2 2 0 0 0 2 2h.5a.5.5 0 0 1 .5.5v.2a7 7 0 0 0 1.7 4.6l.3.4v1.7a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2v-1.7l.3-.4A7 7 0 0 0 16.5 13.7v-.2a.5.5 0 0 1 .5-.5h.5a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-3V5a3 3 0 0 0-3-3z"/>
            </svg>
            <span>{{ PROJECT_NAME }} 智能 AI 助手</span>
          </div>
          <div class="ai-header-actions">
            <div class="ai-history-wrap">
              <button
                class="ai-header-btn"
                :class="{ active: showHistory }"
                title="历史对话"
                @click="toggleHistory"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="1 4 1 10 7 10"/>
                  <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>
                </svg>
              </button>
              <!-- 历史会话下拉列表 -->
              <Transition name="ai-history">
                <div v-if="showHistory" class="ai-history-dropdown">
                  <div class="ai-history-header">
                    <span>历史会话</span>
                    <button v-if="sessions.length" class="ai-history-clear" @click="handleClearAll">清空</button>
                  </div>
                  <div v-if="!sessions.length" class="ai-history-empty">暂无历史会话</div>
                  <div v-else class="ai-history-list">
                    <div
                      v-for="session in sessions"
                      :key="session.id"
                      class="ai-history-item"
                      :class="{ active: session.id === currentSessionId }"
                      @click="switchSession(session.id)"
                    >
                      <div class="ai-history-title">{{ session.title }}</div>
                      <div class="ai-history-meta">{{ formatTime(session.updatedAt) }}</div>
                      <button
                        class="ai-history-delete"
                        title="删除"
                        @click.stop="handleDeleteSession(session.id)"
                      >
                        ×
                      </button>
                    </div>
                  </div>
                </div>
              </Transition>
            </div>
            <button class="ai-header-btn" title="新建对话" @click="handleNewChat">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
            <button class="ai-header-btn close" title="关闭" @click="closePanel">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- 消息区域 -->
        <div ref="messagesRef" class="ai-messages">
          <div v-if="messages.length === 0" class="ai-welcome">
            <div class="ai-welcome-icon">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M12 2a3 3 0 0 0-3 3v1H6a2 2 0 0 0-2 2v3a2 2 0 0 0 2 2h.5a.5.5 0 0 1 .5.5v.2a7 7 0 0 0 1.7 4.6l.3.4v1.7a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2v-1.7l.3-.4A7 7 0 0 0 16.5 13.7v-.2a.5.5 0 0 1 .5-.5h.5a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-3V5a3 3 0 0 0-3-3z"/>
              </svg>
            </div>
            <p class="ai-welcome-title">{{ PROJECT_NAME }} 智能 AI 助手，欢迎您！</p>
            <p class="ai-welcome-desc">我可以帮您查询用例、执行测试任务、分析结果，随时为您服务。</p>
          </div>

          <template v-else>
            <div
              v-for="(msg, index) in messages"
              :key="index"
              class="ai-message"
              :class="msg.role"
            >
              <div class="ai-message-avatar">
                <svg v-if="msg.role === 'user'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
                <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 2a3 3 0 0 0-3 3v1H6a2 2 0 0 0-2 2v3a2 2 0 0 0 2 2h.5a.5.5 0 0 1 .5.5v.2a7 7 0 0 0 1.7 4.6l.3.4v1.7a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2v-1.7l.3-.4A7 7 0 0 0 16.5 13.7v-.2a.5.5 0 0 1 .5-.5h.5a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-3V5a3 3 0 0 0-3-3z"/>
                </svg>
              </div>
              <div class="ai-message-body">
                <div v-if="msg.role === 'user'" class="ai-message-content user">{{ msg.content }}</div>

                <!-- 助手回复：整体可折叠卡片 -->
                <div v-else class="ai-result-card">
                  <button
                    class="ai-result-header"
                    :class="msg.status"
                    @click="msg.expanded = !msg.expanded"
                  >
                    <span class="ai-status-icon">
                      <span v-if="msg.status === 'running'" class="ai-spin-dot"></span>
                      <svg v-else-if="msg.status === 'done'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="20 6 9 17 4 12"/>
                      </svg>
                      <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <circle cx="12" cy="12" r="10"/>
                        <line x1="12" y1="8" x2="12" y2="12"/>
                        <line x1="12" y1="16" x2="12.01" y2="16"/>
                      </svg>
                    </span>
                    <span class="ai-status-text">{{ statusText(msg) }}</span>
                    <svg
                      class="ai-expand-icon"
                      :class="{ rotated: msg.expanded }"
                      width="12"
                      height="12"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                    >
                      <polyline points="9 18 15 12 9 6"/>
                    </svg>
                  </button>

                  <div v-show="msg.expanded" class="ai-result-body">
                    <!-- 执行进度 -->
                    <div v-if="msg.status === 'running' || msg.progressList?.length" class="ai-section">
                      <div class="ai-section-title">执行进度</div>
                      <div class="ai-progress-list">
                        <div
                          v-for="(progress, pIndex) in msg.progressList"
                          :key="pIndex"
                          class="ai-progress-item"
                          :class="{ active: pIndex === msg.progressList.length - 1 && msg.status === 'running' }"
                        >
                          <span class="ai-progress-dot"></span>
                          <span class="ai-progress-text">{{ progress }}</span>
                        </div>
                      </div>
                    </div>

                    <!-- 运行过程 -->
                    <div v-if="msg.thinking || msg.toolEvents?.length || msg.status === 'running'" class="ai-section">
                      <div class="ai-section-title">
                        运行过程
                        <span v-if="msg.status === 'running'" class="ai-thinking-indicator">
                          <span class="ai-pulse-dot"></span>
                          思考中…
                        </span>
                      </div>
                      <div class="ai-thinking-text">
                        <span v-if="msg.thinking">{{ msg.thinking }}</span>
                        <span v-else-if="msg.status === 'running'" class="ai-empty-text">等待模型输出…</span>
                      </div>
                      <div
                        v-for="(tool, tIndex) in msg.toolEvents"
                        :key="tIndex"
                        class="ai-tool-event"
                        :class="tool.type"
                      >
                        <span class="ai-tool-label">{{ tool.type === 'start' ? '调用' : '返回' }}</span>
                        <span v-if="tool.name" class="ai-tool-name">{{ tool.name }}</span>
                        <span v-if="tool.input" class="ai-tool-detail">{{ tool.input }}</span>
                        <span v-if="tool.output" class="ai-tool-detail">{{ tool.output }}</span>
                      </div>
                    </div>

                    <!-- 输出结果 -->
                    <div class="ai-section">
                      <div class="ai-section-title">输出结果</div>
                      <div class="ai-answer-content">
                        <span v-if="msg.content">
                          {{ msg.content }}
                          <span v-if="msg.status === 'running'" class="ai-typing-cursor"></span>
                        </span>
                        <span v-else-if="msg.isStreaming">{{ msg.currentProgress || '正在生成回答…' }}<span class="ai-typing-cursor"></span></span>
                        <span v-else-if="msg.status === 'done' && (msg.toolEvents?.length || msg.thinking)" class="ai-empty-text">Agent 已完成操作，详细结果请查看「运行过程」</span>
                        <span v-else class="ai-empty-text">暂无输出</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>

        <!-- 输入区域 -->
        <div class="ai-input-area">
          <textarea
            v-model="inputText"
            class="ai-input"
            rows="1"
            placeholder="请输入您的问题…"
            @keydown.enter.prevent="handleSend"
            @input="autoResize"
          />
          <button
            class="ai-send-btn"
            :disabled="!inputTextTrim || isLoading"
            @click="handleSend"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="22" y1="2" x2="11" y2="13"/>
              <polygon points="22 2 15 22 11 13 2 9"/>
            </svg>
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useTheme } from '@/composables/useTheme.js'
import { useAiAssistant } from '@/composables/useAiAssistant.js'

const { isDark } = useTheme()
const {
  isOpen,
  isLoading,
  sessions,
  currentSessionId,
  messages,
  showHistory,
  PROJECT_NAME,
  togglePanel,
  closePanel,
  toggleHistory,
  switchSession,
  newSession,
  deleteSession,
  sendMessage,
  clearAllSessions,
} = useAiAssistant()

const inputText = ref('')
const inputTextTrim = computed(() => String(inputText.value ?? '').trim())
const messagesRef = ref(null)

watch(messages, () => {
  nextTick(() => {
    const el = messagesRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}, { deep: true })

function autoResize(e) {
  const el = e.target
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

function statusText(msg) {
  if (msg.status === 'running') {
    return msg.currentProgress || (msg.currentTool ? `正在运行：${msg.currentTool}` : '正在运行')
  }
  if (msg.status === 'error') return '运行出错'
  return '运行完成'
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${m}-${day} ${h}:${min}`
}

async function handleSend() {
  const text = String(inputText.value ?? '').trim()
  if (!text || isLoading.value) return
  inputText.value = ''
  nextTick(() => {
    const el = document.querySelector('.ai-input')
    if (el) {
      el.style.height = 'auto'
    }
  })
  await sendMessage(text)
}

async function handleNewChat() {
  try {
    await ElMessageBox.confirm('确定要开启新对话吗？当前会话将被保存到历史中。', '新建对话', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    newSession()
  } catch {
    // 用户取消
  }
}

async function handleDeleteSession(sessionId) {
  try {
    await ElMessageBox.confirm('确定删除该历史会话吗？删除后无法恢复。', '删除会话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    deleteSession(sessionId)
  } catch {
    // 用户取消
  }
}

async function handleClearAll() {
  try {
    await ElMessageBox.confirm('确定清空所有历史会话吗？', '清空历史', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning',
    })
    clearAllSessions()
  } catch {
    // 用户取消
  }
}
</script>

<style scoped>
.ai-assistant {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.ai-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--mute, #e5e5e5);
  background: transparent;
  color: var(--ghost, #a3a3a3);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  outline: none;
}

.ai-trigger:hover,
.ai-trigger.active {
  border-color: var(--ink, #0a0a0a);
  color: var(--ink, #0a0a0a);
}

.ai-trigger:active {
  transform: scale(0.95);
}

.ai-trigger-text {
  font-weight: 500;
}

.ai-panel {
  position: fixed;
  top: 56px;
  right: 24px;
  width: 460px;
  max-width: calc(100vw - 48px);
  height: 600px;
  max-height: calc(100vh - 72px);
  background: var(--paper, #fafafa);
  border: 1px solid var(--mute, #e5e5e5);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  z-index: 2000;
}

.ai-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 16px;
  border-bottom: 1px solid var(--mute, #e5e5e5);
  flex-shrink: 0;
}

.ai-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--ink, #0a0a0a);
}

.ai-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-history-wrap {
  position: relative;
}

.ai-header-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  background: transparent;
  color: var(--ghost, #a3a3a3);
  cursor: pointer;
  transition: all 0.15s;
  padding: 0;
}

.ai-header-btn:hover:not(:disabled),
.ai-header-btn.active {
  border-color: var(--mute, #e5e5e5);
  color: var(--ink, #0a0a0a);
}

.ai-header-btn.close:hover {
  color: #e53e3e;
}

.ai-history-dropdown {
  position: absolute;
  top: 36px;
  right: 0;
  width: 260px;
  max-height: 320px;
  background: var(--paper, #fafafa);
  border: 1px solid var(--mute, #e5e5e5);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  z-index: 10;
}

.ai-history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--mute, #e5e5e5);
  font-size: 12px;
  font-weight: 500;
  color: var(--ink, #0a0a0a);
}

.ai-history-clear {
  font-size: 11px;
  color: #e53e3e;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
}

.ai-history-empty {
  padding: 24px 12px;
  text-align: center;
  font-size: 12px;
  color: var(--ghost, #a3a3a3);
}

.ai-history-list {
  overflow-y: auto;
  padding: 4px;
}

.ai-history-item {
  position: relative;
  padding: 8px 28px 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.ai-history-item:hover,
.ai-history-item.active {
  background: rgba(0, 0, 0, 0.04);
}

.ai-history-title {
  font-size: 12px;
  color: var(--ink, #0a0a0a);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

.ai-history-meta {
  font-size: 11px;
  color: var(--ghost, #a3a3a3);
  margin-top: 2px;
}

.ai-history-delete {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--ghost, #a3a3a3);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
}

.ai-history-item:hover .ai-history-delete {
  opacity: 1;
}

.ai-history-delete:hover {
  color: #e53e3e;
}

.ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ai-welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  gap: 12px;
}

.ai-welcome-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ink, #0a0a0a);
  color: var(--paper, #fafafa);
  border-radius: 50%;
}

.ai-welcome-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--ink, #0a0a0a);
  margin: 0;
}

.ai-welcome-desc {
  font-size: 12px;
  color: var(--ghost, #a3a3a3);
  max-width: 280px;
  margin: 0;
  line-height: 1.6;
}

.ai-message {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.ai-message.user {
  flex-direction: row-reverse;
}

.ai-message-avatar {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ink, #0a0a0a);
  color: var(--paper, #fafafa);
  border-radius: 50%;
}

.ai-message.user .ai-message-avatar {
  background: var(--ghost, #a3a3a3);
}

.ai-message-body {
  max-width: calc(100% - 46px);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ai-message-content {
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-message-content.user {
  background: var(--ink, #0a0a0a);
  color: var(--paper, #fafafa);
  border-radius: 12px 12px 0 12px;
}

.ai-result-card {
  border: 1px solid var(--mute, #e5e5e5);
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  min-width: 260px;
}

.ai-result-header {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: none;
  background: #f9fafb;
  color: var(--ink, #0a0a0a);
  font-size: 12px;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}

.ai-result-header:hover {
  background: #f3f4f6;
}

.ai-result-header.running {
  color: #2563eb;
}

.ai-result-header.error {
  color: #e53e3e;
}

.ai-status-icon {
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ai-status-text {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-expand-icon {
  flex-shrink: 0;
  transition: transform 0.2s ease;
}

.ai-expand-icon.rotated {
  transform: rotate(90deg);
}

.ai-result-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-top: 1px solid var(--mute, #e5e5e5);
}

.ai-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ai-section-title {
  font-size: 11px;
  font-weight: 500;
  color: var(--ghost, #a3a3a3);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.ai-thinking-text {
  font-size: 12px;
  color: var(--ghost, #737373);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-tool-event {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px;
  background: #f9fafb;
  border-radius: 4px;
  font-size: 12px;
}

.ai-tool-label {
  font-weight: 500;
  color: var(--ink, #0a0a0a);
}

.ai-tool-name {
  color: #2563eb;
}

.ai-tool-detail {
  width: 100%;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: var(--ghost, #a3a3a3);
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-progress-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ai-progress-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--ghost, #737373);
  line-height: 1.4;
}

.ai-progress-item.active {
  color: #2563eb;
}

.ai-progress-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ghost, #d4d4d4);
  flex-shrink: 0;
}

.ai-progress-item.active .ai-progress-dot {
  background: #2563eb;
  animation: ai-pulse 1.4s ease-in-out infinite;
}

@keyframes ai-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.ai-answer-content {
  font-size: 13px;
  line-height: 1.6;
  color: var(--ink, #0a0a0a);
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-empty-text {
  color: var(--ghost, #a3a3a3);
  font-size: 12px;
}

.ai-spin-dot {
  width: 12px;
  height: 12px;
  border: 2px solid #dbeafe;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: ai-spin 0.8s linear infinite;
}

@keyframes ai-spin {
  to { transform: rotate(360deg); }
}

.ai-typing-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  background: var(--ghost, #a3a3a3);
  margin-left: 2px;
  animation: ai-blink 1s step-end infinite;
  vertical-align: middle;
}

@keyframes ai-blink {
  50% { opacity: 0; }
}

.ai-input-area {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--mute, #e5e5e5);
  flex-shrink: 0;
}

.ai-input {
  flex: 1;
  min-height: 36px;
  max-height: 120px;
  padding: 8px 12px;
  border: 1px solid var(--mute, #e5e5e5);
  background: transparent;
  color: var(--ink, #0a0a0a);
  font-size: 13px;
  line-height: 1.5;
  resize: none;
  outline: none;
  transition: border-color 0.2s;
}

.ai-input:focus {
  border-color: var(--ink, #0a0a0a);
}

.ai-input::placeholder {
  color: var(--ghost, #a3a3a3);
}

.ai-send-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--ink, #0a0a0a);
  background: var(--ink, #0a0a0a);
  color: var(--paper, #fafafa);
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.ai-send-btn:hover:not(:disabled) {
  opacity: 0.85;
}

.ai-send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 弹窗进入/离开动画 */
.ai-panel-enter-active,
.ai-panel-leave-active {
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.ai-panel-enter-from,
.ai-panel-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.98);
}

/* 历史下拉动画 */
.ai-history-enter-active,
.ai-history-leave-active {
  transition: all 0.2s ease;
}

.ai-history-enter-from,
.ai-history-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* 暗色主题适配 */
.ai-panel.dark {
  background: #171717;
  border-color: #404040;
}

.ai-panel.dark .ai-panel-header,
.ai-panel.dark .ai-input-area {
  border-color: #404040;
}

.ai-panel.dark .ai-title,
.ai-panel.dark .ai-welcome-title {
  color: #fafafa;
}

.ai-panel.dark .ai-history-dropdown,
.ai-panel.dark .ai-result-card {
  background: #262626;
  border-color: #404040;
}

.ai-panel.dark .ai-result-header,
.ai-panel.dark .ai-tool-event,
.ai-panel.dark .ai-result-body {
  background: #262626;
  color: #fafafa;
}

.ai-panel.dark .ai-result-header:hover {
  background: #333;
}

.ai-panel.dark .ai-section-title,
.ai-panel.dark .ai-thinking-text,
.ai-panel.dark .ai-empty-text,
.ai-panel.dark .ai-history-meta,
.ai-panel.dark .ai-progress-item {
  color: #a3a3a3;
}

.ai-panel.dark .ai-progress-item.active {
  color: #60a5fa;
}

.ai-panel.dark .ai-progress-dot {
  background: #525252;
}

.ai-panel.dark .ai-progress-item.active .ai-progress-dot {
  background: #60a5fa;
}

.ai-panel.dark .ai-answer-content {
  color: #fafafa;
}

.ai-panel.dark .ai-input {
  background: #262626;
  border-color: #404040;
  color: #fafafa;
}

.ai-panel.dark .ai-input:focus {
  border-color: #fafafa;
}
</style>
