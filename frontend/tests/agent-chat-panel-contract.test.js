import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

async function readPanelSource() {
  return readFile(new URL('../src/agent/components/AgentChatPanel.vue', import.meta.url), 'utf8')
}

test('智能体面板明确是查询助手并接入会话控制器', async () => {
  const source = await readPanelSource()

  assert.match(source, /AI 查询助手/)
  assert.match(source, /sendAgentChat/)
  assert.match(source, /reduceAgentMessages/)
  assert.match(source, /createAgentChatSession/)
  assert.match(source, /当前阶段只执行查询/)
})

test('面板把输入、发送和 loading 状态绑定到会话控制器', async () => {
  const source = await readPanelSource()

  assert.match(source, /<el-input[\s\S]*v-model="session\.input"[\s\S]*type="textarea"/)
  assert.match(source, /@submit\.prevent="submitMessage"/)
  assert.match(source, /@keydown\.enter\.exact\.prevent="handleEnter"/)
  assert.match(source, /session\.send\(\{[\s\S]*meetingId: props\.meetingId[\s\S]*workspaceRevision: props\.workspaceRevision[\s\S]*disabled: props\.disabled/)
  assert.match(source, /:loading="session\.loading"/)
  assert.match(source, /:disabled="disabled \|\| session\.loading[^"]*session\.input\.trim\(\)[^"]*"/)
})

test('面板折叠工具轨迹并在卸载时释放会话', async () => {
  const source = await readPanelSource()

  assert.match(source, /v-for="message in session\.messages"/)
  assert.match(source, /<details[\s\S]*message\.toolTrace\?\.length/)
  assert.match(source, /<summary>工具轨迹/)
  assert.match(source, /onBeforeUnmount/)
  assert.match(source, /session\.dispose\(\)/)
})
