<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Delete, MagicStick, Rank } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage } from '@/api/http'
import type { ElementType } from '@/types/workspace'

interface DraftElement {
  localId: string
  type: ElementType
  code?: string
  label?: string
  row: number
  column: number
  rowSpan: number
  columnSpan: number
  rotation: number
  capacity: number
  assignable: boolean
  walkable: boolean
  groupCode?: string
  groupLabel?: string
  sequenceNo?: number
  backgroundColor: string
  borderColor: string
}

interface GridPoint {
  row: number
  column: number
}

interface DrawState {
  start: GridPoint
  current: GridPoint
  pointerId: number
}

const router = useRouter()
const config = reactive({
  name: '自定义会议室',
  description: '',
  gridRows: 16,
  gridColumns: 30,
  cellSize: 34,
  frontDirection: 'TOP',
})
const selectedTool = ref<ElementType | 'ERASER'>('SEAT')
const rowSpan = ref(1)
const columnSpan = ref(1)
const elements = ref<DraftElement[]>([])
const selectedId = ref<string>()
const saving = ref(false)
const drawing = ref<DrawState>()
const panMode = ref(false)
const isPanning = ref(false)
const scrollRef = ref<HTMLElement>()
const unit = 28
let panStartX = 0
let panStartY = 0
let panScrollLeft = 0
let panScrollTop = 0

const selected = computed(() =>
  elements.value.find((element) => element.localId === selectedId.value),
)
const gridStyle = computed(() => ({
  width: `${config.gridColumns * unit}px`,
  height: `${config.gridRows * unit}px`,
  '--designer-unit': `${unit}px`,
}))

const tools: Array<{ type: ElementType | 'ERASER'; label: string; color: string }> = [
  { type: 'SEAT', label: '座位', color: '#ffffff' },
  { type: 'AISLE', label: '走廊', color: '#eff6ff' },
  { type: 'WALL', label: '墙壁', color: '#7c9ac4' },
  { type: 'DOOR', label: '门', color: '#bfdbfe' },
  { type: 'STAGE', label: '舞台', color: '#dbeafe' },
  { type: 'TABLE', label: '桌子', color: '#bae6fd' },
  { type: 'STAIR', label: '楼梯', color: '#dbeafe' },
  { type: 'LABEL', label: '文字', color: '#f1f5f9' },
  { type: 'ERASER', label: '橡皮擦', color: '#fee2e2' },
]

const elementTools = computed(() =>
  tools.filter((tool): tool is { type: ElementType; label: string; color: string } =>
    tool.type !== 'ERASER',
  ),
)

function defaults(type: ElementType) {
  const map: Record<ElementType, Partial<DraftElement>> = {
    SEAT: {
      assignable: true,
      walkable: false,
      capacity: 1,
      backgroundColor: '#ffffff',
    },
    AISLE: {
      assignable: false,
      walkable: true,
      capacity: 0,
      backgroundColor: '#eff6ff',
    },
    WALL: {
      assignable: false,
      walkable: false,
      capacity: 0,
      backgroundColor: '#7c9ac4',
    },
    DOOR: {
      assignable: false,
      walkable: true,
      capacity: 0,
      backgroundColor: '#bfdbfe',
    },
    STAIR: {
      assignable: false,
      walkable: true,
      capacity: 0,
      backgroundColor: '#dbeafe',
    },
    STAGE: {
      assignable: false,
      walkable: true,
      capacity: 0,
      backgroundColor: '#dbeafe',
    },
    TABLE: {
      assignable: false,
      walkable: false,
      capacity: 0,
      backgroundColor: '#bae6fd',
    },
    SCREEN: {
      assignable: false,
      walkable: false,
      capacity: 0,
      backgroundColor: '#bfdbfe',
    },
    PODIUM: {
      assignable: false,
      walkable: false,
      capacity: 0,
      backgroundColor: '#93c5fd',
    },
    LABEL: {
      assignable: false,
      walkable: true,
      capacity: 0,
      backgroundColor: '#f1f5f9',
    },
    EMPTY: {
      assignable: false,
      walkable: false,
      capacity: 0,
      backgroundColor: '#ffffff',
    },
  }
  return map[type]
}

function occupiedAt(row: number, column: number, excludeId?: string) {
  return elements.value.find(
    (element) =>
      element.localId !== excludeId &&
      row >= element.row &&
      row < element.row + element.rowSpan &&
      column >= element.column &&
      column < element.column + element.columnSpan,
  )
}

