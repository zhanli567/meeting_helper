import assert from 'node:assert/strict'
import { access, readFile } from 'node:fs/promises'
import test from 'node:test'

async function readSource(path) {
  return readFile(new URL(`../${path}`, import.meta.url), 'utf8')
}

test('编辑器支持画布拉伸、属性侧栏和无旋转交互', async () => {
  const source = await readSource('src/components/VenueLayoutEditor.vue')
  assert.match(source, /canvas-resize-east/)
  assert.match(source, /canvas-resize-south/)
  assert.match(source, /canvas-resize-corner/)
  assert.match(source, /<VenueElementPanel/)
  assert.match(source, /undo/)
  assert.match(source, /redo/)
  assert.doesNotMatch(source, /rotate|旋转/)
})

test('多格座位要求选择逐格或合并', async () => {
  const source = await readSource('src/components/VenueElementPicker.vue')
  assert.match(source, /逐格生成座位/)
  assert.match(source, /合并为一个座位/)
})

test('编辑器提供完整画布手势、八向元素缩放和事件清理契约', async () => {
  const source = await readSource('src/components/VenueLayoutEditor.vue')

  assert.match(source, /const CELL_SIZE = 44/)
  assert.match(source, /Math\.min\(2\.5,\s*Math\.max\(0\.25/)
  assert.match(source, /event\.button !== 2/)
  assert.match(source, /@wheel="onWheel"/)
  assert.match(source, /@contextmenu\.prevent/)
  assert.match(source, /resizeHandles = \['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'\]/)
  assert.match(source, /canPlaceRect/)
  assert.match(source, /canvasResizeConflict/)
  assert.match(source, /conflicts\.map\(\(element\) => element\.id\)/)
  assert.match(source, /pointerDeltaToGrid/)
  assert.match(source, /window\.addEventListener\('pointermove'/)
  assert.match(source, /window\.removeEventListener\('pointermove'/)
  assert.match(source, /onBeforeUnmount/)
})

test('元素上的右键事件仍能冒泡到画布执行平移', async () => {
  const source = await readSource('src/components/VenueLayoutEditor.vue')

  assert.match(source, /@pointerdown="startElementMove\(\$event,\s*element\)"/)
  assert.doesNotMatch(
    source,
    /@pointerdown\.stop="startElementMove\(\$event,\s*element\)"/,
  )
})

test('指针会话按 pointerId 互斥并统一释放指针捕获', async () => {
  const source = await readSource('src/components/VenueLayoutEditor.vue')

  assert.match(source, /activePointerId/)
  assert.match(source, /event\.pointerId !== activePointerId/)
  assert.match(source, /setPointerCapture/)
  assert.match(source, /releasePointerCapture/)
})

test('元素选择与属性编辑只使用通用模型字段并支持取消实时预览', async () => {
  const pickerSource = await readSource('src/components/VenueElementPicker.vue')
  const panelSource = await readSource('src/components/VenueElementPanel.vue')

  assert.match(pickerSource, /COMMON_ELEMENT_SUGGESTIONS/)
  assert.match(pickerSource, /自定义元素名称/)
  assert.match(pickerSource, /ELEMENT_KINDS\.GENERIC/)
  assert.match(panelSource, /kind/)
  assert.match(panelSource, /name/)
  assert.match(panelSource, /fillColor/)
  assert.match(panelSource, /borderColor/)
  assert.match(panelSource, /emit\('preview'/)
  assert.match(panelSource, /emit\('cancel'/)
  assert.doesNotMatch(panelSource, /capacity|assignable|walkable|code/)
})

test('新建流程复用编辑器并通过统一创建载荷完成保存', async () => {
  const source = await readSource('src/views/VenueCreateView.vue')

  assert.match(source, /<VenueLayoutEditor/)
  assert.match(source, /venueApi\.create\(toCreateVenuePayload\(info,\s*layout\)\)/)
  assert.match(source, /@update:model-value="updateLayout"/)
})

test('现有模板编辑保留本地布局并携带行版本保存', async () => {
  const source = await readSource('src/views/VenueLayoutEditorView.vue')

  assert.match(source, /venueApi\.detail/)
  assert.match(source, /venueApi\.layout/)
  assert.match(source, /venueApi\.updateLayout\(route\.params\.venueId/)
  assert.match(source, /elements:\s*layout\.elements\.map\(toElementPayload\)/)
  assert.match(source, /rowVersion:\s*rowVersion\.value/)
  assert.match(source, /status === 409/)
  assert.match(source, /loadFailed/)
  assert.match(source, /v-if="!loading && !loadFailed"/)
  assert.match(source, /<VenueLayoutEditor/)
})

test('旧设计器已由可复用编辑器取代', async () => {
  await assert.rejects(access(new URL('../src/views/VenueDesignerView.vue', import.meta.url)))
})
