<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Back,
  Check,
  Download,
  House,
  Plus,
  RefreshRight,
  Upload,
  User,
  ZoomIn,
  ZoomOut,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AddParticipantDialog from '@/components/AddParticipantDialog.vue'
import ImportDialog from '@/components/ImportDialog.vue'
import ParticipantPanel from '@/components/ParticipantPanel.vue'
import PublishVersionDialog from '@/components/PublishVersionDialog.vue'
import VenueCanvas from '@/components/VenueCanvas.vue'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage } from '@/api/http'
import { useWorkspaceStore } from '@/stores/workspace'
import { attendingPendingCount } from '@/utils/participantRules'
import { placeFloatingMenu } from '@/utils/workbenchLayout'
const router = useRouter()
const route = useRoute()
const store = useWorkspaceStore()
const zoom = ref(0.92)
const importVisible = ref(false)
const addVisible = ref(false)
const undoStack = ref([])
const redoStack = ref([])
const applyingHistory = ref(false)
const draggingParticipantId = ref()
const activeVersionKey = ref('draft')
const publishedWorkspace = ref()
const loadingVersion = ref(false)
const publishing = ref(false)
const publishVisible = ref(false)
const addMenuVisible = ref(false)
const addTargetElementId = ref()
const participantPanelCollapsed = ref(false)
const autoSaveSeconds = ref(0)
const fabReady = ref(false)
const fab = reactive({
  x: 0,
  y: 0,
  dragging: false,
})
let fabOffsetX = 0
let fabOffsetY = 0
let fabPointerStartX = 0
let fabPointerStartY = 0
let fabMoved = false
let suppressFabClick = false
let addMenuHideTimer
let autoSaveTimer
const workspace = computed(() => publishedWorkspace.value || store.workspace)
const readonlyMode = computed(() => activeVersionKey.value !== 'draft')
const activeVersionId = computed(() => (readonlyMode.value ? activeVersionKey.value : undefined))
const assignedCount = computed(
  () => workspace.value?.participants.filter((person) => person.assignedElementId).length || 0,
)
const pendingCount = computed(
  () => attendingPendingCount(workspace.value?.participants),
)
const activePublishedVersion = computed(() =>
  store.workspace?.versions.find((version) => version.id === activeVersionKey.value),
)
const fabStyle = computed(() => ({
  left: `${fab.x}px`,
  top: `${fab.y}px`,
}))
const addMenuStyle = computed(() => {
  const position = placeFloatingMenu({
    anchor: { x: fab.x, y: fab.y, width: 48, height: 48 },
    menu: { width: 220, height: 132 },
    viewport: { width: window.innerWidth, height: window.innerHeight },
    gap: 10,
    margin: 10,
  })
  return {
    left: `${position.left}px`,
    top: `${position.top}px`,
  }
})
onMounted(async () => {
  const meetingId = typeof route.params.meetingId === 'string' ? route.params.meetingId : ''
  if (meetingId) store.rememberMeeting(meetingId)
  await store.initialize()
  if (store.activeMeetingId && store.activeMeetingId !== meetingId) {
    await router.replace(`/workbench/${store.activeMeetingId}`)
  }
  resetFab()
  window.addEventListener('resize', keepFabInViewport)
  window.addEventListener('beforeunload', warnUnsavedChanges)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', keepFabInViewport)
  window.removeEventListener('beforeunload', warnUnsavedChanges)
  window.clearTimeout(addMenuHideTimer)
  window.clearInterval(autoSaveTimer)
  stopFabDrag()
})
async function switchMeeting(meetingId) {
  if (!(await saveDraft(true))) return
  activeVersionKey.value = 'draft'
  publishedWorkspace.value = undefined
  undoStack.value = []
  redoStack.value = []
  await store.switchMeeting(meetingId)
  await router.replace(`/workbench/${meetingId}`)
}
async function switchVersion(versionKey) {
  if (versionKey !== 'draft' && !(await saveDraft(true))) return
  activeVersionKey.value = versionKey
  store.selectParticipant(undefined)
  draggingParticipantId.value = undefined
  if (versionKey === 'draft') {
    publishedWorkspace.value = undefined
    return
  }
  if (!store.workspace) return
  loadingVersion.value = true
  try {
    publishedWorkspace.value = await meetingApi.versionSnapshot(store.workspace.plan.id, versionKey)
  } catch (error) {
    activeVersionKey.value = 'draft'
    ElMessage.error(apiErrorMessage(error))
  } finally {
    loadingVersion.value = false
  }
}
async function publishDraft() {
  if (!store.workspace || readonlyMode.value) return
  if (pendingCount.value > 0) {
    await ElMessageBox.alert(
      `当前还有 ${pendingCount.value} 位参会人员尚未排座。请先完成全部人员排座，再发布只读版本。`,
      '暂时无法发布',
      {
        type: 'warning',
        confirmButtonText: '我知道了',
      },
    )
    return
  }
  publishVisible.value = true
}

