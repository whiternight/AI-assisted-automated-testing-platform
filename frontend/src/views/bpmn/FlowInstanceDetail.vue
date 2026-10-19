<template>
  <div class="fid-root">
    <!-- 顶部信息栏 -->
    <div class="top-bar">
      <span class="back-btn" @click="$router.back()">← 返回</span>
      <span class="flow-name">{{ detail?.processName || '流程实例' }}</span>
      <el-tag v-if="detail" :type="detail.isErrorEnded ? 'danger' : (detail.endTime ? 'success' : 'warning')" size="small">
        {{ detail.isErrorEnded ? '异常终止' : (detail.endTime ? '已完成' : '运行中') }}
      </el-tag>
      <span class="time-info" v-if="detail">
        {{ fmt(detail.startTime) }} → {{ fmt(detail.endTime) }} · {{ detail.durationMs }}ms
      </span>
    </div>

    <!-- 步骤导航条 -->
    <div class="stepper-wrap" v-if="nodes.length">
      <div class="stepper">
        <template v-for="(n, i) in nodes" :key="i">
          <div :class="['step', getNodeStatus(n), { active: selectedIdx === i }]" @click="selectNode(i)">
            <span class="step-icon">{{ stepIcon(n) }}</span>
            <span class="step-name">{{ n.activityName || n.activityId }}</span>
            <span class="step-type">{{ typeLabel(n.activityType) }}</span>
            <span class="step-time" v-if="n.durationMs || n.durationMs === 0">{{ n.durationMs }}ms</span>
          </div>
          <div v-if="i < nodes.length - 1" class="step-arrow">→</div>
        </template>
      </div>
    </div>

    <!-- 主内容区：双卡片布局 -->
    <div class="content" v-if="selectedNode">
      <div class="cards-row">
        <!-- 左侧：流程图卡片 -->
        <div class="diagram-card">
          <div class="card-header">
            <span class="card-title">流程图</span>
            <el-button size="small" type="primary" @click="handleReEdit">重新编辑</el-button>
          </div>
          <div class="diagram-body">
            <div id="bpmn-viewer"></div>
          </div>
        </div>

        <!-- 右侧：节点详情卡片 -->
        <div class="detail-card">
          <div class="card-header">
            <span :class="['status-dot', getNodeStatus(selectedNode)]"></span>
            <span class="card-title">{{ selectedNode.activityName || selectedNode.activityId }}</span>
            <el-tag size="small">{{ typeLabel(selectedNode.activityType) }}</el-tag>
            <span class="card-time" v-if="selectedNode.durationMs || selectedNode.durationMs === 0">{{ selectedNode.durationMs }}ms</span>
          </div>

          <!-- SKIPPED 节点提示 -->
          <div class="card-body" v-if="selectedNode.status === 'SKIPPED'" style="text-align:center;padding:40px">
            <div style="font-size:48px;color:#d1d5db;margin-bottom:12px">—</div>
            <div style="color:#9ca3af;font-size:14px">该节点未到达，流程在此之前已终止</div>
          </div>

          <!-- 服务任务节点详情 -->
          <template v-if="selectedNode.activityType === 'serviceTask' && selectedNode.status !== 'SKIPPED'">
            <!-- 原服务信息 -->
            <div class="card-body" v-if="selectedNode.serviceInfo">
              <div class="section-title">原服务信息</div>
              <div class="chip-grid">
                <div class="chip chip-blue"><span class="chip-label">名称</span><span class="chip-val">{{ selectedNode.serviceInfo.serviceName }}</span></div>
                <div class="chip chip-blue"><span class="chip-label">协议</span><span class="chip-val fw">{{ selectedNode.serviceInfo.protocol }}</span></div>
                <div class="chip chip-blue full"><span class="chip-label">URL</span><span class="chip-val mono">{{ selectedNode.serviceInfo.url }}</span></div>
                <div class="chip chip-green"><span class="chip-label">方法</span><span class="chip-val fw">{{ selectedNode.serviceInfo.method || '—' }}</span></div>
                <div class="chip chip-gray"><span class="chip-label">超时</span><span class="chip-val">{{ selectedNode.serviceInfo.timeoutMs }}ms</span></div>
              </div>
            </div>

            <!-- 节点映射配置 -->
            <div class="card-body" v-if="selectedNode.nodeConfig">
              <div class="section-title">节点映射</div>
              <div class="chip-grid">
                <div class="chip chip-purple">
                  <span class="chip-label">输入映射（覆盖）</span>
                  <pre class="chip-pre" v-if="selectedNode.nodeConfig.inputMapping">{{ fmtJson(selectedNode.nodeConfig.inputMapping) }}</pre>
                  <span class="chip-val dim" v-else>—</span>
                </div>
                <div class="chip chip-purple">
                  <span class="chip-label">输出映射（覆盖）</span>
                  <pre class="chip-pre" v-if="selectedNode.nodeConfig.outputMapping">{{ fmtJson(selectedNode.nodeConfig.outputMapping) }}</pre>
                  <span class="chip-val dim" v-else>—</span>
                </div>
              </div>
            </div>

            <!-- 映射结果 -->
            <div class="card-body" v-if="selectedNode.nodeConfig && (selectedNode.nodeConfig.resolvedInput || selectedNode.nodeConfig.resolvedOutput)">
              <div class="section-title">映射结果</div>
              <div class="map-block" v-if="selectedNode.nodeConfig.resolvedInput && Object.keys(selectedNode.nodeConfig.resolvedInput).length">
                <div class="map-subtitle">输入映射</div>
                <div class="map-item" v-for="(v,k) in selectedNode.nodeConfig.resolvedInput" :key="k">
                  <span class="m-expr">${{ k }}</span>
                  <span class="m-arrow">→</span>
                  <span class="m-val">{{ fmtResolved(v) }}</span>
                </div>
              </div>
              <div class="map-block" v-if="selectedNode.nodeConfig.resolvedOutput && Object.keys(selectedNode.nodeConfig.resolvedOutput).length" style="margin-top:8px">
                <div class="map-subtitle">输出映射</div>
                <div class="map-item" v-for="(item, src) in selectedNode.nodeConfig.resolvedOutput" :key="src">
                  <span class="m-expr">{{ src }}</span>
                  <span class="m-arrow">→</span>
                  <span class="m-label2">{{ item.var }}</span>
                  <span class="m-arrow">=</span>
                  <span class="m-val">{{ fmtResolved(item.val) }}</span>
                </div>
              </div>
            </div>

            <!-- 执行结果 -->
            <div class="card-body" v-if="selectedNode.variables && Object.keys(selectedNode.variables).length">
              <div class="section-title">执行结果</div>
              <div class="chip-grid">
                <div :class="['chip', selectedNode.variables._success ? 'chip-green' : 'chip-red']" v-if="selectedNode.variables._success !== undefined">
                  <span class="chip-label">执行状态</span>
                  <span class="chip-val fw">{{ selectedNode.variables._success ? '✓ 成功' : '✗ 失败' }}</span>
                </div>
                <div class="chip chip-green" v-if="selectedNode.variables._statusCode">
                  <span class="chip-label">HTTP 状态码</span>
                  <span class="chip-val fw">{{ selectedNode.variables._statusCode }}</span>
                </div>
                <div class="chip chip-gray"><span class="chip-label">开始时间</span><span class="chip-val mono">{{ fmt(selectedNode.startTime) }}</span></div>
                <div class="chip chip-gray"><span class="chip-label">结束时间</span><span class="chip-val mono">{{ fmt(selectedNode.endTime) }}</span></div>
                <div class="chip chip-gray" v-if="selectedNode.variables._durationMs">
                  <span class="chip-label">接口耗时</span><span class="chip-val">{{ selectedNode.variables._durationMs }}ms</span>
                </div>
                <div class="chip chip-red full" v-if="selectedNode.variables._errorMessage">
                  <span class="chip-label">错误信息</span>
                  <span class="chip-val">{{ selectedNode.variables._errorMessage }}</span>
                </div>
              </div>
              
              <!-- 响应数据 -->
              <div class="resp-section" v-if="selectedNode.variables._rawResponse">
                <div class="resp-label">响应数据</div>
                <pre class="resp-pre">{{ fmtJson(selectedNode.variables._rawResponse) }}</pre>
              </div>
            </div>
          </template>

          <!-- 非服务任务节点 -->
          <template v-else-if="selectedNode.activityType !== 'serviceTask'">
            <div class="card-body">
              <div class="chip-grid">
                <div class="chip chip-gray"><span class="chip-label">开始时间</span><span class="chip-val mono">{{ fmt(selectedNode.startTime) }}</span></div>
                <div class="chip chip-gray"><span class="chip-label">结束时间</span><span class="chip-val mono">{{ fmt(selectedNode.endTime) }}</span></div>
                <div class="chip chip-gray"><span class="chip-label">耗时</span><span class="chip-val">{{ selectedNode.durationMs }}ms</span></div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import BpmnViewer from 'bpmn-js/lib/Viewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import { getFlowExecutionResult } from '@/api/admin'

