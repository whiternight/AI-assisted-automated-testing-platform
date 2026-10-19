<template>
  <div class="flow-designer">
    <!-- 加载中提示（后端响应超过3秒） -->
    <transition name="banner-slide">
      <div v-if="slowLoadingTip" class="slow-loading-tip">
        后端响应较慢，正在加载中...
      </div>
    </transition>

    <!-- 顶部工具栏 -->
    <div class="designer-toolbar">
      <div class="toolbar-left">
        <el-button :icon="ArrowLeft" @click="goBack" text>返回</el-button>
        <el-divider direction="vertical" />
        <span class="toolbar-title">流程编排</span>
      </div>
      <div class="toolbar-center">
        <el-select v-model="flowAppId" placeholder="请选择本流程所属应用" filterable clearable style="width: 200px; margin-left: 8px" size="small">
          <el-option v-for="app in filteredApps" :key="app.id" :label="app.name" :value="String(app.id)" />
        </el-select>
        <el-input v-model="flowName" placeholder="流程名称" style="width: 200px" size="small" />
        <el-select v-model="flowOwnerId" placeholder="负责人" filterable clearable style="width: 140px; margin-left: 8px" size="small">
          <el-option v-for="user in ownerList" :key="user.id" :label="user.realName || user.username" :value="user.id" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button size="small" type="info" @click="handleSaveDraft" :loading="savingDraft">保存草稿</el-button>
        <el-button size="small" type="primary" :icon="Document" @click="handleSaveOrUpdate" :loading="saving">保存设计</el-button>
        <el-button size="small" type="warning" @click="handleExecute" :disabled="!flowId || currentFlowStatus !== 'DEPLOYED'">执行</el-button>
        <el-button size="small" @click="openHistoryModal" title="历史记录">历史记录</el-button>

        <el-divider direction="vertical" style="margin: 0 8px" />
        
        <el-button-group>
          <el-button size="small" @click="handleUndo" title="撤销"><el-icon><RefreshLeft /></el-icon></el-button>
          <el-button size="small" @click="handleRedo" title="重做"><el-icon><RefreshRight /></el-icon></el-button>
        </el-button-group>
        <el-button size="small" @click="handleZoomIn" title="放大"><el-icon><ZoomIn /></el-icon></el-button>
        <el-button size="small" @click="handleZoomOut" title="缩小"><el-icon><ZoomOut /></el-icon></el-button>
        <el-button size="small" @click="handleFitView" title="适应画布"><el-icon><FullScreen /></el-icon></el-button>
        <el-button size="small" @click="handleExportXml" title="导出XML"><el-icon><Download /></el-icon></el-button>
      </div>
    </div>

    <!-- 主体两栏布局 -->
    <div class="designer-body">
      <!-- 中间画布区域 -->
      <div class="canvas-container">
        <BpmnCanvas
          ref="bpmnCanvasRef"
          :initialXml="initialXml"
          @element-click="handleElementClick"
          @shape-added="handleShapeAdded"
          @import-done="handleImportDone"
          @dragover.prevent="onDragOver"
          @drop="onDrop"
        />
      </div>

      <!-- 右侧面板（节点配置 + 流程历史） -->
      <aside class="right-panel">
        <!-- 节点配置区域 -->
        <div class="config-section">
          <div class="panel-title" v-if="selectedElement">
            节点配置
            <el-button text size="small" @click="selectedElement = null"><el-icon><Close /></el-icon></el-button>
          </div>
          
          <div v-if="!selectedElement" class="tab-placeholder">
            点击画布上的任务节点<br/>查看和编辑配置
          </div>
          
          <template v-else>
            <el-form label-position="top" size="small" class="config-form">
              <el-form-item label="节点类型">
                <el-tag size="small">{{ getNodeTypeLabel(selectedElement.type) }}</el-tag>
              </el-form-item>
              
              <!-- 服务任务专属属性 -->
              <template v-if="isServiceTask(selectedElement)">
                <!-- 绑定原服务 -->
                <div class="bind-group">
                  <div class="bind-label">绑定原服务</div>
                  
                  <!-- 已绑定状态 -->
                  <div v-if="selectedNodeServiceId" class="bound-service">
                    <el-tag type="success" size="small">{{ selectedNodeServiceName }}</el-tag>
                    <el-button size="small" text type="danger" @click="unbindCurrentNode">取消绑定</el-button>
                  </div>
                  
                  <!-- 未绑定状态：两步选择（应用 → 服务）-->
                  <div v-else class="bind-steps">
                    <!-- 步骤1：选择应用 -->
                    <div v-if="!nodeBindAppId" class="bind-step">
                      <div class="step-hint">第 1 步：选择所属应用</div>
                      <el-select
                        v-model="nodeBindAppId"
                        placeholder="请选择应用"
                        size="small"
                        clearable
                        filterable
                        @change="onNodeAppChange"
                        style="width: 100%"
                      >
                        <el-option 
                          v-for="app in appList" 
                          :key="app.id" 
                          :label="app.name" 
                          :value="app.id" 
                        />
                      </el-select>
                    </div>
                    
                    <!-- 步骤2：选择服务 -->
                    <div v-else class="bind-step">
                      <div class="step-hint">
                        第 2 步：选择原服务
                        <el-button text size="small" type="info" @click="nodeBindAppId = null" style="margin-left: 8px">← 重选应用</el-button>
                      </div>
                      <el-select
                        v-model="quickBindServiceId"
                        placeholder="搜索并选择原服务"
                        size="small"
                        clearable
                        filterable
                        :disabled="nodeServiceList.length === 0"
                        @change="onQuickBind"
                        style="width: 100%; margin-top: 4px"
                      >
                        <el-option 
                          v-for="s in nodeServiceList" 
                          :key="s.id" 
                          :label="s.name" 
                          :value="s.id"
                        >
                          <div style="display: flex; justify-content: space-between; align-items: center">
                            <span>{{ s.name }}</span>
                            <span style="color: #999; font-size: 11px; margin-left: 8px">{{ s.address || '' }}</span>
                          </div>
                        </el-option>
                      </el-select>
                      <div v-if="nodeServiceList.length === 0 && !serviceLoading" class="no-binding-text" style="font-size: 11px; margin-top: 4px">
                        该应用下暂无原服务
                      </div>
                      <div v-if="serviceLoading" class="no-binding-text" style="font-size: 11px; margin-top: 4px">
                        加载中...
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 只读模式提示横幅（从测试集"详情"进入时展示） -->
                <div v-if="readonlyView" class="readonly-banner">
                  <el-icon style="margin-right:4px;vertical-align:-2px"><Warning /></el-icon>
                  只读模式：来源于「测试集」，输入映射、输出映射与断言规则不允许修改
                </div>

                <!-- 输入/输出映射 -->
                <template v-if="selectedNodeServiceId">
                  <div class="bind-group">
                    <div class="bind-label">
                      输入映射
                      <span class="mapping-hint">Input Mapping</span>
                      <el-tag v-if="readonlyView" type="info" size="small" effect="plain" style="margin-left:6px">只读</el-tag>
                    </div>
                    <div class="mapping-tip">用 <code>${变量名}</code> 引用上下文，三层叠加：服务默认 → 节点覆盖</div>
                    <el-input
                      v-model="editingInputMapping"
                      type="textarea"
                      :rows="5"
                      size="small"
                      class="mapping-textarea"
                      :disabled="readonlyView"
                      placeholder='{"userId":{"type":"dynamic","jsonPath":"$.userId"},"name":{"type":"static","value":"demo"}}'
                      @change="onInputMappingChange"
                    />
                  </div>

                  <div class="bind-group">
                    <div class="bind-label">
                      输出映射
                      <span class="mapping-hint">Output Mapping</span>
                      <el-tag v-if="readonlyView" type="info" size="small" effect="plain" style="margin-left:6px">只读</el-tag>
                    </div>
                    <div class="mapping-tip">用 <code>$.json.path</code> 从响应中提取字段到流程变量</div>
                    <el-input
                      v-model="editingOutputMapping"
                      type="textarea"
                      :rows="4"
                      size="small"
                      class="mapping-textarea"
                      :disabled="readonlyView"
                      placeholder='{"userId":"$.data.userId","userName":"$.data.name"}'
                      @change="onOutputMappingChange"
                    />
                  </div>

                  <!-- 断言规则 -->
                  <div class="bind-group" v-if="selectedNodeServiceId">
                    <div class="bind-label">
                      断言规则
                      <el-tag v-if="readonlyView" type="info" size="small" effect="plain" style="margin-left:6px">只读</el-tag>
                    </div>
                    <div class="assert-section">
                      <div class="mapping-tip">节点断言</div>
                      <div v-if="!editingAssertions.length" class="no-binding-text" style="font-size:11px">暂无断言</div>
                      <div v-for="(r,i) in editingAssertions" :key="i" class="assert-row">
                        <el-input v-model="r.field" size="small" placeholder="字段路径，如 data.id 或 statusCode" style="width:140px" :disabled="readonlyView" @change="saveNodeAssertions"/>
                        <el-select v-model="r.operator" size="small" style="width:85px" :disabled="readonlyView" @change="saveNodeAssertions">
                          <el-option label="等于" value="=="/>
                          <el-option label="≠" value="!="/>
                          <el-option label=">" value=">"/>
                          <el-option label="<" value="<"/>
                          <el-option label="≥" value=">="/>
                          <el-option label="≤" value="<="/>
                          <el-option label="包含" value="contains"/>
                        </el-select>
                        <el-input v-model="r.expectedValue" size="small" placeholder="期望值" style="width:90px" :disabled="readonlyView" @change="saveNodeAssertions"/>
                        <el-button v-if="!readonlyView" size="small" text type="danger" @click="removeNodeAssertion(i)">×</el-button>
                      </div>
                      <el-button v-if="!readonlyView" size="small" text @click="addNodeAssertion" style="font-size:11px">+ 添加</el-button>
                    </div>
                  </div>
                </template>
              </template>

              <!-- 排他网关属性 -->
              <template v-if="isExclusiveGateway(selectedElement)">
                <el-form-item label="网关说明">
                  <el-input v-model="selectedNodeProps.description" type="textarea" :rows="2" placeholder="条件分支说明" />
                </el-form-item>
              </template>
            </el-form>
          </template>
        </div>
      </aside>
    </div>

    <!-- 执行弹窗 -->
    <el-dialog v-model="executeDialogVisible" title="执行流程" width="500px" class="execute-dialog" destroy-on-close>
      <el-form :model="executeForm" label-width="100px">
        <el-form-item label="流程名称">
          <el-input :value="flowName" disabled />
        </el-form-item>
        <el-form-item label="执行环境">
          <el-select v-model="executeForm.env" style="width: 100%">
            <el-option label="默认环境" value="default" />
            <el-option label="测试环境" value="test" />
            <el-option label="生产环境" value="production" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行模式">
          <el-radio-group v-model="executeForm.executeMode">
            <el-radio :value="1">同步</el-radio>
            <el-radio :value="2">异步</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="超时时间(ms)">
          <el-input-number v-model="executeForm.timeout" :min="5000" :max="120000" :step="5000" />
        </el-form-item>
        <el-form-item label="入参(JSON)">
          <el-input v-model="executeForm.inputParamsStr" type="textarea" :rows="4" placeholder='{"key": "value"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="executeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitExecute" :loading="executeLoading">执行</el-button>
      </template>
    </el-dialog>

    <!-- 执行结果弹窗 -->
    <el-dialog v-model="resultDialogVisible" title="执行结果" width="700px" class="result-dialog" destroy-on-close>
      <div v-loading="resultLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="执行编号">{{ executionResult.taskNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getExecStatusType(executionResult.status)">{{ getExecStatusText(executionResult.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(executionResult.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatTime(executionResult.endTime) }}</el-descriptions-item>
          <el-descriptions-item label="总耗时">{{ executionResult.totalDuration || '-' }}ms</el-descriptions-item>
        </el-descriptions>
        <div v-if="executionResult.nodeTraces && executionResult.nodeTraces.length" style="margin-top: 16px">
          <h4 style="margin-bottom: 8px">节点执行轨迹</h4>
          <el-table :data="executionResult.nodeTraces" size="small" border>
            <el-table-column prop="activityName" label="节点名称" />
            <el-table-column prop="activityType" label="类型" width="120" />
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="{
                  COMPLETED: 'success',
                  FAILED: 'danger',
                  ACTIVE: 'primary',
                  NOT_EXECUTED: 'info',
                  SKIPPED: 'info'
                }[row.status] || 'warning'">{{ row.status === 'NOT_EXECUTED' ? '未执行' : row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="duration" label="耗时(ms)" width="100" />
          </el-table>
        </div>
        <div v-if="executionResult.errorDetail" style="margin-top: 12px">
          <el-alert type="error" :closable="false">
            <template #title>错误详情</template>
            <pre style="white-space: pre-wrap; font-size: 12px">{{ typeof executionResult.errorDetail === 'string' ? executionResult.errorDetail : JSON.stringify(executionResult.errorDetail, null, 2) }}</pre>
          </el-alert>
        </div>
        <div v-if="executionResult.errorMsg" style="margin-top: 12px">
          <el-alert type="error" :closable="false">
            <template #title>错误信息</template>
            <pre style="white-space: pre-wrap; font-size: 12px">{{ executionResult.errorMsg }}</pre>
          </el-alert>
        </div>
      </div>
      <template #footer>
        <el-button @click="resultDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 历史记录弹窗（可拖拽，点击外部关闭） -->
    <RecordModal v-model:visible="historyModalVisible" title="历史记录" :width="780" :initX="100" :initY="90">
      <div v-loading="historyLoading">
        <!-- 多条件组合搜索 -->
        <div class="record-search-bar">
          <el-input v-model="historySearch.name" placeholder="流程名称" size="small" style="width: 160px" clearable />
          <el-select v-model="historySearch.status" placeholder="状态" size="small" style="width: 120px" clearable>
            <el-option label="成功" value="1" />
            <el-option label="失败" value="2" />
            <el-option label="中断" value="3" />
          </el-select>
          <el-date-picker v-model="historySearch.startTime" type="datetime" placeholder="开始时间" size="small" style="width: 180px" value-format="YYYY-MM-DD HH:mm:ss" />
          <el-date-picker v-model="historySearch.endTime" type="datetime" placeholder="结束时间" size="small" style="width: 180px" value-format="YYYY-MM-DD HH:mm:ss" />
          <el-button size="small" type="primary" @click="loadHistoryList">搜索</el-button>
        </div>
        <!-- 历史记录列表（默认按时间倒序） -->
        <el-table :data="historyList" size="small" border style="margin-top: 12px">
          <el-table-column prop="flowName" label="流程名称" min-width="140" />
          <el-table-column prop="createTime" label="执行时间" width="170">
            <template #default="{ row }">{{ row.createTime || row.startTime || '-' }}</template>
          </el-table-column>
          <el-table-column label="总耗时(ms)" width="110">
            <template #default="{ row }">{{ row.duration != null ? row.duration : (row.totalDuration != null ? row.totalDuration : '-') }}</template>
          </el-table-column>
          <el-table-column prop="status" label="执行状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="historyStatusType(row.status)">{{ historyStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="executor" label="执行人" width="100">
            <template #default="{ row }">{{ row.executor || row.creatorName || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="viewHistoryDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!historyList.length && !historyLoading" class="record-empty">暂无历史记录</div>
      </div>
    </RecordModal>

    <!-- 历史记录详情弹窗（点击历史列表"详情"按钮，展示该次执行的执行结果） -->
    <RecordModal v-model:visible="historyDetailModalVisible" title="执行结果详情" :width="820" :initX="140" :initY="120">
      <div v-loading="historyDetailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="流程实例ID">{{ historyDetailResult.processInstanceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getExecStatusType(historyDetailResult.status)">{{ getExecStatusText(historyDetailResult.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(historyDetailResult.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatTime(historyDetailResult.endTime) }}</el-descriptions-item>
          <el-descriptions-item label="总耗时">{{ historyDetailResult.totalDuration || '-' }}ms</el-descriptions-item>
        </el-descriptions>
        <div v-if="historyDetailResult.nodeTraces && historyDetailResult.nodeTraces.length > 0" style="margin-top: 16px">
          <h4 style="margin-bottom: 8px">节点执行轨迹</h4>
          <el-table :data="historyDetailResult.nodeTraces" size="small" border>
            <el-table-column prop="activityName" label="节点名称" />
            <el-table-column prop="activityType" label="类型" width="120" />
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="{
                  COMPLETED: 'success',
                  FAILED: 'danger',
                  ACTIVE: 'primary',
                  NOT_EXECUTED: 'info',
                  SKIPPED: 'info'
                }[row.status] || 'warning'">{{ row.status === 'NOT_EXECUTED' ? '未执行' : row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="duration" label="耗时(ms)" width="100" />
          </el-table>
        </div>
        <div v-if="historyDetailResult.errorDetail" style="margin-top: 12px">
          <el-alert type="error" :closable="false">
            <template #title>错误详情</template>
            <pre style="white-space: pre-wrap; font-size: 12px">{{ typeof historyDetailResult.errorDetail === 'string' ? historyDetailResult.errorDetail : JSON.stringify(historyDetailResult.errorDetail, null, 2) }}</pre>
          </el-alert>
        </div>
        <div v-if="historyDetailResult.errorMsg" style="margin-top: 12px">
          <el-alert type="error" :closable="false">
            <template #title>错误信息</template>
            <pre style="white-space: pre-wrap; font-size: 12px">{{ historyDetailResult.errorMsg }}</pre>
          </el-alert>
        </div>
      </div>
    </RecordModal>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch, markRaw, toRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, RefreshLeft, RefreshRight, ZoomIn, ZoomOut, FullScreen,
  Upload, Download, VideoPlay, Close, Setting, Edit, Delete, Document, Warning
} from '@element-plus/icons-vue'
import BpmnCanvas from '@/components/bpmn/BpmnCanvas.vue'
import {
  deployFlowDefinition,
  saveFlowDefinition,
  saveDraft,
  updateFlowDefinition,
  updateFlowStatus,
  getFlowDefinitionDetail,
  getFlowDefinitionList,
  deleteFlowDefinition,
  executeFlow,
  getFlowExecutionResult,
  listOriginalServices,
  getOriginalServiceDetail
} from '@/api/admin'
import {
  terminateFlow,
  getFlowExecutionHistory,
  classifyError,
  FRONTEND_ERROR_CODES
} from '@/api/flowExecution'
import RecordModal from '@/components/RecordModal.vue'
import { loadDepartmentTree, loadAllUsers } from '@/utils/cache'

const route = useRoute()
const router = useRouter()

// ==================== 空白 BPMN 模板 ====================
const emptyXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="200" y="200" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

// ==================== 应用上下文（应用选择）====================
// 应用列表：根据当前用户角色智能加载
const appList = ref([])
const filteredApps = computed(() => appList.value)

// 当前用户信息（用于判断是否管理员）
let currentUserInfo = null
let currentUserDeptId = null

// ==================== 原服务列表（支持拖拽）====================
const serviceList = ref([])
const serviceSearchText = ref('')

const filteredServices = computed(() => {
  if (!serviceSearchText.value) return serviceList.value
  const kw = serviceSearchText.value.toLowerCase()
  return serviceList.value.filter(s =>
    s.name.toLowerCase().includes(kw) ||
    (s.code && s.code.toLowerCase().includes(kw))
  )
})

const flatServiceList = computed(() => serviceList.value)

// ==================== 流程状态管理 ====================
const flowId = ref(route.params.id || null)
const flowName = ref('未命名流程')
const flowAppId = ref('')
const currentFlowStatus = ref('DRAFT') // DRAFT | UNDEPLOYED | DEPLOYED
const saving = ref(false)
const deploying = ref(false)
const savingDraft = ref(false)
const initialXml = ref('')  // BPMN 初始 XML，传给 BpmnCanvas
// 只读模式：从测试集"详情"跳转进入，节点映射/断言等不允许修改
const readonlyView = ref(route.query.from === 'testSuite')
const readonlyViewFrom = ref(route.query.from || '')

// ==================== 流程历史列表 ====================
const flowList = ref([])

// ==================== 节点绑定映射 ====================
// { bpmnNodeId: { atomicServiceId, inputMapping, outputMapping, assertionRules } }
const nodeBindings = ref({})

// ==================== 选中节点状态 ====================
const bpmnCanvasRef = ref(null)  // BpmnCanvas 组件引用 ⭐ 新增
const selectedElement = ref(null)
// ==================== 执行状态可视化 ====================
const currentExecutionId = ref(null)           // 当前执行的流程实例ID
const isExecuting = ref(false)                 // 是否正在执行
const executingNodeId = ref(null)              // 当前正在执行的节点ID
const completedNodeIds = ref(new Set())        // 已完成的节点ID集合
const failedNodeIds = ref(new Set())           // 失败的节点ID集合
// 加载中提示（后端响应超过3秒时显示）
const slowLoadingTip = ref(false)
let slowLoadingTimer = null

// ==================== 历史记录 / 执行记录 弹窗 ====================
// 同时最多打开3个记录窗口，超出时提示
const MAX_RECORD_MODALS = 3
// 历史记录弹窗
const historyModalVisible = ref(false)
const historyLoading = ref(false)
const historyList = ref([])
const historySearch = reactive({
  name: '',
  status: '',
  startTime: '',
  endTime: ''
})
// 历史记录详情弹窗（点击历史列表"详情"按钮，展示该次执行的执行结果）
const historyDetailModalVisible = ref(false)
const historyDetailLoading = ref(false)
const historyDetailResult = ref({})
// 当前已打开的记录窗口计数
const openRecordCount = computed(() => {
  return (historyModalVisible.value ? 1 : 0) + (historyDetailModalVisible.value ? 1 : 0)
})
const selectedNodeProps = reactive({
  name: '',
  serviceId: '',
  serviceAppId: '',
  inputMapping: '',
  outputMapping: '',
  description: ''
})
const selectedNodeServiceId = ref(null)
const quickBindServiceId = ref(null)
const nodeBindAppId = ref(null)  // 节点绑定时的临时应用选择
const nodeServiceList = ref([])     // 选中应用下的服务列表
const serviceLoading = ref(false)   // 服务列表加载状态
const editingInputMapping = ref('')
const editingOutputMapping = ref('')
const editingAssertions = ref([])

// ==================== 服务详情和断言 ====================
const currentServiceDetail = ref(null)
const serviceSearchLoading = ref(false)
const filteredServiceList = ref([])

// ==================== 负责人列表 ====================
const ownerList = ref([])
const flowOwnerId = ref('')
let currentUserId = null


// ==================== 执行弹窗 ====================
const executeDialogVisible = ref(false)
const executeLoading = ref(false)
const executeForm = reactive({
  env: 'default',
  executeMode: 1,
  timeout: 30000,
  inputParamsStr: ''
})
const resultDialogVisible = ref(false)
const resultLoading = ref(false)
const executionResult = ref({})
let pollTimer = null

// ==================== 拖拽状态 ====================
let dragService = null

// ==================== 辅助函数 ====================
const isServiceTask = (element) => {
  if (!element) return false
  return element.type === 'bpmn:ServiceTask' || element.type === 'bpmn:serviceTask' ||
    (element.type && element.type.includes('ServiceTask'))
}

const FLOWABLE_DELEGATE_EXPRESSION = '${originalServiceDelegate}'

const normalizeJsonString = (value) => {
  if (value === null || value === undefined || value === '') return ''
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

const getFlowableAttr = (bo, name) => {
  if (!bo) return ''
  try {
    const value = typeof bo.get === 'function' ? bo.get(`flowable:${name}`) : undefined
    if (value !== undefined && value !== null && value !== '') return value
  } catch (e) {
    // bpmn-js may throw before a custom attribute is registered in moddle.
  }
  const attrValue = bo.$attrs?.[`flowable:${name}`]
  if (attrValue !== undefined && attrValue !== null && attrValue !== '') return attrValue
  return bo[name] || ''
}

const setFlowableAttrs = (element, attrs = {}) => {
  const rawElement = toRaw(element)
  if (!rawElement?.businessObject) return

  const props = {}
  const bo = rawElement.businessObject
  Object.entries(attrs).forEach(([name, value]) => {
    const normalized = value === null || value === undefined ? '' : value
    const shouldRemove = normalized === ''
    const flowableName = `flowable:${name}`
    props[flowableName] = shouldRemove ? undefined : normalized
    try {
      bo.set?.(flowableName, shouldRemove ? undefined : normalized)
    } catch (e) {
      bo.$attrs = bo.$attrs || {}
      if (shouldRemove) {
        delete bo.$attrs[flowableName]
      } else {
        bo.$attrs[flowableName] = normalized
      }
    }
    if (shouldRemove && bo.$attrs) {
      delete bo.$attrs[flowableName]
    }
  })

  const modeling = bpmnCanvasRef.value?.getModeler?.()?.get('modeling')
  if (modeling && rawElement.id) {
    modeling.updateProperties(rawElement, props)
  }
}

const validateInputParamsConfig = (inputParamsJson, nodeName = '') => {
  if (!inputParamsJson || !inputParamsJson.trim()) return
  let parsed
  try {
    parsed = JSON.parse(inputParamsJson)
  } catch {
    throw new Error(`节点 "${nodeName}" 的入参配置不是合法 JSON`)
  }
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error(`节点 "${nodeName}" 的入参配置必须是对象`)
  }
  Object.entries(parsed).forEach(([fieldName, config]) => {
    if (!config || Array.isArray(config) || typeof config !== 'object') {
      throw new Error(`节点 "${nodeName}" 的入参字段 "${fieldName}" 必须配置为 {type,value/jsonPath}`)
    }
    const type = String(config.type || 'static').toLowerCase()
    if (type === 'dynamic' && (!config.jsonPath || typeof config.jsonPath !== 'string')) {
      throw new Error(`节点 "${nodeName}" 的动态入参 "${fieldName}" 缺少 jsonPath`)
    }
    if (type === 'static' && !Object.prototype.hasOwnProperty.call(config, 'value')) {
      throw new Error(`节点 "${nodeName}" 的静态入参 "${fieldName}" 缺少 value`)
    }
  })
}

const validateOutputParamsConfig = (outputParamsJson, nodeName = '') => {
  if (!outputParamsJson || !outputParamsJson.trim()) return
  try {
    const parsed = JSON.parse(outputParamsJson)
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      throw new Error('outputParams must be object')
    }
  } catch {
    throw new Error(`节点 "${nodeName}" 的出参配置必须是合法 JSON 对象`)
  }
}

