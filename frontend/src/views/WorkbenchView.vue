<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Back,
  Clock,
  Download,
  FolderOpened,
  Plus,
  RefreshRight,
  Upload,
  ZoomIn,
  ZoomOut,
} from '@element-plus/icons-vue'
import AddParticipantDialog from '@/components/AddParticipantDialog.vue'
import ImportDialog from '@/components/ImportDialog.vue'
import ParticipantDetailDrawer from '@/components/ParticipantDetailDrawer.vue'
import ParticipantPanel from '@/components/ParticipantPanel.vue'
import VenueCanvas from '@/components/VenueCanvas.vue'
import VersionDialog from '@/components/VersionDialog.vue'
import { useWorkspaceStore } from '@/stores/workspace'
import type { LayoutElement, Participant } from '@/types/workspace'

const router = useRouter()
const store = useWorkspaceStore()
const zoom = ref(0.92)
const importVisible = ref(false)
const addVisible = ref(false)
const versionVisible = ref(false)
const queue = ref<string[]>([])
const undoStack = ref<HistoryAction[]>([])
const redoStack = ref<HistoryAction[]>([])
const applyingHistory = ref(false)

interface HistoryAction {
  label: string
  undo: () => Promise<void>
  redo: () => Promise<void>
}

const selectedSeat = computed(() =>
  store.workspace?.layout.elements.find(
    (element) => element.id === store.selectedParticipant?.assignedElementId,
  ),
)
const continuousParticipantId = computed(() => queue.value[0])

onMounted(() => store.initialize())

