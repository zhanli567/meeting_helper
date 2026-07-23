<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Lock } from '@element-plus/icons-vue'
import type { LayoutElement, Participant, PlanItem, Workspace } from '@/types/workspace'

const props = defineProps<{
  workspace: Workspace
  zoom: number
  selectedParticipantId?: string
  continuousParticipantId?: string
  draggingParticipantId?: string
}>()

const emit = defineEmits<{
  assign: [participantId: string, targetElementId: string]
  select: [participant?: Participant]
  seatClick: [element: LayoutElement]
  dragState: [participantId?: string]
}>()

const scrollRef = ref<HTMLElement>()
const dragTargetId = ref<string>()
const isPanning = ref(false)
let panStartX = 0
let panStartY = 0
let panScrollLeft = 0
let panScrollTop = 0

const participantById = computed(
  () => new Map(props.workspace.participants.map((person) => [person.id, person])),
)
const itemByTarget = computed(() => {
  const result = new Map<string, PlanItem>()
  props.workspace.items.forEach((item) =>
    item.targetElementIds.forEach((elementId) => result.set(elementId, item)),
  )
  return result
})

const unit = computed(() => props.workspace.layout.cellSize * props.zoom)
const canvasStyle = computed(() => ({
  width: `${props.workspace.layout.gridColumns * unit.value}px`,
  height: `${props.workspace.layout.gridRows * unit.value}px`,
  '--unit': `${unit.value}px`,
}))

function elementStyle(element: LayoutElement) {
  return {
    left: `${(element.column - 1) * unit.value}px`,
    top: `${(element.row - 1) * unit.value}px`,
    width: `${element.columnSpan * unit.value}px`,
    height: `${element.rowSpan * unit.value}px`,
    backgroundColor: element.backgroundColor || '#fff',
    borderColor: element.borderColor || '#d7dee9',
    transform: element.rotation ? `rotate(${element.rotation}deg)` : undefined,
    zIndex: element.assignable ? 5 : element.type === 'DOOR' ? 4 : 2,
  }
}

function itemFor(elementId: string) {
  return itemByTarget.value.get(elementId)
}

function participantFor(elementId: string) {
  const item = itemFor(elementId)
  return item?.participantId ? participantById.value.get(item.participantId) : undefined
}

