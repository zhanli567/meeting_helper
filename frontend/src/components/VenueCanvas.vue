<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Lock } from '@element-plus/icons-vue'
import { startParticipantDrag as performParticipantDrag } from '@/utils/participantActions'
import { regionLabelAnchors, reservedItems } from '@/utils/seatRegions'
import { computeSeatLabels } from '@/utils/seatNumbering'
import { displayCellUnit, elementBox } from '@/utils/venueCanvasMetrics'
const props = defineProps({
  workspace: { type: Object, required: true },
  zoom: { type: Number, required: true },
  selectedParticipantId: { type: String, default: undefined },
  continuousParticipantId: { type: String, default: undefined },
  draggingParticipantId: { type: String, default: undefined },
  readonly: { type: Boolean, default: false },
  markerMode: { type: Boolean, default: false },
  markerRectEnabled: { type: Boolean, default: false },
  activeMarkerId: { type: String, default: undefined },
  markerSelectionIds: { type: Array, default: () => [] },
  participantColorById: { type: Object, default: () => ({}) },
})
const emit = defineEmits([
  'assign',
  'unassign',
  'select',
  'seatClick',
  'dragState',
  'zoomChange',
  'marker-seat-toggle',
  'marker-select',
  'marker-rect-select',
  'canvas-clear',
])
const scrollRef = ref()
const canvasRef = ref()
const dragTargetId = ref()
const tooltipSuppressed = ref(false)
const isPanning = ref(false)
const markerDrawing = ref(false)
const markerRectStart = ref()
const markerRectCurrent = ref()
let panMoved = false
let panStartX = 0
let panStartY = 0
let panScrollLeft = 0
let panScrollTop = 0
let markerMoved = false
let markerSuppressClick = false
const participantById = computed(
  () => new Map(props.workspace.participants.map((person) => [person.id, person])),
)
const itemByTarget = computed(() => {
  const result = new Map()
  ;(props.workspace.items || []).forEach((item) =>
    (item.targetElementIds || []).forEach((elementId) => result.set(elementId, item)),
  )
  return result
})
const reservedAreaItems = computed(() => reservedItems(props.workspace.items))
const reservedItemByElementId = computed(() => {
  const result = new Map()
  reservedAreaItems.value.forEach((item) =>
    (item.targetElementIds || []).forEach((elementId) => result.set(elementId, item)),
  )
  return result
})
const regionAnchors = computed(() =>
  reservedAreaItems.value.flatMap((item) =>
    regionLabelAnchors(item, props.workspace.layout.elements || []),
  ),
)
const markerSelectionSet = computed(() => new Set(props.markerSelectionIds || []))
const seatNumbering = computed(() => computeSeatLabels(props.workspace.layout.elements || []))
const rowLabelColumnBounds = computed(() =>
  (props.workspace.layout.elements || [])
    .filter(isSeat)
    .reduce((current, element) => {
      const column = Number(element.column)
      if (!Number.isFinite(column)) return current
      const columnSpan = Math.max(1, Number(element.columnSpan || 1))
      return {
        minColumn: Math.min(current.minColumn, column),
        maxColumn: Math.max(current.maxColumn, column + columnSpan - 1),
      }
    }, { minColumn: props.workspace.layout.gridColumns, maxColumn: 1 }),
)
const unit = computed(() => displayCellUnit(props.zoom))
const markerSelectionRect = computed(() => {
  if (!markerDrawing.value || !markerRectStart.value || !markerRectCurrent.value) return undefined
  return normalizeMarkerRect(markerRectStart.value, markerRectCurrent.value)
})
const seatTooltipDisabled = computed(() =>
  Boolean(props.draggingParticipantId) || tooltipSuppressed.value || markerDrawing.value,
)
const canvasStyle = computed(() => ({
  width: `${props.workspace.layout.gridColumns * unit.value}px`,
  height: `${props.workspace.layout.gridRows * unit.value}px`,
  '--unit': `${unit.value}px`,
}))
const isSeat = (element) => element.kind === 'SEAT'
function elementStyle(element) {
  const box = elementBox(element, unit.value)
  return {
    left: `${box.left}px`,
    top: `${box.top}px`,
    width: `${box.width}px`,
    height: `${box.height}px`,
    backgroundColor: element.fillColor || '#fff',
    borderColor: element.borderColor || '#d7dee9',
    zIndex: isSeat(element) ? 5 : 2,
  }
}
function itemFor(elementId) {
  return itemByTarget.value.get(elementId)
}
function participantFor(elementId) {
  const item = itemFor(elementId)
  return item?.participantId ? participantById.value.get(item.participantId) : undefined
}
function seatLabelFor(elementId) {
  return seatNumbering.value.labelsByElementId.get(elementId) || '座位'
}
function participantStyleFor(person) {
  return person?.id ? props.participantColorById?.[person.id] : undefined
}
function participantTooltipRows(person) {
  if (!person) return []
  return (props.workspace.fieldDefinitions || [])
    .filter((field) => !['name', 'employeeNo'].includes(field.code))
    .map((field) => {
      const values = person.attributeValues?.[field.code] || []
      const value = values.length ? values.join('、') : person.primaryAttributes?.[field.code]
      return {
        label: field.label,
        value: String(value || '').trim(),
      }
    })
    .filter((row) => row.value)
}
function visualStyle(element) {
  const item = itemFor(element.id)
  const person = participantFor(element.id)
  const participantStyle = participantStyleFor(person)
  return {
    ...elementStyle(element),
    backgroundColor:
      participantStyle?.backgroundColor ||
      person?.displayColor ||
      item?.backgroundColor ||
      element.fillColor ||
      '#ffffff',
    color: participantStyle?.textColor || item?.textColor || '#172033',
    fontWeight: item?.bold ? '700' : undefined,
  }
}
function rowLabelStyle(rowLabel, side) {
  const bounds =
    rowLabelColumnBounds.value.minColumn <= rowLabelColumnBounds.value.maxColumn
      ? rowLabelColumnBounds.value
      : { minColumn: 1, maxColumn: props.workspace.layout.gridColumns }
  return {
    top: `${(rowLabel.sourceRow - 0.5) * unit.value}px`,
    left:
      side === 'left'
        ? `${(bounds.minColumn - 1) * unit.value - 52}px`
        : `${bounds.maxColumn * unit.value + 18}px`,
  }
}
function regionAnchorStyle(anchor) {
  return {
    left: `${(anchor.centerColumn - 0.5) * unit.value}px`,
    top: `${(anchor.centerRow - 0.5) * unit.value}px`,
    color: anchor.textColor || '#172033',
    fontWeight: anchor.bold ? '700' : undefined,
  }
}
function canvasPointFromEvent(event) {
  const canvas = canvasRef.value
  if (!canvas) return undefined
  const rect = canvas.getBoundingClientRect()
  return {
    x: Math.min(Math.max(event.clientX - rect.left, 0), rect.width),
    y: Math.min(Math.max(event.clientY - rect.top, 0), rect.height),
  }
}
function normalizeMarkerRect(start, current) {
  const left = Math.min(start.x, current.x)
  const top = Math.min(start.y, current.y)
  return {
    left,
    top,
    width: Math.abs(current.x - start.x),
    height: Math.abs(current.y - start.y),
  }
}
function markerRectStyle(rect) {
  return {
    left: `${rect.left}px`,
    top: `${rect.top}px`,
    width: `${rect.width}px`,
    height: `${rect.height}px`,
  }
}
function elementOverlapsRect(element, rect) {
  const box = elementBox(element, unit.value)
  return (
    box.left < rect.left + rect.width &&
    box.left + box.width > rect.left &&
    box.top < rect.top + rect.height &&
    box.top + box.height > rect.top
  )
}
function seatIdsInMarkerRect(rect) {
  if (!rect || (rect.width < 2 && rect.height < 2)) return []
  return (props.workspace.layout.elements || [])
    .filter((element) => isSeat(element) && elementOverlapsRect(element, rect))
    .map((element) => element.id)
}
function canStartMarkerRectSelection(event) {
  return (
    props.markerMode &&
    props.markerRectEnabled &&
    !props.readonly &&
    event.button === 0 &&
    !event.target?.closest?.('.region-label')
  )
}
function startMarkerRectSelection(event) {
  if (!canStartMarkerRectSelection(event)) return
  const point = canvasPointFromEvent(event)
  if (!point) return
  markerDrawing.value = true
  markerMoved = false
  markerRectStart.value = point
  markerRectCurrent.value = point
  tooltipSuppressed.value = true
  window.addEventListener('pointermove', moveMarkerRectSelection)
  window.addEventListener('pointerup', finishMarkerRectSelection)
  window.addEventListener('pointercancel', cancelMarkerRectSelection)
  event.preventDefault()
}
function moveMarkerRectSelection(event) {
  if (!markerDrawing.value) return
  const point = canvasPointFromEvent(event)
  if (!point) return
  markerRectCurrent.value = point
  if (
    !markerMoved &&
    markerRectStart.value &&
    Math.hypot(point.x - markerRectStart.value.x, point.y - markerRectStart.value.y) > 4
  ) {
    markerMoved = true
  }
}
function cleanupMarkerRectSelection() {
  markerDrawing.value = false
  markerRectStart.value = undefined
  markerRectCurrent.value = undefined
  window.removeEventListener('pointermove', moveMarkerRectSelection)
  window.removeEventListener('pointerup', finishMarkerRectSelection)
  window.removeEventListener('pointercancel', cancelMarkerRectSelection)
  window.setTimeout(() => {
    tooltipSuppressed.value = false
  }, 0)
}
function finishMarkerRectSelection(event) {
  if (!markerDrawing.value) return
  const point = canvasPointFromEvent(event)
  if (point) markerRectCurrent.value = point
  const rect = markerSelectionRect.value
  const shouldEmit = markerMoved && rect
  const elementIds = shouldEmit ? seatIdsInMarkerRect(rect) : []
  cleanupMarkerRectSelection()
  if (!shouldEmit) return
  markerSuppressClick = true
  emit('marker-rect-select', elementIds)
  window.setTimeout(() => {
    markerSuppressClick = false
    markerMoved = false
  }, 0)
}
function cancelMarkerRectSelection() {
  cleanupMarkerRectSelection()
  markerMoved = false
}
function onDragStart(event, participant) {
  performParticipantDrag({
    event,
    participant,
    readonly: props.readonly,
    locked: participant.locked,
    onSelect: (person) => emit('select', person),
    onDragState: (participantId) => emit('dragState', participantId),
  })
}
function startParticipantDrag(event, elementId) {
  const participant = participantFor(elementId)
  if (participant) {
    tooltipSuppressed.value = true
    onDragStart(event, participant)
  }
}
function endParticipantDrag() {
  dragTargetId.value = undefined
  emit('dragState', undefined)
  window.setTimeout(() => {
    tooltipSuppressed.value = false
  }, 0)
}
function onDragOver(event, element) {
  if (props.readonly || props.markerMode || !isSeat(element) || reservedItemByElementId.value.has(element.id)) {
    return
  }
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dragTargetId.value = element.id
}
function onDragLeave(event, element) {
  if (event.currentTarget.contains(event.relatedTarget)) return
  if (dragTargetId.value === element.id) dragTargetId.value = undefined
}
function onDrop(event, element) {
  if (props.readonly || props.markerMode || !isSeat(element) || reservedItemByElementId.value.has(element.id)) {
    return
  }
  event.preventDefault()
  const participantId = event.dataTransfer?.getData('text/participant-id')
  if (participantId) emit('assign', participantId, element.id)
  dragTargetId.value = undefined
  emit('dragState', undefined)
  tooltipSuppressed.value = false
}
function onSeatClick(element) {
  if (panMoved || !isSeat(element)) return
  if (props.markerMode) {
    selectReservedRegionFromSeat(element)
    return
  }
  const item = itemFor(element.id)
  const person = participantFor(element.id)
  if (person) emit('select', person)
  else if (!item) emit('seatClick', element)
}
function selectReservedRegionFromSeat(element) {
  const reservedItem = reservedItemByElementId.value.get(element.id)
  if (reservedItem && reservedItem.id !== props.activeMarkerId) emit('marker-select', reservedItem)
}
function onCanvasClear() {
  if (panMoved || markerSuppressClick) return
  emit('canvas-clear')
}
function onMarkerSeatToggle(element) {
  if (props.markerMode) {
    if (props.readonly || !isSeat(element)) return
    const item = itemFor(element.id)
    if (item && item.type !== 'RESERVED') return
    emit('marker-seat-toggle', element)
    return
  }
  if (props.readonly || !isSeat(element)) return
  const item = itemFor(element.id)
  const person = participantFor(element.id)
  if (person && !item?.locked) emit('unassign', person.id)
}
function startPan(event) {
  if (event.button !== 2) return
  const container = scrollRef.value
  if (!container) return
  isPanning.value = true
  panStartX = event.clientX
  panStartY = event.clientY
  panScrollLeft = container.scrollLeft
  panScrollTop = container.scrollTop
  panMoved = false
  window.addEventListener('mousemove', movePan)
  window.addEventListener('mouseup', endPan)
  event.preventDefault()
}
function movePan(event) {
  if (!isPanning.value || !scrollRef.value) return
  if (Math.abs(event.clientX - panStartX) > 3 || Math.abs(event.clientY - panStartY) > 3) {
    panMoved = true
  }
  scrollRef.value.scrollLeft = panScrollLeft - (event.clientX - panStartX)
  scrollRef.value.scrollTop = panScrollTop - (event.clientY - panStartY)
}
function endPan() {
  isPanning.value = false
  window.removeEventListener('mousemove', movePan)
  window.removeEventListener('mouseup', endPan)
  window.setTimeout(() => {
    panMoved = false
  }, 0)
}
function onWheel(event) {
  event.preventDefault()
  const container = scrollRef.value
  if (!container) return
  const oldZoom = props.zoom
  const rect = container.getBoundingClientRect()
  const pointerX = event.clientX - rect.left + container.scrollLeft
  const pointerY = event.clientY - rect.top + container.scrollTop
  emit('zoomChange', event.deltaY < 0 ? 0.08 : -0.08, event)
  nextTick(() => {
    if (!scrollRef.value || props.zoom === oldZoom) return
    scrollRef.value.scrollLeft = (pointerX / oldZoom) * props.zoom - (event.clientX - rect.left)
    scrollRef.value.scrollTop = (pointerY / oldZoom) * props.zoom - (event.clientY - rect.top)
  })
}
function centerCanvas() {
  const container = scrollRef.value
  if (!container) return
  container.scrollLeft = Math.max(0, (container.scrollWidth - container.clientWidth) / 2)
  container.scrollTop = Math.max(0, (container.scrollHeight - container.clientHeight) / 2)
}
function centerCanvasAfterRender() {
  nextTick(centerCanvas)
}
onMounted(centerCanvasAfterRender)
watch(
  () => [
    props.workspace.meeting.id,
    props.workspace.layout.gridRows,
    props.workspace.layout.gridColumns,
  ],
  centerCanvasAfterRender,
)
watch(
  () => props.draggingParticipantId,
  (participantId) => {
    if (!participantId) {
      window.setTimeout(() => {
        tooltipSuppressed.value = false
      }, 0)
    }
  },
)
onBeforeUnmount(() => {
  endPan()
  cancelMarkerRectSelection()
})
</script>

