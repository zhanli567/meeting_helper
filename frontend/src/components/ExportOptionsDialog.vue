<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const visible = defineModel({ required: true })
const props = defineProps({
  fieldDefinitions: { type: Array, default: () => [] },
  submitting: { type: Boolean, default: false },
})
const emit = defineEmits(['export'])

const activeTab = ref('sheets')
const confirmCollapse = ref(['sheets', 'participants', 'layout', 'seatDetails'])
const dynamicFields = computed(() =>
  (props.fieldDefinitions || []).filter((field) => !['name', 'employeeNo'].includes(field.code)),
)
const form = reactive({
  participants: {
    enabled: false,
    fieldCodes: [],
    includeAttendance: true,
    includeSeatLabel: true,
  },
  layout: {
    enabled: false,
    fieldCodes: [],
    colorFieldCodes: [],
  },
  seatDetails: {
    enabled: false,
    fieldCodes: [],
    includeOccupancyType: true,
    includeRegionName: true,
    includeParticipant: true,
  },
})

const sheetDefinitions = [
  { key: 'participants', label: '人员名单', tabLabel: '人员名单配置' },
  { key: 'layout', label: '排座图', tabLabel: '排座图配置' },
  { key: 'seatDetails', label: '座位明细', tabLabel: '座位明细配置' },
]
const selectedSheetDefinitions = computed(() =>
  sheetDefinitions.filter((sheet) => form[sheet.key].enabled),
)
const exportTabs = computed(() => [
  { name: 'sheets', label: '选择子表' },
  ...selectedSheetDefinitions.value.map((sheet) => ({
    name: sheet.key,
    label: sheet.tabLabel,
  })),
  { name: 'confirm', label: '确认导出' },
])
const currentTabIndex = computed(() =>
  exportTabs.value.findIndex((tab) => tab.name === activeTab.value),
)
const isFirstTab = computed(() => currentTabIndex.value <= 0)
const isConfirmTab = computed(() => activeTab.value === 'confirm')
const availableLayoutColorFields = computed(() =>
  dynamicFields.value.filter((field) => form.layout.fieldCodes.includes(field.code)),
)
const layoutFieldCodes = computed({
  get: () => form.layout.fieldCodes,
  set: (codes) => {
    form.layout.fieldCodes = codes
  },
})
const layoutColorFieldCodes = computed({
  get: () => form.layout.colorFieldCodes,
  set: (codes) => {
    form.layout.colorFieldCodes = codes
  },
})
const selectedSheetCount = computed(() => selectedSheetDefinitions.value.length)

function resetOptions() {
  const codes = dynamicFields.value.map((field) => field.code)
  activeTab.value = 'sheets'
  confirmCollapse.value = ['sheets', 'participants', 'layout', 'seatDetails']
  form.participants = {
    enabled: false,
    fieldCodes: [...codes],
    includeAttendance: true,
    includeSeatLabel: true,
  }
  form.layout = {
    enabled: false,
    fieldCodes: [],
    colorFieldCodes: [],
  }
  form.seatDetails = {
    enabled: false,
    fieldCodes: [...codes],
    includeOccupancyType: true,
    includeRegionName: true,
    includeParticipant: true,
  }
}

function retainAvailableFields(codes, availableCodes) {
  return codes.filter((code) => availableCodes.has(code))
}

function fieldSummary(codes) {
  const labelsByCode = new Map(dynamicFields.value.map((field) => [field.code, field.label]))
  const labels = codes.map((code) => labelsByCode.get(code) || code)
  return labels.length ? labels.join('、') : '未选择'
}

function includeSummary(included) {
  return included ? '包含' : '不包含'
}

function canExport() {
  if (!selectedSheetCount.value) {
    ElMessage.warning('请至少选择一个导出子表')
    return false
  }
  return true
}

function normalizeActiveTab() {
  const tabs = exportTabs.value
  if (!tabs.some((tab) => tab.name === activeTab.value)) {
    activeTab.value = tabs.at(-1)?.name || 'sheets'
  }
}

