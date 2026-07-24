<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Back,
  Check,
  Download,
  Document,
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
import ParticipantDetailDrawer from '@/components/ParticipantDetailDrawer.vue'
import ParticipantPanel from '@/components/ParticipantPanel.vue'
import VenueCanvas from '@/components/VenueCanvas.vue'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage } from '@/api/http'
import { useWorkspaceStore } from '@/stores/workspace'
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
const addMenuVisible = ref(false)
const fabReady = ref(false)
const fab = reactive({
  x: 0,
  y: 0,
  edge: 'right',
  hidden: false,
  dragging: false,
})
let fabOffsetX = 0
let fabOffsetY = 0
let fabMoved = false
let hideTimer
const workspace = computed(() => publishedWorkspace.value || store.workspace)
const readonlyMode = computed(() => activeVersionKey.value !== 'draft')
const activeVersionId = computed(() => (readonlyMode.value ? activeVersionKey.value : undefined))
const selectedParticipant = computed(() =>
  workspace.value?.participants.find((person) => person.id === store.selectedParticipantId),
)
const selectedSeat = computed(() =>
  workspace.value?.layout.elements.find(
    (element) => element.id === selectedParticipant.value?.assignedElementId,
  ),
)
const assignedCount = computed(
  () => workspace.value?.participants.filter((person) => person.assignedElementId).length || 0,
)
const pendingCount = computed(
  () => workspace.value?.participants.filter((person) => !person.assignedElementId).length || 0,
)
const activePublishedVersion = computed(() =>
  store.workspace?.versions.find((version) => version.id === activeVersionKey.value),
)
const fabStyle = computed(() => ({
  left: `${fab.x}px`,
  top: `${fab.y}px`,
}))
const addMenuStyle = computed(() => {
  const width = 220
  const height = 150
  return {
    left: `${Math.min(Math.max(10, fab.x - width + 48), window.innerWidth - width - 10)}px`,
    top: `${Math.min(Math.max(70, fab.y - height - 10), window.innerHeight - height - 10)}px`,
  }
})
onMounted(async () => {
  const meetingId = typeof route.params.meetingId === 'string' ? route.params.meetingId : ''
  if (meetingId) store.activeMeetingId = meetingId
  await store.initialize()
  if (store.activeMeetingId && store.activeMeetingId !== meetingId) {
    await router.replace(`/workbench/${store.activeMeetingId}`)
  }
  resetFab()
  window.addEventListener('resize', keepFabInViewport)
  scheduleFabHide()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', keepFabInViewport)
  stopFabDrag()
  if (hideTimer) window.clearTimeout(hideTimer)
})
async function switchMeeting(meetingId) {
  activeVersionKey.value = 'draft'
  publishedWorkspace.value = undefined
  undoStack.value = []
  redoStack.value = []
  await store.switchMeeting(meetingId)
  await router.replace(`/workbench/${meetingId}`)
}
async function switchVersion(versionKey) {
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
  try {
    const nextVersion = (store.workspace.versions[0]?.versionNo || 0) + 1
    const { value } = await ElMessageBox.prompt(
      `本次发布后将生成只读版本 V${nextVersion}，草稿仍可继续修改。`,
      `发布 V${nextVersion}`,
      {
        confirmButtonText: '确认发布',
        cancelButtonText: '取消',
        inputPlaceholder: '填写本次变更说明（可留空）',
        inputType: 'textarea',
      },
    )
    publishing.value = true
    const version = await meetingApi.createVersion(store.workspace.plan.id, {
      versionName: `V${nextVersion}`,
      changeNote: value?.trim(),
      automatic: false,
    })
    await store.loadWorkspace()
    activeVersionKey.value = version.id
    await switchVersion(version.id)
    ElMessage.success(`V${nextVersion} 已发布，当前进入只读查看`)
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error))
  } finally {
    publishing.value = false
  }
}
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
function onSeatClick() {
  // 第一版仅保留拖拽排座，空座位点击不再触发隐藏的“连续排座”模式。
}
function selectParticipant(person) {
  store.selectParticipant(person)
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
  fab.edge = 'right'
  fabReady.value = true
}
function keepFabInViewport() {
  fab.x = Math.min(Math.max(4, fab.x), window.innerWidth - 52)
  fab.y = Math.min(Math.max(64, fab.y), window.innerHeight - 52)
}
function startFabDrag(event) {
  if (hideTimer) window.clearTimeout(hideTimer)
  fab.hidden = false
  fab.dragging = true
  fabMoved = false
  fabOffsetX = event.clientX - fab.x
  fabOffsetY = event.clientY - fab.y
  window.addEventListener('pointermove', moveFab)
  window.addEventListener('pointerup', stopFabDrag)
  event.preventDefault()
}
function moveFab(event) {
  if (!fab.dragging) return
  fabMoved = true
  fab.x = Math.min(Math.max(2, event.clientX - fabOffsetX), window.innerWidth - 50)
  fab.y = Math.min(Math.max(64, event.clientY - fabOffsetY), window.innerHeight - 50)
}
function stopFabDrag(event) {
  if (!fab.dragging) return
  const wasMoved = fabMoved
  fab.dragging = false
  window.removeEventListener('pointermove', moveFab)
  window.removeEventListener('pointerup', stopFabDrag)
  const distances = {
    left: fab.x,
    right: window.innerWidth - fab.x - 48,
    top: fab.y - 64,
    bottom: window.innerHeight - fab.y - 48,
  }
  fab.edge = Object.entries(distances).sort((left, right) => left[1] - right[1])[0]?.[0] || 'right'
  if (fab.edge === 'left') fab.x = 4
  if (fab.edge === 'right') fab.x = window.innerWidth - 52
  if (fab.edge === 'top') fab.y = 66
  if (fab.edge === 'bottom') fab.y = window.innerHeight - 52
  if (!wasMoved && event) {
    fabMoved = false
    toggleAddMenu()
    return
  }
  window.setTimeout(() => {
    fabMoved = false
  }, 0)
  scheduleFabHide()
}
function toggleAddMenu() {
  if (fabMoved) return
  fab.hidden = false
  addMenuVisible.value = !addMenuVisible.value
  if (addMenuVisible.value && hideTimer) window.clearTimeout(hideTimer)
  else scheduleFabHide()
}
function revealFab() {
  fab.hidden = false
  if (hideTimer) window.clearTimeout(hideTimer)
}
function scheduleFabHide() {
  if (hideTimer) window.clearTimeout(hideTimer)
  hideTimer = window.setTimeout(() => {
    if (!fab.dragging && !addMenuVisible.value) fab.hidden = true
  }, 3600)
}
function openSingleAdd() {
  addMenuVisible.value = false
  addVisible.value = true
  scheduleFabHide()
}
function openBatchImport() {
  addMenuVisible.value = false
  importVisible.value = true
  scheduleFabHide()
}
function downloadTemplate() {
  const hasAwardData = workspace.value?.participants.some((person) => person.awards.length)
  const templateCode = hasAwardData ? 'AWARD_CEREMONY_V1' : 'GENERAL_V1'
  window.open(`/api/import-templates/${templateCode}/file`, '_blank')
  addMenuVisible.value = false
  scheduleFabHide()
}
</script>

