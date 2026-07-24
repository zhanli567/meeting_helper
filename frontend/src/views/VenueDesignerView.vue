<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  Close,
  Delete,
  MagicStick,
  RefreshLeft,
  RefreshRight,
  Setting,
  ZoomIn,
  ZoomOut,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage } from '@/api/http'
const router = useRouter()
const route = useRoute()
const venueId = computed(() =>
  typeof route.params.venueId === 'string' ? route.params.venueId : undefined,
)
const config = reactive({
  name: '自定义会议室',
  description: '',
  gridRows: 16,
  gridColumns: 30,
  cellSize: 34,
  frontDirection: 'TOP',
})
const elements = ref([])
const existingVenues = ref([])
const saving = ref(false)
const loading = ref(false)
const drawing = ref()
const pendingRect = ref()
const pickerVisible = ref(false)
const selectedId = ref()
const editorDraft = ref()
const editorPosition = reactive({ left: 360, top: 110 })
const undoStack = ref([])
const redoStack = ref([])
const zoom = ref(0.9)
const scrollRef = ref()
const canvasAreaRef = ref()
const panelRef = ref()
const isPanning = ref(false)
const panelDock = ref('left')
const panelCollapsed = ref(false)
const panelFreePosition = ref()
const isPanelDragging = ref(false)
const unit = 32
let panStartX = 0
let panStartY = 0
let panScrollLeft = 0
let panScrollTop = 0
let panelOffsetX = 0
let panelOffsetY = 0
const elementOptions = [
  { type: 'SEAT', label: '座位', description: '每格生成一个独立座位', color: '#ffffff' },
  { type: 'AISLE', label: '走廊', description: '可通行的连续区域', color: '#eff6ff' },
  { type: 'STAGE', label: '舞台', description: '面向观众的舞台区域', color: '#dbeafe' },
  { type: 'TABLE', label: '桌子', description: '可跨越多个网格', color: '#bae6fd' },
  { type: 'WALL', label: '墙壁', description: '不可通行的边界', color: '#8ca7ce' },
  { type: 'DOOR', label: '门', description: '可通行的出入口', color: '#bfdbfe' },
  { type: 'STAIR', label: '楼梯', description: '连接不同高度的通道', color: '#dbeafe' },
  { type: 'SCREEN', label: '屏幕', description: '展示设备区域', color: '#bfdbfe' },
  { type: 'PODIUM', label: '讲台', description: '演讲或主持位置', color: '#93c5fd' },
  { type: 'LABEL', label: '文字', description: '添加说明文字', color: '#f1f5f9' },
  { type: 'ERASER', label: '清空区域', description: '删除框选区域内的元素', color: '#fee2e2' },
]
const editableOptions = elementOptions.filter((option) => option.type !== 'ERASER')
const colorSwatches = [
  '#ffffff',
  '#eff6ff',
  '#dbeafe',
  '#bfdbfe',
  '#bae6fd',
  '#e0f2fe',
  '#f1f5f9',
  '#dbe4f0',
  '#8ca7ce',
  '#fde68a',
  '#fed7aa',
  '#fee2e2',
]
const selected = computed(() =>
  elements.value.find((element) => element.localId === selectedId.value),
)
const gridBaseStyle = computed(() => ({
  width: `${config.gridColumns * unit}px`,
  height: `${config.gridRows * unit}px`,
  '--designer-unit': `${unit}px`,
  transform: `scale(${zoom.value})`,
}))
const stageStyle = computed(() => ({
  width: `${config.gridColumns * unit * zoom.value}px`,
  height: `${config.gridRows * unit * zoom.value}px`,
}))
const previewRect = computed(() =>
  drawing.value ? normalizedRect(drawing.value.start, drawing.value.current) : undefined,
)
const previewStyle = computed(() => {
  if (!previewRect.value) return {}
  return {
    top: `${(previewRect.value.row - 1) * unit}px`,
    left: `${(previewRect.value.column - 1) * unit}px`,
    width: `${previewRect.value.columnSpan * unit}px`,
    height: `${previewRect.value.rowSpan * unit}px`,
  }
})
const panelStyle = computed(() =>
  panelFreePosition.value
    ? {
        left: `${panelFreePosition.value.left}px`,
        top: `${panelFreePosition.value.top}px`,
        right: 'auto',
        bottom: 'auto',
      }
    : undefined,
)
onMounted(async () => {
  loading.value = true
  try {
    existingVenues.value = await meetingApi.venues()
    if (venueId.value) {
      const venue = await meetingApi.venue(venueId.value)
      if (venue.preset) {
        ElMessage.warning('系统预置场馆只允许查看，不能编辑')
        await router.replace('/venues')
        return
      }
      Object.assign(config, {
        name: venue.name,
        description: venue.description || '',
        gridRows: venue.gridRows,
        gridColumns: venue.gridColumns,
        cellSize: venue.cellSize,
        frontDirection: venue.frontDirection,
      })
      elements.value = venue.elements.map((element) => ({
        ...element,
        localId: crypto.randomUUID(),
        backgroundColor: element.backgroundColor || '#ffffff',
        borderColor: element.borderColor || '#93b4df',
      }))
    }
    await nextTick()
    centerCanvas()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    loading.value = false
  }
})
function cloneElements(source = elements.value) {
  return source.map((element) => ({ ...element }))
}
function recordHistory() {
  undoStack.value.push(cloneElements())
  if (undoStack.value.length > 60) undoStack.value.shift()
  redoStack.value = []
}
function undo() {
  const snapshot = undoStack.value.pop()
  if (!snapshot) return
  redoStack.value.push(cloneElements())
  elements.value = cloneElements(snapshot)
  closeEditor()
}
function redo() {
  const snapshot = redoStack.value.pop()
  if (!snapshot) return
  undoStack.value.push(cloneElements())
  elements.value = cloneElements(snapshot)
  closeEditor()
}
function defaults(type) {
  const map = {
    SEAT: { assignable: true, walkable: false, capacity: 1, backgroundColor: '#ffffff' },
    AISLE: { assignable: false, walkable: true, capacity: 0, backgroundColor: '#eff6ff' },
    WALL: { assignable: false, walkable: false, capacity: 0, backgroundColor: '#8ca7ce' },
    DOOR: { assignable: false, walkable: true, capacity: 0, backgroundColor: '#bfdbfe' },
    STAIR: { assignable: false, walkable: true, capacity: 0, backgroundColor: '#dbeafe' },
    STAGE: { assignable: false, walkable: true, capacity: 0, backgroundColor: '#dbeafe' },
    TABLE: { assignable: false, walkable: false, capacity: 0, backgroundColor: '#bae6fd' },
    SCREEN: { assignable: false, walkable: false, capacity: 0, backgroundColor: '#bfdbfe' },
    PODIUM: { assignable: false, walkable: false, capacity: 0, backgroundColor: '#93c5fd' },
    LABEL: { assignable: false, walkable: true, capacity: 0, backgroundColor: '#f1f5f9' },
    EMPTY: { assignable: false, walkable: false, capacity: 0, backgroundColor: '#ffffff' },
  }
  return map[type]
}
function typeLabel(type) {
  return editableOptions.find((option) => option.type === type)?.label || '元素'
}
function occupiedAt(row, column, excludeId) {
  return elements.value.find(
    (element) =>
      element.localId !== excludeId &&
      row >= element.row &&
      row < element.row + element.rowSpan &&
      column >= element.column &&
      column < element.column + element.columnSpan,
  )
}
function rectCollides(rect, excludeId) {
  for (let row = rect.row; row < rect.row + rect.rowSpan; row++) {
    for (let column = rect.column; column < rect.column + rect.columnSpan; column++) {
      if (occupiedAt(row, column, excludeId)) return true
    }
  }
  return false
}
function pointFromEvent(event) {
  const grid = event.currentTarget
  const rect = grid.getBoundingClientRect()
  const column = Math.floor((event.clientX - rect.left) / (unit * zoom.value)) + 1
  const row = Math.floor((event.clientY - rect.top) / (unit * zoom.value)) + 1
  if (row < 1 || row > config.gridRows || column < 1 || column > config.gridColumns) return
  return { row, column }
}
function normalizedRect(start, end) {
  return {
    row: Math.min(start.row, end.row),
    column: Math.min(start.column, end.column),
    rowSpan: Math.abs(start.row - end.row) + 1,
    columnSpan: Math.abs(start.column - end.column) + 1,
  }
}
function onGridPointerDown(event) {
  if (event.button !== 0) return
  if (event.target.closest('.draft-element')) return
  closeEditor()
  const point = pointFromEvent(event)
  if (!point) return
  drawing.value = { start: point, current: point, pointerId: event.pointerId }
  event.currentTarget.setPointerCapture(event.pointerId)
  event.preventDefault()
}
function onGridPointerMove(event) {
  if (!drawing.value || drawing.value.pointerId !== event.pointerId) return
  const point = pointFromEvent(event)
  if (point) drawing.value.current = point
}
function onGridPointerUp(event) {
  if (!drawing.value || drawing.value.pointerId !== event.pointerId) return
  pendingRect.value = normalizedRect(drawing.value.start, drawing.value.current)
  drawing.value = undefined
  pickerVisible.value = true
}
function chooseElement(type) {
  const rect = pendingRect.value
  if (!rect) return
  pickerVisible.value = false
  if (type === 'ERASER') {
    const removable = elements.value.filter((element) => intersects(element, rect))
    if (!removable.length) {
      ElMessage.info('框选区域内没有可删除的元素')
      return
    }
    recordHistory()
    const ids = new Set(removable.map((element) => element.localId))
    elements.value = elements.value.filter((element) => !ids.has(element.localId))
    ElMessage.success(`已删除 ${removable.length} 个元素`)
    return
  }
  if (type === 'SEAT') {
    const available = []
    for (let row = rect.row; row < rect.row + rect.rowSpan; row++) {
      for (let column = rect.column; column < rect.column + rect.columnSpan; column++) {
        if (!occupiedAt(row, column)) available.push({ row, column })
      }
    }
    if (!available.length) {
      ElMessage.warning('框选区域已被其他元素占用')
      return
    }
    recordHistory()
    available.forEach(({ row, column }) => createElement('SEAT', row, column, 1, 1))
    selectedId.value = available.length === 1 ? elements.value.at(-1)?.localId : undefined
    if (available.length === 1 && selectedId.value) openEditorById(selectedId.value)
    ElMessage.success(`已放置 ${available.length} 个座位`)
    return
  }
  if (rectCollides(rect)) {
    ElMessage.warning('框选区域与已有元素重叠')
    return
  }
  recordHistory()
  const element = createElement(type, rect.row, rect.column, rect.rowSpan, rect.columnSpan)
  openEditorById(element.localId)
}
function intersects(element, rect) {
  return !(
    element.row + element.rowSpan - 1 < rect.row ||
    element.row > rect.row + rect.rowSpan - 1 ||
    element.column + element.columnSpan - 1 < rect.column ||
    element.column > rect.column + rect.columnSpan - 1
  )
}
function createElement(type, row, column, rowSpan, columnSpan) {
  const preset = defaults(type)
  const element = {
    localId: crypto.randomUUID(),
    type,
    row,
    column,
    rowSpan,
    columnSpan,
    rotation: 0,
    capacity: preset.capacity ?? 0,
    assignable: preset.assignable ?? false,
    walkable: preset.walkable ?? false,
    backgroundColor: preset.backgroundColor || '#ffffff',
    borderColor: '#93b4df',
    label: typeLabel(type),
  }
  if (type === 'SEAT') {
    const seatNumber = elements.value.filter((item) => item.type === 'SEAT').length + 1
    element.code = `座位${String(seatNumber).padStart(3, '0')}`
  }
  elements.value.push(element)
  return element
}
function renderElement(element) {
  return selectedId.value === element.localId && editorDraft.value ? editorDraft.value : element
}
function elementStyle(element) {
  const visual = renderElement(element)
  return {
    top: `${(element.row - 1) * unit}px`,
    left: `${(element.column - 1) * unit}px`,
    width: `${element.columnSpan * unit}px`,
    height: `${element.rowSpan * unit}px`,
    backgroundColor: visual.backgroundColor,
    borderColor: visual.borderColor,
  }
}
function openEditor(element, event) {
  selectedId.value = element.localId
  editorDraft.value = { ...element }
  const area = canvasAreaRef.value?.getBoundingClientRect()
  if (event && area) {
    editorPosition.left = Math.min(Math.max(12, event.clientX - area.left + 16), area.width - 344)
    editorPosition.top = Math.min(Math.max(64, event.clientY - area.top + 12), area.height - 510)
  }
}
function openEditorById(id) {
  const element = elements.value.find((item) => item.localId === id)
  if (element) openEditor(element)
}
function closeEditor() {
  selectedId.value = undefined
  editorDraft.value = undefined
}
function applyTypeDefaults() {
  if (!editorDraft.value) return
  const preset = defaults(editorDraft.value.type)
  editorDraft.value.assignable = preset.assignable ?? false
  editorDraft.value.walkable = preset.walkable ?? false
  editorDraft.value.capacity = preset.capacity ?? 0
  editorDraft.value.backgroundColor = preset.backgroundColor || editorDraft.value.backgroundColor
  if (!editorDraft.value.label) editorDraft.value.label = typeLabel(editorDraft.value.type)
}
function confirmEditor() {
  const draft = editorDraft.value
  if (!draft) return
  const rect = {
    row: draft.row,
    column: draft.column,
    rowSpan: draft.rowSpan,
    columnSpan: draft.columnSpan,
  }
  if (
    draft.row < 1 ||
    draft.column < 1 ||
    draft.row + draft.rowSpan - 1 > config.gridRows ||
    draft.column + draft.columnSpan - 1 > config.gridColumns
  ) {
    ElMessage.warning('元素不能超出画布范围')
    return
  }
  if (rectCollides(rect, draft.localId)) {
    ElMessage.warning('调整后的区域与其他元素重叠')
    return
  }
  const index = elements.value.findIndex((element) => element.localId === draft.localId)
  if (index < 0) return
  recordHistory()
  elements.value[index] = { ...draft }
  closeEditor()
  ElMessage.success('元素修改已确认')
}
function removeElement(element) {
  recordHistory()
  elements.value = elements.value.filter((item) => item.localId !== element.localId)
  closeEditor()
  ElMessage.success('元素已删除')
}
function removeSelected() {
  if (selected.value) removeElement(selected.value)
}
function normalizeGridSize() {
  const requiredRows = Math.max(
    5,
    ...elements.value.map((element) => element.row + element.rowSpan - 1),
  )
  const requiredColumns = Math.max(
    5,
    ...elements.value.map((element) => element.column + element.columnSpan - 1),
  )
  if (config.gridRows < requiredRows || config.gridColumns < requiredColumns) {
    config.gridRows = Math.max(config.gridRows, requiredRows)
    config.gridColumns = Math.max(config.gridColumns, requiredColumns)
    ElMessage.warning('画布不能缩小到已放置元素以内')
  }
}
function renumberSeats() {
  if (!elements.value.some((element) => element.type === 'SEAT')) {
    ElMessage.info('当前画布还没有座位')
    return
  }
  recordHistory()
  const rows = Array.from(
    new Set(
      elements.value.filter((element) => element.type === 'SEAT').map((element) => element.row),
    ),
  ).sort((left, right) => left - right)
  rows.forEach((gridRow, rowIndex) => {
    elements.value
      .filter((element) => element.type === 'SEAT' && element.row === gridRow)
      .sort((left, right) => left.column - right.column)
      .forEach((element, seatIndex) => {
        element.code = `${rowIndex + 1}排${String(seatIndex + 1).padStart(2, '0')}`
        element.groupCode = `ROW_${rowIndex + 1}`
        element.groupLabel = `${rowIndex + 1}排`
        element.sequenceNo = seatIndex + 1
      })
  })
  closeEditor()
  ElMessage.success('已按行重新编号座位')
}
function startPan(event) {
  if (event.button !== 2 || !scrollRef.value) return
  if (event.target.closest('.floating-settings, .element-editor, button, input')) {
    return
  }
  closeEditor()
  isPanning.value = true
  panStartX = event.clientX
  panStartY = event.clientY
  panScrollLeft = scrollRef.value.scrollLeft
  panScrollTop = scrollRef.value.scrollTop
  window.addEventListener('pointermove', movePan)
  window.addEventListener('pointerup', endPan)
  event.preventDefault()
}
function movePan(event) {
  if (!isPanning.value || !scrollRef.value) return
  scrollRef.value.scrollLeft = panScrollLeft - (event.clientX - panStartX)
  scrollRef.value.scrollTop = panScrollTop - (event.clientY - panStartY)
}
function endPan() {
  isPanning.value = false
  window.removeEventListener('pointermove', movePan)
  window.removeEventListener('pointerup', endPan)
}
function onWheel(event) {
  const container = scrollRef.value
  if (!container) return
  event.preventDefault()
  const oldZoom = zoom.value
  const nextZoom = Math.min(2, Math.max(0.45, oldZoom + (event.deltaY < 0 ? 0.08 : -0.08)))
  if (nextZoom === oldZoom) return
  const rect = container.getBoundingClientRect()
  const pointerX = event.clientX - rect.left + container.scrollLeft
  const pointerY = event.clientY - rect.top + container.scrollTop
  zoom.value = Number(nextZoom.toFixed(2))
  nextTick(() => {
    container.scrollLeft = (pointerX / oldZoom) * zoom.value - (event.clientX - rect.left)
    container.scrollTop = (pointerY / oldZoom) * zoom.value - (event.clientY - rect.top)
  })
}
function changeZoom(delta) {
  zoom.value = Math.min(2, Math.max(0.45, Number((zoom.value + delta).toFixed(2))))
}
function centerCanvas() {
  const container = scrollRef.value
  if (!container) return
  container.scrollLeft = Math.max(0, (container.scrollWidth - container.clientWidth) / 2)
  container.scrollTop = Math.max(0, (container.scrollHeight - container.clientHeight) / 2)
}
function startPanelDrag(event) {
  const panel = panelRef.value
  const area = canvasAreaRef.value
  if (!panel || !area || event.target.closest('button')) return
  const panelRect = panel.getBoundingClientRect()
  const areaRect = area.getBoundingClientRect()
  panelFreePosition.value = {
    left: panelRect.left - areaRect.left,
    top: panelRect.top - areaRect.top,
  }
  panelOffsetX = event.clientX - panelRect.left
  panelOffsetY = event.clientY - panelRect.top
  isPanelDragging.value = true
  window.addEventListener('pointermove', movePanel)
  window.addEventListener('pointerup', endPanelDrag)
  event.preventDefault()
}
function movePanel(event) {
  const area = canvasAreaRef.value
  const panel = panelRef.value
  if (!isPanelDragging.value || !area || !panel) return
  const areaRect = area.getBoundingClientRect()
  panelFreePosition.value = {
    left: Math.min(
      Math.max(8, event.clientX - areaRect.left - panelOffsetX),
      areaRect.width - panel.offsetWidth - 8,
    ),
    top: Math.min(
      Math.max(8, event.clientY - areaRect.top - panelOffsetY),
      areaRect.height - panel.offsetHeight - 8,
    ),
  }
}
function endPanelDrag() {
  const area = canvasAreaRef.value
  const position = panelFreePosition.value
  if (area && position) {
    if (position.top > area.clientHeight * 0.58) panelDock.value = 'bottom'
    else panelDock.value = position.left < area.clientWidth / 2 ? 'left' : 'right'
  }
  panelFreePosition.value = undefined
  isPanelDragging.value = false
  window.removeEventListener('pointermove', movePanel)
  window.removeEventListener('pointerup', endPanelDrag)
}
async function save() {
  const name = config.name.trim()
  if (!name || !elements.value.length) {
    ElMessage.warning('请填写场馆名称并至少放置一个元素')
    return
  }
  const duplicate = existingVenues.value.some(
    (venue) =>
      venue.id !== venueId.value &&
      venue.name.trim().toLocaleLowerCase() === name.toLocaleLowerCase(),
  )
  if (duplicate) {
    ElMessage.warning('场馆名称已存在，请换一个名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...config,
      name,
      elements: elements.value.map(({ localId, ...element }) => {
        void localId
        return element
      }),
    }
    if (venueId.value) await meetingApi.updateVenue(venueId.value, payload)
    else await meetingApi.createVenue(payload)
    ElMessage.success(venueId.value ? '场馆修改已保存' : '场馆模板已保存')
    await router.push('/venues')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    saving.value = false
  }
}
onBeforeUnmount(() => {
  endPan()
  endPanelDrag()
})
</script>

