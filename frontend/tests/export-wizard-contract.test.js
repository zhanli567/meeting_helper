import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('export dialog is a sheet configuration wizard', async () => {
  const source = await readFile(new URL('../src/components/ExportOptionsDialog.vue', import.meta.url), 'utf8')

  assert.match(source, /el-steps/)
  assert.match(source, /选择子表/)
  assert.match(source, /人员名单配置/)
  assert.match(source, /排座图配置/)
  assert.match(source, /座位明细配置/)
  assert.match(source, /确认导出/)
  assert.match(source, /layoutFieldCodes/)
  assert.match(source, /layoutColorFieldCodes/)
  assert.match(source, /participants:\s*\{\s*enabled:\s*true/)
  assert.match(source, /layout:\s*\{\s*enabled:\s*true/)
  assert.match(source, /seatDetails:\s*\{\s*enabled:\s*true/)
})

test('layout color fields are selected from layout fields only', async () => {
  const source = await readFile(new URL('../src/components/ExportOptionsDialog.vue', import.meta.url), 'utf8')

  assert.match(source, /availableLayoutColorFields/)
  assert.match(source, /form\.layout\.colorFieldCodes = form\.layout\.colorFieldCodes\.filter/)
})
