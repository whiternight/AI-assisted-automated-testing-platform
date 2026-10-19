import { ref, computed } from 'vue'

const STORAGE_KEY = 'userInfo'

// 单例响应式状态（模块级变量，所有调用 useUserStore() 的组件共享同一份）
const userInfo = ref(null)

const isAdmin = computed(() => {
  const u = userInfo.value
  if (!u) return false
  // 兼容多种字段：userType / roleName / username
  return u.userType === 'admin' || u.roleName === 'admin' || u.username === 'admin'
})

const showName = computed(() => {
  const u = userInfo.value
  return u?.realName || u?.username || 'A'
})

/** 从 sessionStorage 读取用户信息（不做 JSON.parse 容错由调用方处理） */
const readFromStorage = () => {
  const str = sessionStorage.getItem(STORAGE_KEY)
  if (!str || str === 'undefined' || str === 'null') return null
  try {
    return JSON.parse(str)
  } catch (e) {
    sessionStorage.removeItem(STORAGE_KEY)
    return null
  }
}

/** 初始化：应用启动时从 sessionStorage 恢复用户信息到响应式状态 */
const initUserStore = () => {
  userInfo.value = readFromStorage()
}

/** 设置用户信息：同步更新响应式状态与 sessionStorage */
const setUserInfo = (info) => {
  userInfo.value = info
  if (info) {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(info))
  } else {
    sessionStorage.removeItem(STORAGE_KEY)
  }
}

/** 清除用户信息：登出时调用 */
const clearUserInfo = () => {
  userInfo.value = null
  sessionStorage.removeItem(STORAGE_KEY)
}

export function useUserStore() {
  return {
    userInfo,
    isAdmin,
    showName,
    initUserStore,
    setUserInfo,
    clearUserInfo
  }
}