<template>
  <div class="app-page designer-page" v-loading="loading">
    <header class="app-header">
      <el-button text class="back-button" :icon="ArrowLeft" @click="router.push('/venues')">
        返回场馆库
      </el-button>
      <span class="header-divider" />
      <div class="brand-copy">
        <strong>{{ venueId ? '编辑场馆布局' : '自定义场馆设计器' }}</strong>
        <span>框选网格后选择元素；按住鼠标右键拖动画布平移，滚轮缩放</span>
      </div>
      <span class="header-spacer" />
      <el-button type="primary" :loading="saving" @click="save">
        {{ venueId ? '保存场馆修改' : '保存为场馆模板' }}
      </el-button>
    </header>

    <main ref="canvasAreaRef" class="designer-canvas-area">
      <div class="designer-help">
        <div>
          <strong>点击或框选网格开始绘制</strong>
          <span>座位会逐格生成；双击已有元素可快捷删除。</span>
        </div>
        <div class="canvas-actions">
          <el-button-group>
            <el-button :icon="RefreshLeft" :disabled="!undoStack.length" @click="undo">
              撤销
            </el-button>
            <el-button :icon="RefreshRight" :disabled="!redoStack.length" @click="redo">
              重做
            </el-button>
          </el-button-group>
          <el-button-group>
            <el-button :icon="ZoomOut" :disabled="zoom <= 0.45" @click="changeZoom(-0.1)" />
            <el-button class="zoom-copy" @click="centerCanvas">
              {{ Math.round(zoom * 100) }}%
            </el-button>
            <el-button :icon="ZoomIn" :disabled="zoom >= 2" @click="changeZoom(0.1)" />
          </el-button-group>
        </div>
      </div>

      <div
        ref="scrollRef"
        class="designer-scroll"
        :class="{ panning: isPanning }"
        @pointerdown="startPan"
        @contextmenu.prevent
        @wheel="onWheel"
      >
        <div class="zoom-stage" :style="stageStyle">
          <div
            class="designer-grid"
            :style="gridBaseStyle"
            @pointerdown="onGridPointerDown"
            @pointermove="onGridPointerMove"
            @pointerup="onGridPointerUp"
            @pointercancel="drawing = undefined"
          >
            <div
              v-for="element in elements"
              :key="element.localId"
              class="draft-element"
              :class="{
                selected: element.localId === selectedId,
                [`type-${element.type.toLowerCase()}`]: true,
              }"
              :style="elementStyle(element)"
              :title="`${element.code || ''}${element.code && element.label ? ' · ' : ''}${element.label || typeLabel(element.type)}`"
              @click.stop="openEditor(element, $event)"
              @dblclick.stop="removeElement(element)"
            >
              <span v-if="renderElement(element).code" class="element-code">
                {{ renderElement(element).code }}
              </span>
              <strong class="element-name">
                {{ renderElement(element).label || typeLabel(renderElement(element).type) }}
              </strong>
            </div>

            <div v-if="previewRect" class="draw-preview" :style="previewStyle">松开后选择元素</div>
          </div>
        </div>
      </div>

      <aside
        ref="panelRef"
        class="floating-settings"
        :class="[
          `dock-${panelDock}`,
          { collapsed: panelCollapsed, dragging: isPanelDragging, free: panelFreePosition },
        ]"
        :style="panelStyle"
        @pointerdown.stop
      >
        <header @pointerdown="startPanelDrag">
          <div>
            <el-icon><Setting /></el-icon>
            <strong>场馆信息</strong>
          </div>
          <el-button
            text
            circle
            class="collapse-button"
            :class="{ collapsed: panelCollapsed }"
            aria-label="收起或展开场馆信息"
            @click="panelCollapsed = !panelCollapsed"
          >
            <span>‹</span>
          </el-button>
        </header>
        <div v-show="!panelCollapsed" class="settings-content">
          <el-form label-position="top" size="small">
            <el-form-item label="场馆名称">
              <el-input v-model="config.name" maxlength="60" show-word-limit />
            </el-form-item>
            <el-form-item label="说明">
              <el-input
                v-model="config.description"
                type="textarea"
                :rows="2"
                maxlength="200"
                show-word-limit
              />
            </el-form-item>
            <div class="field-grid">
              <el-form-item label="画布行数">
                <el-input-number
                  v-model="config.gridRows"
                  :min="5"
                  :max="60"
                  controls-position="right"
                  @change="normalizeGridSize"
                />
              </el-form-item>
              <el-form-item label="画布列数">
                <el-input-number
                  v-model="config.gridColumns"
                  :min="5"
                  :max="80"
                  controls-position="right"
                  @change="normalizeGridSize"
                />
              </el-form-item>
            </div>
          </el-form>
          <el-button :icon="MagicStick" @click="renumberSeats">按行批量编号座位</el-button>
          <p>拖动本窗口可吸附到画布左侧、右侧或下方。</p>
        </div>
      </aside>

      <section
        v-if="editorDraft"
        class="element-editor"
        :style="{ left: `${editorPosition.left}px`, top: `${editorPosition.top}px` }"
        @pointerdown.stop
      >
        <div class="editor-heading">
          <div>
            <span>元素属性</span>
            <strong>{{ editorDraft.label || typeLabel(editorDraft.type) }}</strong>
          </div>
          <el-button text circle :icon="Close" aria-label="关闭属性卡" @click="closeEditor" />
        </div>

        <div class="editor-grid">
          <label class="full">
            元素类型
            <el-select v-model="editorDraft.type" size="small" @change="applyTypeDefaults">
              <el-option
                v-for="option in editableOptions"
                :key="option.type"
                :label="option.label"
                :value="option.type"
              />
            </el-select>
          </label>
          <label>
            起始行
            <el-input-number
              v-model="editorDraft.row"
              :min="1"
              :max="config.gridRows"
              size="small"
            />
          </label>
          <label>
            起始列
            <el-input-number
              v-model="editorDraft.column"
              :min="1"
              :max="config.gridColumns"
              size="small"
            />
          </label>
          <label>
            高度（格）
            <el-input-number
              v-model="editorDraft.rowSpan"
              :min="1"
              :max="config.gridRows"
              size="small"
            />
          </label>
          <label>
            宽度（格）
            <el-input-number
              v-model="editorDraft.columnSpan"
              :min="1"
              :max="config.gridColumns"
              size="small"
            />
          </label>
          <label class="full">
            元素编号
            <el-input
              v-model="editorDraft.code"
              size="small"
              maxlength="30"
              show-word-limit
              placeholder="用于座位编号、区域编号，可不填"
            />
          </label>
          <label class="full">
            元素名称
            <el-input
              v-model="editorDraft.label"
              size="small"
              maxlength="30"
              show-word-limit
              placeholder="画布主显示名称"
            />
          </label>
          <div class="color-field full">
            <span>填充颜色（选择后实时预览）</span>
            <div class="color-row">
              <button
                v-for="color in colorSwatches"
                :key="color"
                type="button"
                :class="{ active: editorDraft.backgroundColor === color }"
                :style="{ backgroundColor: color }"
                :aria-label="`使用颜色 ${color}`"
                @click="editorDraft.backgroundColor = color"
              />
              <el-color-picker v-model="editorDraft.backgroundColor" />
            </div>
          </div>
          <div class="color-field full">
            <span>边框颜色</span>
            <div class="color-row">
              <button
                v-for="color in colorSwatches.slice(0, 9)"
                :key="color"
                type="button"
                :class="{ active: editorDraft.borderColor === color }"
                :style="{ backgroundColor: color }"
                :aria-label="`使用边框颜色 ${color}`"
                @click="editorDraft.borderColor = color"
              />
              <el-color-picker v-model="editorDraft.borderColor" />
            </div>
          </div>
        </div>
        <footer>
          <el-button type="danger" plain :icon="Delete" @click="removeSelected">删除</el-button>
          <span />
          <el-button @click="closeEditor">取消</el-button>
          <el-button type="primary" @click="confirmEditor">确认修改</el-button>
        </footer>
      </section>
    </main>

    <el-dialog
      v-model="pickerVisible"
      title="选择框选区域的元素"
      width="600px"
      append-to-body
      destroy-on-close
    >
      <p class="picker-tip">
        已选择 {{ pendingRect?.rowSpan }} 行 × {{ pendingRect?.columnSpan }} 列；座位会逐格生成，
        其他元素会作为一个整体覆盖所选区域。
      </p>
      <div class="element-picker">
        <button
          v-for="option in elementOptions"
          :key="option.type"
          :class="{ danger: option.type === 'ERASER' }"
          @click="chooseElement(option.type)"
        >
          <i :style="{ backgroundColor: option.color }" />
          <span>
            <strong>{{ option.label }}</strong>
            <small>{{ option.description }}</small>
          </span>
        </button>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.designer-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.back-button {
  color: rgba(255, 255, 255, 0.9);
}

