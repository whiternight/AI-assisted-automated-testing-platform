<template>
  <div ref="containerRef" class="bpmn-canvas"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, defineExpose } from 'vue'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
// 流程执行轨迹可视化样式（全局，作用于 bpmn-js 注入的 SVG 元素）
import '@/assets/bpmn-execution.css'
// ✅ 静态导入 Flowable 扩展（同一目录下）
import flowableModdle from './flowable.js'

const props = defineProps({
  initialXml: {
    type: String,
    default: ''
  }
})

const emit = defineEmits([
  'element-click',
  'shape-added',
  'connection-created',
  'import-done',
  'save-xml',
  'service-updated'
])

const containerRef = ref(null)
let modeler = null
let updateLabelTimer = null

// 调度服务任务标签更新（防抖，确保在 bpmn-js 重新渲染完成后执行）
const scheduleUpdateServiceTaskLabel = (elementOrElementId) => {
  if (updateLabelTimer) clearTimeout(updateLabelTimer)
  updateLabelTimer = setTimeout(() => {
    requestAnimationFrame(() => {
      updateServiceTaskLabel(elementOrElementId)
    })
  }, 60)
}

// 初始化 bpmn-js Modeler
const initModeler = () => {
  if (!containerRef.value) return

  try {
    console.log('[BpmnCanvas] 📦 Flowable moddle:', flowableModdle ? '✅ 已加载' : '❌ 未定义')
    
    // 创建 Modeler 配置对象
    const modelerConfig = {
      container: containerRef.value,
      keyboard: { bindTo: document },
      propertiesPanel: { parent: '#properties-panel-container' }
    }
    
    // ✅ 如果 Flowable 扩展存在，添加到配置中
    if (flowableModdle) {
      modelerConfig.moddleExtensions = { flowable: flowableModdle }
    }
    
    // 初始化 Modeler
    modeler = new BpmnModeler(modelerConfig)

    console.log(`[BpmnCanvas] ✅ BpmnModeler 已初始化 (${flowableModdle ? '含 Flowable 扩展' : '标准模式'})`)
    
    // 设置事件监听器
    setupEventListeners()
    
    // 导入 XML
    if (props.initialXml) {
      importXML(props.initialXml)
    } else {
      importXML(getDefaultXml())
    }
    
  } catch (error) {
    console.error('[BpmnCanvas] ❌ 初始化失败:', error)
    
    // 降级模式：不使用扩展
    modeler = new BpmnModeler({
      container: containerRef.value,
      keyboard: { bindTo: document },
      propertiesPanel: { parent: '#properties-panel-container' }
    })
    
    setupEventListeners()
    
    if (props.initialXml) importXML(props.initialXml)
    else importXML(getDefaultXml())
  }
}
// 统一设置事件监听器（避免重复代码）
const setupEventListeners = () => {
  // 元素点击事件
  modeler.on('element.click', (event) => {
    emit('element-click', event)
  })

  // 元素变更事件（更新服务任务标签）
  modeler.on('element.changed', (event) => {
    if (event.element && isServiceTask(event.element)) {
      scheduleUpdateServiceTaskLabel(event.element)
    }
  })

  // 形状添加事件
  modeler.on('shape.added', (event) => {
    emit('shape-added', event)
    if (isServiceTask(event.element)) {
      scheduleUpdateServiceTaskLabel(event.element)
    }
  })

  // 连线创建事件
  modeler.on('connection.added', (event) => {
    emit('connection-created', event)
  })

  // 标签更新事件
  modeler.on('shape.updateLabel', (event) => {
    if (isServiceTask(event.element)) {
      scheduleUpdateServiceTaskLabel(event.element)
    }
  })
}

// 判断是否是服务任务
const isServiceTask = (element) => {
  if (!element) return false
  return element.type === 'bpmn:ServiceTask' ||
         element.type === 'bpmn:serviceTask' ||
         (element.type && element.type.includes('ServiceTask'))
}

