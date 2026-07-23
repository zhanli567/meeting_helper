<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage } from '@/api/http'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ planId: string }>()
const emit = defineEmits<{ done: [] }>()
const submitting = ref(false)
const form = reactive({ versionName: '', changeNote: '' })

async function submit() {
  if (!form.versionName.trim()) {
    ElMessage.warning('请填写版本名称')
    return
  }
  submitting.value = true
  try {
    await meetingApi.createVersion(props.planId, {
      versionName: form.versionName.trim(),
      changeNote: form.changeNote.trim(),
      automatic: false,
    })
    ElMessage.success('方案版本已保存')
    visible.value = false
    Object.assign(form, { versionName: '', changeNote: '' })
    emit('done')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="保存排座版本" width="480px">
    <el-form label-position="top">
      <el-form-item label="版本名称" required>
        <el-input v-model="form.versionName" placeholder="例如：领导确认前初稿" maxlength="60" />
      </el-form-item>
      <el-form-item label="变更说明">
        <el-input
          v-model="form.changeNote"
          type="textarea"
          :rows="3"
          placeholder="简要记录本次调整"
          maxlength="300"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存版本</el-button>
    </template>
  </el-dialog>
</template>
