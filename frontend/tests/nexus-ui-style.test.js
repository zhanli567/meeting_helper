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

  assert.doesNotMatch(source, /currentUser/)
  assert.doesNotMatch(source, /User/)
  assert.doesNotMatch(source, /class="user-context"/)
  assert.doesNotMatch(source, />\s*开始排座\s*</)
  assert.match(source, />\s*场馆模板\s*</)
  assert.match(source, /router\.push\('\/venues'\)/)
  assert.match(source, />\s*添加会议\s*</)
  assert.doesNotMatch(source, /router\.push\('\/venues\/select'\)/)
  assert.match(source, /:icon="Collection"/)
  assert.match(source, /:icon="CirclePlus"/)
  assert.doesNotMatch(source, /<p>\s*每场会议拥有独立/)
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