<template>
  <div class="app-page workbench-page" v-loading="store.loading || loadingVersion">
    <header class="app-header">
      <button class="home-brand" title="返回首页" @click="router.push('/')">
        <span class="brand-mark">席</span>
        <span class="brand-copy">
          <strong>会议排座助手</strong>
          <small>Meeting Seating Workspace</small>
        </span>
      </button>
      <span class="header-divider" />

      <el-select
        v-model="store.activeMeetingId"
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
        >
          <div class="meeting-option">
            <span>{{ meeting.name }}</span>
            <small>{{ meeting.layoutName }}</small>
          </div>
        </el-option>
      </el-select>

      <el-select
        v-if="store.workspace"
        v-model="activeVersionKey"
        class="version-selector header-selector"
        popper-class="version-select-popper"
        aria-label="选择会议版本"
        @change="switchVersion"
      >
        <el-option label="草稿（可编辑）" value="draft">
          <span>草稿</span>
          <small class="version-option-note">可编辑</small>
        </el-option>
        <el-option
          v-for="version in store.workspace.versions"
          :key="version.id"
          :label="`V${version.versionNo} · ${version.versionName}`"
          :value="version.id"
        >
          <span>V{{ version.versionNo }} · {{ version.versionName }}</span>
          <small class="version-option-note">只读</small>
        </el-option>
      </el-select>

      <div v-if="workspace" class="header-context">
        <span>{{ workspace.meeting.layoutName }}</span>
        <b v-if="readonlyMode">已发布 · 只读</b>
        <b v-else>草稿</b>
      </div>

      <span class="header-spacer" />
      <span class="save-state">
        <i :class="{ active: store.saving }" />
        <template v-if="readonlyMode">正在查看已发布版本</template>
        <template v-else>{{ store.saving ? '保存中' : '草稿已自动保存' }}</template>
      </span>
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

    <main v-if="workspace" class="workspace-layout">
      <section class="canvas-panel">
        <div class="canvas-toolbar">
          <div class="canvas-title">
            <span class="eyebrow">VENUE LAYOUT · V{{ workspace.meeting.layoutVersion }}</span>
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
            V{{ activePublishedVersion?.versionNo }} 只读版本
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
          <el-dropdown split-button @click="exportPlan('excel')">
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

        <div class="legend-bar">
          <span><i class="legend-seat" /> 空座位</span>
          <span v-for="rule in workspace.styleRules" :key="rule.value">
            <i :style="{ backgroundColor: rule.backgroundColor }" />
            {{ rule.value }}
          </span>
          <span><i class="legend-guest" /> 嘉宾（无批次底色）</span>
          <span><i class="legend-device" /> 设备/禁用</span>
        </div>

        <div class="canvas-body">
          <VenueCanvas
            :workspace="workspace"
            :zoom="zoom"
            :readonly="readonlyMode"
            :selected-participant-id="store.selectedParticipantId"
            :dragging-participant-id="draggingParticipantId"
            @assign="performAssign"
            @select="selectParticipant"
            @seat-click="onSeatClick"
            @drag-state="draggingParticipantId = $event"
            @zoom-change="changeZoom($event)"
          />
        </div>

        <ParticipantDetailDrawer
          :participant="selectedParticipant"
          :seat="selectedSeat"
          :readonly="readonlyMode"
          @lock="store.setLock"
          @unassign="performUnassign"
          @remove="store.removeParticipant"
        />
      </section>

      <ParticipantPanel
        :participants="workspace.participants"
        :field-definitions="workspace.fieldDefinitions"
        :selected-id="store.selectedParticipantId"
        :saving="store.saving"
        :readonly="readonlyMode"
        @select="selectParticipant"
        @unassign="performUnassign"
        @drag-state="draggingParticipantId = $event"
      />
    </main>

    <template v-if="workspace && !readonlyMode && fabReady">
      <div
        v-if="addMenuVisible"
        class="add-menu"
        :style="addMenuStyle"
        @mouseenter="revealFab"
        @mouseleave="scheduleFabHide"
      >
        <button @click="openSingleAdd">
          <el-icon><User /></el-icon>
          <span><strong>单个添加</strong><small>录入一位参会人员</small></span>
        </button>
        <button @click="downloadTemplate">
          <el-icon><Document /></el-icon>
          <span><strong>下载Excel模板</strong><small>按标准列名准备名单</small></span>
        </button>
        <button @click="openBatchImport">
          <el-icon><Upload /></el-icon>
          <span><strong>上传Excel批量添加</strong><small>解析后预览并确认导入</small></span>
        </button>
      </div>
      <button
        class="floating-add"
        :class="[`edge-${fab.edge}`, { hidden: fab.hidden, dragging: fab.dragging }]"
        :style="fabStyle"
        aria-label="添加参会人员"
        title="拖动可调整位置"
        @pointerdown="startFabDrag"
        @mouseenter="revealFab"
        @mouseleave="scheduleFabHide"
      >
        <Plus />
      </button>
    </template>

    <AddParticipantDialog
      v-if="store.workspace && !readonlyMode"
      v-model="addVisible"
      :meeting-id="store.workspace.meeting.id"
      @done="store.loadWorkspace"
    />
    <ImportDialog
      v-if="store.workspace && !readonlyMode"
      v-model="importVisible"
      :meeting-id="store.workspace.meeting.id"
      @done="store.loadWorkspace"
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
  display: grid;
}

