<template>
  <div class="api-tester-container">
    <div class="api-content">
      <div class="top-bar">
        <div class="input-area">
          <el-input v-model="form.appName" placeholder="用例名称" class="app-name-input" clearable size="small" />
          <el-select v-model="form.method" placeholder="方法" class="method-select" size="small">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
          <el-input v-model="form.url" placeholder="接口路径，/api/xxx 或 https://..." class="url-input" clearable />
        </div>
        <div class="action-group">
          <el-dropdown>
            <el-button type="primary" @click="sendRequest">发送</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click.native.prevent="sendRequest">发送 (立即)</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button @click="saveRequest">保存</el-button>
          <el-button :icon="ArrowRight" @click="goHome" circle />
        </div>
      </div>

      <!-- 鉴权配置 -->
      <ApiAuth ref="apiAuthRef" class="auth-section-wrapper" />

      <el-card class="tester-card">
        <el-tabs v-model="activeTab" type="card" class="api-tabs-wrap">
          <!-- 统一给所有tab-pane内部套滚动容器 -->
          <el-tab-pane label="Params" name="params">
            <div class="tab-scroll-wrap">
              <div class="params-area">
                <div class="params-header">Query 参数</div>
                <div class="params-table">
                  <div class="param-row header">
                    <div class="col name">参数名</div>
                    <div class="col value">参数值</div>
                    <div class="col type">类型</div>
                    <div class="col desc">说明</div>
                    <div class="col actions">操作</div>
                  </div>
                  <div v-for="(p, i) in params" :key="p.id" class="param-row">
                    <div class="col name"><el-input v-model="p.name" size="small" /></div>
                    <div class="col value"><el-input v-model="p.value" size="small" /></div>
                    <div class="col type"><el-select v-model="p.type" size="small">
                        <el-option label="string" value="string" />
                        <el-option label="number" value="number" />
                        <el-option label="boolean" value="boolean" />
                        <el-option label="integer" value="integer" />
                        <el-option label="array" value="array" />

                      </el-select>
                    </div>
                    <div class="col desc"><el-input v-model="p.desc" size="small" /></div>
                    <div class="col actions"><el-button type="text" size="small" @click="removeParam(i)">删除</el-button>
                    </div>
                  </div>
                  <div style="margin-top:8px"><el-button size="small" @click="addParam">添加参数</el-button></div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="Body" name="body">
            <div class="tab-scroll-wrap">
              <ApiBody v-model="form.paramsJson" v-model:bodyType="form.bodyType" />
            </div>
          </el-tab-pane>

          <el-tab-pane label="Headers" name="headers">
            <div class="tab-scroll-wrap">
              <div class="headers-header">
                <span>Headers</span>
                <span class="hidden-count">已隐藏 7 个</span>
                <div class="header-actions">
                  <el-button size="small" icon="SortAscending" />
                  <el-button size="small" icon="SortDescending" />
                  <el-button size="small">批量编辑</el-button>
                </div>
              </div>
              <div class="params-table">
                <div class="param-row header">
                  <div class="col name">参数名</div>
                  <div class="col value">参数值</div>
                  <div class="col type">类型</div>
                  <div class="col desc">说明</div>
                  <div class="col actions">操作</div>
                </div>
                <div v-for="(h, i) in headers" :key="h.id" class="param-row">
                  <div class="col name">
                    <el-autocomplete v-model="h.name" :fetch-suggestions="searchHeaders" placeholder="添加参数" size="small"
                      class="header-input" />
                  </div>
                  <div class="col value">
                    <el-input v-model="h.value" size="small" />
                  </div>
                  <div class="col type">
                    <el-select v-model="h.type" size="small">
                      <el-option label="string" value="string" />
                      <el-option label="integer" value="integer" />
                      <el-option label="boolean" value="boolean" />
                      <el-option label="number" value="number" />
                      <el-option label="array" value="array" />
                    </el-select>
                  </div>
                  <div class="col desc"><el-input v-model="h.desc" size="small" /></div>
                  <div class="col actions"><el-button type="text" size="small" @click="removeHeader(i)">删除</el-button>
                  </div>
                </div>
                <div style="margin-top:8px"><el-button size="small" @click="addHeader">添加参数</el-button></div>
              </div>
            </div>
          </el-tab-pane>

          <!-- Auth标签页 -->
          <el-tab-pane label="Auth" name="auth">
            <div class="tab-scroll-wrap">
              <div class="auth-panel">
                <div class="auth-label">鉴权方式</div>

                <!-- 下拉选择器 -->
                <div class="auth-select-wrapper">
                  <el-select v-model="authType" size="large" class="auth-select" placeholder="选择鉴权方式">
                    <el-option label="无需鉴权" value="none" />
                    <el-option label="继承" value="inherit" />
                    <el-option label="API Key" value="apiKey" />
                    <el-option label="Bearer Token" value="bearer" />
                    <el-option label="JWT Bearer" value="jwt" />
                  </el-select>
                </div>

                <!-- 分隔线和分组标题 -->
                <div class="auth-divider">
                  <span class="divider-text">自行设置</span>
                </div>

                <!-- 自行设置选项列表 -->
                <div class="auth-options">
                  <!-- API Key选项 -->
                  <div class="auth-option" :class="{ active: authType === 'apiKey' }" @click="authType = 'apiKey'">
                    <div class="option-header">
                      <span class="option-name">API Key</span>
                      <el-icon v-if="authType === 'apiKey'" class="option-check">
                        <Check />
                      </el-icon>
                    </div>
                    <span class="option-desc">通过URL参数或请求头传递API Key</span>
                  </div>

                  <!-- Bearer Token选项 -->
                  <div class="auth-option" :class="{ active: authType === 'bearer' }" @click="authType = 'bearer'">
                    <div class="option-header">
                      <span class="option-name">Bearer Token</span>
                      <el-icon v-if="authType === 'bearer'" class="option-check">
                        <Check />
                      </el-icon>
                    </div>
                    <span class="option-desc">使用Bearer Token进行身份验证</span>
                  </div>

                  <!-- JWT Bearer选项 -->
                  <div class="auth-option" :class="{ active: authType === 'jwt' }" @click="authType = 'jwt'">
                    <div class="option-header">
                      <span class="option-name">JWT Bearer</span>
                      <el-icon v-if="authType === 'jwt'" class="option-check">
                        <Check />
                      </el-icon>
                    </div>
                    <span class="option-desc">使用JSON Web Token进行身份验证</span>
                  </div>
                </div>

                <!-- 配置区域 -->
                <div class="auth-config-area">
                  <!-- API Key配置 -->
                  <div v-if="authType === 'apiKey'" class="auth-config">
                    <div class="config-header">API Key 配置</div>
                    <div class="config-row">
                      <label>Key</label>
                      <el-input v-model="authConfig.apiKey.key" placeholder="输入Key名称" />
                    </div>
                    <div class="config-row">
                      <label>Value</label>
                      <el-input v-model="authConfig.apiKey.value" placeholder="输入Key值" type="password" />
                    </div>
                    <div class="config-row">
                      <label>位置</label>
                      <el-select v-model="authConfig.apiKey.location" class="config-select">
                        <el-option label="Header" value="header" />
                        <el-option label="Query" value="query" />
                      </el-select>
                    </div>
                  </div>

                  <!-- Bearer Token配置 -->
                  <div v-if="authType === 'bearer'" class="auth-config">
                    <div class="config-header">Bearer Token 配置</div>
                    <div class="config-row">
                      <label>Token</label>
                      <el-input v-model="authConfig.bearer.token" placeholder="输入Bearer Token" type="password" />
                    </div>
                  </div>

                  <!-- JWT Bearer配置 -->
                  <div v-if="authType === 'jwt'" class="auth-config">
                    <div class="config-header">JWT Bearer 配置</div>
                    <div class="config-row">
                      <label>Token</label>
                      <el-input v-model="authConfig.jwt.token" placeholder="输入JWT Token" type="password" />
                    </div>
                    <div class="config-row">
                      <label>算法</label>
                      <el-select v-model="authConfig.jwt.algorithm" class="config-select">
                        <el-option label="HS256" value="HS256" />
                        <el-option label="HS384" value="HS384" />
                        <el-option label="HS512" value="HS512" />
                        <el-option label="RS256" value="RS256" />
                      </el-select>
                    </div>
                  </div>

                  <!-- 无需鉴权说明 -->
                  <div v-if="authType === 'none'" class="auth-tip">
                    <span>不使用任何鉴权</span>
                  </div>

                  <!-- 继承说明 -->
                  <div v-if="authType === 'inherit'" class="auth-tip">
                    <span>跟随父级目录的配置</span>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- ========== 新增 Cookie 标签页 ========== -->
          <el-tab-pane label="Cookie" name="cookie">
            <div class="tab-scroll-wrap">
              <div class="cookie-panel">
                <div class="cookie-header">
                  <span class="cookie-title">Cookie 管理</span>
                  <div class="cookie-actions">
                    <el-button size="small" @click="importCookieFromBrowser">从浏览器导入</el-button>
                    <el-button size="small" type="primary" @click="addCookie">添加 Cookie</el-button>
                  </div>
                </div>

                <div class="cookie-table">
                  <div class="cookie-row header">
                    <div class="cookie-col name">名称</div>
                    <div class="cookie-col value">值</div>
                    <div class="cookie-col domain">Domain</div>
                    <div class="cookie-col path">Path</div>
                    <div class="cookie-col expire">过期时间</div>
                    <div class="cookie-col actions">操作</div>
                  </div>

                  <div v-for="(c, i) in cookies" :key="c.id" class="cookie-row">
                    <div class="cookie-col name">
                      <el-input v-model="c.name" placeholder="Cookie 名称" size="small" />
                    </div>
                    <div class="cookie-col value">
                      <el-input v-model="c.value" placeholder="Cookie 值" size="small" />
                    </div>
                    <div class="cookie-col domain">
                      <el-input v-model="c.domain" placeholder="域名" size="small" />
                    </div>
                    <div class="cookie-col path">
                      <el-input v-model="c.path" placeholder="路径" size="small" />
                    </div>
                    <div class="cookie-col expire">
                      <el-date-picker v-model="c.expire" type="datetime" placeholder="过期时间" size="small"
                        format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DD HH:mm:ss" />
                    </div>
                    <div class="cookie-col actions">
                      <el-button type="text" size="small" @click="removeCookie(i)">删除</el-button>
                    </div>
                  </div>

                  <div v-if="cookies.length === 0" class="cookie-empty">
                    <el-icon :size="32" color="#c0c4cc">
                      <DocumentDelete />
                    </el-icon>
                    <p>暂无 Cookie，点击 "添加 Cookie" 按钮添加</p>
                  </div>
                </div>

                <div class="cookie-tip">
                  <el-alert title="Cookie 将自动添加到请求头的 Cookie 字段中" type="info" :closable="false" show-icon />
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="断言" name="assertion">
            <div class="tab-scroll-wrap">
              <div class="assertions-panel">
                <div class="assertions-header">
                  <span class="assertions-title">断言配置（验证响应是否符合预期）</span>
                </div>
                <div class="assertions-table">
                  <div class="assertions-table-header">
                    <div class="assert-th" style="flex:1.2">断言类型</div>
                    <div class="assert-th" style="flex:1">运算符</div>
                    <div class="assert-th" style="flex:1">表达式</div>
                    <div class="assert-th" style="flex:1.2">期望值</div>
                    <div class="assert-th" style="flex:0.4">操作</div>
                  </div>

                  <div v-if="assertions.length === 0" class="assertion-empty-state">
                    <el-icon :size="48" color="#c0c4cc">
                      <DocumentDelete />
                    </el-icon>
                    <p class="empty-text">暂无断言规则</p>
                    <el-button type="primary" size="small" @click="addAssertion">
                      添加断言
                    </el-button>
                  </div>

                  <div v-for="(c, i) in assertions" :key="c.id" class="assertion-row">
                    <el-input v-model="c.type" size="small" class="assertion-input" style="flex:1.2"
                      placeholder="如: STATUS / HEADER / BODY / JSON_PATH / REGEX" />
                    <el-select v-model="c.operator" size="small" class="assertion-select" style="flex:1" placeholder="运算符">
                      <el-option v-for="opt in assertionOperatorOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                    </el-select>
                    <el-input v-model="c.expression" size="small" class="assertion-input" style="flex:1"
                      :placeholder="c.type === 'JSON_PATH' ? '如: $.data.name' : c.type === 'HEADER' ? '响应头字段名' : c.type === 'REGEX' ? '正则表达式' : '可选'" />
                    <el-input v-model="c.value" size="small" class="assertion-input" style="flex:1.2" placeholder="期望值" />
                    <el-button type="text" size="small" class="assertion-delete" style="flex:0.4"
                      @click="removeAssertion(i)">删除</el-button>
                  </div>

                  <div class="assertions-footer">
                    <el-button type="primary" size="small" @click="addAssertion">
                      添加断言
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>

        <!-- 返回响应区域：独立滚动 -->
        <div class="response-area">
          <div class="response-top">
            <div>返回响应</div>
            <div class="response-controls">
              <span>状态码</span>
              <el-input v-model="actualStatusCode" size="small" style="width:100px; margin-left:8px;color:var(--ghost)" disabled
                placeholder="请求后自动填充" />
            </div>
          </div>
          <div class="response-scroll-box">
            <div class="response-content">
              <pre v-if="lastRecord">{{ formatJson(lastRecord.responseSnapshot || lastRecord) }}</pre>
              <div v-else class="empty">点击"发送"按钮获取返回结果</div>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 保存用例对话框 -->
    <el-dialog 
      v-model="saveDialogVisible" 
      title="保存用例" 
      width="500px"
      @close="handleSaveDialogClose"
    >
      <el-form label-width="100px">
        <el-form-item label="选择应用">
          <el-tree-select
            v-model="selectedApplicationId" 
            placeholder="请选择应用" 
            :data="departmentTree"
            filterable
            :render-after-expand="false"
            check-strictly
            :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
            style="width: 100%"
            @change="checkDuplicateCase"
          />
        </el-form-item>

        <!-- 重复检查结果提示 -->
        <el-alert 
          v-if="duplicateCheckResult && duplicateCheckResult.isDuplicate"
          type="warning"
          :closable="false"
          show-icon
        >
          <template #title>
            <div>
              <p>发现重复用例：<strong>{{ duplicateCheckResult.duplicateCaseName }}</strong></p>
              <p style="margin-top: 8px; font-size: 12px; color: var(--ghost);">
                该应用下已存在相同地址的用例，您可以选择覆盖更新或修改参数后保存为新用例。
              </p>
            </div>
          </template>
        </el-alert>

        <el-alert 
          v-else-if="duplicateCheckResult && !duplicateCheckResult.isDuplicate"
          type="success"
          :closable="false"
          show-icon
        >
          <template #title>
            <div>
              <p>未发现重复用例</p>
              <p style="margin-top: 8px; font-size: 12px; color: var(--ghost);">
                可以安全地保存为新用例。
              </p>
            </div>
          </template>
        </el-alert>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="handleSaveDialogClose">取消</el-button>
          
          <!-- 保存按钮 -->
          <el-tooltip 
            :content="canSave ? '创建新用例' : '存在重复用例，请选择覆盖'" 
            placement="top"
          >
            <span>
              <el-button 
                type="primary" 
                @click="doSave(false)"
                :loading="saveLoading"
                :disabled="!canSave || !selectedApplicationId"
              >
                保存为新用例
              </el-button>
            </span>
          </el-tooltip>
          
          <!-- 覆盖按钮 -->
          <el-tooltip 
            :content="canOverwrite ? '覆盖已有的重复用例' : '未发现重复用例，无需覆盖'" 
            placement="top"
          >
            <span>
              <el-button 
                type="warning" 
                @click="doSave(true)"
                :loading="saveLoading"
                :disabled="!canOverwrite || !selectedApplicationId"
              >
                覆盖更新
              </el-button>
            </span>
          </el-tooltip>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 导入 Vue 核心响应式 API
