import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('人员面板使用三个统一图标按钮并暴露编辑事件', async () => {
  const source = await readFile(new URL('../src/components/ParticipantPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /defineEmits\(\[[^\]]*'edit'/s)
  assert.match(source, /@click="emit\('edit', person\)"/)
  assert.match(source, /class="person-actions icon-actions"/)
  assert.match(source, /class="person-fixed"/)
  assert.match(source, /class="person-employee-no"/)
  assert.match(source, /class="person-dynamic"/)
  assert.match(source, /class="participant-status-dot"/)
  assert.doesNotMatch(source, /v-if="[\s\S]*participantDynamicSummary\(person\)\.length[\s\S]*"\s*class="person-dynamic"/)
  assert.match(source, /\.person-main\s*\{[\s\S]*grid-template-rows:\s*auto minmax\(20px,\s*auto\);/)
  assert.match(source, /\.person-main\s*\{[\s\S]*align-content:\s*start;/)
  assert.match(source, /\.person-dynamic\s*\{[\s\S]*min-height:\s*20px;/)
  assert.match(source, /\.person-card:hover \.person-actions/)
  assert.match(source, /\.person-actions\s*\{[\s\S]*position:\s*absolute[\s\S]*opacity:\s*0/)
  assert.match(source, /\.participant-list\s*\{[\s\S]*padding-right:\s*0;/)
  assert.match(source, /\.participant-status-dot\.status-assigned\s*\{[\s\S]*background:\s*#86efac;/)
  assert.match(source, /\.person-employee-no\s*\{[\s\S]*margin-right:\s*16px;/)
  assert.doesNotMatch(source, /assigned-dot/)
  assert.doesNotMatch(source, /\.person-actions\s*\{[\s\S]*width:\s*38px/)
  assert.doesNotMatch(source, /移出会议<\/el-button>/)
})

test('编辑人员弹窗工号只读并使用横向表格编辑记录和字段', async () => {
  const source = await readFile(new URL('../src/components/EditParticipantDialog.vue', import.meta.url), 'utf8')

  assert.match(source, /label="工号"/)
  assert.match(source, /disabled/)
  assert.match(source, /<el-table/)
  assert.match(source, /class="record-table-wrap"/)
  assert.match(source, /添加记录/)
  assert.match(source, /添加列/)
  assert.match(source, /removeCustomColumn/)
  assert.match(source, /record-row-index/)
  assert.doesNotMatch(source, /获奖信息/)
  assert.doesNotMatch(source, /class="record-card"/)
  assert.doesNotMatch(source, /multiRecord/)
  assert.doesNotMatch(source, /placeholder=/)
  assert.match(source, /:validate-on-rule-change="false"/)
  assert.match(source, /:validate-event="false"/)
  assert.doesNotMatch(source, /trigger:\s*(?:'|"|\[)/)
})

test('排座工作台接入人员编辑弹窗并刷新选中结果', async () => {
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /import EditParticipantDialog from '@\/components\/EditParticipantDialog.vue'/)
  assert.match(source, /@edit="openParticipantEdit"/)
  assert.match(source, /v-model="editParticipantVisible"/)
  assert.match(source, /@done="onParticipantUpdated"/)
})