const route = useRoute()
const router = useRouter()
const instanceId = route.params.instanceId

const detail = ref(null)
const nodes = ref([])
const selectedIdx = ref(0)

let viewer = null

// 计算属性：当前选中的节点
const selectedNode = computed(() => nodes.value[selectedIdx.value] || null)

// 辅助函数
const fmt = v => { if(!v) return '-'; const s = String(v).replace('T',' '); return s.length > 16 ? s.substring(0, 16) : s }
const fmtJson = v => { try { return JSON.stringify(typeof v === 'string' ? JSON.parse(v) : v, null, 2) } catch { return String(v) } }
const fmtResolved = v => { if(v === null || v === undefined) return '—'; if(typeof v === 'object') return JSON.stringify(v); return String(v) }

const typeLabel = t => ({ startEvent:'开始', endEvent:'结束', serviceTask:'服务调用', userTask:'人工任务', exclusiveGateway:'网关' }[t] || t)

const getNodeStatus = n => {
  if (n.status === 'SKIPPED') return 'skipped'
  if (!n.variables || n.variables._success === undefined) return ''
  return n.variables._success ? 'success' : 'failed'
}

const stepIcon = n => {
  if (n.activityType === 'startEvent') return '▶'
  if (n.activityType === 'endEvent') return '⏹'
  if (getNodeStatus(n) === 'success') return '✓'
  if (getNodeStatus(n) === 'failed') return '✗'
  if (getNodeStatus(n) === 'skipped') return '—'
  return '●'
}

