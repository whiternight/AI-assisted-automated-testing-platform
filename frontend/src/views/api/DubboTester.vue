<template>
  <div class="dubbo-tester-container">
    <!-- 主内容区 -->
    <div class="tester-content">
      <!-- 请求区域 -->
      <div class="request-panel">
        <!-- 请求顶部栏 -->
        <div class="request-header">
          <el-input 
            v-model="formData.appName" 
            placeholder="应用名称" 
            class="app-name-input"
          />
          <el-select v-model="formData.dubboType" class="method-select">
            <el-option label="Dubbo" value="Dubbo" />
            <el-option label="Dubbo泛化" value="DubboGeneric" />
            <el-option label="直连" value="DirectConnection" />
            <el-option label="服务测试" value="ServiceTest" />
          </el-select>
          <el-input 
            v-model="formData.url" 
            placeholder="接口地址，如 dubbo://xxx 或 com.xxx.Service" 
            class="url-input"
          />
          <el-button type="primary" @click="sendRequest" class="send-btn">发送</el-button>
          <el-button @click="saveCase" class="save-btn">保存</el-button>
          <el-button :icon="ArrowRight" @click="goHome" circle />
        </div>

        <!-- 标签页 -->
        <div class="tabs-container">
          <el-tabs v-model="activeTab" type="card" class="request-tabs">
            <!-- Params 标签页 -->
            <el-tab-pane label="Params" name="params">
              <div class="params-panel">
                <div class="params-header">
                  <span class="params-label">Query 参数</span>
                </div>
                <div class="params-table">
                  <div class="params-table-header">
                    <div class="th">参数名</div>
                    <div class="th">参数值</div>
                    <div class="th">类型</div>
                    <div class="th">说明</div>
                    <div class="th">操作</div>
                  </div>
                  <div 
                    v-for="(param, index) in formData.params" 
                    :key="index" 
                    class="params-table-row"
                  >
                    <el-input 
                      v-model="param.name" 
                      placeholder="参数名" 
                      class="param-input small"
                    />
                    <el-input 
                      v-model="param.value" 
                      placeholder="参数值" 
                      class="param-input"
                    />
                    <el-select v-model="param.type" class="type-select">
                      <el-option label="string" value="string" />
                      <el-option label="number" value="number" />
                      <el-option label="boolean" value="boolean" />
                      <el-option label="array" value="array" />
                    </el-select>
                    <el-input 
                      v-model="param.description" 
                      placeholder="说明" 
                      class="param-input small"
                    />
                    <el-button type="text" @click="removeParam(index)" class="delete-btn">删除</el-button>
                  </div>
                </div>
                <el-button type="text" @click="addParam" class="add-param-btn">添加参数</el-button>
              </div>
            </el-tab-pane>

            <!-- Body 标签页 -->
            <el-tab-pane label="Body" name="body">
              <div class="body-panel">
                <el-input
                  type="textarea"
                  :rows="8"
                  v-model="formData.body"
                  placeholder='{"key": "value"}'
                  class="body-textarea"
                />
                <div class="body-actions">
                  <el-button type="text" size="small" @click="formatBody">格式化</el-button>
                  <el-button type="text" size="small" @click="clearBody">清空</el-button>
                </div>
              </div>
            </el-tab-pane>

            <!-- Headers 标签页 -->
            <el-tab-pane label="Headers" name="headers">
              <div class="headers-panel">
                <div class="headers-table">
                  <div 
                    v-for="(header, index) in formData.headers" 
                    :key="index" 
                    class="header-row"
                  >
                    <el-input 
                      v-model="header.key" 
                      placeholder="Header 名称" 
                      class="header-input"
                    />
                    <el-input 
                      v-model="header.value" 
                      placeholder="Header 值" 
                      class="header-input"
                    />
                    <el-button type="text" @click="removeHeader(index)" class="delete-btn">删除</el-button>
                  </div>
                </div>
                <el-button type="text" @click="addHeader" class="add-header-btn">添加 Header</el-button>
              </div>
            </el-tab-pane>

            <!-- 断言标签页 -->
            <el-tab-pane label="断言" name="assertions">
              <div class="assertions-panel">
                <div class="assertions-header">
                  <span class="assertions-title">断言配置（验证响应是否符合预期）</span>
                  <el-button type="text" @click="addAssertion" class="add-assertion-btn">+ 添加断言</el-button>
                </div>
                <div class="assertions-table">
                  <!-- 表头 -->
                  <div class="assertions-table-header">
                    <div class="assert-th">断言类型</div>
                    <div class="assert-th">运算符</div>
                    <div class="assert-th">期望值类型</div>
                    <div class="assert-th">操作</div>
                  </div>
                  
                  <!-- 空状态提示 -->
                  <div v-if="formData.assertions.length === 0" class="assertion-empty-state">
                    <el-icon :size="48" color="#c0c4cc"><DocumentDelete /></el-icon>
                    <p class="empty-text">暂无断言规则</p>
                    <el-button type="primary" size="small" @click="addAssertion">添加第一个断言</el-button>
                  </div>
                  
                  <!-- 断言列表 -->
                  <div 
                    v-for="(assertion, index) in formData.assertions" 
                    :key="index" 
                    class="assertion-row"
                  >
                    <!-- 断言类型选择（五种类型） -->
                    <el-select 
                      v-model="assertion.type" 
                      class="assertion-select"
                      placeholder="选择断言类型"
                      @change="handleTypeChange(assertion)"
                    >
                      <el-option label="响应状态码" value="status_code" />
                      <el-option label="响应时间" value="response_time" />
                      <el-option label="响应头字段" value="response_header" />
                      <el-option label="响应体字段" value="response_body" />
                      <el-option label="JSONPath表达式" value="json_path" />
                    </el-select>
                    
                    <!-- 运算符选择（七种运算符） -->
                    <el-select 
                      v-model="assertion.operator" 
                      class="assertion-select"
                      placeholder="选择运算符"
                    >
                      <el-option label="等于 (==)" value="==" />
                      <el-option label="不等于 (!=)" value="!=" />
                      <el-option label="大于 (>)" value=">" />
                      <el-option label="小于 (<)" value="<" />
                      <el-option label="大于等于 (>=)" value=">=" />
                      <el-option label="小于等于 (<=)" value="<=" />
                      <el-option label="包含 (contains)" value="contains" />
                    </el-select>
                    
                    <!-- 期望值类型选择 -->
                    <el-select 
                      v-model="assertion.valueType" 
                      class="assertion-select"
                      placeholder="选择期望值类型"
                    >
                      <el-option label="固定值" value="fixed" />
                      <el-option label="引用JSON Key" value="json_key" />
                    </el-select>
                    
                    <!-- 期望值输入区域 - 根据类型动态显示 -->
                    <template v-if="assertion.valueType === 'fixed'">
                      <!-- 状态码：下拉选择常用状态码 -->
                      <el-select 
                        v-if="assertion.type === 'status_code'" 
                        v-model="assertion.value" 
                        class="assertion-input"
                        filterable
                        allow-create
                        default-first-option
                        placeholder="选择或输入状态码"
                      >
                        <el-option label="200 - OK" value="200" />
                        <el-option label="201 - Created" value="201" />
                        <el-option label="400 - Bad Request" value="400" />
                        <el-option label="401 - Unauthorized" value="401" />
                        <el-option label="403 - Forbidden" value="403" />
                        <el-option label="404 - Not Found" value="404" />
                        <el-option label="500 - Internal Server Error" value="500" />
                        <el-option label="502 - Bad Gateway" value="502" />
                        <el-option label="503 - Service Unavailable" value="503" />
                      </el-select>
                      
                      <!-- 响应时间：数字输入 -->
                      <el-input 
                        v-else-if="assertion.type === 'response_time'" 
                        v-model.number="assertion.value" 
                        class="assertion-input"
                        type="number"
                        placeholder="毫秒数"
                      >
                        <template #append>ms</template>
                      </el-input>
                      
                      <!-- 其他类型：普通文本输入 -->
                      <el-input 
                        v-else
                        v-model="assertion.value" 
                        class="assertion-input"
                        placeholder="输入期望值"
                      />
                    </template>
                    
                    <!-- JSON Key引用模式 -->
                    <template v-else>
                      <el-input 
                        v-model="assertion.jsonKey" 
                        class="assertion-input"
                        placeholder="输入JSON Key路径，如: data.code"
                      />
                    </template>
                    
                    <el-button type="text" @click="removeAssertion(index)" class="delete-btn assertion-delete">删除</el-button>
                  </div>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>

        <!-- 响应区域 -->
        <div class="response-panel">
          <div class="response-header">
            <span class="response-label">返回响应</span>
            <div class="response-right">
              <span class="auth-toggle">
                <span>校验响应</span>
                <el-switch v-model="responseConfig.validate" />
              </span>
              <el-select v-model="responseConfig.timeout" class="timeout-select">
                <el-option label="200ms" :value="200" />
                <el-option label="500ms" :value="500" />
                <el-option label="1000ms" :value="1000" />
                <el-option label="3000ms" :value="3000" />
                <el-option label="5000ms" :value="5000" />
              </el-select>
            </div>
          </div>
          
          <!-- 断言结果区域 -->
          <div v-if="assertionResults.length > 0 && responseData" class="assertion-results">
            <div class="assertion-result-header">
              <span :class="allAssertionsPassed ? 'result-success' : 'result-fail'">
                {{ allAssertionsPassed ? `✓ 全部通过 (${assertionResults.length}/${assertionResults.length})` : `✗ 部分失败 (${assertionResults.filter(r => r.pass).length}/${assertionResults.length})` }}
              </span>
              <el-button type="text" @click="clearAssertionResults" class="clear-btn">×</el-button>
            </div>
            <div 
              v-for="(result, index) in assertionResults" 
              :key="index" 
              :class="['assertion-result-item', result.pass ? 'pass' : 'fail']"
            >
              <span class="result-dot" :class="result.pass ? 'pass' : 'fail'"></span>
              <div class="result-content">
                <span class="result-text">{{ result.message }}</span>
                <div class="result-detail" v-if="!result.pass">
                  <span>实际值: <strong>{{ result.actual }}</strong></span>
                  <span>期望值: <strong>{{ result.expected }}</strong></span>
                </div>
              </div>
            </div>
            <div class="assertion-summary">
              <span>状态码: {{ responseStatusCode }} | 耗时: {{ responseTime }}ms</span>
            </div>
          </div>
          
          <div class="response-body" :class="{ 'has-response': responseData }">
            <div v-if="responseData" class="response-content">
              <pre>{{ formattedResponse }}</pre>
            </div>
            <div v-else class="empty-response">
              点击"发送"按钮获取返回结果
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import {  ArrowRight, DocumentDelete } from '@element-plus/icons-vue'