const xmlEscape = (value) => String(value ?? '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')

const isExclusiveGateway = (element) => {
  if (!element) return false
  return element.type === 'bpmn:ExclusiveGateway' || element.type === 'bpmn:exclusiveGateway' ||
    (element.type && element.type.includes('ExclusiveGateway'))
}

const getNodeTypeLabel = (type) => {
  if (!type) return ''
  const typeLower = type.toLowerCase()
  if (typeLower.includes('startevent') || typeLower.includes('start')) return '开始事件'
  if (typeLower.includes('endevent') || typeLower.includes('end')) return '结束事件'
  if (typeLower.includes('servicetask') || typeLower.includes('service')) return '服务任务'
  if (typeLower.includes('exclusivegateway') || typeLower.includes('gateway')) return '排他网关'
  if (typeLower.includes('usertask') || typeLower.includes('user')) return '用户任务'
  return type
}

const fmtTime = v => { 
  if(!v) return '-'; 
  const s = String(v).replace('T',' '); 
  return s.length > 16 ? s.substring(0, 16) : s 
}

const formatMappingJson = (v) => {
  if (!v) return ''
  if (typeof v === 'string') {
    try { return JSON.stringify(JSON.parse(v), null, 2) } catch { return v }
  }
  try { return JSON.stringify(v, null, 2) } catch { return String(v) }
}

