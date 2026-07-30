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

test('工作台按模式拆分页面级保存入口并把首页放在左上角', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /showModeSave/)
  assert.match(source, /currentSaveLabel/)
  assert.match(source, /saveCurrentMode/)
  assert.match(source, /保存排座/)
  assert.match(source, /保存布局/)
  assert.match(source, /保存区域/)
  assert.match(source, /workspaceBusy/)
  assert.match(source, /v-loading="workspaceBusy"/)
  assert.match(source, /saveStatusText/)
  assert.match(source, /activeModeDirty/)
  assert.match(source, /hasAnyUnsavedChanges/)
  assert.match(source, /seatCount/)
  assert.match(source, />\s*座位数\s*</)
  assert.doesNotMatch(source, /<span class="brand-mark">席<\/span>/)
  assert.doesNotMatch(source, /会议排座助手<\/strong>/)
  assert.doesNotMatch(source, /class="home-brand"/)
  assert.doesNotMatch(source, /class="brand-slot"/)
  assert.match(
    source,
    /<el-button\s+class="header-home header-home-left"[\s\S]*首页[\s\S]*<\/el-button>[\s\S]*class="header-divider"[\s\S]*class="meeting-selector header-selector workbench-select"/,
  )
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
  assert.match(source, /ref="layoutEditorRef"/)
  assert.match(source, /saveMeetingLayout/)
  assert.match(source, /updateMeetingLayout/)
  assert.match(source, /save-label="保存布局"/)
  assert.match(source, /toolbar-placement="none"/)
  assert.match(source, /class="canvas-zoom-controls"/)
  assert.doesNotMatch(source, /save-label="保存会议布局"/)
})

test('工作台工具栏和侧栏按当前模式统一派发', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')
  const editor = await readFile(new URL('../src/components/VenueLayoutEditor.vue', import.meta.url), 'utf8')
  const canvas = await readFile(new URL('../src/components/VenueCanvas.vue', import.meta.url), 'utf8')

  assert.match(source, /const sidePanelCollapsed = ref\(false\)/)
  assert.match(source, /const layoutEditorRef = ref/)
  assert.match(source, /const venueCanvasRef = ref/)
  assert.match(source, /toolbarUndoDisabled/)
  assert.match(source, /performToolbarUndo/)
  assert.match(source, /layoutEditorRef\.value\?\.undo\(\)/)
  assert.match(source, /markerUndoStack/)
  assert.match(source, /performMarkerUndo\(\)/)
  assert.match(source, /performToolbarRedo/)
  assert.match(source, /layoutEditorRef\.value\?\.redo\(\)/)
  assert.match(source, /markerRedoStack/)
  assert.match(source, /performMarkerRedo\(\)/)
  assert.match(source, /performToolbarFit/)
  assert.match(source, /layoutEditorRef\.value\?\.fitCanvas\(\)/)
  assert.match(source, /venueCanvasRef\.value\?\.fitCanvas\(\)/)
  assert.match(source, /zoomCurrentCanvas/)
  assert.match(source, /title="回退"/)
  assert.match(source, /title="前进"/)
  assert.match(source, />\s*适应\s*</)
  assert.match(source, /sidePanelCollapsed \? '展开当前侧栏' : '收起当前侧栏'/)
  assert.match(source, /v-show="!sidePanelCollapsed"/)
  assert.doesNotMatch(source, /participantPanelCollapsed/)
  assert.doesNotMatch(source, /:disabled="readonlyMode \|\| !undoStack\.length"/)
  assert.match(editor, /defineExpose/)
  assert.match(editor, /canUndo/)
  assert.match(editor, /canRedo/)
  assert.match(editor, /toolbarPlacement === 'top'/)
  assert.match(canvas, /defineExpose/)
  assert.match(canvas, /fitCanvas/)
  assert.match(canvas, /emit\('zoomChange', nextZoom - props\.zoom/)
})

test('三种工作台模式初始缩放和画布背景保持一致', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')
  const editor = await readFile(new URL('../src/components/VenueLayoutEditor.vue', import.meta.url), 'utf8')
  const canvas = await readFile(new URL('../src/components/VenueCanvas.vue', import.meta.url), 'utf8')

  assert.match(source, /const DEFAULT_CANVAS_ZOOM = 0\.8/)
  assert.match(source, /const zoom = ref\(DEFAULT_CANVAS_ZOOM\)/)
  assert.match(editor, /const DEFAULT_EDITOR_ZOOM = 0\.8/)
  assert.match(editor, /const zoom = ref\(DEFAULT_EDITOR_ZOOM\)/)
  assert.match(editor, /canvasOffsetX\.value = 0[\s\S]*canvasOffsetY\.value = 0[\s\S]*scheduleCenterCanvas\(\)/)
  assert.match(editor, /nextTick\(centerCanvas\)/)
  assert.match(editor, /requestAnimationFrame/)
  assert.doesNotMatch(editor, /nextTick\(fitCanvas\)/)
  assert.match(editor, /function scheduleCenterCanvas/)
  assert.match(editor, /\.venue-layout-editor\.external-panel-mode \.canvas-content\s*\{[\s\S]*display:\s*flex;/)
  assert.match(canvas, /linear-gradient\(rgba\(0,\s*0,\s*0,\s*0\.045\) 1px,\s*transparent 1px\),/)
  assert.match(editor, /linear-gradient\(rgba\(0,\s*0,\s*0,\s*0\.045\) 1px,\s*transparent 1px\),/)
  assert.match(editor, /#f7f8fa;/)
})

test('三种模式离开前统一处理未保存状态', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /modeDirty/)
  assert.match(source, /modeSave/)
  assert.match(source, /modeDiscard/)
  assert.match(source, /confirmUnsavedModeChanges/)
  assert.match(source, /confirmAllUnsavedChanges/)
  assert.match(source, /保存并离开/)
  assert.match(source, /放弃修改/)
  assert.match(source, /changeWorkbenchMode[\s\S]*confirmUnsavedModeChanges\(workbenchMode\.value\)/)
  assert.match(source, /warnUnsavedChanges[\s\S]*hasAnyUnsavedChanges\.value/)
  assert.match(source, /switchMeeting[\s\S]*confirmAllUnsavedChanges\(\)/)
  assert.match(source, /switchVersion[\s\S]*confirmAllUnsavedChanges\(\)/)
  assert.match(source, /goHome[\s\S]*confirmAllUnsavedChanges\(\)/)
})

test('切换模式时使用统一未保存提示并可放弃草稿', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /layoutDirty/)
  assert.match(source, /changeWorkbenchMode/)
  assert.match(source, /ElMessageBox\.confirm/)
  assert.match(source, /保存并离开/)
  assert.match(source, /放弃修改/)
  assert.match(source, /离开\$\{modeName\}/)
  assert.doesNotMatch(source, /离开布局编辑模式/)
  assert.doesNotMatch(source, /confirmDiscardLayoutChanges/)
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
