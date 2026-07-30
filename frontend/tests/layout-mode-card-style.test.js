import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('layout mode separates canvas and element panel into card areas', async () => {
  const source = await readFile(new URL('../src/components/VenueLayoutEditor.vue', import.meta.url), 'utf8')

  assert.match(source, /\.editor-body\s*\{[\s\S]*gap:\s*14px;[\s\S]*padding:\s*14px;/)
  assert.match(source, /\.canvas-pane\s*\{[\s\S]*background:\s*#fff;[\s\S]*border:\s*1px solid var\(--line\);[\s\S]*border-radius:\s*var\(--radius-md\);[\s\S]*box-shadow:\s*var\(--shadow\);/)
  assert.match(source, /\.venue-element-panel\s*\{[\s\S]*border:\s*1px solid var\(--line\);[\s\S]*border-radius:\s*var\(--radius-md\);[\s\S]*box-shadow:\s*var\(--shadow\);/)
  assert.doesNotMatch(source, /border-left:\s*1px solid #dbe4f0;[\s\S]*border-right:\s*1px solid #dbe4f0;/)
})

test('workbench layout mode uses the same left canvas and right panel shell as other modes', async () => {
  const workbench = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')
  const editor = await readFile(new URL('../src/components/VenueLayoutEditor.vue', import.meta.url), 'utf8')
  const panel = await readFile(new URL('../src/components/VenueElementPanel.vue', import.meta.url), 'utf8')

  assert.doesNotMatch(workbench, /layout-wide/)
  assert.match(workbench, /id="workbench-layout-side"/)
  assert.match(workbench, /class="participant-side"/)
  assert.match(workbench, /'layout-editor-host': workbenchMode === 'layout'/)
  assert.match(workbench, /side-panel-target="#workbench-layout-side"/)
  assert.match(workbench, /\.workspace-shell\s*\{[\s\S]*grid-auto-rows:\s*minmax\(0,\s*1fr\);/)
  assert.match(workbench, /\.participant-side\s*\{[\s\S]*grid-column:\s*2;[\s\S]*grid-row:\s*1;/)
  assert.match(workbench, /\.canvas-shell\s*\{[\s\S]*grid-column:\s*1;[\s\S]*grid-row:\s*1;/)
  assert.match(editor, /sidePanelTarget/)
  assert.match(editor, /<Teleport[\s\S]*:disabled="!sidePanelTarget"/)
  assert.match(editor, /external-panel-mode/)
  assert.match(panel, /SidePanelEmptyState/)
  assert.match(panel, /未选中元素/)
  assert.doesNotMatch(panel, /<el-icon><Setting \/><\/el-icon>/)
  assert.doesNotMatch(panel, /import \{[^}]*Setting/)
})

test('workbench add participant flow removes old import menu and opens one dialog directly', async () => {
  const workbench = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(workbench, /@add="openParticipantEntry"/)
  assert.match(workbench, /@click="openParticipantEntry"/)
  assert.match(workbench, /addTargetElementId\.value = undefined/)
  assert.doesNotMatch(workbench, /ImportDialog/)
  assert.doesNotMatch(workbench, /importVisible/)
  assert.doesNotMatch(workbench, /addMenuVisible/)
  assert.doesNotMatch(workbench, /openBatchImport/)
  assert.doesNotMatch(workbench, /class="add-menu"/)
})
