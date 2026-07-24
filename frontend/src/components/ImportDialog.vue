<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage } from '@/api/http'
const visible = defineModel({ required: true })
const props = defineProps({
  meetingId: { type: String, required: true },
})
const emit = defineEmits(['done'])
const templates = ref([])
const templateCode = ref('AWARD_CEREMONY_V1')
const file = ref()
const preview = ref()
const selections = reactive({})
const loading = ref(false)
const currentTemplate = computed(() =>
  templates.value.find((item) => item.code === templateCode.value),
)
const canCommit = computed(
  () =>
    preview.value &&
    preview.value.duplicateGroups.every((group) => selections[group.employeeNo] !== undefined),
)
onMounted(async () => {
  try {
    templates.value = await meetingApi.importTemplates()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  }
})
watch(visible, (value) => {
  if (!value) {
    file.value = undefined
    preview.value = undefined
    Object.keys(selections).forEach((key) => delete selections[key])
  }
})
function onFileChange(uploadFile) {
  file.value = uploadFile.raw
  preview.value = undefined
}
function downloadTemplate() {
  window.open(`/api/import-templates/${templateCode.value}/file`, '_blank')
}
async function parseFile() {
  if (!file.value) {
    ElMessage.warning('请先选择Excel文件')
    return
  }
  loading.value = true
  try {
    preview.value = await meetingApi.previewImport(props.meetingId, templateCode.value, file.value)
    preview.value.duplicateGroups.forEach((group) => {
      if (group.candidates.length === 1)
        selections[group.employeeNo] = group.candidates[0].sourceRow
    })
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
    const result = await meetingApi.commitImport(props.meetingId, preview.value.token, selections)
    ElMessage.success(`导入完成：新增${result.inserted}人，更新${result.updated}人`)
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
      <section class="template-section">
        <label>选择数据模板</label>
        <el-select v-model="templateCode" class="template-select">
          <el-option
            v-for="template in templates"
            :key="template.code"
            :label="template.name"
            :value="template.code"
          />
        </el-select>
        <div v-if="currentTemplate" class="template-note">
          <strong>{{ currentTemplate.description }}</strong>
          <span>
            包含：
            {{
              currentTemplate.sheets
                .map((sheet) => `${sheet.name}（${sheet.rowMeaning}）`)
                .join('、')
            }}
          </span>
        </div>
        <el-button :icon="Download" @click="downloadTemplate">下载标准模板</el-button>
      </section>

      <el-upload
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

      <section v-if="preview" class="preview-section">
        <div class="preview-stats">
          <div>
            <strong>{{ preview.participantRowCount }}</strong
            ><span>人员行</span>
          </div>
          <div>
            <strong>{{ preview.awardRowCount }}</strong
            ><span>业务记录</span>
          </div>
          <div>
            <strong>{{ preview.duplicateGroups.length }}</strong
            ><span>重复工号</span>
          </div>
          <div>
            <strong>{{ preview.errors.length }}</strong
            ><span>数据提醒</span>
          </div>
        </div>

        <el-alert
          v-for="error in preview.errors"
          :key="error"
          :title="error"
          type="warning"
          :closable="false"
          show-icon
        />

        <div
          v-for="group in preview.duplicateGroups"
          :key="group.employeeNo"
          class="duplicate-group"
        >
          <header>
            <strong>重复工号 {{ group.employeeNo }}</strong>
            <span>请选择本次导入使用的记录</span>
          </header>
          <el-radio-group v-model="selections[group.employeeNo]">
            <el-radio
              v-for="candidate in group.candidates"
              :key="candidate.sourceRow"
              :value="candidate.sourceRow"
              border
            >
              第{{ candidate.sourceRow }}行 · {{ candidate.name }} · 职级{{
                candidate.level ?? '—'
              }}
              · {{ candidate.department || '未填写部门' }} ·
              {{ candidate.participantType || '未填写类型' }}
            </el-radio>
          </el-radio-group>
        </div>
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
.template-section,
.preview-section {
  display: grid;
  gap: 14px;
}

.template-section {
  grid-template-columns: 140px 1fr auto;
  align-items: center;
  padding: 14px;
  background: #f6f8fb;
  border: 1px solid #e1e7ef;
  border-radius: 10px;
}

.template-section > label {
  color: #475569;
  font-weight: 700;
}

.template-note {
  grid-column: 2 / 4;
  display: grid;
  gap: 4px;
  color: #718096;
  font-size: 12px;
}

.template-note strong {
  color: #475569;
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
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
}

.preview-stats strong {
  font-size: 22px;
}

.preview-stats span {
  color: #718096;
  font-size: 11px;
}

.duplicate-group {
  padding: 12px;
  border: 1px solid #f1c978;
  border-radius: 10px;
}

.duplicate-group header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #92400e;
  font-size: 12px;
}

.duplicate-group :deep(.el-radio-group) {
  display: grid;
  gap: 8px;
}

.duplicate-group :deep(.el-radio) {
  width: 100%;
  margin: 0;
}
</style>