import { onMounted, reactive, ref } from 'vue'
// 导入路由
import { useRouter, useRoute } from 'vue-router'
// 导入 Element Plus 消息组件
import { ElMessage } from 'element-plus'
// 导入 Element Plus 图标
import { ArrowRight, DocumentDelete, Check } from '@element-plus/icons-vue'
// 导入子组件
import ApiBody from "./ApiBody.vue"
// 导入 API 接口
import { createHttpCase, getHttpCaseList, getHttpCase, updateHttpCase, deleteHttpCase, batchDeleteHttpCases, debugHttpCase, checkDuplicate, getApplicationsByDept, directTest, getDepartmentTree } from '../../api/admin'

const route = useRoute()

// 加载编辑数据（从路由参数或后端获取）
const loadEditData = async () => {
  // 从 Apihub / 原服务页面 调试入口进入：用服务信息回填地址/方法，并从 sessionStorage 读取完整调试数据
  if (route.query.from === 'apihub' || route.query.from === 'originalService') {
    isFromApiHub.value = true
    form.appName = decodeURIComponent(route.query.serviceName || '')
    form.url = decodeURIComponent(route.query.servicePath || '')
    form.method = (route.query.method || 'GET').toUpperCase()
    if (route.query.applicationId) {
      currentApplicationId.value = route.query.applicationId
    }
    // 清空 caseId，避免误认为是编辑已有用例
    caseId.value = ''
    originalCaseId.value = ''

    // 从 sessionStorage 读取完整调试数据（headers/body/assertions 等）
    try {
      const payloadStr = sessionStorage.getItem('apihub_debug_payload')
      if (payloadStr) {
        const payload = JSON.parse(payloadStr)
        console.log('[ApiTester] 读取调试数据:', payload)

        // 回填超时
        if (payload.timeout) {
          form.timeout = payload.timeout
        }

        // 回填请求头
        if (payload.requestHeaders) {
          let headersObj = payload.requestHeaders
          if (typeof headersObj === 'string') {
            try { headersObj = JSON.parse(headersObj) } catch (e) { headersObj = {} }
          }
          if (headersObj && typeof headersObj === 'object' && !Array.isArray(headersObj) && Object.keys(headersObj).length > 0) {
            headers.value = Object.keys(headersObj).map(key => ({
              id: `h_${Date.now()}_${Math.random()}`,
              name: key,
              value: String(headersObj[key]),
              type: 'string',
              desc: ''
            }))
          }
        }
        if (headers.value.length === 0) {
          headers.value = [{ id: `h_${Date.now()}`, name: '', value: '', type: 'string', desc: '' }]
        }

        // 回填请求体
        if (payload.requestBody) {
          form.paramsJson = typeof payload.requestBody === 'object'
            ? JSON.stringify(payload.requestBody, null, 2)
            : payload.requestBody
          form.bodyType = 'json'
        }

        // 回填断言规则
        if (payload.assertions) {
          let assertList = payload.assertions
          if (typeof assertList === 'string') {
            try { assertList = JSON.parse(assertList) } catch (e) { assertList = [] }
          }
          if (Array.isArray(assertList) && assertList.length > 0) {
            assertions.value = assertList.map(a => ({
              id: `a_${Date.now()}_${Math.random()}`,
              mode: (a.type === 'JSON_PATH' || a.type === 'JSONPATH') ? 'hierarchy' : 'normal',
              type: a.type || 'STATUS_CODE',
              expression: a.expression || '',
              operator: a.operator || 'EQUALS',
              value: a.expectedValue || a.value || ''
            }))
          } else {
            assertions.value = []
          }
        }

        // 读取完成后清除 sessionStorage，避免污染下次进入
        sessionStorage.removeItem('apihub_debug_payload')
      }
    } catch (e) {
      console.warn('[ApiTester] 读取调试数据失败', e)
    }
    return
  }

  // 优先检查是否有caseId参数（从ApiHub进入）
  const caseIdFromQuery = route.query.caseId
  const isFromApiHubFromQuery = route.query.isFromApiHub === 'true'
  
  if (caseIdFromQuery) {
    console.log('✓ 从ApiHub进入，用例ID:', caseIdFromQuery)
    
    try {
      // 调用后端接口获取用例详情
      const res = await getHttpCase(caseIdFromQuery)
      console.log('获取到用例详情原始响应：', res)
      
      // 提取实际用例数据（兼容 {code, data, message} 和直接返回两种格式）
      const response = (res.data && typeof res.data === 'object' && res.data.id) ? res.data : res
      console.log('提取的用例数据：', response)
      
      // 设置标记
      isFromApiHub.value = true
      
      // 填充表单数据
      caseId.value = response.id || caseIdFromQuery
      originalCaseId.value = response.id || caseIdFromQuery
      currentApplicationId.value = response.applicationId || ''
      
      form.appName = response.name || ''
      form.url = response.address || ''
      form.method = response.requestType || 'GET'
      form.timeout = response.timeout || 30000
      
      // 填充请求头（处理字符串或对象格式）
      headers.value = []
      if (response.requestHeaders) {
        let headersObj = response.requestHeaders
        // 如果是字符串，尝试解析为对象
        if (typeof headersObj === 'string') {
          try {
            headersObj = JSON.parse(headersObj)
          } catch (e) {
            console.warn('requestHeaders 不是有效的JSON字符串，跳过解析', e)
            headersObj = {}
          }
        }
        // 如果是对象且有键值对，则填充
        if (headersObj && typeof headersObj === 'object' && !Array.isArray(headersObj) && Object.keys(headersObj).length > 0) {
          headers.value = Object.keys(headersObj).map(key => ({
            id: `h_${Date.now()}_${Math.random()}`,
            name: key,
            value: String(headersObj[key]),
            type: 'string',
            desc: ''
          }))
        }
      }
      // 如果没有请求头，添加一个空行
      if (headers.value.length === 0) {
        headers.value.push({ id: `h_${Date.now()}`, name: '', value: '', type: 'string', desc: '' })
      }
      
      // 填充请求参数（处理字符串或对象格式）
      params.value = []
      if (response.requestParams) {
        let paramsObj = response.requestParams
        // 如果是字符串，尝试解析为对象
        if (typeof paramsObj === 'string') {
          try {
            paramsObj = JSON.parse(paramsObj)
          } catch (e) {
            console.warn('requestParams 不是有效的JSON字符串，跳过解析', e)
            paramsObj = {}
          }
        }
        // 如果是对象且有键值对，则填充
        if (paramsObj && typeof paramsObj === 'object' && !Array.isArray(paramsObj) && Object.keys(paramsObj).length > 0) {
          params.value = Object.keys(paramsObj).map(key => {
            const value = paramsObj[key]
            let type = 'string'
            if (typeof value === 'number') {
              type = Number.isInteger(value) ? 'integer' : 'number'
            } else if (typeof value === 'boolean') {
              type = 'boolean'
            }
            return {
              id: `p_${Date.now()}_${Math.random()}`,
              name: key,
              value: String(value),
              type,
              desc: ''
            }
          })
        }
      }
      // 如果没有请求参数，添加一个空行
      if (params.value.length === 0) {
        params.value.push({ id: `p_${Date.now()}`, name: '', value: '', type: 'string', desc: '' })
      }
      
      // 填充请求体
      if (response.requestBody) {
        form.paramsJson = typeof response.requestBody === 'object'
          ? JSON.stringify(response.requestBody, null, 2)
          : response.requestBody
        form.bodyType = 'json'
      }
      
      // 填充断言规则
      if (response.responseAssertions && Array.isArray(response.responseAssertions) && response.responseAssertions.length > 0) {
        assertions.value = response.responseAssertions.map(a => ({
          id: `a_${Date.now()}_${Math.random()}`,
          mode: a.type === 'JSON_PATH' ? 'hierarchy' : 'normal',
          type: a.type || 'STATUS_CODE',
          expression: a.expression || '',
          operator: a.operator || 'EQUALS',
          value: a.expectedValue || ''
        }))
      } else {
        assertions.value = []
      }
      
      ElMessage.success('已加载用例数据')
    } catch (error) {
      console.error('加载用例详情失败：', error)
      const errorMsg = error.response?.data?.message || error.message || '加载用例详情失败'
      ElMessage.error(errorMsg)
    }
    return
  }
  
  // 兼容旧的editData参数方式（保留向后兼容）
  const editDataStr = route.query.editData
  if (editDataStr) {
    try {
      const editData = JSON.parse(editDataStr)
      console.log('接收到编辑数据（旧方式）：', editData)

      // 判断是否从ApiHub进入
      isFromApiHub.value = editData.isFromApiHub || false
      if (isFromApiHub.value) {
        console.log('✓ 从ApiHub进入（旧方式），将显示保存对话框')
      }

      // 填充表单
      form.appName = editData.name || ''
      form.url = editData.address || ''
      form.method = editData.requestType || 'GET'
      caseId.value = editData.id || ''
      
      // 保存原始用例ID（用于重复检查时排除自己）
      originalCaseId.value = editData.id || ''
      
      // 保存应用ID（用于重复检查）
      currentApplicationId.value = editData.applicationId || ''

      // 填充请求头
      if (editData.requestHeaders) {
        let requestHeadersObj = editData.requestHeaders
        
        // 如果是字符串，尝试解析为对象
        if (typeof requestHeadersObj === 'string') {
          try {
            requestHeadersObj = JSON.parse(requestHeadersObj)
          } catch (e) {
            console.warn('requestHeaders 不是有效的JSON字符串，跳过解析', e)
            requestHeadersObj = {}
          }
        }
        
        // 如果是对象且有键值对，则填充
        if (requestHeadersObj && typeof requestHeadersObj === 'object' && Object.keys(requestHeadersObj).length > 0) {
          headers.value = Object.keys(requestHeadersObj).map(key => ({
            id: `h_${Date.now()}`,
            name: key,
            value: String(requestHeadersObj[key]),
            type: 'string',
            desc: ''
          }))
        } else {
          headers.value = [{ id: `h_${Date.now()}`, name: '', value: '', type: 'string', desc: '' }]
        }
      }

      // 填充请求参数
      if (editData.requestParams) {
        let requestParamsObj = editData.requestParams
        
        // 如果是字符串，尝试解析为对象
        if (typeof requestParamsObj === 'string') {
          try {
            requestParamsObj = JSON.parse(requestParamsObj)
          } catch (e) {
            console.warn('requestParams 不是有效的JSON字符串，跳过解析', e)
            requestParamsObj = {}
          }
        }
        
        // 如果是对象且有键值对，则填充
        if (requestParamsObj && typeof requestParamsObj === 'object' && Object.keys(requestParamsObj).length > 0) {
          params.value = Object.keys(requestParamsObj).map(key => ({
            id: `p_${Date.now()}`,
            name: key,
            value: String(requestParamsObj[key]),
            type: typeof requestParamsObj[key] === 'number' ? 'number' : 'string',
            desc: ''
          }))
        } else {
          // 如果没有参数，初始化为空数组
          params.value = []
        }
      }

      // 填充请求体
      if (editData.requestBody) {
        form.paramsJson = editData.requestBody
        form.bodyType = 'json'
      }

      // 填充超时时间
      if (editData.timeout) {
        form.timeout = editData.timeout
      }

      ElMessage.success('已加载用例数据')
    } catch (error) {
      console.error('解析编辑数据失败：', error)
      ElMessage.error('解析用例数据失败')
    }
  }
}