// 更新服务任务的 SVG 标签显示
const updateServiceTaskLabel = (elementOrElementId) => {
  if (!modeler) return

  const elementRegistry = modeler.get('elementRegistry')

  // 获取元素（可能是对象或ID）
  const elementId = typeof elementOrElementId === 'string' ? elementOrElementId : elementOrElementId.id
  const element = elementRegistry.get(elementId)

  if (!element) {
    console.warn('[BpmnCanvas] updateServiceTaskLabel: 元素不存在', elementId)
    return
  }

  // 获取该元素的图形
  const shapeGfx = elementRegistry.getGraphics(element)
  if (!shapeGfx) {
    console.warn('[BpmnCanvas] updateServiceTaskLabel: 无法获取图形元素', elementId)
    return
  }

  // 获取业务对象
  const businessObject = element.businessObject || {}

  // 🔍 调试：输出 businessObject 的完整信息
  console.log('[BpmnCanvas] 更新标签 - 元素:', elementId, 'businessObject:', businessObject, '\n  name:', businessObject.name)

  // 获取服务信息 - 使用多种策略尝试读取
  let serviceName = null
  let serviceAddress = null
  let serviceId = null

  // ✅✅✅ 策略0（最高优先级）：从内部缓存读取（最可靠）
  const cachedInfo = serviceInfoCache.get(elementId)
  if (cachedInfo) {
    serviceName = cachedInfo.serviceName || null
    serviceAddress = cachedInfo.serviceAddress || null
    console.log('[BpmnCanvas] ✅✅✅ 从缓存读取到服务信息:', { serviceName, serviceAddress })
  }

  // 策略1：尝试使用 get() 方法读取 flowable 扩展属性
  if (!serviceName && !serviceAddress && typeof businessObject.get === 'function') {
    try {
      serviceName = businessObject.get('flowable:serviceName') || null
      serviceAddress = businessObject.get('flowable:serviceAddress') || null
      serviceId = businessObject.get('flowable:serviceId') || null
      
      if (serviceName || serviceAddress || serviceId) {
        console.log('[BpmnCanvas] 通过 get() 方法读取到服务信息:', { serviceName, serviceAddress, serviceId })
      }
    } catch (e) {
      console.warn('[BpmnCanvas] 使用 get() 方法失败:', e.message)
    }
  }

  // 策略2：直接从 businessObject 读取标准属性（备用方案）
  if (!serviceName && !serviceAddress) {
    serviceName = businessObject.serviceName || null
    serviceAddress = businessObject.serviceAddress || null
    serviceId = businessObject.serviceId || null
    
    if (serviceName || serviceAddress) {
      console.log('[BpmnCanvas] 通过直接属性读取到服务信息:', { serviceName, serviceAddress, serviceId })
    }
  }

  // 最终回退：使用标准 name 属性作为显示名称
  const nodeName = businessObject.name || ''
  const displayName = serviceName || nodeName || ''
  const displayAddress = serviceAddress || ''

  console.log('[BpmnCanvas] 最终显示值:', { displayName, displayAddress, hasServiceId: !!serviceId })

  // 隐藏 bpmn-js 默认的外部标签（避免与我们的内嵌双行标签冲突）
  const defaultLabel = shapeGfx.querySelector('.djs-label')
  if (defaultLabel) {
    defaultLabel.style.display = 'none'
  }

  // 查找 bpmn-js 的默认 visual 容器
  const djsVisual = shapeGfx.querySelector('.djs-visual')
  if (!djsVisual) {
    console.warn('[BpmnCanvas] 找不到 .djs-visual 容器')
    return
  }

  // 查找或创建自定义标签组
  let labelGroup = djsVisual.querySelector('.service-task-custom-label')
  if (!labelGroup) {
    labelGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g')
    labelGroup.setAttribute('class', 'service-task-custom-label')
    djsVisual.appendChild(labelGroup)
  } else {
    // 清空现有内容
    labelGroup.innerHTML = ''
  }

  // 获取形状的 bounds
  const width = element.width || 100
  const height = element.height || 80

  // 添加服务名称（第一行，粗体，居中）
  if (displayName) {
    const nameText = document.createElementNS('http://www.w3.org/2000/svg', 'text')
    nameText.setAttribute('x', width / 2)
    nameText.setAttribute('y', height / 2 - 4)
    nameText.setAttribute('text-anchor', 'middle')
    nameText.setAttribute('font-size', '11')
    nameText.setAttribute('font-weight', 'bold')
    nameText.setAttribute('fill', '#1f2937')
    
    // 截断过长的名称
    const maxNameLength = 12
    nameText.textContent = displayName.length > maxNameLength ? displayName.substring(0, maxNameLength) + '...' : displayName
    labelGroup.appendChild(nameText)
  }

  // 添加服务地址（第二行，较小字体，灰色）
  if (displayAddress) {
    const addrText = document.createElementNS('http://www.w3.org/2000/svg', 'text')
    addrText.setAttribute('x', width / 2)
    addrText.setAttribute('y', height / 2 + 14)
    addrText.setAttribute('text-anchor', 'middle')
    addrText.setAttribute('font-size', '9')
    addrText.setAttribute('fill', '#6b7280')
    
    // 截断过长的地址
    const maxAddrLength = 20
    addrText.textContent = displayAddress.length > maxAddrLength ? displayAddress.substring(0, maxAddrLength) + '...' : displayAddress
    labelGroup.appendChild(addrText)
  } else if (!serviceId && !displayName) {
    // 未绑定服务时显示提示文字
    const hintText = document.createElementNS('http://www.w3.org/2000/svg', 'text')
    hintText.setAttribute('x', width / 2)
    hintText.setAttribute('y', height / 2 + 6)
    hintText.setAttribute('text-anchor', 'middle')
    hintText.setAttribute('font-size', '10')
    hintText.setAttribute('fill', '#9ca3af')
    hintText.textContent = '未绑定服务'
    labelGroup.appendChild(hintText)
  }
}

