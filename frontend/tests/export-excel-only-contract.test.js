import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('前端导出只保留 Excel 入口', async () => {
  const api = await readFile(new URL('../src/api/meeting.js', import.meta.url), 'utf8')
  const store = await readFile(new URL('../src/stores/workspace.js', import.meta.url), 'utf8')
  const workbench = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(api, /exportExcel/)
  assert.match(api, /\/exports\/excel/)
  assert.match(store, /meetingApi\.exportExcel/)
  assert.match(workbench, /导出Excel/)
  assert.doesNotMatch(`${api}\n${store}\n${workbench}`, /pdf|PDF/)
})
