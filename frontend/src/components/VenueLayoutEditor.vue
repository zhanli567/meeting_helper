<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'
import {
  Aim,
  ArrowLeft,
  Check,
  Minus,
  Plus,
  RefreshLeft,
  RefreshRight,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import VenueElementPanel from '@/components/VenueElementPanel.vue'
import VenueElementPicker from '@/components/VenueElementPicker.vue'
import {
  activeSelectionRect,
  appendHistorySnapshot,
  canvasAnchorAdjustment,
  canvasResizeConflict,
  canvasSizeFromPointer,
  canPlaceRect,
  createSeatElements,
  moveRect,
  normalizeGridRect,
  placePanelBesideRect,
  pointerDeltaToGrid,
  rectsOverlap,
  resizeRect,
  shouldDismissDesignerOverlays,
} from '@/utils/designerGeometry'
import { elementBox } from '@/utils/venueCanvasMetrics'
import {
  DEFAULT_CANVAS,
  ELEMENT_KINDS,
  MIN_CANVAS_SIZE,
} from '@/utils/venueModel'

const props = defineProps({
  modelValue: {
    type: Object,
    required: true,
  },
  venueName: {
    type: String,
    default: '场馆模板',
  },
  venueDescription: {
    type: String,
    default: '',
  },
  title: {
    type: String,
    default: '编辑场馆布局',
  },
  saveLabel: {
    type: String,
    default: '保存布局',
  },
  saving: {
    type: Boolean,
    default: false,
  },
  manualCapacity: {
    type: Number,
    default: null,
  },
  showBack: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['update:modelValue', 'change', 'save', 'back'])
const CELL_SIZE = 44
const resizeHandles = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w']
const gridRows = ref(DEFAULT_CANVAS.rows)
const gridColumns = ref(DEFAULT_CANVAS.columns)
const elements = ref([])
const zoom = ref(1)
const undoStack = ref([])
const redoStack = ref([])
const selectedId = ref()
const editorPreview = ref()
const drawing = ref()
const pendingRect = ref()
const pickerVisible = ref(false)
const pickerPosition = ref({ left: 80, top: 80 })
const pickerDocked = ref(false)
const manipulation = ref()
const canvasResizeSession = ref()
const conflictElementIds = ref([])
const pickerConflictElementIds = ref([])
const panelCollapsed = ref(false)
const panelDock = ref('right')
const canvasOffsetX = ref(0)
const canvasOffsetY = ref(0)
const isPanning = ref(false)
const canvasSurfaceRef = ref()
const viewportRef = ref()
const canvasRef = ref()
const pickerRef = ref()
const panelRef = ref()
let globalPointerTracking = false
let panSession
let activePointerId
let pointerCaptureTarget

function nextElementId() {
  return (
    globalThis.crypto?.randomUUID?.() ||
    `venue-element-${Date.now()}-${Math.random().toString(16).slice(2)}`
  )
}

function asEditorElement(element) {
  const id = element.id || element.editorId || nextElementId()
  return {
    id,
    editorId: id,
    kind: element.kind || ELEMENT_KINDS.GENERIC,
    name: String(element.name || '未命名元素'),
    row: Number(element.row),
    column: Number(element.column),
    rowSpan: Number(element.rowSpan),
    columnSpan: Number(element.columnSpan),
    fillColor: element.fillColor || '#dbeafe',
    borderColor: element.borderColor || '#93c5fd',
  }
}

function plainEditorElement(element) {
  return {
    id: element.id,
    editorId: element.editorId,
    kind: element.kind,
    name: element.name,
    row: Number(element.row),
    column: Number(element.column),
    rowSpan: Number(element.rowSpan),
    columnSpan: Number(element.columnSpan),
    fillColor: element.fillColor,
    borderColor: element.borderColor,
  }
}

function currentSnapshot() {
  return {
    gridRows: gridRows.value,
    gridColumns: gridColumns.value,
    elements: elements.value.map(plainEditorElement),
  }
}

function comparableLayout(layout) {
  return JSON.stringify({
    gridRows: Number(layout?.gridRows || DEFAULT_CANVAS.rows),
    gridColumns: Number(layout?.gridColumns || DEFAULT_CANVAS.columns),
    elements: (layout?.elements || []).map((element) => ({
      kind: element.kind,
      name: element.name,
      row: Number(element.row),
      column: Number(element.column),
      rowSpan: Number(element.rowSpan),
      columnSpan: Number(element.columnSpan),
      fillColor: element.fillColor,
      borderColor: element.borderColor,
    })),
  })
}

function restoreSnapshot(snapshot) {
  gridRows.value = Number(snapshot.gridRows)
  gridColumns.value = Number(snapshot.gridColumns)
  elements.value = snapshot.elements.map(asEditorElement)
}

function publishLayout() {
  const layout = currentSnapshot()
  emit('update:modelValue', layout)
  emit('change', layout)
}

function recordHistory(snapshot = currentSnapshot()) {
  undoStack.value = appendHistorySnapshot(undoStack.value, snapshot)
  redoStack.value = []
}

function closePicker() {
  pickerVisible.value = false
  pickerDocked.value = false
  pendingRect.value = undefined
  pickerConflictElementIds.value = []
}

function closeElementPanel() {
  editorPreview.value = undefined
  selectedId.value = undefined
}

function closeOverlays() {
  closePicker()
  closeElementPanel()
}

function undo() {
  const snapshot = undoStack.value.at(-1)
  if (!snapshot) return
  redoStack.value = appendHistorySnapshot(redoStack.value, currentSnapshot())
  undoStack.value = undoStack.value.slice(0, -1)
  restoreSnapshot(snapshot)
  closeOverlays()
  publishLayout()
}

function redo() {
  const snapshot = redoStack.value.at(-1)
  if (!snapshot) return
  undoStack.value = appendHistorySnapshot(undoStack.value, currentSnapshot())
  redoStack.value = redoStack.value.slice(0, -1)
  restoreSnapshot(snapshot)
  closeOverlays()
  publishLayout()
}

watch(
  () => props.modelValue,
  (layout) => {
    if (!layout || comparableLayout(layout) === comparableLayout(currentSnapshot())) return
    restoreSnapshot({
      gridRows: layout.gridRows || DEFAULT_CANVAS.rows,
      gridColumns: layout.gridColumns || DEFAULT_CANVAS.columns,
      elements: layout.elements || [],
    })
    undoStack.value = []
    redoStack.value = []
    nextTick(fitCanvas)
  },
  { immediate: true, deep: true },
)

const selectedElement = computed(() =>
  elements.value.find((element) => element.editorId === selectedId.value),
)
const drawingRect = computed(() =>
  drawing.value ? normalizeGridRect(drawing.value.start, drawing.value.current) : undefined,
)
const selectionRect = computed(() =>
  activeSelectionRect(drawingRect.value, pendingRect.value),
)
const displayRows = computed(() =>
  canvasResizeSession.value?.valid
    ? canvasResizeSession.value.candidate.rows
    : gridRows.value,
)
const displayColumns = computed(() =>
  canvasResizeSession.value?.valid
    ? canvasResizeSession.value.candidate.columns
    : gridColumns.value,
)
const seatCount = computed(
  () =>
    elements.value.filter(
      (element) => renderedElement(element).kind === ELEMENT_KINDS.SEAT,
    ).length,
)
const capacityMismatch = computed(
  () => props.manualCapacity !== null && seatCount.value !== props.manualCapacity,
)
const stageStyle = computed(() => ({
  width: `${displayColumns.value * CELL_SIZE * zoom.value}px`,
  height: `${displayRows.value * CELL_SIZE * zoom.value}px`,
  transform: `translate(${canvasOffsetX.value}px, ${canvasOffsetY.value}px)`,
}))
const canvasStyle = computed(() => ({
  width: `${displayColumns.value * CELL_SIZE}px`,
  height: `${displayRows.value * CELL_SIZE}px`,
  '--editor-cell': `${CELL_SIZE}px`,
  transform: `scale(${zoom.value})`,
}))
const selectionStyle = computed(() => {
  if (!selectionRect.value) return {}
  return {
    left: `${(selectionRect.value.column - 1) * CELL_SIZE}px`,
    top: `${(selectionRect.value.row - 1) * CELL_SIZE}px`,
    width: `${selectionRect.value.columnSpan * CELL_SIZE}px`,
    height: `${selectionRect.value.rowSpan * CELL_SIZE}px`,
  }
})
const pickerStyle = computed(() => ({
  left: `${pickerPosition.value.left}px`,
  top: `${pickerPosition.value.top}px`,
}))

function renderedElement(element) {
  let rendered = element
  if (editorPreview.value?.editorId === element.editorId) {
    rendered = { ...rendered, ...editorPreview.value }
  }
  if (manipulation.value?.editorId === element.editorId) {
    const candidate = manipulation.value.candidate
    rendered = {
      ...rendered,
      row: candidate.row,
      column: candidate.column,
      rowSpan: candidate.rowSpan,
      columnSpan: candidate.columnSpan,
    }
  }
  return rendered
}

function elementStyle(element) {
  const rendered = renderedElement(element)
  const box = elementBox(rendered, CELL_SIZE)
  return {
    left: `${box.left}px`,
    top: `${box.top}px`,
    width: `${box.width}px`,
    height: `${box.height}px`,
    backgroundColor: rendered.fillColor,
    borderColor: rendered.borderColor,
  }
}

function isElementConflict(element) {
  return (
    conflictElementIds.value.includes(element.editorId) ||
    pickerConflictElementIds.value.includes(element.editorId) ||
    (manipulation.value?.editorId === element.editorId && !manipulation.value.valid)
  )
}

function gridPointFromEvent(event) {
  const rect = canvasRef.value?.getBoundingClientRect()
  if (!rect) return
  return {
    row: Math.min(
      displayRows.value,
      Math.max(1, Math.floor((event.clientY - rect.top) / (CELL_SIZE * zoom.value)) + 1),
    ),
    column: Math.min(
      displayColumns.value,
      Math.max(1, Math.floor((event.clientX - rect.left) / (CELL_SIZE * zoom.value)) + 1),
    ),
  }
}

function attachGlobalPointerTracking() {
  if (globalPointerTracking) return
  globalPointerTracking = true
  window.addEventListener('pointermove', onGlobalPointerMove)
  window.addEventListener('pointerup', onGlobalPointerUp)
  window.addEventListener('pointercancel', cancelPointerSession)
}

function detachGlobalPointerTracking() {
  if (!globalPointerTracking) return
  globalPointerTracking = false
  window.removeEventListener('pointermove', onGlobalPointerMove)
  window.removeEventListener('pointerup', onGlobalPointerUp)
  window.removeEventListener('pointercancel', cancelPointerSession)
  if (
    pointerCaptureTarget?.hasPointerCapture?.(activePointerId)
  ) {
    pointerCaptureTarget.releasePointerCapture(activePointerId)
  }
  activePointerId = undefined
  pointerCaptureTarget = undefined
}

function beginPointerSession(event) {
  if (activePointerId !== undefined) return false
  activePointerId = event.pointerId
  pointerCaptureTarget = event.currentTarget
  try {
    pointerCaptureTarget?.setPointerCapture?.(activePointerId)
  } catch {
    pointerCaptureTarget = undefined
  }
  attachGlobalPointerTracking()
  return true
}

function startSelection(event) {
  if (event.button !== 0 || event.target !== event.currentTarget) return
  if (
    shouldDismissDesignerOverlays({
      hasOverlay: pickerVisible.value || Boolean(selectedId.value),
      insideOverlay: false,
    })
  ) {
    closeOverlays()
    return
  }
  const point = gridPointFromEvent(event)
  if (!point) return
  if (!beginPointerSession(event)) return
  conflictElementIds.value = []
  drawing.value = { start: point, current: point }
  pendingRect.value = undefined
  event.preventDefault()
}

function positionPicker() {
  if (!pendingRect.value || !canvasRef.value || !canvasSurfaceRef.value) return
  const canvasBounds = canvasRef.value.getBoundingClientRect()
  const surfaceBounds = canvasSurfaceRef.value.getBoundingClientRect()
  const pickerBounds = pickerNode()?.getBoundingClientRect()
  const rect = pendingRect.value
  const selectionBounds = {
    left:
      canvasBounds.left -
      surfaceBounds.left +
      (rect.column - 1) * CELL_SIZE * zoom.value,
    top:
      canvasBounds.top -
      surfaceBounds.top +
      (rect.row - 1) * CELL_SIZE * zoom.value,
    width: rect.columnSpan * CELL_SIZE * zoom.value,
    height: rect.rowSpan * CELL_SIZE * zoom.value,
  }
  const placement = placePanelBesideRect(
    selectionBounds,
    { width: surfaceBounds.width, height: surfaceBounds.height },
    {
      width: pickerBounds?.width || 390,
      height: pickerBounds?.height || 430,
    },
  )
  pickerDocked.value = placement.dock === 'right'
  if (!pickerDocked.value) pickerPosition.value = placement
}

function conflictingIds(candidate, ignoredId) {
  return elements.value
    .filter(
      (element) =>
        element.editorId !== ignoredId && rectsOverlap(element, candidate),
    )
    .map((element) => element.editorId)
}

function chooseElement(choice) {
  if (!pendingRect.value) return
  let created
  if (choice.kind === ELEMENT_KINDS.SEAT) {
    created = createSeatElements(pendingRect.value, choice.mode || 'merge', choice)
  } else {
    created = [{ ...choice, ...pendingRect.value }]
  }
  const candidates = created.map(asEditorElement)
  const occupied = candidates.some((candidate, index) => {
    const earlier = candidates.slice(0, index)
    return (
      !canPlaceRect(elements.value, candidate) ||
      !canPlaceRect(earlier, candidate)
    )
  })
  if (occupied) {
    pickerConflictElementIds.value = [
      ...new Set(
        candidates.flatMap((candidate) => conflictingIds(candidate)),
      ),
    ]
    ElMessage.warning('所选区域已有元素，请重新选择空白区域')
    return
  }
  recordHistory()
  elements.value.push(...candidates)
  closePicker()
  publishLayout()
  if (candidates.length === 1) {
    selectedId.value = candidates[0].editorId
  }
}

function openElementPanel(element) {
  closePicker()
  if (selectedId.value !== element.editorId) editorPreview.value = undefined
  selectedId.value = element.editorId
}

function startElementMove(event, element) {
  if (event.button !== 0) return
  if (!beginPointerSession(event)) return
  openElementPanel(element)
  conflictElementIds.value = []
  manipulation.value = {
    mode: 'move',
    editorId: element.editorId,
    startX: event.clientX,
    startY: event.clientY,
    origin: plainEditorElement(element),
    candidate: plainEditorElement(element),
    valid: true,
  }
  event.preventDefault()
}

function startElementResize(event, element, handle) {
  if (event.button !== 0) return
  if (!beginPointerSession(event)) return
  openElementPanel(element)
  conflictElementIds.value = []
  manipulation.value = {
    mode: 'resize',
    handle,
    editorId: element.editorId,
    startX: event.clientX,
    startY: event.clientY,
    origin: plainEditorElement(element),
    candidate: plainEditorElement(element),
    valid: true,
  }
  event.preventDefault()
}

function updateManipulation(event) {
  const session = manipulation.value
  if (!session) return
  const delta = pointerDeltaToGrid(
    event.clientX - session.startX,
    event.clientY - session.startY,
    CELL_SIZE,
    zoom.value,
  )
  const bounds = { rows: gridRows.value, columns: gridColumns.value }
  const geometry =
    session.mode === 'move'
      ? moveRect(session.origin, delta.rows, delta.columns, bounds)
      : resizeRect(session.origin, session.handle, delta.rows, delta.columns, bounds)
  const candidate = { ...session.origin, ...geometry }
  const valid = canPlaceRect(elements.value, candidate, session.origin.id)
  session.candidate = candidate
  session.valid = valid
  conflictElementIds.value = valid
    ? []
    : conflictingIds(candidate, session.editorId)
}

function finishManipulation() {
  const session = manipulation.value
  if (!session) return
  if (session.valid) {
    const changed = ['row', 'column', 'rowSpan', 'columnSpan'].some(
      (key) => session.origin[key] !== session.candidate[key],
    )
    if (changed) {
      recordHistory()
      const index = elements.value.findIndex(
        (element) => element.editorId === session.editorId,
      )
      if (index >= 0) {
        Object.assign(elements.value[index], {
          row: session.candidate.row,
          column: session.candidate.column,
          rowSpan: session.candidate.rowSpan,
          columnSpan: session.candidate.columnSpan,
        })
      }
      publishLayout()
    }
  }
  manipulation.value = undefined
  conflictElementIds.value = []
}

function deleteElement(element) {
  recordHistory()
  elements.value = elements.value.filter(
    (item) => item.editorId !== element.editorId,
  )
  closeElementPanel()
  publishLayout()
}

function previewElement(changes) {
  if (!selectedElement.value) return
  editorPreview.value = {
    editorId: selectedElement.value.editorId,
    ...changes,
  }
}

function confirmElement(changes) {
  if (!selectedElement.value) return
  recordHistory()
  const index = elements.value.findIndex(
    (element) => element.editorId === selectedId.value,
  )
  if (index >= 0) elements.value[index] = { ...elements.value[index], ...changes }
  closeElementPanel()
  publishLayout()
}

function startCanvasResize(event, direction) {
  if (event.button !== 0) return
  if (!beginPointerSession(event)) return
  closeOverlays()
  conflictElementIds.value = []
  canvasResizeSession.value = {
    direction,
    startX: event.clientX,
    startY: event.clientY,
    start: { rows: gridRows.value, columns: gridColumns.value },
    candidate: { rows: gridRows.value, columns: gridColumns.value },
    anchorBounds: canvasRef.value?.getBoundingClientRect(),
    startOffsetX: canvasOffsetX.value,
    startOffsetY: canvasOffsetY.value,
    valid: true,
  }
  event.preventDefault()
}

function maintainCanvasAnchor() {
  const session = canvasResizeSession.value
  const viewport = viewportRef.value
  const currentBounds = canvasRef.value?.getBoundingClientRect()
  if (!session?.anchorBounds || !viewport || !currentBounds) return
  const adjustment = canvasAnchorAdjustment(
    session.direction,
    session.anchorBounds,
    currentBounds,
    {
      left: viewport.scrollLeft,
      top: viewport.scrollTop,
      maximumLeft: viewport.scrollWidth - viewport.clientWidth,
      maximumTop: viewport.scrollHeight - viewport.clientHeight,
    },
  )
  viewport.scrollLeft = adjustment.scrollLeft
  viewport.scrollTop = adjustment.scrollTop
  canvasOffsetX.value += adjustment.offsetX
  canvasOffsetY.value += adjustment.offsetY
}

function updateCanvasResize(event) {
  const session = canvasResizeSession.value
  if (!session) return
  const proposed = canvasSizeFromPointer(
    session.start,
    session.direction,
    event.clientX - session.startX,
    event.clientY - session.startY,
    CELL_SIZE,
    zoom.value,
    MIN_CANVAS_SIZE,
  )
  const conflicts = canvasResizeConflict(
    elements.value,
    proposed.rows,
    proposed.columns,
  )
  session.candidate = proposed
  session.valid = conflicts.length === 0
  nextTick(maintainCanvasAnchor)
  if (conflicts.length) {
    conflictElementIds.value = conflicts.map((element) => element.id)
    return
  }
  conflictElementIds.value = []
}

function finishCanvasResize() {
  const session = canvasResizeSession.value
  if (!session) return
  if (session.valid) {
    const changed =
      session.start.rows !== session.candidate.rows ||
      session.start.columns !== session.candidate.columns
    if (changed) {
      recordHistory()
      gridRows.value = session.candidate.rows
      gridColumns.value = session.candidate.columns
      publishLayout()
    }
    conflictElementIds.value = []
  } else {
    canvasOffsetX.value = session.startOffsetX
    canvasOffsetY.value = session.startOffsetY
    ElMessage.warning('画布边界内仍有元素，无法缩小')
    conflictElementIds.value = []
  }
  canvasResizeSession.value = undefined
}

function startPan(event) {
  if (event.button !== 2) return
  const viewport = viewportRef.value
  if (!viewport) return
  if (!beginPointerSession(event)) return
  panSession = {
    startX: event.clientX,
    startY: event.clientY,
    scrollLeft: viewport.scrollLeft,
    scrollTop: viewport.scrollTop,
  }
  isPanning.value = true
  event.preventDefault()
}

function updatePan(event) {
  if (!panSession || !viewportRef.value) return
  viewportRef.value.scrollLeft =
    panSession.scrollLeft - (event.clientX - panSession.startX)
  viewportRef.value.scrollTop =
    panSession.scrollTop - (event.clientY - panSession.startY)
}

function finishPan() {
  isPanning.value = false
  panSession = undefined
}

function onGlobalPointerMove(event) {
  if (event.pointerId !== activePointerId) return
  if (drawing.value) {
    const point = gridPointFromEvent(event)
    if (point) drawing.value.current = point
    return
  }
  if (manipulation.value) {
    updateManipulation(event)
    return
  }
  if (canvasResizeSession.value) {
    updateCanvasResize(event)
    return
  }
  if (isPanning.value) updatePan(event)
}

function onGlobalPointerUp(event) {
  if (event.pointerId !== activePointerId) return
  if (drawing.value) {
    const point = gridPointFromEvent(event)
    if (point) drawing.value.current = point
    pendingRect.value = normalizeGridRect(
      drawing.value.start,
      drawing.value.current,
    )
    drawing.value = undefined
    pickerVisible.value = true
    nextTick(positionPicker)
  } else if (manipulation.value) {
    finishManipulation()
  } else if (canvasResizeSession.value) {
    finishCanvasResize()
  } else if (isPanning.value) {
    finishPan()
  }
  detachGlobalPointerTracking()
}

function cancelPointerSession(event) {
  if (event && event.pointerId !== activePointerId) return
  if (canvasResizeSession.value) {
    canvasOffsetX.value = canvasResizeSession.value.startOffsetX
    canvasOffsetY.value = canvasResizeSession.value.startOffsetY
  }
  drawing.value = undefined
  manipulation.value = undefined
  canvasResizeSession.value = undefined
  conflictElementIds.value = []
  finishPan()
  detachGlobalPointerTracking()
}

function onWheel(event) {
  event.preventDefault()
  const viewport = viewportRef.value
  if (!viewport) return
  const oldZoom = zoom.value
  const nextZoom = Math.min(2.5, Math.max(0.25, oldZoom + (event.deltaY < 0 ? 0.1 : -0.1)))
  if (nextZoom === oldZoom) return
  const bounds = viewport.getBoundingClientRect()
  const pointerX = event.clientX - bounds.left + viewport.scrollLeft
  const pointerY = event.clientY - bounds.top + viewport.scrollTop
  zoom.value = Number(nextZoom.toFixed(2))
  nextTick(() => {
    viewport.scrollLeft = (pointerX / oldZoom) * zoom.value - (event.clientX - bounds.left)
    viewport.scrollTop = (pointerY / oldZoom) * zoom.value - (event.clientY - bounds.top)
    if (pickerVisible.value) positionPicker()
  })
}

function setZoom(value) {
  zoom.value = Number(Math.min(2.5, Math.max(0.25, value)).toFixed(2))
  nextTick(() => {
    centerCanvas()
    if (pickerVisible.value) positionPicker()
  })
}

function centerCanvas() {
  const viewport = viewportRef.value
  if (!viewport) return
  viewport.scrollLeft = Math.max(0, (viewport.scrollWidth - viewport.clientWidth) / 2)
  viewport.scrollTop = Math.max(0, (viewport.scrollHeight - viewport.clientHeight) / 2)
}

function fitCanvas() {
  const viewport = viewportRef.value
  if (!viewport) return
  canvasOffsetX.value = 0
  canvasOffsetY.value = 0
  const fitted = Math.min(
    (viewport.clientWidth - 120) / (gridColumns.value * CELL_SIZE),
    (viewport.clientHeight - 120) / (gridRows.value * CELL_SIZE),
    1,
  )
  zoom.value = Number(Math.min(2.5, Math.max(0.25, fitted)).toFixed(2))
  nextTick(centerCanvas)
}

function elementFromTarget(target) {
  const node = target instanceof Element ? target.closest('[data-editor-id]') : undefined
  return node?.dataset.editorId
}

function panelNode() {
  return panelRef.value?.$el || panelRef.value
}

function pickerNode() {
  return pickerRef.value?.$el || pickerRef.value
}

function closeOnOutsidePointer(event) {
  const target = event.target
  if (pickerVisible.value && !pickerNode()?.contains(target)) closePicker()
  if (
    selectedId.value &&
    !panelNode()?.contains(target) &&
    elementFromTarget(target) !== String(selectedId.value)
  ) {
    closeElementPanel()
  }
}

onMounted(() => {
  window.addEventListener('pointerdown', closeOnOutsidePointer)
  nextTick(fitCanvas)
})

onBeforeUnmount(() => {
  window.removeEventListener('pointerdown', closeOnOutsidePointer)
  detachGlobalPointerTracking()
})
</script>

<template>
  <section class="venue-layout-editor">
    <header class="editor-toolbar">
      <el-button
        v-if="showBack"
        text
        class="back-button"
        :icon="ArrowLeft"
        @click="emit('back')"
      >
        返回
      </el-button>
      <span v-if="showBack" class="toolbar-divider" />
      <strong>{{ title }}</strong>
      <span class="canvas-size">{{ displayRows }} × {{ displayColumns }}</span>
      <span class="capacity-summary">
        布局座位数 {{ seatCount }} · 人工容纳人数 {{ manualCapacity ?? '未填写' }}
      </span>
      <el-tag
        v-if="capacityMismatch"
        class="capacity-warning"
        type="warning"
        effect="plain"
      >
        容量不一致
      </el-tag>
      <span class="toolbar-spacer" />
      <el-button-group>
        <el-button :icon="RefreshLeft" :disabled="!undoStack.length" @click="undo">
          撤销
        </el-button>
        <el-button :icon="RefreshRight" :disabled="!redoStack.length" @click="redo">
          重做
        </el-button>
      </el-button-group>
      <el-button :icon="Aim" @click="fitCanvas">适应画布</el-button>
      <el-button-group>
        <el-button :icon="Minus" aria-label="缩小画布" @click="setZoom(zoom - 0.1)" />
        <el-button class="zoom-value" @click="setZoom(1)">
          {{ Math.round(zoom * 100) }}%
        </el-button>
        <el-button :icon="Plus" aria-label="放大画布" @click="setZoom(zoom + 0.1)" />
      </el-button-group>
      <el-button type="primary" :icon="Check" :loading="saving" @click="emit('save')">
        {{ saveLabel }}
      </el-button>
    </header>

    <div class="editor-body" :class="`dock-${panelDock}`">
      <VenueElementPanel
        ref="panelRef"
        :element="selectedElement"
        :venue-name="venueName"
        :venue-description="venueDescription"
        :grid-rows="displayRows"
        :grid-columns="displayColumns"
        :collapsed="panelCollapsed"
        :dock="panelDock"
        @preview="previewElement"
        @confirm="confirmElement"
        @cancel="closeElementPanel"
        @toggle="panelCollapsed = !panelCollapsed"
        @dock="panelDock = $event"
      />

      <main class="canvas-pane">
        <div ref="canvasSurfaceRef" class="canvas-surface">
          <div
            v-if="conflictElementIds.length && !manipulation"
            class="conflict-banner"
          >
            画布边界内仍有元素，当前尺寸未生效
          </div>
          <div
            ref="viewportRef"
            class="canvas-viewport"
            :class="{ panning: isPanning }"
            @pointerdown="startPan"
            @contextmenu.prevent
            @wheel="onWheel"
          >
            <div class="canvas-content">
              <div class="canvas-stage" :style="stageStyle">
                <div
                  ref="canvasRef"
                  class="designer-canvas"
                  :style="canvasStyle"
                  @pointerdown="startSelection"
                >
                  <div
                    v-for="element in elements"
                    :key="element.editorId"
                    class="layout-element"
                    :class="{
                      selected: element.editorId === selectedId,
                      seat: renderedElement(element).kind === ELEMENT_KINDS.SEAT,
                      conflict: isElementConflict(element),
                    }"
                    :style="elementStyle(element)"
                    :data-editor-id="element.editorId"
                    @pointerdown="startElementMove($event, element)"
                    @dblclick.stop="deleteElement(element)"
                  >
                    <span>{{ renderedElement(element).name }}</span>
                    <template v-if="element.editorId === selectedId">
                      <button
                        v-for="handle in resizeHandles"
                        :key="handle"
                        type="button"
                        class="resize-handle"
                        :class="`handle-${handle}`"
                        :aria-label="`向 ${handle} 调整元素大小`"
                        @pointerdown.stop="startElementResize($event, element, handle)"
                      />
                    </template>
                  </div>

                  <div
                    v-if="selectionRect"
                    class="selection-preview"
                    :class="{ pending: !drawing }"
                    :style="selectionStyle"
                  />

                  <button
                    type="button"
                    class="canvas-resize-handle canvas-resize-north"
                    aria-label="调整画布高度"
                    @pointerdown.stop="startCanvasResize($event, 'north')"
                  />
                  <button
                    type="button"
                    class="canvas-resize-handle canvas-resize-east"
                    aria-label="调整画布宽度"
                    @pointerdown.stop="startCanvasResize($event, 'east')"
                  />
                  <button
                    type="button"
                    class="canvas-resize-handle canvas-resize-south"
                    aria-label="调整画布高度"
                    @pointerdown.stop="startCanvasResize($event, 'south')"
                  />
                  <button
                    type="button"
                    class="canvas-resize-handle canvas-resize-west"
                    aria-label="调整画布宽度"
                    @pointerdown.stop="startCanvasResize($event, 'west')"
                  />
                  <button
                    v-for="corner in ['north-west', 'north-east', 'south-east', 'south-west']"
                    :key="corner"
                    type="button"
                    class="canvas-resize-handle canvas-resize-corner"
                    :class="`canvas-resize-${corner}`"
                    aria-label="同时调整画布长宽"
                    @pointerdown.stop="startCanvasResize($event, corner)"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>

        <VenueElementPicker
          v-if="pickerVisible && pendingRect"
          ref="pickerRef"
          :class="pickerDocked ? 'picker-dock' : 'picker-popover'"
          :rect="pendingRect"
          :style="pickerDocked ? undefined : pickerStyle"
          @choose="chooseElement"
          @cancel="closePicker"
        />
      </main>
    </div>
  </section>
</template>

<style scoped>
.venue-layout-editor {
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f4f7fb;
}

.editor-toolbar {
  min-height: 58px;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid #dbe4f0;
}

.capacity-summary {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}

.editor-toolbar > strong {
  color: var(--ink);
  font-size: 15px;
}

.back-button {
  color: var(--muted);
}

.toolbar-divider {
  width: 1px;
  height: 22px;
  background: #dbe4f0;
}

.toolbar-spacer {
  flex: 1;
}

.canvas-size {
  padding: 4px 9px;
  color: var(--brand);
  background: #edf5ff;
  border: 1px solid #cfe2fb;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.zoom-value {
  width: 64px;
}

.editor-body {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

.editor-body.dock-right .venue-element-panel {
  order: 2;
}

.editor-body.dock-left .venue-element-panel {
  order: 0;
}

.canvas-pane {
  flex: 1;
  min-width: 0;
  position: relative;
  order: 1;
  display: flex;
  overflow: hidden;
}

.canvas-surface {
  flex: 1;
  min-width: 0;
  height: 100%;
  position: relative;
}

.canvas-viewport {
  width: 100%;
  height: 100%;
  overflow: auto;
  background:
    linear-gradient(rgba(55, 87, 126, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(55, 87, 126, 0.06) 1px, transparent 1px),
    #f4f7fb;
  background-size: 24px 24px;
  scrollbar-gutter: stable;
}

.canvas-viewport.panning {
  cursor: grabbing;
  user-select: none;
}

.canvas-content {
  width: max-content;
  min-width: 100%;
  height: max-content;
  min-height: 100%;
  display: grid;
  place-items: center;
  padding: 84px;
}

.canvas-stage {
  position: relative;
  flex: none;
}

.designer-canvas {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: 0 0;
  touch-action: none;
  background:
    linear-gradient(rgba(34, 73, 122, 0.09) 1px, transparent 1px),
    linear-gradient(90deg, rgba(34, 73, 122, 0.09) 1px, transparent 1px),
    #fff;
  background-size: var(--editor-cell) var(--editor-cell);
  border: 1px solid #b9cbe2;
  box-shadow: 0 14px 34px rgba(40, 75, 118, 0.13);
  cursor: crosshair;
}

.layout-element {
  position: absolute;
  z-index: 3;
  display: grid;
  place-items: center;
  box-sizing: border-box;
  overflow: hidden;
  color: #26364b;
  border: 1px solid;
  cursor: move;
  user-select: none;
}

.layout-element.seat {
  border-radius: 6px;
}

.layout-element > span {
  max-width: 100%;
  overflow: hidden;
  padding: 2px;
  font-size: 10px;
  font-weight: 650;
  line-height: 1.15;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.layout-element.selected {
  z-index: 8;
  overflow: visible;
  box-shadow:
    0 0 0 2px #fff,
    0 0 0 5px var(--brand);
}

.layout-element.conflict {
  z-index: 9;
  background: #fee2e2 !important;
  border-color: #dc2626 !important;
  box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.24);
}

.resize-handle {
  width: 10px;
  height: 10px;
  position: absolute;
  z-index: 12;
  padding: 0;
  background: #fff;
  border: 2px solid var(--brand);
  border-radius: 50%;
}

.handle-nw {
  top: -7px;
  left: -7px;
  cursor: nwse-resize;
}

.handle-n {
  top: -7px;
  left: calc(50% - 5px);
  cursor: ns-resize;
}

.handle-ne {
  top: -7px;
  right: -7px;
  cursor: nesw-resize;
}

.handle-e {
  top: calc(50% - 5px);
  right: -7px;
  cursor: ew-resize;
}

.handle-se {
  right: -7px;
  bottom: -7px;
  cursor: nwse-resize;
}

.handle-s {
  bottom: -7px;
  left: calc(50% - 5px);
  cursor: ns-resize;
}

.handle-sw {
  bottom: -7px;
  left: -7px;
  cursor: nesw-resize;
}

.handle-w {
  top: calc(50% - 5px);
  left: -7px;
  cursor: ew-resize;
}

.selection-preview {
  position: absolute;
  z-index: 15;
  box-sizing: border-box;
  background: rgba(37, 99, 235, 0.12);
  border: 2px solid var(--brand);
  pointer-events: none;
}

.selection-preview.pending {
  background:
    repeating-linear-gradient(
      -45deg,
      rgba(37, 99, 235, 0.24) 0,
      rgba(37, 99, 235, 0.24) 7px,
      rgba(255, 255, 255, 0.72) 7px,
      rgba(255, 255, 255, 0.72) 14px
    );
  box-shadow: inset 0 0 0 2px #fff;
}

.canvas-resize-handle {
  position: absolute;
  z-index: 18;
  padding: 0;
  background: transparent;
  border: 0;
}

.canvas-resize-north,
.canvas-resize-south {
  right: 18px;
  left: 18px;
  height: 10px;
  cursor: ns-resize;
}

.canvas-resize-north {
  top: -5px;
}

.canvas-resize-south {
  bottom: -5px;
}

.canvas-resize-east,
.canvas-resize-west {
  top: 18px;
  bottom: 18px;
  width: 10px;
  cursor: ew-resize;
}

.canvas-resize-east {
  right: -5px;
}

.canvas-resize-west {
  left: -5px;
}

.canvas-resize-corner {
  width: 16px;
  height: 16px;
  background: #fff;
  border: 2px solid var(--brand);
  border-radius: 4px;
}

.canvas-resize-north-west {
  top: -8px;
  left: -8px;
  cursor: nwse-resize;
}

.canvas-resize-north-east {
  top: -8px;
  right: -8px;
  cursor: nesw-resize;
}

.canvas-resize-south-east {
  right: -8px;
  bottom: -8px;
  cursor: nwse-resize;
}

.canvas-resize-south-west {
  bottom: -8px;
  left: -8px;
  cursor: nesw-resize;
}

.picker-popover {
  position: absolute;
  z-index: 40;
}

.picker-dock {
  flex: none;
  align-self: flex-start;
  max-height: calc(100% - 24px);
  margin: 12px;
  overflow: auto;
}

.conflict-banner {
  position: absolute;
  top: 12px;
  left: 50%;
  z-index: 35;
  padding: 8px 14px;
  color: #b42318;
  background: #fff2f0;
  border: 1px solid #f3b5ad;
  border-radius: 8px;
  box-shadow: 0 8px 20px rgba(116, 26, 17, 0.12);
  font-size: 12px;
  transform: translateX(-50%);
}
</style>
