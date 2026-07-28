import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('画布渲染区域标记中心标签并支持双击切换座位', async () => {
  const source = await readFile(new URL('../src/components/VenueCanvas.vue', import.meta.url), 'utf8')

  assert.match(source, /region-label/)
  assert.match(source, /markerMode/)
  assert.match(source, /@dblclick\.stop="onMarkerSeatToggle\(element\)"/)
  assert.match(source, /marker-seat-toggle/)
})

test('区域标记面板提供名称颜色座位数和保存删除操作', async () => {
  const source = await readFile(new URL('../src/components/RegionMarkerPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /标记名称/)
  assert.match(source, /已选座位/)
  assert.match(source, /添加座位/)
  assert.match(source, /移除座位/)
  assert.match(source, /删除标记/)
})

test('工作台区域标记模式保存 RESERVED 区域', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /saveReservedAreas/)
  assert.match(source, /RegionMarkerPanel/)
  assert.match(source, /markerSelection/)
})
