<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ParticipantRecordTable from '@/components/ParticipantRecordTable.vue'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage, downloadBlob } from '@/api/http'
import { submitParticipant } from '@/utils/participantActions'

const visible = defineModel({ required: true })
const props = defineProps({
  meetingId: { type: String, required: true },
  targetElementId: { type: String, default: undefined },
  participants: { type: Array, default: () => [] },
  fieldDefinitions: { type: Array, default: () => [] },
})
const emit = defineEmits(['done'])
const tableRef = ref()
const file = ref()
const preview = ref()
const importing = ref(false)
const submitting = ref(false)
const allowMultipleRows = computed(() => !props.targetElementId)
const form = reactive({
  records: [],
  customFields: [],
})

function blankRow() {
  return {
    employeeNo: '',
    name: '',
    attributes: {},
    createdInDialog: true,
  }
}

function resetForm() {
  form.records = [blankRow()]
  form.customFields = []
  file.value = undefined
  preview.value = undefined
}

watch(
  () => [visible.value, props.targetElementId],
  () => {
    if (visible.value) resetForm()
  },
  { immediate: true },
)

function addRow() {
  if (!allowMultipleRows.value) return
  tableRef.value?.addRecord()
}

function addTargetElementId(index) {
  return props.targetElementId && index === 0 ? props.targetElementId : undefined
}

function onFileChange(uploadFile) {
  file.value = uploadFile.raw
  preview.value = undefined
}

async function downloadTemplate() {
  importing.value = true
  try {
    const template = await meetingApi.importTemplate()
    downloadBlob(
      template,
      '参会人员导入模板.xlsx',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    )
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    importing.value = false
  }
}

