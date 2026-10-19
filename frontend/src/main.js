import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './assets/main.css'
import { useTheme } from '@/composables/useTheme.js'
import { useUserStore } from '@/composables/useUserStore.js'
import { useAiAssistant } from '@/composables/useAiAssistant.js'


// 删除这两行（因为已经在 index.html 中全局引入了）
// import 'ztree'
// import 'ztree/css/zTreeStyle/zTreeStyle.css'

// 如果之前有导入 jQuery，也删除
// import $ from 'jquery'
// window.$ = window.jQuery = $

// 在 Vue 应用挂载前初始化主题与用户信息，避免首屏闪烁/身份错位
const { initTheme } = useTheme()
initTheme()
const { initUserStore, userInfo } = useUserStore()
initUserStore()
// 若已登录，同步初始化 AI 助手会话
if (userInfo.value) {
  const { initAssistant } = useAiAssistant()
  initAssistant()
}

const app = createApp(App)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
