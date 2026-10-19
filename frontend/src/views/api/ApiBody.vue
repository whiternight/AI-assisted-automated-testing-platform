<template>
  <div class="api-body">
    <div class="types-bar">
      <el-button v-for="t in types" :key="t" size="small" type="text" :class="['type-btn', {active: t===bodyType} ]" @click="setType(t)">{{ t }}</el-button>
    </div>

    <div class="body-subtypes" v-if="bodyType==='Body' || bodyType==='JSON'">
      <!-- reserved for subtype tabs like none/form-data/JSON -->
    </div>

    <div class="body-area" :class="{'dark': true}">
      <div v-if="bodyType === 'none'" class="placeholder">该请求没有 Body</div>

      <div v-else-if="bodyType === 'form-data' || bodyType === 'x-www-form-urlencoded'">
        <div class="kv-table">
          <div class="kv-row header">
            <div class="col key">Key</div>
            <div class="col value">Value</div>
            <div class="col action">操作</div>
          </div>
          <div v-for="(f, idx) in fields" :key="f.id" class="kv-row">
            <div class="col key"><el-input v-model="f.key" size="small" clearable/></div>
            <div class="col value"><el-input v-model="f.value" size="small" clearable/></div>
            <div class="col action"><el-button type="text" size="small" @click="removeField(idx)">删除</el-button></div>
          </div>
          <div style="margin-top:8px"><el-button size="small" @click="addField">添加字段</el-button></div>
        </div>
      </div>

      <div v-else-if="bodyType === 'Binary'">
        <input type="file" @change="onFileChange" />
        <div v-if="fileName" class="file-name">已选择：{{ fileName }}</div>
      </div>

      <div v-else>
        <el-input type="textarea" :rows="12" v-model="localValue" class="body-textarea" placeholder='{"key":"value"}'/>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, toRefs, watch, ref } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Array, Object], default: '' },
  bodyType: { type: String, default: 'none' }
})
const emit = defineEmits(['update:modelValue', 'update:bodyType'])

const types = ['none','form-data','x-www-form-urlencoded','JSON','XML','Text','Binary','GraphQL','msgpack']

const localValue = ref(typeof props.modelValue === 'string' ? props.modelValue : JSON.stringify(props.modelValue, null, 2))
const fields = reactive([])
const fileName = ref('')

watch(() => props.modelValue, v => {
  localValue.value = typeof v === 'string' ? v : JSON.stringify(v, null, 2)
})

watch(localValue, v => emit('update:modelValue', v))

const setType = (t) => {
  emit('update:bodyType', t)
  if (t === 'none') {
    emit('update:modelValue', '')
  }
}

const addField = () => fields.push({ id: `f_${Date.now()}`, key: '', value: '' })
const removeField = (i) => fields.splice(i,1)

const onFileChange = (e) => {
  const f = e.target.files && e.target.files[0]
  if (f) {
    fileName.value = f.name
    emit('update:modelValue', f)
  }
}

// initialize one empty field for form-data
addField()
</script>

<style scoped>
.api-body { padding: 8px 4px }
.types-bar { display:flex; gap:8px; align-items:center; padding-bottom:8px }
.type-btn { color:var(--ghost, #a3a3a3); padding:6px 10px; border:1px solid var(--mute, #e5e5e5); background:transparent }
.type-btn.active { background:var(--ink, #0a0a0a); color:#fff; border-color:var(--ink, #0a0a0a) }
.body-area.dark { background:var(--smoke, #f5f5f5); color:var(--ink, #0a0a0a); padding:12px; border:1px solid var(--mute, #e5e5e5); min-height:120px }
.placeholder { color:var(--ghost, #a3a3a3); padding:36px 8px; text-align:center }
.body-textarea .el-input__inner { background:transparent; border: none; color: var(--ink, #0a0a0a); min-height:160px }
.kv-table { background:transparent }
.kv-row { display:flex; gap:8px; align-items:center; padding:6px 0 }
.kv-row.header { font-weight:500; color:var(--ghost, #a3a3a3); font-size:10px; text-transform:uppercase; letter-spacing:0.06em }
.kv-row .col.key { width:180px }
.kv-row .col.value { flex:1 }
.kv-row .col.action { width:80px; text-align:center }
.file-name { color:var(--ink, #0a0a0a); margin-top:8px }
</style>
