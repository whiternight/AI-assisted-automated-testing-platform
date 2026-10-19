import { ref, computed } from 'vue'

const THEME_KEY = 'testplatform-theme'
const DARK = 'dark'
const LIGHT = 'light'

const currentTheme = ref(LIGHT)
const isDark = computed(() => currentTheme.value === DARK)

const applyTheme = (theme) => {
  currentTheme.value = theme
  // data-theme 用于项目自定义变量（base.css 中的 [data-theme="dark"] 覆盖）
  document.documentElement.setAttribute('data-theme', theme)
  // .dark 类用于 Element Plus 暗色模式（element-plus/theme-chalk/dark/css-vars.css）
  document.documentElement.classList.toggle('dark', theme === DARK)
  localStorage.setItem(THEME_KEY, theme)
}

const initTheme = () => {
  const saved = localStorage.getItem(THEME_KEY)
  if (saved === DARK || saved === LIGHT) {
    applyTheme(saved)
    return
  }
  const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
  applyTheme(prefersDark ? DARK : LIGHT)
}

const toggleTheme = () => {
  applyTheme(isDark.value ? LIGHT : DARK)
}

export function useTheme() {
  return {
    currentTheme,
    isDark,
    initTheme,
    toggleTheme
  }
}