// 搜索关键词
// 本地存储键名
const STORAGE_KEY_API_TEST_RECORDS = 'api_test_records'



// ========== 表单数据 ==========
const form = reactive({
  token: '',                    // 令牌
  url: '',                     // 接口地址
  method: 'POST',              // 请求方法
  appName: '',                 // 应用名称
  paramsJson: '{\n  "key": "value"\n}', // 请求参数 JSON
  bodyType: 'none',            // 请求体类型
  outputJson: '{\n  "code": 0,\n  "data": {}\n}', // 期望输出 JSON
  headersRaw: '',              // 请求头原始内容
  assertionsRaw: '',           // 断言原始内容
  includeAutomation: true,     // 是否包含自动化
})

// ========== 状态变量 ==========
const caseId = ref('')              // 当前编辑的用例ID
const originalCaseId = ref('')      // 原始用例ID（用于重复检查时排除自己）
const isFromApiHub = ref(false)     // 是否从ApiHub进入
const currentApplicationId = ref('') // 当前应用ID
const actualStatusCode = ref('')    // 实际状态码
const isSending = ref(false)        // 请求发送中状态
const lastRecord = ref(null)        // 最后一条测试记录
const router = useRouter()          // 路由实例
const currentUser = reactive({ username: 'guest', userType: '1' }) // 当前用户信息
const records = ref([])             // 测试记录列表

