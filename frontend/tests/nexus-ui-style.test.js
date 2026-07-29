import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

async function readSource(path) {
  return readFile(new URL(`../${path}`, import.meta.url), 'utf8')
}

test('全局样式使用 Agent Nexus 工作台 token，顶部栏不再使用蓝色渐变壳', async () => {
  const css = await readSource('src/styles/main.css')

  assert.match(css, /--brand:\s*#0a59f7;/)
  assert.match(css, /--workspace:\s*#f1f3f5;/)
  assert.match(css, /--radius-md:\s*16px;/)
  assert.doesNotMatch(css, /HarmonyOS/)
  assert.match(css, /font-family:\s*system-ui,/)
  assert.doesNotMatch(css, /\.app-header\s*\{[^}]*linear-gradient/s)
})

test('工作台页面使用 Nexus 风格的静态工具栏和分层内容区', async () => {
  const source = await readSource('src/views/WorkbenchView.vue')

  assert.match(source, /class="workspace-shell"/)
  assert.match(source, /class="canvas-shell"/)
  assert.match(source, /class="toolbar-card"/)
  assert.match(source, /\.app-header\s*:deep\(\.el-button--primary\)/)
  assert.match(source, /\.workbench-page\s*\{[^}]*height:\s*100vh;[^}]*overflow:\s*hidden;/s)
  assert.match(source, /\.canvas-body\s*\{[^}]*overflow:\s*hidden;/s)
})

test('首页保留模板管理入口并在会议列表标题处提供添加会议', async () => {
  const source = await readSource('src/views/HomeView.vue')
  const store = await readSource('src/stores/workspace.js')
  const workbench = await readSource('src/views/WorkbenchView.vue')

  assert.doesNotMatch(source, /currentUser/)
  assert.doesNotMatch(source, /User/)
  assert.doesNotMatch(source, /class="user-context"/)
  assert.match(source, /class="brand-slot"/)
  assert.doesNotMatch(source, /brand-mark/)
  assert.doesNotMatch(source, /会议排座助手/)
  assert.doesNotMatch(source, />\s*开始排座\s*</)
  assert.match(source, />\s*场馆模板\s*</)
  assert.match(source, /router\.push\('\/venues'\)/)
  assert.match(source, />\s*添加会议\s*</)
  assert.doesNotMatch(source, /router\.push\('\/venues\/select'\)/)
  assert.match(source, /:icon="Collection"/)
  assert.match(source, /:icon="CirclePlus"/)
  assert.match(source, /openRecentMeeting/)
  assert.match(source, /recentVersionKey/)
  assert.doesNotMatch(source, /meeting\.status/)
  assert.doesNotMatch(source, />\s*DRAFT\s*</)
  assert.match(store, /recentVersionKey/)
  assert.match(store, /readRecentMeetingSession/)
  assert.match(store, /versionKey/)
  assert.match(workbench, /route\.query\.version/)
  assert.match(workbench, /store\.rememberMeeting\(.*activeVersionKey\.value/s)
  assert.doesNotMatch(source, /<p>\s*每场会议拥有独立/)
})

test('主要列表容器固定高度并在内部滚动', async () => {
  const home = await readSource('src/views/HomeView.vue')
  const addDialog = await readSource('src/components/AddParticipantDialog.vue')
  const editDialog = await readSource('src/components/EditParticipantDialog.vue')
  const exportDialog = await readSource('src/components/ExportOptionsDialog.vue')
  const importDialog = await readSource('src/components/ImportDialog.vue')
  const regionPanel = await readSource('src/components/RegionMarkerPanel.vue')
  const regionCreate = await readSource('src/components/RegionCreateDialog.vue')
  const colorPicker = await readSource('src/components/ColorPickerPopover.vue')
  const elementPicker = await readSource('src/components/VenueElementPicker.vue')
  const elementPanel = await readSource('src/components/VenueElementPanel.vue')

  assert.match(home, /\.home-scroll\s*\{[\s\S]*overflow:\s*hidden;/)
  assert.match(home, /\.home-content\s*\{[\s\S]*height:\s*100%;[\s\S]*display:\s*flex;/)
  assert.match(home, /\.meeting-grid\s*\{[\s\S]*flex:\s*1;[\s\S]*overflow-y:\s*auto;/)
  assert.match(addDialog, /class="participant-form-scroll"/)
  assert.match(addDialog, /\.participant-form-scroll\s*\{[\s\S]*max-height:[\s\S]*overflow-y:\s*auto;/)
  assert.match(addDialog, /class="extra-field-list"/)
  assert.match(addDialog, /\.extra-field-list\s*\{[\s\S]*min-height:[\s\S]*max-height:[\s\S]*overflow-y:\s*auto;/)
  assert.match(editDialog, /class="participant-form-scroll"/)
  assert.match(editDialog, /max-height="260"/)
  assert.match(editDialog, /class="extra-field-list"/)
  assert.match(editDialog, /\.extra-field-list\s*\{[\s\S]*min-height:[\s\S]*max-height:[\s\S]*overflow-y:\s*auto;/)
  assert.match(exportDialog, /\.field-checks\s*\{[\s\S]*min-height:[\s\S]*max-height:[\s\S]*overflow-y:\s*auto;/)
  assert.match(importDialog, /\.import-layout\s*\{[\s\S]*max-height:[\s\S]*overflow-y:\s*auto;/)
  assert.match(importDialog, /\.field-section\s*\{[\s\S]*max-height:[\s\S]*overflow-y:\s*auto;/)
  assert.match(regionPanel, /\.marker-list\s*\{[\s\S]*min-height:[\s\S]*max-height:[\s\S]*overflow-y:\s*auto;/)
  assert.match(regionPanel, /\.marker-form\s*\{[\s\S]*overflow-y:\s*auto;/)
  assert.match(regionPanel, /ColorPickerPopover/)
  assert.match(regionCreate, /ColorPickerPopover/)
  assert.match(colorPicker, /\.swatch-grid\s*\{[\s\S]*min-height:[\s\S]*max-height:[\s\S]*overflow-y:\s*auto;/)
  assert.match(elementPicker, /\.suggestion-grid\s*\{[\s\S]*min-height:[\s\S]*max-height:[\s\S]*overflow-y:\s*auto;/)
  assert.match(elementPanel, /ColorPickerPopover/)
})

test('预览和工作区读取通用元素字段且不渲染旋转', async () => {
  const preview = await readSource('src/components/VenuePreviewDialog.vue')
  const canvas = await readSource('src/components/VenueCanvas.vue')

  assert.match(preview, /element\.name/)
  assert.match(preview, /element\.fillColor/)
  assert.match(canvas, /element\.kind === 'SEAT'/)
  assert.doesNotMatch(preview, /element\.rotation/)
  assert.doesNotMatch(canvas, /element\.assignable/)
  assert.doesNotMatch(preview, /\.cellSize/)
  assert.doesNotMatch(canvas, /\.cellSize/)
})

test('人员侧栏和导入弹窗使用统一的白底细边框工作台视觉', async () => {
  const participantPanel = await readSource('src/components/ParticipantPanel.vue')
  const importDialog = await readSource('src/components/ImportDialog.vue')

  assert.match(participantPanel, /class="panel-tools"/)
  assert.match(participantPanel, /border:\s*1px solid var\(--line\)/)
  assert.match(importDialog, /class="import-card import-card--template"/)
  assert.match(importDialog, /class="upload-surface"/)
})