async function performAssign(participantId: string, targetElementId: string) {
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

async function performUnassign(participantId: string) {
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
  const action = undoStack.value.pop()
  if (!action) return
  applyingHistory.value = true
  await action.undo()
  applyingHistory.value = false
  redoStack.value.push(action)
}

async function redo() {
  const action = redoStack.value.pop()
  if (!action) return
  applyingHistory.value = true
  await action.redo()
  applyingHistory.value = false
  undoStack.value.push(action)
}

function onSeatClick(element: LayoutElement) {
  if (continuousParticipantId.value) {
    performAssign(continuousParticipantId.value, element.id)
  }
}

function selectParticipant(person?: Participant) {
  store.selectParticipant(person)
}

function versionLabel() {
  return store.workspace?.plan.currentVersionNo
    ? `V${store.workspace.plan.currentVersionNo}`
    : '未保存版本'
}
</script>

<template>
  <div class="app-page workbench-page" v-loading="store.loading">
    <header class="app-header">
      <div class="brand-mark">席</div>
      <div class="brand-copy">
        <strong>会议排座助手</strong>
        <span>Meeting Seating Workspace</span>
      </div>
      <span class="header-divider" />

      <el-select
        v-model="store.activeMeetingId"
        class="meeting-selector"
        popper-class="meeting-select-popper"
        @change="store.switchMeeting"
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

      <div v-if="store.workspace" class="header-context">
        <span>{{ store.workspace.meeting.layoutName }}</span>
        <b>{{ versionLabel() }}</b>
      </div>

      <span class="header-spacer" />
      <span class="save-state">
        <i :class="{ active: store.saving }" />
        {{ store.saving ? '保存中' : '草稿已自动保存' }}
      </span>
      <el-button
        text
        class="header-text-button"
        :icon="FolderOpened"
        @click="router.push('/venues')"
      >
        场馆库
      </el-button>
      <el-button text class="header-text-button" :icon="Upload" @click="importVisible = true">
        导入
      </el-button>
      <el-button type="primary" plain :icon="Clock" @click="versionVisible = true">
        保存版本
      </el-button>
    </header>

    <main v-if="store.workspace" class="workspace-layout">
      <section class="canvas-panel">
        <div class="canvas-toolbar">
          <div class="canvas-title">
            <span class="eyebrow">VENUE LAYOUT · V{{ store.workspace.meeting.layoutVersion }}</span>
            <h1>{{ store.workspace.meeting.layoutName }}</h1>
          </div>
          <div class="canvas-stats">
            <span
              ><b>{{ store.workspace.participants.length }}</b> 总人数</span
            >
            <span
              ><b>{{ store.assignedCount }}</b> 已排</span
            >
            <span
              ><b>{{ store.pendingCount }}</b> 待排</span
            >
          </div>
          <span class="toolbar-spacer" />
          <el-button-group>
            <el-button :icon="Back" :disabled="!undoStack.length" title="撤销" @click="undo" />
            <el-button
              :icon="RefreshRight"
              :disabled="!redoStack.length"
              title="重做"
              @click="redo"
            />
          </el-button-group>
          <el-button-group>
            <el-button
              :icon="ZoomOut"
              :disabled="zoom <= 0.56"
              @click="zoom = Math.max(0.56, zoom - 0.08)"
            />
            <el-button class="zoom-value">{{ Math.round(zoom * 100) }}%</el-button>
            <el-button
              :icon="ZoomIn"
              :disabled="zoom >= 1.35"
              @click="zoom = Math.min(1.35, zoom + 0.08)"
            />
          </el-button-group>
          <el-dropdown split-button @click="store.exportPlan('excel')">
            <el-icon><Download /></el-icon>
            导出Excel
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="store.exportPlan('excel')"
                  >导出Excel工作簿</el-dropdown-item
                >
                <el-dropdown-item @click="store.exportPlan('pdf')">导出PDF场馆图</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <div class="legend-bar">
          <span><i class="legend-seat" /> 空座位</span>
          <span v-for="rule in store.workspace.styleRules" :key="rule.value">
            <i :style="{ backgroundColor: rule.backgroundColor }" />
            {{ rule.value }}
          </span>
          <span><i class="legend-guest" /> 嘉宾（无批次底色）</span>
          <span><i class="legend-device" /> 设备/禁用</span>
        </div>

        <div class="canvas-body">
          <VenueCanvas
            :workspace="store.workspace"
            :zoom="zoom"
            :selected-participant-id="store.selectedParticipantId"
            :continuous-participant-id="continuousParticipantId"
            @assign="performAssign"
            @select="selectParticipant"
            @seat-click="onSeatClick"
          />
        </div>

        <ParticipantDetailDrawer
          :participant="store.selectedParticipant"
          :seat="selectedSeat"
          @lock="store.setLock"
          @unassign="performUnassign"
          @remove="store.removeParticipant"
        />
      </section>

      <ParticipantPanel
        :participants="store.workspace.participants"
        :field-definitions="store.workspace.fieldDefinitions"
        :selected-id="store.selectedParticipantId"
        :saving="store.saving"
        @select="selectParticipant"
        @unassign="performUnassign"
        @queue-change="queue = $event"
      />
    </main>

    <el-button class="floating-add" type="primary" circle :icon="Plus" @click="addVisible = true" />

    <AddParticipantDialog
      v-if="store.workspace"
      v-model="addVisible"
      :meeting-id="store.workspace.meeting.id"
      @done="store.loadWorkspace"
    />
    <ImportDialog
      v-if="store.workspace"
      v-model="importVisible"
      :meeting-id="store.workspace.meeting.id"
      @done="store.loadWorkspace"
    />
    <VersionDialog
      v-if="store.workspace"
      v-model="versionVisible"
      :plan-id="store.workspace.plan.id"
      @done="store.loadWorkspace"
    />
  </div>
</template>

<style scoped>
.workbench-page {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.meeting-selector {
  width: 300px;
}

.meeting-selector :deep(.el-select__wrapper) {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.22) inset;
}

.header-context {
  display: grid;
  gap: 2px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 11px;
}

.header-context b {
  color: #fff;
  font-weight: 650;
}

.save-state {
  display: flex;
  align-items: center;
  gap: 7px;
  color: rgba(255, 255, 255, 0.68);
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

.header-text-button {
  color: rgba(255, 255, 255, 0.82);
}

.workspace-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 370px;
}

.canvas-panel {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.canvas-toolbar {
  min-height: 70px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 18px;
  background: #fff;
  border-bottom: 1px solid var(--line);
}

.canvas-title {
  min-width: 210px;
}

.canvas-title h1 {
  margin: 2px 0 0;
  font-size: 16px;
}

.canvas-stats {
  display: flex;
  gap: 17px;
  padding-left: 18px;
  border-left: 1px solid var(--line);
}

.canvas-stats span {
  display: grid;
  gap: 2px;
  color: #718096;
  font-size: 10px;
}

.canvas-stats b {
  color: #26354e;
  font-size: 16px;
}

.toolbar-spacer {
  flex: 1;
}

.zoom-value {
  width: 54px;
  padding: 0;
  color: #64748b;
  pointer-events: none;
}

.legend-bar {
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 18px;
  overflow: hidden;
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
}

.floating-add {
  position: fixed;
  right: 386px;
  bottom: 88px;
  z-index: 30;
  width: 44px;
  height: 44px;
  box-shadow: 0 8px 18px rgba(39, 79, 147, 0.3);
}

@keyframes blink {
  50% {
    opacity: 0.35;
  }
}
</style>
