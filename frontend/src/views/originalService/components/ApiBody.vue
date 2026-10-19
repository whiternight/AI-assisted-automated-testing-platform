<template>
  <div class="api-body-container">
    <div class="body-type-selector">
      <el-radio-group v-model="localBodyType" size="small">
        <el-radio-button value="json">JSON</el-radio-button>
        <el-radio-button value="text">Text</el-radio-button>
        <el-radio-button value="form">Form</el-radio-button>
      </el-radio-group>
    </div>

    <div class="body-editor">
      <el-input
        v-if="localBodyType === 'json' || localBodyType === 'text'"
        v-model="localValue"
        type="textarea"
        :rows="12"
        placeholder="请输入请求体内容"
        @input="handleInput"
      />
      
      <div v-else-if="localBodyType === 'form'" class="form-body">
        <div class="param-row header">
          <div class="col name">Key</div>
          <div class="col value">Value</div>
          <div class="col actions">操作</div>
        </div>
        <div v-for="(item, i) in formItems" :key="item.id" class="param-row">
          <div class="col name"><el-input v-model="item.key" size="small" /></div>
          <div class="col value"><el-input v-model="item.value" size="small" /></div>
          <div class="col actions">
            <el-button type="text" size="small" @click="removeFormItem(i)">删除</el-button>
          </div>
        </div>
        <div style="margin-top:8px">
          <el-button size="small" @click="addFormItem">+添加字段</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  bodyType: {
    type: String,
    default: 'json'
  }
})

const emit = defineEmits(['update:modelValue', 'update:bodyType'])

const localValue = ref(props.modelValue)
const localBodyType = ref(props.bodyType)
const formItems = ref([])

// 监听外部变化
watch(() => props.modelValue, (val) => {
  localValue.value = val
})

watch(() => props.bodyType, (val) => {
  localBodyType.value = val
})

// 处理输入
const handleInput = () => {
  emit('update:modelValue', localValue.value)
}

// 监听bodyType变化
watch(localBodyType, (val) => {
  emit('update:bodyType', val)
})

// Form相关方法
const addFormItem = () => {
  formItems.value.push({
    id: Date.now(),
    key: '',
    value: ''
  })
  
  // 同步到modelValue
  syncFormToModel()
}

const removeFormItem = (index) => {
  formItems.value.splice(index, 1)
  syncFormToModel()
}

const syncFormToModel = () => {
  const obj = {}
  formItems.value.forEach(item => {
    if (item.key && item.key.trim()) {
      obj[item.key.trim()] = item.value || ''
    }
  })
  localValue.value = JSON.stringify(obj, null, 2)
  emit('update:modelValue', localValue.value)
}
</script>

<style lang="scss" scoped>
.api-body-container {
  .body-type-selector {
    margin-bottom: 12px;
  }

  .body-editor {
    :deep(.el-textarea__inner) {
      font-family: 'JetBrains Mono', monospace;
      font-size: 13px;
      line-height: 1.6;
    }
  }

  .form-body {
    .param-row {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;

      &.header {
        font-size: 12px;
        color: var(--ghost, #a3a3a3);
        margin-bottom: 4px;

        .col {
          font-weight: 500;
        }
      }

      .col {
        &.name {
          width: 200px;
        }

        &.value {
          flex: 1;
        }

        &.actions {
          width: 80px;
          text-align: right;
        }
      }
    }
  }
}
</style>