async function confirmPublish(payload) {
  if (!store.workspace || readonlyMode.value) return
  try {
    if (!(await saveDraft(true))) return
    publishing.value = true
    const version = await meetingApi.createVersion(store.workspace.plan.id, payload)
    await store.loadWorkspace()
    publishVisible.value = false
    activeVersionKey.value = version.id
    await switchVersion(version.id)
    ElMessage.success(`“${version.versionName}”已发布，当前进入只读查看`)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    publishing.value = false
  }
}
async function overwriteDraftFromVersion() {
  if (!store.workspace || !activePublishedVersion.value) return
  const versionName = activePublishedVersion.value.versionName
  try {
    await ElMessageBox.confirm(
      `将用“${versionName}”的排座、设备占位、锁定、样式和出席状态覆盖当前草稿。当前名单保持不变，后来新增的人员会保留在待排列表中。`,
      '覆盖当前草稿',
      {
        type: 'warning',
        confirmButtonText: '确认覆盖',
        cancelButtonText: '取消',
      },
    )
    await meetingApi.restoreVersion(store.workspace.plan.id, activePublishedVersion.value.id)
    await store.loadWorkspace()
    activeVersionKey.value = 'draft'
    publishedWorkspace.value = undefined
    store.selectParticipant(undefined)
    undoStack.value = []
    redoStack.value = []
    ElMessage.success(`已使用“${versionName}”覆盖草稿`)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error))
  }
}
async function saveDraft(silent = false) {
  if (readonlyMode.value || !store.dirty) return true
  return store.saveAssignments({ silent })
}
async function goHome() {
  if (!(await saveDraft(true))) return
  await router.push('/')
}
function resetAutoSaveTimer() {
  window.clearInterval(autoSaveTimer)
  autoSaveTimer = undefined
  if (!autoSaveSeconds.value) return
  autoSaveTimer = window.setInterval(() => {
    if (!readonlyMode.value && store.dirty && !store.saving) {
      saveDraft(true)
    }
  }, autoSaveSeconds.value * 1000)
}
function warnUnsavedChanges(event) {
  if (!store.dirty) return
  event.preventDefault()
  event.returnValue = ''
}
watch(autoSaveSeconds, resetAutoSaveTimer)
async function performAssign(participantId, targetElementId) {
  if (readonlyMode.value) return
  if (!store.workspace || applyingHistory.value) {
    await store.assign(participantId, targetElementId)
    return
  }
  const person = store.workspace.participants.find((value) => value.id === participantId)
  if (!person) return
  const originalTarget = person.assignedElementId
  const success = await store.assign(participantId, targetElementId)
  if (!success) return
  undoStack.value.push({
    label: originalTarget ? '移动或交换人员' : '安排人员',
    undo: async () => {
      if (originalTarget) await store.assign(participantId, originalTarget)
      else await store.unassign(participantId)
    },
    redo: async () => {
      await store.assign(participantId, targetElementId)
    },
  })
  redoStack.value = []
}
async function performUnassign(participantId) {
  if (readonlyMode.value) return
  const person = store.workspace?.participants.find((value) => value.id === participantId)
  const originalTarget = person?.assignedElementId
  if (!originalTarget) return
  const success = await store.unassign(participantId)
  if (!success) return
  ElMessage.success('已移回待排列表')
  if (!applyingHistory.value) {
    undoStack.value.push({
      label: '移回待排',
      undo: async () => {
        await store.assign(participantId, originalTarget)
      },
      redo: async () => {
        await store.unassign(participantId)
      },
    })
    redoStack.value = []
  }
}
async function undo() {
  if (readonlyMode.value) return
  const action = undoStack.value.pop()
  if (!action) return
  applyingHistory.value = true
  await action.undo()
  applyingHistory.value = false
  redoStack.value.push(action)
}
async function redo() {
  if (readonlyMode.value) return
  const action = redoStack.value.pop()
  if (!action) return
  applyingHistory.value = true
  await action.redo()
  applyingHistory.value = false
  undoStack.value.push(action)
}
async function onSeatClick(element) {
  if (readonlyMode.value || !element?.id) return
  if (!(await saveDraft(true))) return
  addTargetElementId.value = element.id
  addMenuVisible.value = false
  addVisible.value = true
}
function selectParticipant(person) {
  store.selectParticipant(person)
}
async function updateParticipantAttendance(person, attendanceStatus) {
  if (readonlyMode.value) return
  if (attendanceStatus === 'TEMPORARILY_ABSENT' && person.assignedElementId) {
    try {
      await ElMessageBox.confirm(
        `${person.name} 已安排座位，标记为临时不出席后会释放该座位。`,
        '确认临时不出席',
        {
          type: 'warning',
          confirmButtonText: '确认',
          cancelButtonText: '取消',
        },
      )
    } catch {
      return
    }
  }
  const success = await store.updateAttendance(person.id, attendanceStatus)
  if (success) {
    undoStack.value = []
    redoStack.value = []
  }
}
async function removeParticipant(person) {
  if (readonlyMode.value) return
  try {
    await ElMessageBox.confirm(
      `确认将 ${person.name} 从本次会议名单中移出吗？该人员的座位也会被释放。`,
      '移出会议',
      {
        type: 'warning',
        confirmButtonText: '确认移出',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  if (!(await saveDraft(true))) return
  await store.removeParticipant(person.id)
  undoStack.value = []
  redoStack.value = []
}
function changeZoom(delta) {
  zoom.value = Math.min(2.5, Math.max(0.4, Number((zoom.value + delta).toFixed(2))))
}
function exportPlan(type) {
  store.exportPlan(type, activeVersionId.value)
}
function resetFab() {
  fab.x = Math.max(12, window.innerWidth - 440)
  fab.y = Math.max(76, window.innerHeight - 100)
  fabReady.value = true
}
function keepFabInViewport() {
  fab.x = Math.min(Math.max(4, fab.x), window.innerWidth - 52)
  fab.y = Math.min(Math.max(64, fab.y), window.innerHeight - 52)
}
function startFabDrag(event) {
  window.clearTimeout(addMenuHideTimer)
  addMenuVisible.value = false
  fab.dragging = true
  fabMoved = false
  suppressFabClick = false
  fabPointerStartX = event.clientX
  fabPointerStartY = event.clientY
  fabOffsetX = event.clientX - fab.x
  fabOffsetY = event.clientY - fab.y
  window.addEventListener('pointermove', moveFab)
  window.addEventListener('pointerup', stopFabDrag)
}
function moveFab(event) {
  if (!fab.dragging) return
  if (
    !fabMoved &&
    Math.hypot(event.clientX - fabPointerStartX, event.clientY - fabPointerStartY) < 5
  ) {
    return
  }
  fabMoved = true
  fab.x = Math.min(Math.max(2, event.clientX - fabOffsetX), window.innerWidth - 50)
  fab.y = Math.min(Math.max(64, event.clientY - fabOffsetY), window.innerHeight - 50)
}
function stopFabDrag() {
  if (!fab.dragging) return
  const wasMoved = fabMoved
  fab.dragging = false
  window.removeEventListener('pointermove', moveFab)
  window.removeEventListener('pointerup', stopFabDrag)
  if (!wasMoved) return
  suppressFabClick = true
  window.setTimeout(() => {
    fabMoved = false
    suppressFabClick = false
  }, 0)
}
function handleFabClick() {
  if (suppressFabClick) return
  showAddMenu()
}
function showAddMenu() {
  window.clearTimeout(addMenuHideTimer)
  if (!fab.dragging) addMenuVisible.value = true
}
function scheduleAddMenuHide() {
  window.clearTimeout(addMenuHideTimer)
  addMenuHideTimer = window.setTimeout(() => {
    if (!fab.dragging) addMenuVisible.value = false
  }, 140)
}
async function openSingleAdd() {
  if (!(await saveDraft(true))) return
  addTargetElementId.value = undefined
  addMenuVisible.value = false
  addVisible.value = true
}
async function openBatchImport() {
  if (!(await saveDraft(true))) return
  addMenuVisible.value = false
  importVisible.value = true
}
async function onParticipantAdded(participant) {
  addTargetElementId.value = undefined
  await store.loadWorkspace()
  if (participant) {
    const added = store.workspace?.participants.find((person) => person.id === participant.id)
    if (added) store.selectParticipant(added)
  }
}
</script>

<template>
  <div class="app-page workbench-page" v-loading="store.loading || loadingVersion">
    <header class="app-header">
      <button class="home-brand" title="返回首页" @click="goHome">
        <span class="brand-mark">席</span>
        <span class="brand-copy">
          <strong>会议排座助手</strong>
        </span>
      </button>
      <span class="header-divider" />

      <el-select
        :model-value="store.activeMeetingId"
        class="meeting-selector header-selector"
        popper-class="meeting-select-popper"
        aria-label="选择会议"
        @change="switchMeeting"
      >
        <el-option
          v-for="meeting in store.meetings"
          :key="meeting.id"
          :label="meeting.name"
          :value="meeting.id"
        />
      </el-select>

      <el-select
        v-if="store.workspace"
        :model-value="activeVersionKey"
        class="version-selector header-selector"
        popper-class="version-select-popper"
        aria-label="选择会议版本"
        @change="switchVersion"
      >
        <el-option label="草稿" value="draft" />
        <el-option
          v-for="version in store.workspace.versions"
          :key="version.id"
          :label="version.versionName"
          :value="version.id"
        />
      </el-select>

      <span class="header-spacer" />
      <el-button class="header-home" text :icon="House" @click="goHome">
        首页
      </el-button>
      <span class="save-state">
        <i :class="{ active: store.saving, dirty: store.dirty }" />
        <template v-if="readonlyMode">已发布版本</template>
        <template v-else>
          {{ store.saving ? '保存中' : store.dirty ? '有未保存改动' : '草稿已保存' }}
        </template>
      </span>
      <div v-if="!readonlyMode" class="header-save-control">
        <el-button type="primary" @click="saveDraft(false)">保存</el-button>
        <el-select
          v-model="autoSaveSeconds"
          class="header-auto-save"
          aria-label="自动保存周期"
        >
          <el-option label="自动保存：关闭" :value="0" />
          <el-option label="每30秒自动保存" :value="30" />
          <el-option label="每1分钟自动保存" :value="60" />
          <el-option label="每3分钟自动保存" :value="180" />
          <el-option label="每5分钟自动保存" :value="300" />
        </el-select>
      </div>
      <el-button
        v-if="readonlyMode"
        type="primary"
        plain
        :icon="RefreshRight"
        @click="overwriteDraftFromVersion"
      >
        覆盖当前草稿
      </el-button>
      <el-button
        v-if="!readonlyMode"
        type="primary"
        plain
        :icon="Check"
        :loading="publishing"
        @click="publishDraft"
      >
        发布版本
      </el-button>
    </header>

    <main
      v-if="workspace"
      class="workspace-layout"
      :class="{ 'participant-collapsed': participantPanelCollapsed }"
    >
      <section class="canvas-panel">
        <div class="canvas-toolbar">
          <div class="canvas-title">
            <h1>{{ workspace.meeting.layoutName }}</h1>
          </div>
          <div class="canvas-stats">
            <span
              ><b>{{ workspace.participants.length }}</b
              ><small>总人数</small></span
            >
            <span
              ><b>{{ assignedCount }}</b
              ><small>已排</small></span
            >
            <span
              ><b>{{ pendingCount }}</b
              ><small>待排</small></span
            >
          </div>
          <el-tag v-if="readonlyMode" type="primary" effect="plain">
            {{ activePublishedVersion?.versionName }}
          </el-tag>
          <span class="toolbar-spacer" />
          <el-button-group>
            <el-button
              :icon="Back"
              :disabled="readonlyMode || !undoStack.length"
              title="撤销"
              @click="undo"
            />
            <el-button
              :icon="RefreshRight"
              :disabled="readonlyMode || !redoStack.length"
              title="重做"
              @click="redo"
            />
          </el-button-group>
          <el-button-group>
            <el-button :icon="ZoomOut" :disabled="zoom <= 0.4" @click="changeZoom(-0.1)" />
            <el-button class="zoom-value">{{ Math.round(zoom * 100) }}%</el-button>
            <el-button :icon="ZoomIn" :disabled="zoom >= 2.5" @click="changeZoom(0.1)" />
          </el-button-group>
          <el-dropdown v-if="readonlyMode" split-button @click="exportPlan('excel')">
            <el-icon><Download /></el-icon>
            导出Excel
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="exportPlan('excel')">导出Excel工作簿</el-dropdown-item>
                <el-dropdown-item @click="exportPlan('pdf')">导出PDF场馆图</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <div class="canvas-body">
          <VenueCanvas
            :workspace="workspace"
            :zoom="zoom"
            :readonly="readonlyMode"
            :selected-participant-id="store.selectedParticipantId"
            :dragging-participant-id="draggingParticipantId"
            @assign="performAssign"
            @unassign="performUnassign"
            @select="selectParticipant"
            @seat-click="onSeatClick"
            @drag-state="draggingParticipantId = $event"
            @zoom-change="changeZoom($event)"
          />
        </div>

      </section>

      <div class="participant-side">
        <button
          class="participant-panel-toggle"
          :title="participantPanelCollapsed ? '展开人员安排' : '收起人员安排'"
          :aria-label="participantPanelCollapsed ? '展开人员安排' : '收起人员安排'"
          @click="participantPanelCollapsed = !participantPanelCollapsed"
        >
          {{ participantPanelCollapsed ? '‹' : '›' }}
        </button>
        <ParticipantPanel
          v-show="!participantPanelCollapsed"
          :participants="workspace.participants"
          :field-definitions="workspace.fieldDefinitions"
          :selected-id="store.selectedParticipantId"
          :saving="store.saving"
          :readonly="readonlyMode"
          @select="selectParticipant"
          @unassign="performUnassign"
          @attendance="updateParticipantAttendance"
          @remove="removeParticipant"
          @drag-state="draggingParticipantId = $event"
        />
      </div>
    </main>

    <template v-if="workspace && !readonlyMode && fabReady">
      <div
        v-if="addMenuVisible"
        class="add-menu"
        :style="addMenuStyle"
        @mouseenter="showAddMenu"
        @mouseleave="scheduleAddMenuHide"
      >
        <button @click="openSingleAdd">
          <el-icon><User /></el-icon>
          <span><strong>单个添加</strong></span>
        </button>
        <button @click="openBatchImport">
          <el-icon><Upload /></el-icon>
          <span><strong>上传Excel批量添加</strong></span>
        </button>
      </div>
      <button
        class="floating-add"
        :class="{ dragging: fab.dragging }"
        :style="fabStyle"
        aria-label="添加参会人员"
        title="拖动可调整位置"
        @pointerdown="startFabDrag"
        @click="handleFabClick"
        @mouseenter="showAddMenu"
        @mouseleave="scheduleAddMenuHide"
      >
        <Plus />
      </button>
    </template>

    <AddParticipantDialog
      v-if="store.workspace && !readonlyMode"
      v-model="addVisible"
      :meeting-id="store.workspace.meeting.id"
      :target-element-id="addTargetElementId"
      :participants="store.workspace.participants"
      @done="onParticipantAdded"
    />
    <ImportDialog
      v-if="store.workspace && !readonlyMode"
      v-model="importVisible"
      :meeting-id="store.workspace.meeting.id"
      @done="store.loadWorkspace"
    />
    <PublishVersionDialog
      v-if="store.workspace && !readonlyMode"
      v-model="publishVisible"
      :versions="store.workspace.versions"
      :submitting="publishing"
      @publish="confirmPublish"
    />
  </div>
</template>

<style scoped>
.workbench-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.home-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0;
  color: #fff;
  background: transparent;
  border: 0;
  cursor: pointer;
  text-align: left;
}

.home-brand .brand-copy {
  display: block;
}

.meeting-selector {
  width: 300px;
}

.version-selector {
  width: 190px;
}

.header-selector :deep(.el-select__wrapper) {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.25) inset;
}

.header-selector :deep(.el-select__selected-item),
.header-selector :deep(.el-select__placeholder),
.header-selector :deep(.el-select__caret) {
  color: #fff !important;
}

.save-state {
  display: flex;
  align-items: center;
  gap: 7px;
  color: rgba(255, 255, 255, 0.72);
  font-size: 11px;
  white-space: nowrap;
}

.header-home {
  color: rgba(255, 255, 255, 0.86) !important;
}

.header-home:hover {
  color: #fff !important;
  background: rgba(255, 255, 255, 0.12) !important;
}

.header-save-control {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.header-auto-save {
  width: 142px;
}

.header-auto-save :deep(.el-select__wrapper) {
  min-height: 32px;
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.28) inset;
}

.header-auto-save :deep(.el-select__selected-item),
.header-auto-save :deep(.el-select__caret) {
  color: #fff !important;
}

.save-state i {
  width: 7px;
  height: 7px;
  background: #66d19e;
  border-radius: 50%;
}

.save-state i.active {
  background: #f5c451;
  animation: blink 1s infinite;
}

.save-state i.dirty:not(.active) {
  background: #f59e0b;
}

.workspace-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  overflow: hidden;
  transition: grid-template-columns 0.18s ease;
}

