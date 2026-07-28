import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('座位画布展示动态座位号、行号和人员姓名', async () => {
  const source = await readFile(new URL('../src/components/VenueCanvas.vue', import.meta.url), 'utf8')

  assert.match(source, /computeSeatLabels/)
  assert.match(source, /seatLabelFor\(element\.id\)/)
  assert.match(source, /row-label/)
  assert.match(source, /participantColorById/)
  assert.doesNotMatch(source, /<span v-if="participantSeatSummary\(element\.id\)" class="seat-summary">/)
})

test('工作台提供按人员字段着色的选择器', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /colorFieldOptions/)
  assert.match(source, /groupColorFieldCode/)
  assert.match(source, /buildParticipantColorMap/)
  assert.match(source, /按字段着色/)
})