// ========== 保存对话框相关 ==========
const saveDialogVisible = ref(false) // 保存对话框显示状态
const applicationList = ref([])      // 应用列表（兼容旧逻辑）
const departmentTree = ref([])       // 部门-应用树形数据
const selectedApplicationId = ref('') // 选中的应用ID
const duplicateCheckResult = ref(null) // 重复检查结果
const canSave = ref(true)            // 是否可以保存
const canOverwrite = ref(false)      // 是否可以覆盖
const saveLoading = ref(false)       // 保存加载状态

// ========== 保存对话框相关函数 ==========
// 加载部门-应用树形数据
const loadApplicationList = async () => {
  try {
    console.log('开始获取部门-应用树')
    const res = await getDepartmentTree()
    console.log('部门-应用树响应:', res)
    
    // 提取树数据（兼容 {code, data} 和直接返回两种格式）
    let treeRaw = []
    if (Array.isArray(res)) {
      treeRaw = res
    } else if (Array.isArray(res.data)) {
      treeRaw = res.data
    } else if (res.data && Array.isArray(res.data.list)) {
      treeRaw = res.data.list
    }
    
    // 构造成 el-tree-select 所需的树形结构
    // 后端格式可能是: { id, name, children: [{ id, name }] } 其中 children 是应用
    departmentTree.value = treeRaw.map(dept => ({
      label: dept.name || dept.departmentName || `部门${dept.id}`,
      value: `dept_${dept.id}`,  // 部门节点 value（不可选）
      disabled: true,
      children: (dept.children || dept.applications || []).map(app => ({
        label: app.name || app.appName || `应用${app.id}`,
        value: app.id,
        disabled: false
      }))
    }))
    
    // 同时维护扁平应用列表（兼容旧逻辑）
    const allApps = []
    treeRaw.forEach(dept => {
      const apps = dept.children || dept.applications || []
      apps.forEach(app => allApps.push(app))
    })
    applicationList.value = allApps
    
    console.log('部门-应用树:', departmentTree.value)
    console.log('扁平应用列表:', applicationList.value)
    
    if (allApps.length === 0) {
      ElMessage.warning('暂无可用应用')
    }
  } catch (error) {
    console.error('获取部门-应用树失败', error)
    const errorMsg = error.response?.data?.message || error.message || '获取应用列表失败'
    ElMessage.error(errorMsg)
  }
}

// 检查重复用例（应用下拉变化时触发）
const checkDuplicateCase = async () => {
  await performDuplicateCheck()
}

// 关闭保存对话框
const handleSaveDialogClose = () => {
  saveDialogVisible.value = false
  selectedApplicationId.value = ''
  duplicateCheckResult.value = null
  canSave.value = true
  canOverwrite.value = false
}

// ========== 请求参数相关 ==========
// URL 查询参数列表
const params = ref([
  { id: `p_${Date.now()}`, name: '', value: '', type: 'string', desc: '' }
])
// 请求头列表
const headers = ref([
  { id: `h_${Date.now()}`, name: '', value: '', type: 'string', desc: '' }
])
// 当前激活的标签页
const activeTab = ref('params')

// ========== Cookie 相关 ==========
const cookies = ref([
  {
    id: `c_${Date.now()}`,
    name: '',
    value: '',
    domain: '',
    path: '/',
    expire: ''
  }
])



// 添加 Cookie
const addCookie = () => {
  cookies.value.push({
    id: `c_${Date.now()}`,
    name: '',
    value: '',
    domain: '',
    path: '/',
    expire: ''
  })
}


// 删除 Cookie
const removeCookie = (i) => {
  cookies.value.splice(i, 1)
}

// 从浏览器导入 Cookie（模拟功能）
const importCookieFromBrowser = () => {
  // 这里可以实现从浏览器 document.cookie 导入
  // 也可以弹窗让用户手动输入
  ElMessage.info('从浏览器导入功能开发中，请手动填写 Cookie')
}

// 构建 Cookie 字符串
const buildCookieString = () => {
  return cookies.value
    .filter(c => c.name.trim() && c.value.trim())
    .map(c => `${c.name.trim()}=${c.value.trim()}`)
    .join('; ')
}

// ========== 认证相关 ==========
// 鉴权方式
const authType = ref('inherit') // inherit, none, apiKey, bearer, jwt
// 认证配置
const authConfig = reactive({
  apiKey: {
    key: '',
    value: '',
    location: 'header' // header, query
  },
  bearer: {
    token: ''
  },
  jwt: {
    token: '',
    algorithm: 'HS256'
  }
})