.workspace-layout.participant-collapsed {
  grid-template-columns: minmax(0, 1fr) 36px;
}

.participant-side {
  min-width: 0;
  min-height: 0;
  position: relative;
  background: #fff;
}

.participant-panel-toggle {
  width: 27px;
  height: 52px;
  position: absolute;
  top: calc(50% - 26px);
  left: -13px;
  z-index: 25;
  padding: 0;
  color: #3565a6;
  background: #fff;
  border: 1px solid #b9cce6;
  border-radius: 14px 0 0 14px;
  box-shadow: -5px 4px 12px rgba(37, 85, 151, 0.12);
  cursor: pointer;
  font-size: 23px;
  line-height: 1;
}

.participant-panel-toggle:hover {
  color: #174f99;
  background: #edf5ff;
}

.participant-collapsed .participant-panel-toggle {
  left: 4px;
  border-radius: 14px;
}

.canvas-panel {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.canvas-toolbar {
  min-height: 70px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 18px;
  background: #fff;
  border-bottom: 1px solid var(--line);
}

.canvas-title {
  width: 150px;
  min-width: 150px;
}

.canvas-title h1 {
  margin: 2px 0 0;
  font-size: 16px;
}

.canvas-stats {
  flex: none;
  display: flex;
  gap: 14px;
  padding-left: 14px;
  border-left: 1px solid var(--line);
}

.canvas-stats span {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.canvas-stats b {
  color: #12325f;
  font-size: 21px;
  line-height: 1;
}

.canvas-stats small {
  color: #718096;
  font-size: 11px;
}

.toolbar-spacer {
  flex: 1;
  min-width: 0;
}

.canvas-toolbar > .el-button-group,
.canvas-toolbar :deep(.el-dropdown) {
  flex: none;
}

.canvas-toolbar > .el-button-group {
  display: inline-flex;
  flex-wrap: nowrap;
}

.zoom-value {
  width: 56px;
  padding: 0;
  color: #64748b;
  pointer-events: none;
}

.canvas-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.floating-add {
  width: 48px;
  height: 48px;
  position: fixed;
  z-index: 70;
  display: grid;
  place-items: center;
  padding: 0;
  color: #fff;
  background: #2f80d8;
  border: 0;
  border-radius: 50%;
  box-shadow: 0 9px 22px rgba(39, 94, 164, 0.36);
  cursor: grab;
  transition:
    transform 0.2s ease,
    opacity 0.2s ease;
}

.floating-add svg {
  width: 20px;
}

.floating-add.dragging {
  cursor: grabbing;
  transition: none;
}

.add-menu {
  width: 220px;
  position: fixed;
  z-index: 72;
  display: grid;
  gap: 4px;
  padding: 7px;
  background: #fff;
  border: 1px solid #cfdced;
  border-radius: 12px;
  box-shadow: 0 16px 38px rgba(27, 62, 112, 0.22);
}

.add-menu button {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px;
  color: #2f486a;
  background: transparent;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
}

.add-menu button:hover {
  color: #1d5ba7;
  background: #edf5ff;
}

@media (max-width: 1450px) {
  .workbench-page > .app-header {
    gap: 10px;
    padding-inline: 16px;
  }

  .home-brand .brand-copy {
    min-width: 132px;
  }

  .save-state {
    display: none;
  }

  .meeting-selector {
    width: 230px;
  }

  .version-selector {
    width: 158px;
  }
}

.add-menu .el-icon {
  width: 30px;
  height: 30px;
  flex: none;
  display: grid;
  place-items: center;
  color: #2f6eb6;
  background: #e5f0ff;
  border-radius: 8px;
}

.add-menu span {
  display: block;
}

@keyframes blink {
  50% {
    opacity: 0.35;
  }
}
</style>
