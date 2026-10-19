import { createRouter, createWebHistory } from 'vue-router'
import AuthLayout from '@/components/AuthLayout.vue'
import Layout from '@/components/Layout.vue'
import FullLayout from '@/components/FullLayout.vue'
import { ElMessage } from 'element-plus'

const routes = [
  {
    path: '/auth',
    component: AuthLayout,
    redirect: '/auth/login',
    children: [
      // 登录注册放到 logAndRegister 文件夹
      { path: 'login', component: () => import('@/views/logAndRegister/login.vue') },
      { path: 'register', component: () => import('@/views/logAndRegister/register.vue') }
    ]
  },
  { path: '/', redirect: '/auth/login' },
  {
    path: '/layout',
    component: Layout,
    redirect: '/layout/home',
    children: [
      { path: 'home', component: () => import('@/views/home/home.vue'), meta: { needLogin: true } },
      { path: 'history', component: () => import('@/views/history/history.vue'), meta: { needLogin: true } },
      { path: 'automated', component: () => import('@/views/automatedTest/automated.vue'), meta: { needLogin: true } },
      {
        path: 'Apihub',
        component: () => import('@/views/api/Apihub.vue'),
        meta: { needLogin: true }
      },
      {
        path: 'ApiRecord',
        component: () => import('@/views/api/ApiRecord.vue'),
        meta: { needLogin: true }
      },
      {
        path: 'Apihistory',
        component: () => import('@/views/api/Apihistory.vue'),
        meta: { needLogin: true }
      },
      {
        path: 'DubboTester',
        component: () => import('@/views/api/DubboTester.vue'),
        meta: { needLogin: true }
      },
      {
        path: 'ApiTester',
        component: () => import('@/views/api/ApiTester.vue'),
        meta: { needLogin: true }
      },
      {
        path: 'bpmn/design',
        component: () => import('@/views/bpmn/BpmnDesignPage.vue'),
        meta: { needLogin: true }
      },
      {
        path: 'originalservice',
        component: () => import('@/views/originalService/OriginalService.vue'),
        meta: { needLogin: true }
      },
      {
        path: 'originalservicedetail',
        component: () => import('@/views/originalService/OriginalServiceDetail.vue'),
        meta: { needLogin: true }
      },
      {
        path: 'settings',
        component: () => import('@/views/setting/FrontendDeploy.vue'),
        meta: { needLogin: true }
      },
      {
        path: 'testSuite',
        component: () => import('@/views/testSuite/TestSuite.vue'),
        meta: { needLogin: true }
      }
    ]
  },
  {
    path: '/bpmn/designer/:id?',
    name: 'FlowDesigner',
    component: () => import('@/views/bpmn/FlowDesigner.vue'),
    meta: { needLogin: true }
  },
  {
    path: '/profile',
    component: () => import('@/components/ProfileLayout.vue'),
    redirect: '/profile/index',
    children: [
      // 个人信息、改密码、审核页面统一 personalDetails
      { path: 'index', component: () => import('@/views/personalDetails/profile.vue') },
      { path: 'pwd', component: () => import('@/views/personalDetails/pwdEdit.vue') },
      { path: 'newUser', component: () => import('@/views/personalDetails/newUserAudit.vue'), meta: { role: ['admin'] } },
      { path: 'userList', component: () => import('@/views/personalDetails/userList.vue'), meta: { role: ['admin'] } }
    ]
  },
  // 在路由中添加实例详情页面
  {
    path: '/layout/flow-instance/:instanceId',
    name: 'FlowInstanceDetail',
    component: () => import('@/views/bpmn/FlowInstanceDetail.vue'),
    meta: { needLogin: true }
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

// 守卫逻辑完全原样保留不动
router.beforeEach(async (to, from, next) => {
  const tokenStr = sessionStorage.getItem('token')
  const token = (tokenStr && tokenStr !== 'null' && tokenStr.trim() !== '') ? tokenStr : null

  const userInfoStr = sessionStorage.getItem('userInfo')
  const userInfo = (userInfoStr && userInfoStr !== 'undefined' && userInfoStr !== 'null')
    ? JSON.parse(userInfoStr)
    : null

  const whiteList = ['/auth/login', '/auth/register']
  const isWhite = whiteList.includes(to.path)

  if (token) {
    if (isWhite) {
      next({ path: '/layout/home' })
    } else {
      if (to.meta.needLogin) {
        if (to.meta.isAdmin) {
          if (userInfo && userInfo.userType === 'admin') {
            next()
          } else {
            ElMessage.error('仅系统管理员可访问')
            next({ path: '/layout/home' })
          }
        } else {
          next()
        }
      } else {
        next()
      }
    }
  } else {
    if (isWhite) {
      next()
    } else {
      next({
        path: '/auth/login',
        query: { redirect: to.fullPath }
      })
    }
  }
})

export default router