<template>
  <div
    ref="scrollRef"
    class="canvas-scroll"
    :class="{ panning: isPanning, 'drag-active': draggingParticipantId, 'marker-mode': markerMode }"
    @mousedown="startPan"
    @contextmenu.prevent
    @wheel="onWheel"
  >
    <div class="canvas-content">
      <div
        ref="canvasRef"
        class="venue-canvas"
        :class="{ 'marker-rect-enabled': markerMode && markerRectEnabled }"
        :style="canvasStyle"
        @pointerdown="startMarkerRectSelection"
        @click="onCanvasClear"
      >
        <template v-for="element in workspace.layout.elements" :key="element.id">
        <el-tooltip
          v-if="isSeat(element)"
          :disabled="seatTooltipDisabled"
          :show-after="360"
          placement="top"
          effect="light"
          popper-class="seat-tooltip"
        >
          <template #content>
            <div class="tooltip-card">
              <strong>{{ seatLabelFor(element.id) }}</strong>
              <template v-if="participantFor(element.id)">
                <span>
                  {{ participantFor(element.id)?.name }} ·
                  {{ participantFor(element.id)?.employeeNo }}
                </span>
                <span
                  v-for="row in participantTooltipRows(participantFor(element.id))"
                  :key="row.label"
                >
                  {{ row.label }}：{{ row.value }}
                </span>
              </template>
              <span v-else-if="itemFor(element.id)">
                {{ itemFor(element.id)?.label || itemFor(element.id)?.type }}
              </span>
              <span v-else>空座位，可拖入人员</span>
            </div>
          </template>

          <div
            class="layout-element seat-element"
            :class="{
              occupied: participantFor(element.id),
              selected:
                Boolean(selectedParticipantId) &&
                participantFor(element.id)?.id === selectedParticipantId,
              locked: itemFor(element.id)?.locked,
              reserved: reservedItemByElementId.has(element.id),
              'marker-selected': markerSelectionSet.has(element.id),
              device:
                itemFor(element.id) &&
                itemFor(element.id)?.type !== 'PERSON' &&
                itemFor(element.id)?.type !== 'RESERVED',
              'continuous-target': continuousParticipantId && !itemFor(element.id),
              'drop-ready': draggingParticipantId && !itemFor(element.id),
              'swap-ready': draggingParticipantId && participantFor(element.id),
              'drop-target': dragTargetId === element.id,
            }"
            :style="visualStyle(element)"
            @dragover="onDragOver($event, element)"
            @dragleave="onDragLeave($event, element)"
            @drop="onDrop($event, element)"
            @click.stop="onSeatClick(element)"
            @dblclick.stop="onMarkerSeatToggle(element)"
          >
            <span v-if="!reservedItemByElementId.has(element.id)" class="seat-code">
              {{ seatLabelFor(element.id) }}
            </span>
            <template v-if="participantFor(element.id)">
              <span
                class="seat-person"
                :draggable="!readonly && !itemFor(element.id)?.locked"
                :title="readonly ? '已发布版本仅供查看' : '双击移回待排列表'"
                @dragstart="startParticipantDrag($event, element.id)"
                @dragend="endParticipantDrag"
              >
                {{ participantFor(element.id)?.name }}
              </span>
              <el-icon v-if="itemFor(element.id)?.locked" class="lock-badge"><Lock /></el-icon>
            </template>
            <span v-else-if="itemFor(element.id) && itemFor(element.id)?.type !== 'RESERVED'" class="seat-device">
              {{ itemFor(element.id)?.label }}
            </span>
          </div>
        </el-tooltip>

        <div
          v-else
          class="layout-element structural-element"
          :style="visualStyle(element)"
          :title="element.name"
          @click.stop
        >
          <span>{{ element.name }}</span>
        </div>
        </template>
        <span
          v-for="anchor in regionAnchors"
          :key="anchor.id"
          class="region-label"
          :style="regionAnchorStyle(anchor)"
        >
          {{ anchor.label }}
        </span>
        <div
          v-if="markerSelectionRect"
          class="marker-selection-preview"
          :style="markerRectStyle(markerSelectionRect)"
        />
        <template v-for="rowLabel in seatNumbering.rows" :key="rowLabel.sourceRow">
          <span class="row-label row-label-left" :style="rowLabelStyle(rowLabel, 'left')">
            第{{ rowLabel.displayRow }}排
          </span>
          <span class="row-label row-label-right" :style="rowLabelStyle(rowLabel, 'right')">
            第{{ rowLabel.displayRow }}排
          </span>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.canvas-scroll {
  width: 100%;
  height: 100%;
  overflow: auto;
  background:
    linear-gradient(rgba(0, 0, 0, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 0, 0, 0.045) 1px, transparent 1px), #f7f8fa;
  background-size: 24px 24px;
  cursor: default;
}