function goPrevious() {
  const index = currentTabIndex.value
  if (index <= 0) return
  activeTab.value = exportTabs.value[index - 1].name
}

function goNext() {
  if (!canExport()) return
  const index = currentTabIndex.value
  const tabs = exportTabs.value
  activeTab.value = tabs[Math.min(index + 1, tabs.length - 1)].name
}

function submit() {
  if (!canExport()) return
  emit('export', {
    sheets: {
      participants: { ...form.participants },
      layout: { ...form.layout },
      seatDetails: { ...form.seatDetails },
    },
  })
}

watch(visible, (isVisible) => {
  if (isVisible) resetOptions()
})

watch(dynamicFields, (fields) => {
  if (!visible.value) return
  const availableCodes = new Set(fields.map((field) => field.code))
  form.participants.fieldCodes = retainAvailableFields(form.participants.fieldCodes, availableCodes)
  form.layout.fieldCodes = retainAvailableFields(form.layout.fieldCodes, availableCodes)
  form.seatDetails.fieldCodes = retainAvailableFields(form.seatDetails.fieldCodes, availableCodes)
})

watch(selectedSheetDefinitions, normalizeActiveTab)

watch(
  () => form.layout.fieldCodes,
  () => {
    form.layout.colorFieldCodes = form.layout.colorFieldCodes.filter((code) =>
      form.layout.fieldCodes.includes(code),
    )
  },
  { deep: true },
)
</script>