// 内部服务信息缓存（确保标签显示不依赖 bpmn-js 的属性系统）
const serviceInfoCache = new Map()

// 公开更新服务任务标签的方法 - 支持传入完整参数
const refreshServiceTaskLabel = (element, serviceName, serviceAddress) => {
  if (!modeler || !element) {
    console.warn('[BpmnCanvas] refreshServiceTaskLabel: 缺少必要参数', { modeler: !!modeler, element })
    return
  }

  const elementRegistry = modeler.get('elementRegistry')
  const modeling = modeler.get('modeling')

  // 获取元素
  const elementId = element.id || element
  const shape = elementRegistry.get(elementId)

  if (!shape) {
    console.warn('[BpmnCanvas] refreshServiceTaskLabel: 无法找到元素', elementId)
    return
  }

  // ✅ 关键修复：将服务信息存入内部缓存（不依赖 bpmn-js 属性系统）
  if (serviceName !== undefined || serviceAddress !== undefined) {
    const cacheEntry = {}
    
    if (serviceName !== undefined && serviceName !== null && serviceName !== '') {
      cacheEntry.serviceName = String(serviceName)
    }
    if (serviceAddress !== undefined && serviceAddress !== null && serviceAddress !== '') {
      cacheEntry.serviceAddress = String(serviceAddress)
    }
    
    if (Object.keys(cacheEntry).length > 0) {
      // 存入内部缓存
      serviceInfoCache.set(elementId, cacheEntry)
      console.log('[BpmnCanvas] 已缓存服务信息:', elementId, cacheEntry)
      
      // 同时尝试更新 bpmn-js 标准属性（作为备用）
      try {
        const updates = {}
        if (cacheEntry.serviceName) {
          updates.name = cacheEntry.serviceName
        }
        if (Object.keys(updates).length > 0) {
          modeling.updateProperties(shape, updates)
        }
      } catch (e) {
        console.warn('[BpmnCanvas] 更新 bpmn 属性失败（不影响显示）:', e.message)
      }
    } else {
      // 清除缓存
      serviceInfoCache.delete(elementId)
      console.log('[BpmnCanvas] 已清除服务信息缓存:', elementId)
    }
  }

  // 延迟调用标签更新，确保 bpmn-js 完成重新渲染
  scheduleUpdateServiceTaskLabel(elementId)
}

// 获取默认空白 BPMN XML
const getDefaultXml = () => {
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                   xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                   xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                   xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                   xmlns:flowable="http://flowable.org/bpmn"
                   id="Definitions_1"
                   targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="开始" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="180" y="100" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="168" y="143" width="60" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
}

// 导入 XML
const importXML = async (xml) => {
  if (!modeler) return
  try {
    const result = await modeler.importXML(xml)
    emit('import-done', result)
    // 自动适应画布
    modeler.get('canvas').zoom('fit-viewport', { animate: true, maxZoom: 1 })
    return result
  } catch (error) {
    console.error('[BpmnCanvas] 导入 XML 失败:', error)
    throw error
  }
}

// 获取当前 XML
const getXML = async () => {
  if (!modeler) return ''
  try {
    const result = await modeler.saveXML({ format: true })
    return result.xml
  } catch (error) {
    console.error('[BpmnCanvas] 保存 XML 失败:', error)
    throw error
  }
}