// 解析断言规则：binding.assertionRules 可能是 JSON 字符串、数组或 null，统一归一化为数组
const parseAssertionRules = (v) => {
  if (!v) return []
  let arr = []
  if (Array.isArray(v)) {
    arr = JSON.parse(JSON.stringify(v))
  } else if (typeof v === 'string') {
    try {
      const parsed = JSON.parse(v)
      arr = Array.isArray(parsed) ? parsed : []
    } catch { return [] }
  }
  return arr.map(item => {
    const backendType = item.type || item._backendType || ''
    const field = item.field || item.expression || backendType || ''
    return {
      field,
      operator: toUiOperator(item.operator),
      expectedValue: item.expectedValue !== undefined ? String(item.expectedValue) : (item.expected !== undefined ? String(item.expected) : ''),
      _backendType: backendType
    }
  }).filter(item => item.field || item.expectedValue)
}

// 将后端运算符（EQUALS/CONTAINS/GT/LT/GTE/LTE/MATCHES）映射为前端下拉选项
const toUiOperator = (op) => {
  if (!op) return '=='
  const map = {
    'EQUALS': '==',
    '==': '==',
    'NOT_EQUALS': '!=',
    '!=': '!=',
    'GT': '>',
    '>': '>',
    'LT': '<',
    '<': '<',
    'GTE': '>=',
    '>=': '>=',
    'LTE': '<=',
    '<=': '<=',
    'CONTAINS': 'contains',
    'MATCHES': 'contains'
  }
  return map[String(op).toUpperCase()] || '=='
}

// 根据字段路径推断后端断言类型
const inferAssertionType = (field) => {
  const f = String(field || '').trim()
  if (!f) return 'STATUS_CODE'
  if (f.startsWith('$.') || f.startsWith('$[')) return 'JSON_PATH'
  if (f.toUpperCase() === 'STATUSCODE' || f.toUpperCase() === 'STATUS_CODE') return 'STATUS_CODE'
  if (f.toUpperCase() === 'RESPONSETIME' || f.toUpperCase() === 'RESPONSE_TIME') return 'RESPONSE_TIME'
  return 'STATUS_CODE'
}

// 将前端运算符映射回后端可识别的形式
const toBackendOperator = (op) => {
  if (!op) return 'EQUALS'
  const map = {
    '==': 'EQUALS',
    '!=': 'NOT_EQUALS',
    '>': 'GT',
    '<': 'LT',
    '>=': 'GTE',
    '<=': 'LTE',
    'contains': 'CONTAINS'
  }
  return map[op] || 'EQUALS'
}

const buildServiceTaskFlowableAttrs = ({
  serviceId = '',
  serviceName = '',
  serviceAddress = '',
  inputMapping = '',
  outputMapping = '',
  assertions = '',
  httpMethod = 'POST'
} = {}) => {
  const attrs = {
    delegateExpression: FLOWABLE_DELEGATE_EXPRESSION,
    serviceId,
    serviceName,
    serviceAddress,
    inputMapping,
    outputMapping,
    assertions
  }

  if (serviceAddress) {
    attrs.type = 'http'
    attrs.async = true
  } else {
    attrs.type = ''
    attrs.async = ''
  }

  return attrs
}

const getServiceName = (serviceId) => {
  if (!serviceId) return ''
  // 优先从 nodeServiceList（当前应用下的服务列表）查找
  let found = nodeServiceList.value.find(s => s.id === serviceId)
  // 如果找不到，再从全局 serviceList（流程所属应用）查找
  if (!found) {
    found = serviceList.value.find(s => s.id === serviceId)
  }
  return found ? found.name : ''
}

// 当前选中节点绑定服务的显示名称：
// 优先用已加载服务列表中的名称；若服务属于其他应用（列表中查不到），
// 回退到节点绑定中保存的服务名（保存时即服务名），避免显示成 ID。
const selectedNodeServiceName = computed(() => {
  const sid = selectedNodeServiceId.value
  if (!sid) return ''
  const name = getServiceName(sid)
  if (name) return name
  const el = selectedElement.value
  const binding = el ? nodeBindings.value[el.id] : null
  return (binding && binding.name) || String(sid)
})

// ==================== 数据加载方法 ====================

// 加载原服务列表
const loadServices = async (applicationId) => {
  if (!applicationId) { serviceList.value = []; return }
  try {
    const res = await listOriginalServices({ applicationId })
    const data = res.data || res
    serviceList.value = data.records || data || []
  } catch (e) {
    serviceList.value = []
  }
}


// 加载单个服务详情（用于显示断言规则等）
const loadServiceDetail = async (serviceId) => {
  if (!serviceId) return
  try {
    const res = await getOriginalServiceDetail(serviceId)
    currentServiceDetail.value = res.data || res
  } catch (e) {
    console.warn('[FlowDesigner] 加载服务详情失败:', e)
    currentServiceDetail.value = null
  }
}

// 加载流程历史列表
const flowLoading = ref(false)
const loadFlows = async () => {
  flowLoading.value = true
  try {
    const params = { pageNum: 1, pageSize: 20 }
    // 如果选择了应用，则按应用过滤；否则显示全部
    if (flowAppId.value) {
      params.applicationId = Number(flowAppId.value)
    }
    
    const r = await getFlowDefinitionList(params)
    if (r.code === 0 && r.data) {
      flowList.value = r.data.records || []
    }
  } catch (e) {
    flowList.value = []
  } finally {
    flowLoading.value = false
  }
}

// 加载流程详情
const loadFlowDetail = async () => {
  if (!flowId.value) return
  try {
    const res = await getFlowDefinitionDetail(flowId.value)
    const detail = res.data || res
    
    flowName.value = detail.name || '未命名流程'
    flowAppId.value = detail.applicationId ? String(detail.applicationId) : ''
    // 状态映射：0=草稿(DRAFT) 1=未部署(UNDEPLOYED) 2=已发布(DEPLOYED) 3=已停用
    const statusMap = { 0: 'DRAFT', 1: 'UNDEPLOYED', 2: 'DEPLOYED', 3: 'DISABLED' }
    currentFlowStatus.value = statusMap[detail.status] || 'DRAFT'
    
    if (detail.ownerId) flowOwnerId.value = detail.ownerId
    
    const xmlContent = detail.definitionXml || detail.bpmnXml
    if (xmlContent) initialXml.value = xmlContent
    
    // 回填节点绑定（serviceTask 配置）
    // ⚠️ 字段名必须与 handleElementClick / generateNodesArray / getResolvedNodeBindings 中
    //    读取的字段保持一致：atomicServiceId、name、inputMapping、outputMapping、assertionRules(字符串)、applicationId
    if (Array.isArray(detail.nodes) && detail.nodes.length > 0) {
      const bindings = {}
      detail.nodes.forEach(n => {
        if (n.originalServiceId) {
          // BPMN serviceTask 的 element.id 对应后端 nodeKey（保存时存的就是 element.id）
          const nodeKey = n.nodeKey || n.id
          if (!nodeKey) return
          bindings[nodeKey] = {
            atomicServiceId: n.originalServiceId,
            serviceId: n.originalServiceId,
            name: n.nodeName || n.name || '',
            inputMapping: n.inputParams || '',
            outputMapping: n.outputParams || '',
            // assertionRules 统一以 JSON 字符串形式保存（与 saveNodeAssertions / generateNodesArray 一致）
            assertionRules: n.assertions || null,
            applicationId: flowAppId.value || null,
            timeout: n.timeout,
            retryCount: n.retryCount,
            retryInterval: n.retryInterval,
            retryType: n.retryType
          }
        }
      })
      nodeBindings.value = bindings
    }
    
    // 加载服务列表和流程历史
    await Promise.all([
      loadServices(flowAppId.value),
      loadFlows()
    ])
  } catch (e) {
    console.error('[FlowDesigner] 加载流程详情失败:', e)
  }
}

// ==================== 拖拽处理 ====================

const onDragStart = (event, service) => {
  dragService = service
  event.dataTransfer.setData('text/plain', service.id)
  event.dataTransfer.effectAllowed = 'copy'
}

const onDragOver = () => {
  // 由 @dragover.prevent 处理，不需要额外逻辑
}

