<script setup>
import { reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { hasDuplicateVersionName, normalizeVersionName } from '@/utils/versionRules'

const visible = defineModel({ required: true })
const props = defineProps({
  versions: { type: Array, default: () => [] },
  submitting: { type: Boolean, default: false },
})
const emit = defineEmits(['publish'])
const form = reactive({
  versionName: '',
  changeNote: '',
})

watch(visible, (isVisible) => {
  if (!isVisible) {
    form.versionName = ''
    form.changeNote = ''
  }
})

function submit() {
  const versionName = normalizeVersionName(form.versionName)
  if (!versionName) {
    ElMessage.warning('请填写版本名称')
    return
  }
  if (hasDuplicateVersionName(versionName, props.versions)) {
    ElMessage.warning('版本名称已存在，请使用其他名称')
    return
  }
  emit('publish', {
    versionName,
    changeNote: form.changeNote.trim(),
    automatic: false,
  })
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="发布排座版本"
    width="520px"
    append-to-body
    destroy-on-close
  >
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="版本名称" required>
        <el-input
          v-model="form.versionName"
          maxlength="120"
          show-word-limit
          placeholder="例如：会务最终确认版"
          @keyup.enter="submit"
        />
      </el-form-item>
      <el-form-item label="变更说明">
        <el-input
          v-model="form.changeNote"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="记录本次版本的主要调整，可不填"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认发布</el-button>
    </template>
  </el-dialog>
</template>