const router = useRouter()

// 当前激活的标签页
const activeTab = ref('params')

// 跳转首页
const goHome = () => {
  router.push('/layout/home')
}


// 请求数据
const formData = reactive({
  appName: '',
  dubboType: 'Dubbo',
  url: '',
  params: [
    { name: '', value: '', type: 'string', description: '' },
    { name: '', value: '', type: 'string', description: '' }
  ],
  body: '',
  headers: [
    { key: '', value: '' }
  ],
  assertions: [
    { 
      type: 'status_code',           // 断言类型：status_code, response_time, response_header, response_body, json_path
      operator: '==',                // 运算符：==, !=, >, <, >=, <=, contains
      valueType: 'fixed',            // 期望值类型：fixed（固定值）或 json_key（引用JSON Key）
      value: '',                     // 固定值
      jsonKey: ''                    // JSON Key路径
    }
  ]

})

// 响应配置
const responseConfig = reactive({
  validate: true,
  timeout: 2000
})

// 响应数据
const responseData = ref(null)
const responseStatusCode = ref(0)
const responseTime = ref(0)
const assertionResults = ref([])

// 格式化响应
const formattedResponse = computed(() => {
  if (!responseData.value) return ''
  try {
    return JSON.stringify(JSON.parse(responseData.value), null, 2)
  } catch {
    return responseData.value
  }
})

