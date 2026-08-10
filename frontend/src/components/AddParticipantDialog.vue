<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Close, Download, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ParticipantRecordTable from '@/components/ParticipantRecordTable.vue'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage, downloadBlob } from '@/api/http'
import { submitParticipant } from '@/utils/participantActions'
import { mergePreviewRowsIntoParticipantDraft } from '@/utils/participantFields'

const visible = defineModel({ required: true })
const props = defineProps({
  meetingId: { type: String, required: true },
  targetElementId: { type: String, default: undefined },
  participants: { type: Array, default: () => [] },
  fieldDefinitions: { type: Array, default: () => [] },
})
const emit = defineEmits(['done'])
const tableRef = ref()
const uploadRef = ref()
const file = ref()
const preview = ref()
const templateDownloading = ref(false)
const previewing = ref(false)
const submitting = ref(false)
const allowMultipleRows = computed(() => !props.targetElementId)
const selectedFileName = computed(() => file.value?.name || '')
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
    if (visible.value) {
      resetForm()
    }
  },
  { immediate: true },
)

function addRow() {
  if (!allowMultipleRows.value) {
    return
  }
  tableRef.value?.addRecord()
}

function addTargetElementId(index) {
  return props.targetElementId && index === 0 ? props.targetElementId : undefined
}

function setSelectedFile(nextFile) {
  file.value = nextFile
  preview.value = undefined
}

function onFileChange(uploadFile) {
  setSelectedFile(uploadFile.raw || uploadFile)
}

function onFileExceed(files) {
  const nextFile = files?.[0]
  if (!nextFile) {
    return
  }
  uploadRef.value?.clearFiles()
  setSelectedFile(nextFile)
}

function clearFile() {
  uploadRef.value?.clearFiles()
  file.value = undefined
  preview.value = undefined
}

async function downloadTemplate() {
  if (templateDownloading.value) {
    return
  }
  templateDownloading.value = true
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
    templateDownloading.value = false
  }
}

async function parseFile() {
  if (previewing.value) {
    return
  }
  if (submitting.value) {
    return
  }
  if (!file.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  previewing.value = true
  try {
    preview.value = await meetingApi.previewImport(props.meetingId, file.value)
    applyPreviewRows(preview.value)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    previewing.value = false
  }
}

function applyPreviewRows(nextPreview) {
  const result = mergePreviewRowsIntoParticipantDraft({
    records: form.records,
    customFields: form.customFields,
    fieldDefinitions: props.fieldDefinitions,
    preview: nextPreview,
  })
  form.records = result.records
  form.customFields = result.customFields
  if (result.appendedCount) {
    ElMessage.success(`已追加 ${result.appendedCount} 条解析记录`)
  }
  if (result.skippedDuplicateCount) {
    ElMessage.warning(`已跳过 ${result.skippedDuplicateCount} 条完全相同记录`)
  }
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
  if (skipped) {
    ElMessage.warning(`存在 ${skipped} 条完全相同的人员记录，已跳过重复项`)
  }
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
  if (submitting.value) {
    return
  }
  if (!tableRef.value?.validateCustomFields()) {
    return
  }
  if (!tableRef.value?.validateRecords()) {
    return
  }
  const rows = uniqueSubmitRows(normalizedRows())
  if (!rows.length || !validateBatchNames(rows)) {
    return
  }
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
      :disabled="submitting"
      v-loading="submitting"
      label-position="top"
      class="participant-form-scroll"
    >
      <section v-if="allowMultipleRows" class="import-card import-card--template">
        <div>
          <strong>导入Excel</strong>
        </div>
        <div class="import-actions">
          <el-button
            :icon="Download"
            :loading="templateDownloading"
            :disabled="previewing || submitting"
            @click="downloadTemplate"
          >
            下载模板
          </el-button>
          <el-upload
            ref="uploadRef"
            class="upload-surface"
            :auto-upload="false"
            :show-file-list="false"
            :limit="1"
            accept=".xlsx"
            :on-change="onFileChange"
            :on-exceed="onFileExceed"
            :on-remove="clearFile"
          >
            <el-button :icon="UploadFilled" :disabled="previewing || submitting">选择Excel</el-button>
          </el-upload>
          <span v-if="selectedFileName" class="selected-file-name" :title="selectedFileName">
            <span class="selected-file-text">{{ selectedFileName }}</span>
            <button
              type="button"
              class="selected-file-remove"
              :disabled="previewing || submitting || templateDownloading"
              aria-label="移除已选Excel"
              @click="clearFile"
            >
              <Close />
            </button>
          </span>
          <el-button
            type="primary"
            plain
            :loading="previewing"
            :disabled="previewing || !file"
            @click="parseFile"
          >
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
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
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

.import-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.upload-surface {
  display: inline-flex;
}

.selected-file-name {
  max-width: 170px;
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 9px;
  color: var(--muted);
  background: #f3f6fb;
  border: 1px solid var(--line);
  border-radius: 8px;
  font-size: 12px;
}

.selected-file-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-file-remove {
  width: 18px;
  height: 18px;
  flex: none;
  display: grid;
  place-items: center;
  padding: 0;
  color: #94a3b8;
  background: transparent;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
}

.selected-file-remove:hover {
  color: var(--brand);
  background: #e8f1ff;
}

.selected-file-remove svg {
  width: 12px;
  height: 12px;
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
