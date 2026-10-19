<template>
  <div class="login-form">
    <el-form ref="ruleFormRef" :model="formData" :rules="rules" label-position="top">
      <el-form-item label="邮箱 / 用户名" prop="username">
        <el-input v-model="formData.username" placeholder="name@company.com" maxlength="20" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="formData.password" placeholder="••••••••" type="password" show-password maxlength="20" />
      </el-form-item>
      <div class="form-extras">
        <label class="remember-me">
          <el-checkbox v-model="rememberMe" />
          <span>记住我</span>
        </label>
        <a href="#" class="forgot-link">忘记密码?</a>
      </div>
      <el-button class="btn-login" type="primary" @click="submitForm" :loading="loading">登录</el-button>
    </el-form>
    <div class="form-footer">
      <p>还没有账号? <router-link to="/auth/register">申请试用</router-link></p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, getUserDetail } from '@/api/admin'
import { useUserStore } from '@/composables/useUserStore.js'
import { useAiAssistant } from '@/composables/useAiAssistant.js'

const router = useRouter()
const { setUserInfo } = useUserStore()
const { initAssistant } = useAiAssistant()
const route = useRoute()
const ruleFormRef = ref(null)
const loading = ref(false)
const rememberMe = ref(true)

const formData = reactive({
  username: '',
  password: ''
})

// 用户名格式校验：字母、数字、下划线，4~20位，不能以数字开头
const validateUsername = (rule, value, callback) => {
  if (!value) return callback(new Error('请输入用户名'))
  if (/^\d/.test(value)) return callback(new Error('用户名不能以数字开头'))
  if (!/^[a-zA-Z_][a-zA-Z0-9_]{3,19}$/.test(value)) {
    return callback(new Error('用户名须为4~20位字母、数字或下划线'))
  }
  callback()
}

const rules = {
  username: [{ required: true, validator: validateUsername, trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6~20位', trigger: 'blur' }
  ]
}

const submitForm = async () => {
  if (!ruleFormRef.value) return
  try {
    await ruleFormRef.value.validate()
  } catch {
    return
  }
  loading.value = true

  try {
    const res = await login(formData)
    console.log('登录返回', res)

    if (res.code !== 200) {
      ElMessage.error(res.message || '登录校验失败')
      loading.value = false
      return
    }

    const { token, userId, username, status } = res.data

    if (status === 2) {
      ElMessage.error('账号已锁定，请30分钟后重试')
      loading.value = false
      return
    }
    if (status === 0) {
      ElMessage.warning('账号待管理员审批，当前仅只读权限')
    }

    if (token) {
      // 保存 token
      sessionStorage.setItem('token', token)

      // 第一步：保存基础用户信息
      const userCache = {
        userId,
        username,
        status
      }
      sessionStorage.setItem('userInfo', JSON.stringify(userCache))
      setUserInfo(userCache)

      // 第二步：调用 getUserDetail 获取完整的用户信息（包含角色、部门、真实姓名等）
      try {
        const detailRes = await getUserDetail()
        console.log('用户详情返回：', detailRes)
        
        if (detailRes.code === 200 || detailRes.code === 20000) {
          const detailData = detailRes.data || detailRes
          
          // 合并完整的用户信息
          const fullUserInfo = {
            userId: detailData.id || userId,
            username: detailData.username || username,
            realName: detailData.realName || '',
            status: detailData.status || status,
            // 角色信息（根据实际返回结构调整）
            roleId: detailData.roleId || detailData.role?.id || detailData.departments?.[0]?.roleId || '',
            roleName: detailData.roleName || detailData.role?.name || detailData.departments?.[0]?.roleName || '',
            // 部门信息
            departmentId: detailData.departmentId || detailData.department?.id || detailData.departments?.[0]?.id || '',
            departmentName: detailData.departmentName || detailData.department?.name || detailData.departments?.[0]?.name || '',
            // 其他字段
            departments: detailData.departments || []
          }
          
          console.log('完整用户信息：', fullUserInfo)
          setUserInfo(fullUserInfo)
        } else {
          // 如果获取详情失败，使用基础信息
          console.warn('获取用户详情失败，使用基础信息')
        }
      } catch (detailError) {
        console.warn('获取用户详情异常：', detailError)
        // 即使获取详情失败，也不影响登录
      }

      ElMessage.success('登录成功')
      // 登录成功后同步初始化 AI 助手会话
      initAssistant()
      const redirect = route.query.redirect || '/layout/home'
      router.push(redirect)
    } else {
      ElMessage.error('登录失败，服务器未返回凭证')
    }

  } catch (err) {
    ElMessage.error('网络请求异常')
    console.error(err)
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-form {
  text-align: left;

  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item__label) {
    font-size: 10px !important;
    font-weight: 500 !important;
    color: var(--ghost, #a3a3a3) !important;
    text-transform: uppercase !important;
    letter-spacing: 0.08em !important;
    margin-bottom: 4px !important;
  }

  .form-extras {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 24px;

    .remember-me {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      color: var(--ghost, #a3a3a3);
      cursor: pointer;
    }

    .forgot-link {
      font-size: 12px;
      color: var(--ghost, #a3a3a3);
      transition: color 0.2s;
      &:hover {
        color: var(--ink, #0a0a0a);
      }
    }
  }

  .btn-login {
    width: 100%;
    height: 38px;
    font-size: 13px;
    font-weight: 500;
    letter-spacing: 0.02em;
  }

  .form-footer {
    margin-top: 24px;
    padding-top: 20px;
    border-top: 1px solid var(--mute, #e5e5e5);

    p {
      font-size: 12px;
      color: var(--ghost, #a3a3a3);

      a {
        color: var(--ink, #0a0a0a);
        font-weight: 500;
        &:hover {
          color: var(--ghost, #a3a3a3);
        }
      }
    }
  }
}
</style>