<template>
  <el-dialog
    v-model="visible"
    title="导出Excel"
    width="880px"
    class="export-options-dialog"
    append-to-body
    destroy-on-close
  >
    <div class="export-dialog-body">
      <el-tabs v-model="activeTab" tab-position="left" class="export-tabs export-side-nav">
        <el-tab-pane
          v-for="tab in exportTabs"
          :key="tab.name"
          :label="tab.label"
          :name="tab.name"
        >
          <el-form
            v-if="tab.name === 'sheets'"
            class="tab-panel"
            label-position="top"
            @submit.prevent
          >
            <div class="config-scroll">
              <el-form-item label="导出子表" class="config-section">
                <div class="sheet-card-grid">
                  <label
                    v-for="sheet in sheetDefinitions"
                    :key="sheet.key"
                    class="sheet-option check-card"
                    :class="{ 'sheet-option--active': form[sheet.key].enabled }"
                  >
                    <el-checkbox v-model="form[sheet.key].enabled">
                      {{ sheet.label }}
                    </el-checkbox>
                  </label>
                </div>
              </el-form-item>
            </div>
          </el-form>

          <el-form
            v-else-if="tab.name === 'participants'"
            class="tab-panel"
            label-position="top"
            @submit.prevent
          >
            <div class="config-scroll">
              <el-form-item label="扩展字段" class="config-section">
                <el-checkbox-group
                  v-if="dynamicFields.length"
                  v-model="form.participants.fieldCodes"
                  class="field-checks"
                >
                  <el-checkbox
                    v-for="field in dynamicFields"
                    :key="field.code"
                    class="check-card"
                    :label="field.code"
                  >
                    {{ field.label }}
                  </el-checkbox>
                </el-checkbox-group>
                <p v-else class="empty-export-options">暂无扩展字段</p>
              </el-form-item>
              <el-form-item label="其他列" class="config-section">
                <div class="extra-checks">
                  <el-checkbox v-model="form.participants.includeAttendance" class="check-card">出席情况</el-checkbox>
                  <el-checkbox v-model="form.participants.includeSeatLabel" class="check-card">座位编号</el-checkbox>
                </div>
              </el-form-item>
            </div>
          </el-form>

          <el-form
            v-else-if="tab.name === 'layout'"
            class="tab-panel"
            label-position="top"
            @submit.prevent
          >
            <div class="config-scroll">
              <el-form-item label="排座字段" class="config-section">
                <el-checkbox-group
                  v-if="dynamicFields.length"
                  v-model="layoutFieldCodes"
                  class="field-checks"
                >
                  <el-checkbox
                    v-for="field in dynamicFields"
                    :key="field.code"
                    class="check-card"
                    :label="field.code"
                  >
                    {{ field.label }}
                  </el-checkbox>
                </el-checkbox-group>
                <p v-else class="empty-export-options">暂无扩展字段</p>
              </el-form-item>
              <el-form-item label="着色字段" class="config-section">
                <el-checkbox-group
                  v-if="availableLayoutColorFields.length"
                  v-model="layoutColorFieldCodes"
                  class="field-checks"
                >
                  <el-checkbox
                    v-for="field in availableLayoutColorFields"
                    :key="field.code"
                    class="check-card"
                    :label="field.code"
                  >
                    {{ field.label }}
                  </el-checkbox>
                </el-checkbox-group>
                <p v-else class="empty-export-options">暂无可着色字段</p>
              </el-form-item>
            </div>
          </el-form>

          <el-form
            v-else-if="tab.name === 'seatDetails'"
            class="tab-panel"
            label-position="top"
            @submit.prevent
          >
            <div class="config-scroll">
              <el-form-item label="扩展字段" class="config-section">
                <el-checkbox-group
                  v-if="dynamicFields.length"
                  v-model="form.seatDetails.fieldCodes"
                  class="field-checks"
                >
                  <el-checkbox
                    v-for="field in dynamicFields"
                    :key="field.code"
                    class="check-card"
                    :label="field.code"
                  >
                    {{ field.label }}
                  </el-checkbox>
                </el-checkbox-group>
                <p v-else class="empty-export-options">暂无扩展字段</p>
              </el-form-item>
              <el-form-item label="其他列" class="config-section">
                <div class="extra-checks">
                  <el-checkbox v-model="form.seatDetails.includeOccupancyType" class="check-card">占用类型</el-checkbox>
                  <el-checkbox v-model="form.seatDetails.includeRegionName" class="check-card">区域名称</el-checkbox>
                  <el-checkbox v-model="form.seatDetails.includeParticipant" class="check-card">人员信息</el-checkbox>
                </div>
              </el-form-item>
            </div>
          </el-form>

          <el-form v-else class="tab-panel" label-position="top" @submit.prevent>
            <div class="confirm-scroll">
              <el-form-item label="导出子表" class="config-section">
                <div class="summary-tags">
                  <el-tag v-if="form.participants.enabled">人员名单</el-tag>
                  <el-tag v-if="form.layout.enabled">排座图</el-tag>
                  <el-tag v-if="form.seatDetails.enabled">座位明细</el-tag>
                </div>
              </el-form-item>
              <el-collapse v-model="confirmCollapse" class="summary-collapse">
                <el-collapse-item
                  v-if="form.participants.enabled"
                  title="人员名单配置"
                  name="participants"
                >
                  <div class="summary-lines">
                    <p><strong>扩展字段</strong><span>{{ fieldSummary(form.participants.fieldCodes) }}</span></p>
                    <p><strong>出席情况</strong><span>{{ includeSummary(form.participants.includeAttendance) }}</span></p>
                    <p><strong>座位编号</strong><span>{{ includeSummary(form.participants.includeSeatLabel) }}</span></p>
                  </div>
                </el-collapse-item>
                <el-collapse-item
                  v-if="form.layout.enabled"
                  title="排座图配置"
                  name="layout"
                >
                  <div class="summary-lines">
                    <p><strong>排座字段</strong><span>{{ fieldSummary(form.layout.fieldCodes) }}</span></p>
                    <p><strong>着色字段</strong><span>{{ fieldSummary(form.layout.colorFieldCodes) }}</span></p>
                  </div>
                </el-collapse-item>
                <el-collapse-item
                  v-if="form.seatDetails.enabled"
                  title="座位明细配置"
                  name="seatDetails"
                >
                  <div class="summary-lines">
                    <p><strong>扩展字段</strong><span>{{ fieldSummary(form.seatDetails.fieldCodes) }}</span></p>
                    <p><strong>占用类型</strong><span>{{ includeSummary(form.seatDetails.includeOccupancyType) }}</span></p>
                    <p><strong>区域名称</strong><span>{{ includeSummary(form.seatDetails.includeRegionName) }}</span></p>
                    <p><strong>人员信息</strong><span>{{ includeSummary(form.seatDetails.includeParticipant) }}</span></p>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>

    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button :disabled="submitting || isFirstTab" @click="goPrevious">上一步</el-button>
      <el-button v-if="!isConfirmTab" type="primary" :disabled="submitting" @click="goNext">
        下一步
      </el-button>
      <el-button v-else type="primary" :loading="submitting" @click="submit">导出Excel</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.export-dialog-body {
  height: 100%;
  min-height: 0;
}

