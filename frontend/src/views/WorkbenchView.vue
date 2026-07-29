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
import EditParticipantDialog from '@/components/EditParticipantDialog.vue'
import ExportOptionsDialog from '@/components/ExportOptionsDialog.vue'
import GroupColorLegend from '@/components/GroupColorLegend.vue'
import ImportDialog from '@/components/ImportDialog.vue'
import ParticipantPanel from '@/components/ParticipantPanel.vue'
import PublishVersionDialog from '@/components/PublishVersionDialog.vue'
import RegionCreateDialog from '@/components/RegionCreateDialog.vue'
import RegionMarkerPanel from '@/components/RegionMarkerPanel.vue'
import VenueCanvas from '@/components/VenueCanvas.vue'
import VenueLayoutEditor from '@/components/VenueLayoutEditor.vue'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage } from '@/api/http'
import { useWorkspaceStore } from '@/stores/workspace'
import {
  buildFieldColorEntries,
  buildParticipantColorMap,
  GROUP_COLOR_PALETTE,
  readGroupColorOverrides,
  saveGroupColorOverride,
} from '@/utils/groupColors'
import { attendingPendingCount } from '@/utils/participantRules'
import { reservedItems, toggleSeatSelection } from '@/utils/seatRegions'
import { normalizeHexColor } from '@/utils/venuePreferences'
import { toElementPayload } from '@/utils/venueModel'
import { placeFloatingMenu } from '@/utils/workbenchLayout'
const router = useRouter()
const route = useRoute()
const store = useWorkspaceStore()
const zoom = ref(0.92)
const importVisible = ref(false)
const exportOptionsVisible = ref(false)
const addVisible = ref(false)
const undoStack = ref([])
const redoStack = ref([])
const applyingHistory = ref(false)
const draggingParticipantId = ref()
const activeVersionKey = ref('draft')
const workbenchMode = ref('seating')
const groupColorFieldCode = ref('')
const groupColorLegendCollapsed = ref(false)
const groupColorOverrides = ref(readGroupColorOverrides())
const publishedWorkspace = ref()
const loadingVersion = ref(false)
const publishing = ref(false)
const layoutSaving = ref(false)
const publishVisible = ref(false)
const addMenuVisible = ref(false)
const addTargetElementId = ref()
const editParticipantVisible = ref(false)
const editingParticipant = ref()
const participantPanelCollapsed = ref(false)
const autoSaveSeconds = ref(0)
const fabReady = ref(false)
const fab = reactive({
  x: 0,
  y: 0,
  dragging: false,
})
const layoutDraft = ref({
  gridRows: 20,
  gridColumns: 30,
  elements: [],
})
const defaultMarkerDraft = {
  id: '',
  label: '',
  backgroundColor: '#FEF3C7',
  textColor: '#7C2D12',
  bold: true,
}
const regionCreateVisible = ref(false)
const markerDraft = reactive({ ...defaultMarkerDraft })
const markerSelection = ref(new Set())
const markerSubmitting = ref(false)
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
const seatCount = computed(
  () => workspace.value?.layout?.elements.filter((element) => element.kind === 'SEAT').length || 0,
)
const activePublishedVersion = computed(() =>
  store.workspace?.versions.find((version) => version.id === activeVersionKey.value),
)
const protectedElementIds = computed(() => [
  ...new Set((workspace.value?.items || []).flatMap((item) => item.targetElementIds || [])),
])
const markerSelectionIds = computed(() => [...markerSelection.value])
const markerItems = computed(() => reservedItems(workspace.value?.items || []))
const showAssignmentSave = computed(() => !readonlyMode.value && workbenchMode.value === 'seating')
const workspaceBusy = computed(() => store.saving || layoutSaving.value || markerSubmitting.value)
const saveStatusText = computed(() => {
  if (readonlyMode.value) return '已发布版本'
  if (workbenchMode.value === 'layout') {
    if (layoutSaving.value) return '布局保存中'
    return layoutDirty.value ? '布局未保存' : '布局已保存'
  }
  if (workbenchMode.value === 'marker') {
    if (markerSubmitting.value) return '区域保存中'
    return '区域模式'
  }
  if (store.saving) return '排座保存中'
  return store.dirty ? '排座未保存' : '排座已保存'
})
const colorFieldOptions = computed(() =>
  (workspace.value?.fieldDefinitions || []).filter(
    (field) => !['name', 'employeeNo'].includes(field.code),
  ),
)
const activeColorField = computed(() =>
  colorFieldOptions.value.find((field) => field.code === groupColorFieldCode.value),
)
const groupColorFieldOverrides = computed(
  () => groupColorOverrides.value?.[groupColorFieldCode.value] || {},
)
const participantColorById = computed(() =>
  Object.fromEntries(
    buildParticipantColorMap(
      workspace.value?.participants || [],
      groupColorFieldCode.value,
      GROUP_COLOR_PALETTE,
      groupColorFieldOverrides.value,
    ),
  ),
)
const groupColorEntries = computed(() =>
  buildFieldColorEntries(
    workspace.value?.participants || [],
    groupColorFieldCode.value,
    GROUP_COLOR_PALETTE,
    groupColorFieldOverrides.value,
  ),
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
const layoutDirty = computed(() => {
  if (!workspace.value?.layout) return false
  return JSON.stringify(layoutDraft.value) !== JSON.stringify(cloneLayout(workspace.value.layout))
})
function cloneLayout(layout) {
  return {
    gridRows: Number(layout?.gridRows || 20),
    gridColumns: Number(layout?.gridColumns || 30),
    elements: (layout?.elements || []).map((element) => ({
      id: element.id,
      kind: element.kind,
      name: element.name,
      row: element.row,
      column: element.column,
      rowSpan: element.rowSpan,
      columnSpan: element.columnSpan,
      fillColor: element.fillColor,
      borderColor: element.borderColor,
    })),
  }
}
function resetLayoutDraft() {
  if (workspace.value?.layout) layoutDraft.value = cloneLayout(workspace.value.layout)
}
async function confirmDiscardLayoutChanges() {
  if (!layoutDirty.value) return true
  try {
    await ElMessageBox.confirm(
      '布局模式有未保存修改，切换模式后这些修改会被放弃。',
      '离开布局模式',
      {
        type: 'warning',
        confirmButtonText: '放弃修改',
        cancelButtonText: '继续编辑',
      },
    )
    resetLayoutDraft()
    return true
  } catch {
    return false
  }
}
async function changeWorkbenchMode(nextMode) {
  if (readonlyMode.value || nextMode === workbenchMode.value) return
  if (workbenchMode.value === 'layout' && !(await confirmDiscardLayoutChanges())) return
  workbenchMode.value = nextMode
}
function reservedAreaInputs(excludedId) {
  return markerItems.value
    .filter((item) => item.id !== excludedId)
    .map((item) => ({
      id: item.id,
      label: item.label,
      backgroundColor: item.backgroundColor || '#FEF3C7',
      textColor: item.textColor || '#7C2D12',
      bold: item.bold,
      targetElementIds: item.targetElementIds || [],
    }))
}
function currentMarkerInput() {
  return {
    id: markerDraft.id || undefined,
    label: markerDraft.label.trim(),
    backgroundColor: markerDraft.backgroundColor,
    textColor: markerDraft.textColor,
    bold: Boolean(markerDraft.bold),
    targetElementIds: markerSelectionIds.value,
  }
}
function normalizedMarkerLabel(value) {
  return String(value || '').trim().toLocaleLowerCase()
}
function normalizedMarkerColor(value) {
  return normalizeHexColor(value) || ''
}
function validateMarkerUniqueness(marker, excludedId) {
  const label = normalizedMarkerLabel(marker?.label)
  const color = normalizedMarkerColor(marker?.backgroundColor)
  if (
    label &&
    markerItems.value.some(
      (item) => item.id !== excludedId && normalizedMarkerLabel(item.label) === label,
    )
  ) {
    ElMessage.warning('区域名称已存在，请使用其他名称')
    return false
  }
  if (
    color &&
    markerItems.value.some(
      (item) => item.id !== excludedId && normalizedMarkerColor(item.backgroundColor) === color,
    )
  ) {
    ElMessage.warning('区域颜色已被其他区域使用，请选择其他颜色')
    return false
  }
  return true
}
function resetMarkerDraft() {
  Object.assign(markerDraft, defaultMarkerDraft)
  markerSelection.value = new Set()
  regionCreateVisible.value = false
}
function updateMarkerDraft(value) {
  Object.assign(markerDraft, value)
}
function selectedRegionSeatIdsFromRect(elementIds) {
  return (elementIds || []).filter((id) => !markerBlockingItem(id))
}
function openRegionCreateDialog(elementIds) {
  if (readonlyMode.value || workbenchMode.value !== 'marker') return
  const availableIds = selectedRegionSeatIdsFromRect(elementIds)
  if (!availableIds.length) {
    ElMessage.warning('框选范围内没有可用座位')
    return
  }
  resetMarkerDraft()
  markerSelection.value = new Set(availableIds)
  regionCreateVisible.value = true
}
async function createReservedAreaFromDialog(payload) {
  if (!validateMarkerUniqueness(payload)) return
  Object.assign(markerDraft, {
    id: '',
    label: payload.label || '',
    backgroundColor: payload.backgroundColor || defaultMarkerDraft.backgroundColor,
    textColor: payload.textColor || '#172033',
    bold: payload.bold !== false,
  })
  const saved = await saveReservedAreas()
  if (saved) regionCreateVisible.value = false
}
async function mergeReservedAreaFromDialog(payload) {
  const target = markerItems.value.find((item) => item.id === payload?.targetMarkerId)
  if (!target) {
    ElMessage.warning('请选择要合并的区域')
    return
  }
  Object.assign(markerDraft, {
    id: target.id,
    label: target.label || '',
    backgroundColor: target.backgroundColor || defaultMarkerDraft.backgroundColor,
    textColor: target.textColor || '#172033',
    bold: Boolean(target.bold),
  })
  markerSelection.value = new Set([
    ...(target.targetElementIds || []),
    ...markerSelectionIds.value,
  ])
  const saved = await saveReservedAreas()
  if (saved) regionCreateVisible.value = false
}
function selectReservedMarker(item) {
  if (readonlyMode.value || workbenchMode.value !== 'marker' || !item || item.type !== 'RESERVED') return
  if (markerDraft.id === item.id) {
    resetMarkerDraft()
    return
  }
  Object.assign(markerDraft, {
    id: item.id,
    label: item.label || '',
    backgroundColor: item.backgroundColor || '#FEF3C7',
    textColor: item.textColor || '#7C2D12',
    bold: Boolean(item.bold),
  })
  markerSelection.value = new Set(item.targetElementIds || [])
}
function markerBlockingItem(elementId) {
  const item = (workspace.value?.items || []).find((value) =>
    (value.targetElementIds || []).includes(elementId),
  )
  if (!item) return undefined
  if (item.type === 'RESERVED' && item.id === markerDraft.id) return undefined
  return item
}
async function toggleMarkerSeat(element) {
  if (readonlyMode.value || workbenchMode.value !== 'marker' || !element?.id) return
  const blockingItem = markerBlockingItem(element.id)
  if (blockingItem) {
    if (blockingItem.type === 'RESERVED') {
      selectReservedMarker(blockingItem)
      ElMessage.info(`已选中“${blockingItem.label || '区域'}”，再次双击可移除该座位`)
      return
    }
    ElMessage.warning('该座位已有人员或其他占用')
    return
  }
  const removing = markerSelection.value.has(element.id)
  if (!removing && !markerDraft.label.trim()) {
    ElMessage.warning('请先框选座位创建区域')
    return
  }
  markerSelection.value = toggleSeatSelection(markerSelection.value, element.id)
  if (removing && markerDraft.id && markerSelection.value.size === 0) {
    await deleteReservedMarker(true)
  }
}
async function saveReservedAreas() {
  if (!store.workspace || readonlyMode.value) return false
  if (markerDraft.id && !markerSelection.value.size) {
    return deleteReservedMarker(true)
  }
  if (!markerDraft.label.trim()) {
    ElMessage.warning('请填写区域名称')
    return false
  }
  if (!markerSelection.value.size) {
    ElMessage.warning('请至少选择一个座位')
    return false
  }
  if (!validateMarkerUniqueness(markerDraft, markerDraft.id)) return false
  if (!(await saveDraft(true))) return false
  markerSubmitting.value = true
  try {
    await meetingApi.saveReservedAreas(store.workspace.plan.id, {
      reservedAreas: [
        ...reservedAreaInputs(markerDraft.id),
        currentMarkerInput(),
      ],
    })
    await store.loadWorkspace()
    resetMarkerDraft()
    undoStack.value = []
    redoStack.value = []
    ElMessage.success('区域已保存')
    return true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
    return false
  } finally {
    markerSubmitting.value = false
  }
}
async function deleteReservedMarker(skipConfirm = false) {
  if (!store.workspace || readonlyMode.value) return false
  if (!markerDraft.id) {
    resetMarkerDraft()
    return false
  }
  if (!skipConfirm) {
    try {
      await ElMessageBox.confirm(`确认删除“${markerDraft.label}”区域吗？`, '删除区域', {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      })
    } catch {
      return false
    }
  }
  if (!(await saveDraft(true))) return false
  markerSubmitting.value = true
  try {
    await meetingApi.saveReservedAreas(store.workspace.plan.id, {
      reservedAreas: reservedAreaInputs(markerDraft.id),
    })
    await store.loadWorkspace()
    resetMarkerDraft()
    undoStack.value = []
    redoStack.value = []
    ElMessage.success(skipConfirm ? '区域座位已清空，区域已删除' : '区域已删除')
    return true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
    return false
  } finally {
    markerSubmitting.value = false
  }
}
function routeVersionKey() {
  return typeof route.query.version === 'string' ? route.query.version : ''
}
function workbenchRoute(meetingId, versionKey = 'draft') {
  return {
    path: `/workbench/${meetingId}`,
    query: versionKey && versionKey !== 'draft' ? { version: versionKey } : {},
  }
}
onMounted(async () => {
  const meetingId = typeof route.params.meetingId === 'string' ? route.params.meetingId : ''
  const requestedVersionKey = routeVersionKey()
  if (meetingId) store.rememberMeeting(meetingId, requestedVersionKey || 'draft')
  await store.initialize()
  if (store.activeMeetingId && store.activeMeetingId !== meetingId) {
    await router.replace(workbenchRoute(store.activeMeetingId, store.recentVersionKey))
  }
  const initialVersionKey = requestedVersionKey || store.recentVersionKey || 'draft'
  if (
    initialVersionKey !== 'draft' &&
    store.workspace?.versions.some((version) => version.id === initialVersionKey)
  ) {
    await switchVersion(initialVersionKey)
  } else if (store.activeMeetingId) {
    activeVersionKey.value = 'draft'
    publishedWorkspace.value = undefined
    store.rememberMeeting(store.activeMeetingId, 'draft')
    await router.replace(workbenchRoute(store.activeMeetingId, 'draft'))
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
  workbenchMode.value = 'seating'
  publishedWorkspace.value = undefined
  resetMarkerDraft()
  undoStack.value = []
  redoStack.value = []
  await store.switchMeeting(meetingId)
  await router.replace(workbenchRoute(meetingId, 'draft'))
}
async function switchVersion(versionKey) {
  if (versionKey !== 'draft' && !(await saveDraft(true))) return
  const meetingId = store.workspace?.meeting?.id || store.activeMeetingId
  activeVersionKey.value = versionKey
  if (versionKey !== 'draft') workbenchMode.value = 'seating'
  store.selectParticipant(undefined)
  draggingParticipantId.value = undefined
  if (versionKey === 'draft') {
    publishedWorkspace.value = undefined
    if (meetingId) {
      store.rememberMeeting(meetingId, 'draft')
      await router.replace(workbenchRoute(meetingId, 'draft'))
    }
    return
  }
  if (!store.workspace) return
  loadingVersion.value = true
  try {
    publishedWorkspace.value = await meetingApi.versionSnapshot(store.workspace.plan.id, versionKey)
    if (meetingId) {
      store.rememberMeeting(meetingId, versionKey)
      await router.replace(workbenchRoute(meetingId, versionKey))
    }
  } catch (error) {
    activeVersionKey.value = 'draft'
    publishedWorkspace.value = undefined
    if (meetingId) {
      store.rememberMeeting(meetingId, 'draft')
      await router.replace(workbenchRoute(meetingId, 'draft'))
    }
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
    store.rememberMeeting(store.workspace.meeting.id, 'draft')
    await router.replace(workbenchRoute(store.workspace.meeting.id, 'draft'))
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
  if (store.workspace?.meeting?.id) {
    store.rememberMeeting(store.workspace.meeting.id, activeVersionKey.value)
  }
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
  if (!store.dirty && !layoutDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}
watch(autoSaveSeconds, resetAutoSaveTimer)
watch(
  () => workspace.value?.layout,
  (layout) => {
    if (layout) layoutDraft.value = cloneLayout(layout)
  },
  { immediate: true, deep: true },
)
watch(readonlyMode, (readonly) => {
  if (readonly) workbenchMode.value = 'seating'
})
watch(colorFieldOptions, (options) => {
  if (
    groupColorFieldCode.value &&
    !options.some((field) => field.code === groupColorFieldCode.value)
  ) {
    groupColorFieldCode.value = ''
  }
})
watch(groupColorFieldCode, () => {
  groupColorLegendCollapsed.value = false
})
watch(workbenchMode, (mode) => {
  if (mode !== 'marker') resetMarkerDraft()
  if (mode !== 'seating') {
    addMenuVisible.value = false
    draggingParticipantId.value = undefined
    store.selectParticipant(undefined)
  }
})
async function performAssign(participantId, targetElementId) {
  if (readonlyMode.value || workbenchMode.value !== 'seating') return
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
  if (readonlyMode.value || workbenchMode.value !== 'seating') return
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
  if (readonlyMode.value || workbenchMode.value !== 'seating' || !element?.id) return
  const occupied = workspace.value?.participants.find(
    (person) => person.assignedElementId === element.id,
  )
  if (occupied) {
    await openParticipantEdit(occupied)
    return
  }
  if (!(await saveDraft(true))) return
  addTargetElementId.value = element.id
  addMenuVisible.value = false
  addVisible.value = true
}
function setGroupColorOverride(payload) {
  if (!groupColorFieldCode.value || !payload?.value || !payload?.color) return
  saveGroupColorOverride(groupColorFieldCode.value, payload.value, payload.color)
  groupColorOverrides.value = readGroupColorOverrides()
}
function selectParticipant(person) {
  store.selectParticipant(store.selectedParticipantId === person?.id ? undefined : person)
}
function clearCanvasSelection() {
  store.selectParticipant(undefined)
  resetMarkerDraft()
}
async function openParticipantEdit(person) {
  if (readonlyMode.value || workbenchMode.value !== 'seating' || !person) return
  if (!(await saveDraft(true))) return
  editingParticipant.value = person
  editParticipantVisible.value = true
}
async function updateParticipantAttendance(person, attendanceStatus) {
  if (readonlyMode.value || workbenchMode.value !== 'seating') return
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
  if (readonlyMode.value || workbenchMode.value !== 'seating') return
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
function openExportOptions() {
  exportOptionsVisible.value = true
}
async function exportPlan(options) {
  if (!readonlyMode.value && !(await saveDraft(true))) return
  exportOptionsVisible.value = false
  await store.exportPlan(activeVersionId.value, options)
}
async function confirmDeleteProtectedElement(element) {
  try {
    await ElMessageBox.confirm(
      `“${element.name}”已有人员或区域占用，删除后相关座位会回到待排或被清空。`,
      '确认删除布局元素',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )
    return true
  } catch {
    return false
  }
}
async function saveMeetingLayout() {
  if (!store.workspace || readonlyMode.value || layoutSaving.value) return
  const layoutSnapshot = cloneLayout(layoutDraft.value)
  layoutSaving.value = true
  try {
    if (!(await saveDraft(true))) return
    const updated = await meetingApi.updateMeetingLayout(store.workspace.meeting.id, {
      gridRows: layoutSnapshot.gridRows,
      gridColumns: layoutSnapshot.gridColumns,
      elements: layoutSnapshot.elements.map((element) => ({
        id: element.id,
        ...toElementPayload(element),
      })),
    })
    store.replaceWorkspace(updated)
    undoStack.value = []
    redoStack.value = []
    layoutDraft.value = cloneLayout(updated.layout)
    ElMessage.success('会议布局已保存')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    layoutSaving.value = false
  }
}
function resetFab() {
  fab.x = 24
  fab.y = Math.max(76, window.innerHeight - 96)
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
async function onParticipantUpdated(participant) {
  const participantId = participant?.id || editingParticipant.value?.id
  await store.loadWorkspace()
  const updated = store.workspace?.participants.find((person) => person.id === participantId)
  if (updated) store.selectParticipant(updated)
}
</script>

<template>
  <div class="app-page workbench-page" v-loading="store.loading || loadingVersion">
    <header class="app-header">
      <button class="home-brand" title="返回首页" @click="goHome">
        <span class="brand-slot" aria-hidden="true" />
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
        <i :class="{ active: store.saving || layoutSaving || markerSubmitting, dirty: store.dirty || layoutDirty }" />
        {{ saveStatusText }}
      </span>
      <div v-if="showAssignmentSave" class="header-save-control">
        <el-button type="primary" @click="saveDraft(false)">保存排座</el-button>
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
      v-loading="workspaceBusy"
      class="workspace-shell"
      :class="{
        'participant-collapsed': participantPanelCollapsed && workbenchMode === 'seating',
      }"
    >
      <div
        v-if="workbenchMode === 'layout'"
        id="workbench-layout-side"
        class="participant-side layout-editor-host"
      />

      <section class="canvas-shell">
        <div class="toolbar-card">
          <div class="canvas-title">
            <h1>{{ workspace.meeting.layoutName }}</h1>
          </div>
          <div class="canvas-stats">
            <span
              ><b>{{ seatCount }}</b
              ><small>座位数</small></span
            >
            <span
              ><b>{{ workspace.participants.length }}</b
              ><small>总人数</small></span
            >
            <span
              ><b>{{ pendingCount }}</b
              ><small>待排</small></span
            >
          </div>
          <el-tag v-if="readonlyMode" type="primary" effect="plain">
            {{ activePublishedVersion?.versionName }}
          </el-tag>
          <el-radio-group
            :model-value="workbenchMode"
            class="workbench-mode-switch"
            :disabled="readonlyMode"
            size="small"
            @change="changeWorkbenchMode"
          >
            <el-radio-button label="排座模式" value="seating" />
            <el-radio-button label="布局模式" value="layout" />
            <el-radio-button label="区域模式" value="marker" />
          </el-radio-group>
          <el-select
            v-if="colorFieldOptions.length"
            v-model="groupColorFieldCode"
            class="color-field-select"
            clearable
            size="small"
            aria-label="按字段着色"
          >
            <el-option
              v-for="field in colorFieldOptions"
              :key="field.code"
              :label="field.label"
              :value="field.code"
            />
          </el-select>
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
          <el-button-group v-if="workbenchMode !== 'layout'" class="canvas-zoom-controls">
            <el-button :icon="ZoomOut" :disabled="zoom <= 0.4" @click="changeZoom(-0.1)" />
            <el-button class="zoom-value">{{ Math.round(zoom * 100) }}%</el-button>
            <el-button :icon="ZoomIn" :disabled="zoom >= 2.5" @click="changeZoom(0.1)" />
          </el-button-group>
          <el-button
            type="primary"
            plain
            :icon="Download"
            @click="openExportOptions"
          >
            导出Excel
          </el-button>
        </div>

        <div class="canvas-body">
          <VenueLayoutEditor
            v-if="workbenchMode === 'layout'"
            v-model="layoutDraft"
            title="编辑布局"
            save-label="保存布局"
            :show-back="false"
            :saving="layoutSaving"
            toolbar-placement="side"
            side-panel-target="#workbench-layout-side"
            :manual-capacity="workspace.participants.length"
            :venue-name="workspace.meeting.layoutName"
            :protected-element-ids="protectedElementIds"
            :delete-confirm-message="confirmDeleteProtectedElement"
            @save="saveMeetingLayout"
          />
          <VenueCanvas
            v-else
            :workspace="workspace"
            :zoom="zoom"
            :readonly="readonlyMode"
            :marker-mode="workbenchMode === 'marker'"
            :marker-rect-enabled="workbenchMode === 'marker' && !markerDraft.id"
            :active-marker-id="markerDraft.id"
            :marker-selection-ids="workbenchMode === 'marker' ? markerSelectionIds : []"
            :participant-color-by-id="participantColorById"
            :selected-participant-id="store.selectedParticipantId"
            :dragging-participant-id="draggingParticipantId"
            @assign="performAssign"
            @unassign="performUnassign"
            @select="selectParticipant"
            @seat-click="onSeatClick"
            @drag-state="draggingParticipantId = $event"
            @marker-seat-toggle="toggleMarkerSeat"
            @marker-select="selectReservedMarker"
            @marker-rect-select="openRegionCreateDialog"
            @canvas-clear="clearCanvasSelection"
            @zoom-change="changeZoom($event)"
          />
          <GroupColorLegend
            v-if="groupColorFieldCode && groupColorEntries.length"
            v-model:collapsed="groupColorLegendCollapsed"
            :field-label="activeColorField?.label || groupColorFieldCode"
            :entries="groupColorEntries"
            @set-color="setGroupColorOverride"
          />
        </div>

      </section>

      <div v-if="workbenchMode !== 'layout'" class="participant-side">
        <button
          v-if="workbenchMode === 'seating'"
          class="participant-panel-toggle"
          :title="participantPanelCollapsed ? '展开人员安排' : '收起人员安排'"
          :aria-label="participantPanelCollapsed ? '展开人员安排' : '收起人员安排'"
          @click="participantPanelCollapsed = !participantPanelCollapsed"
        >
          {{ participantPanelCollapsed ? '‹' : '›' }}
        </button>
        <RegionMarkerPanel
          v-if="workbenchMode === 'marker'"
          :model-value="markerDraft"
          :markers="markerItems"
          :active-marker-id="markerDraft.id"
          :selected-seat-count="markerSelection.size"
          :submitting="markerSubmitting"
          @update:model-value="updateMarkerDraft"
          @select="selectReservedMarker"
          @new="resetMarkerDraft"
          @save="saveReservedAreas"
          @delete="deleteReservedMarker"
          @cancel="resetMarkerDraft"
        />
        <ParticipantPanel
          v-else
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
          @edit="openParticipantEdit"
          @drag-state="draggingParticipantId = $event"
        />
      </div>
    </main>

    <template v-if="workspace && !readonlyMode && fabReady">
      <div
        v-if="addMenuVisible && workbenchMode === 'seating'"
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
        v-if="workbenchMode === 'seating'"
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
      :field-definitions="store.workspace.fieldDefinitions"
      @done="onParticipantAdded"
    />
    <ImportDialog
      v-if="store.workspace && !readonlyMode"
      v-model="importVisible"
      :meeting-id="store.workspace.meeting.id"
      @done="store.loadWorkspace"
    />
    <EditParticipantDialog
      v-if="store.workspace && !readonlyMode && editingParticipant"
      v-model="editParticipantVisible"
      :meeting-id="store.workspace.meeting.id"
      :participant="editingParticipant"
      :field-definitions="store.workspace.fieldDefinitions"
      @done="onParticipantUpdated"
    />
    <PublishVersionDialog
      v-if="store.workspace && !readonlyMode"
      v-model="publishVisible"
      :versions="store.workspace.versions"
      :submitting="publishing"
      @publish="confirmPublish"
    />
    <ExportOptionsDialog
      v-if="workspace"
      v-model="exportOptionsVisible"
      :field-definitions="workspace.fieldDefinitions"
      :submitting="store.saving"
      @export="exportPlan"
    />
    <RegionCreateDialog
      v-if="store.workspace && !readonlyMode"
      v-model="regionCreateVisible"
      :selected-seat-count="markerSelection.size"
      :markers="markerItems"
      :submitting="markerSubmitting"
      @submit="createReservedAreaFromDialog"
      @merge="mergeReservedAreaFromDialog"
      @cancel="resetMarkerDraft"
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
  width: 148px;
  min-width: 148px;
  min-height: 34px;
  display: flex;
  align-items: center;
  padding: 0;
  color: var(--ink);
  background: transparent;
  border: 0;
  cursor: pointer;
  text-align: left;
}

.brand-slot {
  width: 100%;
  height: 34px;
  display: block;
  border-radius: 8px;
}

.meeting-selector {
  width: 300px;
}

.version-selector {
  width: 190px;
}

.header-selector :deep(.el-select__wrapper) {
  color: var(--ink);
  background: #f8f9fb;
  box-shadow: 0 0 0 1px var(--line) inset;
}

.header-selector :deep(.el-select__selected-item),
.header-selector :deep(.el-select__placeholder),
.header-selector :deep(.el-select__caret) {
  color: var(--ink) !important;
}

.save-state {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--muted);
  font-size: 11px;
  white-space: nowrap;
}

.header-home {
  color: var(--muted) !important;
}

.header-home:hover {
  color: var(--brand) !important;
  background: var(--brand-soft) !important;
}

.header-save-control {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.app-header :deep(.el-button--primary) {
  min-height: 34px;
}

.header-auto-save {
  width: 142px;
}

.header-auto-save :deep(.el-select__wrapper) {
  min-height: 32px;
  color: var(--ink);
  background: #f8f9fb;
  box-shadow: 0 0 0 1px var(--line) inset;
}

.header-auto-save :deep(.el-select__selected-item),
.header-auto-save :deep(.el-select__caret) {
  color: var(--ink) !important;
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

.workspace-shell {
  flex: 1;
  min-height: 0;
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  gap: 14px;
  padding: 14px;
  background: var(--workspace);
  overflow: hidden;
  transition: grid-template-columns 0.18s ease;
}

.workspace-shell.participant-collapsed {
  grid-template-columns: minmax(0, 1fr) 36px;
}

.participant-side {
  grid-column: 2;
  min-width: 0;
  min-height: 0;
  position: relative;
  background: transparent;
}

.layout-editor-host {
  display: flex;
  flex-direction: column;
}

.participant-panel-toggle {
  width: 27px;
  height: 52px;
  position: absolute;
  top: calc(50% - 26px);
  left: -13px;
  z-index: 25;
  padding: 0;
  color: var(--brand);
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 14px 0 0 14px;
  box-shadow: -5px 4px 12px rgba(37, 85, 151, 0.12);
  cursor: pointer;
  font-size: 23px;
  line-height: 1;
}

.participant-panel-toggle:hover {
  color: var(--brand-hover);
  background: var(--brand-soft);
}

.participant-collapsed .participant-panel-toggle {
  left: 4px;
  border-radius: 14px;
}

.canvas-shell {
  grid-column: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow);
}

.toolbar-card {
  min-height: 68px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid var(--line);
}

.canvas-title {
  width: 150px;
  min-width: 150px;
}

.canvas-title h1 {
  margin: 2px 0 0;
  color: var(--ink);
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
  color: var(--ink);
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

.workbench-mode-switch {
  flex: none;
}

.workbench-mode-switch :deep(.el-radio-button__inner) {
  min-width: 76px;
}

.color-field-select {
  width: 132px;
}

.color-field-select :deep(.el-select__wrapper) {
  min-height: 28px;
  color: var(--ink);
  background: #f8f9fb;
  box-shadow: 0 0 0 1px var(--line) inset;
}

.toolbar-card > .el-button-group {
  flex: none;
}

.toolbar-card > .el-button-group {
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
  position: relative;
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
  background: var(--brand);
  border: 0;
  border-radius: 50%;
  box-shadow: 0 4px 14px rgba(10, 89, 247, 0.22);
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
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-hover);
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
  color: var(--brand);
  background: var(--brand-soft);
}

@media (max-width: 1450px) {
  .workbench-page > .app-header {
    gap: 10px;
    padding-inline: 16px;
  }

    .home-brand {
      width: 132px;
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
  color: var(--brand);
  background: var(--brand-soft);
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