const onDrop = (event) => {
  event.preventDefault()
  if (!dragService || !bpmnCanvasRef.value) return

  try {
    const canvas = bpmnCanvasRef.value.getModeler()?.get('canvas')
    const canvasContainer = document.querySelector('.bpmn-canvas')
    if (!canvas || !canvasContainer) return
    
    const rect = canvasContainer.getBoundingClientRect()
    const viewbox = canvas.viewbox()

    // 屏幕坐标 → 画布坐标
    const x = (event.clientX - rect.left) / viewbox.scale + viewbox.x - 50
    const y = (event.clientY - rect.top) / viewbox.scale + viewbox.y - 40

    // 获取 bpmn-js 内部服务
    const elementFactory = bpmnCanvasRef.value.getModeler()?.get('elementFactory')
    const modeling = bpmnCanvasRef.value.getModeler()?.get('modeling')
    const elementRegistry = bpmnCanvasRef.value.getModeler()?.get('elementRegistry')
    
    if (!elementFactory || !modeling || !elementRegistry) return

    // 找到根流程元素
    const process = elementRegistry.find(el => el.type === 'bpmn:Process')
    if (!process) {
      ElMessage.warning('找不到根流程元素')
      return
    }

 // 创建 shape（bpmn-js 自动创建 businessObject）
    const shape = elementFactory.createShape({ type: 'bpmn:ServiceTask' })
    shape.businessObject.name = dragService.name

    // 添加到画布（先添加）
    modeling.createShape(shape, { x, y }, process)
    
   setTimeout(() => {
  try {
    const serviceType = (dragService.type || 'http').toLowerCase()
    
    const props = {
      'flowable:type': serviceType,
      'flowable:async': true
    }
    
    // 添加服务类型特定属性
    if (serviceType === 'http') {
      Object.assign(props, {
        'flowable:httpRequestMethod': dragService.httpMethod || 'POST',
        'flowable:httpRequestUrl': dragService.address || dragService.url || ''
      })
    }
    
    modeling.updateProperties(elementRegistry.get(nodeId), props)
    
    console.log(`✅ 节点 [${nodeId}] 属性设置完成:`, props)
    ElMessage.success(`已添加节点: ${dragService.name} (${serviceType})`)
  } catch (error) {
    console.error('❌ 设置属性失败:', error)
    ElMessage.error('节点属性设置失败')
  }
}, 150)

    // 绑定原服务：记录 ID + 复制配置（包含完整的服务信息）
    const nodeId = shape.id
    nodeBindings.value = {
      ...nodeBindings.value,
      [nodeId]: {
        atomicServiceId: dragService.id,
        inputMapping: dragService.defaultInputMapping || null,
        outputMapping: dragService.defaultOutputMapping || null,
        assertionRules: dragService.assertionRules || null,
        name: dragService.name,
        serviceId: dragService.id,
        // ✅ 新增：保存完整的 HTTP/Dubbo 配置信息
        type: dragService.type || 'http',
        address: dragService.address || dragService.url || dragService.serviceAddress || '',
        httpMethod: dragService.httpMethod || 'POST',
        interfaceName: dragService.interfaceName || '',
        methodName: dragService.methodName || '',
        version: dragService.version || '1.0.0'
      }
    }

    console.log('💾 已保存节点绑定:', {
      nodeId,
      serviceName: dragService.name,
      serviceType: dragService.type || 'http',
      serviceAddress: dragService.address || dragService.url || ''
    })

    // 更新画布标签：显示服务名称 + 地址（双行显示）
    if (bpmnCanvasRef.value) {
      setTimeout(() => {
        try {
          bpmnCanvasRef.value.refreshServiceTaskLabel(
            nodeId, 
            dragService.name, 
            dragService.address || ''  // 显示原服务地址
          )
        } catch (e) {
          console.warn('[FlowDesigner] 更新节点标签失败:', e)
        }
      }, 100)  // 延迟确保 bpmn-js 完成渲染
    }

    ElMessage.success(`已添加节点: ${dragService.name}`)
  } catch (e) {
    console.error('拖拽创建节点失败:', e)
    ElMessage.error('创建节点失败')
  } finally {
    dragService = null
  }
}
// ==================== 服务配置函数 ====================

function setupHttpService(shape, service) {
  shape.businessObject.set('flowable:httpUrl', service.address || service.url || '')
  shape.businessObject.set('flowable:httpMethod', service.httpMethod || 'POST')
}

function setupDubboService(shape, service) {
  shape.businessObject.set('flowable:dubboInterface', service.interfaceName || '')
  shape.businessObject.set('flowable:dubboMethod', service.methodName || '')
  shape.businessObject.set('flowable:dubboVersion', service.version || '1.0.0')
}

function setupGrpcService(shape, service) {
  shape.businessObject.set('flowable:grpcUrl', service.address || '')
  shape.businessObject.set('flowable:grpcMethod', service.methodName || '')
}

// ==================== 节点点击事件处理 ====================

const handleElementClick = (event) => {
  if (!event || !event.element) return
  
  const element = event.element
  selectedElement.value = element
  
  if (element.businessObject) {
    selectedNodeProps.value = {
      name: element.businessObject.name || '',
      id: element.businessObject.id,
      type: element.type,
      description: ''
    }
  } else {
    selectedNodeProps.value = { name: '', id: element.id, type: element.type, description: '' }
  }
  
  if (isServiceTask(element)) {
    const binding = nodeBindings.value[element.id]
    
    if (binding && binding.atomicServiceId) {
      selectedNodeServiceId.value = binding.atomicServiceId
      quickBindServiceId.value = null
      editingInputMapping.value = formatMappingJson(binding.inputMapping)
      editingOutputMapping.value = formatMappingJson(binding.outputMapping)
      // assertionRules 统一以 JSON 字符串形式存储，这里需要解析为数组供编辑使用
      editingAssertions.value = parseAssertionRules(binding.assertionRules)
      loadServiceDetail(binding.atomicServiceId)
      
      // ✅ 新增：智能恢复应用选择状态（支持多应用共存）
      if (binding.applicationId && binding.applicationId !== nodeBindAppId.value) {
        console.log('[FlowDesigner] 🔄 恢复应用选择:', binding.applicationId, '用于服务:', binding.atomicServiceId)
        nodeBindAppId.value = binding.applicationId
        // 异步加载该应用的服务列表（不阻塞UI）
        onNodeAppChange(binding.applicationId).then(() => {
          console.log('[FlowDesigner] ✅ 已恢复服务列表, 当前服务:', getServiceName(binding.atomicServiceId))
        })
      }
    } else {
      selectedNodeServiceId.value = null
      quickBindServiceId.value = null
      editingInputMapping.value = ''
      editingOutputMapping.value = ''
      editingAssertions.value = []
      currentServiceDetail.value = null
      // 重置应用选择（让用户重新选择）
      nodeBindAppId.value = null
      nodeServiceList.value = []
    }
  } else {
    selectedNodeServiceId.value = null
    quickBindServiceId.value = null
  }
}

const updateNodeText = () => {
  if (!selectedElement.value || !bpmnCanvasRef.value) return
  
  try {
    const modeling = bpmnCanvasRef.value.getModeler()?.get('modeling')
    if (modeling && selectedNodeProps.value.name !== undefined) {
      modeling.updateProperties(selectedElement.value, { name: selectedNodeProps.value.name })
      
      if (isServiceTask(selectedElement.value)) {
        const binding = nodeBindings.value[selectedElement.value.id]
        if (binding) {
          bpmnCanvasRef.value.refreshServiceTaskLabel(
            selectedElement.value, 
            selectedNodeProps.value.name, 
            ''
          )
        }
      }
    }
  } catch (e) {
    console.warn('[FlowDesigner] 更新节点名称失败:', e)
  }
}

// ==================== 节点绑定操作 ====================

// 节点绑定：选择应用后加载服务列表
const onNodeAppChange = async (appId) => {
  if (!appId) {
    nodeServiceList.value = []
    return
  }
  
  serviceLoading.value = true
  nodeServiceList.value = []
  quickBindServiceId.value = null
try {
    const res = await listOriginalServices({ applicationId: appId })
    const data = res.data || res
    nodeServiceList.value = data.records || data || []
    console.log('[FlowDesigner] 已加载应用', appId, '下的服务:', nodeServiceList.value.length, '个')
  } catch (e) {
    console.error('[FlowDesigner] 加载服务列表失败:', e)
    nodeServiceList.value = []
    ElMessage.error('加载原服务列表失败')
  } finally {
    serviceLoading.value = false
  }
}

const onQuickBind = (serviceId) => {
  if (!selectedElement.value || !serviceId) return
  
  console.log('[FlowDesigner] 开始绑定服务:', serviceId)
  
  // 优先从 nodeServiceList（当前应用下的服务列表）查找
  let service = nodeServiceList.value.find(s => s.id === serviceId)
  // 如果找不到，再从全局 serviceList 查找（兼容性）
  if (!service) {
    service = serviceList.value.find(s => s.id === serviceId)
  }

  
  const inputMapping = service ? (service.defaultInputMapping || null) : null
  const outputMapping = service ? (service.defaultOutputMapping || null) : null
  const serviceName = service?.name || '未命名服务'
  const serviceAddress = service?.address || service?.url || ''
  
  console.log('[FlowDesigner] 服务信息:', { serviceName, serviceAddress, hasService: !!service })
  
  nodeBindings.value = {
    ...nodeBindings.value,
    [selectedElement.value.id]: {
      atomicServiceId: serviceId,
      inputMapping,
      outputMapping,
      name: serviceName,
      serviceId,
      applicationId: nodeBindAppId.value,  // ✅ 新增：记录所属应用ID
      address: serviceAddress,        // ✅ 新增
      httpMethod: service?.httpMethod || 'POST'          // ✅ 新增
    }
  }
  
  selectedNodeServiceId.value = serviceId
  editingInputMapping.value = formatMappingJson(inputMapping)
  editingOutputMapping.value = formatMappingJson(outputMapping)
  quickBindServiceId.value = null

  setFlowableAttrs(selectedElement.value, buildServiceTaskFlowableAttrs({
    serviceId,
    serviceName,
    serviceAddress,
    inputMapping: normalizeJsonString(inputMapping),
    outputMapping: normalizeJsonString(outputMapping),
    assertions: normalizeJsonString(service?.assertionRules),
    httpMethod: service?.httpMethod || 'POST'
  }))

  // ✅ 强制更新画布上的节点名称和服务地址（双行显示）
  if (bpmnCanvasRef.value && selectedElement.value) {
    try {
      // ✅ 关键修复：使用 toRaw() 获取原始元素，避免 Vue Proxy 与 bpmn-js 冲突
      const rawElement = toRaw(selectedElement.value)
      const elementId = rawElement.id
      
      console.log('[FlowDesigner] 🎨 开始更新画布标签, elementId:', elementId)
      
      const modeling = bpmnCanvasRef.value.getModeler()?.get('modeling')
      if (modeling) {
        // 使用原始元素更新属性
        modeling.updateProperties(rawElement, { name: serviceName })
        console.log('[FlowDesigner] ✅ modeling.updateProperties 成功')
      }
      
      setTimeout(() => {
        try {
          console.log('[FlowDesigner] 🔄 调用 refreshServiceTaskLabel:', { elementId, serviceName, serviceAddress })
          
          // 直接传递 elementId（字符串），让 BpmnCanvas 内部通过 elementRegistry 获取原始元素
          const result = bpmnCanvasRef.value.refreshServiceTaskLabel(
            elementId,  // ← 改为传递 ID 字符串而不是 Vue Proxy 对象
            serviceName, 
            serviceAddress
          )
          
          console.log('[FlowDesigner] ✅ refreshServiceTaskLabel 调用完成')
        } catch (e) {
          console.error('[FlowDesigner] ❌ refreshServiceTaskLabel 调用失败:', e)
          console.error('[FlowDesigner] 错误详情:', e.message)
          console.error('[FlowDesigner] 错误堆栈:', e.stack)
        }
      }, 150)
    } catch (e) {
      console.error('[FlowDesigner] ❌ 绑定服务后更新画布失败:', e)
      console.error('[FlowDesigner] 失败原因:', e.message)
    }
  } else {
    console.warn('[FlowDesigner] ⚠️ 无法更新画布:', { 
      hasBpmnCanvasRef: !!bpmnCanvasRef.value, 
      hasSelectedElement: !!selectedElement.value 
    })
  }
}
const onInputMappingChange = () => {
  if (!selectedElement.value) return
  const binding = nodeBindings.value[selectedElement.value.id]
  if (!binding) return
  const val = editingInputMapping.value.trim()
  if (!val) {
    binding.inputMapping = null
    setFlowableAttrs(selectedElement.value, { inputMapping: '' })
    return
  }
  try {
    validateInputParamsConfig(val, binding.name || selectedElement.value.businessObject?.name || selectedElement.value.id)
    binding.inputMapping = val
    setFlowableAttrs(selectedElement.value, { inputMapping: val })
  } catch (e) {
    ElMessage.error(e.message || '入参配置格式错误')
  }
}
// 更新画布节点高亮状态
// 同时构建 nodeErrors 映射，传给 BpmnCanvas 在失败节点上显示错误气泡
// 支持传入整个执行结果对象或单独的nodeTraces和activeConnections
const updateExecutionHighlights = (resultOrTraces, activeConnections) => {
  let nodeTraces, activeFlows;

  // 兼容两种调用方式：传入完整result对象 或 单独传入traces和connections
  if (Array.isArray(resultOrTraces)) {
    nodeTraces = resultOrTraces;
    activeFlows = activeConnections || [];
  } else if (resultOrTraces && typeof resultOrTraces === 'object') {
    nodeTraces = resultOrTraces.nodeTraces || [];
    activeFlows = resultOrTraces.activeSequenceFlows || [];
  } else {
    nodeTraces = [];
    activeFlows = [];
  }

  if (!bpmnCanvasRef.value) {
    console.warn('[FlowDesigner] updateExecutionHighlights: bpmnCanvasRef 未初始化')
    return
  }

  console.log('[FlowDesigner] 🎨 开始更新执行高亮, nodeTraces 数量:', nodeTraces.length, '连线数量:', activeFlows.length)

  const newCompleted = new Set()
  const newFailed = new Set()
  let newExecuting = null
  const newNodeErrors = {} // { nodeId: errorMessage } 传给 BpmnCanvas 显示节点气泡

  // 解析节点轨迹数据
  nodeTraces.forEach(trace => {
    const nodeId = trace.activityId || trace.nodeId || trace.id
    if (!nodeId) return

    const status = (trace.status || trace.executionStatus || '').toUpperCase()

    switch (status) {
      case 'COMPLETED':
      case 'SUCCESS':
      case 'FINISHED':
        newCompleted.add(nodeId)
        break
      case 'FAILED':
      case 'ERROR':
      case 'EXCEPTION':
        newFailed.add(nodeId)
        // 记录失败节点的错误信息（供节点气泡显示）
        newNodeErrors[nodeId] = trace.errorMessage || trace.errorMsg || trace.message || '节点执行失败'
        break
      case 'RUNNING':
      case 'EXECUTING':
      case 'IN_PROGRESS':
      case 'ACTIVE':
        newExecuting = nodeId
        break
      case 'SKIPPED':
      case 'PENDING':
      case 'NOT_EXECUTED':
        // 跳过/等待/未执行的节点不计入任何高亮
        break
      default:
        console.log('[FlowDesigner] ⚠️ 未知状态:', status, '节点:', nodeId)
    }
  })

  // 更新响应式状态
  completedNodeIds.value = newCompleted
  failedNodeIds.value = newFailed
  executingNodeId.value = newExecuting

  // 调用 BpmnCanvas 的方法来应用高亮（含连线轨迹与状态图标）
  try {
    bpmnCanvasRef.value.highlightNodes({
      completed: Array.from(newCompleted),
      executing: newExecuting,
      failed: Array.from(newFailed),
      nodeErrors: newNodeErrors,
      activeConnections: activeFlows
    })

    console.log('[FlowDesigner] ✅ 高亮更新完成:', {
      completed: newCompleted.size,
      executing: newExecuting ? 1 : 0,
      failed: newFailed.size,
      connections: activeFlows.length
    })
  } catch (e) {
    console.error('[FlowDesigner] ❌ 高亮更新失败:', e)
  }

  // 执行控制逻辑：出现失败节点时，立即停止后续执行并终止流程
  if (newFailed.size > 0) {
    handleExecutionFailure(newFailed)
  }
}

