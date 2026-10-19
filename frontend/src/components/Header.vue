<template>
    <header class="header-wrap">
        <div class="left">
            <div class="logo-icon">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
                </svg>
            </div>
            <span class="brand-name">智能测试平台</span>
        </div>
        <div class="right">
            <AiAssistant />
            <button class="theme-toggle" @click="toggleTheme" :title="isDark ? '切换亮色主题' : '切换亮色主题'">
                <svg v-if="isDark" class="theme-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="5"/>
                    <line x1="12" y1="1" x2="12" y2="3"/>
                    <line x1="12" y1="21" x2="12" y2="23"/>
                    <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
                    <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
                    <line x1="1" y1="12" x2="3" y2="12"/>
                    <line x1="21" y1="12" x2="23" y2="12"/>
                    <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
                    <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
                </svg>
                <svg v-else class="theme-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
                </svg>
            </button>
            <span class="realtime-clock">{{ currentTime }}</span>
            <div class="divider-v"></div>
            <el-dropdown trigger="hover">
                <div class="user-static">
                    <div class="user-avatar">{{ showName.charAt(0) }}</div>
                    <span class="user-text">{{ showName }}</span>
                    <svg class="arrow-svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="6 9 12 15 18 9"/>
                    </svg>
                </div>
                <template #dropdown>
                    <el-dropdown-menu class="dropdown-menu">
                        <el-dropdown-item class="menu-item" @click="$router.push('/profile/index')">
                            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                                <circle cx="12" cy="7" r="4"/>
                            </svg>
                            <span>个人详情</span>
                        </el-dropdown-item>
                        <el-dropdown-item divided class="menu-item logout-item" @click="handleLogout">
                            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
                                <polyline points="16 17 21 12 16 7"/>
                                <line x1="21" y1="12" x2="9" y2="12"/>
                            </svg>
                            <span>退出登录</span>
                        </el-dropdown-item>
                    </el-dropdown-menu>
                </template>
            </el-dropdown>
        </div>
    </header>
</template>

<script setup>
import { User, ArrowDown, Document, SwitchButton } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { useTheme } from '@/composables/useTheme.js'
import { useUserStore } from '@/composables/useUserStore.js'
import { useAiAssistant } from '@/composables/useAiAssistant.js'
import AiAssistant from './AiAssistant.vue'

const router = useRouter()
const { isDark, toggleTheme } = useTheme()
const { showName, clearUserInfo } = useUserStore()
const { newSession, closePanel, clearAllSessions } = useAiAssistant()

// --- 实时时间 ---
const currentTime = ref('')
let clockTimer = null

const updateTime = () => {
    const now = new Date()
    const y = now.getFullYear()
    const m = String(now.getMonth() + 1).padStart(2, '0')
    const d = String(now.getDate()).padStart(2, '0')
    const h = String(now.getHours()).padStart(2, '0')
    const min = String(now.getMinutes()).padStart(2, '0')
    const s = String(now.getSeconds()).padStart(2, '0')
    currentTime.value = `${y}-${m}-${d} ${h}:${min}:${s}`
}

onMounted(() => {
    updateTime()
    clockTimer = setInterval(updateTime, 1000)
})

onBeforeUnmount(() => {
    if (clockTimer) { clearInterval(clockTimer); clockTimer = null }
})

// 退出登录
const handleLogout = () => {
    sessionStorage.removeItem('token')
    clearUserInfo()
    closePanel()
    clearAllSessions()
    ElMessage.success('已安全退出登录')
    router.push('/auth/login')
}
</script>

<style scoped>
.header-wrap {
    display: flex;
    justify-content: space-between;
    align-items: center;
    height: 48px;
    padding: 0 24px;
    border-bottom: 1px solid var(--mute, #e5e5e5);
    background: var(--paper, #fafafa);
    flex-shrink: 0;
}

.left {
    display: flex;
    align-items: center;
    gap: 8px;
}

.logo-icon {
    width: 28px;
    height: 28px;
    background: var(--ink, #0a0a0a);
    color: var(--paper, #fafafa);
    display: flex;
    align-items: center;
    justify-content: center;
}

.brand-name {
    font-size: 14px;
    font-weight: 500;
    letter-spacing: -0.02em;
    color: var(--ink, #0a0a0a);
}

.right {
    display: flex;
    align-items: center;
    gap: 16px;
}

.theme-toggle {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px solid var(--mute, #e5e5e5);
    background: transparent;
    color: var(--ghost, #a3a3a3);
    cursor: pointer;
    transition: all 0.2s ease;
    padding: 0;
    outline: none;
}

.theme-toggle:hover {
    border-color: var(--ink, #0a0a0a);
    color: var(--ink, #0a0a0a);
}

.theme-toggle:active {
    transform: scale(0.95);
}

.theme-icon {
    transition: transform 0.25s ease, opacity 0.2s ease;
}

.realtime-clock {
    font-size: 11px;
    color: var(--ghost, #a3a3a3);
    font-variant-numeric: tabular-nums;
    letter-spacing: 0.02em;
    white-space: nowrap;
    font-family: 'JetBrains Mono', monospace;
}

.divider-v {
    width: 1px;
    height: 16px;
    background: var(--mute, #e5e5e5);
}

.user-static {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    padding: 4px 8px;
    transition: all 0.15s;
    border: 1px solid transparent;
}

.user-static:hover {
    border-color: var(--mute, #e5e5e5);
}

.user-avatar {
    width: 24px;
    height: 24px;
    background: var(--ink, #0a0a0a);
    color: var(--paper, #fafafa);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 10px;
    font-weight: 500;
}

.user-text {
    font-size: 12px;
    font-weight: 500;
    color: var(--ink, #0a0a0a);
}

.arrow-svg {
    color: var(--ghost, #a3a3a3);
}

:deep(.dropdown-menu) {
    padding: 4px 0;
    border: 1px solid var(--mute, #e5e5e5);
    border-radius: 0;
}

:deep(.menu-item) {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    font-size: 12px;
    color: var(--ghost, #a3a3a3);
}

:deep(.menu-item:hover) {
    background: rgba(0, 0, 0, 0.02);
    color: var(--ink, #0a0a0a);
}

:deep(.logout-item:hover) {
    color: #e53e3e !important;
}
</style>
