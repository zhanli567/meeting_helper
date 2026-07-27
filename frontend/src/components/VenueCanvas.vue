<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Lock } from '@element-plus/icons-vue'
import { firstParticipantSummary } from '@/utils/participantFields'
import { startParticipantDrag as performParticipantDrag } from '@/utils/participantActions'
import { displayCellUnit, elementBox } from '@/utils/venueCanvasMetrics'
const props = defineProps({
  workspace: { type: Object, required: true },
  zoom: { type: Number, required: true },
  selectedParticipantId: { type: String, default: undefined },
  continuousParticipantId: { type: String, default: undefined },
  draggingParticipantId: { type: String, default: undefined },
  readonly: { type: Boolean, default: false },
})
const emit = defineEmits(['assign', 'unassign', 'select', 'seatClick', 'dragState', 'zoomChange'])
const scrollRef = ref()
const dragTargetId = ref()
const isPanning = ref(false)
let panMoved = false
let panStartX = 0
let panStartY = 0
let panScrollLeft = 0
let panScrollTop = 0
const participantById = computed(
  () => new Map(props.workspace.participants.map((person) => [person.id, person])),
)
const itemByTarget = computed(() => {
  const result = new Map()
  props.workspace.items.forEach((item) =>
    item.targetElementIds.forEach((elementId) => result.set(elementId, item)),
  )
  return result
})
const unit = computed(() => displayCellUnit(props.workspace.layout.cellSize, props.zoom))
const canvasStyle = computed(() => ({
  width: `${props.workspace.layout.gridColumns * unit.value}px`,
  height: `${props.workspace.layout.gridRows * unit.value}px`,
  '--unit': `${unit.value}px`,
}))
function elementStyle(element) {
  const box = elementBox(element, unit.value)
  return {
    left: `${box.left}px`,
    top: `${box.top}px`,
    width: `${box.width}px`,
    height: `${box.height}px`,
    backgroundColor: element.backgroundColor || '#fff',
    borderColor: element.borderColor || '#d7dee9',
    transform: element.rotation ? `rotate(${element.rotation}deg)` : undefined,
    zIndex: element.assignable ? 5 : element.type === 'DOOR' ? 4 : 2,
  }
}
function itemFor(elementId) {
  return itemByTarget.value.get(elementId)
}
function participantFor(elementId) {
  const item = itemFor(elementId)
  return item?.participantId ? participantById.value.get(item.participantId) : undefined
}
function participantSeatSummary(elementId) {
  return firstParticipantSummary(participantFor(elementId), props.workspace.fieldDefinitions)
}
function visualStyle(element) {
  const item = itemFor(element.id)
  const person = participantFor(element.id)
  return {
    ...elementStyle(element),
    backgroundColor:
      person?.displayColor || item?.backgroundColor || element.backgroundColor || '#ffffff',
    color: item?.textColor || '#172033',
    fontWeight: item?.bold ? '700' : undefined,
  }
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
  if (participant) onDragStart(event, participant)
}
function onDragOver(event, element) {
  if (props.readonly) return
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dragTargetId.value = element.id
}
function onDragLeave(event, element) {
  if (event.currentTarget.contains(event.relatedTarget)) return
  if (dragTargetId.value === element.id) dragTargetId.value = undefined
}
function onDrop(event, element) {
  if (props.readonly) return
  event.preventDefault()
  const participantId = event.dataTransfer?.getData('text/participant-id')
  if (participantId) emit('assign', participantId, element.id)
  dragTargetId.value = undefined
  emit('dragState', undefined)
}
function onSeatClick(element) {
  if (panMoved) return
  const item = itemFor(element.id)
  const person = participantFor(element.id)
  if (person) emit('select', person)
  else if (!item) emit('seatClick', element)
}
function onSeatDoubleClick(element) {
  if (props.readonly) return
  const item = itemFor(element.id)
  const person = participantFor(element.id)
  if (person && !item?.locked) emit('unassign', person.id)
}
function typeLabel(type) {
  return (
    {
      STAGE: '舞台',
      AISLE: '走廊',
      DOOR: '门',
      WALL: '墙',
      TABLE: '桌子',
      STAIR: '楼梯',
      SCREEN: '屏幕',
      PODIUM: '讲台',
    }[type] || ''
  )
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
onBeforeUnmount(endPan)
</script>

<template>
  <div
    ref="scrollRef"
    class="canvas-scroll"
    :class="{ panning: isPanning, 'drag-active': draggingParticipantId }"
    @mousedown="startPan"
    @contextmenu.prevent
    @wheel="onWheel"
  >
    <div class="canvas-content">
      <div class="venue-canvas" :style="canvasStyle">
        <template v-for="element in workspace.layout.elements" :key="element.id">
        <el-tooltip
          v-if="element.assignable"
          :show-after="360"
          placement="top"
          effect="light"
          popper-class="seat-tooltip"
        >
          <template #content>
            <div class="tooltip-card">
              <strong>{{ element.code }}</strong>
              <template v-if="participantFor(element.id)">
                <span>
                  {{ participantFor(element.id)?.name }} ·
                  {{ participantFor(element.id)?.employeeNo }}
                </span>
                <span v-if="participantSeatSummary(element.id)">
                  {{ participantSeatSummary(element.id) }}
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
              device: itemFor(element.id) && itemFor(element.id)?.type !== 'PERSON',
              'continuous-target': continuousParticipantId && !itemFor(element.id),
              'drop-ready': draggingParticipantId && !itemFor(element.id),
              'swap-ready': draggingParticipantId && participantFor(element.id),
              'drop-target': dragTargetId === element.id,
            }"
            :style="visualStyle(element)"
            @dragover="onDragOver($event, element)"
            @dragleave="onDragLeave($event, element)"
            @drop="onDrop($event, element)"
            @click="onSeatClick(element)"
            @dblclick.stop="onSeatDoubleClick(element)"
          >
            <span class="seat-code">{{ element.code }}</span>
            <template v-if="participantFor(element.id)">
              <span
                class="seat-person"
                :draggable="!readonly && !itemFor(element.id)?.locked"
                :title="readonly ? '已发布版本仅供查看' : '双击移回待排列表'"
                @dragstart="startParticipantDrag($event, element.id)"
                @dragend="emit('dragState', undefined)"
              >
                {{ participantFor(element.id)?.name }}
              </span>
              <span v-if="participantSeatSummary(element.id)" class="seat-summary">
                {{ participantSeatSummary(element.id) }}
              </span>
              <el-icon v-if="itemFor(element.id)?.locked" class="lock-badge"><Lock /></el-icon>
            </template>
            <span v-else-if="itemFor(element.id)" class="seat-device">
              {{ itemFor(element.id)?.label }}
            </span>
            <span v-if="dragTargetId === element.id" class="drop-copy">
              {{ participantFor(element.id) ? '交换到这里' : '放到这里' }}
            </span>
          </div>
        </el-tooltip>

        <div
          v-else
          class="layout-element structural-element"
          :class="`type-${element.type.toLowerCase()}`"
          :style="visualStyle(element)"
        >
          <span v-if="element.label || typeLabel(element.type)">
            {{ element.label || typeLabel(element.type) }}
          </span>
        </div>
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
  padding: 26px 30px 38px;
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

