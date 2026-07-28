import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('排座工作台提供三段模式且发布版本只读', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /workbenchMode\s*=\s*ref\('seating'\)/)
  assert.match(source, /label="排座模式"/)
  assert.match(source, /label="布局编辑模式"/)
  assert.match(source, /label="区域标记模式"/)
  assert.match(source, /:disabled="readonlyMode"/)
})

test('布局编辑模式复用编辑器并保存会议布局', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /VenueLayoutEditor/)
  assert.match(source, /saveMeetingLayout/)
  assert.match(source, /updateMeetingLayout/)
})

test('布局编辑器双击删除走统一删除保护入口', async () => {
  const source = await readFile(new URL('../src/components/VenueLayoutEditor.vue', import.meta.url), 'utf8')

  assert.match(source, /protectedElementIds/)
  assert.match(source, /requestDeleteElement/)
  assert.match(source, /@dblclick\.stop="requestDeleteElement\(element\)"/)
})
