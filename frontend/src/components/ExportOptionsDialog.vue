<script setup>
import { computed, reactive, watch } from 'vue'

const visible = defineModel({ required: true })
const props = defineProps({
  fieldDefinitions: { type: Array, default: () => [] },
  submitting: { type: Boolean, default: false },
})
const emit = defineEmits(['export'])

const dynamicFields = computed(() =>
  (props.fieldDefinitions || []).filter((field) => !['name', 'employeeNo'].includes(field.code)),
)
const form = reactive({
  fieldCodes: [],
  includeAttendance: true,
  includeSeatLabel: true,
})

function resetOptions() {
  form.fieldCodes = dynamicFields.value.map((field) => field.code)
  form.includeAttendance = true
  form.includeSeatLabel = true
}

watch(visible, (isVisible) => {
  if (isVisible) resetOptions()
})

watch(dynamicFields, (fields) => {
  if (!visible.value) return
  const availableCodes = new Set(fields.map((field) => field.code))
  form.fieldCodes = form.fieldCodes.filter((code) => availableCodes.has(code))
})

function submit() {
  emit('export', {
    fieldCodes: [...form.fieldCodes],
    includeAttendance: form.includeAttendance,
    includeSeatLabel: form.includeSeatLabel,
  })
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="导出Excel"
    width="480px"
    append-to-body
    destroy-on-close
  >
    <div class="required-columns">工号、姓名为必选列</div>
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="扩展字段">
        <el-checkbox-group
          v-if="dynamicFields.length"
          v-model="form.fieldCodes"
          class="field-checks"
        >
          <el-checkbox
            v-for="field in dynamicFields"
            :key="field.code"
            :label="field.code"
          >
            {{ field.label }}
          </el-checkbox>
        </el-checkbox-group>
        <p v-else class="empty-export-options">暂无扩展字段</p>
      </el-form-item>
      <el-form-item label="其他列">
        <div class="extra-checks">
          <el-checkbox v-model="form.includeAttendance">出席情况</el-checkbox>
          <el-checkbox v-model="form.includeSeatLabel">座位编号</el-checkbox>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">导出Excel</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.required-columns {
  margin-bottom: 14px;
  padding: 10px 12px;
  color: var(--muted);
  background: #f5f7fb;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.field-checks,
.extra-checks {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
  width: 100%;
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
</style>