// 判断所有断言是否通过
const allAssertionsPassed = computed(() => {
  return assertionResults.value.length > 0 && assertionResults.value.every(r => r.pass)
})

// 执行断言验证
const executeAssertions = (code, response) => {
  const results = []
  
  // 解析响应数据
  let responseBody = null
  try {
    responseBody = JSON.parse(response)
  } catch (e) {
    responseBody = response
  }
  
  formData.assertions.forEach(assertion => {
    let pass = false
    let message = ''
    let actualValue = ''
    
    // 根据断言类型获取实际值
    switch (assertion.type) {
      case 'status_code':
        actualValue = code.toString()
        break
        
      case 'response_time':
        actualValue = responseTime.value.toString()
        break
        
      case 'response_body':
        // 从响应体中获取字段值
        if (responseBody && assertion.jsonKey) {
          const keys = assertion.jsonKey.split('.')
          let value = responseBody
          for (const key of keys) {
            if (value && typeof value === 'object' && key in value) {
              value = value[key]
            } else {
              value = undefined
              break
            }
          }
          actualValue = value !== undefined ? String(value) : 'undefined'
        } else {
          actualValue = 'undefined'
        }
        break
        
      case 'response_header':
        // 从响应头中获取值（这里简化处理，实际需要从响应对象获取）
        actualValue = 'N/A' // TODO: 需要从真实响应头获取
        break
        
      case 'json_path':
        // 支持简单的JSON路径表达式
        if (responseBody && assertion.jsonPath) {
          const path = assertion.jsonPath.replace(/\[(\d+)\]/g, '.$1')
          const parts = path.split('.').filter(p => p !== '')
          let value = responseBody
          for (const part of parts) {
            if (value && typeof value === 'object' && part in value) {
              value = value[part]
            } else {
              value = undefined
              break
            }
          }
          actualValue = value !== undefined ? String(value) : 'undefined'
        } else {
          actualValue = 'undefined'
        }
        break
    }
    
    // 获取期望值（支持固定值和JSON Key引用）
    let expectedValue = ''
    
    if (assertion.valueType === 'json_key') {
      // JSON Key引用模式：从响应体中提取值作为期望值
      if (responseBody && assertion.jsonKey) {
        const keys = assertion.jsonKey.split('.')
        let value = responseBody
        for (const key of keys) {
          if (value && typeof value === 'object' && key in value) {
            value = value[key]
          } else {
            value = undefined
            break
          }
        }
        expectedValue = value !== undefined ? String(value) : 'undefined'
      } else {
        expectedValue = 'undefined'
      }
    } else {
      // 固定值模式：直接使用用户输入的值
      expectedValue = assertion.value || ''
    }
    
    // 执行比较运算
    switch (assertion.operator) {
      case '==':
        pass = actualValue === expectedValue
        break
        
      case '!=':
        pass = actualValue !== expectedValue
        break
        
      case '>':
        pass = Number(actualValue) > Number(expectedValue)
        break
        
      case '<':
        pass = Number(actualValue) < Number(expectedValue)
        break
        
      case '>=':
        pass = Number(actualValue) >= Number(expectedValue)
        break
        
      case '<=':
        pass = Number(actualValue) <= Number(expectedValue)
        break
        
      case 'contains':
        pass = actualValue.includes(expectedValue)
        break
        
      default:
        pass = false
        message = `不支持的运算符: ${assertion.operator}`
    }
    
    // 构建结果消息
    if (!message) {
      const typeLabel = getTypeLabel(assertion.type)
      const valueTypeLabel = assertion.valueType === 'json_key' ? `(引用: ${assertion.jsonKey})` : ''
      message = `${typeLabel} ${actualValue} ${assertion.operator} ${expectedValue} ${valueTypeLabel} ${pass ? '✓ 通过' : '✗ 失败'}`
    }
    
    results.push({ 
      pass, 
      message,
      type: assertion.type,
      actual: actualValue,
      expected: expectedValue
    })
  })
  
  assertionResults.value = results
}

