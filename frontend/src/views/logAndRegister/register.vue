<template>
    <div class="register-form">
        <el-form ref="ruleFormRef" :model="formData" :rules="rules" label-position="top" style="width:100%">
            <el-form-item label="用户名" prop="username">
                <el-input v-model="formData.username" placeholder="4~20位字母、数字或下划线" maxlength="20" />
            </el-form-item>
            <el-form-item label="真实姓名" prop="realName">
                <el-input v-model="formData.realName" placeholder="2~10位中文姓名" maxlength="10" />
            </el-form-item>
            <el-form-item label="部门" prop="departmentId">
                <el-select v-model="formData.departmentId" placeholder="选择部门" style="width:100%">
                    <el-option v-for="item in deptList" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
            </el-form-item>
            <el-form-item label="申请角色" prop="roleId">
                <el-select v-model="formData.roleId" placeholder="选择角色" style="width:100%">
                    <el-option v-for="item in roleList" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
            </el-form-item>
            <el-form-item label="密码" prop="password">
                <el-input v-model="formData.password" type="password" show-password placeholder="6~20位，须含字母和数字" maxlength="20" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
                <el-input v-model="formData.confirmPassword" type="password" show-password placeholder="再次输入密码" maxlength="20" />
            </el-form-item>
            <el-button class="btn-register" type="primary" @click="submitForm" :loading="loading">提交注册</el-button>
        </el-form>
        <div class="form-footer">
            <p>已有账户? <router-link to="/auth/login">去登录</router-link></p>
        </div>
    </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register, getRoleList, getDeptList } from '@/api/admin'

const router = useRouter()
const ruleFormRef = ref(null)
const loading = ref(false)
const roleList = ref([])
const deptList = ref([])

const formData = reactive({
    username: '',
    password: '',
    confirmPassword: '',
    realName: '',
    departmentId: '',
    roleId: ''
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

// 真实姓名校验：仅中文，2~10位
const validateRealName = (rule, value, callback) => {
    if (!value) return callback(new Error('请输入真实姓名'))
    if (!/^[\u4e00-\u9fa5]{2,10}$/.test(value)) {
        return callback(new Error('姓名须为2~10位中文'))
    }
    callback()
}

// 密码强度校验：6~20位，须同时包含字母和数字
const validatePassword = (rule, value, callback) => {
    if (!value) return callback(new Error('请设置密码'))
    if (value.length < 6 || value.length > 20) {
        return callback(new Error('密码长度为6~20位'))
    }
    if (!/[a-zA-Z]/.test(value)) {
        return callback(new Error('密码须包含至少一个字母'))
    }
    if (!/\d/.test(value)) {
        return callback(new Error('密码须包含至少一个数字'))
    }
    callback()
}

// 密码一致性校验
const validatePwd = (rule, value, callback) => {
    if (!value) return callback(new Error('请确认密码'))
    if (value !== formData.password) {
        callback(new Error('两次输入密码不一致'))
    } else {
        callback()
    }
}

const rules = {
    username: [{ required: true, validator: validateUsername, trigger: 'blur' }],
    realName: [{ required: true, validator: validateRealName, trigger: 'blur' }],
    departmentId: [{ required: true, message: '请选择部门', trigger: 'change' }],
    roleId: [{ required: true, message: '请选择申请角色', trigger: 'change' }],
    password: [{ required: true, validator: validatePassword, trigger: 'blur' }],
    confirmPassword: [
        { required: true, validator: validatePwd, trigger: 'blur' }
    ]
}

// 监听密码变化，实时校验确认密码
watch(() => formData.password, () => {
    if (formData.confirmPassword && ruleFormRef.value) {
        ruleFormRef.value.validateField('confirmPassword')
    }
})

const submitForm = async () => {
    // 1. 表单实例不存在 / 正在加载中，直接拦截
    if (!ruleFormRef.value || loading.value) return

    try {
        // 2. 表单校验，失败会直接进入catch
        await ruleFormRef.value.validate()
        // 3. 校验通过，开启加载状态
        loading.value = true

        // 剔除确认密码字段
        const submitParams = { ...formData }
        delete submitParams.confirmPassword
        // 调用注册接口
        await register(submitParams)

        ElMessage.success('注册提交成功，请等待管理员审批激活')
        router.replace('/auth/login')
    } catch (err) {
        // 两种情况都会进这里：表单校验失败 / 接口请求失败
        // 如果是表单校验错误，不需要弹大报错，组件自带红色提示
        if (!err?.fields) {
            ElMessage.error(err?.msg || err?.message || '注册失败，请稍后重试')
        }
    } finally {
        // 无论成功失败，关闭loading
        loading.value = false
    }
}

onMounted(async () => {
    // 并行加载角色和部门列表，任一失败不影响另一个
    const [roleResult, deptResult] = await Promise.allSettled([getRoleList(), getDeptList()])

    // 角色列表
    if (roleResult.status === 'fulfilled') {
        const roleRes = roleResult.value
        const roleArr = roleRes?.data || []
        roleList.value = Array.isArray(roleArr) ? roleArr : []
    } else {
        console.warn('角色列表加载失败', roleResult.reason)
        ElMessage.warning('角色数据加载异常，请联系管理员')
    }

    // 部门列表
    if (deptResult.status === 'fulfilled') {
        const deptRes = deptResult.value
        // 兼容处理：如果返回的是HTML页面，视为异常
        if (typeof deptRes === 'string' && deptRes.includes('<!DOCTYPE html>')) {
            console.error('部门接口返回HTML页面，后端接口异常')
            ElMessage.warning('部门数据加载异常')
        } else {
            const deptArr = deptRes?.data || []
            deptList.value = Array.isArray(deptArr) ? deptArr : []
        }
    } else {
        console.warn('部门列表加载失败', deptResult.reason)
        ElMessage.warning('部门数据加载异常，请联系管理员')
    }
})
</script>

<style lang="scss" scoped>
.register-form {
    text-align: left;

    :deep(.el-form-item) {
        margin-bottom: 16px;
    }

    :deep(.el-form-item__label) {
        font-size: 10px !important;
        font-weight: 500 !important;
        color: var(--ghost, #a3a3a3) !important;
        text-transform: uppercase !important;
        letter-spacing: 0.08em !important;
        margin-bottom: 4px !important;
    }

    .btn-register {
        width: 100%;
        height: 38px;
        font-size: 13px;
        font-weight: 500;
        margin-top: 8px;
    }

    .form-footer {
        margin-top: 20px;
        padding-top: 16px;
        border-top: 1px solid var(--mute, #e5e5e5);
        text-align: center;

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