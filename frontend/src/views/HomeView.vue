<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Calendar,
  CirclePlus,
  Collection,
  Delete,
  EditPen,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiErrorMessage } from '@/api/http'
import { meetingApi } from '@/api/meeting'
import { useWorkspaceStore } from '@/stores/workspace'
const router = useRouter()
const store = useWorkspaceStore()
const editVisible = ref(false)
const editSubmitting = ref(false)
const deleteSubmitting = ref('')
const editingMeeting = reactive({
  id: '',
  name: '',
})
const editName = ref('')
const recentMeeting = computed(
  () =>
    store.meetings.find((meeting) => meeting.id === store.activeMeetingId) || store.meetings[0],
)
onMounted(() => store.initialize())
function openMeeting(meetingId, versionKey = 'draft') {
  store.rememberMeeting(meetingId, versionKey)
  router.push({
    path: `/workbench/${meetingId}`,
    query: versionKey && versionKey !== 'draft' ? { version: versionKey } : {},
  })
}
function openRecentMeeting() {
  if (!recentMeeting.value) return
  openMeeting(recentMeeting.value.id, store.recentVersionKey)
}
function formatTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
function editMeeting(meeting) {
  editingMeeting.id = meeting.id
  editingMeeting.name = meeting.name
  editName.value = meeting.name
  editVisible.value = true
}
async function saveMeetingName() {
  if (editSubmitting.value) return
  const trimmedName = editName.value.trim()
  if (!trimmedName) {
    ElMessage.warning('请输入会议名称')
    return
  }
  if (!editingMeeting.id || trimmedName === editingMeeting.name) {
    editVisible.value = false
    return
  }
  editSubmitting.value = true
  try {
    const updated = await meetingApi.updateMeetingName(editingMeeting.id, trimmedName)
    const index = store.meetings.findIndex((meeting) => meeting.id === updated.id)
    if (index >= 0) store.meetings[index] = { ...store.meetings[index], ...updated }
    if (store.workspace?.meeting?.id === updated.id) {
      store.workspace.meeting.name = updated.name
    }
    editingMeeting.name = updated.name
    editVisible.value = false
    ElMessage.success('会议名称已更新')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    editSubmitting.value = false
  }
}
async function removeMeeting(meeting) {
  if (deleteSubmitting.value) return
  deleteSubmitting.value = meeting.id
  try {
    await ElMessageBox.confirm(
      `确认删除会议“${meeting.name}”？删除后人员名单、排座和版本记录都会一并移除。`,
      '确认删除会议',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    deleteSubmitting.value = ''
    return
  }
  try {
    await meetingApi.deleteMeeting(meeting.id)
    store.meetings = store.meetings.filter((item) => item.id !== meeting.id)
    if (store.workspace?.meeting?.id === meeting.id) {
      store.workspace = undefined
    }
    if (store.activeMeetingId === meeting.id) {
      const nextMeeting = store.meetings[0]
      store.rememberMeeting(nextMeeting?.id || '', 'draft')
    }
    if (editingMeeting.id === meeting.id) {
      editVisible.value = false
      editingMeeting.id = ''
      editingMeeting.name = ''
      editName.value = ''
    }
    ElMessage.success('会议已删除')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    deleteSubmitting.value = ''
  }
}
</script>

<template>
  <div class="app-page home-page" v-loading="store.loading">
    <header class="app-header home-header">
      <span class="header-spacer" />
    </header>

    <main class="home-scroll">
      <div class="home-content">
        <section class="home-hero">
          <div>
            <h1>今天要安排哪场会议？</h1>
          </div>
          <div class="hero-actions">
            <el-button
              v-if="recentMeeting"
              size="large"
              :icon="ArrowRight"
              @click="openRecentMeeting"
            >
              继续最近会议
            </el-button>
            <el-button
              type="primary"
              size="large"
              :icon="Collection"
              @click="router.push('/venues')"
            >
              场馆模板
            </el-button>
          </div>
        </section>

        <section class="home-section">
          <div class="section-heading">
            <div>
              <h2>我的会议</h2>
            </div>
            <el-button type="primary" :icon="CirclePlus" @click="router.push('/venues')">
              添加会议
            </el-button>
          </div>

          <div v-if="store.meetings.length" class="meeting-grid">
            <article
              v-for="meeting in store.meetings"
              :key="meeting.id"
              class="meeting-card"
              @click="openMeeting(meeting.id)"
            >
              <div class="meeting-icon"><Calendar /></div>
              <div class="meeting-copy">
                <h3>{{ meeting.name }}</h3>
                <p>{{ meeting.layoutName }}</p>
                <span
                  >最近更新：{{ formatTime(meeting.updatedAt) }} · {{ meeting.updatedByName }}</span
                >
              </div>
              <div class="meeting-actions">
                <el-button
                  circle
                  text
                  :icon="EditPen"
                  :disabled="deleteSubmitting === meeting.id"
                  aria-label="编辑会议名称"
                  @click.stop="editMeeting(meeting)"
                />
                <el-button
                  circle
                  text
                  type="danger"
                  :icon="Delete"
                  :loading="deleteSubmitting === meeting.id"
                  aria-label="删除会议"
                  @click.stop="removeMeeting(meeting)"
                />
                <el-button
                  circle
                  text
                  :icon="ArrowRight"
                  :disabled="deleteSubmitting === meeting.id"
                  aria-label="进入排座工作台"
                  @click.stop="openMeeting(meeting.id)"
                />
              </div>
            </article>
          </div>

          <div v-else class="home-empty">
            <Calendar />
            <h3>还没有会议</h3>
            <p>选择一个可用场馆模板，创建会议后即可导入人员并排座。</p>
            <el-button type="primary" :icon="CirclePlus" @click="router.push('/venues')">
              添加会议
            </el-button>
          </div>
        </section>
      </div>
    </main>

    <el-dialog v-model="editVisible" title="编辑会议名称" width="420px">
      <el-form class="meeting-edit-form" :disabled="editSubmitting" @submit.prevent="saveMeetingName">
        <el-form-item label="会议名称" required>
          <el-input
            v-model="editName"
            maxlength="80"
            clearable
            autofocus
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="editSubmitting" @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="saveMeetingName">
          确认
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.home-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.home-header {
  flex: none;
}

.home-scroll {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.home-content {
  width: min(1280px, calc(100% - 64px));
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  margin: 0 auto;
  padding: 32px 0 64px;
}

.home-hero {
  min-height: 160px;
  flex: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 48px;
  padding: 40px 52px;
  color: var(--ink);
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow);
}

.home-hero h1 {
  margin: 0;
  font-size: 34px;
}

.hero-actions {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.home-section {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  margin-top: 34px;
}

.section-heading {
  flex: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}

.section-heading h2 {
  margin: 5px 0 0;
  font-size: 23px;
}

.meeting-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-content: start;
  gap: 16px;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 4px;
}

.meeting-card {
  min-height: 168px;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 22px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow);
  cursor: pointer;
  transition: 0.18s ease;
}

.meeting-card:hover {
  border-color: var(--line-strong);
  box-shadow: var(--shadow-hover);
}

.meeting-icon {
  width: 42px;
  height: 42px;
  flex: none;
  display: grid;
  place-items: center;
  color: var(--brand);
  background: var(--brand-soft);
  border-radius: var(--radius-sm);
}

.meeting-icon svg {
  width: 20px;
}

.meeting-copy {
  flex: 1;
  min-width: 0;
}

.meeting-copy h3 {
  margin: 10px 0 6px;
  overflow: hidden;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meeting-copy p {
  margin: 0 0 18px;
  color: var(--muted);
  font-size: 12px;
}

.meeting-copy > span {
  color: var(--tertiary);
  font-size: 10px;
}

.meeting-actions {
  flex: none;
  display: inline-flex;
  gap: 4px;
}

.meeting-actions :deep(.el-button) {
  margin: 0;
}

.home-empty {
  padding: 56px;
  color: var(--muted);
  background: #fff;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-md);
  text-align: center;
}

.home-empty > svg {
  width: 36px;
}

.meeting-edit-form :deep(.el-form-item) {
  margin-bottom: 0;
}

@media (max-width: 1280px) {
  .meeting-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
