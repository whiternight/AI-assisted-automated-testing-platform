<template>
    <div class="profile-page">
        <div class="info-card">
            <div class="card-header">
                <h2>个人基础信息</h2>
                <span class="sub-desc">查看当前登录账号完整资料</span>
            </div>
            <el-form :model="userForm" label-width="140px" v-loading="loadLoading" class="info-form">
                <el-form-item label="登录用户名">
                    <el-input v-model="userForm.username" disabled class="disabled-input" />
                </el-form-item>
                <el-form-item label="真实姓名">
                    <el-input v-model="userForm.realName" disabled class="disabled-input" />
                </el-form-item>
                <el-form-item label="所属部门">
                    <el-input v-model="userForm.departmentName" disabled class="disabled-input" />
                </el-form-item>
                <el-form-item label="账号权限角色">
                    <el-input v-model="roleText" disabled class="disabled-input" />
                </el-form-item>
            </el-form>
        </div>
    </div>
</template>

<script setup lang="js">
import { reactive, computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserDetail, getUserDetailById } from '@/api/admin'
import { useUserStore } from '@/composables/useUserStore.js'

const { userInfo, setUserInfo } = useUserStore()

const userForm = reactive({
    username: '',
    realName: '',
    departmentName: ''
})
const loadLoading = ref(false)
const userRaw = ref(null)

const roleText = computed(() => {
    const user = userRaw.value
    if (!user) return '未知'
    if (user.departments && user.departments.length > 0) {
        return user.departments[0].roleName || '普通用户'
    }
    return '普通用户'
})

const loadUserInfo = async () => {
    loadLoading.value = true
    try {
        // 优先使用已登录账号的 userId（store 中的值来自登录响应，是最可信的当前身份标识）
        let userId = userInfo.value?.userId

        // 若 store 中无 userId（如直接刷新页面），再 fallback 到 /auth/getUser
        if (!userId) {
            const res1 = await getUserDetail()
            const userData = res1.data || res1
            userId = userData.id
        }

        if (!userId) {
            ElMessage.warning('未获取到用户ID')
            return
        }

        const res2 = await getUserDetailById({ userId: userId })
        const detailData = res2.data || res2

        userRaw.value = detailData

        userForm.username = detailData.username || ''
        userForm.realName = detailData.realName || ''

        if (detailData.departments && detailData.departments.length > 0) {
            userForm.departmentName = detailData.departments[0].departmentName || ''
        } else {
            userForm.departmentName = ''
        }

        // 同步更新用户 store，确保顶部导航栏等处展示的身份信息实时一致
        setUserInfo({
            userId: detailData.id || userId,
            username: detailData.username || userInfo.value?.username || '',
            realName: detailData.realName || '',
            departments: detailData.departments || [],
            roleName: detailData.departments?.[0]?.roleName || '',
            departmentName: userForm.departmentName
        })

    } catch (err) {
        console.error('接口请求失败', err)
        ElMessage.error('拉取个人信息接口异常')
    } finally {
        loadLoading.value = false
    }
}

onMounted(() => {
    loadUserInfo()
})

// 当用户身份变更（如登录切换账号）时，自动重新拉取个人信息，保证页面数据实时准确
watch(
    () => userInfo.value?.userId,
    (newId, oldId) => {
        if (newId && newId !== oldId) {
            loadUserInfo()
        }
    }
)
</script>

<style scoped lang="scss">
.profile-page {
    padding: 0;
    background: var(--paper, #fafafa);
    min-height: calc(100vh - 60px);
}

.info-card {
    max-width: 800px;
    background: var(--paper, #fafafa);
    border: 1px solid var(--mute, #e5e5e5);
    padding: 0;

    .card-header {
        display: flex;
        flex-direction: column;
        gap: 4px;
        padding: 16px 20px;
        border-bottom: 1px solid var(--mute, #e5e5e5);

        h2 {
            font-size: 13px;
            font-weight: 500;
            color: var(--ink, #0a0a0a);
            margin: 0;
        }

        .sub-desc {
            font-size: 11px;
            color: var(--ghost, #a3a3a3);
        }
    }

    .info-form {
        padding: 20px;

        :deep(.el-form-item) {
            margin-bottom: 16px;
        }

        :deep(.el-form-item__label) {
            font-size: 10px !important;
            font-weight: 500 !important;
            color: var(--ghost, #a3a3a3) !important;
            text-transform: uppercase !important;
            letter-spacing: 0.08em !important;
        }

        .disabled-input {
            :deep(.el-input__inner) {
                background-color: var(--smoke, #f5f5f5);
                border-radius: 0;
                height: 34px;
                font-size: 12px;
                padding: 0 11px;
                color: var(--ink, #0a0a0a);
            }
        }
    }
}
</style>