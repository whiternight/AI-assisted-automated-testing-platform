<template>
  <div class="home-container">
    <!-- 欢迎标题 -->
    <div class="welcome-box">
      <h1>智能测试平台</h1>
      <p>一站式 HTTP / Dubbo 接口自动化测试解决方案</p>
    </div>

    <!-- 数据统计面板 -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">总接口用例</div>
        <div class="stat-number">{{ statistics.totalCaseCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">HTTP接口</div>
        <div class="stat-number">{{ statistics.httpCaseCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Dubbo接口</div>
        <div class="stat-number">{{ statistics.dubboCaseCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">测试成功率</div>
        <div class="stat-number">{{ statistics.successRate }}%</div>
        <div class="bar"><div class="bar-fill" :style="{ width: statistics.successRate + '%' }"></div></div>
      </div>
    </div>

    <!-- 核心功能卡片 -->
    <div class="func-grid">
      <div class="func-card" @click="$router.push('/layout/ApiTester')">
        <div class="func-header">
          <h3>HTTP 接口测试</h3>
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="5" y1="12" x2="19" y2="12"/>
            <polyline points="12 5 19 12 12 19"/>
          </svg>
        </div>
        <p>支持 GET/POST/PUT/DELETE 可视化调试，断言规则配置</p>
        <div class="func-stats">
          <span>GET</span>
          <span>POST</span>
          <span>PUT</span>
          <span>DELETE</span>
        </div>
      </div>

      <div class="func-card" @click="$router.push('/layout/DubboTester')">
        <div class="func-header">
          <h3>Dubbo 接口测试</h3>
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="5" y1="12" x2="19" y2="12"/>
            <polyline points="12 5 19 12 12 19"/>
          </svg>
        </div>
        <p>支持注册中心、直连、泛化调用、服务测试</p>
        <div class="func-stats">
          <span>Dubbo</span>
          <span>泛化调用</span>
          <span>直连</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getApiCaseStatistics } from '@/api/admin.js'
import { ElMessage } from 'element-plus'

const statistics = ref({
  totalCaseCount: 0,
  httpCaseCount: 0,
  dubboCaseCount: 0,
  successRate: 0
})

const loadStatistics = async () => {
  try {
    const res = await getApiCaseStatistics()
    const data = res.data || res
    if (data) {
      statistics.value = {
        totalCaseCount: data.totalCaseCount || 0,
        httpCaseCount: data.httpCaseCount || 0,
        dubboCaseCount: data.dubboCaseCount || 0,
        successRate: data.successRate != null ? data.successRate : 0
      }
    }
  } catch (e) {
    console.error('获取首页统计数据失败', e)
    ElMessage.error('获取首页统计数据失败')
  }
}

onMounted(() => {
  loadStatistics()
})
</script>

<style scoped>
.home-container {
  width: 100%;
  height: 100%;
  padding: 0;
}

.welcome-box {
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--mute, #e5e5e5);
}

.welcome-box h1 {
  font-size: 20px;
  font-weight: 500;
  color: var(--ink, #0a0a0a);
  margin-bottom: 6px;
  letter-spacing: -0.02em;
}

.welcome-box p {
  font-size: 12px;
  color: var(--ghost, #a3a3a3);
  letter-spacing: 0.02em;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.stat-card {
  padding: 16px;
  border: 1px solid var(--mute, #e5e5e5);
  background: var(--paper, #fafafa);
  transition: border-color 0.25s;
}

.stat-card:hover {
  border-color: var(--fog, #d4d4d4);
}

.stat-label {
  font-size: 10px;
  font-weight: 500;
  color: var(--ghost, #a3a3a3);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 6px;
}

.stat-number {
  font-size: 22px;
  font-weight: 300;
  color: var(--ink, #0a0a0a);
  margin-bottom: 4px;
}

.bar {
  height: 2px;
  background: var(--mute, #e5e5e5);
  margin-top: 6px;
}

.bar-fill {
  height: 100%;
  background: var(--ink, #0a0a0a);
  transition: width 0.6s;
}

.func-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.func-card {
  padding: 20px;
  border: 1px solid var(--mute, #e5e5e5);
  background: var(--paper, #fafafa);
  cursor: pointer;
  transition: border-color 0.25s;
}

.func-card:hover {
  border-color: var(--ink, #0a0a0a);
}

.func-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.func-header h3 {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink, #0a0a0a);
}

.func-header svg {
  color: var(--ghost, #a3a3a3);
  transition: color 0.2s;
}

.func-card:hover .func-header svg {
  color: var(--ink, #0a0a0a);
}

.func-card p {
  font-size: 12px;
  color: var(--ghost, #a3a3a3);
  line-height: 1.5;
  margin-bottom: 16px;
}

.func-stats {
  display: flex;
  gap: 6px;
}

.func-stats span {
  padding: 3px 8px;
  font-size: 10px;
  font-weight: 500;
  border: 1px solid var(--mute, #e5e5e5);
  color: var(--ghost, #a3a3a3);
}
</style>