.export-tabs {
  height: 100%;
}

.export-tabs :deep(.el-tabs__header.is-left) {
  width: 170px;
  flex: 0 0 170px;
  margin-right: 22px;
}

.export-tabs :deep(.el-tabs__nav-wrap.is-left) {
  padding: 8px 10px;
  border-right: 1px solid var(--line);
}

.export-tabs :deep(.el-tabs__nav-wrap.is-left::after),
.export-tabs :deep(.el-tabs__active-bar.is-left) {
  display: none;
}

.export-tabs :deep(.el-tabs__item) {
  width: 136px;
  justify-content: flex-start;
  height: 40px;
  line-height: 40px;
  margin-bottom: 6px;
  padding: 0 12px;
  color: var(--secondary);
  border-radius: 8px;
  text-align: left;
  white-space: nowrap;
}

.export-tabs :deep(.el-tabs__item.is-active) {
  color: var(--brand);
  background: var(--brand-soft);
  font-weight: 700;
}

.export-tabs :deep(.el-tabs__content),
.export-tabs :deep(.el-tab-pane) {
  height: 100%;
  min-height: 0;
}

.tab-panel {
  height: 100%;
  min-height: 0;
}

.config-scroll,
.confirm-scroll {
  height: 100%;
  min-height: 0;
  display: grid;
  align-content: start;
  gap: 14px;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 6px;
}

.config-section {
  margin-bottom: 0;
  padding: 16px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 10px;
}

.config-section :deep(.el-form-item__label) {
  margin-bottom: 12px;
  color: var(--ink);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.2;
}

.config-section :deep(.el-form-item__content) {
  width: 100%;
}

.sheet-card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

.sheet-option {
  min-height: 72px;
  display: flex;
  align-items: center;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease;
}

.sheet-option--active {
  border-color: var(--brand);
  box-shadow: 0 8px 20px rgba(10, 89, 247, 0.08);
}

.field-checks,
.extra-checks {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
  width: 100%;
}

.field-checks {
  min-height: 96px;
  max-height: 220px;
  align-content: start;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 2px;
}

.field-checks :deep(.el-checkbox),
.extra-checks :deep(.el-checkbox),
.sheet-option :deep(.el-checkbox) {
  min-width: 0;
  margin-right: 0;
}

.field-checks :deep(.check-card),
.extra-checks :deep(.check-card) {
  min-height: 42px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  background: #f8fafc;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.field-checks :deep(.check-card.is-checked),
.extra-checks :deep(.check-card.is-checked) {
  background: var(--brand-soft);
  border-color: #bfd8ff;
}

.empty-export-options {
  margin: 0;
  color: var(--tertiary);
  font-size: 13px;
}

.summary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.summary-collapse {
  border-top: 1px solid var(--line);
}

.summary-collapse :deep(.el-collapse-item__header) {
  font-size: 14px;
  font-weight: 700;
}

.summary-lines {
  display: grid;
  gap: 8px;
}

.summary-lines p {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 12px;
  margin: 0;
  color: var(--secondary);
}

.summary-lines strong {
  color: var(--primary);
  font-weight: 700;
}

.summary-lines span {
  min-width: 0;
  overflow-wrap: anywhere;
}

:global(.export-options-dialog) {
  height: min(82vh, 760px);
  margin-top: min(6vh, 44px);
  display: flex;
  flex-direction: column;
}

:global(.export-options-dialog .el-dialog__body) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

:global(.export-options-dialog .el-dialog__footer) {
  flex: 0 0 auto;
}
</style>
