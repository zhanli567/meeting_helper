<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Delete, MagicStick } from '@element-plus/icons-vue'
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
const unit = 25

const selected = computed(() =>
  elements.value.find((element) => element.localId === selectedId.value),
)
const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${config.gridColumns}, ${unit}px)`,
  gridTemplateRows: `repeat(${config.gridRows}, ${unit}px)`,
  width: `${config.gridColumns * unit}px`,
  height: `${config.gridRows * unit}px`,
}))

const tools: Array<{ type: ElementType | 'ERASER'; label: string; color: string }> = [
  { type: 'SEAT', label: '座位', color: '#ffffff' },
  { type: 'AISLE', label: '走廊', color: '#f1f5f9' },
  { type: 'WALL', label: '墙壁', color: '#64748b' },
  { type: 'DOOR', label: '门', color: '#fed7aa' },
  { type: 'STAGE', label: '舞台', color: '#25324a' },
  { type: 'TABLE', label: '桌子', color: '#c6a477' },
  { type: 'STAIR', label: '楼梯', color: '#cbd5e1' },
  { type: 'LABEL', label: '文字', color: '#eef2f7' },
  { type: 'ERASER', label: '橡皮擦', color: '#fee2e2' },
]

function defaults(type: ElementType) {
  const map: Record<ElementType, Partial<DraftElement>> = {
    SEAT: { assignable: true, walkable: false, capacity: 1, backgroundColor: '#ffffff' },
    AISLE: { assignable: false, walkable: true, capacity: 0, backgroundColor: '#f8fafc' },
    WALL: { assignable: false, walkable: false, capacity: 0, backgroundColor: '#64748b' },
    DOOR: { assignable: false, walkable: true, capacity: 0, backgroundColor: '#fed7aa' },
    STAIR: { assignable: false, walkable: true, capacity: 0, backgroundColor: '#cbd5e1' },
    STAGE: { assignable: false, walkable: true, capacity: 0, backgroundColor: '#25324a' },
    TABLE: { assignable: false, walkable: false, capacity: 0, backgroundColor: '#c6a477' },
    SCREEN: { assignable: false, walkable: false, capacity: 0, backgroundColor: '#334155' },
    PODIUM: { assignable: false, walkable: false, capacity: 0, backgroundColor: '#9a6c3b' },
    LABEL: { assignable: false, walkable: true, capacity: 0, backgroundColor: '#eef2f7' },
    EMPTY: { assignable: false, walkable: false, capacity: 0, backgroundColor: '#ffffff' },
  }
  return map[type]
}

function occupiedAt(row: number, column: number) {
  return elements.value.find(
    (element) =>
      row >= element.row &&
      row < element.row + element.rowSpan &&
      column >= element.column &&
      column < element.column + element.columnSpan,
  )
}

function place(row: number, column: number) {
  const existing = occupiedAt(row, column)
  if (selectedTool.value === 'ERASER') {
    if (existing)
      elements.value = elements.value.filter((item) => item.localId !== existing.localId)
    return
  }
  if (existing) {
    selectedId.value = existing.localId
    return
  }
  const actualRowSpan = Math.min(rowSpan.value, config.gridRows - row + 1)
  const actualColumnSpan = Math.min(columnSpan.value, config.gridColumns - column + 1)
  for (let checkRow = row; checkRow < row + actualRowSpan; checkRow++) {
    for (let checkColumn = column; checkColumn < column + actualColumnSpan; checkColumn++) {
      if (occupiedAt(checkRow, checkColumn)) {
        ElMessage.warning('目标区域已有元素')
        return
      }
    }
  }
  const type = selectedTool.value
  const preset = defaults(type)
  const element: DraftElement = {
    localId: crypto.randomUUID(),
    type,
    row,
    column,
    rowSpan: actualRowSpan,
    columnSpan: actualColumnSpan,
    rotation: 0,
    capacity: preset.capacity ?? 0,
    assignable: preset.assignable ?? false,
    walkable: preset.walkable ?? false,
    backgroundColor: preset.backgroundColor || '#ffffff',
    borderColor: '#94a3b8',
    label: tools.find((tool) => tool.type === type)?.label,
  }
  if (type === 'SEAT')
    element.code = `座位${String(elements.value.filter((item) => item.type === 'SEAT').length + 1).padStart(3, '0')}`
  elements.value.push(element)
  selectedId.value = element.localId
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

function elementStyle(element: DraftElement) {
  return {
    gridRow: `${element.row} / span ${element.rowSpan}`,
    gridColumn: `${element.column} / span ${element.columnSpan}`,
    backgroundColor: element.backgroundColor,
    borderColor: element.borderColor,
    color: ['WALL', 'STAGE'].includes(element.type) ? '#fff' : '#334155',
  }
}

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
        <span>网格是编辑标尺，元素可以跨越多个基础格</span>
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
              <el-form-item label="行数">
                <el-input-number
                  v-model="config.gridRows"
                  :min="5"
                  :max="60"
                  controls-position="right"
                />
              </el-form-item>
              <el-form-item label="列数">
                <el-input-number
                  v-model="config.gridColumns"
                  :min="5"
                  :max="80"
                  controls-position="right"
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
          <div class="span-config">
            <label>高度<el-input-number v-model="rowSpan" :min="1" :max="12" size="small" /></label>
            <label
              >宽度<el-input-number v-model="columnSpan" :min="1" :max="20" size="small"
            /></label>
          </div>
          <el-button :icon="MagicStick" @click="renumberSeats">按行批量编号座位</el-button>
        </section>

        <section v-if="selected">
          <span class="eyebrow">SELECTED ELEMENT</span>
          <h2>元素属性</h2>
          <el-form label-position="top" size="small">
            <el-form-item label="显示编号">
              <el-input v-model="selected.code" />
            </el-form-item>
            <el-form-item label="显示名称">
              <el-input v-model="selected.label" />
            </el-form-item>
            <div class="field-grid">
              <el-form-item label="高度">
                <el-input-number v-model="selected.rowSpan" :min="1" controls-position="right" />
              </el-form-item>
              <el-form-item label="宽度">
                <el-input-number v-model="selected.columnSpan" :min="1" controls-position="right" />
              </el-form-item>
            </div>
            <el-form-item label="填充颜色">
              <el-color-picker v-model="selected.backgroundColor" />
            </el-form-item>
          </el-form>
          <el-button
            type="danger"
            plain
            :icon="Delete"
            @click="elements = elements.filter((item) => item.localId !== selectedId)"
          >
            删除元素
          </el-button>
        </section>
      </aside>

      <section class="designer-canvas-area">
        <div class="designer-help">
          <strong>点击网格放置元素</strong>
          <span>再次点击已有元素可选中；使用橡皮擦删除。舞台和桌子可通过宽高跨越多个格子。</span>
        </div>
        <div class="designer-scroll">
          <div class="designer-grid" :style="gridStyle">
            <button
              v-for="index in config.gridRows * config.gridColumns"
              :key="index"
              class="grid-cell"
              :style="{
                gridRow: Math.floor((index - 1) / config.gridColumns) + 1,
                gridColumn: ((index - 1) % config.gridColumns) + 1,
              }"
              @click="
                place(
                  Math.floor((index - 1) / config.gridColumns) + 1,
                  ((index - 1) % config.gridColumns) + 1,
                )
              "
            />
            <button
              v-for="element in elements"
              :key="element.localId"
              class="draft-element"
              :class="{ selected: element.localId === selectedId }"
              :style="elementStyle(element)"
              @click.stop="selectedId = element.localId"
            >
              {{ element.code || element.label }}
            </button>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.designer-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.back-button {
  color: rgba(255, 255, 255, 0.82);
}

.designer-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
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
  border-bottom: 1px solid #e5e9ef;
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
  padding: 7px;
  color: #475569;
  background: #fff;
  border: 1px solid #dce3ec;
  border-radius: 7px;
  cursor: pointer;
}

.tool-grid button.active {
  color: #214f90;
  background: #edf4ff;
  border-color: #6f91c4;
}

.tool-grid i {
  width: 12px;
  height: 12px;
  border: 1px solid #94a3b8;
  border-radius: 2px;
}

.span-config {
  margin: 12px 0;
}

.span-config label {
  display: grid;
  gap: 4px;
  color: #718096;
  font-size: 11px;
}

.designer-canvas-area {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.designer-help {
  min-height: 54px;
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid var(--line);
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
  overflow: auto;
  padding: 36px;
}

.designer-grid {
  position: relative;
  display: grid;
  margin: 0 auto;
  background: #fff;
  box-shadow: var(--shadow);
}

.grid-cell {
  padding: 0;
  background: #fff;
  border: 0;
  box-shadow:
    1px 0 #e8edf3 inset,
    0 1px #e8edf3 inset;
  cursor: crosshair;
}

.grid-cell:hover {
  background: #edf4ff;
}

.draft-element {
  z-index: 2;
  overflow: hidden;
  padding: 1px;
  border: 1px solid;
  font-size: 7px;
  text-align: center;
  cursor: pointer;
}

.draft-element.selected {
  z-index: 3;
  box-shadow: 0 0 0 2px #2563eb;
}
</style>
