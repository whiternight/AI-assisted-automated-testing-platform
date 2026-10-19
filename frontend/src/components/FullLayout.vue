<template>
  <div class="full-layout">
    <!-- 顶部 Header -->
    <Header />
    <!-- 下方内容区域，无侧边栏 -->
    <el-main class="full-main">
      <router-view />
    </el-main>
  </div>
</template>

<script setup>
import Header from './Header.vue'
</script>

<style>
.full-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: var(--paper, #fafafa);
}

.full-main {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: var(--paper, #fafafa);
  padding: 0;
  margin: 0 !important;
}

.el-container {
  border: none !important;
}
.el-header, .el-aside, .el-main, .el-footer {
  margin: 0 !important;
}
</style>