// 常用请求头列表（用于自动补全）
const commonHeaders = [
  'Accept', 'Accept-Charset', 'Accept-Encoding', 'Accept-Language',
  'Access-Control-Request-Method', 'Access-Control-Request-Headers',
  'Authorization', 'Cache-Control', 'Content-Length', 'Content-Type',
  'Content-MD5', 'Content-Transfer-Encoding', 'Content-Language',
  'Date', 'Expect', 'From', 'Host', 'If-Match', 'If-Modified-Since',
  'If-None-Match', 'If-Unmodified-Since', 'Max-Forwards', 'Origin',
  'Pragma', 'Proxy-Authorization', 'Range', 'Referer', 'TE', 'User-Agent',
  'Upgrade', 'Via', 'Warning', 'Connection', 'Cookie'
]

// 搜索请求头（用于自动补全）
const searchHeaders = (queryString, callback) => {
  const results = commonHeaders.filter(h =>
    h.toLowerCase().includes(queryString.toLowerCase())
  )
  callback(results.map(h => ({ value: h, label: h })))
}

// 添加请求头
const addHeader = () => {
  headers.value.push({ id: `h_${Date.now()}`, name: '', value: '', type: 'string', desc: '' })
}

// 删除请求头
const removeHeader = (i) => {
  headers.value.splice(i, 1)
}

// 断言运算符选项
const assertionOperatorOptions = [
  { label: '等于 (EQUALS)', value: 'EQUALS' },
  { label: '包含 (CONTAINS)', value: 'CONTAINS' },
  { label: '正则匹配 (MATCHES)', value: 'MATCHES' },
  { label: '大于 (GT)', value: 'GT' },
  { label: '小于 (LT)', value: 'LT' },
  { label: '大于等于 (GTE)', value: 'GTE' },
  { label: '小于等于 (LTE)', value: 'LTE' }
]

// 断言列表
const assertions = ref([])

// 添加断言
const addAssertion = () => {
  assertions.value.push({
    id: `a_${Date.now()}`,
    type: '',
    operator: 'EQUALS',
    expression: '',
    value: ''
  })
}

// 删除断言
const removeAssertion = (i) => {
  // 允许删除所有断言，包括最后一个
  assertions.value.splice(i, 1)
}

// ========== 验证相关 ==========
const expectedStatus = ref(200)      // 期望状态码

// 添加查询参数
const addParam = () => {
  params.value.push({ id: `p_${Date.now()}`, name: '', value: '', type: 'string', desc: '' })
}

// 删除查询参数
const removeParam = (i) => {
  params.value.splice(i, 1)
}

// ========== 用例操作 ==========
// 保存请求用例（创建或更新）
const saveRequest = async () => {
  if (!form.url.trim()) {
    ElMessage.warning('请输入接口地址')
    return
  }

  // 重置对话框状态
  selectedApplicationId.value = currentApplicationId.value || ''
  duplicateCheckResult.value = null
  canSave.value = true
  canOverwrite.value = false

  // 加载应用列表（获取用户所属部门下的应用）
  await loadApplicationList()

  // 如果有选中的应用，先做重复校验
  if (selectedApplicationId.value) {
    await performDuplicateCheck()
  }

  // 显示保存对话框
  saveDialogVisible.value = true
}

// 执行重复校验
const performDuplicateCheck = async () => {
  if (!selectedApplicationId.value) return

  try {
    // excludeId 必须是 String 类型（后端接口要求）
    const checkData = {
      applicationId: selectedApplicationId.value,
      address: form.url.trim(),
      protocolType: 'HTTP',
      excludeId: originalCaseId.value ? String(originalCaseId.value) : undefined
    }

    console.log('重复检查参数:', checkData)
    const res = await checkDuplicate(checkData)
    console.log('重复检查结果:', res)

    duplicateCheckResult.value = res.data || res

    if (duplicateCheckResult.value.isDuplicate) {
      canSave.value = false
      canOverwrite.value = true
    } else {
      canSave.value = true
      canOverwrite.value = false
    }
  } catch (error) {
    console.error('重复检查失败:', error)
    // 校验失败不阻断保存流程，默认允许保存
    ElMessage.warning('重复校验接口异常，可直接保存')
    duplicateCheckResult.value = null
    canSave.value = true
    canOverwrite.value = false
  }
}

// 执行实际保存操作
const doSave = async (isOverwrite = false) => {
  // ===== 新增接口必填字段验证 =====
  if (!isOverwrite) {
    if (!form.appName.trim()) {
      ElMessage.warning('请填写用例名称')
      return
    }
    if (!form.url.trim()) {
      ElMessage.warning('请填写请求地址')
      return
    }
    if (!form.method) {
      ElMessage.warning('请选择请求类型')
      return
    }
    const hasSnapshot = lastRecord.value?.responseSnapshot
    if (!hasSnapshot) {
      ElMessage.warning('响应结果不能为空，请先发送请求获取响应')
      return
    }
    if (!selectedApplicationId.value) {
      ElMessage.warning('请选择所属应用')
      return
    }
    // 断言不能为空：至少有一条有效断言
    const validAssertions = assertions.value.filter(a => {
      if (a.mode === 'hierarchy') {
        return a.type && a.operator && a.expression && a.value
      }
      return a.type && a.operator && a.value
    })
    if (validAssertions.length === 0) {
      ElMessage.warning('断言不能为空，请至少添加一条有效断言')
      return
    }
  }

  // ===== 更新接口必填字段验证 =====
  if (isOverwrite) {
    if (!duplicateCheckResult.value?.duplicateCaseId) {
      ElMessage.warning('用例ID不能为空，未发现可覆盖的重复用例')
      return
    }
  }

  saveLoading.value = true
  
  try {
    // 构建请求头对象
    const requestHeaders = {}
    headers.value.forEach(h => {
      if (h.name.trim()) {
        requestHeaders[h.name.trim()] = h.value.trim()
      }
    })

    // 构建请求参数对象
    const requestParams = {}
    params.value.forEach(p => {
      if (p.name.trim()) {
        let value = p.value
        if (p.type === 'number' || p.type === 'integer') {
          value = Number(p.value) || 0
        } else if (p.type === 'boolean') {
          value = p.value === 'true' || p.value === true
        }
        requestParams[p.name.trim()] = value
      }
    })

    // 构建断言规则列表（普通 + 层级，统一映射为后端 AssertionRuleDTO 格式）
    const responseAssertions = assertions.value
      .filter(a => {
        if (a.mode === 'hierarchy') {
          return a.type && a.operator && a.expression && a.value
        }
        return a.type && a.operator && a.value
      })
      .map(a => ({
        type: a.type || 'JSON_PATH',
        description: a.description || '',
        expression: a.expression,
        expectedValue: a.value,
        operator: a.operator || 'EQUALS'
      }))

    // 构建响应体（后端要求必须传）
    let responseBody = null
    if (lastRecord.value?.responseSnapshot) {
      const snap = lastRecord.value.responseSnapshot
      responseBody = typeof snap === 'string' ? snap : JSON.stringify(snap)
    } else {
      responseBody = '{}'
    }

    let response

    if (isOverwrite && duplicateCheckResult.value?.duplicateCaseId) {
      // ===== 覆盖模式：构建 HttpCaseUpdateDTO（字段结构与新增完全不同） =====
      const headersObj = {}
      headers.value.forEach(h => {
        if (h.name.trim()) headersObj[h.name.trim()] = h.value.trim()
      })

      const updateAssertionRules = assertions.value
        .filter(a => {
          if (a.mode === 'hierarchy') {
            return a.type && a.operator && a.expression && a.value
          }
          return a.type && a.operator && a.value
        })
        .map(a => ({
          type: a.type || 'JSON_PATH',
          description: a.description || '',
          expression: a.expression,
          expectedValue: a.value,
          operator: a.operator || 'EQUALS'
        }))

      const updateData = {
        id: duplicateCheckResult.value.duplicateCaseId,
        name: form.appName.trim() || undefined,
        url: form.url.trim(),
        method: form.method,
        headers: Object.keys(headersObj).length > 0 ? JSON.stringify(headersObj) : undefined,
        body: form.bodyType !== 'none' && form.paramsJson && form.paramsJson.trim() ? form.paramsJson.trim() : undefined,
        timeout: form.timeout || 30000,
        assertionRules: updateAssertionRules.length > 0 ? updateAssertionRules : undefined
      }

      response = await updateHttpCase(duplicateCheckResult.value.duplicateCaseId, updateData)
      ElMessage.success('用例覆盖更新成功')

    } else {
      // ===== 新增模式：构建 HttpCaseCreateDTO =====
      const caseData = {
        name: form.appName.trim() || form.url.split('/').pop() || '未命名用例',
        address: form.url.trim(),
        requestType: form.method,
        requestHeaders: Object.keys(requestHeaders).length > 0 ? requestHeaders : undefined,
        requestParams: Object.keys(requestParams).length > 0 ? requestParams : undefined,
        requestBody: form.bodyType !== 'none' && form.paramsJson && form.paramsJson.trim() ? form.paramsJson.trim() : undefined,
        responseBody: responseBody,
        responseAssertions: responseAssertions.length > 0 ? responseAssertions : undefined,
        responseFormat: 'JSON',
        applicationId: selectedApplicationId.value || 1,
        businessDomainId: 0,
        timeout: form.timeout || 30000,
        env: 'default'
      }

      response = await createHttpCase(caseData)
      // 提取新建用例ID
      const newId = response?.data || response?.caseId || response?.id
      if (newId && typeof newId === 'string') {
        caseId.value = newId
      } else if (newId && typeof newId === 'object') {
        caseId.value = newId.caseId || newId.id || ''
      }
      ElMessage.success('用例创建成功')
    }
    
    console.log('操作结果:', response)
    
    // 关闭对话框
    if (saveDialogVisible.value) {
      saveDialogVisible.value = false
    }
  } catch (error) {
    console.error('保存失败:', error)
    const errorMsg = error.response?.data?.message || error.message || '操作失败'
    ElMessage.error(errorMsg)
  } finally {
    saveLoading.value = false
  }
}



