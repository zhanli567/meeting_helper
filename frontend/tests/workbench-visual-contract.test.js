import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')

test('workbench header places home on the left and uses shared select/button sizing', () => {
  const workbench = source('../src/views/WorkbenchView.vue')

  assert.match(
    workbench,
    /<el-button\s+class="header-home header-home-left"[\s\S]*首页[\s\S]*<\/el-button>[\s\S]*class="header-divider"[\s\S]*class="meeting-selector header-selector workbench-select"/,
  )
  assert.doesNotMatch(workbench, /class="home-brand"/)
  assert.doesNotMatch(workbench, /class="brand-slot"/)
  assert.match(workbench, /class="version-selector header-selector workbench-select"/)
  assert.match(workbench, /class="color-field-select workbench-select"/)
  assert.match(workbench, /class="header-auto-save workbench-select"/)
  assert.match(workbench, /class="header-action-button header-save-button"/)
  assert.match(workbench, /class="header-action-button header-publish-button"/)
  assert.match(workbench, /\.header-action-button\s*\{[\s\S]*height:\s*34px;[\s\S]*font-size:\s*13px;/)
  assert.match(workbench, /\.workbench-select\s+:deep\(\.el-select__wrapper\)\s*\{[\s\S]*background:\s*#fff;/)
})

test('workbench shared toolbar does not expose a layout-only center button', () => {
  const workbench = source('../src/views/WorkbenchView.vue')

  assert.doesNotMatch(workbench, /performToolbarCenterLayout/)
  assert.doesNotMatch(workbench, /:icon="Rank"/)
  assert.match(workbench, /\.toolbar-card\s*\{[\s\S]*flex-wrap:\s*wrap;[\s\S]*overflow:\s*visible;/)
})

test('workbench adapts when browser width is reduced without hiding core panels', () => {
  const workbench = source('../src/views/WorkbenchView.vue')
  const mainCss = source('../src/styles/main.css')

  assert.doesNotMatch(mainCss, /min-width:\s*1180px;/)
  assert.match(workbench, /\.workspace-shell\s*\{[\s\S]*overflow:\s*auto;/)
  assert.match(workbench, /@media \(max-width:\s*1180px\)\s*\{[\s\S]*\.workspace-shell\s*\{[\s\S]*grid-template-columns:\s*minmax\(0,\s*1fr\);/)
  assert.match(workbench, /@media \(max-width:\s*1180px\)\s*\{[\s\S]*\.participant-side\s*\{[\s\S]*grid-column:\s*1;/)
  assert.match(workbench, /@media \(max-width:\s*1180px\)\s*\{[\s\S]*\.canvas-shell\s*\{[\s\S]*min-height:\s*520px;/)
  assert.match(workbench, /@media \(max-width:\s*760px\)\s*\{[\s\S]*\.canvas-stats\s*\{[\s\S]*display:\s*grid;/)
})

test('layout mode canvas and right panel use the same rounded white shell as seating mode', () => {
  const editor = source('../src/components/VenueLayoutEditor.vue')
  const canvasViewport = source('../src/components/CanvasViewport.vue')
  const canvasBoard = source('../src/components/CanvasBoard.vue')
  const elementPanel = source('../src/components/VenueElementPanel.vue')
  const markerPanel = source('../src/components/RegionMarkerPanel.vue')

  assert.doesNotMatch(editor, /\.venue-layout-editor\.external-panel-mode \.canvas-pane\s*\{[\s\S]*border-radius:\s*0;/)
  assert.match(editor, /\.venue-layout-editor\.external-panel-mode \.canvas-pane\s*\{[\s\S]*border-radius:\s*var\(--radius-md\);/)
  assert.match(editor, /<CanvasBoard[\s\S]*class="designer-canvas"[\s\S]*grid/)
  assert.match(canvasBoard, /\.app-canvas-board\s*\{[\s\S]*border-radius:\s*12px;/)
  assert.match(canvasBoard, /\.app-canvas-board\.grid-board\s*\{[\s\S]*background-size:\s*var\(--editor-cell, var\(--unit, 44px\)\)/)
  assert.doesNotMatch(editor, /\.venue-layout-editor\.external-panel-mode \.designer-canvas\s*\{/)
  assert.match(canvasViewport, /\.app-canvas-viewport\s*\{[\s\S]*#f7f8fa;[\s\S]*scrollbar-gutter:\s*stable;/)
  assert.match(canvasViewport, /\.app-canvas-content\s*\{[\s\S]*display:\s*flex;[\s\S]*padding:\s*26px 68px 38px;/)
  assert.match(canvasViewport, /\.app-canvas-content\s*\{[\s\S]*align-items:\s*center;[\s\S]*justify-content:\s*center;/)
  assert.match(canvasViewport, /\.app-canvas-content\s*\{[\s\S]*min-width:\s*200%;[\s\S]*min-height:\s*200%;/)
  assert.match(elementPanel, /\.element-editor\s*\{[\s\S]*background:\s*#fff;/)
  assert.match(markerPanel, /\.marker-detail-section\s*\{[\s\S]*background:\s*#fff;/)
  assert.doesNotMatch(elementPanel, /\.panel-title\s*\{[\s\S]*font-size:\s*18px;/)
  assert.doesNotMatch(markerPanel, /\.panel-heading h2\s*\{[\s\S]*font-size:\s*18px;/)
})
