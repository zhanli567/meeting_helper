import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('选中人员信息窗展示完整人员信息并支持收缩编辑', async () => {
  const source = await readFile(new URL('../src/components/SelectedParticipantInfo.vue', import.meta.url), 'utf8')

  assert.match(source, /defineProps\(\{[\s\S]*participant/)
  assert.match(source, /fieldDefinitions/)
  assert.match(source, /seatLabel/)
  assert.match(source, /collapsed/)
  assert.match(source, /defineEmits\(\['update:collapsed', 'edit'\]\)/)
  assert.match(source, /人员信息/)
  assert.match(source, /工号/)
  assert.match(source, /座位/)
  assert.match(source, /状态/)
  assert.match(source, /记录 \{\{ index \+ 1 \}\}/)
  assert.match(source, /participant-info-panel/)
  assert.match(source, /info-toggle/)
  assert.doesNotMatch(source, /获奖信息/)
})

test('排座工作台在画布左上挂载选中人员信息窗', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /SelectedParticipantInfo/)
  assert.match(source, /participantInfoCollapsed/)
  assert.match(source, /selectedParticipantSeatLabel/)
  assert.match(source, /workbenchMode === 'seating' && selectedParticipant/)
  assert.match(source, /v-model:collapsed="participantInfoCollapsed"/)
  assert.match(source, /@edit="openParticipantEdit"/)
})
