<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage } from '@/api/http'
import { groupableFields } from '@/utils/participantFields'
import { updateParticipantDetails } from '@/utils/participantActions'

const visible = defineModel({ required: true })
const props = defineProps({
  meetingId: { type: String, required: true },
  participant: { type: Object, default: undefined },
  fieldDefinitions: { type: Array, default: () => [] },
})
const emit = defineEmits(['done'])
const formRef = ref()
const recordTableRef = ref()
const submitting = ref(false)
const recordTableHeight = 'min(36vh, 360px)'
const form = reactive({
  employeeNo: '',
  name: '',
  records: [],
  customFields: [],
  fieldDefinitions: [],
})
const dynamicFields = computed(() => groupableFields(props.fieldDefinitions))
const recordFields = computed(() => [
  ...dynamicFields.value.map((field) => ({
    id: field.code,
    code: field.code,
    label: field.label,
    custom: false,
  })),
  ...form.customFields,
])
const rules = {
  name: [{ required: true, message: '请输入姓名' }],
}
let customFieldSerial = 0

function normalizeRecords(participant) {
  if (participant?.records?.length) {
    return participant.records.map((record, index) => ({
      id: record.id,
      recordOrder: record.recordOrder || index + 1,
      attributes: { ...(record.attributes || {}) },
    }))
  }
  return [
    {
      id: undefined,
      recordOrder: 1,
      attributes: { ...(participant?.primaryAttributes || {}) },
    },
  ]
}

function resetForm() {
  form.employeeNo = props.participant?.employeeNo || ''
  form.name = props.participant?.name || ''
  form.records = normalizeRecords(props.participant)
  form.customFields = []
  form.fieldDefinitions = props.fieldDefinitions
}

watch(
  () => [visible.value, props.participant, props.fieldDefinitions],
  () => {
    if (visible.value) resetForm()
  },
  { immediate: true },
)

function normalizeFieldName(value) {
  return String(value || '').trim().toLocaleLowerCase()
}

function addRecord() {
  form.records.push({
    id: undefined,
    recordOrder: form.records.length + 1,
    attributes: {},
  })
}

function removeRecord(index) {
  if (form.records.length <= 1) return
  form.records.splice(index, 1)
}

function addColumn() {
  const id = `custom-field-${++customFieldSerial}`
  form.customFields.push({
    id,
    code: id,
    label: '',
    custom: true,
  })
  form.records.forEach((record) => {
    if (!Object.prototype.hasOwnProperty.call(record.attributes, id)) {
      record.attributes[id] = ''
    }
  })
  scrollToNewestColumn()
}

function removeCustomColumn(field) {
  form.customFields = form.customFields.filter((item) => item.id !== field.id)
  form.records.forEach((record) => {
    delete record.attributes[field.code]
  })
}

function scrollToNewestColumn() {
  nextTick(() => {
    const table = recordTableRef.value
    if (typeof table?.setScrollLeft === 'function') {
      table.setScrollLeft(Number.MAX_SAFE_INTEGER)
      return
    }
    const scrollWrap = table?.$el?.querySelector?.('.el-scrollbar__wrap')
    if (scrollWrap) scrollWrap.scrollLeft = scrollWrap.scrollWidth
  })
}

function fieldNameCandidates(field) {
  return [field?.code, field?.label]
    .map((value) => normalizeFieldName(value))
    .filter(Boolean)
}

function validateCustomFields() {
  const existing = new Set(dynamicFields.value.flatMap(fieldNameCandidates))
  const seen = new Set()
  for (const field of form.customFields) {
    const key = normalizeFieldName(field.label)
    if (!key) {
      ElMessage.warning('请输入列名')
      return false
    }
    if (existing.has(key) || seen.has(key)) {
      ElMessage.warning('该字段已存在，请使用其他列名')
      return false
    }
    seen.add(key)
  }
  return true
}

async function submit() {
  if (!props.participant?.id) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!validateCustomFields()) return
  form.fieldDefinitions = props.fieldDefinitions
  submitting.value = true
  try {
    const participant = await updateParticipantDetails({
      updateParticipant: meetingApi.updateParticipant,
      meetingId: props.meetingId,
      participantId: props.participant.id,
      form,
    })
    ElMessage.success('人员信息已更新')
    visible.value = false
    emit('done', participant)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="编辑人员" width="900px">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :validate-on-rule-change="false"
      label-position="top"
      class="participant-form-scroll"
    >
      <div class="form-grid">
        <el-form-item label="工号">
          <el-input v-model="form.employeeNo" disabled :validate-event="false" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" :validate-event="false" />
        </el-form-item>
      </div>

      <section class="record-table-section">
        <header class="record-tools">
          <span>记录数据</span>
          <div class="record-actions">
            <el-button size="small" :icon="Plus" @click="addRecord">添加记录</el-button>
            <el-button size="small" :icon="Plus" @click="addColumn">添加列</el-button>
          </div>
        </header>
        <div class="record-table-wrap">
          <el-table
            ref="recordTableRef"
            :data="form.records"
            :height="recordTableHeight"
            border
            scrollbar-always-on
            size="small"
            :empty-text="recordFields.length ? '暂无记录' : '暂无扩展字段'"
          >
            <el-table-column fixed label="记录" width="96">
              <template #default="{ $index }">
                <span class="record-row-index">记录 {{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column
              v-for="field in recordFields"
              :key="field.id"
              :min-width="170"
            >
              <template #header>
                <div class="field-header">
                  <el-input
                    v-if="field.custom"
                    v-model="field.label"
                    class="custom-field-name-input"
                    aria-label="新增列名"
                    maxlength="32"
                    :validate-event="false"
                  />
                  <span v-else>{{ field.label }}</span>
                  <el-button
                    v-if="field.custom"
                    text
                    type="danger"
                    :icon="Delete"
                    :aria-label="`删除新增列 ${field.label}`"
                    @click.stop="removeCustomColumn(field)"
                  />
                </div>
              </template>
              <template #default="{ row }">
                <el-input
                  v-model="row.attributes[field.code]"
                  maxlength="80"
                  :validate-event="false"
                />
              </template>
            </el-table-column>
            <el-table-column fixed="right" label="操作" width="86">
              <template #default="{ $index }">
                <el-button
                  text
                  type="danger"
                  :disabled="form.records.length <= 1"
                  @click="removeRecord($index)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.participant-form-scroll {
  max-height: min(62vh, 620px);
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 4px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}

.record-table-section {
  display: grid;
  gap: 10px;
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.record-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.record-tools > span {
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
}

.record-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.record-table-wrap {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.record-table-wrap :deep(.el-table) {
  min-width: 100%;
}

.record-table-wrap :deep(.el-input__wrapper) {
  box-shadow: none;
}

.record-row-index {
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
}

.field-header {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.custom-field-name-input {
  min-width: 118px;
}

.field-header span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