function visualStyle(element: LayoutElement) {
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

function onDragStart(event: DragEvent, participant: Participant) {
  event.dataTransfer?.setData('text/participant-id', participant.id)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
  emit('select', participant)
  emit('dragState', participant.id)
}

function startParticipantDrag(event: DragEvent, elementId: string) {
  const participant = participantFor(elementId)
  if (participant) onDragStart(event, participant)
}

function onDragOver(event: DragEvent, element: LayoutElement) {
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dragTargetId.value = element.id
}

function onDragLeave(event: DragEvent, element: LayoutElement) {
  if ((event.currentTarget as HTMLElement).contains(event.relatedTarget as Node)) return
  if (dragTargetId.value === element.id) dragTargetId.value = undefined
}

function onDrop(event: DragEvent, element: LayoutElement) {
  event.preventDefault()
  const participantId = event.dataTransfer?.getData('text/participant-id')
  if (participantId) emit('assign', participantId, element.id)
  dragTargetId.value = undefined
  emit('dragState', undefined)
}

function onSeatClick(element: LayoutElement) {
  const person = participantFor(element.id)
  if (person) emit('select', person)
  else emit('seatClick', element)
}

function typeLabel(type: string) {
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

function startPan(event: MouseEvent) {
  if (event.button !== 0) return
  const target = event.target as HTMLElement
  if (target.closest('.seat-element, button, input')) return
  const container = scrollRef.value
  if (!container) return
  isPanning.value = true
  panStartX = event.clientX
  panStartY = event.clientY
  panScrollLeft = container.scrollLeft
  panScrollTop = container.scrollTop
  window.addEventListener('mousemove', movePan)
  window.addEventListener('mouseup', endPan)
  event.preventDefault()
}

function movePan(event: MouseEvent) {
  if (!isPanning.value || !scrollRef.value) return
  scrollRef.value.scrollLeft = panScrollLeft - (event.clientX - panStartX)
  scrollRef.value.scrollTop = panScrollTop - (event.clientY - panStartY)
}

function endPan() {
  isPanning.value = false
  window.removeEventListener('mousemove', movePan)
  window.removeEventListener('mouseup', endPan)
}

onBeforeUnmount(endPan)
</script>

<template>
  <div
    ref="scrollRef"
    class="canvas-scroll"
    :class="{ panning: isPanning, 'drag-active': draggingParticipantId }"
    @mousedown="startPan"
  >
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
                <span>
                  {{ participantFor(element.id)?.participantType || '参会人员' }}
                  <template v-if="participantFor(element.id)?.level">
                    · 职级{{ participantFor(element.id)?.level }}
                  </template>
                </span>
                <span v-if="participantFor(element.id)?.primaryBatchName">
                  {{ participantFor(element.id)?.primaryBatchName }} ·
                  {{ participantFor(element.id)?.awards[0]?.awardName }}
                </span>
                <span v-if="participantFor(element.id)?.repeatedBatches.length">
                  重复批次：{{ participantFor(element.id)?.repeatedBatches.join('、') }}
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
              selected: participantFor(element.id)?.id === selectedParticipantId,
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
          >
            <span class="seat-code">{{ element.code }}</span>
            <template v-if="participantFor(element.id)">
              <span
                class="seat-person"
                :draggable="!itemFor(element.id)?.locked"
                @dragstart="startParticipantDrag($event, element.id)"
                @dragend="emit('dragState', undefined)"
              >
                {{ participantFor(element.id)?.name }}
              </span>
              <span v-if="participantFor(element.id)?.repeatedBatches.length" class="repeat-badge">
                +{{ participantFor(element.id)?.repeatedBatches.length }}
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
</template>

<style scoped>
.canvas-scroll {
  width: 100%;
  height: 100%;
  overflow: auto;
  padding: 26px 30px 38px;
  background:
    linear-gradient(#e9edf4 1px, transparent 1px),
    linear-gradient(90deg, #e9edf4 1px, transparent 1px), #f8fafc;
  background-size: 24px 24px;
  cursor: grab;
}

.canvas-scroll.panning {
  cursor: grabbing;
  user-select: none;
}

.venue-canvas {
  position: relative;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #d8e0eb;
  box-shadow: 0 16px 36px rgba(30, 45, 72, 0.12);
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
  color: #718096;
  font-size: max(7px, calc(var(--unit) * 0.22));
  text-align: center;
}

.type-stage {
  color: #1e3a5f;
  border-radius: 0 0 12px 12px;
  font-size: max(12px, calc(var(--unit) * 0.42));
  font-weight: 700;
  letter-spacing: 0.34em;
}

.type-aisle {
  border-style: dashed;
  color: #a0aec0;
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
  color: #9a3412;
  font-weight: 700;
}

.seat-element {
  cursor: pointer;
  user-select: none;
}

.canvas-scroll.drag-active .seat-element.drop-ready {
  border-color: #60a5fa !important;
  box-shadow: inset 0 0 0 1px rgba(96, 165, 250, 0.5);
}

.canvas-scroll.drag-active .seat-element.swap-ready:not(.locked) {
  border-color: #a5b4fc !important;
  box-shadow: inset 0 0 0 1px rgba(129, 140, 248, 0.45);
}

.seat-element.drop-target {
  z-index: 20 !important;
  border-color: #1d4ed8 !important;
  box-shadow:
    0 0 0 3px rgba(59, 130, 246, 0.46),
    0 8px 18px rgba(37, 99, 235, 0.28) !important;
  transform: scale(1.12);
}

.seat-element:hover {
  z-index: 12 !important;
  box-shadow: 0 0 0 2px #4d77b8;
}

.seat-element.selected {
  z-index: 13 !important;
  box-shadow:
    0 0 0 3px #2457a6,
    0 6px 14px rgba(36, 87, 166, 0.24);
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
  color: #64748b;
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

.repeat-badge {
  position: absolute;
  top: 1px;
  right: 1px;
  padding: 1px 3px;
  color: #fff;
  background: #8b5cf6;
  border-radius: 6px;
  font-size: 7px;
  line-height: 1.2;
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
  background: rgba(37, 99, 235, 0.9);
  font-size: max(7px, calc(var(--unit) * 0.22));
  font-weight: 700;
  line-height: 1.1;
  text-align: center;
}

.tooltip-card {
  display: grid;
  min-width: 190px;
  gap: 5px;
  color: #475569;
  font-size: 12px;
}

.tooltip-card strong {
  color: #172033;
}

@keyframes targetPulse {
  50% {
    box-shadow: inset 0 0 0 2px #3b82f6;
  }
}
</style>