function rectCollides(
  row: number,
  column: number,
  height: number,
  width: number,
  excludeId?: string,
) {
  for (let checkRow = row; checkRow < row + height; checkRow++) {
    for (let checkColumn = column; checkColumn < column + width; checkColumn++) {
      if (occupiedAt(checkRow, checkColumn, excludeId)) return true
    }
  }
  return false
}

function pointFromEvent(event: PointerEvent): GridPoint | undefined {
  const grid = event.currentTarget as HTMLElement
  const rect = grid.getBoundingClientRect()
  const column = Math.floor((event.clientX - rect.left) / unit) + 1
  const row = Math.floor((event.clientY - rect.top) / unit) + 1
  if (row < 1 || row > config.gridRows || column < 1 || column > config.gridColumns) return
  return { row, column }
}

function normalizedRect(start: GridPoint, end: GridPoint) {
  const row = Math.min(start.row, end.row)
  const column = Math.min(start.column, end.column)
  return {
    row,
    column,
    rowSpan: Math.abs(start.row - end.row) + 1,
    columnSpan: Math.abs(start.column - end.column) + 1,
  }
}

const previewRect = computed(() => {
  if (!drawing.value) return undefined
  return normalizedRect(drawing.value.start, drawing.value.current)
})

const previewStyle = computed(() => {
  if (!previewRect.value) return {}
  return {
    top: `${(previewRect.value.row - 1) * unit}px`,
    left: `${(previewRect.value.column - 1) * unit}px`,
    width: `${previewRect.value.columnSpan * unit}px`,
    height: `${previewRect.value.rowSpan * unit}px`,
  }
})

function onGridPointerDown(event: PointerEvent) {
  if (panMode.value || event.button === 1) {
    startPan(event)
    return
  }
  if (event.button !== 0) return
  const point = pointFromEvent(event)
  if (!point) return
  const existing = occupiedAt(point.row, point.column)
  if (existing && selectedTool.value !== 'ERASER') {
    selectedId.value = existing.localId
    return
  }
  selectedId.value = undefined
  drawing.value = { start: point, current: point, pointerId: event.pointerId }
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
  event.preventDefault()
}

function onGridPointerMove(event: PointerEvent) {
  if (!drawing.value || drawing.value.pointerId !== event.pointerId) return
  const point = pointFromEvent(event)
  if (point) drawing.value.current = point
}

function onGridPointerUp(event: PointerEvent) {
  if (!drawing.value || drawing.value.pointerId !== event.pointerId) return
  const state = drawing.value
  drawing.value = undefined
  ;(event.currentTarget as HTMLElement).releasePointerCapture(event.pointerId)
  const dragged = state.start.row !== state.current.row || state.start.column !== state.current.column
  const rect = normalizedRect(state.start, state.current)

  if (selectedTool.value === 'ERASER') {
    const before = elements.value.length
    elements.value = elements.value.filter(
      (element) =>
        element.row + element.rowSpan - 1 < rect.row ||
        element.row > rect.row + rect.rowSpan - 1 ||
        element.column + element.columnSpan - 1 < rect.column ||
        element.column > rect.column + rect.columnSpan - 1,
    )
    const removed = before - elements.value.length
    if (removed) ElMessage.success(`已擦除 ${removed} 个元素`)
    return
  }

  if (selectedTool.value === 'SEAT' && dragged) {
    let created = 0
    for (let row = rect.row; row < rect.row + rect.rowSpan; row++) {
      for (let column = rect.column; column < rect.column + rect.columnSpan; column++) {
        if (!occupiedAt(row, column)) {
          createElement('SEAT', row, column, 1, 1)
          created++
        }
      }
    }
    if (created) ElMessage.success(`已批量放置 ${created} 个座位`)
    return
  }

  const height = dragged
    ? rect.rowSpan
    : Math.min(rowSpan.value, config.gridRows - rect.row + 1)
  const width = dragged
    ? rect.columnSpan
    : Math.min(columnSpan.value, config.gridColumns - rect.column + 1)
  if (rectCollides(rect.row, rect.column, height, width)) {
    ElMessage.warning('绘制区域与已有元素重叠')
    return
  }
  createElement(selectedTool.value, rect.row, rect.column, height, width)
}

function createElement(
  type: ElementType,
  row: number,
  column: number,
  height: number,
  width: number,
) {
  const preset = defaults(type)
  const element: DraftElement = {
    localId: crypto.randomUUID(),
    type,
    row,
    column,
    rowSpan: height,
    columnSpan: width,
    rotation: 0,
    capacity: preset.capacity ?? 0,
    assignable: preset.assignable ?? false,
    walkable: preset.walkable ?? false,
    backgroundColor: preset.backgroundColor || '#ffffff',
    borderColor: '#93b4df',
    label: elementTools.value.find((tool) => tool.type === type)?.label,
  }
  if (type === 'SEAT') {
    element.code = `座位${String(elements.value.filter((item) => item.type === 'SEAT').length + 1).padStart(3, '0')}`
  }
  elements.value.push(element)
  selectedId.value = element.localId
}