// 获取 SVG
const getSVG = async () => {
  if (!modeler) return ''
  try {
    const result = await modeler.saveSVG()
    return result.svg
  } catch (error) {
    console.error('[BpmnCanvas] 保存 SVG 失败:', error)
    throw error
  }
}

// 添加新节点
const addNode = (type, x, y, options = {}) => {
  if (!modeler) return null

  const elementFactory = modeler.get('elementFactory')
  const modeling = modeler.get('modeling')

  let shape
  switch (type) {
    case 'bpmn:startEvent':
      shape = elementFactory.createShape({ type: 'bpmn:StartEvent', x, y, ...options })
      break
    case 'bpmn:endEvent':
      shape = elementFactory.createShape({ type: 'bpmn:EndEvent', x, y, ...options })
      break
    case 'bpmn:serviceTask':
      shape = elementFactory.createShape({ type: 'bpmn:ServiceTask', x, y, ...options })
      break
    case 'bpmn:exclusiveGateway':
      shape = elementFactory.createShape({ type: 'bpmn:ExclusiveGateway', x, y, ...options })
      break
    default:
      console.warn('[BpmnCanvas] 不支持的节点类型:', type)
      return null
  }

  modeling.createShape(shape, { x, y })
  return shape
}

// 删除选中元素
const removeElement = (element) => {
  if (!modeler || !element) return
  const modeling = modeler.get('modeling')
  modeling.removeElements([element])
}

// 更新元素属性
const updateProperties = (element, properties) => {
  if (!modeler || !element) return
  const modeling = modeler.get('modeling')
  modeling.updateProperties(element, properties)
}

// 获取元素工厂
const getElementFactory = () => {
  return modeler?.get('elementFactory')
}

// 获取元素注册表
const getElementRegistry = () => {
  return modeler?.get('elementRegistry')
}

// 获取画布
const getCanvas = () => {
  return modeler?.get('canvas')
}

// 撤销
const undo = () => {
  modeler?.get('commandStack')?.undo()
}

// 重做
const redo = () => {
  modeler?.get('commandStack')?.redo()
}

// 缩放
const zoomIn = () => {
  const canvas = modeler?.get('canvas')
  const currentZoom = canvas?.zoom() || 1
  canvas?.zoom(currentZoom + 0.1, { animate: true })
}

const zoomOut = () => {
  const canvas = modeler?.get('canvas')
  const currentZoom = canvas?.zoom() || 1
  canvas?.zoom(Math.max(0.1, currentZoom - 0.1), { animate: true })
}

const fitView = () => {
  modeler?.get('canvas')?.zoom('fit-viewport', { animate: true, maxZoom: 1 })
}

// 获取选中元素
const getSelectedElements = () => {
  return modeler?.get('selection')?.getSelectedElements() || []
}

// 选择元素
const selectElement = (element) => {
  modeler?.get('selection')?.select(element)
}
// ==================== 执行状态高亮功能 ====================

