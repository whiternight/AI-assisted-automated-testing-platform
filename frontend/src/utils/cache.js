import { getDepartmentTree, getUserList } from '@/api/admin'

// 应用列表缓存（避免每个页面重复调用 getDepartmentTree）
let appsCache = null
let appsCachePromise = null

// 部门树缓存（保留完整结构，用于按部门过滤）
let deptTreeCache = null
let deptTreeCachePromise = null

// 用户列表缓存
let usersCache = null
let usersCachePromise = null

/**
 * 获取部门树（带缓存，保留完整结构）
 * @param {boolean} force 是否强制刷新缓存
 */
export const loadDepartmentTree = async (force = false) => {
  if (deptTreeCache && !force) return deptTreeCache
  if (deptTreeCachePromise && !force) return deptTreeCachePromise

  deptTreeCachePromise = (async () => {
    try {
      const res = await getDepartmentTree()
      let treeData = []
      if (Array.isArray(res)) {
        treeData = res
      } else if (res && Array.isArray(res.data)) {
        treeData = res.data
      }
      deptTreeCache = treeData
      return treeData
    } catch (e) {
      console.error('加载部门树失败', e)
      deptTreeCache = []
      return []
    } finally {
      deptTreeCachePromise = null
    }
  })()

  return deptTreeCachePromise
}

/**
 * 获取所有应用列表（带缓存）
 * @param {boolean} force 是否强制刷新缓存
 */
export const loadAllApps = async (force = false) => {
  if (appsCache && !force) return appsCache
  if (appsCachePromise && !force) return appsCachePromise

  appsCachePromise = (async () => {
    try {
      const treeData = await loadDepartmentTree(force)
      const apps = []
      treeData.forEach(dept => {
        const appList = dept.applications || dept.children || []
        if (Array.isArray(appList)) {
          appList.forEach(app => {
            if (app && app.id) apps.push({ id: app.id, name: app.name })
          })
        }
      })
      appsCache = apps
      return apps
    } catch (e) {
      console.error('加载应用列表失败', e)
      appsCache = []
      return []
    } finally {
      appsCachePromise = null
    }
  })()

  return appsCachePromise
}

/**
 * 获取用户列表（带缓存）
 * @param {boolean} force 是否强制刷新缓存
 */
export const loadAllUsers = async (force = false) => {
  if (usersCache && !force) return usersCache
  if (usersCachePromise && !force) return usersCachePromise

  usersCachePromise = (async () => {
    try {
      const res = await getUserList({ pageNum: 1, pageSize: 500 })
      const data = res.data || res
      const records = data.records || data.list || []
      usersCache = records
      return records
    } catch (e) {
      console.error('加载用户列表失败', e)
      usersCache = []
      return []
    } finally {
      usersCachePromise = null
    }
  })()

  return usersCachePromise
}

/**
 * 获取用户名称映射 { userId: userName }
 */
export const loadUserNameMap = async (force = false) => {
  const users = await loadAllUsers(force)
  const map = {}
  users.forEach(u => {
    map[u.id] = u.realName || u.username || String(u.id)
  })
  return map
}
