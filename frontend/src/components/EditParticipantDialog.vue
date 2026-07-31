<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ParticipantRecordTable from '@/components/ParticipantRecordTable.vue'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage } from '@/api/http'
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
const rules = {
  name: [{ required: true, message: '请输入姓名' }],
}

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
    if (visible.value) {
      resetForm()
    }
  },
  { immediate: true },
)

async function submit() {
  if (submitting.value) {
    return
  }
  if (!props.participant?.id) {
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (!recordTableRef.value?.validateCustomFields()) {
    return
  }
  if (!recordTableRef.value?.validateRecords()) {
    return
  }
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
  <el-dialog
    v-model="visible"
    title="编辑人员"
    width="900px"
    class="edit-participant-dialog"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :validate-on-rule-change="false"
      :disabled="submitting"
      v-loading="submitting"
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

      <ParticipantRecordTable
        ref="recordTableRef"
        v-model:records="form.records"
        v-model:custom-fields="form.customFields"
        :field-definitions="fieldDefinitions"
        :table-height="recordTableHeight"
        add-row-label="添加记录"
        add-column-label="添加列"
      />
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
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

:global(.edit-participant-dialog) {
  height: min(78vh, 720px);
  margin-top: min(8vh, 48px);
  display: flex;
  flex-direction: column;
}

:global(.edit-participant-dialog .el-dialog__body) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

:global(.edit-participant-dialog .el-dialog__footer) {
  flex: 0 0 auto;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}

</style>
