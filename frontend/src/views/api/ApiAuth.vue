<template>
  <div class="auth-container">
    <div class="auth-section">
      <div class="section-title">鉴权方式</div>
      <div class="auth-selector" @click="showDropdown = !showDropdown">
        <div class="selector-left">
          <el-radio v-model="selectedAuth.type" :value="currentAuthType" />
          <span class="auth-label">{{ selectedAuth.label }}</span>
        </div>
        <div class="selector-right">
          <el-icon class="arrow-icon">
            <ArrowDown />
          </el-icon>
          <el-icon class="eye-icon" name="eye" />
        </div>
      </div>

      <div v-show="showDropdown" class="dropdown-menu">
        <div 
          v-for="auth in authOptions" 
          :key="auth.type"
          class="dropdown-item"
          :class="{ active: selectedAuth.type === auth.type, disabled: auth.disabled }"
          @click="selectAuth(auth)"
        >
          <div class="item-left">
            <el-radio v-model="selectedAuth.type" :value="auth.type" />
            <span class="item-label">{{ auth.label }}</span>
            <span v-if="auth.recommend" class="recommend-tag">推荐</span>
          </div>
          <div class="item-right">
            <span class="item-desc">{{ auth.desc }}</span>
            <el-icon v-if="auth.active" class="check-icon">
              <Check />
            </el-icon>
          </div>
        </div>

        <div class="dropdown-divider"></div>

        <div class="dropdown-item add-item" @click="addAuthComponent">
          <el-icon class="plus-icon">
            <Plus />
          </el-icon>
          <span>新建鉴权组件</span>
        </div>

        <div class="dropdown-divider"></div>

        <div class="dropdown-section">
          <span class="section-label">自行设置</span>
          <div 
            v-for="auth in customAuthOptions" 
            :key="auth.type"
            class="dropdown-item"
            :class="{ active: selectedAuth.type === auth.type }"
            @click="selectAuth(auth)"
          >
            <div class="item-left">
              <el-radio v-model="selectedAuth.type" :value="auth.type" />
              <span class="item-label">{{ auth.label }}</span>
            </div>
            <div class="item-right">
              <span class="item-desc">{{ auth.desc }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="selectedAuth.type === 'apiKey'" class="auth-form">
        <el-form label-width="80px">
          <el-form-item label="参数名">
            <el-input v-model="authConfig.apiKey.name" placeholder="请输入参数名" />
          </el-form-item>
          <el-form-item label="参数值">
            <el-input v-model="authConfig.apiKey.value" type="password" placeholder="请输入参数值" />
          </el-form-item>
          <el-form-item label="位置">
            <el-select v-model="authConfig.apiKey.location">
              <el-option label="Header" value="header" />
              <el-option label="Query" value="query" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <div v-if="selectedAuth.type === 'bearerToken'" class="auth-form">
        <el-form label-width="80px">
          <el-form-item label="Token">
            <el-input v-model="authConfig.bearerToken.value" type="password" placeholder="请输入 Token" />
          </el-form-item>
        </el-form>
      </div>

      <div v-if="selectedAuth.type === 'jwtBearer'" class="auth-form">
        <el-form label-width="80px">
          <el-form-item label="Token">
            <el-input v-model="authConfig.jwtBearer.value" type="password" placeholder="请输入 JWT Token" />
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ArrowDown, Check, Plus } from '@element-plus/icons-vue'

const showDropdown = ref(false)
const selectedAuth = reactive({
  type: 'inherit',
  label: '继承'
})

const authOptions = [
  { type: 'none', label: '无需鉴权', desc: '不使用任何鉴权', active: false },
  { type: 'inherit', label: '继承', desc: '跟随父级目录的配置', active: true },
  { type: 'component', label: '鉴权组件', desc: '可复用，兼容 OpenAPI 规范', recommend: true, disabled: false }
]

const customAuthOptions = [
  { type: 'apiKey', label: 'API Key', desc: '' },
  { type: 'bearerToken', label: 'Bearer Token', desc: '' },
  { type: 'jwtBearer', label: 'JWT Bearer', desc: '' }
]

const authConfig = reactive({
  apiKey: { name: '', value: '', location: 'header' },
  bearerToken: { value: '' },
  jwtBearer: { value: '' }
})

const currentAuthType = computed(() => selectedAuth.type)

const selectAuth = (auth) => {
  if (!auth.disabled) {
    selectedAuth.type = auth.type
    selectedAuth.label = auth.label
    showDropdown.value = false
  }
}

const addAuthComponent = () => {
  showDropdown.value = false
}

defineExpose({
  getAuthConfig: () => ({
    type: selectedAuth.type,
    config: authConfig
  })
})
</script>

<style scoped>
.auth-container {
  padding: 12px;
}

.auth-section {
  background: var(--paper, #fafafa);
  border: 1px solid var(--mute, #e5e5e5);
  padding: 12px;
}

.section-title {
  font-size: 10px;
  color: var(--ghost, #a3a3a3);
  margin-bottom: 12px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.auth-selector {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: var(--smoke, #f5f5f5);
  border: 1px solid var(--mute, #e5e5e5);
  cursor: pointer;
}

.auth-selector:hover {
  border-color: var(--ink, #0a0a0a);
}

.selector-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.auth-label {
  font-size: 12px;
  color: var(--ink, #0a0a0a);
}

.selector-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.arrow-icon, .eye-icon {
  color: var(--ghost, #a3a3a3);
}

.dropdown-menu {
  margin-top: 8px;
  background: var(--paper, #fafafa);
  border: 1px solid var(--mute, #e5e5e5);
  overflow: hidden;
}

.dropdown-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  cursor: pointer;
}

.dropdown-item:hover {
  background: var(--smoke, #f5f5f5);
}

.dropdown-item.active {
  background: var(--smoke, #f5f5f5);
}

.dropdown-item.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.item-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-label {
  font-size: 12px;
  color: var(--ink, #0a0a0a);
}

.recommend-tag {
  font-size: 10px;
  color: var(--ink, #0a0a0a);
  background: var(--smoke, #f5f5f5);
  border: 1px solid var(--mute, #e5e5e5);
  padding: 2px 6px;
}

.item-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-desc {
  font-size: 11px;
  color: var(--ghost, #a3a3a3);
}

.check-icon {
  color: var(--ink, #0a0a0a);
}

.add-item {
  color: var(--ink, #0a0a0a);
}

.plus-icon {
  margin-right: 4px;
}

.dropdown-divider {
  height: 1px;
  background: var(--mute, #e5e5e5);
  margin: 4px 0;
}

.dropdown-section {
  padding-top: 4px;
}

.section-label {
  display: block;
  font-size: 10px;
  color: var(--ghost, #a3a3a3);
  padding: 8px 12px 4px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.auth-form {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--mute, #e5e5e5);
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.auth-form :deep(.el-form-item__label) {
  color: var(--ghost, #a3a3a3);
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.auth-form :deep(.el-input__wrapper) {
  background: var(--paper, #fafafa);
  border-color: var(--mute, #e5e5e5);
  border-radius: 0;
}

.auth-form :deep(.el-input__wrapper:hover) {
  border-color: var(--ink, #0a0a0a);
}

.auth-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--ink, #0a0a0a);
  box-shadow: none;
}

.auth-form :deep(.el-input__inner) {
  color: var(--ink, #0a0a0a);
}

.auth-form :deep(.el-select) {
  width: 100%;
}
</style>