// 获取类型标签
const getTypeLabel = (type) => {
  const labels = {
    status_code: '响应状态码',
    response_time: '响应时间',
    response_body: '响应体字段',
    response_header: '响应头字段',
    json_path: 'JSONPath表达式'
  }
  return labels[type] || type
}

// 获取字段信息
const getFieldInfo = (assertion) => {
  if (assertion.type === 'bodyField' && assertion.jsonKey) {
    return `[${assertion.jsonKey}]`
  } else if (assertion.type === 'header' && assertion.headerName) {
    return `[${assertion.headerName}]`
  } else if (assertion.type === 'jsonPath' && assertion.jsonPath) {
    return `[${assertion.jsonPath}]`
  }
  return ''
}

// 清空断言结果
const clearAssertionResults = () => {
  assertionResults.value = []
}

// 添加参数
const addParam = () => {
  formData.params.push({ name: '', value: '', type: 'string', description: '' })
}

// 删除参数
const removeParam = (index) => {
  if (formData.params.length > 1) {
    formData.params.splice(index, 1)
  }
}

// 添加 Header
const addHeader = () => {
  formData.headers.push({ key: '', value: '' })
}

// 删除 Header
const removeHeader = (index) => {
  if (formData.headers.length > 1) {
    formData.headers.splice(index, 1)
  }
}

