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
  assert.match(source, /:title="suggestion\.name"/)
  assert.match(source, /gap:\s*4px\s+6px/)
  assert.match(source, /align-content:\s*start/)
  assert.match(source, /min-height:\s*38px/)
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

  assert.match(pickerSource, /availableElementSuggestions/)
  assert.match(pickerSource, /自定义元素名称/)
  assert.match(pickerSource, /removeCustomElement/)
  assert.match(pickerSource, /overflow-x:\s*hidden/)
  assert.match(pickerSource, /overflow-y:\s*auto/)
  assert.match(pickerSource, /ELEMENT_KINDS\.GENERIC/)
  assert.match(panelSource, /kind/)
  assert.match(panelSource, /name/)
  assert.match(panelSource, /fillColor/)
  assert.match(panelSource, /borderColor/)
  assert.match(panelSource, /ColorPickerPopover/)
  assert.match(panelSource, /v-model="draft\.fillColor"/)
  assert.match(panelSource, /v-model="draft\.borderColor"/)
  assert.match(panelSource, /emit\('preview'/)
  assert.match(panelSource, /emit\('cancel'/)
  assert.match(panelSource, /label="显示名称"/)
  assert.doesNotMatch(panelSource, /label="元素名称"/)
  assert.doesNotMatch(panelSource, /label="元素类型"|v-model="draft\.kind"/)
  assert.doesNotMatch(panelSource, /<el-color-picker/)
  assert.doesNotMatch(panelSource, /capacity|assignable|walkable|code/)
})

test('场馆信息表单只保留高频字段', async () => {
  const source = await readSource('src/components/VenueInfoForm.vue')

  assert.match(source, /label="地点"/)
  assert.match(source, /label="园区"/)
  assert.match(source, /label="容纳人数"/)
  assert.match(source, /label="接口人"/)
  assert.match(source, /label="备注"/)
  assert.doesNotMatch(source, /label="预定链接"/)
  assert.doesNotMatch(source, /label="主屏分辨率"/)
  assert.doesNotMatch(source, /label="舞台尺寸"/)
  assert.doesNotMatch(source, /label="会议室功能"/)
  assert.doesNotMatch(source, /label="服务提供"/)
  assert.doesNotMatch(source, /label="说明"/)
})

test('场馆布局缩小冲突只保留编辑器内提示并固定停靠操作在底部', async () => {
  const editorSource = await readSource('src/components/VenueLayoutEditor.vue')
  const panelSource = await readSource('src/components/VenueElementPanel.vue')

  assert.match(editorSource, /conflict-banner/)
  assert.doesNotMatch(editorSource, /ElMessage\.warning\('画布边界内仍有元素/)
  assert.match(panelSource, /margin-top:\s*auto/)
})

test('新建流程复用编辑器并通过统一创建载荷完成保存', async () => {
  const source = await readSource('src/views/VenueCreateView.vue')

  assert.match(source, /<VenueLayoutEditor/)
  assert.match(source, /:manual-capacity="info\.manualCapacity"/)
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
  assert.match(source, /:manual-capacity="venue\?\.manualCapacity"/)
})

test('旧设计器已由可复用编辑器取代', async () => {
  await assert.rejects(access(new URL('../src/views/VenueDesignerView.vue', import.meta.url)))
})