.canvas-content {
  width: max-content;
  min-width: 100%;
  height: max-content;
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 26px 68px 38px;
}

.canvas-scroll.panning {
  cursor: grabbing;
  user-select: none;
}

.venue-canvas {
  flex: none;
  position: relative;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  box-shadow: var(--shadow);
}

.venue-canvas.marker-rect-enabled {
  cursor: crosshair;
}

.marker-selection-preview {
  position: absolute;
  z-index: 32;
  pointer-events: none;
  background: rgba(10, 89, 247, 0.1);
  border: 1px solid rgba(10, 89, 247, 0.8);
  border-radius: 6px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.75) inset;
}

.layout-element {
  position: absolute;
  overflow: hidden;
  border: 1px solid;
  transition:
    box-shadow 0.15s ease,
    transform 0.15s ease,
    background-color 0.15s ease;
}

.structural-element {
  display: grid;
  place-items: center;
  color: var(--muted);
  font-size: max(7px, calc(var(--unit) * 0.22));
  text-align: center;
}

.seat-element {
  cursor: pointer;
  user-select: none;
}

.canvas-scroll.marker-mode .seat-element {
  cursor: pointer;
}

.seat-element.reserved {
  border-style: dashed;
}

.seat-element.marker-selected {
  z-index: 18 !important;
  box-shadow:
    inset 0 0 0 2px var(--brand),
    0 0 0 2px rgba(10, 89, 247, 0.18);
}

