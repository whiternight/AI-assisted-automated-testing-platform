<template>
  <div class="pwd-wrap">
    <div class="pwd-card">
      <div class="card-header">
        <h2>修改登录密码</h2>
        <span class="sub-desc">更新您的账号密码</span>
      </div>
      <div class="card-body">
        <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="120px" style="width: 520px">
          <el-form-item label="原密码" prop="oldPassword">
            <el-input v-model="pwdForm.oldPassword" show-password placeholder="请输入当前登录密码"></el-input>
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwdForm.newPassword" show-password placeholder="密码至少6位"></el-input>
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPwd">
            <el-input v-model="pwdForm.confirmPwd" show-password placeholder="再次填写新密码"></el-input>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submitPwd" :loading="submitLoading">确认修改</el-button>
            <el-button @click="resetForm">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api/admin'

const pwdFormRef = ref(null)
const submitLoading = ref(false)

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPwd: ''
})

const checkConfirmPwd = (rule, value, callback) => {
  if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请填写原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请填写新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPwd: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: checkConfirmPwd, trigger: 'blur' }
  ]
}

const submitPwd = async () => {
  try {
    await pwdFormRef.value.validate()
  } catch {
    return
  }
  submitLoading.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    location.href = '/auth/login'
  } catch (err) {
    ElMessage.error(err?.message || '密码修改失败')
  } finally {
    submitLoading.value = false
  }
}

const resetForm = () => {
  pwdFormRef.value.resetFields()
}
</script>

<style scoped>
.pwd-wrap {
  max-width: 700px;
}

.pwd-card {
  border: 1px solid var(--mute, #e5e5e5);
  background: var(--paper, #fafafa);
}

.card-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--mute, #e5e5e5);
}

.card-header h2 {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink, #0a0a0a);
  margin: 0;
}

.card-header .sub-desc {
  font-size: 11px;
  color: var(--ghost, #a3a3a3);
}

.card-body {
  padding: 20px;
}

:deep(.el-form-item__label) {
  font-size: 10px !important;
  font-weight: 500 !important;
  color: var(--ghost, #a3a3a3) !important;
  text-transform: uppercase !important;
  letter-spacing: 0.08em !important;
}
</style>