// 跳转上一页（从 Apihub 进入则返回 Apihub）
const goHome = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/layout/home')
  }
}

// 加载用例详情
const loadHttpCase = async (id) => {
  try {
    const res = await getHttpCase(id)
    // 提取实际用例数据（兼容 {code, data, message} 和直接返回两种格式）
    const response = (res.data && typeof res.data === 'object' && res.data.id) ? res.data : res
    // 填充表单数据
    caseId.value = id
    form.appName = response.name || ''
    form.url = response.address || ''
    form.method = response.requestType || 'POST'

    // 填充请求头（处理字符串或对象格式）
    headers.value = []
    if (response.requestHeaders) {
      let headersObj = response.requestHeaders
      if (typeof headersObj === 'string') {
        try { headersObj = JSON.parse(headersObj) } catch (e) { headersObj = {} }
      }
      if (headersObj && typeof headersObj === 'object' && !Array.isArray(headersObj) && Object.keys(headersObj).length > 0) {
        Object.keys(headersObj).forEach(key => {
          headers.value.push({
            id: `h_${Date.now()}`,
            name: key,
            value: String(headersObj[key]),
            type: 'string',
            desc: ''
          })
        })
      }
    }
    if (headers.value.length === 0) {
      headers.value.push({ id: `h_${Date.now()}`, name: '', value: '', type: 'string', desc: '' })
    }

    // 填充查询参数（处理字符串或对象格式）
    params.value = []
    if (response.requestParams) {
      let paramsObj = response.requestParams
      if (typeof paramsObj === 'string') {
        try { paramsObj = JSON.parse(paramsObj) } catch (e) { paramsObj = {} }
      }
      if (paramsObj && typeof paramsObj === 'object' && !Array.isArray(paramsObj) && Object.keys(paramsObj).length > 0) {
        Object.keys(paramsObj).forEach(key => {
          const value = paramsObj[key]
          let type = 'string'
          if (typeof value === 'number') {
            type = Number.isInteger(value) ? 'integer' : 'number'
          } else if (typeof value === 'boolean') {
            type = 'boolean'
          }
          params.value.push({
            id: `p_${Date.now()}`,
            name: key,
            value: String(value),
            type,
            desc: ''
          })
        })
      }
    }
    if (params.value.length === 0) {
      params.value.push({ id: `p_${Date.now()}`, name: '', value: '', type: 'string', desc: '' })
    }

    // 填充请求体
    if (response.requestBody) {
      form.paramsJson = typeof response.requestBody === 'object'
        ? JSON.stringify(response.requestBody, null, 2)
        : response.requestBody
      form.bodyType = 'json'
    }

    // 填充断言规则
    if (response.responseAssertions && Array.isArray(response.responseAssertions) && response.responseAssertions.length > 0) {
      assertions.value = response.responseAssertions.map(a => ({
        id: `a_${Date.now()}_${Math.random()}`,
        mode: a.type === 'JSON_PATH' ? 'hierarchy' : 'normal',
        type: a.type || '',
        expression: a.expression || '',
        operator: a.operator || 'EQUALS',
        value: a.expectedValue || ''
      }))
    } else {
      assertions.value = []
    }

    ElMessage.success('用例加载成功')
  } catch (error) {
    ElMessage.error('加载用例失败: ' + (error.msg || error.message))
  }
}

// 删除单个用例
const deleteHttpCaseById = async (id) => {
  try {
    await deleteHttpCase(id)
    caseId.value = ''
    // 重置表单
    resetForm()
    headers.value = [{ id: `h_${Date.now()}`, name: '', value: '', type: 'string', desc: '' }]
    params.value = [{ id: `p_${Date.now()}`, name: '', value: '', type: 'string', desc: '' }]
    ElMessage.success('用例删除成功')
  } catch (error) {
    ElMessage.error('删除用例失败: ' + (error.msg || error.message))
  }
}

// 批量删除用例
const batchDeleteCases = async (ids) => {
  try {
    await batchDeleteHttpCases(ids)
    ElMessage.success('批量删除成功')
  } catch (error) {
    ElMessage.error('批量删除失败: ' + (error.msg || error.message))
  }
}

// ========== 数据加载 ==========
// 加载用户信息
const loadUserInfo = () => {
  try {
    const raw = localStorage.getItem('userInfo')
    if (raw) {
      const info = JSON.parse(raw)
      currentUser.username = info.username || info.name || 'guest'
      currentUser.userType = String(info.userType || info.userType || '1')
    }
  } catch {
    currentUser.username = 'guest'
    currentUser.userType = '1'
  }
}

// 加载测试记录
const loadRecords = () => {
  try {
    const saved = localStorage.getItem(STORAGE_KEY_API_TEST_RECORDS)
    records.value = saved ? JSON.parse(saved) : []
  } catch {
    records.value = []
  }
}

// 保存测试记录到本地存储
const saveRecords = () => {
  localStorage.setItem(STORAGE_KEY_API_TEST_RECORDS, JSON.stringify(records.value))
}

// ========== 工具函数 ==========
// 解析 JSON 字符串
const parseJson = (value, label) => {
  const raw = value.trim()
  if (!raw) {
    return {}
  }
  try {
    const parsed = JSON.parse(raw)
    if (typeof parsed === 'object' && parsed !== null) {
      return parsed
    }
    ElMessage.warning(`${label} 必须为对象或数组`)
    return null
  } catch {
    ElMessage.warning(`${label} 格式不正确，请检查`)
    return null
  }
}

// 解析入参 JSON
const parseParamsJson = () => parseJson(form.paramsJson, '入参 JSON')
// 解析出参 JSON
const parseOutputJson = () => parseJson(form.outputJson, '出参 JSON')

// 构建查询字符串
const buildQueryString = (params) => {
  return Object.entries(params)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&')
}

// 获取完整请求 URL（包含查询参数）
const getRequestUrl = (params) => {
  if (form.method === 'GET' && params && Object.keys(params).length) {
    return `${form.url}${form.url.includes('?') ? '&' : '?'}${buildQueryString(params)}`
  }
  return form.url
}

// 格式化 JSON 字符串（美化输出）
const formatJson = (value) => {
  try {
    return JSON.stringify(typeof value === 'string' ? JSON.parse(value) : value, null, 2)
  } catch {
    return typeof value === 'string' ? value : JSON.stringify(value)
  }
}

// 重置表单
const resetForm = () => {
  form.token = ''
  form.url = ''
  form.method = 'POST'
  form.appName = ''
  form.paramsJson = '{\n  "key": "value"\n}'
  form.outputJson = '{\n  "code": 0,\n  "data": {}\n}'
  form.includeAutomation = true
}