.designer-canvas-area {
  flex: 1;
  min-height: 0;
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.designer-help {
  min-height: 58px;
  z-index: 40;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 15px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid var(--line);
}

.designer-help > div:first-child {
  display: flex;
  align-items: baseline;
  gap: 14px;
}

.designer-help strong {
  font-size: 13px;
}

.designer-help span {
  color: #718096;
  font-size: 11px;
}

.canvas-actions {
  display: flex;
  gap: 10px;
}

.zoom-copy {
  width: 62px;
}

.designer-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 86px;
  background:
    linear-gradient(#e2ebf5 1px, transparent 1px),
    linear-gradient(90deg, #e2ebf5 1px, transparent 1px), #edf5fc;
  background-size: 24px 24px;
  cursor: default;
  scrollbar-gutter: stable;
}

.designer-scroll.panning {
  cursor: grabbing;
  user-select: none;
}

.zoom-stage {
  position: relative;
  min-width: 1px;
  min-height: 1px;
  margin: 0 auto;
}

.designer-grid {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: 0 0;
  background:
    linear-gradient(#dfe8f3 1px, transparent 1px),
    linear-gradient(90deg, #dfe8f3 1px, transparent 1px), #fff;
  background-size: var(--designer-unit) var(--designer-unit);
  box-shadow: 0 18px 42px rgba(37, 99, 235, 0.14);
  cursor: crosshair;
  touch-action: none;
}

.draft-element {
  position: absolute;
  z-index: 2;
  display: grid;
  grid-template-rows: auto 1fr;
  min-width: 0;
  overflow: hidden;
  padding: 2px;
  color: #17365f;
  border: 1px solid;
  cursor: pointer;
  user-select: none;
}

.draft-element:hover {
  z-index: 5;
  box-shadow: inset 0 0 0 2px #60a5fa;
}

.draft-element.selected {
  z-index: 6;
  box-shadow:
    0 0 0 3px #2563eb,
    0 6px 16px rgba(37, 99, 235, 0.22);
}

.element-code,
.element-name {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.element-code {
  color: #5f7390;
  font-size: 7px;
  line-height: 1;
}

.element-name {
  align-self: center;
  font-size: 8px;
  line-height: 1.1;
  text-align: center;
}

.draft-element.type-stage .element-name {
  font-weight: 700;
  letter-spacing: 0.12em;
}

.draw-preview {
  position: absolute;
  z-index: 12;
  display: grid;
  place-items: center;
  color: #1d4ed8;
  background: rgba(147, 197, 253, 0.38);
  border: 2px solid #2563eb;
  font-size: 10px;
  font-weight: 700;
  pointer-events: none;
}

.floating-settings {
  width: 300px;
  max-height: calc(100% - 92px);
  position: absolute;
  z-index: 45;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.97);
  border: 1px solid #9fbce4;
  border-radius: 14px;
  box-shadow: 0 16px 36px rgba(37, 85, 151, 0.18);
  transition:
    width 0.18s ease,
    transform 0.18s ease;
}

.floating-settings.dock-left {
  top: 78px;
  left: 16px;
}

.floating-settings.dock-right {
  top: 78px;
  right: 16px;
}

.floating-settings.dock-bottom {
  right: 50%;
  bottom: 16px;
  transform: translateX(50%);
}

.floating-settings.free,
.floating-settings.dragging {
  transform: none;
  transition: none;
}

.floating-settings.collapsed {
  width: 48px;
}

.floating-settings > header {
  min-height: 46px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 8px 0 14px;
  color: #244a80;
  background: #edf5ff;
  cursor: move;
}

.floating-settings > header > div {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.floating-settings.collapsed > header {
  padding: 0;
  justify-content: center;
}

.floating-settings.collapsed > header > div {
  display: none;
}

.collapse-button span {
  display: block;
  font-size: 26px;
  line-height: 1;
  transition: transform 0.18s;
}

.dock-right .collapse-button span,
.collapse-button.collapsed span {
  transform: rotate(180deg);
}

.dock-bottom .collapse-button span {
  transform: rotate(90deg);
}

.dock-bottom .collapse-button.collapsed span {
  transform: rotate(-90deg);
}

.settings-content {
  overflow: auto;
  padding: 14px;
}

.settings-content p {
  margin: 12px 0 0;
  color: #7b8ba0;
  font-size: 10px;
}

.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.field-grid :deep(.el-input-number) {
  width: 100%;
}

.element-editor {
  width: 332px;
  max-height: calc(100% - 84px);
  position: absolute;
  z-index: 60;
  overflow: auto;
  padding: 14px;
  background: rgba(255, 255, 255, 0.99);
  border: 1px solid #8fb3e5;
  border-radius: 14px;
  box-shadow: 0 20px 48px rgba(29, 78, 216, 0.22);
}

.editor-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  margin-bottom: 10px;
  border-bottom: 1px solid #e1eaf5;
}

.editor-heading > div {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.editor-heading span {
  color: #75869c;
  font-size: 10px;
}

.editor-heading strong {
  overflow: hidden;
  color: #17365f;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.editor-grid label,
.color-field {
  min-width: 0;
  display: grid;
  gap: 5px;
  color: #63758d;
  font-size: 10px;
}

.editor-grid .full {
  grid-column: 1 / -1;
}

.editor-grid :deep(.el-input-number),
.editor-grid :deep(.el-select) {
  width: 100%;
}

.editor-grid :deep(.el-input__wrapper) {
  min-width: 0;
  overflow: hidden;
}

.color-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.color-row > button {
  width: 24px;
  height: 24px;
  padding: 0;
  border: 1px solid #b8c8dc;
  border-radius: 6px;
  cursor: pointer;
}

.color-row > button.active {
  box-shadow:
    0 0 0 2px #fff,
    0 0 0 4px #2563eb;
}

.element-editor footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 13px;
  margin-top: 13px;
  border-top: 1px solid #e5ebf3;
}

.element-editor footer > span {
  flex: 1;
}

.picker-tip {
  margin: -4px 0 14px;
  color: #667085;
  font-size: 12px;
}

.element-picker {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.element-picker button {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  color: #334155;
  background: #fff;
  border: 1px solid #d8e4f3;
  border-radius: 10px;
  cursor: pointer;
  text-align: left;
}

.element-picker button:hover {
  color: #1d4ed8;
  background: #f3f8ff;
  border-color: #7da6df;
}

.element-picker button.danger:hover {
  color: #b42318;
  background: #fff5f5;
  border-color: #f4a5a5;
}

.element-picker i {
  width: 28px;
  height: 28px;
  flex: none;
  border: 1px solid #a9bdd5;
  border-radius: 7px;
}

.element-picker span {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.element-picker strong,
.element-picker small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.element-picker small {
  color: #7b8ba0;
  font-size: 10px;
}
</style>
