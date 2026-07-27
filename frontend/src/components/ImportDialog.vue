<script setup>
import { computed, ref, watch } from 'vue'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { importContract, meetingApi } from '@/api/meeting'
import { apiErrorMessage, downloadBlob } from '@/api/http'
const visible = defineModel({ required: true })
const props = defineProps({
  meetingId: { type: String, required: true },
})
const emit = defineEmits(['done'])
const file = ref()
const preview = ref()
const loading = ref(false)
const canCommit = computed(() => importContract.canCommit(preview.value))
watch(visible, (value) => {
  if (!value) {
    file.value = undefined
    preview.value = undefined
  }
})
function onFileChange(uploadFile) {
  file.value = uploadFile.raw
  preview.value = undefined
}
async function downloadTemplate() {
  loading.value = true
  try {
    const template = await meetingApi.importTemplate()
    downloadBlob(
      template,
      '参会人员导入模板.xlsx',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    )
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    loading.value = false
  }
}
async function parseFile() {
  if (!file.value) {
    ElMessage.warning('请先选择Excel文件')
    return
  }
  loading.value = true
  try {
    preview.value = await meetingApi.previewImport(props.meetingId, file.value)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    loading.value = false
  }
}
async function commit() {
  if (!preview.value || !canCommit.value) return
  loading.value = true
  try {
    const result = await meetingApi.commitImport(props.meetingId, preview.value.token)
    ElMessage.success(
      `导入完成：新增${result.newParticipants}人，合并${result.mergedRecords}条记录，追加${result.appendedRecords}条记录`,
    )
    visible.value = false
    emit('done')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="导入参会人员" width="820px" top="5vh">
    <div class="import-layout">
      <section class="import-card import-card--template">
        <div>
          <strong>人员导入模板</strong>
          <p>使用单一 Excel 模板导入人员及其动态记录。</p>
        </div>
        <el-button :icon="Download" :loading="loading" @click="downloadTemplate">
          下载人员模板
        </el-button>
      </section>

      <el-upload
        class="upload-surface"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xlsx"
        :on-change="onFileChange"
        :on-remove="() => (file = undefined)"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将Excel拖到这里，或<em>选择文件</em></div>
        <template #tip>
          <div class="el-upload__tip">上传后先预览，不会直接修改会议名单。</div>
        </template>
      </el-upload>

      <el-button v-if="!preview" type="primary" :loading="loading" @click="parseFile">
        解析并预览
      </el-button>

      <section v-if="preview" class="preview-section import-card">
        <div class="preview-stats">
          <div>
            <strong>{{ preview.totalRows }}</strong
            ><span>总行数</span>
          </div>
          <div>
            <strong>{{ preview.ignoredDuplicateRows }}</strong
            ><span>去重行数</span>
          </div>
          <div>
            <strong>{{ preview.participantCount }}</strong
            ><span>人员数</span>
          </div>
          <div>
            <strong>{{ preview.recordCount }}</strong
            ><span>记录数</span>
          </div>
        </div>

        <section class="field-section">
          <header>本次新增字段</header>
          <el-tag v-for="field in preview.newFields" :key="field" type="success">{{ field }}</el-tag>
          <span v-if="preview.newFields.length === 0">无</span>
        </section>

        <section class="field-section">
          <header>已有字段</header>
          <el-tag v-for="field in preview.existingFields" :key="field" type="info">{{ field }}</el-tag>
          <span v-if="preview.existingFields.length === 0">无</span>
        </section>

        <el-alert
          v-for="error in preview.errors"
          :key="error"
          :title="`阻断错误：${error}`"
          type="error"
          :closable="false"
          show-icon
        />
      </section>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button v-if="preview" @click="parseFile">重新解析</el-button>
      <el-button
        v-if="preview"
        type="primary"
        :disabled="!canCommit"
        :loading="loading"
        @click="commit"
      >
        确认导入
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.import-layout,
.import-card,
.preview-section,
.field-section {
  display: grid;
  gap: 14px;
}

.import-card {
  padding: 14px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
}

.import-card--template {
  grid-template-columns: 1fr auto;
  align-items: center;
  background: #fbfcfd;
}

.import-card--template p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.upload-surface :deep(.el-upload) {
  width: 100%;
}

.upload-surface :deep(.el-upload-dragger) {
  padding: 28px;
  background: #fbfcfd;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-md);
}

.upload-surface :deep(.el-upload-dragger:hover) {
  border-color: var(--brand);
  background: #f8fbff;
}

.upload-surface :deep(.el-icon--upload) {
  color: var(--brand);
}

.upload-surface :deep(.el-upload__tip) {
  color: var(--tertiary);
}

.preview-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.preview-stats div {
  display: grid;
  place-items: center;
  padding: 12px;
  background: #fbfcfd;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.preview-stats strong {
  font-size: 22px;
}

.preview-stats span {
  color: var(--muted);
  font-size: 11px;
}

.field-section {
  grid-template-columns: auto repeat(auto-fit, minmax(90px, max-content));
  align-items: center;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  color: var(--muted);
  font-size: 12px;
}
</style>
