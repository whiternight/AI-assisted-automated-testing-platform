<template>
    <div class="auth-layout">
        <!-- 网格背景 -->
        <div class="grid-bg"></div>
        <!-- 居中卡片 -->
        <div class="card-wrapper reveal">
            <!-- Logo 区域 -->
            <div class="brand-header">
                <div class="brand-icon">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2">
                        <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
                    </svg>
                </div>
                <span class="brand-name">智能测试平台</span>
            </div>
            <p class="brand-desc">自动化测试平台</p>
            <!-- 路由视图 -->
            <router-view></router-view>
        </div>
        <p class="copyright">© 2026 智能测试平台</p>
    </div>
</template>

<script setup>
</script>

<style lang="scss" scoped>
.auth-layout {
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    overflow: hidden;
    background: var(--paper, #fafafa);
}

.grid-bg {
    position: absolute;
    inset: 0;
    background-image:
        linear-gradient(var(--mute, #e5e5e5) 1px, transparent 1px),
        linear-gradient(90deg, var(--mute, #e5e5e5) 1px, transparent 1px);
    background-size: 60px 60px;
    opacity: 0.2;
}

.card-wrapper {
    position: relative;
    z-index: 10;
    width: 100%;
    max-width: 400px;
    margin: 0 16px;
    padding: 40px 32px;
    border: 1px solid var(--mute, #e5e5e5);
    background: var(--paper, #fafafa);
    text-align: center;
}

.brand-header {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;
}

.brand-icon {
    width: 32px;
    height: 32px;
    background: var(--ink, #0a0a0a);
    display: flex;
    align-items: center;
    justify-content: center;
}

.brand-name {
    font-size: 16px;
    font-weight: 500;
    letter-spacing: -0.02em;
    color: var(--ink, #0a0a0a);
}

.brand-desc {
    font-size: 12px;
    color: var(--ghost, #a3a3a3);
    letter-spacing: 0.05em;
    margin-bottom: 32px;
}

.copyright {
    position: absolute;
    bottom: 20px;
    font-size: 10px;
    color: var(--fog, #d4d4d4);
    letter-spacing: 0.1em;
}
</style>