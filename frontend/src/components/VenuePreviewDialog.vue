<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Refresh, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { apiErrorMessage } from '@/api/http'
import { meetingApi } from '@/api/meeting'

const visible = defineModel({ required: true })
const props = defineProps({
  venueId: { type: String, default: '' },
})
const loading = ref(false)
const venue = ref()
const viewportRef = ref()
const zoom = ref(1)
const panning = ref(false)
let panStartX = 0
let panStartY = 0
let panScrollLeft = 0
let panScrollTop = 0

const cellSize = computed(() => venue.value?.cellSize || 34)
const canvasStyle = computed(() => ({
  width: `${(venue.value?.gridColumns || 1) * cellSize.value * zoom.value}px`,
  height: `${(venue.value?.gridRows || 1) * cellSize.value * zoom.value}px`,
  '--preview-cell': `${cellSize.value * zoom.value}px`,
}))

watch(
  () => [visible.value, props.venueId],
  async ([isVisible, venueId]) => {
    if (!isVisible || !venueId) return
    loading.value = true
    try {
      venue.value = await meetingApi.venue(venueId)
      await nextTick()
      fitCanvas()
    } catch (error) {
      ElMessage.error(apiErrorMessage(error))
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

function elementStyle(element) {
  const unit = cellSize.value * zoom.value
  return {
    top: `${(element.row - 1) * unit}px`,
    left: `${(element.column - 1) * unit}px`,
    width: `${element.columnSpan * unit}px`,
    height: `${element.rowSpan * unit}px`,
    zIndex: element.type === 'SEAT' ? 3 : 1,
    color: readableTextColor(element.backgroundColor),
    backgroundColor: element.backgroundColor || defaultColor(element.type),
    borderColor: element.borderColor || '#93b4df',
    transform: element.rotation ? `rotate(${element.rotation}deg)` : undefined,
  }
}

function readableTextColor(color) {
  if (!color?.startsWith('#') || color.length !== 7) return '#31506f'
  const red = Number.parseInt(color.slice(1, 3), 16)
  const green = Number.parseInt(color.slice(3, 5), 16)
  const blue = Number.parseInt(color.slice(5, 7), 16)
  return red * 0.299 + green * 0.587 + blue * 0.114 < 150 ? '#ffffff' : '#23415f'
}

function defaultColor(type) {
  return (
    {
      SEAT: '#ffffff',
      STAGE: '#dceafd',
      AISLE: '#f8fafc',
      WALL: '#8da2bd',
      DOOR: '#ffedd5',
      TABLE: '#d8c4a4',
      STAIR: '#dbe7f5',
      LABEL: '#f8fafc',
    }[type] || '#eef4fb'
  )
}

function typeLabel(type) {
  return (
    {
      SEAT: '座位',
      STAGE: '舞台',
      AISLE: '走廊',
      WALL: '墙',
      DOOR: '门',
      TABLE: '桌子',
      STAIR: '楼梯',
      SCREEN: '屏幕',
      PODIUM: '讲台',
      LABEL: '文字',
    }[type] || type
  )
}

function changeZoom(delta, event) {
  const viewport = viewportRef.value
  const oldZoom = zoom.value
  const nextZoom = Math.min(2.5, Math.max(0.25, Number((oldZoom + delta).toFixed(2))))
  if (!viewport || nextZoom === oldZoom) return
  const rect = viewport.getBoundingClientRect()
  const pointerX = (event?.clientX ?? rect.left + rect.width / 2) - rect.left + viewport.scrollLeft
  const pointerY = (event?.clientY ?? rect.top + rect.height / 2) - rect.top + viewport.scrollTop
  zoom.value = nextZoom
  nextTick(() => {
    viewport.scrollLeft = (pointerX / oldZoom) * nextZoom - (event?.clientX ?? rect.left + rect.width / 2) + rect.left
    viewport.scrollTop = (pointerY / oldZoom) * nextZoom - (event?.clientY ?? rect.top + rect.height / 2) + rect.top
  })
}

function onWheel(event) {
  event.preventDefault()
  changeZoom(event.deltaY < 0 ? 0.08 : -0.08, event)
}

function fitCanvas() {
  const viewport = viewportRef.value
  if (!viewport || !venue.value) return
  const rawWidth = venue.value.gridColumns * cellSize.value
  const rawHeight = venue.value.gridRows * cellSize.value
  zoom.value = Math.min(
    1.2,
    Math.max(0.25, (viewport.clientWidth - 80) / rawWidth),
    Math.max(0.25, (viewport.clientHeight - 80) / rawHeight),
  )
  nextTick(centerCanvas)
}

function centerCanvas() {
  const viewport = viewportRef.value
  if (!viewport) return
  viewport.scrollLeft = Math.max(0, (viewport.scrollWidth - viewport.clientWidth) / 2)
  viewport.scrollTop = Math.max(0, (viewport.scrollHeight - viewport.clientHeight) / 2)
}

function startPan(event) {
  if (event.button !== 0 || !viewportRef.value) return
  panning.value = true
  panStartX = event.clientX
  panStartY = event.clientY
  panScrollLeft = viewportRef.value.scrollLeft
  panScrollTop = viewportRef.value.scrollTop
  window.addEventListener('mousemove', movePan)
  window.addEventListener('mouseup', stopPan)
  event.preventDefault()
}

function movePan(event) {
  if (!panning.value || !viewportRef.value) return
  viewportRef.value.scrollLeft = panScrollLeft - (event.clientX - panStartX)
  viewportRef.value.scrollTop = panScrollTop - (event.clientY - panStartY)
}

function stopPan() {
  panning.value = false
  window.removeEventListener('mousemove', movePan)
  window.removeEventListener('mouseup', stopPan)
}

onBeforeUnmount(stopPan)
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="venue ? `预览场馆：${venue.name}` : '预览场馆'"
    width="88vw"
    top="5vh"
    append-to-body
    destroy-on-close
    @opened="fitCanvas"
  >
    <div class="preview-shell" v-loading="loading">
      <div class="preview-toolbar">
        <span v-if="venue">
          {{ venue.gridRows }} 行 × {{ venue.gridColumns }} 列 · {{ venue.elements.length }} 个元素
        </span>
        <span class="toolbar-spacer" />
        <el-button-group>
          <el-button :icon="ZoomOut" @click="changeZoom(-0.1)" />
          <el-button class="zoom-value">{{ Math.round(zoom * 100) }}%</el-button>
          <el-button :icon="ZoomIn" @click="changeZoom(0.1)" />
        </el-button-group>
        <el-button :icon="Refresh" @click="fitCanvas">适应窗口</el-button>
      </div>
      <div
        ref="viewportRef"
        class="preview-viewport"
        :class="{ panning }"
        @mousedown="startPan"
        @wheel="onWheel"
      >
        <div class="preview-content">
          <div v-if="venue" class="preview-canvas" :style="canvasStyle">
            <div
              v-for="element in venue.elements"
              :key="`${element.row}-${element.column}-${element.code}-${element.type}`"
              class="preview-element"
              :class="`type-${element.type.toLowerCase()}`"
              :style="elementStyle(element)"
              :title="[element.code, element.label, typeLabel(element.type)].filter(Boolean).join(' · ')"
            >
              <span>{{ element.label || element.code || typeLabel(element.type) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.preview-shell {
  height: 76vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #d7e2ef;
  border-radius: 12px;
}

.preview-toolbar {
  min-height: 52px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  color: #64748b;
  background: #fff;
  border-bottom: 1px solid #e3eaf2;
  font-size: 12px;
}

.toolbar-spacer {
  flex: 1;
}

.zoom-value {
  width: 62px;
  pointer-events: none;
}

.preview-viewport {
  flex: 1;
  min-height: 0;
  overflow: auto;
  cursor: grab;
  background:
    linear-gradient(#e4ebf3 1px, transparent 1px),
    linear-gradient(90deg, #e4ebf3 1px, transparent 1px), #f3f7fb;
  background-size: 24px 24px;
}

.preview-viewport.panning {
  cursor: grabbing;
  user-select: none;
}

.preview-content {
  width: max-content;
  min-width: 100%;
  height: max-content;
  min-height: 100%;
  display: grid;
  place-items: center;
  padding: 38px;
}

.preview-canvas {
  position: relative;
  flex: none;
  overflow: hidden;
  background:
    linear-gradient(#edf2f7 1px, transparent 1px),
    linear-gradient(90deg, #edf2f7 1px, transparent 1px), #fff;
  background-size: var(--preview-cell) var(--preview-cell);
  border: 1px solid #ccd8e7;
  box-shadow: 0 16px 38px rgba(28, 65, 113, 0.18);
}

.preview-element {
  position: absolute;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid;
  font-size: max(7px, calc(var(--preview-cell) * 0.23));
  line-height: 1.1;
  text-align: center;
}

.preview-element span {
  max-width: 100%;
  padding: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-aisle {
  border-style: dashed;
}

.type-wall {
  background-image: repeating-linear-gradient(
    45deg,
    rgba(255, 255, 255, 0.15) 0,
    rgba(255, 255, 255, 0.15) 5px,
    transparent 5px,
    transparent 10px
  );
}

.type-stage {
  font-weight: 700;
  letter-spacing: 0.2em;
}
</style>