// 高亮显示执行中的节点
// 参数说明：
//   completed         : 已完成节点ID数组（绿色 #52C41A）
//   executing         : 正在执行的节点ID（蓝色 #1890FF，带旋转加载动画）
//   failed            : 失败节点ID数组（红色 #F5222D，带 × 图标与错误提示）
//   nodeErrors        : 失败节点错误信息映射 { nodeId: errorMessage }
//   activeConnections : 已触发的连线ID数组，用于显示动态执行路径
const highlightNodes = ({ completed = [], executing = null, failed = [], nodeErrors = {}, activeConnections = [] }) => {
  if (!modeler) {
    console.warn('[BpmnCanvas] highlightNodes: modeler 未初始化')
    return
  }

  console.log('[BpmnCanvas] 🎨 开始更新节点高亮:', { completed, executing, failed, activeConnections })

  const canvas = modeler.get('canvas')
  const elementRegistry = modeler.get('elementRegistry')

  // 清除所有现有高亮与状态图标
  clearAllHighlights()

  // 高亮已触发的连线（执行路径）- 先处理连线，再处理节点，确保节点样式覆盖连线
  // 关键：只有源节点已完成的连线才标记为绿色（已完成）；
  //       源节点失败或未执行（流程中断后的下游连线）则标记为失效（灰色虚线），
  //       避免失败节点后的连线（如连向结束节点的线）仍显示为绿色。
  if (activeConnections && activeConnections.length > 0) {
    const completedSet = new Set(completed)
    activeConnections.forEach((connId, index) => {
      const conn = elementRegistry.get(connId)
      if (conn) {
        try {
          const sourceId = conn.source && conn.source.id
          if (sourceId && !completedSet.has(sourceId)) {
            // 源节点失败或未执行 → 流程已中断，标记为失效虚线
            canvas.addMarker(conn, 'highlight-connection-disabled')
          } else {
            // 源节点已完成 → 正常标记执行路径
            const isLatest = index === activeConnections.length - 1
            if (isLatest && executing) {
              canvas.addMarker(conn, 'highlight-connection-active')
            } else {
              canvas.addMarker(conn, 'highlight-connection-completed')
            }
          }
        } catch (e) {
          console.warn('[BpmnCanvas] 添加连线标记失败:', connId, e)
        }
      }
    })
  }

  // 高亮已完成的节点（绿色 #52C41A）
  completed.forEach(nodeId => {
    const element = elementRegistry.get(nodeId)
    if (element) {
      try {
        canvas.addMarker(element, 'highlight-completed')
        // 注入成功图标 √（24×24px）
        injectStatusIcon(element, 'success')
        console.log('[BpmnCanvas] ✅ 已完成:', nodeId)
      } catch (e) {
        console.warn('[BpmnCanvas] 添加完成标记失败:', nodeId, e)
      }
    }
  })

  // 高亮正在执行的节点（蓝色 #1890FF 脉冲）+ 旋转加载动画
  if (executing) {
    const element = elementRegistry.get(executing)
    if (element) {
      try {
        canvas.addMarker(element, 'highlight-executing')
        // 注入加载动画（顺时针旋转圆形进度条，直径24px）
        injectStatusIcon(element, 'loading')
        console.log('[BpmnCanvas] 🔄 正在执行:', executing)

        // 自动滚动到正在执行的节点
        scrollToElement(element)
      } catch (e) {
        console.warn('[BpmnCanvas] 添加执行中标记失败:', executing, e)
      }
    }
  }

  // 高亮失败的节点（红色 #F5222D）+ × 图标 + 错误提示气泡
  failed.forEach(nodeId => {
    const element = elementRegistry.get(nodeId)
    if (element) {
      try {
        canvas.addMarker(element, 'highlight-failed')
        // 出连线标记为失效（灰色虚线，流程已中断）
        markConnections(element, 'outgoing', 'highlight-connection-disabled')
        // 注入失败图标 ×（24×24px）与错误提示
        injectStatusIcon(element, 'failed', nodeErrors[nodeId])
        console.log('[BpmnCanvas] ❌ 执行失败:', nodeId)
      } catch (e) {
        console.warn('[BpmnCanvas] 添加失败标记失败:', nodeId, e)
      }
    }
  })
}

// 标记连线的辅助方法：direction='incoming'|'outgoing'
const markConnections = (element, direction, marker) => {
  if (!modeler || !element) return
  const canvas = modeler.get('canvas')
  const conns = direction === 'incoming' ? (element.incoming || []) : (element.outgoing || [])
  conns.forEach(conn => {
    try {
      canvas.addMarker(conn, marker)
    } catch (e) { /* 忽略单条连线错误 */ }
  })
}