// 选择节点
const selectNode = (i) => {
  selectedIdx.value = i
  highlightSelectedNode()
}

// 应用节点颜色标记
const applyNodeColors = () => {
  if (!viewer) return
  const canvas = viewer.get('canvas')
  const registry = viewer.get('elementRegistry')
  const overlays = viewer.get('overlays')

  for (const n of nodes.value) {
    const el = registry.get(n.activityId)
    if (!el) continue

    // 根据类型和状态添加颜色标记
    if (n.activityType === 'serviceTask') {
      const st = getNodeStatus(n)
      if (st === 'success') {
        try { canvas.addMarker(el, 'exec-success') } catch(e) {}
      } else if (st === 'failed') {
        try { canvas.addMarker(el, 'exec-failed') } catch(e) {}
      } else if (st === 'skipped') {
        try { canvas.addMarker(el, 'exec-skipped') } catch(e) {}
      } else {
        try { canvas.addMarker(el, 'exec-pending') } catch(e) {}
      }
    } else {
      try { canvas.addMarker(el, 'exec-done') } catch(e) {}
    }

    // 添加耗时 badge
    if (n.durationMs || n.durationMs === 0) {
      const st = getNodeStatus(n)
      const clr = st === 'success' ? '#059669' : st === 'failed' ? '#dc2626' : st === 'skipped' ? '#9ca3af' : '#6b7280'
      const icon = st === 'success' ? '✓' : st === 'failed' ? '✗' : ''
      try {
        overlays.add(n.activityId, {
          position: { bottom: -4, left: 0 },
          html: `<div style="font-size:10px;background:${clr};color:#fff;padding:1px 6px;border-radius:3px;white-space:nowrap">${icon} ${n.durationMs}ms</div>`
        })
      } catch(e) {}
    }
  }
}

// 高亮选中节点
const highlightSelectedNode = () => {
  if (!viewer || !selectedNode.value) return
  const registry = viewer.get('elementRegistry')
  const canvas = viewer.get('canvas')

  // 清除所有选择标记
  registry.getAll().forEach(el => {
    try { canvas.removeMarker(el, 'exec-selected') } catch(e) {}
  })

  // 高亮当前选中节点
  const el = registry.get(selectedNode.value.activityId)
  if (el) {
    try { canvas.addMarker(el, 'exec-selected') } catch(e) {}
    try { canvas.scrollToElement(el, { top: 80, left: 20 }) } catch(e) {}
  }
}

// 重新编辑流程
const handleReEdit = () => {
  const pId = detail.value?.processDefinitionId || detail.value?.processId
  if (pId) {
    router.push({ path: `/bpmn/designer/${pId}` })
  } else {
    ElMessage.warning('无法获取流程定义ID')
  }
}

