<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage } from '@/api/http'
import { groupableFields } from '@/utils/participantFields'
import { submitParticipant } from '@/utils/participantActions'
import { hasDuplicateEmployeeNo, isValidEmployeeNo } from '@/utils/participantRules'
const visible = defineModel({ required: true })
const props = defineProps({
  meetingId: { type: String, required: true },
  targetElementId: { type: String, default: undefined },
  participants: { type: Array, default: () => [] },
  fieldDefinitions: { type: Array, default: () => [] },
})
const emit = defineEmits(['done'])
const formRef = ref()
const submitting = ref(false)
const form = reactive({
  employeeNo: '',
  name: '',
  attributes: {},
  extraFields: [],
  fieldDefinitions: [],
})
const dynamicFields = computed(() => groupableFields(props.fieldDefinitions))
const rules = {
  employeeNo: [
    { required: true, message: '请输入工号', trigger: 'blur' },
    {
      validator: (_rule, value, callback) =>
        !isValidEmployeeNo(value)
          ? callback(new Error('工号必须为8位数字、1个小写字母加8位数字，或wx加6或7位数字'))
          : hasDuplicateEmployeeNo(value, props.participants)
            ? callback(new Error('该工号已在当前会议名单中'))
            : callback(),
      trigger: ['blur', 'change'],
    },
  ],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
}
async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  form.fieldDefinitions = props.fieldDefinitions
  submitting.value = true
  try {
    const participant = await submitParticipant({
      addParticipant: meetingApi.addParticipant,
      meetingId: props.meetingId,
      form,
      targetElementId: props.targetElementId,
    })
    ElMessage.success(
      props.targetElementId ? '人员已添加并安排到所选座位' : '人员已加入待排列表',
    )
    visible.value = false
    Object.assign(form, {
      employeeNo: '',
      name: '',
      attributes: {},
      extraFields: [],
      fieldDefinitions: [],
    })
    emit('done', participant)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    submitting.value = false
  }
}
function addExtraField() {
  form.extraFields.push({ name: '', value: '' })
}
function removeExtraField(index) {
  form.extraFields.splice(index, 1)
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="targetElementId ? '在所选空座新增人员' : '新增参会人员'"
    width="520px"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="participant-form-scroll"
    >
      <div class="form-grid">
        <el-form-item label="工号" prop="employeeNo">
          <el-input v-model="form.employeeNo" maxlength="9" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
      </div>
      <el-form-item v-for="field in dynamicFields" :key="field.code" :label="field.label">
        <el-input v-model="form.attributes[field.code]" />
      </el-form-item>
      <div class="extra-field-section">
        <div class="extra-field-heading">
          <span>新增列</span>
          <el-button size="small" @click="addExtraField">添加列</el-button>
        </div>
        <div
          v-for="(field, index) in form.extraFields"
          :key="index"
          class="extra-field-row"
        >
          <el-input v-model="field.name" aria-label="列名" maxlength="32" />
          <el-input v-model="field.value" aria-label="列值" maxlength="80" />
          <el-button text type="danger" @click="removeExtraField(index)">移除</el-button>
        </div>
      </div>
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
  max-height: min(58vh, 560px);
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 4px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}

.extra-field-section {
  display: grid;
  gap: 8px;
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.extra-field-heading,
.extra-field-row {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 10px;
}

.extra-field-heading span {
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
}

.extra-field-row {
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1fr) auto;
}
</style>