// 添加断言（使用新的数据结构）
const addAssertion = () => {
  formData.assertions.push({ 
    type: 'status_code',           // 断言类型：status_code, response_time, response_header, response_body, json_path
    operator: '==',                // 运算符：==, !=, >, <, >=, <=, contains
    valueType: 'fixed',            // 期望值类型：fixed（固定值）或 json_key（引用JSON Key）
    value: '',                     // 固定值
    jsonKey: ''                    // JSON Key路径
  })
}

// 处理类型变化，清空相关字段
const handleTypeChange = (assertion) => {
  // 清空所有特定类型的字段
  assertion.value = ''
  assertion.jsonKey = ''
  assertion.valueType = 'fixed' // 重置为固定值
  
  // 根据类型设置默认运算符
  if (assertion.type === 'response_time') {
    assertion.operator = '<='
  } else {
    assertion.operator = '=='
  }
}

// 删除断言
const removeAssertion = (index) => {
  // 允许删除所有断言，包括最后一个
  formData.assertions.splice(index, 1)
  
  // 如果删除后没有断言了，可以提示用户或自动添加一个空的（可选）
  if (formData.assertions.length === 0) {
    addAssertion()
  }
}

// 格式化 Body
const formatBody = () => {
  try {
    formData.body = JSON.stringify(JSON.parse(formData.body), null, 2)
  } catch (e) {
    ElMessage.error('JSON 格式错误')
  }
}

// 清空 Body
const clearBody = () => {
  formData.body = ''
}