// ========== 请求执行 ==========
// 发送请求（统一调用直接测试接口 POST /test/direct）
const sendRequest = async () => {
  actualStatusCode.value = ''
  lastRecord.value = null

  if (!form.url.trim()) {
    ElMessage.warning('请输入接口地址')
    return
  }

  isSending.value = true
  const startTime = Date.now()

  try {
    // 构建请求头对象
    const requestHeaders = {}
    headers.value.forEach(h => {
      if (h.name.trim()) {
        requestHeaders[h.name.trim()] = h.value.trim()
      }
    })

    // 添加 Cookie
    const cookieString = buildCookieString()
    if (cookieString) {
      requestHeaders.Cookie = cookieString
    }

    // 构建请求参数
    let requestParams = undefined
    if (form.method === 'GET') {
      // GET 请求参数放 URL
      requestParams = {}
      params.value.forEach(p => {
        if (p.name.trim()) {
          let value = p.value
          if (p.type === 'number' || p.type === 'integer') value = Number(p.value) || 0
          else if (p.type === 'boolean') value = p.value === 'true'
          requestParams[p.name.trim()] = value
        }
      })
      if (Object.keys(requestParams).length === 0) requestParams = undefined
    }

    // 构建请求体（后端 DirectTestRequestDTO.requestBody 为 String 类型，直接传字符串）
    let requestBody = undefined
    if (form.bodyType !== 'none' && form.paramsJson && form.paramsJson.trim()) {
      requestBody = form.paramsJson.trim()
    }

    // 构建断言规则（普通 + 层级，统一映射为后端 AssertionRuleDTO 格式）
    const responseAssertions = assertions.value
      .filter(a => {
        if (a.mode === 'hierarchy') {
          return a.type && a.operator && a.expression && a.value
        }
        return a.type && a.operator && a.value
      })
      .map(a => ({
        type: a.type || 'JSON_PATH',
        operator: a.operator,
        expression: a.expression,
        expectedValue: a.value
      }))

    // 构建直接测试请求数据
    const testData = {
      protocolType: 'HTTP',
      requestType: form.method,
      address: form.url.trim(),
      requestHeaders: Object.keys(requestHeaders).length > 0 ? requestHeaders : undefined,
      requestParams: requestParams,
      requestBody: requestBody,
      responseFormat: 'JSON',
      responseAssertions: responseAssertions.length > 0 ? responseAssertions : undefined,
      timeout: form.timeout || 30000
    }

    console.log('直接测试请求参数：', testData)
    const res = await directTest(testData)
    console.log('直接测试响应：', res)

    // 提取测试数据（兼容 {code, data, message} 和直接返回两种格式）
    const testResult = (res.data && typeof res.data === 'object' && 'success' in res.data) ? res.data : res

    const duration = Date.now() - startTime
    const statusCode = testResult.response?.statusCode || 200

    // 响应展示：仅提取 response.body 部分，避免显示完整响应对象（headers/statusCode 等）
    const responseBody = testResult.response?.body !== undefined
      ? testResult.response.body
      : (testResult.responseSnapshot || testResult.response || testResult)

    const record = {
      id: `record_${Date.now()}`,
      createdAt: new Date().toISOString(),
      creator: currentUser.username,
      url: form.url,
      method: form.method,
      appName: form.appName,
      requestSnapshot: testData,
      responseSnapshot: responseBody,
      statusCode: statusCode,
      result: testResult.success ? '成功' : '失败',
      duration: testResult.duration || duration,
      includeAutomation: form.includeAutomation,
    }

    actualStatusCode.value = statusCode
    records.value.unshift(record)
    saveRecords()
    lastRecord.value = record

    if (testResult.success) {
      ElMessage.success(`请求执行成功，耗时 ${testResult.duration || duration}ms`)
    } else {
      ElMessage.error(testResult.errorMsg || '请求执行失败')
    }
  } catch (error) {
    // 请求失败处理
    const duration = Date.now() - startTime
    const record = {
      id: `record_${Date.now()}`,
      createdAt: new Date().toISOString(),
      creator: currentUser.username,
      url: form.url,
      method: form.method,
      appName: form.appName,
      requestSnapshot: { url: form.url, method: form.method },
      responseSnapshot: error.response?.data || error.msg || error.message || '请求失败',
      statusCode: error.response?.status || 0,
      result: '失败',
      duration,
      includeAutomation: form.includeAutomation,
    }
    if (error.response?.status) {
      actualStatusCode.value = error.response.status
    } else {
      actualStatusCode.value = '请求失败'
    }

    records.value.unshift(record)
    saveRecords()
    lastRecord.value = record

    const errorMsg = error.response?.data?.message || error.message || '请求执行失败'
    ElMessage.error(errorMsg)
  } finally {
    isSending.value = false
  }
}

// ========== 生命周期 ==========
// 组件挂载时初始化
onMounted(() => {
  loadUserInfo()
  loadRecords()
  loadEditData()
})
</script>

<style scoped>
.api-tester-container {
  height: 100%;
  margin: 0;
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--paper, #fafafa);
}

.api-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 20px;
  overflow: hidden;
  min-height: 0;
}

.tester-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  background: var(--paper, #fafafa);
  border: 1px solid var(--mute, #e5e5e5);
  padding: 24px;
  margin-bottom: 20px;
}

/* el-card 内部 body 需要支持 flex 布局 */
.tester-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* Tab容器固定，内容区滚动 */
.api-tabs-wrap {

  flex-shrink: 0;
  margin-bottom: 12px;
}

.tab-scroll-wrap {
  max-height: 200px;
  /* 参数面板固定可视高度，可自行调整 */
  overflow-y: auto;
  padding-right: 4px;
}

/* 返回响应区域自适应剩余高度，内部滚动 */
.response-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-top: 12px;
  min-height: 400px;
  overflow: hidden;
}

.response-scroll-box {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  min-height: 0;
  word-break: break-all;
}

/* 美化滚动条（可选，优化视觉） */
.tab-scroll-wrap::-webkit-scrollbar,
.response-scroll-box::-webkit-scrollbar {
  width: 6px;
}

