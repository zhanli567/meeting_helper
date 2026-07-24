<script setup>
import { reactive, ref } from 'vue'
import { Clock, RefreshLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage } from '@/api/http'
const visible = defineModel({ required: true })
const props = defineProps()
const emit = defineEmits()
const submitting = ref(false)
const restoringId = ref('')
const activeTab = ref('history')
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
    Object.assign(form, { versionName: '', changeNote: '' })
    activeTab.value = 'history'
    emit('done')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    submitting.value = false
  }
}
async function restore(version) {
  try {
    await ElMessageBox.confirm(
      `恢复到 V${version.versionNo}「${version.versionName}」后，当前未保存的排座调整会被替换。`,
      '确认恢复版本',
      {
        type: 'warning',
        confirmButtonText: '确认恢复',
        cancelButtonText: '取消',
      },
    )
    restoringId.value = version.id
    await meetingApi.restoreVersion(props.planId, version.id)
    ElMessage.success(`已恢复到 V${version.versionNo}`)
    emit('done')
    visible.value = false
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error))
  } finally {
    restoringId.value = ''
  }
}
function formatTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <el-dialog v-model="visible" title="排座版本管理" width="620px">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="版本记录" name="history">
        <div v-if="versions.length" class="version-list">
          <article
            v-for="version in versions"
            :key="version.id"
            :class="{ current: version.versionNo === currentVersionNo }"
          >
            <div class="version-badge">V{{ version.versionNo }}</div>
            <div class="version-copy">
              <div>
                <strong>{{ version.versionName }}</strong>
                <el-tag v-if="version.versionNo === currentVersionNo" size="small" type="primary">
                  当前版本
                </el-tag>
              </div>
              <p>{{ version.changeNote || '未填写变更说明' }}</p>
              <span>
                {{ formatTime(version.createdAt) }} · {{ version.createdByName }} · 已排
                {{ version.assignedCount }} / 待排 {{ version.unassignedCount }}
              </span>
            </div>
            <el-button
              :icon="RefreshLeft"
              :loading="restoringId === version.id"
              :disabled="version.versionNo === currentVersionNo"
              @click="restore(version)"
            >
              恢复
            </el-button>
          </article>
        </div>
        <div v-else class="empty-version">
          <el-icon><Clock /></el-icon>
          <span>还没有保存过版本</span>
        </div>
      </el-tab-pane>

      <el-tab-pane label="保存新版本" name="create">
        <el-form label-position="top">
          <el-form-item label="版本名称" required>
            <el-input
              v-model="form.versionName"
              placeholder="例如：领导确认前初稿"
              maxlength="60"
            />
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
        <div class="create-actions">
          <el-button @click="visible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submit">保存新版本</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<style scoped>
.version-list {
  max-height: 430px;
  display: grid;
  gap: 10px;
  overflow: auto;
  padding: 4px 4px 4px 0;
}

.version-list article {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 14px;
  background: #f8fbff;
  border: 1px solid #dce7f5;
  border-radius: 12px;
}

.version-list article.current {
  background: #edf5ff;
  border-color: #93baf0;
}

.version-badge {
  width: 42px;
  height: 42px;
  flex: none;
  display: grid;
  place-items: center;
  color: #1d4ed8;
  background: #dbeafe;
  border-radius: 11px;
  font-weight: 750;
}

.version-copy {
  flex: 1;
  min-width: 0;
}

.version-copy > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.version-copy p {
  margin: 5px 0;
  overflow: hidden;
  color: #52657e;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-copy > span {
  color: #8492a6;
  font-size: 10px;
}

.empty-version {
  min-height: 220px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  color: #7d8ca0;
}

.empty-version .el-icon {
  font-size: 30px;
}

.create-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
