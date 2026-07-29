<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const visible = defineModel({ required: true })
const props = defineProps({
  fieldDefinitions: { type: Array, default: () => [] },
  submitting: { type: Boolean, default: false },
})
const emit = defineEmits(['export'])

const activeStep = ref(0)
const dynamicFields = computed(() =>
  (props.fieldDefinitions || []).filter((field) => !['name', 'employeeNo'].includes(field.code)),
)
const form = reactive({
  participants: {
    enabled: true,
    fieldCodes: [],
    includeAttendance: true,
    includeSeatLabel: true,
  },
  layout: {
    enabled: true,
    fieldCodes: [],
    colorFieldCodes: [],
  },
  seatDetails: {
    enabled: true,
    fieldCodes: [],
    includeOccupancyType: true,
    includeRegionName: true,
    includeParticipant: true,
  },
})

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
const selectedSheetCount = computed(() =>
  [form.participants.enabled, form.layout.enabled, form.seatDetails.enabled]
    .filter(Boolean).length,
)

function resetOptions() {
  const codes = dynamicFields.value.map((field) => field.code)
  activeStep.value = 0
  form.participants = {
    enabled: true,
    fieldCodes: [...codes],
    includeAttendance: true,
    includeSeatLabel: true,
  }
  form.layout = {
    enabled: true,
    fieldCodes: [],
    colorFieldCodes: [],
  }
  form.seatDetails = {
    enabled: true,
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

function nextStep() {
  if (!canExport()) return
  activeStep.value += 1
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
    width="640px"
    append-to-body
    destroy-on-close
  >
    <el-steps :active="activeStep" finish-status="success" simple>
      <el-step title="选择子表" />
      <el-step title="人员名单配置" />
      <el-step title="排座图配置" />
      <el-step title="座位明细配置" />
      <el-step title="确认导出" />
    </el-steps>

    <el-form class="wizard-form" label-position="top" @submit.prevent>
      <template v-if="activeStep === 0">
        <el-form-item label="导出子表">
          <el-checkbox v-model="form.participants.enabled">人员名单</el-checkbox>
          <el-checkbox v-model="form.layout.enabled">排座图</el-checkbox>
          <el-checkbox v-model="form.seatDetails.enabled">座位明细</el-checkbox>
        </el-form-item>
      </template>

      <template v-else-if="activeStep === 1">
        <el-form-item label="扩展字段">
          <el-checkbox-group
            v-if="dynamicFields.length"
            v-model="form.participants.fieldCodes"
            class="field-checks"
          >
            <el-checkbox v-for="field in dynamicFields" :key="field.code" :label="field.code">
              {{ field.label }}
            </el-checkbox>
          </el-checkbox-group>
          <p v-else class="empty-export-options">暂无扩展字段</p>
        </el-form-item>
        <el-form-item label="其他列">
          <div class="extra-checks">
            <el-checkbox v-model="form.participants.includeAttendance">出席情况</el-checkbox>
            <el-checkbox v-model="form.participants.includeSeatLabel">座位编号</el-checkbox>
          </div>
        </el-form-item>
      </template>

      <template v-else-if="activeStep === 2">
        <el-form-item label="排座字段">
          <el-checkbox-group
            v-if="dynamicFields.length"
            v-model="layoutFieldCodes"
            class="field-checks"
          >
            <el-checkbox v-for="field in dynamicFields" :key="field.code" :label="field.code">
              {{ field.label }}
            </el-checkbox>
          </el-checkbox-group>
          <p v-else class="empty-export-options">暂无扩展字段</p>
        </el-form-item>
        <el-form-item label="着色字段">
          <el-checkbox-group
            v-if="availableLayoutColorFields.length"
            v-model="layoutColorFieldCodes"
            class="field-checks"
          >
            <el-checkbox
              v-for="field in availableLayoutColorFields"
              :key="field.code"
              :label="field.code"
            >
              {{ field.label }}
            </el-checkbox>
          </el-checkbox-group>
          <p v-else class="empty-export-options">暂无可着色字段</p>
        </el-form-item>
        <el-form-item label="固定字段">
          <span>座位编号、姓名、左右排号始终导出</span>
        </el-form-item>
      </template>

      <template v-else-if="activeStep === 3">
        <el-form-item label="扩展字段">
          <el-checkbox-group
            v-if="dynamicFields.length"
            v-model="form.seatDetails.fieldCodes"
            class="field-checks"
          >
            <el-checkbox v-for="field in dynamicFields" :key="field.code" :label="field.code">
              {{ field.label }}
            </el-checkbox>
          </el-checkbox-group>
          <p v-else class="empty-export-options">暂无扩展字段</p>
        </el-form-item>
        <el-form-item label="其他列">
          <div class="extra-checks">
            <el-checkbox v-model="form.seatDetails.includeOccupancyType">占用类型</el-checkbox>
            <el-checkbox v-model="form.seatDetails.includeRegionName">区域名称</el-checkbox>
            <el-checkbox v-model="form.seatDetails.includeParticipant">人员信息</el-checkbox>
          </div>
        </el-form-item>
      </template>

      <template v-else>
        <el-form-item label="导出子表">
          <el-tag v-if="form.participants.enabled">人员名单</el-tag>
          <el-tag v-if="form.layout.enabled">排座图</el-tag>
          <el-tag v-if="form.seatDetails.enabled">座位明细</el-tag>
        </el-form-item>
        <el-form-item v-if="form.participants.enabled" label="人员名单配置">
          <div class="summary-lines">
            <p>扩展字段：{{ fieldSummary(form.participants.fieldCodes) }}</p>
            <p>出席情况：{{ includeSummary(form.participants.includeAttendance) }}</p>
            <p>座位编号：{{ includeSummary(form.participants.includeSeatLabel) }}</p>
          </div>
        </el-form-item>
        <el-form-item v-if="form.layout.enabled" label="排座图配置">
          <div class="summary-lines">
            <p>排座字段：{{ fieldSummary(form.layout.fieldCodes) }}</p>
            <p>着色字段：{{ fieldSummary(form.layout.colorFieldCodes) }}</p>
            <p>固定字段：座位编号、姓名、左右排号</p>
          </div>
        </el-form-item>
        <el-form-item v-if="form.seatDetails.enabled" label="座位明细配置">
          <div class="summary-lines">
            <p>扩展字段：{{ fieldSummary(form.seatDetails.fieldCodes) }}</p>
            <p>占用类型：{{ includeSummary(form.seatDetails.includeOccupancyType) }}</p>
            <p>区域名称：{{ includeSummary(form.seatDetails.includeRegionName) }}</p>
            <p>人员信息：{{ includeSummary(form.seatDetails.includeParticipant) }}</p>
          </div>
        </el-form-item>
      </template>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button :disabled="submitting || activeStep === 0" @click="activeStep--">上一步</el-button>
      <el-button v-if="activeStep < 4" type="primary" :disabled="submitting" @click="nextStep">
        下一步
      </el-button>
      <el-button v-else type="primary" :loading="submitting" @click="submit">导出Excel</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.wizard-form {
  padding-top: 20px;
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
  max-height: 184px;
  align-content: start;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 2px;
}

.field-checks :deep(.el-checkbox),
.extra-checks :deep(.el-checkbox) {
  min-width: 0;
  margin-right: 0;
}

.empty-export-options {
  margin: 0;
  color: var(--tertiary);
  font-size: 13px;
}

.summary-lines {
  width: 100%;
}

.summary-lines p {
  margin: 0 0 6px;
}

.el-tag {
  margin: 0 8px 8px 0;
}
</style>
