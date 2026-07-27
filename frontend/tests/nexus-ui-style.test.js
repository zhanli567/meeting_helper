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
})

test('人员侧栏和导入弹窗使用统一的白底细边框工作台视觉', async () => {
  const participantPanel = await readSource('src/components/ParticipantPanel.vue')
  const importDialog = await readSource('src/components/ImportDialog.vue')

  assert.match(participantPanel, /class="panel-tools"/)
  assert.match(participantPanel, /border:\s*1px solid var\(--line\)/)
  assert.match(importDialog, /class="import-card import-card--template"/)
  assert.match(importDialog, /class="upload-surface"/)
})