// 生命周期：加载实例详情
onMounted(async () => {
  try {
    const res = await getFlowExecutionResult(instanceId)
    const data = res.data || res
    
    if (data) {
      detail.value = data
      nodes.value = data.nodeTraces || data.nodes || []

      // 如果有 BPMN XML，渲染流程图
      if (data.bpmnXml || data.xmlContent) {
        await nextTick()
        viewer = new BpmnViewer({ container: '#bpmn-viewer' })
        try { 
          await viewer.importXML(data.bpmnXml || data.xmlContent) 
        } catch(e) { 
          console.warn('导入BPMN XML失败:', e) 
        }
        try { 
          viewer.get('canvas').zoomToFit({ maxZoom: 1, margin: 10 }) 
        } catch(e) {}
        
        applyNodeColors()
      }

      // 默认选中第一个服务任务节点
      const svcIdx = nodes.value.findIndex(n => n.activityType === 'serviceTask')
      selectNode(svcIdx >= 0 ? svcIdx : 0)
    }
  } catch(e) { 
    console.error('加载实例详情失败:', e)
    ElMessage.error('加载实例详情失败')
  }
})
</script>

<style scoped lang="scss">
.fid-root { 
  display: flex; 
  flex-direction: column; 
  height: 100%; 
  overflow: hidden; 
  background: #f5f6f8; 
}

.top-bar { 
  display: flex; 
  align-items: center; 
  gap: 10px; 
  padding: 8px 20px; 
  background: #fff; 
  border-bottom: 1px solid #e5e7eb; 
  flex-shrink: 0; 
}
.back-btn { 
  font-size: 13px; 
  color: #6366f1; 
  cursor: pointer; 
  font-weight: 500; 
  &:hover { text-decoration: underline; }
}
.flow-name { 
  font-size: 15px; 
  font-weight: 600; 
  color: #111827; 
}
.time-info { 
  font-size: 12px; 
  color: #6b7280; 
  margin-left: auto; 
}

