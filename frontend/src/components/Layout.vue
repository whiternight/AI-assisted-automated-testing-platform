<template>
  <div class="app-layout">
    <!-- 顶部 Header -->
    <Header />
    <!-- 下方左右分栏 -->
    <div class="layout-body">
      <Aside />
      <div class="right-column">
        <el-main class="main-content">
          <router-view />
        </el-main>
        <Footer />
      </div>
    </div>
  </div>
</template>
  
<script setup>
import Header from './Header.vue'
import Aside from './Aside.vue'
import Footer from './Footer.vue'
</script>
  
<style>
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: var(--paper, #fafafa);
}

.layout-body {
  flex: 1;
  display: flex;
  min-height: 0;
  overflow: hidden;
}

.right-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.main-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: var(--paper, #fafafa);
  padding: 20px 24px;
  margin: 0 !important;
}

/* 清除element自带组件间隙 */
.el-container {
  border: none !important;
}
.el-header, .el-aside, .el-main, .el-footer {
  margin: 0 !important;
}
</style>