function elementStyle(element: DraftElement) {
  return {
    top: `${(element.row - 1) * unit}px`,
    left: `${(element.column - 1) * unit}px`,
    width: `${element.columnSpan * unit}px`,
    height: `${element.rowSpan * unit}px`,
    backgroundColor: element.backgroundColor,
    borderColor: element.borderColor,
    color: '#17365f',
  }
}

const editorStyle = computed(() => {
  if (!selected.value) return {}
  const width = 272
  const preferredLeft =
    (selected.value.column - 1 + selected.value.columnSpan) * unit + 10
  const left =
    preferredLeft + width <= config.gridColumns * unit
      ? preferredLeft
      : Math.max(8, (selected.value.column - 1) * unit - width - 10)
  const top = Math.min(
    Math.max(8, (selected.value.row - 1) * unit),
    Math.max(8, config.gridRows * unit - 350),
  )
  return { left: `${left}px`, top: `${top}px` }
})

function updateSelectedSpan(axis: 'rowSpan' | 'columnSpan', value?: number) {
  if (!selected.value || !value) return
  const nextHeight = axis === 'rowSpan' ? value : selected.value.rowSpan
  const nextWidth = axis === 'columnSpan' ? value : selected.value.columnSpan
  if (
    selected.value.row + nextHeight - 1 > config.gridRows ||
    selected.value.column + nextWidth - 1 > config.gridColumns ||
    rectCollides(
      selected.value.row,
      selected.value.column,
      nextHeight,
      nextWidth,
      selected.value.localId,
    )
  ) {
    ElMessage.warning('调整后的区域超出画布或与其他元素重叠')
    return
  }
  selected.value[axis] = value
}

function updateSelectedPosition(axis: 'row' | 'column', value?: number) {
  if (!selected.value || !value) return
  const nextRow = axis === 'row' ? value : selected.value.row
  const nextColumn = axis === 'column' ? value : selected.value.column
  if (
    nextRow + selected.value.rowSpan - 1 > config.gridRows ||
    nextColumn + selected.value.columnSpan - 1 > config.gridColumns ||
    rectCollides(
      nextRow,
      nextColumn,
      selected.value.rowSpan,
      selected.value.columnSpan,
      selected.value.localId,
    )
  ) {
    ElMessage.warning('移动后的区域超出画布或与其他元素重叠')
    return
  }
  selected.value[axis] = value
}

function changeSelectedType(type: ElementType) {
  if (!selected.value) return
  const preset = defaults(type)
  selected.value.type = type
  selected.value.assignable = preset.assignable ?? false
  selected.value.walkable = preset.walkable ?? false
  selected.value.capacity = preset.capacity ?? 0
  selected.value.backgroundColor = preset.backgroundColor || selected.value.backgroundColor
  selected.value.label = elementTools.value.find((tool) => tool.type === type)?.label
}

function removeSelected() {
  elements.value = elements.value.filter((item) => item.localId !== selectedId.value)
  selectedId.value = undefined
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
  const seatRows = Array.from(
    new Set(
      elements.value.filter((element) => element.type === 'SEAT').map((element) => element.row),
    ),
  ).sort((a, b) => a - b)
  seatRows.forEach((gridRow, rowIndex) => {
    elements.value
      .filter((element) => element.type === 'SEAT' && element.row === gridRow)
      .sort((a, b) => a.column - b.column)
      .forEach((element, seatIndex) => {
        element.code = `${rowIndex + 1}排${String(seatIndex + 1).padStart(2, '0')}`
        element.groupCode = `ROW_${rowIndex + 1}`
        element.groupLabel = `${rowIndex + 1}排`
        element.sequenceNo = seatIndex + 1
      })
  })
  ElMessage.success('已按从上到下、从左到右重新编号')
}