.stepper-wrap { 
  flex-shrink: 0; 
  padding: 14px 24px; 
  background: #fff; 
  border-bottom: 1px solid #e5e7eb; 
  overflow-x: auto; 
}
.stepper { 
  display: flex; 
  align-items: center; 
  justify-content: center; 
  min-width: max-content; 
}
.step { 
  display: flex; 
  flex-direction: column; 
  align-items: center; 
  gap: 4px; 
  padding: 8px 20px; 
  border-radius: 10px; 
  cursor: pointer; 
  transition: all .2s; 
  min-width: 90px;
  
  &:hover { background: #f3f4f6; }
  &.active { 
    background: #eef2ff; 
    box-shadow: 0 0 0 2px #6366f1; 
  }
  &.success .step-icon { color: #059669; background: #ecfdf5; }
  &.failed .step-icon { color: #dc2626; background: #fef2f2; }
  &.skipped .step-icon { color: #d1d5db; background: #f9fafb; }
  &.skipped .step-name { color: #9ca3af; }
}
.step-icon { 
  font-size: 18px; 
  font-weight: 700; 
  width: 32px; 
  height: 32px; 
  border-radius: 50%; 
  display: flex; 
  align-items: center; 
  justify-content: center; 
  background: #f3f4f6; 
  color: #6b7280; 
}
.step-name { 
  font-size: 12px; 
  font-weight: 500; 
  color: #111827; 
}
.step-type { 
  font-size: 10px; 
  color: #9ca3af; 
}
.step-time { 
  font-size: 10px; 
  color: #6b7280; 
}
.step-arrow { 
  font-size: 18px; 
  color: #d1d5db; 
  flex-shrink: 0; 
  margin: 0 -4px; 
}

.content { 
  flex: 1; 
  overflow: hidden; 
  padding: 16px; 
}
.cards-row { 
  display: flex; 
  gap: 16px; 
  height: 100%; 
}

.diagram-card { 
  flex: 1; 
  background: #fff; 
  border-radius: 12px; 
  border: 1px solid #e5e7eb; 
  overflow: hidden; 
  display: flex; 
  flex-direction: column; 
  min-width: 0; 
}
.diagram-body { 
  flex: 1; 
  min-height: 0; 
  overflow: auto; 
}
#bpmn-viewer { 
  width: 100%; 
  height: 100%; 
}

.detail-card { 
  flex: 1; 
  background: #fff; 
  border-radius: 12px; 
  border: 1px solid #e5e7eb; 
  overflow: hidden; 
  display: flex; 
  flex-direction: column; 
  overflow-y: auto; 
}
.card-header { 
  display: flex; 
  align-items: center; 
  gap: 8px; 
  padding: 14px 20px; 
  border-bottom: 1px solid #f0f0f0; 
  flex-shrink: 0; 
}
.status-dot { 
  width: 10px; 
  height: 10px; 
  border-radius: 50%; 
  flex-shrink: 0;
  &.success { background: #059669; }
  &.failed { background: #dc2626; }
}
.card-title { 
  font-size: 14px; 
  font-weight: 600; 
  color: #111827; 
  flex: 1; 
}
.card-time { 
  font-size: 13px; 
  color: #6b7280; 
}

.card-body { 
  padding: 14px 18px; 
  border-bottom: 1px solid #f0f0f0; 
  &:last-child { border-bottom: none; } 
}
.section-title { 
  font-size: 10px; 
  font-weight: 600; 
  color: #9ca3af; 
  text-transform: uppercase; 
  letter-spacing: 0.5px; 
  margin-bottom: 8px; 
}

.chip-grid { 
  display: grid; 
  grid-template-columns: 1fr 1fr; 
  gap: 6px; 
}
.chip { 
  display: flex; 
  flex-direction: column; 
  gap: 2px; 
  padding: 8px 10px; 
  border-radius: 8px;
  &.full { grid-column: 1 / -1; }
  &.chip-blue { background: #eff6ff; }
  &.chip-green { background: #ecfdf5; }
  &.chip-gray { background: #f9fafb; }
  &.chip-red { background: #fef2f2; }
  &.chip-purple { background: #f5f3ff; }
}
.chip-label { 
  font-size: 10px; 
  color: #9ca3af; 
  text-transform: uppercase; 
  letter-spacing: 0.3px; 
}
.chip-val { 
  font-size: 13px; 
  color: #111827; 
  font-weight: 500;
  &.fw { font-weight: 600; }
  &.dim { color: #9ca3af; font-weight: 400; }
  &.mono { font-family: monospace; font-size: 12px; word-break: break-all; }
}
.chip-pre { 
  margin: 2px 0 0 0; 
  padding: 6px 8px; 
  background: rgba(255,255,255,.6); 
  border-radius: 4px; 
  font-size: 11px; 
  line-height: 1.5; 
  max-height: 100px; 
  overflow: auto; 
  color: #374151; 
}

.resp-section { margin-top: 12px; }
.resp-label { 
  font-size: 10px; 
  color: #9ca3af; 
  text-transform: uppercase; 
  letter-spacing: 0.3px; 
  margin-bottom: 4px; 
}
.resp-pre { 
  margin: 0; 
  padding: 10px 12px; 
  background: #f9fafb; 
  border-radius: 8px; 
  font-size: 12px; 
  line-height: 1.6; 
  max-height: 280px; 
  overflow: auto; 
  color: #374151; 
  border: 1px solid #f0f0f0; 
}

.map-block { 
  padding: 8px 10px; 
  background: #fafbfc; 
  border-radius: 6px; 
}
.map-subtitle { 
  font-size: 10px; 
  font-weight: 600; 
  color: #9ca3af; 
  text-transform: uppercase; 
  margin-bottom: 4px; 
}
.map-item { 
  display: flex; 
  align-items: center; 
  gap: 6px; 
  padding: 3px 0; 
  font-size: 12px; 
}
.m-expr { 
  color: #6366f1; 
  background: rgba(99,102,241,.08); 
  padding: 1px 5px; 
  border-radius: 3px; 
  font-family: monospace; 
  font-size: 11px; 
}
.m-arrow { color: #d1d5db; }
.m-label2 { 
  color: #6366f1; 
  font-weight: 500; 
  font-family: monospace; 
  font-size: 11px; 
}
.m-val { 
  color: #111827; 
  font-weight: 500; 
  font-family: monospace; 
  font-size: 12px; 
  word-break: break-all; 
}

:deep(.djs-element.selected .djs-outline) { visibility: hidden; }

:deep(.exec-success .djs-visual > :first-child) { stroke: #059669 !important; fill: #ecfdf5 !important; }
:deep(.exec-failed .djs-visual > :first-child) { stroke: #dc2626 !important; fill: #fef2f2 !important; }
:deep(.exec-skipped .djs-visual > :first-child) { stroke: #d1d5db !important; fill: #f9fafb !important; stroke-dasharray: 4 2; }
:deep(.exec-selected .djs-visual > :first-child) { stroke: #6366f1 !important; stroke-width: 3px !important; }
</style>