// 在节点右上角注入状态图标（success=√ / loading=旋转圈 / failed=×）+ 错误提示
const injectStatusIcon = (element, type, errorMsg) => {
  if (!modeler || !element) return
  const elementRegistry = modeler.get('elementRegistry')
  const gfx = elementRegistry.getGraphics(element)
  if (!gfx) return

  let djsVisual = gfx.querySelector('.djs-visual')
  if (!djsVisual) return

  // 移除已有图标组
  const existing = djsVisual.querySelector('.bpmn-status-icon-group')
  if (existing) existing.remove()

  const width = element.width || 100
  const height = element.height || 80
  // 执行中动画图标尺寸自适应：取节点最小边的 18%，限制在 8~12px，确保小巧不遮挡节点
  const minSide = Math.min(width, height)
  const loadingSize = Math.max(8, Math.min(12, Math.round(minSide * 0.18)))
  // 成功/失败状态图标固定 20×20px，置于节点右上角外侧
  const iconSize = type === 'loading' ? loadingSize : 20
  const cx = width + 2
  const cy = -2

  const ns = 'http://www.w3.org/2000/svg'
  const group = document.createElementNS(ns, 'g')
  // 执行中图标定位在节点右上角内侧（贴合节点视觉），成功/失败在右上角外侧
  const posX = type === 'loading' ? (width - loadingSize - 2) : (cx - iconSize / 2)
  const posY = type === 'loading' ? 2 : (cy - iconSize / 2)
  group.setAttribute('class', 'bpmn-status-icon-group')
  group.setAttribute('transform', `translate(${posX}, ${posY})`)

  // 背景圆（白色对比底，确保图标清晰）
  const bg = document.createElementNS(ns, 'circle')
  bg.setAttribute('cx', iconSize / 2)
  bg.setAttribute('cy', iconSize / 2)
  bg.setAttribute('r', iconSize / 2)
  bg.setAttribute('fill', '#FFFFFF')
  bg.setAttribute('stroke', '#FFFFFF')
  group.appendChild(bg)

  if (type === 'success') {
    // 绿色 √（#52C41A）
    const circle = document.createElementNS(ns, 'circle')
    circle.setAttribute('cx', iconSize / 2)
    circle.setAttribute('cy', iconSize / 2)
    circle.setAttribute('r', (iconSize / 2) - 1)
    circle.setAttribute('fill', '#52C41A')
    group.appendChild(circle)
    const check = document.createElementNS(ns, 'path')
    check.setAttribute('d', 'M7 12 L11 16 L17 9')
    check.setAttribute('stroke', '#FFFFFF')
    check.setAttribute('stroke-width', '2.5')
    check.setAttribute('fill', 'none')
    check.setAttribute('stroke-linecap', 'round')
    check.setAttribute('stroke-linejoin', 'round')
    group.appendChild(check)
  } else if (type === 'failed') {
    // 红色 ×（#F5222D）
    const circle = document.createElementNS(ns, 'circle')
    circle.setAttribute('cx', iconSize / 2)
    circle.setAttribute('cy', iconSize / 2)
    circle.setAttribute('r', (iconSize / 2) - 1)
    circle.setAttribute('fill', '#F5222D')
    group.appendChild(circle)
    const cross1 = document.createElementNS(ns, 'path')
    cross1.setAttribute('d', 'M9 9 L15 15')
    cross1.setAttribute('stroke', '#FFFFFF')
    cross1.setAttribute('stroke-width', '2.5')
    cross1.setAttribute('stroke-linecap', 'round')
    group.appendChild(cross1)
    const cross2 = document.createElementNS(ns, 'path')
    cross2.setAttribute('d', 'M15 9 L9 15')
    cross2.setAttribute('stroke', '#FFFFFF')
    cross2.setAttribute('stroke-width', '2.5')
    cross2.setAttribute('stroke-linecap', 'round')
    group.appendChild(cross2)

    // 错误提示气泡（最大宽度300px，自动换行，背景#FFF1F0，边框1px solid #FFCCC7）
    if (errorMsg) {
      const tipGroup = document.createElementNS(ns, 'g')
      tipGroup.setAttribute('class', 'bpmn-error-tooltip-group')
      tipGroup.setAttribute('transform', `translate(${width / 2 - 150}, ${height + 6})`)
      const tipText = document.createElementNS(ns, 'text')
      tipText.setAttribute('class', 'bpmn-error-tooltip')
      tipText.setAttribute('x', 0)
      tipText.setAttribute('y', 14)
      tipText.setAttribute('font-size', '12')
      tipText.setAttribute('fill', '#F5222D')
      // 截断过长错误信息（SVG 文本不自动换行，此处做单行截断展示，完整信息在顶部错误条展示）
      const maxLen = 60
      tipText.textContent = errorMsg.length > maxLen ? errorMsg.substring(0, maxLen) + '...' : errorMsg
      tipGroup.appendChild(tipText)
      group.appendChild(tipGroup)
    }
  } else if (type === 'loading') {
    // 蓝色顺时针旋转圆形进度条（#1890FF，直径24px）
    const spinner = document.createElementNS(ns, 'circle')
    spinner.setAttribute('class', 'bpmn-status-spinner')
    spinner.setAttribute('cx', iconSize / 2)
    spinner.setAttribute('cy', iconSize / 2)
    spinner.setAttribute('r', (iconSize / 2) - 1)
    spinner.setAttribute('fill', 'none')
    spinner.setAttribute('stroke', '#1890FF')
    // 描边宽度随图标尺寸缩放，小图标用细描边
    spinner.setAttribute('stroke-width', iconSize <= 10 ? 1.5 : 2)
    spinner.setAttribute('stroke-linecap', 'round')
    spinner.setAttribute('stroke-dasharray', '30 70')
    group.appendChild(spinner)
    // 内部小圆点，增强"执行中"视觉
    const dot = document.createElementNS(ns, 'circle')
    dot.setAttribute('cx', iconSize / 2)
    dot.setAttribute('cy', iconSize / 2)
    dot.setAttribute('r', iconSize <= 10 ? 1 : 1.5)
    dot.setAttribute('fill', '#1890FF')
    group.appendChild(dot)
  }

  djsVisual.appendChild(group)
}

