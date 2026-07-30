<script setup>
import { computed, nextTick, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { canAddParticipantRecord, groupableFields } from '@/utils/participantFields'

const records = defineModel('records', { type: Array, required: true })
const customFields = defineModel('customFields', { type: Array, default: () => [] })

const props = defineProps({
  fieldDefinitions: { type: Array, default: () => [] },
  includeFixedFields: { type: Boolean, default: false },
  allowAddRows: { type: Boolean, default: true },
  allowAddColumns: { type: Boolean, default: true },
  allowRemoveRows: { type: Boolean, default: true },
  showRowStatus: { type: Boolean, default: false },
  tableHeight: { type: String, default: 'min(36vh, 360px)' },
  minRows: { type: Number, default: 1 },
  addRowLabel: { type: String, default: '添加记录' },
  addColumnLabel: { type: String, default: '添加列' },
})

const recordTableRef = ref()
const dynamicFields = computed(() => groupableFields(props.fieldDefinitions))
const recordFields = computed(() => [
  ...dynamicFields.value.map((field) => ({
    id: field.code,
    code: field.code,
    label: field.label,
    custom: false,
  })),
  ...(customFields.value || []),
])
let customFieldSerial = 0

function blankRecord() {
  const record = { attributes: {}, createdInDialog: true }
  if (props.includeFixedFields) {
    record.employeeNo = ''
    record.name = ''
  }
  return record
}

function addRecord() {
  if (!canAddParticipantRecord(records.value)) {
    ElMessage.warning('请先填写已新增记录的数据')
    return false
  }
  records.value.push(blankRecord())
  scrollToNewestRecord()
  return true
}

function removeRecord(index) {
  if (!props.allowRemoveRows || records.value.length <= props.minRows) return
  records.value.splice(index, 1)
}

function addColumn(label = '') {
  const normalizedLabel = String(label || '').trim()
  const id = normalizedLabel || `custom-field-${++customFieldSerial}`
  if (
    normalizedLabel &&
    !customFields.value.some((field) => field.label === normalizedLabel || field.code === normalizedLabel)
  ) {
    customFields.value.push({
      id,
      code: normalizedLabel,
      label: normalizedLabel,
      custom: true,
    })
  } else if (!normalizedLabel) {
    customFields.value.push({
      id,
      code: id,
      label: '',
      custom: true,
    })
  }
  records.value.forEach((record) => {
    if (!Object.prototype.hasOwnProperty.call(record.attributes, id)) {
      record.attributes[id] = record.attributes[normalizedLabel] || ''
    }
  })
  scrollToNewestColumn()
}

function removeCustomColumn(field) {
  customFields.value = customFields.value.filter((item) => item.id !== field.id)
  records.value.forEach((record) => {
    delete record.attributes[field.code]
    delete record.attributes[field.id]
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

function scrollToNewestRecord() {
  nextTick(() => {
    const table = recordTableRef.value
    if (typeof table?.setScrollTop === 'function') {
      table.setScrollTop(Number.MAX_SAFE_INTEGER)
      return
    }
    const scrollWrap = table?.$el?.querySelector?.('.el-scrollbar__wrap')
    if (scrollWrap) scrollWrap.scrollTop = scrollWrap.scrollHeight
  })
}

function normalizeFieldName(value) {
  return String(value || '').trim().toLocaleLowerCase()
}

function fieldNameCandidates(field) {
  return [field?.code, field?.label]
    .map((value) => normalizeFieldName(value))
    .filter(Boolean)
}

function validateCustomFields() {
  const existing = new Set(dynamicFields.value.flatMap(fieldNameCandidates))
  const seen = new Set()
  for (const field of customFields.value || []) {
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

function validateRecords() {
  if (!props.includeFixedFields) return true
  for (const [index, record] of records.value.entries()) {
    if (!String(record.employeeNo || '').trim()) {
      ElMessage.warning(`第 ${index + 1} 行工号不能为空`)
      return false
    }
    if (!String(record.name || '').trim()) {
      ElMessage.warning(`第 ${index + 1} 行姓名不能为空`)
      return false
    }
  }
  return true
}

defineExpose({
  addRecord,
  addColumn,
  validateCustomFields,
  validateRecords,
})
</script>

<template>
  <section class="record-table-section">
    <header class="record-tools">
      <span>记录数据</span>
      <div class="record-actions">
        <el-button
          v-if="allowAddRows"
          size="small"
          :icon="Plus"
          @click="addRecord"
        >
          {{ addRowLabel }}
        </el-button>
        <el-button
          v-if="allowAddColumns"
          size="small"
          :icon="Plus"
          @click="addColumn()"
        >
          {{ addColumnLabel }}
        </el-button>
      </div>
    </header>
    <div class="record-table-wrap">
      <el-table
        ref="recordTableRef"
        :data="records"
        :height="tableHeight"
        border
        scrollbar-always-on
        size="small"
        :empty-text="recordFields.length ? '暂无记录' : '暂无扩展字段'"
      >
        <el-table-column fixed label="记录" width="82">
          <template #default="{ $index }">
            <span class="record-row-index">记录 {{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column
          v-if="includeFixedFields"
          fixed
          label="工号"
          width="168"
        >
          <template #default="{ row }">
            <el-input v-model="row.employeeNo" :validate-event="false" />
          </template>
        </el-table-column>
        <el-table-column
          v-if="includeFixedFields"
          fixed
          label="姓名"
          width="150"
        >
          <template #default="{ row }">
            <el-input v-model="row.name" :validate-event="false" />
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
        <el-table-column fixed="right" label="状态" width="150" v-if="showRowStatus">
          <template #default="{ row }">
            <span class="record-row-status">{{ row.expectedAction || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          v-if="allowRemoveRows"
          fixed="right"
          label="操作"
          width="86"
        >
          <template #default="{ $index }">
            <el-button
              text
              type="danger"
              :disabled="records.length <= minRows"
              @click="removeRecord($index)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<style scoped>
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

.record-row-index,
.record-row-status {
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
