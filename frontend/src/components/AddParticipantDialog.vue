<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage } from '@/api/http'
const visible = defineModel({ required: true })
const props = defineProps()
const emit = defineEmits()
const formRef = ref()
const submitting = ref(false)
const form = reactive({
  employeeNo: '',
  name: '',
  level: undefined,
  department: '',
  participantType: '获奖人员',
  tags: '',
})
const rules = {
  employeeNo: [
    { required: true, message: '请输入工号', trigger: 'blur' },
    { pattern: /^[A-Za-z][0-9]{8}$/, message: '工号必须为一个字母加8位数字', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
}
async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await meetingApi.addParticipant(props.meetingId, { ...form, attributes: {} })
    ElMessage.success('人员已加入待排列表')
    visible.value = false
    Object.assign(form, {
      employeeNo: '',
      name: '',
      level: undefined,
      department: '',
      participantType: '获奖人员',
      tags: '',
    })
    emit('done')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="新增参会人员" width="520px">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="form-grid">
        <el-form-item label="工号" prop="employeeNo">
          <el-input v-model="form.employeeNo" placeholder="A12345678" maxlength="9" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="职级">
          <el-input-number v-model="form.level" :min="1" :max="99" controls-position="right" />
        </el-form-item>
        <el-form-item label="人员类型">
          <el-select v-model="form.participantType">
            <el-option label="获奖人员" value="获奖人员" />
            <el-option label="嘉宾" value="嘉宾" />
            <el-option label="特邀嘉宾" value="特邀嘉宾" />
            <el-option label="参会人员" value="参会人员" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="部门">
        <el-input v-model="form.department" placeholder="请输入部门" />
      </el-form-item>
      <el-form-item label="标签">
        <el-input v-model="form.tags" placeholder="多个标签使用逗号分隔" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">加入待排列表</el-button>
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