function startPan(event: MouseEvent | PointerEvent) {
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

async function save() {
  if (!config.name.trim() || !elements.value.length) {
    ElMessage.warning('请填写场馆名称并至少放置一个元素')
    return
  }
  saving.value = true
  try {
    await meetingApi.createVenue({
      ...config,
      elements: elements.value.map(({ localId, ...element }) => {
        void localId
        return element
      }),
    })
    ElMessage.success('场馆模板已保存')
    router.push('/venues')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="app-page designer-page">
    <header class="app-header">
      <el-button text class="back-button" :icon="ArrowLeft" @click="router.push('/venues')">
        返回场馆库
      </el-button>
      <span class="header-divider" />
      <div class="brand-copy">
        <strong>自定义场馆设计器</strong>
        <span>拖动绘制区域，选中元素后在旁边直接修改</span>
      </div>
      <span class="header-spacer" />
      <el-button type="primary" :loading="saving" @click="save">保存为场馆模板</el-button>
    </header>

    <main class="designer-layout">
      <aside class="designer-sidebar">
        <section>
          <span class="eyebrow">BASIC SETTINGS</span>
          <h2>场馆信息</h2>
          <el-form label-position="top" size="small">
            <el-form-item label="场馆名称">
              <el-input v-model="config.name" />
            </el-form-item>
            <el-form-item label="说明">
              <el-input v-model="config.description" type="textarea" :rows="2" />
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
        </section>

        <section>
          <span class="eyebrow">ELEMENT TOOLS</span>
          <h2>绘制元素</h2>
          <div class="tool-grid">
            <button
              v-for="tool in tools"
              :key="tool.type"
              :class="{ active: selectedTool === tool.type }"
              @click="selectedTool = tool.type"
            >
              <i :style="{ backgroundColor: tool.color }" />
              {{ tool.label }}
            </button>
          </div>
          <p class="tool-tip">
            拖动可画出整片区域；座位拖动时会生成多个独立座位。橡皮擦支持框选批量删除。
          </p>
          <div class="span-config">
            <label
              >单击默认高度
              <el-input-number v-model="rowSpan" :min="1" :max="12" size="small" />
            </label>
            <label
              >单击默认宽度
              <el-input-number v-model="columnSpan" :min="1" :max="20" size="small" />
            </label>
          </div>
          <el-button :icon="MagicStick" @click="renumberSeats">按行批量编号座位</el-button>
        </section>
      </aside>

      <section class="designer-canvas-area">
        <div class="designer-help">
          <div>
            <strong>在网格上按住并拖动绘制</strong>
            <span>单击已有元素会在元素旁打开属性卡；画布大小不会裁掉已有元素。</span>
          </div>
          <el-button
            :type="panMode ? 'primary' : 'default'"
            :icon="Rank"
            @click="panMode = !panMode"
          >
            {{ panMode ? '抓手已开启' : '抓手平移' }}
          </el-button>
        </div>
        <div
          ref="scrollRef"
          class="designer-scroll"
          :class="{ panning: isPanning }"
          @mousedown.self="startPan"
        >
          <div
            class="designer-grid"
            :class="{ 'pan-mode': panMode }"
            :style="gridStyle"
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
            >
              {{ element.code || element.label }}
            </div>

            <div
              v-if="previewRect"
              class="draw-preview"
              :class="{ erasing: selectedTool === 'ERASER' }"
              :style="previewStyle"
            >
              {{ selectedTool === 'ERASER' ? '松开擦除' : '松开完成' }}
            </div>

            <div
              v-if="selected"
              class="element-editor"
              :style="editorStyle"
              @pointerdown.stop
              @click.stop
            >
              <div class="editor-heading">
                <div>
                  <span>已选元素</span>
                  <strong>{{ selected.code || selected.label || '未命名元素' }}</strong>
                </div>
                <el-button text circle :icon="Delete" aria-label="删除元素" @click="removeSelected" />
              </div>

              <div class="editor-grid">
                <label class="full">
                  元素类型
                  <el-select
                    :model-value="selected.type"
                    size="small"
                    @change="changeSelectedType"
                  >
                    <el-option
                      v-for="tool in elementTools"
                      :key="tool.type"
                      :label="tool.label"
                      :value="tool.type"
                    />
                  </el-select>
                </label>
                <label>
                  起始行
                  <el-input-number
                    :model-value="selected.row"
                    :min="1"
                    :max="config.gridRows"
                    size="small"
                    @change="updateSelectedPosition('row', $event)"
                  />
                </label>
                <label>
                  起始列
                  <el-input-number
                    :model-value="selected.column"
                    :min="1"
                    :max="config.gridColumns"
                    size="small"
                    @change="updateSelectedPosition('column', $event)"
                  />
                </label>
                <label>
                  高度（格）
                  <el-input-number
                    :model-value="selected.rowSpan"
                    :min="1"
                    :max="config.gridRows"
                    size="small"
                    @change="updateSelectedSpan('rowSpan', $event)"
                  />
                </label>
                <label>
                  宽度（格）
                  <el-input-number
                    :model-value="selected.columnSpan"
                    :min="1"
                    :max="config.gridColumns"
                    size="small"
                    @change="updateSelectedSpan('columnSpan', $event)"
                  />
                </label>
                <label class="full">
                  显示编号
                  <el-input v-model="selected.code" size="small" placeholder="可选" />
                </label>
                <label class="full">
                  显示名称
                  <el-input v-model="selected.label" size="small" />
                </label>
                <label>
                  填充颜色
                  <el-color-picker v-model="selected.backgroundColor" />
                </label>
                <label>
                  边框颜色
                  <el-color-picker v-model="selected.borderColor" />
                </label>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
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

.designer-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 324px minmax(0, 1fr);
}

.designer-sidebar {
  overflow: auto;
  padding: 20px;
  background: #fff;
  border-right: 1px solid var(--line);
}

.designer-sidebar section {
  padding-bottom: 22px;
  margin-bottom: 22px;
  border-bottom: 1px solid #e0e9f4;
}

.designer-sidebar h2 {
  margin: 3px 0 14px;
  font-size: 15px;
}

.field-grid,
.span-config {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.tool-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 7px;
}

.tool-grid button {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 7px;
  color: #475d79;
  background: #fff;
  border: 1px solid #d8e4f3;
  border-radius: 8px;
  cursor: pointer;
}

.tool-grid button.active {
  color: #1d4ed8;
  background: #eaf2ff;
  border-color: #6ea0e5;
  box-shadow: inset 0 0 0 1px #93baf0;
}

.tool-grid i {
  width: 12px;
  height: 12px;
  border: 1px solid #8aa5c8;
  border-radius: 2px;
}

.tool-tip {
  margin: 10px 0 0;
  color: #7b8ba0;
  font-size: 10px;
  line-height: 1.6;
}

.span-config {
  margin: 12px 0;
}

.span-config label {
  display: grid;
  gap: 4px;
  color: #718096;
  font-size: 10px;
}

.designer-canvas-area {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.designer-help {
  min-height: 58px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 15px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid var(--line);
}

.designer-help > div {
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

.designer-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 54px;
  background: #edf5fc;
  cursor: grab;
}

.designer-scroll.panning {
  cursor: grabbing;
  user-select: none;
}

.designer-grid {
  position: relative;
  margin: 0 auto;
  background:
    linear-gradient(#dfe8f3 1px, transparent 1px),
    linear-gradient(90deg, #dfe8f3 1px, transparent 1px), #fff;
  background-size: var(--designer-unit) var(--designer-unit);
  box-shadow: 0 18px 42px rgba(37, 99, 235, 0.12);
  cursor: crosshair;
  touch-action: none;
}

.designer-grid.pan-mode {
  cursor: grab;
}

.draft-element {
  position: absolute;
  z-index: 2;
  display: grid;
  place-items: center;
  overflow: hidden;
  padding: 2px;
  border: 1px solid;
  font-size: 8px;
  line-height: 1.1;
  text-align: center;
  pointer-events: none;
}

.draft-element.selected {
  z-index: 4;
  box-shadow:
    0 0 0 3px #2563eb,
    0 6px 16px rgba(37, 99, 235, 0.22);
}

.draft-element.type-stage {
  font-weight: 700;
  letter-spacing: 0.12em;
}

.draw-preview {
  position: absolute;
  z-index: 8;
  display: grid;
  place-items: center;
  color: #1d4ed8;
  background: rgba(147, 197, 253, 0.38);
  border: 2px solid #2563eb;
  font-size: 10px;
  font-weight: 700;
  pointer-events: none;
}

.draw-preview.erasing {
  color: #b42318;
  background: rgba(254, 202, 202, 0.55);
  border-color: #ef4444;
}

.element-editor {
  width: 272px;
  position: absolute;
  z-index: 30;
  padding: 13px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid #9bbce8;
  border-radius: 13px;
  box-shadow: 0 18px 42px rgba(29, 78, 216, 0.2);
  cursor: default;
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
  display: grid;
  gap: 2px;
}

.editor-heading span {
  color: #75869c;
  font-size: 9px;
}

.editor-heading strong {
  color: #17365f;
  font-size: 13px;
}

.editor-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 9px;
}

.editor-grid label {
  min-width: 0;
  display: grid;
  gap: 4px;
  color: #63758d;
  font-size: 9px;
}

.editor-grid label.full {
  grid-column: 1 / -1;
}

.editor-grid :deep(.el-input-number) {
  width: 100%;
}
</style>