.canvas-scroll.drag-active .seat-element.drop-ready {
  border-color: var(--brand) !important;
  box-shadow: inset 0 0 0 1px rgba(10, 89, 247, 0.26);
}

.canvas-scroll.drag-active .seat-element.swap-ready:not(.locked) {
  border-color: rgba(10, 89, 247, 0.38) !important;
  box-shadow: inset 0 0 0 1px rgba(10, 89, 247, 0.18);
}

.seat-element.drop-target {
  z-index: 20 !important;
  border-color: var(--brand) !important;
  box-shadow:
    0 0 0 3px var(--brand-soft),
    0 8px 18px rgba(10, 89, 247, 0.2) !important;
  transform: scale(1.12);
}

.seat-element:hover {
  z-index: 12 !important;
  box-shadow: 0 0 0 2px rgba(10, 89, 247, 0.32);
}

.seat-element.selected {
  z-index: 13 !important;
  box-shadow:
    0 0 0 3px var(--brand),
    0 6px 14px rgba(10, 89, 247, 0.2);
}

.seat-element.continuous-target {
  animation: targetPulse 1.4s ease-in-out infinite;
}

.seat-element.device {
  background-image: repeating-linear-gradient(
    -45deg,
    rgba(255, 255, 255, 0.2) 0,
    rgba(255, 255, 255, 0.2) 5px,
    transparent 5px,
    transparent 10px
  );
}

