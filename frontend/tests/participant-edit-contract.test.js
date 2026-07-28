import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('人员面板使用三个统一图标按钮并暴露编辑事件', async () => {
  const source = await readFile(new URL('../src/components/ParticipantPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /defineEmits\(\[[^\]]*'edit'/s)
  assert.match(source, /@click="emit\('edit', person\)"/)
  assert.match(source, /class="person-actions icon-actions"/)
  assert.match(source, /class="person-fixed"/)
  assert.match(source, /class="person-dynamic"/)
  assert.match(source, /\.person-card:hover \.person-actions/)
  assert.match(source, /\.person-actions\s*\{[\s\S]*position:\s*absolute[\s\S]*opacity:\s*0/)
  assert.doesNotMatch(source, /\.person-actions\s*\{[\s\S]*width:\s*38px/)
  assert.doesNotMatch(source, /移出会议<\/el-button>/)
})

test('编辑人员弹窗工号只读并支持多记录表格', async () => {
  const source = await readFile(new URL('../src/components/EditParticipantDialog.vue', import.meta.url), 'utf8')

  assert.match(source, /label="工号"/)
  assert.match(source, /disabled/)
  assert.match(source, /el-table/)
  assert.match(source, /添加列/)
})

test('排座工作台接入人员编辑弹窗并刷新选中结果', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /import EditParticipantDialog from '@\/components\/EditParticipantDialog.vue'/)
  assert.match(source, /@edit="openParticipantEdit"/)
  assert.match(source, /v-model="editParticipantVisible"/)
  assert.match(source, /@done="onParticipantUpdated"/)
})
