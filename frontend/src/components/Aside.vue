<template>
    <aside class="aside-wrap">
        <el-menu :default-active="activeMenuPath" router class="side-menu">
            <el-menu-item index="/layout/home">
                <el-icon><House /></el-icon>
                <span>首页</span>
            </el-menu-item>
            <el-menu-item index="/layout/Apihub">
                <el-icon><Connection /></el-icon>
                <span>ApiHub</span>
            </el-menu-item>
            <el-menu-item index="/layout/automated">
                <el-icon><Cpu /></el-icon>
                <span>自动化测试</span>
            </el-menu-item>
            <el-sub-menu index="flow-group">
                <template #title>
                    <el-icon><FolderOpened /></el-icon>
                    <span>流程</span>
                </template>
                <el-menu-item index="/layout/originalservice">
                    <el-icon><SetUp /></el-icon>
                    <span>原服务</span>
                </el-menu-item>
                <el-menu-item index="/layout/bpmn/design">
                    <el-icon><Collection /></el-icon>
                    <span>流程设计</span>
                </el-menu-item>
                <el-menu-item index="/layout/testSuite">
                    <el-icon><FolderChecked /></el-icon>
                    <span>测试集</span>
                </el-menu-item>
            </el-sub-menu>
            <el-menu-item index="/layout/settings">
                <el-icon><Setting /></el-icon>
                <span>设置</span>
            </el-menu-item>
        </el-menu>
    </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { House, Connection, Cpu, Collection, SetUp, FolderOpened, FolderChecked, Setting } from '@element-plus/icons-vue'

const route = useRoute()
// 响应式计算当前激活的菜单路径，确保页面切换后菜单高亮状态保持
const activeMenuPath = computed(() => {
    // 处理子路由（如 /layout/bpmn/designer/:id）映射到父级菜单
    const path = route.path
    if (path.startsWith('/layout/bpmn/designer')) return '/layout/bpmn/design'
    if (path.startsWith('/layout/ApiTester')) return '/layout/Apihub'
    if (path.startsWith('/layout/DubboTester')) return '/layout/Apihub'
    return path
})
</script>

<style scoped>
.aside-wrap {
    width: 180px;
    height: 100%;
    border-right: 1px solid var(--mute, #e5e5e5);
    background: var(--paper, #fafafa);
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    overflow: hidden;
}

.side-menu {
    height: 100%;
    border-right: none !important;
    background: transparent !important;
    padding: 8px 0 !important;
}

.side-menu :deep(.el-menu-item) {
    height: 36px;
    line-height: 36px;
    margin: 1px 0;
    padding: 0 16px !important;
    font-size: 12px;
    color: var(--ghost, #a3a3a3);
    border-radius: 0;
    transition: all 0.15s;
    position: relative;
    display: flex;
    align-items: center;
    gap: 8px;
}

.side-menu :deep(.el-menu-item:hover) {
    color: var(--ink, #0a0a0a);
    background: rgba(0, 0, 0, 0.015);
}

.side-menu :deep(.el-menu-item.is-active) {
    color: var(--ink, #0a0a0a);
    font-weight: 500;
    background: rgba(0, 0, 0, 0.02);
}

.side-menu :deep(.el-menu-item.is-active::before) {
    content: '';
    position: absolute;
    left: 0;
    width: 2px;
    height: 16px;
    background: var(--ink, #0a0a0a);
}

.side-menu :deep(.el-sub-menu__title) {
    height: 36px;
    line-height: 36px;
    margin: 1px 0;
    padding: 0 16px !important;
    font-size: 12px;
    color: var(--ghost, #a3a3a3);
    border-radius: 0;
    transition: all 0.15s;
    display: flex;
    align-items: center;
    gap: 8px;
}

.side-menu :deep(.el-sub-menu__title:hover) {
    color: var(--ink, #0a0a0a);
    background: rgba(0, 0, 0, 0.015);
}

.side-menu :deep(.el-sub-menu .el-menu-item) {
    padding-left: 38px !important;
    height: 32px;
    line-height: 32px;
    font-size: 12px;
}

.side-menu :deep(.el-icon) {
    font-size: 14px;
    margin-right: 0;
}
</style>