.seat-code {
  position: absolute;
  top: 1px;
  left: 2px;
  max-width: calc(100% - 4px);
  overflow: hidden;
  color: var(--muted);
  font-size: max(5px, calc(var(--unit) * 0.16));
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.seat-person,
.seat-device {
  position: absolute;
  inset: 35% 1px 1px;
  display: grid;
  place-items: center;
  overflow: hidden;
  font-size: max(7px, calc(var(--unit) * 0.27));
  font-weight: 650;
  line-height: 1;
  text-align: center;
  white-space: nowrap;
}

.lock-badge {
  position: absolute;
  right: 1px;
  bottom: 1px;
  color: #334155;
  font-size: 8px;
}

.region-label {
  max-width: calc(var(--unit) * 5);
  min-width: 0;
  min-height: auto;
  position: absolute;
  z-index: 24;
  transform: translate(-50%, -50%);
  display: grid;
  place-items: center;
  padding: 0 2px;
  overflow: hidden;
  background: transparent;
  border: 0;
  box-shadow: none;
  font-size: max(9px, calc(var(--unit) * 0.24));
  line-height: 1;
  pointer-events: none;
  text-shadow:
    0 1px 2px rgba(255, 255, 255, 0.85),
    0 1px 1px rgba(15, 23, 42, 0.2);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-label {
  width: auto;
  min-width: 42px;
  height: 20px;
  position: absolute;
  transform: translateY(-50%);
  display: grid;
  place-items: center;
  padding: 0 6px;
  color: #475569;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.34);
  border-radius: 7px;
  box-shadow: 0 2px 7px rgba(15, 23, 42, 0.08);
  font-size: clamp(8px, calc(var(--unit) * 0.2), 12px);
  font-weight: 650;
  line-height: 1;
  pointer-events: none;
  white-space: nowrap;
  writing-mode: horizontal-tb;
}

.tooltip-card {
  display: grid;
  min-width: 190px;
  gap: 5px;
  color: var(--muted);
  font-size: 12px;
}

.tooltip-card strong {
  color: var(--ink);
}

@keyframes targetPulse {
  50% {
    box-shadow: inset 0 0 0 2px var(--brand);
  }
}
</style>
