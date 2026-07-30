import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('export dialog is a sheet configuration wizard', async () => {
  const source = await readFile(new URL('../src/components/ExportOptionsDialog.vue', import.meta.url), 'utf8')

  assert.match(source, /class="export-options-dialog"/)
  assert.match(source, /class="export-dialog-body"/)
  assert.match(source, /el-tabs/)
  assert.match(source, /el-tab-pane/)
  assert.match(source, /el-collapse/)
  assert.match(source, /exportTabs/)
  assert.match(source, /selectedSheetDefinitions/)
  assert.match(source, /选择子表/)
  assert.match(source, /确认导出/)
  assert.match(source, /人员名单配置/)
  assert.match(source, /排座图配置/)
  assert.match(source, /座位明细配置/)
  assert.match(source, /layoutFieldCodes/)
  assert.match(source, /layoutColorFieldCodes/)
  assert.match(source, /participants:\s*\{\s*enabled:\s*false/)
  assert.match(source, /layout:\s*\{\s*enabled:\s*false/)
  assert.match(source, /seatDetails:\s*\{\s*enabled:\s*false/)
  assert.doesNotMatch(source, /el-steps/)
  assert.doesNotMatch(source, /固定字段/)
  assert.match(source, /width="880px"/)
  assert.match(source, /height:\s*min\(82vh,\s*760px\);/)
  assert.match(source, /class="[^"]*export-side-nav[^"]*"/)
  assert.match(source, /class="config-section"/)
  assert.match(source, /class="check-card"/)
})

test('layout color fields are selected from layout fields only', async () => {
  const source = await readFile(new URL('../src/components/ExportOptionsDialog.vue', import.meta.url), 'utf8')

  assert.match(source, /availableLayoutColorFields/)
  assert.match(source, /form\.layout\.colorFieldCodes = form\.layout\.colorFieldCodes\.filter/)
})

test('workbench passes resolved layout color rules to Excel export', async () => {
  const workbench = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')
  const request = await readFile(
    new URL('../../backend/src/main/java/com/company/meetinghelper/export/api/dto/request/ExportExcelRequest.java', import.meta.url),
    'utf8',
  )
  const writer = await readFile(
    new URL('../../backend/src/main/java/com/company/meetinghelper/export/service/LayoutSheetWriter.java', import.meta.url),
    'utf8',
  )

  assert.match(workbench, /buildExportColorRules/)
  assert.match(workbench, /styleRules:\s*buildExportColorRules/)
  assert.match(workbench, /assignedParticipantsForColor/)
  assert.match(workbench, /let reservedColors = \[\.\.\.layoutReservedColors\.value\]/)
  assert.match(request, /record StyleRule/)
  assert.match(request, /List<StyleRule> styleRules/)
  assert.match(writer, /styleRulesByFieldValue/)
})
