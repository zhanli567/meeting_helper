import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('前端导出只保留 Excel 入口', async () => {
  const api = await readFile(new URL('../src/api/meeting.js', import.meta.url), 'utf8')
  const store = await readFile(new URL('../src/stores/workspace.js', import.meta.url), 'utf8')
  const workbench = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')
  const dialog = await readFile(new URL('../src/components/ExportOptionsDialog.vue', import.meta.url), 'utf8')

  assert.match(api, /exportExcel/)
  assert.match(api, /\/exports\/excel/)
  assert.match(api, /fieldCodes/)
  assert.match(api, /includeAttendance/)
  assert.match(api, /includeSeatLabel/)
  assert.match(store, /meetingApi\.exportExcel/)
  assert.doesNotMatch(store, /草稿版本不支持导出/)
  assert.match(workbench, /ExportOptionsDialog/)
  assert.match(workbench, /exportOptionsVisible/)
  assert.match(workbench, /v-if="!readonlyMode"/)
  assert.match(workbench, /导出Excel/)
  assert.match(dialog, /导出Excel/)
  assert.match(dialog, /工号、姓名为必选列/)
  assert.match(dialog, /扩展字段/)
  assert.match(dialog, /出席情况/)
  assert.match(dialog, /座位编号/)
  assert.match(dialog, /fieldCodes/)
  assert.match(dialog, /includeAttendance/)
  assert.match(dialog, /includeSeatLabel/)
  assert.doesNotMatch(`${api}\n${store}\n${workbench}\n${dialog}`, /pdf|PDF/)
})
