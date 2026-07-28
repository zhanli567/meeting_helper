import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('排座工作台提供三段模式且发布版本只读', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /workbenchMode\s*=\s*ref\('seating'\)/)
  assert.match(source, /label="排座模式"/)
  assert.match(source, /label="布局编辑模式"/)
  assert.match(source, /label="区域标记模式"/)
  assert.match(source, /:disabled="readonlyMode"/)
})

test('布局编辑模式复用编辑器并保存会议布局', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /VenueLayoutEditor/)
  assert.match(source, /saveMeetingLayout/)
  assert.match(source, /updateMeetingLayout/)
})

test('布局编辑模式带未保存改动时切换模式会提示并可放弃草稿', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /layoutDirty/)
  assert.match(source, /changeWorkbenchMode/)
  assert.match(source, /ElMessageBox\.confirm/)
  assert.match(source, /继续编辑/)
  assert.match(source, /放弃修改/)
  assert.match(source, /:model-value="workbenchMode"/)
  assert.match(source, /@change="changeWorkbenchMode"/)
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