.tab-scroll-wrap::-webkit-scrollbar-thumb,
.response-scroll-box::-webkit-scrollbar-thumb {
  background: var(--fog, #d4d4d4);
}

.tab-scroll-wrap::-webkit-scrollbar-track,
.response-scroll-box::-webkit-scrollbar-track {
  background: transparent;
}

/* ========== 以下是你原有全部样式，无任何删减 ========== */
/* 新建菜单弹窗样式 */
.new-popover-content {
  padding: 12px;
  background: var(--ink, #0a0a0a);
  color: var(--paper, #fafafa);
  min-width: 380px;
}

.popover-section {
  margin-bottom: 16px;
}

.section-title {
  font-size: 12px;
  color: var(--ghost, #a3a3a3);
  padding: 8px 8px 4px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 8px 4px;
}

.section-more {
  font-size: 12px;
  color: var(--ghost, #a3a3a3);
  cursor: pointer;
}

.section-grid-large {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  padding: 0 4px;
}

.popover-item-large {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  transition: background 0.2s;
}

.popover-item-large:hover {
  background: #1a1a1a;
}

.item-icon-large {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.http-icon-large {
  background: var(--ink, #0a0a0a);
  color: #ffffff;
}

.quick-icon-large {
  background: var(--ghost, #a3a3a3);
  color: #ffffff;
}

.section-grid-small {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  padding: 4px;
}

.popover-item-small {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.popover-item-small:hover {
  background: #1a1a1a;
}

.item-icon-small {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ws-icon-small {
  background: var(--ink, #0a0a0a);
  color: #ffffff;
}

.socket-icon-small {
  background: var(--ghost, #a3a3a3);
  color: #ffffff;
}

.mcp-icon-small {
  background: var(--fog, #d4d4d4);
  color: var(--ink, #0a0a0a);
}

.more-icon-small {
  background: #1a1a1a;
  color: var(--ghost, #a3a3a3);
}

.model-icon-small {
  background: #1a1a1a;
  color: var(--ghost, #a3a3a3);
}

.md-icon-small {
  background: #1a1a1a;
  color: var(--ghost, #a3a3a3);
}

.dir-icon-small {
  background: #1a1a1a;
  color: var(--ghost, #a3a3a3);
}

.module-icon-small {
  background: #1a1a1a;
  color: var(--ghost, #a3a3a3);
}

.section-list {
  padding: 0 4px;
}

.popover-item-list {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  cursor: pointer;
  transition: background 0.2s;
  position: relative;
}

.popover-item-list:hover {
  background: #1a1a1a;
}

.item-icon-list {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1a1a1a;
  color: var(--ghost, #a3a3a3);
}

.item-shortcut {
  position: absolute;
  right: 12px;
  font-size: 12px;
  color: var(--ghost, #a3a3a3);
}

.page-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.title-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.back-btn {
  padding: 6px 8px;
}

.page-title h2 {
  margin: 0;
  font-size: 24px;
  color: var(--ink, #0a0a0a);
}

.page-title p {
  color: var(--ghost, #a3a3a3);
  margin: 8px 0 0;
}

.result-card {
  background: var(--paper, #fafafa);
  border: 1px solid var(--mute, #e5e5e5);
  padding: 24px;
  margin-bottom: 20px;
}

.tester-form {
  width: 100%;
}

.form-actions {
  display: flex;
  justify-content: flex-start;
  gap: 12px;
  margin-top: 20px;
}

.result-title {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 16px;
  color: var(--ink, #0a0a0a);
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.json-panel {
  grid-column: span 2;
  background: var(--smoke, #f5f5f5);
  border: 1px solid var(--mute, #e5e5e5);
  padding: 16px;
}

.panel-title {
  font-weight: 500;
  margin-bottom: 10px;
  color: var(--ink, #0a0a0a);
}

.json-panel pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 260px;
  overflow: auto;
  background: var(--paper, #fafafa);
  padding: 14px;
  border: 1px solid var(--mute, #e5e5e5);
}

.history-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;
}

.expand-content {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.expand-block {
  background: var(--smoke, #f5f5f5);
  padding: 14px;
  border: 1px solid var(--mute, #e5e5e5);
}

.expand-title {
  font-weight: 500;
  margin-bottom: 8px;
  color: var(--ink, #0a0a0a);
}

.history-pagination {
  text-align: right;
  margin-top: 16px;
}

/* Auth面板样式 */
.auth-panel {
  padding: 16px;
  min-height: 400px;
}

.auth-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink, #0a0a0a);
  margin-bottom: 12px;
  display: block;
}

.auth-select-wrapper {
  margin-bottom: 20px;
}

.auth-select {
  width: 100%;
}

.auth-divider {
  display: flex;
  align-items: center;
  margin: 20px 0;
}

.auth-divider::before,
.auth-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--mute, #e5e5e5);
}

.divider-text {
  padding: 0 12px;
  font-size: 12px;
  color: var(--ghost, #a3a3a3);
  font-weight: 500;
}

.auth-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;
}

.auth-option {
  padding: 12px 16px;
  border: 1px solid var(--mute, #e5e5e5);
  cursor: pointer;
  transition: border-color 0.2s;
  background: var(--paper, #fafafa);
}

.auth-option:hover {
  border-color: var(--ink, #0a0a0a);
}

.auth-option.active {
  border-color: var(--ink, #0a0a0a);
  background: var(--smoke, #f5f5f5);
}

.option-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.option-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink, #0a0a0a);
}

.option-check {
  color: var(--ink, #0a0a0a);
  font-size: 16px;
}

.option-desc {
  font-size: 12px;
  color: var(--ghost, #a3a3a3);
}

.auth-config-area {
  margin-top: 16px;
}

.auth-config {
  background: var(--smoke, #f5f5f5);
  padding: 16px;
  border: 1px solid var(--mute, #e5e5e5);
}

.config-header {
  font-size: 13px;
  font-weight: 600;
  color: var(--ghost, #a3a3a3);
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--mute, #e5e5e5);
}

.config-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.config-row:last-child {
  margin-bottom: 0;
}

.config-row label {
  width: 80px;
  font-size: 13px;
  color: var(--ghost, #a3a3a3);
  font-weight: 500;
}

.config-row .el-input {
  flex: 1;
  max-width: 300px;
}

.config-select {
  flex: 1;
  max-width: 300px;
}

.auth-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--smoke, #f5f5f5);
  border: 1px solid var(--mute, #e5e5e5);
  margin-top: 12px;
}

.auth-tip span {
  color: var(--ghost, #a3a3a3);
  font-size: 13px;
}

.tip-icon {
  font-size: 16px;
  color: var(--ghost, #a3a3a3);
}

/* Top bar */
.top-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.input-area {
  display: flex;
  gap: 12px;
  align-items: center;
  flex: 1;
}

.app-name-input {
  width: 150px;
}

.method-select {
  width: 90px;
}

.url-input {
  flex: 1;
  min-width: 300px;
}

.action-group {
  display: flex;
  gap: 8px;
}

/* Params table */
.params-table {
  border: 1px solid var(--mute, #e5e5e5);
  padding: 8px;
  background: var(--paper, #fafafa);
}

.param-row {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 6px 4px;
}

.param-row.header {
  font-weight: 500;
  color: var(--ghost, #a3a3a3);
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.param-row .col {
  display: flex;
  align-items: center
}

.param-row .col.name {
  width: 160px
}

.param-row .col.value {
  flex: 1
}

.param-row .col.type {
  width: 120px
}

.param-row .col.desc {
  width: 220px
}

.param-row .col.actions {
  width: 80px;
  justify-content: center
}

/* Headers */
.headers-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 500;
  color: var(--ink, #0a0a0a);
}

.hidden-count {
  font-size: 12px;
  color: var(--ghost, #a3a3a3);
  font-weight: normal;
}

.header-actions {
  display: flex;
  gap: 4px;
}

.header-input {
  width: 100%;
}

/* 断言面板 */
.assertions-panel {
  padding: 12px;
}

.assertions-header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
}

.assertions-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink, #0a0a0a);
}

.assertions-footer {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--mute, #e5e5e5);
}

.add-assertion-btn {
  color: var(--ink, #0a0a0a);
  padding: 0;
}

.assertions-table {
  border: 1px solid var(--mute, #e5e5e5);
  overflow: hidden;
}

/* 空状态样式 */
.assertion-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 20px;
  background: var(--paper, #fafafa);
  border-top: 1px solid var(--mute, #e5e5e5);
}

.empty-text {
  margin: 16px 0;
  color: var(--ghost, #a3a3a3);
  font-size: 14px;
}

.assertions-table-header {
  display: flex;
  background: var(--smoke, #f5f5f5);
  padding: 8px 12px;
}

.assert-th {
  font-size: 12px;
  font-weight: 500;
  color: var(--ghost, #a3a3a3);
}

.assert-th:nth-child(1),
.assert-th:nth-child(2) {
  width: 140px;
}

.assert-th:nth-child(3) {
  flex: 1;
  min-width: 100px;
}

.assert-th:nth-child(4) {
  width: 60px;
  text-align: center;
}

.assertion-row {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-top: 1px solid var(--mute, #e5e5e5);
  gap: 12px;
}

.assertion-select {
  width: 140px;
}

.assertion-input {
  flex: 1;
  min-width: 150px;
  max-width: 250px;
}

.assertion-delete {
  color: var(--ghost, #a3a3a3);
  padding: 0;
  width: 60px;
  text-align: center;
}

/* Response area */
.response-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-top: 12px;
  min-height: 0;
}

.response-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px
}

.response-content {
  background: var(--ink, #0a0a0a);
  color: var(--paper, #fafafa);
  padding: 12px;
  border: 1px solid var(--mute, #e5e5e5);
  min-height: 360px;
}

.response-content pre {
  color: var(--paper, #fafafa);
  white-space: pre-wrap;
}

.empty {
  color: var(--ghost, #a3a3a3)
}

/* ========== 保存对话框样式 ========== */
.save-dialog-content {
  padding: 0 20px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ========== Cookie 面板样式 ========== */
.cookie-panel {
  padding: 12px;
}

.cookie-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.cookie-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink, #0a0a0a);
}

.cookie-actions {
  display: flex;
  gap: 8px;
}

.cookie-table {
  border: 1px solid var(--mute, #e5e5e5);
  overflow: hidden;
}

.cookie-row {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid var(--mute, #e5e5e5);
}

.cookie-row:last-child {
  border-bottom: none;
}

.cookie-row.header {
  background: var(--smoke, #f5f5f5);
  font-weight: 500;
  color: var(--ink, #0a0a0a);
  font-size: 12px;
}

.cookie-col {
  display: flex;
  align-items: center;
}

.cookie-col.name {
  width: 150px;
}

.cookie-col.value {
  flex: 1;
  min-width: 120px;
}

.cookie-col.domain {
  width: 130px;
}

.cookie-col.path {
  width: 100px;
}

.cookie-col.expire {
  width: 180px;
}

.cookie-col.actions {
  width: 60px;
  justify-content: center;
}

.cookie-col .el-input,
.cookie-col .el-date-picker {
  width: 100%;
}

.cookie-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: var(--ghost, #a3a3a3);
  font-size: 14px;
  gap: 8px;
}

.cookie-tip {
  margin-top: 12px;
}
</style>
