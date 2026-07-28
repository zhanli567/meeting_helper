import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('画布渲染区域中心标签并支持双击切换座位', async () => {
  const source = await readFile(new URL('../src/components/VenueCanvas.vue', import.meta.url), 'utf8')

  assert.match(source, /region-label/)
  assert.match(source, /markerMode/)
  assert.match(source, /@dblclick\.stop="onMarkerSeatToggle\(element\)"/)
  assert.match(source, /marker-seat-toggle/)
  assert.match(source, /marker-rect-select/)
  assert.match(source, /markerSelectionRect/)
  assert.match(source, /startMarkerRectSelection/)
  assert.match(source, /selectReservedRegionFromSeat/)
  assert.match(source, /pointer-events:\s*none/)
  assert.doesNotMatch(source, /@click\.stop="emit\('marker-select', anchor\.source\)"/)
})

test('区域面板提供名称颜色座位数和保存删除操作', async () => {
  const source = await readFile(new URL('../src/components/RegionMarkerPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /区域名称/)
  assert.match(source, /已选座位/)
  assert.match(source, /已有区域/)
  assert.match(source, /新建区域/)
  assert.match(source, /删除区域/)
  assert.match(source, /availableColorSwatches/)
  assert.match(source, /saveCustomColor/)
  assert.match(source, /removeCustomColor/)
  assert.match(source, /textColorForBackground/)
  assert.doesNotMatch(source, /区域标记/)
  assert.doesNotMatch(source, /选择方式/)
  assert.doesNotMatch(source, /el-segmented/)
  assert.doesNotMatch(source, /添加座位/)
  assert.doesNotMatch(source, /移除座位/)
})

test('工作台区域模式支持框选创建并保存 RESERVED 区域', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /RegionCreateDialog/)
  assert.match(source, /openRegionCreateDialog/)
  assert.match(source, /createReservedAreaFromDialog/)
  assert.match(source, /mergeReservedAreaFromDialog/)
  assert.match(source, /saveReservedAreas/)
  assert.match(source, /RegionMarkerPanel/)
  assert.match(source, /markerSelection/)
  assert.match(source, /selectReservedMarker\(blockingItem\)/)
  assert.match(source, /deleteReservedMarker\(true\)/)
  assert.doesNotMatch(source, /区域标记/)
  assert.doesNotMatch(source, /markerSeatMode/)
})

test('区域创建弹窗复用本地自定义颜色能力', async () => {
  const source = await readFile(new URL('../src/components/RegionCreateDialog.vue', import.meta.url), 'utf8')

  assert.match(source, /创建区域/)
  assert.match(source, /合并到已有区域/)
  assert.match(source, /mergeTargetId/)
  assert.match(source, /markers/)
  assert.match(source, /mode/)
  assert.match(source, /emit\('merge'/)
  assert.match(source, /区域名称/)
  assert.match(source, /availableColorSwatches/)
  assert.match(source, /saveCustomColor/)
  assert.match(source, /removeCustomColor/)
  assert.match(source, /textColorForBackground/)
})