.type-stage {
  color: var(--ink);
  border-radius: 0 0 12px 12px;
  font-size: max(12px, calc(var(--unit) * 0.42));
  font-weight: 700;
  letter-spacing: 0.34em;
}

.type-aisle {
  border-style: dashed;
  color: var(--tertiary);
}

.type-wall {
  background-image: repeating-linear-gradient(
    45deg,
    rgba(255, 255, 255, 0.12) 0,
    rgba(255, 255, 255, 0.12) 4px,
    transparent 4px,
    transparent 8px
  );
}

.type-door {
  color: var(--warning);
  font-weight: 700;
}

.seat-element {
  cursor: pointer;
  user-select: none;
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

.seat-person:has(+ .seat-summary) {
  inset: 25% 1px auto;
  height: 30%;
}

.seat-summary {
  position: absolute;
  inset: 58% 1px 1px;
  overflow: hidden;
  color: inherit;
  font-size: max(6px, calc(var(--unit) * 0.19));
  line-height: 1;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lock-badge {
  position: absolute;
  right: 1px;
  bottom: 1px;
  color: #334155;
  font-size: 8px;
}

.drop-copy {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: grid;
  place-items: center;
  padding: 2px;
  color: #fff;
  background: rgba(10, 89, 247, 0.9);
  font-size: max(7px, calc(var(--unit) * 0.22));
  font-weight: 700;
  line-height: 1.1;
  text-align: center;
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