/**
 * 执行失败处理：停止轮询 → 调用后端终止流程
 * @param {Set|Array} failedSet 失败节点ID集合
 */
const handleExecutionFailure = async (failedSet) => {
  // 1. 立即停止轮询，避免继续触发
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  isExecuting.value = false

  // 2. 调用后端 API 终止流程，阻止后续节点执行
  if (currentExecutionId.value) {
    try {
      await terminateFlow(currentExecutionId.value)
      console.log('[FlowDesigner] 🛑 已调用后端终止流程:', currentExecutionId.value)
    } catch (e) {
      const classified = classifyError(e)
      console.warn('[FlowDesigner] 终止流程请求失败:', classified)
    }
  }
}

// ============ 加载超时提示（3秒）============
const startSlowLoadingTimer = () => {
  stopSlowLoadingTimer()
  slowLoadingTimer = setTimeout(() => {
    slowLoadingTip.value = true
  }, 3000)
}
const stopSlowLoadingTimer = () => {
  if (slowLoadingTimer) {
    clearTimeout(slowLoadingTimer)
    slowLoadingTimer = null
  }
  slowLoadingTip.value = false
}

// ============ 历史记录 / 执行记录 弹窗 ============
/**
 * 打开历史记录弹窗（同时最多3个记录窗口）
 */
const openHistoryModal = async () => {
  if (openRecordCount.value >= MAX_RECORD_MODALS) {
    ElMessage.warning('最多同时打开3个记录窗口')
    return
  }
  historyModalVisible.value = true
  await loadHistoryList()
}

/**
 * 加载历史记录列表（支持名称/状态/时间范围多条件组合搜索，默认按时间倒序）
 * 对应后端 GET /api-flow-execution/history，参数：flowDefinitionId/status/flowName/startTime/endTime
 */
const loadHistoryList = async () => {
  historyLoading.value = true
  try {
    const params = {
      flowDefinitionId: flowId.value,
      pageNum: 1,
      pageSize: 50
    }
    // 流程名称模糊搜索（后端参数名 flowName）
    if (historySearch.name) params.flowName = historySearch.name
    // 状态：后端 0=执行中 1=成功 2=失败 3=部分成功
    if (historySearch.status !== undefined && historySearch.status !== '' && historySearch.status !== null) {
      params.status = historySearch.status
    }
    // 时间范围（精确到秒，格式 yyyy-MM-dd HH:mm:ss）
    if (historySearch.startTime) params.startTime = historySearch.startTime
    if (historySearch.endTime) params.endTime = historySearch.endTime
    const res = await getFlowExecutionHistory(params)
    const data = res.data || res
    historyList.value = data.records || data.list || data || []
  } catch (e) {
    const classified = classifyError(e)
    ElMessage.error(`加载历史记录失败：${classified.message}`)
    historyList.value = []
  } finally {
    historyLoading.value = false
  }
}

/**
 * 查看历史记录详情：通过流程实例ID加载该次执行的执行结果
 * 历史记录项的 procInstId 对应流程实例ID
 */
const viewHistoryDetail = async (row) => {
  if (openRecordCount.value >= MAX_RECORD_MODALS) {
    ElMessage.warning('最多同时打开3个记录窗口')
    return
  }
  const procInstId = row.procInstId || row.processInstanceId
  if (!procInstId) {
    ElMessage.warning('该记录缺少流程实例ID，无法查询详情')
    return
  }
  historyDetailModalVisible.value = true
  historyDetailLoading.value = true
  historyDetailResult.value = {}
  try {
    const res = await getFlowExecutionResult(procInstId)
    historyDetailResult.value = res.data || res
  } catch (e) {
    const classified = classifyError(e)
    ElMessage.error(`加载执行结果失败：${classified.message}`)
    historyDetailResult.value = {}
  } finally {
    historyDetailLoading.value = false
  }
}

// 历史记录状态文案映射（后端：0=执行中 1=成功 2=失败 3=部分成功）
const historyStatusText = (status) => {
  const map = {
    0: '执行中', 1: '成功', 2: '失败', 3: '中断',
    SUCCESS: '成功', FAILED: '失败', INTERRUPTED: '中断'
  }
  return map[status] || status || '-'
}
const historyStatusType = (status) => {
  const map = {
    0: 'primary', 1: 'success', 2: 'danger', 3: 'warning',
    SUCCESS: 'success', FAILED: 'danger', INTERRUPTED: 'warning'
  }
  return map[status] || 'info'
}

// 清除所有执行高亮
const clearExecutionHighlights = () => {
  if (bpmnCanvasRef.value) {
    bpmnCanvasRef.value.clearAllHighlights()
  }
  
  currentExecutionId.value = null
  isExecuting.value = false
  executingNodeId.value = null
  completedNodeIds.value = new Set()
  failedNodeIds.value = new Set()
  
  console.log('[FlowDesigner] 🧹 已清除所有执行高亮')
}

/**
 * 流程启动时立即高亮开始节点为"执行中"（蓝色），
 * 并将其出连线标记为执行中流动轨迹，营造从开始节点启动的可视化效果。
 */
const highlightStartNodeAsExecuting = () => {
  if (!bpmnCanvasRef.value) return
  const elementRegistry = bpmnCanvasRef.value.getElementRegistry()
  if (!elementRegistry) return

  const allElements = elementRegistry.getAll()
  // 查找开始事件（bpmn:StartEvent）
  const startEvent = allElements.find(el => el.type === 'bpmn:StartEvent' || el.businessObject?.$type === 'bpmn:StartEvent')
  if (!startEvent) {
    console.warn('[FlowDesigner] 未找到开始事件节点，跳过初始高亮')
    return
  }

  executingNodeId.value = startEvent.id
  bpmnCanvasRef.value.highlightNodes({
    completed: [],
    executing: startEvent.id,
    failed: [],
    nodeErrors: {}
  })
  console.log('[FlowDesigner] 🔄 已高亮开始节点为执行中:', startEvent.id)
}

const onOutputMappingChange = () => {
  if (!selectedElement.value) return
  const binding = nodeBindings.value[selectedElement.value.id]
  if (!binding) return
  const val = editingOutputMapping.value.trim()
  if (!val) {
    binding.outputMapping = null
    setFlowableAttrs(selectedElement.value, { outputMapping: '' })
    return
  }
  try {
    validateOutputParamsConfig(val, binding.name || selectedElement.value.businessObject?.name || selectedElement.value.id)
    binding.outputMapping = val
    setFlowableAttrs(selectedElement.value, { outputMapping: val })
  } catch (e) {
    ElMessage.error(e.message || '出参配置格式错误')
  }
}

const unbindCurrentNode = () => {
  if (!selectedElement.value) return
  const newNodeBindings = { ...nodeBindings.value }
  delete newNodeBindings[selectedElement.value.id]
  nodeBindings.value = newNodeBindings
  selectedNodeServiceId.value = null
  editingInputMapping.value = ''
  editingOutputMapping.value = ''
  editingAssertions.value = []

  // 清除画布上的节点名称
  if (bpmnCanvasRef.value && selectedElement.value) {
    const modeling = bpmnCanvasRef.value.getModeler()?.get('modeling')
    if (modeling) {
      modeling.updateProperties(selectedElement.value, { name: '' })
      bpmnCanvasRef.value.refreshServiceTaskLabel(selectedElement.value)
    }
  }
  ElMessage.success('已取消绑定')
}

// 断言规则管理
const addNodeAssertion = () => editingAssertions.value.push({ field:'', operator:'==', expectedValue:'' })
const removeNodeAssertion = (i) => { editingAssertions.value.splice(i,1); saveNodeAssertions() }
const saveNodeAssertions = () => {
  if (!selectedElement.value) return
  const binding = nodeBindings.value[selectedElement.value.id]
  if (!binding) return
  const backendAssertions = editingAssertions.value.map(item => ({
    type: item._backendType || inferAssertionType(item.field),
    operator: toBackendOperator(item.operator),
    expectedValue: item.expectedValue
  }))
  binding.assertionRules = backendAssertions.length ? JSON.stringify(backendAssertions) : null
}

const handleShapeAdded = (event) => {
  console.log('[FlowDesigner] 节点添加:', event)
}

const handleImportDone = (result) => {
  console.log('[FlowDesigner] BPMN 导入完成:', result)
}

// ==================== 流程操作 ====================

const handleNew = async () => {
  if (!flowAppId.value) {
    ElMessage.warning('请先选择应用')
    return
  }
  flowId.value = null
  flowName.value = '未命名流程'
  currentFlowStatus.value = 'DRAFT'
  initialXml.value = emptyXml
  nodeBindings.value = {}
  selectedElement.value = null
  selectedNodeServiceId.value = null
  editingInputMapping.value = ''
  editingOutputMapping.value = ''
  editingAssertions.value = []
}

const handleSelectFlow = async (flow) => {
  flowId.value = flow.id
  flowName.value = flow.name
  currentFlowStatus.value = flow.status === 'DEPLOYED' ? 'DEPLOYED' : 'DRAFT'
  nodeBindings.value = {}
  
  try {
    const res = await getFlowDefinitionDetail(flow.id)
    const detail = res.data || res
    const xmlContent = detail.definitionXml || detail.bpmnXml
    if (xmlContent) {
      initialXml.value = xmlContent
    }
  } catch (e) {
    console.warn('加载流程 XML 失败', e)
  }
}

