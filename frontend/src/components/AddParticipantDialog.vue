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
})
const dynamicFields = computed(() => groupableFields(props.fieldDefinitions))
const rules = {
  employeeNo: [
    { required: true, message: '请输入工号', trigger: 'blur' },
    {
      validator: (_rule, value, callback) =>
        !isValidEmployeeNo(value)
          ? callback(new Error('工号必须为8位数字或1个小写字母加8位数字'))
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
    })
    emit('done', participant)
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
    :title="targetElementId ? '在所选空座新增人员' : '新增参会人员'"
    width="520px"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-grid">
        <el-form-item label="工号" prop="employeeNo">
          <el-input v-model="form.employeeNo" placeholder="12345678 或 a12345678" maxlength="9" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
      </div>
      <el-form-item v-for="field in dynamicFields" :key="field.code" :label="field.label">
        <el-input v-model="form.attributes[field.code]" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        {{ targetElementId ? '添加并安排到该座位' : '加入待排列表' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}
</style>
