import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('排座工作台提供三段模式且发布版本只读', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /workbenchMode\s*=\s*ref\('seating'\)/)
  assert.match(source, /label="排座模式"/)
  assert.match(source, /label="布局模式"/)
  assert.match(source, /label="区域模式"/)
  assert.doesNotMatch(source, /label="布局编辑模式"/)
  assert.doesNotMatch(source, /区域标记模式/)
  assert.match(source, /:disabled="readonlyMode"/)
})

test('工作台按模式拆分保存入口并清空品牌占位', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /showAssignmentSave/)
  assert.match(source, /保存排座/)
  assert.match(source, /workspaceBusy/)
  assert.match(source, /v-loading="workspaceBusy"/)
  assert.match(source, /saveStatusText/)
  assert.match(source, /seatCount/)
  assert.match(source, />\s*座位数\s*</)
  assert.doesNotMatch(source, /<span class="brand-mark">席<\/span>/)
  assert.doesNotMatch(source, /会议排座助手<\/strong>/)
  assert.match(source, /class="brand-slot"/)
})

test('工作台和画布支持取消已选人员与区域', async () => {
  const workbenchSource = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')
  const canvasSource = await readFile(new URL('../src/components/VenueCanvas.vue', import.meta.url), 'utf8')

  assert.match(workbenchSource, /clearCanvasSelection/)
  assert.match(workbenchSource, /store\.selectedParticipantId === person\?\.id/)
  assert.match(workbenchSource, /markerDraft\.id === item\.id/)
  assert.match(canvasSource, /canvas-clear/)
  assert.match(canvasSource, /@click="onCanvasClear"/)
  assert.match(canvasSource, /@click\.stop="onSeatClick\(element\)"/)
})

test('布局编辑模式复用编辑器并保存会议布局', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /VenueLayoutEditor/)
  assert.match(source, /saveMeetingLayout/)
  assert.match(source, /updateMeetingLayout/)
  assert.match(source, /save-label="保存布局"/)
  assert.match(source, /class="canvas-zoom-controls"/)
  assert.match(source, /v-if="workbenchMode !== 'layout'"/)
  assert.doesNotMatch(source, /save-label="保存会议布局"/)
})

test('布局模式带未保存改动时切换模式会提示并可放弃草稿', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /layoutDirty/)
  assert.match(source, /changeWorkbenchMode/)
  assert.match(source, /ElMessageBox\.confirm/)
  assert.match(source, /继续编辑/)
  assert.match(source, /放弃修改/)
  assert.match(source, /离开布局模式/)
  assert.doesNotMatch(source, /离开布局编辑模式/)
  assert.match(source, /:model-value="workbenchMode"/)
  assert.match(source, /@change="changeWorkbenchMode"/)
})

test('保存布局时先冻结布局草稿，避免排座保存刷新覆盖删除结果', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /const layoutSnapshot = cloneLayout\(layoutDraft\.value\)/)
  assert.match(source, /layoutSaving\.value = true[\s\S]*saveDraft\(true\)/)
  assert.match(source, /gridRows:\s*layoutSnapshot\.gridRows/)
  assert.match(source, /gridColumns:\s*layoutSnapshot\.gridColumns/)
  assert.match(source, /layoutSnapshot\.elements\.map/)
  assert.doesNotMatch(source, /gridRows:\s*layoutDraft\.value\.gridRows/)
  assert.doesNotMatch(source, /layoutDraft\.value\.elements\.map/)
})

test('布局编辑器双击删除走统一删除保护入口', async () => {
  const source = await readFile(new URL('../src/components/VenueLayoutEditor.vue', import.meta.url), 'utf8')

  assert.match(source, /protectedElementIds/)
  assert.match(source, /requestDeleteElement/)
  assert.match(source, /@dblclick\.stop="requestDeleteElement\(element\)"/)
})

test('布局编辑器鼠标样式保持轻量且只在框选或拖动时变化', async () => {
  const source = await readFile(new URL('../src/components/VenueLayoutEditor.vue', import.meta.url), 'utf8')

  assert.match(source, /:class="\{ selecting: Boolean\(drawing\) \}"/)
  assert.match(source, /\.designer-canvas\s*\{[\s\S]*cursor:\s*default/)
  assert.match(source, /\.designer-canvas\.selecting\s*\{[\s\S]*cursor:\s*crosshair/)
  assert.match(source, /\.layout-element\s*\{[\s\S]*cursor:\s*pointer/)
  assert.match(source, /\.layout-element\.dragging\s*\{[\s\S]*cursor:\s*grabbing/)
})