// 第一步：保存草稿 —— 仅存基本信息（名称、应用），不存 XML，状态=0
const handleSaveDraft = async () => {
  if (!flowName.value) {
    ElMessage.warning('请输入流程名称')
    return
  }
  if (!flowAppId.value) {
    ElMessage.warning('请选择所属应用')
    return
  }

  savingDraft.value = true
  try {
    const payload = {
      name: flowName.value,
      applicationId: flowAppId.value
    }
    if (flowId.value) payload.id = flowId.value

    const res = await saveDraft(payload)
    const result = res.data || res
    if (!flowId.value) {
      flowId.value = result.id
    }
    currentFlowStatus.value = 'DRAFT'
    ElMessage.success('草稿保存成功')
    await loadFlows()
  } catch (e) {
    ElMessage.error('草稿保存失败: ' + (e.response?.data?.message || e.message))
  } finally {
    savingDraft.value = false
  }
}

// 第二步：保存设计 —— 存 BPMN XML 和节点配置，状态=1=未部署
const handleSaveOrUpdate = async () => {
  if (!flowName.value) {
    ElMessage.warning('请输入流程名称')
    return
  }
  
  try {
    // ✅ 关键：在获取 XML 前确保所有节点都有正确的属性
    await ensureServiceTaskProperties()
    
    let xml = await getBpmnXml()
    console.log('[FlowDesigner] 🛡️ 开始 XML 后处理...')
  const processedXml = injectFlowableAttributesToXml(xml, getResolvedNodeBindings())

    if (processedXml !== xml) {
      console.log('[FlowDesigner] ✅ XML 已修复')
      xml = processedXml
    } else {
      console.log('[FlowDesigner] ℹ️ XML 无需修复')
    }

    const nodesArray = generateNodesArray()
    
const payload = {
  name: flowName.value,
  applicationId: flowAppId.value || null,  // ✅ 空字符串转为 null
  bpmnXml: xml,
  edited: false
}
    
    if (flowId.value) payload.id = flowId.value
    if (nodesArray.length > 0) payload.nodes = nodesArray
    if (flowOwnerId.value && String(flowOwnerId.value) !== String(currentUserId)) {
      payload.ownerId = flowOwnerId.value
    }
    
    const res = await saveFlowDefinition(payload)
    // 后端 Result 包装：兼容 res.data.data / res.data / res
    const result = (res && res.data && res.data.data) || (res && res.data) || res

  if (!flowId.value) {
      flowId.value = result.id || result.flowDefinitionId
    }
    currentFlowStatus.value = 'DEPLOYED'  // 保存即自动部署，状态=已发布
    ElMessage.success('保存成功，流程已自动部署')
    
    await loadFlows()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

// 第三步：部署 —— 部署到 Flowable 引擎，状态=2=已发布
const handleDeployOrUpdate = async () => {
  if (!flowName.value) {
    ElMessage.warning('请输入流程名称')
    return
  }
  
  try {
    await ElMessageBox.confirm(`确定要${flowId.value ? '更新部署' : '部署'}该流程吗？`, '确认', { type: 'warning' })
  } catch { return }
  
 deploying.value = true
  try {
    // ✅ 关键：在获取 XML 前确保所有节点都有正确的属性
    await ensureServiceTaskProperties()
    let xml = await getBpmnXml()
    const processedXml = injectFlowableAttributesToXml(xml, getResolvedNodeBindings())
    if (processedXml !== xml) {
      xml = processedXml
    }
    const nodesArray = generateNodesArray()
    
    const payload = {
    name: flowName.value,
    applicationId: flowAppId.value || null, 
    bpmnXml: xml,
    edited: true
    }
    
    if (nodesArray.length > 0) payload.nodes = nodesArray
    if (flowOwnerId.value && String(flowOwnerId.value) !== String(currentUserId)) {
      payload.ownerId = flowOwnerId.value
    }
    
    let res
    if (flowId.value) {
      payload.id = flowId.value
      res = await updateFlowDefinition(payload)
    } else {
      res = await deployFlowDefinition(payload)
    }
    
    const result = res.data || res
    if (!flowId.value) {
      flowId.value = result.flowDefinitionId || result.id
    }
    
    currentFlowStatus.value = 'DEPLOYED'
    ElMessage.success(`${flowId.value ? '更新部署' : '部署'}成功`)
    await loadFlows()
  } catch (e) {
    ElMessage.error(`${flowId.value ? '更新部署' : '部署'}失败: ` + (e.response?.data?.message || e.message))
  } finally {
    deploying.value = false
  }
}

const handleRevertDraft = async () => {
  if (!flowId.value || currentFlowStatus.value !== 'DEPLOYED') return
  
  try {
    await ElMessageBox.confirm('确定要将该流程回退到草稿状态吗？回退后可以重新编辑节点配置。', '确认回退', { type: 'warning' })
  } catch { return }
  
  try {
    await updateFlowStatus(flowId.value, 0) // 0 = DRAFT
    currentFlowStatus.value = 'DRAFT'
    ElMessage.success('已回退到草稿状态')
    await loadFlows()
  } catch (e) {
    ElMessage.error('回退失败: ' + (e.response?.data?.message || e.message))
  }
}

// --- 画布操作 ---
const handleUndo = () => bpmnCanvasRef.value?.undo()
const handleRedo = () => bpmnCanvasRef.value?.redo()
const handleZoomIn = () => bpmnCanvasRef.value?.zoomIn()
const handleZoomOut = () => bpmnCanvasRef.value?.zoomOut()
const handleFitView = () => bpmnCanvasRef.value?.fitView()

const handleDeleteSelected = () => {
  if (!selectedElement.value || !bpmnCanvasRef.value) return

  bpmnCanvasRef.value.removeElement(selectedElement.value)
  selectedElement.value = null
  ElMessage.success('元素已删除')
}

const handleAppChange = async (value) => {
  console.log('[FlowDesigner] 应用选择变化:', {
    selectedValue: value,
    type: typeof value,
    flowAppId: flowAppId.value
  })
  // 应用变更时，重新加载服务列表
  if (value) {
    await loadServiceList()
  } else {
    serviceList.value = []
  }
}
// ✅ 新增：确保所有 ServiceTask 都有实现属性
const ensureServiceTaskProperties = async () => {
  if (!bpmnCanvasRef.value) return

  const modeler = bpmnCanvasRef.value.getModeler()
  if (!modeler) return

  const elementRegistry = modeler.get('elementRegistry')
  if (!elementRegistry) return

  const serviceTasks = elementRegistry.filter(el =>
    isServiceTask(el) && !el.parent?.type?.includes('Participant')
  )

  for (const task of serviceTasks) {
    try {
      const nodeId = task.id
      const bo = task.businessObject || {}
      const cachedInfo = bpmnCanvasRef.value.getCachedServiceInfo?.(nodeId)
      const binding = nodeBindings.value[nodeId] || {}
      const inputMapping = normalizeJsonString(binding.inputMapping) || getFlowableAttr(bo, 'inputMapping')
      const outputMapping = normalizeJsonString(binding.outputMapping) || getFlowableAttr(bo, 'outputMapping')
      const assertions = normalizeJsonString(binding.assertionRules) || getFlowableAttr(bo, 'assertions')
      const serviceId = binding.atomicServiceId || binding.serviceId || getFlowableAttr(bo, 'serviceId')
      const serviceName = cachedInfo?.serviceName || binding.name || getFlowableAttr(bo, 'serviceName') || bo.name || ''
      const serviceAddress = cachedInfo?.serviceAddress || binding.address || binding.url || getFlowableAttr(bo, 'serviceAddress') || getFlowableAttr(bo, 'httpRequestUrl') || ''
      const httpMethod = (binding.httpMethod || getFlowableAttr(bo, 'httpRequestMethod') || 'POST').toUpperCase()

      setFlowableAttrs(task, buildServiceTaskFlowableAttrs({
        serviceId,
        serviceName,
        serviceAddress,
        inputMapping,
        outputMapping,
        assertions,
        httpMethod
      }))
    } catch (e) {
      console.error(`[FlowDesigner] 设置 ServiceTask 属性失败: ${task.id}`, e)
    }
  }

  await new Promise(resolve => setTimeout(resolve, 100))
}
const buildFlowableHttpFieldsXml = (method = 'POST', url = '') => {
  if (!url) return ''
  return `        <flowable:field name="requestMethod">
          <flowable:string>${xmlEscape((method || 'POST').toUpperCase())}</flowable:string>
        </flowable:field>
        <flowable:field name="requestUrl">
          <flowable:string>${xmlEscape(url)}</flowable:string>
        </flowable:field>
`
}

const injectFlowableHttpFieldsToTask = (xml, nodeId, method, url) => {
  if (!url) return xml

  const escapedId = nodeId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const blockRegex = new RegExp(
    `(<(?:\\w+:)?[Ss]ervice[Tt]ask[^>]*id[^=]*=["']${escapedId}["'][^>]*>)([\\s\\S]*?)(</(?:\\w+:)?[Ss]ervice[Tt]ask>)`,
    'i'
  )
  const fieldsXml = buildFlowableHttpFieldsXml(method, url)

  return xml.replace(blockRegex, (full, openTag, innerXml, closeTag) => {
    let cleanedInner = innerXml.replace(
      /\s*<flowable:field\s+name=["'](?:requestMethod|requestUrl|httpRequestMethod|httpRequestUrl)["'][\s\S]*?<\/flowable:field>/gi,
      ''
    )

    // Remove self-closing <extensionElements/> tags (empty and useless, would cause duplicates)
    cleanedInner = cleanedInner.replace(/\s*<(?:\w+:)?extensionElements[^>]*\/>/gi, '')

    const extensionRegex = /(<(?:\w+:)?extensionElements[^>]*>)([\s\S]*?)(<\/(?:\w+:)?extensionElements>)/i
    if (extensionRegex.test(cleanedInner)) {
      cleanedInner = cleanedInner.replace(extensionRegex, `$1
${fieldsXml}$2$3`)
      return `${openTag}${cleanedInner}${closeTag}`
    }

    return `${openTag}
      <bpmn:extensionElements>
${fieldsXml}      </bpmn:extensionElements>${cleanedInner}${closeTag}`
  })
}

const injectFlowableAttributesToXml = (xml, bindings) => {
  if (!xml || !bindings) return xml

  let resultXml = xml
  if (!/xmlns:flowable/i.test(resultXml)) {
    resultXml = resultXml.replace(
      /<definitions([^>]*)>/i,
      '<definitions$1\n  xmlns:flowable="http://flowable.org/bpmn">'
    )
  }

  Object.entries(bindings).forEach(([nodeId, binding]) => {
    const escapedId = nodeId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const regex = new RegExp(
      `(<(?:\\w+:)?[Ss]ervice[Tt]ask[^>]*id[^=]*=["']${escapedId}["'][^>]*)(>)`,
      'gi'
    )
    const found = regex.exec(resultXml)
    if (!found) return

    const originalTag = found[0]
    const tagBody = found[1]
    const method = (binding.httpMethod || 'POST').toUpperCase()
    const address = binding.address || binding.url || ''
    const attrs = buildServiceTaskFlowableAttrs({
      serviceId: binding.atomicServiceId || binding.serviceId || '',
      serviceName: binding.name || '',
      serviceAddress: address,
      inputMapping: normalizeJsonString(binding.inputMapping),
      outputMapping: normalizeJsonString(binding.outputMapping),
      assertions: normalizeJsonString(binding.assertionRules),
      httpMethod: method
    })

    const cleanBody = tagBody.replace(/\s+flowable:[\w-]+\s*=\s*["'][^"']*["']/gi, '')
    const attrText = Object.entries(attrs)
      .filter(([, value]) => value !== undefined && value !== null && value !== '')
      .map(([name, value]) => ` flowable:${name}="${xmlEscape(value)}"`)
      .join('')

    resultXml = resultXml.replace(originalTag, `${cleanBody}${attrText}>`)
    resultXml = injectFlowableHttpFieldsToTask(resultXml, nodeId, method, address)
  })

  return resultXml
}
const getResolvedNodeBindings = () => {
  const bindings = { ...nodeBindings.value }
  const elementRegistry = bpmnCanvasRef.value?.getElementRegistry?.()
  if (!elementRegistry) return bindings

  elementRegistry.getAll().forEach(element => {
    if (!isServiceTask(element)) return

    const bo = element.businessObject || {}
    const existing = bindings[element.id] || {}
    bindings[element.id] = {
      ...existing,
      atomicServiceId: existing.atomicServiceId || existing.serviceId || getFlowableAttr(bo, 'serviceId'),
      serviceId: existing.serviceId || existing.atomicServiceId || getFlowableAttr(bo, 'serviceId'),
      name: existing.name || getFlowableAttr(bo, 'serviceName') || bo.name || element.id,
      address: existing.address || existing.url || getFlowableAttr(bo, 'serviceAddress') || getFlowableAttr(bo, 'httpRequestUrl'),
      httpMethod: existing.httpMethod || getFlowableAttr(bo, 'httpRequestMethod') || 'POST',
      inputMapping: normalizeJsonString(existing.inputMapping) || getFlowableAttr(bo, 'inputMapping'),
      outputMapping: normalizeJsonString(existing.outputMapping) || getFlowableAttr(bo, 'outputMapping'),
      assertionRules: normalizeJsonString(existing.assertionRules) || getFlowableAttr(bo, 'assertions')
    }
  })

  return bindings
}
const getBpmnXml = async () => {
  if (!bpmnCanvasRef.value) return ''
  return await bpmnCanvasRef.value.getXML()
}

// --- 部署（新建流程）---
const handleDeploy = async () => {
  if (!flowName.value) {
    ElMessage.warning('请输入流程名称')
    return
  }

  const appIdToUse = flowAppId.value

  if (!appIdToUse) {
    ElMessage.warning('请选择所属应用')
    return
  }

  try {
    await ElMessageBox.confirm('确定要部署该流程吗？', '确认部署', { type: 'warning' })
  } catch { return }

  deployLoading.value = true

  try {
    const xml = await getBpmnXml()

    console.log('[FlowDesigner] ===== 开始部署（新建流程）=====')
    console.log('[FlowDesigner] 流程名称:', flowName.value)
    console.log('[FlowDesigner] BPMN XML 长度:', xml.length)

    // 生成 nodes 数组
    const nodesArray = generateNodesArray()

    const payload = {
      name: flowName.value,
      applicationId: flowAppId.value,
      businessDomainId: 0,
      bpmnXml: xml
    }

    if (flowOwnerId.value && String(flowOwnerId.value) !== String(currentUserId)) {
      payload.ownerId = flowOwnerId.value
    }

    if (nodesArray.length > 0) {
      payload.nodes = nodesArray
      console.log('[FlowDesigner] 传递 nodes 数组，数量:', nodesArray.length)
    }

    const res = await deployFlowDefinition(payload)
    const result = res.data || res

    if (result.flowDefinitionId) {
      flowId.value = result.flowDefinitionId
    } else if (result.id) {
      flowId.value = result.id
    }

    ElMessage.success('流程部署成功')
  } catch (e) {
    const status = e.response?.status
    const respData = e.response?.data

    let errMsg = '部署失败'
    if (respData) {
      if (typeof respData === 'string') errMsg = respData
      else if (respData.message) errMsg = respData.message
      else if (respData.msg) errMsg = respData.msg
      else if (respData.error) errMsg = respData.error
      else errMsg = JSON.stringify(respData)
    } else {
      errMsg = `HTTP ${status}: ${e.message}`
    }

    ElMessage.error(`部署失败: ${errMsg}`)
  } finally {
    deployLoading.value = false
  }
}

// --- 更新（编辑已有流程）---
const handleUpdate = async () => {
  if (!flowId.value) {
    ElMessage.warning('请先保存流程后再更新')
    return
  }

  if (!flowName.value) {
    ElMessage.warning('请输入流程名称')
    return
  }

  const appIdToUse = flowAppId.value

  if (!appIdToUse) {
    ElMessage.warning('请选择所属应用')
    return
  }

  try {
    await ElMessageBox.confirm('确定要更新该流程吗？', '确认更新', { type: 'warning' })
  } catch { return }

  updateLoading.value = true
  try {
    const xml = await getBpmnXml()
    const nodesArray = generateNodesArray()

    const payload = {
      id: flowId.value,
      name: flowName.value,
      bpmnXml: xml,
      edited: false
    }

    if (flowOwnerId.value && String(flowOwnerId.value) !== String(currentUserId)) {
      payload.ownerId = flowOwnerId.value
    }

    if (nodesArray.length > 0) {
      payload.nodes = nodesArray
    }

    const res = await updateFlowDefinition(payload)
    ElMessage.success('流程更新成功')
  } catch (e) {
    const respData = e.response?.data
    let errMsg = '更新失败'
    if (respData) {
      if (typeof respData === 'string') errMsg = respData
      else if (respData.message) errMsg = respData.message
      else if (respData.msg) errMsg = respData.msg
      else errMsg = JSON.stringify(respData)
    }
    ElMessage.error(`更新失败: ${errMsg}`)
  } finally {
    updateLoading.value = false
  }
}

// --- 导出XML ---
const handleExportXml = async () => {
  try {
    const xml = await getBpmnXml()
    const blob = new Blob([xml], { type: 'application/xml' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${flowName.value || '流程图'}.bpmn`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('XML 导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

// --- 执行流程 ---
const handleExecute = () => {
  executeDialogVisible.value = true
  executeForm.inputParamsStr = ''
}

const submitExecute = async () => {
  if (!flowId.value) {
    ElMessage.warning('请先保存流程')
    return
  }

  // 清理上一次执行的错误状态与高亮
  clearExecutionHighlights()

  executeLoading.value = true
  // 后端响应超过3秒时显示加载中提示
  startSlowLoadingTimer()
  try {
    let inputParams = {}
    if (executeForm.inputParamsStr) {
      try {
        inputParams = JSON.parse(executeForm.inputParamsStr)
      } catch {
        ElMessage.error('入参必须是合法的 JSON 格式')
        executeLoading.value = false
        stopSlowLoadingTimer()
        return
      }
    }

    const res = await executeFlow(flowId.value, {
      flowDefinitionId: flowId.value,
      env: executeForm.env,
      executeMode: executeForm.executeMode,
      timeout: executeForm.timeout,
      inputParams
    })

    // 后端 Result 包装：{code, message, data: FlowExecuteResponseVO}
    // 兼容多种返回结构：res.data.data / res.data / res
    const result = (res && res.data && res.data.data) || (res && res.data) || res
    executionResult.value = result
    executeDialogVisible.value = false

    // 保存 executionId 用于后续轮询和高亮
    // 优先取 processInstanceId（Flowable实例ID），这是轮询的真正参数
    currentExecutionId.value = result?.processInstanceId || result?.id || result?.taskNo || null
    if (!currentExecutionId.value) {
      console.error('[FlowDesigner] ❌ 执行后未获取到 processInstanceId，res=', res)
      ElMessage.error('执行启动响应缺少流程实例ID，请查看网络日志')
      isExecuting.value = false
      return
    }
    isExecuting.value = true
    console.log('[FlowDesigner] 🚀 流程已启动, processInstanceId:', currentExecutionId.value)

    // 立即从开始节点启动可视化执行：高亮开始节点为"执行中"（蓝色）
    highlightStartNodeAsExecuting()

    // 统一使用轮询模式，实现节点状态的渐进式可视化
    // （同步模式返回时已是最终态，仍通过轮询逐步呈现执行轨迹）
    startPolling()
  } catch (e) {
    // 错误分类：区分前端/后端/网络问题
    const classified = classifyError(e)
    ElMessage.error(`执行失败：${classified.message}（${classified.source}错误码 ${classified.code}）`)
  } finally {
    executeLoading.value = false
    stopSlowLoadingTimer()
  }
}
// 开始轮询执行结果
const startPolling = () => {
  if (pollTimer) clearInterval(pollTimer)

  // 防御：没有 processInstanceId 时不启动轮询，避免请求 /execution/undefined
  if (!currentExecutionId.value) {
    console.warn('[FlowDesigner] ⚠️ startPolling: currentExecutionId 为空，跳过轮询')
    return
  }

  console.log('[FlowDesigner] ⏱️ 开始轮询执行结果, processInstanceId=', currentExecutionId.value)

  pollTimer = setInterval(async () => {
    try {
      const res = await getFlowExecutionResult(currentExecutionId.value)
      // 后端响应被拦截器返回为 Result 包装对象 { code, data, message }，需取 data
      const result = res.data || res
      executionResult.value = result

      // 关键：更新画布高亮（传入完整result对象以包含连线信息；若发现失败节点，handleExecutionFailure 会停止轮询）
      if (result.nodeTraces && result.nodeTraces.length > 0) {
        updateExecutionHighlights(result)
      }

      // 若轮询已被 handleExecutionFailure 清除（失败场景），直接返回
      if (!pollTimer) return

      // 判断是否完成或失败（根据你的后端返回值调整）
      // 假设: 0=执行中, 1=成功, 2=失败, 3=部分成功
      const isFinished = [1, 2, 3].includes(result.status)

      if (isFinished) {
        clearInterval(pollTimer)
        pollTimer = null
        isExecuting.value = false

        console.log('[FlowDesigner] ✅ 流程执行结束, 最终状态:', result.status)

        // 执行结束（成功/失败/部分成功）：弹出执行结果弹窗，与流程设计页保持一致
        const messages = {
          1: '流程执行成功',
          2: '流程执行失败',
          3: '流程部分执行成功'
        }
        if (result.status === 2) {
          ElMessage.error(messages[result.status] || '流程执行失败')
        } else {
          ElMessage.success(messages[result.status] || '流程执行结束')
        }
        resultDialogVisible.value = true
      }
    } catch (e) {
      console.error('[FlowDesigner] ❌ 轮询失败:', e)
      // 轮询失败归一化为前端错误码
      const classified = classifyError(e)
      if (pollTimer) {
        clearInterval(pollTimer)
        pollTimer = null
      }
      isExecuting.value = false
      ElMessage.error(`获取执行结果失败：${classified.message}（错误码 ${classified.code}）`)
    }
  }, 2000)  // 每2秒轮询一次
}

const pollExecutionResult = (processInstanceId) => {
  pollTimer = setInterval(async () => {
    try {
      const res = await getFlowExecutionResult(processInstanceId)
      const result = res.data || res
      executionResult.value = result

      if (result.status !== 0) {
        clearInterval(pollTimer)
        pollTimer = null
        resultLoading.value = false
      }
    } catch (e) {
      clearInterval(pollTimer)
      pollTimer = null
      resultLoading.value = false
      ElMessage.error('获取执行结果失败')
    }
  }, 2000)
}



// --- 加载服务列表 ---
const loadServiceList = async () => {
  if (!flowAppId.value) return
  try {
    const res = await listOriginalServices({ applicationId: flowAppId.value })
    const data = res.data || res
    serviceList.value = data.records || data || []
  } catch (e) {
    console.warn('[FlowDesigner] 加载服务列表失败:', e)
  }
}

// --- 加载应用列表（根据用户角色智能过滤）---
const loadAppList = async () => {
  try {
    // 1. 获取当前用户信息
    const raw = sessionStorage.getItem('userInfo') || localStorage.getItem('userInfo') || '{}'
    currentUserInfo = JSON.parse(raw)
    
    // 判断是否管理员
    const isAdmin = currentUserInfo?.roles?.includes('admin') || 
                   currentUserInfo?.role === 'admin' || 
                   currentUserInfo?.isAdmin === true ||
                   currentUserInfo?.username === 'admin'
    
    currentUserDeptId = currentUserInfo?.deptId || currentUserInfo?.departmentId
    
    console.log('[FlowDesigner] 当前用户:', {
      username: currentUserInfo?.username,
      isAdmin: isAdmin,
      deptId: currentUserDeptId
    })
    
    // 2. 使用缓存获取完整的部门树（包含所有部门和它们的应用）
    const tree = await loadDepartmentTree()
    
    if (!Array.isArray(tree) || tree.length === 0) {
      appList.value = []
      return
    }
    
    // 3. 根据角色过滤应用列表
    let apps = []
    
    if (isAdmin) {
      // 管理员：提取所有部门下的所有应用
      tree.forEach(dept => {
        const appArr = dept.applications || dept.children || []
        if (Array.isArray(appArr)) {
          appArr.forEach(app => {
            if (app?.id && !apps.find(a => a.id === app.id)) {
              apps.push({
                id: app.id,
                name: app.name || app.label || '未命名应用',
                deptName: dept.label || dept.name
              })
            }
          })
        }
      })
      console.log('[FlowDesigner] 管理员模式：加载全部', apps.length, '个应用')
    } else {
      // 普通用户：只加载其所属部门下的应用
      if (currentUserDeptId) {
        const userDept = tree.find(d => String(d.id) === String(currentUserDeptId))
        if (userDept) {
          const appArr = userDept.applications || userDept.children || []
          apps = appArr.filter(app => app?.id).map(app => ({
            id: app.id,
            name: app.name || app.label || '未命名应用',
            deptName: userDept.label || userDept.name
          }))
          console.log('[FlowDesigner] 普通用户模式：加载部门「', userDept.label, '」下的', apps.length, '个应用')
        } else {
          console.warn('[FlowDesigner] 未找到用户所属部门，ID:', currentUserDeptId)
        }
      } else {
        console.warn('[FlowDesigner] 用户无部门信息，无法加载应用')
      }
    }
    
    appList.value = apps
    
  } catch (e) {
    console.error('[FlowDesigner] 加载应用列表失败:', e)
    appList.value = []
  }
}

// --- 加载负责人列表 ---
const loadOwnerList = async () => {
  try {
    const raw = sessionStorage.getItem('userInfo') || localStorage.getItem('userInfo')
    if (raw) {
      try {
        const info = JSON.parse(raw)
        currentUserId = info.userId || info.id
      } catch (e) { /* ignore */ }
    }

    // 使用缓存加载用户列表
    const records = await loadAllUsers()
    ownerList.value = records

    if (currentUserId) {
      const matchedUser = records.find(u => String(u.id) === String(currentUserId))
      if (matchedUser) {
        flowOwnerId.value = matchedUser.id
      }
    }
  } catch (e) {
    console.warn('[FlowDesigner] 加载负责人列表失败:', e)
  }
}

// --- 生成 nodes 数组 ---
const generateNodesArray = () => {
  const nodesArray = []

  if (!bpmnCanvasRef.value) return nodesArray

  const elementRegistry = bpmnCanvasRef.value.getElementRegistry()
  if (!elementRegistry) return nodesArray

  const allElements = elementRegistry.getAll()

  allElements.forEach(element => {
    if (!isServiceTask(element)) return

    const bo = element.businessObject || {}
    const binding = nodeBindings.value[element.id] || {}
    const nodeName = getFlowableAttr(bo, 'serviceName') || binding.name || bo.name || element.id
    const originalServiceId = getFlowableAttr(bo, 'serviceId') || binding.atomicServiceId || binding.serviceId || ''
    const inputParams = getFlowableAttr(bo, 'inputMapping') || normalizeJsonString(binding.inputMapping)
    const outputParams = getFlowableAttr(bo, 'outputMapping') || normalizeJsonString(binding.outputMapping)
    const assertions = getFlowableAttr(bo, 'assertions') || normalizeJsonString(binding.assertionRules)

    if (!originalServiceId) {
      throw new Error(`节点 "${nodeName}" 未绑定原服务`)
    }

    validateInputParamsConfig(inputParams, nodeName)
    validateOutputParamsConfig(outputParams, nodeName)

    nodesArray.push({
      nodeKey: element.id,
      nodeName,
      nodeType: 3,
      originalServiceId,
      inputParams,
      outputParams,
      assertions,
      timeout: 30000,
      retryCount: 0,
      retryInterval: 0,
      retryType: 1
    })
  })

  console.log('[FlowDesigner] 生成 nodes 数组:', nodesArray)
  return nodesArray
}
const getExecStatusText = (s) => ({ 0: '执行中', 1: '成功', 2: '失败', 3: '部分成功' }[s] || '未知')
const getExecStatusType = (s) => ({ 0: 'warning', 1: 'success', 2: 'danger', 3: 'warning' }[s] || 'info')
const formatTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '-'

// 草稿状态下退出自动保存基本信息
const autoSaveDraftOnExit = async () => {
  // 仅在已有 flowId 且处于草稿/未部署状态时自动保存基本信息
  if (!flowId.value) return
  if (currentFlowStatus.value === 'DEPLOYED') return
  if (!flowName.value || !flowAppId.value) return
  try {
    await saveDraft({
      id: flowId.value,
      name: flowName.value,
      applicationId: flowAppId.value
    })
    console.log('[FlowDesigner] 草稿自动保存成功')
  } catch (e) {
    console.warn('[FlowDesigner] 草稿自动保存失败:', e)
  }
}

const goBack = async () => {
  await autoSaveDraftOnExit()
  // 从测试集"详情"进入：返回测试集页面
  if (route.query.from === 'testSuite') {
    router.push('/layout/testSuite')
    return
  }
  router.push('/layout/bpmn/design')
}

// --- 生命周期 ---
onMounted(async () => {
  await nextTick()
  await Promise.all([
    loadOwnerList().catch(e => console.error('[FlowDesigner] loadOwnerList 失败:', e)),
    loadAppList().catch(e => console.error('[FlowDesigner] loadAppList 失败:', e))
  ])
  await loadFlowDetail()
})

onBeforeUnmount(() => {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  stopSlowLoadingTimer()
})
</script>

<style scoped lang="scss">
.flow-designer {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  overflow: hidden;
}

.designer-toolbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
  z-index: 10;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 0 0 auto;
  }
  
  .toolbar-center {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0 16px;
    flex: 1;
    justify-content: center;
  }
  
  .toolbar-right {
    display: flex;
    align-items: center;
    gap: 6px;
    flex: 0 0 auto;
  }
  
  .toolbar-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
    white-space: nowrap;
  }
}

.designer-body {
  flex: 1;
  display: flex;
  min-height: 0;
  overflow: hidden;
}

// 左侧原服务列表面板
.left-panel {
  width: 220px;
  min-width: 200px;
  max-width: 280px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  
  .panel-header {
    padding: 12px 14px;
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    border-bottom: 1px solid #ebeef5;
    background: #fafafa;
  }
  
  .service-list-scroll {
    flex: 1;
    overflow-y: auto;
    padding: 8px;
    
    &::-webkit-scrollbar { width: 4px; }
    &::-webkit-scrollbar-thumb { 
      background: #dcdfe6; 
      border-radius: 2px; 
    }
  }
  
  .service-drag-item {
    padding: 10px 12px;
    margin-bottom: 6px;
    border: 1px solid #e4e7ed;
    border-radius: 6px;
    cursor: grab;
    transition: all 0.2s ease;
    background: #fff;
    
    &:hover {
      border-color: #409eff;
      box-shadow: 0 2px 8px rgba(64,158,255,0.15);
      transform: translateY(-1px);
    }
    
    &:active {
      cursor: grabbing;
      transform: scale(0.98);
    }
    
    .service-name {
      font-size: 13px;
      font-weight: 500;
      color: #303133;
      margin-bottom: 4px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    
    .service-addr {
      font-size: 11px;
      color: #909399;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
  
  .empty-hint {
    text-align: center;
    padding: 40px 20px;
    color: #c0c4cc;
    font-size: 13px;
  }
}

// 中间画布区域
.canvas-container {
  flex: 1;
  min-width: 0;
  position: relative;
  background: #fff;
  
  :deep(.bpmn-canvas) {
    width: 100%;
    height: 100%;
  }
}

// 右侧面板
.right-panel {
  width: 320px;
  min-width: 300px;
  max-width: 380px;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

// 节点配置区域（占满右侧面板）
.config-section {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  
  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-thumb { 
    background: #dcdfe6; 
    border-radius: 2px; 
  }
  
  .panel-title {
    padding: 10px 14px;
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    border-bottom: 1px solid #ebeef5;
    background: #fafafa;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  
  .tab-placeholder {
    padding: 40px 20px;
    text-align: center;
    color: #909399;
    font-size: 13px;
    line-height: 1.6;
  }
  
  .deploy-warning {
    margin: 8px 12px;
    padding: 8px 12px;
    background: #fdf6ec;
    border: 1px solid #faecd8;
    border-radius: 4px;
    font-size: 12px;
    color: #e6a23c;
    line-height: 1.5;
  }
  
  .config-form {
    padding: 12px 14px;
  }
  
  // 绑定组样式
  .readonly-banner {
    display: flex;
    align-items: center;
    padding: 8px 12px;
    margin-bottom: 12px;
    background: #fdf6ec;
    color: #b88230;
    border: 1px solid #faecd8;
    border-radius: 4px;
    font-size: 12px;
    line-height: 1.4;
  }

  .bind-group {
    margin-bottom: 16px;
    
    .bind-label {
      font-size: 13px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 8px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      
      .mapping-hint {
        font-size: 11px;
        font-weight: 400;
        color: #909399;
      }
    }
    
    .bound-service {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
    }
    
    .no-binding-text {
      font-size: 12px;
      color: #909399;
      margin-bottom: 8px;
    }
    
    // 绑定步骤容器
    .bind-steps {
      .bind-step {
        .step-hint {
          font-size: 12px;
          font-weight: 500;
          color: #409eff;
          margin-bottom: 6px;
        }
      }
    }
    
    .mapping-tip {
      font-size: 11px;
      color: #909399;
      margin-bottom: 8px;
      line-height: 1.5;
      
      code {
        background: #f5f7fa;
        padding: 1px 4px;
        border-radius: 3px;
        font-family: 'Monaco', 'Menlo', monospace;
        font-size: 11px;
        color: #409eff;
      }
    }
    
    .mapping-textarea {
      :deep(.el-textarea__inner) {
        font-family: 'Monaco', 'Menlo', monospace;
        font-size: 12px;
        line-height: 1.5;
      }
    }
  }
  
  // 断言规则样式
  .assert-section {
    .assert-row {
      display: flex;
      gap: 6px;
      align-items: center;
      margin-bottom: 8px;
    }
  }
}

// 执行弹窗和结果弹窗保持原有样式
.execute-dialog {
  :deep(.el-dialog__body) {
    padding: 20px 24px;        // ✅ 增加内边距，表单更舒适
  }
  
  :deep(.el-form-item) {
    margin-bottom: 18px;       // ✅ 表单项间距优化
  }
  
  :deep(.el-textarea__inner) {
    font-family: 'Monaco', 'Menlo', monospace;  // ✅ JSON 输入框使用等宽字体
    font-size: 12px;
  }
}
// 执行结果弹窗样式
.result-dialog {
  :deep(.el-dialog__body) {
    padding: 20px 24px;
    max-height: 60vh;          // ✅ 限制最大高度，防止内容过多时溢出屏幕
    overflow-y: auto;          // ✅ 超出时可滚动
  }
  
  :deep(.el-descriptions) {
    margin-bottom: 16px;       // ✅ 描述列表与下方表格保持间距
  }
  
  :deep(.el-table) {
    margin-top: 12px;          // ✅ 表格顶部留白
  }
}

// 画布区域
.canvas-container {
  flex: 1;
  min-width: 0;
  position: relative;
  background: var(--paper, #fafafa);
  overflow: hidden;
}

// 右侧属性面板
.property-panel {
  width: 280px;
  background: var(--paper, #fafafa);
  border-left: 1px solid var(--mute, #e5e5e5);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow-y: auto;

  .panel-title {
    padding: 12px 16px;
    font-size: 10px;
    font-weight: 500;
    color: var(--ghost, #a3a3a3);
    border-bottom: 1px solid var(--mute, #e5e5e5);
    display: flex;
    align-items: center;
    justify-content: space-between;
    text-transform: uppercase;
    letter-spacing: 0.06em;

    .panel-actions {
      display: flex;
      gap: 4px;
      align-items: center;
    }
  }

  .prop-form {
    padding: 12px 16px;
  }

  &.empty-panel {
    display: flex;
    align-items: center;
    justify-content: center;

    .empty-tip {
      text-align: center;
      color: var(--ghost, #a3a3a3);

      p {
        margin-top: 8px;
        font-size: 12px;
      }
    }
  }
}

// 加载中提示条
.slow-loading-tip {
  position: fixed;
  top: 64px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2999;
  padding: 8px 24px;
  background: #FFF7E6;
  color: #FA8C16;
  border: 1px solid #FFD591;
  border-radius: 4px;
  font-size: 13px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

// 横幅过渡动画
.banner-slide-enter-active,
.banner-slide-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}
.banner-slide-enter-from,
.banner-slide-leave-to {
  transform: translate(-50%, -100%);
  opacity: 0;
}

// ==================== 记录弹窗内容样式 ====================
.record-search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.record-empty {
  text-align: center;
  padding: 40px 0;
  color: #c0c4cc;
  font-size: 13px;
}
</style>
