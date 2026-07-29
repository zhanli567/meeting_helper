import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('frontend exposes only the Excel export flow', async () => {
  const api = await readFile(new URL('../src/api/meeting.js', import.meta.url), 'utf8')
  const store = await readFile(new URL('../src/stores/workspace.js', import.meta.url), 'utf8')
  const workbench = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')
  const dialog = await readFile(new URL('../src/components/ExportOptionsDialog.vue', import.meta.url), 'utf8')

  assert.match(api, /exportExcel/)
  assert.match(api, /\/exports\/excel/)
  assert.match(api, /http\.post\(`\/meetings\/\$\{meetingId\}\/exports\/excel`/)
  assert.match(api, /versionId/)
  assert.match(api, /sheets/)
  assert.match(api, /http\.post/)
  assert.doesNotMatch(api, /fieldCodes: Array\.isArray/)
  assert.match(store, /meetingApi\.exportExcel/)
  assert.match(store, /buildExportFilename/)
  assert.match(store, /formatExportTimestamp/)
  assert.match(store, /versionName/)
  assert.match(store, /yyyyMMddHHmm/)
  assert.match(workbench, /ExportOptionsDialog/)
  assert.match(workbench, /exportOptionsVisible/)
  assert.match(workbench, /v-if="!readonlyMode"/)
  assert.match(workbench, /导出Excel/)
  assert.match(dialog, /选择子表/)
  assert.match(dialog, /排座图配置/)
  assert.match(dialog, /layoutColorFieldCodes/)
  assert.doesNotMatch(`${api}\n${store}\n${workbench}\n${dialog}`, /pdf|PDF/)
})
