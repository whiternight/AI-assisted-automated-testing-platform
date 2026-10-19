<template>
  <!-- 遮罩层：点击外部关闭 -->
  <teleport to="body">
    <div v-if="visible" class="record-modal-mask" @mousedown.self="handleMaskClick">
      <!-- 弹窗主体：可拖拽，统一视觉风格（阴影8px、圆角8px、白底） -->
      <div
        class="record-modal"
        :style="{ width: typeof width === 'number' ? width + 'px' : width, left: pos.x + 'px', top: pos.y + 'px' }"
      >
        <!-- 标题栏（拖拽手柄） -->
        <div class="record-modal-header" @mousedown="startDrag">
          <span class="record-modal-title">{{ title }}</span>
          <button class="record-modal-close" @click="handleClose">×</button>
        </div>
        <!-- 内容区 -->
        <div class="record-modal-body">
          <slot></slot>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup>
/**
 * 可拖拽记录弹窗组件
 * ----------------------------------------------------------------------------
 * 视觉规范：阴影深度 8px、圆角 8px、背景色 #FFFFFF
 * 交互：支持拖拽标题栏移动、点击弹窗外部关闭
 * 用途：历史记录 / 执行记录 独立弹窗，可同时打开多个（由父组件控制最多3个）
 */
import { ref, watch, onBeforeUnmount } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '记录' },
  width: { type: [String, Number], default: 720 },
  // 初始位置（相对视口左上角）
  initX: { type: Number, default: 120 },
  initY: { type: Number, default: 100 }
})

const emit = defineEmits(['update:visible', 'close'])

// 弹窗位置（拖拽时更新）
const pos = ref({ x: props.initX, y: props.initY })

// 每次打开时重置位置（避免叠加在已有弹窗上）
watch(() => props.visible, (v) => {
  if (v) {
    // 多弹窗错开定位
    pos.value = { x: props.initX, y: props.initY }
  }
})

// ============ 拖拽逻辑 ============
let dragging = false
let dragStartX = 0
let dragStartY = 0
let posStartX = 0
let posStartY = 0

const startDrag = (e) => {
  // 仅左键拖拽
  if (e.button !== 0) return
  dragging = true
  dragStartX = e.clientX
  dragStartY = e.clientY
  posStartX = pos.value.x
  posStartY = pos.value.y
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
  e.preventDefault()
}

const onDrag = (e) => {
  if (!dragging) return
  const dx = e.clientX - dragStartX
  const dy = e.clientY - dragStartY
  // 限制在视口范围内
  const maxX = window.innerWidth - 80
  const maxY = window.innerHeight - 60
  pos.value = {
    x: Math.max(0, Math.min(maxX, posStartX + dx)),
    y: Math.max(0, Math.min(maxY, posStartY + dy))
  }
}

const stopDrag = () => {
  dragging = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
}

// ============ 关闭逻辑 ============
// 点击遮罩（弹窗外部）关闭
const handleMaskClick = () => {
  handleClose()
}

const handleClose = () => {
  emit('update:visible', false)
  emit('close')
}

// 组件卸载时清理事件监听
onBeforeUnmount(() => {
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
})
</script>

<style scoped>
/* 遮罩：半透明，点击即关闭弹窗 */
.record-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(0, 0, 0, 0.25);
}

/* 弹窗主体：阴影8px、圆角8px、白底，绝对定位支持拖拽 */
.record-modal {
  position: absolute;
  background: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  max-height: 80vh;
  overflow: hidden;
}

/* 标题栏：拖拽手柄 */
.record-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  cursor: move;
  user-select: none;
  background: #fafafa;
  border-radius: 8px 8px 0 0;
}

.record-modal-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.record-modal-close {
  border: none;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  color: #9ca3af;
  cursor: pointer;
  padding: 0 4px;
}

.record-modal-close:hover {
  color: #F5222D;
}

/* 内容区：可滚动 */
.record-modal-body {
  padding: 16px;
  overflow-y: auto;
  flex: 1;
}
</style>
