<script setup>
import { computed, reactive, ref, watch } from 'vue'
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
const submitting = ref(false)
const form = reactive({
  employeeNo: '',
  name: '',
  records: [],
  extraFields: [],
  fieldDefinitions: [],
})
const dynamicFields = computed(() => groupableFields(props.fieldDefinitions))
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
  form.extraFields = []
  form.fieldDefinitions = props.fieldDefinitions
}

watch(
  () => [visible.value, props.participant, props.fieldDefinitions],
  () => {
    if (visible.value) resetForm()
  },
  { immediate: true },
)

function addExtraField() {
  form.extraFields.push({ name: '', value: '' })
}

function removeExtraField(index) {
  form.extraFields.splice(index, 1)
}

async function submit() {
  if (!props.participant?.id) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
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
  <el-dialog v-model="visible" title="编辑人员" width="720px">
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

      <div class="record-list">
        <section
          v-for="(record, index) in form.records"
          :key="record.id || index"
          class="record-card"
        >
          <header class="record-card-header">
            <strong>记录 {{ index + 1 }}</strong>
            <span v-if="form.records.length > 1">第 {{ record.recordOrder || index + 1 }} 条获奖信息</span>
          </header>
          <div v-if="dynamicFields.length" class="record-field-grid">
            <el-form-item
              v-for="field in dynamicFields"
              :key="field.code"
              :label="field.label"
            >
              <el-input v-model="record.attributes[field.code]" :validate-event="false" />
            </el-form-item>
          </div>
          <p v-else class="record-empty">暂无扩展字段</p>
        </section>
      </div>

      <div class="extra-field-section">
        <div class="extra-field-heading">
          <span>新增列</span>
          <el-button size="small" @click="addExtraField">添加列</el-button>
        </div>
        <div class="extra-field-list">
          <div
            v-for="(field, index) in form.extraFields"
            :key="index"
            class="extra-field-row"
          >
            <el-input v-model="field.name" aria-label="列名" maxlength="32" :validate-event="false" />
            <el-input v-model="field.value" aria-label="列值" maxlength="80" :validate-event="false" />
            <el-button text type="danger" @click="removeExtraField(index)">移除</el-button>
          </div>
        </div>
      </div>
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

.record-list {
  max-height: min(32vh, 300px);
  display: grid;
  align-content: start;
  gap: 10px;
  margin-bottom: 12px;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 2px;
}

.record-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  background: #f8fbff;
  border: 1px solid var(--line);
  border-radius: 10px;
}

.record-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.record-card-header strong {
  color: var(--ink);
  font-size: 13px;
}

.record-card-header span,
.record-empty {
  color: var(--muted);
  font-size: 12px;
}

.record-field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.record-empty {
  margin: 0;
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

.extra-field-list {
  min-height: 104px;
  max-height: 184px;
  display: grid;
  align-content: start;
  gap: 8px;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 2px;
}

.extra-field-row {
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1fr) auto;
}
</style>