// 清除所有节点上的状态图标
const clearAllStatusIcons = () => {
  if (!modeler) return
  const elementRegistry = modeler.get('elementRegistry')
  try {
    elementRegistry.getAll().forEach(element => {
      const gfx = elementRegistry.getGraphics(element)
      if (!gfx) return
      const groups = gfx.querySelectorAll('.bpmn-status-icon-group')
      groups.forEach(g => g.remove())
    })
  } catch (e) { /* 忽略 */ }
}

// 清除所有高亮（含连线轨迹标记与状态图标）
const clearAllHighlights = () => {
  if (!modeler) return

  const canvas = modeler.get('canvas')
  const elementRegistry = modeler.get('elementRegistry')

  try {
    // 移除所有元素的标记
    const elements = elementRegistry.getAll()
    elements.forEach(element => {
      try {
        canvas.removeMarker(element, 'highlight-completed')
        canvas.removeMarker(element, 'highlight-executing')
        canvas.removeMarker(element, 'highlight-failed')
        canvas.removeMarker(element, 'highlight-connection-active')
        canvas.removeMarker(element, 'highlight-connection-completed')
        canvas.removeMarker(element, 'highlight-connection-disabled')
      } catch (e) {
        // 忽略单个元素错误
      }
    })

    // 清除所有状态图标
    clearAllStatusIcons()

    console.log('[BpmnCanvas] 🧹 已清除所有高亮')
  } catch (e) {
    console.warn('[BpmnCanvas] 清除高亮失败:', e)
  }
}

// 滚动到指定元素
const scrollToElement = (element) => {
  if (!modeler || !element) return
  
  try {
    const canvas = modeler.get('canvas')
    canvas.scrollIntoView({
      element: element,
      paddingTop: 100,
      paddingBottom: 100,
      paddingLeft: 100,
      paddingRight: 100
    })
  } catch (e) {
    console.warn('[BpmnCanvas] 滚动到元素失败:', e)
  }
}
// ✅ 新增：暴露服务信息缓存给父组件（用于保存时读取）
const getCachedServiceInfo = (elementId) => {
  return serviceInfoCache.get(elementId) || null
}
// 暴露方法给父组件
defineExpose({
  importXML,
  getXML,
  getSVG,
  addNode,
  removeElement,
  updateProperties,
  getElementRegistry,
  getCanvas,
  undo,
  redo,
  zoomIn,
  zoomOut,
  fitView,
  getSelectedElements,
  selectElement,
  getModeler: () => modeler,
  getDefaultXml,
  refreshServiceTaskLabel,
  highlightNodes,
  clearAllHighlights,
  getCachedServiceInfo
})

// 监听初始 XML 变化
watch(() => props.initialXml, (newXml) => {
  if (newXml && modeler) {
    importXML(newXml)
  }
})

onMounted(() => {
  initModeler()
})

onBeforeUnmount(() => {
  if (updateLabelTimer) {
    clearTimeout(updateLabelTimer)
    updateLabelTimer = null
  }
  if (modeler) {
    modeler.destroy()
    modeler = null
  }
})
</script>

<style scoped>
.bpmn-canvas {
  width: 100%;
  height: 100%;
  background: #fafafa;
}

.bpmn-canvas :deep(.djs-container) {
  background: #ffffff;
}

/* 服务任务标签样式 */
.bpmn-canvas :deep(.service-task-custom-label text) {
  pointer-events: none;
  user-select: none;
}
/* ==================== 执行状态高亮样式 ==================== */