.home-brand .brand-copy small {
  color: rgba(255, 255, 255, 0.66);
  font-size: 10px;
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

.version-option-note {
  float: right;
  margin-left: 14px;
  color: #8794a7;
}

.header-context {
  display: grid;
  gap: 2px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 10px;
}

.header-context b {
  color: #fff;
  font-weight: 650;
}

.save-state {
  display: flex;
  align-items: center;
  gap: 7px;
  color: rgba(255, 255, 255, 0.72);
  font-size: 11px;
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

.workspace-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  overflow: hidden;
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

.legend-bar {
  min-height: 34px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 18px;
  overflow-x: auto;
  color: #718096;
  background: #fafbfc;
  border-bottom: 1px solid #e5e9ef;
  font-size: 10px;
  white-space: nowrap;
}

.legend-bar span {
  display: flex;
  align-items: center;
  gap: 5px;
}

.legend-bar i {
  width: 10px;
  height: 10px;
  display: inline-block;
  background: #fff;
  border: 1px solid #cbd5e1;
  border-radius: 2px;
}

.legend-bar .legend-guest {
  background: #fff;
  border: 2px solid #718096;
}

.legend-bar .legend-device {
  background: repeating-linear-gradient(-45deg, #f8d978 0, #f8d978 3px, #fff0b8 3px, #fff0b8 6px);
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

.floating-add.hidden.edge-right {
  transform: translateX(22px);
}

.floating-add.hidden.edge-left {
  transform: translateX(-22px);
}

.floating-add.hidden.edge-top {
  transform: translateY(-22px);
}

.floating-add.hidden.edge-bottom {
  transform: translateY(22px);
}

.floating-add.hidden {
  opacity: 0.72;
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
  display: grid;
  gap: 2px;
}

.add-menu small {
  color: #7c8ca2;
  font-size: 10px;
}

@keyframes blink {
  50% {
    opacity: 0.35;
  }
}
</style>