async function parseFile() {
  if (!file.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  importing.value = true
  try {
    preview.value = await meetingApi.previewImport(props.meetingId, file.value)
    applyPreviewRows(preview.value)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    importing.value = false
  }
}

function knownFieldKeys() {
  return new Set(
    (props.fieldDefinitions || [])
      .flatMap((field) => [field.code, field.label])
      .map((value) => String(value || '').trim().toLocaleLowerCase())
      .filter(Boolean),
  )
}

function addImportedField(fieldName, knownKeys) {
  const label = String(fieldName || '').trim()
  if (!label || knownKeys.has(label.toLocaleLowerCase())) return
  if (form.customFields.some((field) => field.label === label || field.code === label)) return
  form.customFields.push({
    id: label,
    code: label,
    label,
    custom: true,
  })
}

function applyPreviewRows(nextPreview) {
  const rows = nextPreview?.rows || []
  const knownKeys = knownFieldKeys()
  rows.forEach((row) => {
    Object.keys(row.attributes || {}).forEach((fieldName) => addImportedField(fieldName, knownKeys))
  })
  form.records = rows.length
    ? rows.map((row) => ({
        employeeNo: row.employeeNo || '',
        name: row.name || '',
        attributes: { ...(row.attributes || {}) },
        sourceRow: row.sourceRow,
        expectedAction: row.expectedAction,
        createdInDialog: true,
      }))
    : [blankRow()]
}

function normalizedKey(value) {
  return String(value || '').trim().toLocaleLowerCase()
}

function normalizedRows() {
  return form.records.map((row) => ({
    ...row,
    employeeNo: String(row.employeeNo || '').trim(),
    name: String(row.name || '').trim(),
    attributes: { ...(row.attributes || {}) },
    customFields: form.customFields,
    fieldDefinitions: props.fieldDefinitions,
  }))
}

function rowSignature(row) {
  const attributes = Object.entries(row.attributes || {})
    .map(([key, value]) => [String(key).trim(), String(value || '').trim()])
    .filter(([key, value]) => key && value)
    .sort(([left], [right]) => left.localeCompare(right))
  return JSON.stringify([normalizedKey(row.employeeNo), row.name.trim(), attributes])
}

function uniqueSubmitRows(rows) {
  const seen = new Set()
  const uniqueRows = []
  let skipped = 0
  for (const row of rows) {
    const key = rowSignature(row)
    if (seen.has(key)) {
      skipped++
      continue
    }
    seen.add(key)
    uniqueRows.push(row)
  }
  if (skipped) ElMessage.warning(`存在 ${skipped} 条完全相同的人员记录，已跳过重复项`)
  return uniqueRows
}

function validateBatchNames(rows) {
  const namesByEmployeeNo = new Map()
  for (const row of rows) {
    const key = normalizedKey(row.employeeNo)
    const existingName = namesByEmployeeNo.get(key)
    if (existingName && existingName !== row.name) {
      ElMessage.warning(`工号 ${row.employeeNo} 对应了多个姓名`)
      return false
    }
    namesByEmployeeNo.set(key, row.name)
  }
  return true
}

async function submit() {
  if (!tableRef.value?.validateCustomFields()) return
  if (!tableRef.value?.validateRecords()) return
  const rows = uniqueSubmitRows(normalizedRows())
  if (!rows.length || !validateBatchNames(rows)) return
  submitting.value = true
  try {
    const results = []
    for (const [index, row] of rows.entries()) {
      const participant = await submitParticipant({ addParticipant: meetingApi.addParticipant, meetingId: props.meetingId, form: row, targetElementId: addTargetElementId(index), })
      results.push(participant)
    }
    const messages = results.map((participant) => participant?.message).filter(Boolean)
    ElMessage.success(messages.length ? messages.join('；') : '人员已加入待排列表')
    visible.value = false
    emit('done', results)
    resetForm()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="新增人员"
    width="980px"
    class="add-participant-dialog"
  >
    <el-form
      :model="form"
      :validate-on-rule-change="false"
      label-position="top"
      class="participant-form-scroll"
    >
      <section v-if="allowMultipleRows" class="import-card import-card--template">
        <div>
          <strong>导入Excel</strong>
          <p>导入后会先填入下方表格，确认无误后再增加到待排人员。</p>
        </div>
        <div class="import-actions">
          <el-button :icon="Download" :loading="importing" @click="downloadTemplate">
            下载模板
          </el-button>
          <el-upload
            class="upload-surface"
            :auto-upload="false"
            :limit="1"
            accept=".xlsx"
            :on-change="onFileChange"
            :on-remove="() => (file = undefined)"
          >
            <el-button :icon="UploadFilled" :loading="importing">选择Excel</el-button>
          </el-upload>
          <el-button type="primary" plain :loading="importing" @click="parseFile">
            解析
          </el-button>
        </div>
      </section>

      <section v-if="preview" class="import-preview-section">
        <el-alert
          v-for="error in preview.errors"
          :key="error"
          :title="error"
          type="error"
          :closable="false"
          show-icon
        />
        <div class="preview-summary">
          <span>总行数 {{ preview.totalRows }}</span>
          <span>人员数 {{ preview.participantCount }}</span>
          <span>记录数 {{ preview.recordCount }}</span>
          <span>去重 {{ preview.ignoredDuplicateRows }}</span>
        </div>
      </section>

      <p v-if="targetElementId" class="seat-add-note">保存后会直接安排到所选空座。</p>

      <ParticipantRecordTable
        ref="tableRef"
        v-model:records="form.records"
        v-model:custom-fields="form.customFields"
        :field-definitions="fieldDefinitions"
        :include-fixed-fields="true"
        :allow-add-rows="allowMultipleRows"
        :allow-remove-rows="allowMultipleRows"
        :show-row-status="allowMultipleRows"
        table-height="min(42vh, 420px)"
        add-row-label="添加人员"
        add-column-label="添加列"
      />

      <button
        v-if="allowMultipleRows"
        type="button"
        class="sr-only-add-row"
        aria-label="添加行"
        @click="addRow"
      />
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        增加
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.participant-form-scroll {
  height: 100%;
  max-height: none;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 4px;
}

:global(.add-participant-dialog) {
  height: min(80vh, 760px);
  margin-top: min(7vh, 44px);
  display: flex;
  flex-direction: column;
}

:global(.add-participant-dialog .el-dialog__body) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

:global(.add-participant-dialog .el-dialog__footer) {
  flex: 0 0 auto;
}

.import-card {
  display: grid;
  gap: 14px;
  padding: 14px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
}

.import-card--template {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  background: #fbfcfd;
}

.import-card--template p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.import-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.upload-surface {
  display: inline-flex;
}

.upload-surface :deep(.el-upload-list) {
  display: none;
}

.import-preview-section {
  max-height: 136px;
  display: grid;
  gap: 8px;
  margin-top: 10px;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 2px;
}

.preview-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--muted);
  font-size: 12px;
}

.preview-summary span {
  padding: 4px 8px;
  background: #f3f6fb;
  border: 1px solid var(--line);
  border-radius: 7px;
}

.seat-add-note {
  margin: 0 0 6px;
  color: var(--muted);
  font-size: 12px;
}

.sr-only-add-row {
  width: 1px;
  height: 1px;
  position: absolute;
  left: 0;
  bottom: 0;
  padding: 0;
  opacity: 0;
  pointer-events: none;
}
</style>