// 发送请求
const sendRequest = async () => {
  if (!formData.url) {
    ElMessage.warning('请输入接口路径')
    return
  }

  // 清空之前的断言结果
  clearAssertionResults()
  
  const startTime = Date.now()
  
  try {
    await new Promise(resolve => setTimeout(resolve, 1000))
    
    const code = 200
    responseStatusCode.value = code
    responseTime.value = Date.now() - startTime
    
    responseData.value = JSON.stringify({
      code: code,
      message: 'success',
      data: {
        id: 1,
        name: '测试数据',
        createdAt: new Date().toISOString()
      }
    })
    
    // 执行断言验证
    if (responseConfig.validate && formData.assertions.length > 0) {
      executeAssertions(code, responseData.value)
    }
  } catch (error) {
    responseTime.value = Date.now() - startTime
    const code = -1
    responseStatusCode.value = code
    
    responseData.value = JSON.stringify({
      code: code,
      message: error.message || '请求失败'
    })
    
    // 执行断言验证
    if (responseConfig.validate && formData.assertions.length > 0) {
      executeAssertions(code, responseData.value)
    }
  }
}

// 保存用例
const saveCase = () => {
  ElMessage.success('用例保存成功')
}
</script>

<style scoped>
.dubbo-tester-container {
  min-height: 100vh;
  background: var(--paper, #fafafa);
  display: flex;
  flex-direction: column;
}

/* 主内容区 */
.tester-content {
  flex: 1;
  padding: 20px;
}

/* 请求面板 */
.request-panel {
  background: var(--paper, #fafafa);
  border: 1px solid var(--mute, #e5e5e5);
  padding: 20px;
}

/* 请求顶部栏 */
.request-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.app-name-input { width: 120px }
.method-select { width: 100px }
.url-input { flex: 1; min-width: 300px }
.send-btn { padding: 6px 20px }
.save-btn { padding: 6px 16px }
.arrow-btn { margin-left: 8px }

/* 标签页容器 */
.tabs-container { margin-bottom: 20px }
.request-tabs :deep(.el-tabs__header) { margin-bottom: 16px }

/* Params 面板 */
.params-panel { padding: 12px }
.params-header { margin-bottom: 12px }

.params-label {
  font-size: 10px;
  font-weight: 500;
  color: var(--ghost, #a3a3a3);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.params-table {
  border: 1px solid var(--mute, #e5e5e5);
  overflow: hidden;
}

.params-table-header {
  display: flex;
  background: var(--smoke, #f5f5f5);
  padding: 8px 12px;
}

.th {
  font-size: 10px;
  font-weight: 500;
  color: var(--ghost, #a3a3a3);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.th:nth-child(1), .th:nth-child(3), .th:nth-child(4) { width: 120px }
.th:nth-child(2) { flex: 1; min-width: 200px }
.th:nth-child(5) { width: 60px; text-align: center }

.params-table-row {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-top: 1px solid var(--mute, #e5e5e5);
  gap: 12px;
}

.param-input { flex: 1; min-width: 150px }
.param-input.small { width: 120px }
.type-select { width: 120px }

.delete-btn {
  color: #e53e3e;
  padding: 0;
  width: 60px;
  text-align: center;
}

.add-param-btn {
  margin-top: 12px;
  color: var(--ink, #0a0a0a);
}

/* Body 面板 */
.body-panel { padding: 12px }

.body-textarea {
  width: 100%;
  background: var(--smoke, #f5f5f5);
  border-color: var(--mute, #e5e5e5);
}

.body-actions { display: flex; gap: 12px; margin-top: 8px }

/* Headers 面板 */
.headers-panel { padding: 12px }

.headers-table { display: flex; flex-direction: column; gap: 8px }
.header-row { display: flex; gap: 12px }
.header-input { flex: 1; max-width: 300px }

.add-header-btn {
  margin-top: 12px;
  color: var(--ink, #0a0a0a);
}

/* 断言面板 */
.assertions-panel { padding: 12px }

.assertions-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.assertions-title {
  font-size: 10px;
  font-weight: 500;
  color: var(--ink, #0a0a0a);
  text-transform: uppercase;
  letter-spacing: 0.06em;
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
  background: var(--smoke, #f5f5f5);
  border-top: 1px solid var(--mute, #e5e5e5);
}

.empty-text {
  margin: 16px 0;
  color: var(--ghost, #a3a3a3);
  font-size: 12px;
}

.assertions-table-header {
  display: flex;
  background: var(--smoke, #f5f5f5);
  padding: 8px 12px;
}

.assert-th {
  font-size: 10px;
  font-weight: 500;
  color: var(--ghost, #a3a3a3);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.assert-th:nth-child(1), .assert-th:nth-child(2), .assert-th:nth-child(3) { width: 140px }
.assert-th:nth-child(4) { flex: 1; min-width: 100px }
.assert-th:nth-child(5) { width: 60px; text-align: center }

.assertion-row {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-top: 1px solid var(--mute, #e5e5e5);
  gap: 12px;
}

.assertion-select { width: 140px }
.expectation-input-wrapper { flex: 1; min-width: 250px; max-width: 400px }
.assertion-input { width: 100% }

/* JSON Key-Value 输入框样式 */
.json-key-value-input { display: flex; gap: 8px; width: 100% }
.json-key-input { flex: 1; min-width: 120px }
.json-value-input { flex: 1.5; min-width: 150px }

/* JSON Path 输入框样式 */
.json-path-input { display: flex; gap: 8px; width: 100% }
.json-path-field { flex: 1.5; min-width: 180px }
.json-path-value { flex: 1; min-width: 120px }

/* 响应面板 */
.response-panel {
  border: 1px solid var(--mute, #e5e5e5);
  overflow: hidden;
}

.response-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: var(--smoke, #f5f5f5);
  border-bottom: 1px solid var(--mute, #e5e5e5);
}

.response-label {
  font-size: 10px;
  font-weight: 500;
  color: var(--ghost, #a3a3a3);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.response-right { display: flex; align-items: center; gap: 16px }

.auth-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--ink, #0a0a0a);
}

.timeout-select { width: 100px }

/* 断言结果区域 */
.assertion-results {
  border-bottom: 1px solid var(--mute, #e5e5e5);
  padding: 12px 16px;
  background: var(--smoke, #f5f5f5);
}

.assertion-result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.result-success { color: var(--ink, #0a0a0a); font-weight: 500 }
.result-fail { color: #e53e3e; font-weight: 500 }

.clear-btn {
  color: var(--ghost, #a3a3a3);
  padding: 0;
  font-size: 18px;
}

.assertion-result-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 0;
}

.result-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-top: 4px;
  flex-shrink: 0;
}

.result-dot.pass { background: var(--ink, #0a0a0a) }
.result-dot.fail { background: #e53e3e }

.result-content { flex: 1; min-width: 0 }

.result-text {
  font-size: 12px;
  color: var(--ink, #0a0a0a);
  line-height: 1.5;
}

.assertion-result-item.pass .result-text { color: var(--ink, #0a0a0a) }
.assertion-result-item.fail .result-text { color: #e53e3e }

.result-detail {
  margin-top: 4px;
  font-size: 11px;
  color: var(--ghost, #a3a3a3);
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.result-detail span {
  background: var(--paper, #fafafa);
  padding: 2px 6px;
  border: 1px solid var(--mute, #e5e5e5);
}

.result-detail strong {
  color: var(--ink, #0a0a0a);
  font-weight: 500;
}

.assertion-summary {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--mute, #e5e5e5);
  font-size: 11px;
  color: var(--ghost, #a3a3a3);
}

.response-body {
  min-height: 200px;
  background: var(--smoke, #f5f5f5);
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-response {
  color: var(--ghost, #a3a3a3);
  font-size: 12px;
}

.response-content pre {
  color: var(--ink, #0a0a0a);
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow-y: auto;
}

:deep(.el-dialog) {
  border-radius: 0;
  border: 1px solid var(--mute, #e5e5e5);
}
</style>