/* 已完成 - 绿色边框 + 柔和阴影 */
:deep(.highlight-completed .djs-visual) {
  stroke: #52C41A !important;
  stroke-width: 3px !important;
  filter: drop-shadow(0 0 4px rgba(82, 196, 26, 0.4));
  transition: all 0.3s ease;
}

/* 已完成 - 背景色 */
:deep(.highlight-completed .djs-shape) {
  fill: rgba(82, 196, 26, 0.08) !important;
}

/* 正在执行 - 蓝色边框 + 脉冲动画 */
:deep(.highlight-executing .djs-visual) {
  stroke: #1890FF !important;
  stroke-width: 3px !important;
  animation: pulse-blue 1.5s ease-in-out infinite;
  filter: drop-shadow(0 0 6px rgba(24, 144, 255, 0.6));
}

/* 正在执行 - 背景色 */
:deep(.highlight-executing .djs-shape) {
  fill: rgba(24, 144, 255, 0.10) !important;
}

/* 失败 - 红色边框 + 抖动动画 */
:deep(.highlight-failed .djs-visual) {
  stroke: #F5222D !important;
  stroke-width: 3px !important;
  animation: shake-red 0.5s ease-in-out;
  filter: drop-shadow(0 0 6px rgba(245, 34, 45, 0.7));
}

/* 失败 - 背景色 */
:deep(.highlight-failed .djs-shape) {
  fill: rgba(245, 34, 45, 0.10) !important;
}

/* ========== 动画定义 ========== */

/* 蓝色脉冲动画 - 表示正在处理 */
@keyframes pulse-blue {
  0%, 100% {
    filter: drop-shadow(0 0 3px rgba(24, 144, 255, 0.4));
    stroke-opacity: 1;
    transform: scale(1);
  }
  50% {
    filter: drop-shadow(0 0 10px rgba(24, 144, 255, 0.9));
    stroke-opacity: 0.7;
    transform: scale(1.02);
  }
}

/* 红色抖动动画 - 表示错误/失败 */
@keyframes shake-red {
  0%, 100% { 
    transform: translateX(0); 
  }
  20% { 
    transform: translateX(-3px); 
  }
  40% { 
    transform: translateX(3px); 
  }
  60% { 
    transform: translateX(-2px); 
  }
  80% { 
    transform: translateX(2px); 
  }
}

/* 完成时的闪烁效果（可选） */
@keyframes flash-complete {
  0% { 
    filter: drop-shadow(0 0 8px rgba(82, 196, 26, 1)); 
  }
  100% { 
    filter: drop-shadow(0 0 4px rgba(82, 196, 26, 0.4)); 
  }
}

/* ========== 连接线轨迹高亮（三态颜色与节点一致） ========== */

/* 已完成连线 - 绿色，加粗，带流动动画体现轨迹感 */
:deep(.highlight-connection-completed.djs-connection .djs-visual) {
  stroke: #52C41A !important;
  stroke-width: 4px !important;
  filter: drop-shadow(0 0 3px rgba(82, 196, 26, 0.6));
  stroke-dasharray: 8 4;
  animation: dash-flow-green 1s linear infinite;
}

/* 正在执行连线 - 蓝色，加粗，流动动画（突出当前执行路径） */
:deep(.highlight-connection-active.djs-connection .djs-visual) {
  stroke: #1890FF !important;
  stroke-width: 4px !important;
  filter: drop-shadow(0 0 4px rgba(24, 144, 255, 0.8));
  stroke-dasharray: 8 4;
  animation: dash-flow 0.8s linear infinite;
}

/* 失败连线 - 红色，加粗 */
:deep(.highlight-failed.djs-connection .djs-visual) {
  stroke: #F5222D !important;
  stroke-width: 4px !important;
  filter: drop-shadow(0 0 4px rgba(245, 34, 45, 0.7));
}

/* 流动虚线动画 - 蓝色（执行中） */
@keyframes dash-flow {
  to { stroke-dashoffset: -12; }
}

/* 流动虚线动画 - 绿色（已完成） */
@keyframes dash-flow-green {
  to { stroke-dashoffset: -12; }
}

/* 失效连线 - 灰色虚线（标识流程中断点后的连线，失败节点未执行的后续路径） */
:deep(.highlight-connection-disabled.djs-connection .djs-visual) {
  stroke: #909399 !important;
  stroke-width: 2px !important;
  stroke-dasharray: 8 5 !important;
}

</style>