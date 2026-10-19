<template>
    <div class="profile-layout">
      <div class="profile-aside">
        <div class="header-row">
          <div class="back-btn" @click="$router.push('/layout/home')">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </div>
          <div class="logo-group">
            <div class="logo-icon">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2.5">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
              </svg>
            </div>
            <span class="logo">智能测试平台</span>
          </div>
        </div>
        <el-menu router :default-active="activePath">
          <div class="menu-group-label">账户设置</div>
          <el-menu-item index="/profile/index">个人信息</el-menu-item>
          <el-menu-item index="/profile/pwd">修改密码</el-menu-item>
  
          <template v-if="isAdmin">
            <div class="menu-group-label">用户管理</div>
            <el-menu-item index="/profile/newUser">待审批注册</el-menu-item>
            <el-menu-item index="/profile/userList">全部用户列表</el-menu-item>
          </template>
        </el-menu>
      </div>
      <div class="profile-main">
        <router-view />
      </div>
    </div>
  </template>
  
  <script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/composables/useUserStore.js'
const route = useRoute()
const activePath = computed(() => route.path)

const { isAdmin } = useUserStore()
</script>
  
  <style lang="scss" scoped>
  .profile-layout {
    display: flex;
    height: 100vh;
    width: 100%;
    margin: 0;
    padding: 0;
    background: var(--paper, #fafafa);
  }
  .profile-aside {
    width: 200px;
    background: var(--paper, #fafafa);
    border-right: 1px solid var(--mute, #e5e5e5);
    flex-shrink: 0;
    display: flex;
    flex-direction: column;

    .header-row {
      height: 48px;
      display: flex;
      align-items: center;
      border-bottom: 1px solid var(--mute, #e5e5e5);
      padding: 0 12px;
      gap: 8px;

      .back-btn {
        padding: 4px;
        cursor: pointer;
        color: var(--ghost, #a3a3a3);
        display: flex;
        align-items: center;
        transition: color 0.15s;
        &:hover {
          color: var(--ink, #0a0a0a);
        }
      }
      .logo-group {
        display: flex;
        align-items: center;
        gap: 6px;
      }
      .logo-icon {
        width: 22px;
        height: 22px;
        background: var(--ink, #0a0a0a);
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .logo {
        font-size: 13px;
        font-weight: 500;
        color: var(--ink, #0a0a0a);
        letter-spacing: -0.02em;
      }
    }

    .menu-group-label {
      padding: 10px 16px;
      font-size: 10px;
      color: var(--ghost, #a3a3a3);
      text-transform: uppercase;
      letter-spacing: 0.08em;
      font-weight: 500;
    }

    :deep(.el-menu) {
      border-right: none;
      background: transparent;
      padding: 4px 0;
    }
    :deep(.el-menu-item) {
      height: 34px;
      line-height: 34px;
      font-size: 12px;
      color: var(--ghost, #a3a3a3);
      border-radius: 0;
      transition: all 0.15s;
      position: relative;
      padding-left: 16px !important;
      &::before {
        content: '';
        position: absolute;
        left: 0;
        width: 2px;
        height: 14px;
        background: transparent;
        transition: background 0.15s;
      }
      &:hover {
        color: var(--ink, #0a0a0a);
        background: rgba(0, 0, 0, 0.015);
      }
      &.is-active {
        color: var(--ink, #0a0a0a);
        font-weight: 500;
        background: rgba(0, 0, 0, 0.02);
        &::before {
          background: var(--ink, #0a0a0a);
        }
      }
    }
  }
  .profile-main {
    flex: 1;
    padding: 24px;
    background: var(--paper, #fafafa);
    overflow-y: auto;
    height: 100vh;
    box-sizing: border-box;
  }
  </style>