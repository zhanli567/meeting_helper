import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

async function readPanelSource() {
  return readFile(new URL('../src/agent/components/AgentChatPanel.vue', import.meta.url), 'utf8')
}

test('智能体面板明确是查询助手并接入会话控制器', async () => {
  const source = await readPanelSource()

  assert.match(source, /AI 查询助手/)
  assert.match(source, />排座助手</)
  assert.match(source, /sendAgentChat/)
  assert.match(source, /reduceAgentMessages/)
  assert.match(source, /createAgentChatSession/)
  assert.match(source, /只读查询/)
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

test('空会话采用居中欢迎区并在发送后切换到底部输入', async () => {
  const source = await readPanelSource()

  assert.match(source, /isEmptyConversation/)
  assert.match(source, /agent-chat-welcome/)
  assert.match(source, /我们从哪里开始排座？/)
  assert.match(source, /:class="\{ 'is-centered': isEmptyConversation \}"/)
  assert.match(source, /v-if="!isEmptyConversation"/)
})

test('顶部标题采用简洁栏而不是大标题和长说明', async () => {
  const source = await readPanelSource()

  assert.match(source, /agent-chat-header-avatar/)
  assert.match(source, />排座助手</)
  assert.match(source, /agent-chat-mode-chip/)
  assert.doesNotMatch(source, /<h2>AI 查询助手<\/h2>/)
  assert.doesNotMatch(source, /当前阶段只执行查询，不会保存或发布排座。/)
})

test('面板折叠查询过程并在卸载时释放会话', async () => {
  const source = await readPanelSource()

  assert.match(source, /v-for="message in session\.messages"/)
  assert.match(source, /<details[\s\S]*message\.toolTrace\?\.length/)
  assert.match(source, /查询过程/)
  assert.match(source, /onBeforeUnmount/)
  assert.match(source, /session\.dispose\(\)/)
})

test('面板支持拖动、拉伸和视口边界约束', async () => {
  const source = await readPanelSource()

  assert.match(source, /panelStyle/)
  assert.match(source, /startDrag/)
  assert.match(source, /startResize/)
  assert.match(source, /clampPanel/)
  assert.match(source, /@pointerdown="startDrag"/)
  assert.match(source, /resizeDirections/)
  assert.match(source, /startResize\(\$event, direction\)/)
  assert.match(source, /agent-chat-resize-zone/)
  assert.doesNotMatch(source, /agent-chat-resize-handle/)
})

test('面板支持最小化并在消息变化后滚动到底部', async () => {
  const source = await readPanelSource()

  assert.match(source, /minimized/)
  assert.match(source, /minimizePanel/)
  assert.match(source, /restorePanel/)
  assert.match(source, /agent-chat-minimized/)
  assert.match(source, /messageListRef/)
  assert.match(source, /scrollToBottom/)
})

test('面板用受控 Markdown 渲染助手正文', async () => {
  const source = await readPanelSource()

  assert.match(source, /renderAgentMarkdown/)
  assert.match(source, /v-html="renderAgentMarkdown\(message\.text\)"/)
  assert.match(source, /agent-chat-markdown/)
})

test('查询过程采用轻量折叠样式而不是正文工具轨迹列表', async () => {
  const source = await readPanelSource()

  assert.match(source, /agent-chat-activity/)
  assert.match(source, /查询过程/)
  assert.doesNotMatch(source, /agent-chat-tools/)
  assert.doesNotMatch(source, /工具轨迹/)
})

test('入口使用无文字图片并支持拖动', async () => {
  const source = await readPanelSource()

  assert.match(source, /assistantAvatarUrl/)
  assert.match(source, /launcherStyle/)
  assert.match(source, /startLauncherDrag/)
  assert.match(source, /clampLauncher/)
  assert.match(source, /class="agent-chat-launcher-image"/)
  assert.match(source, /@pointerdown="startLauncherDrag"/)
  assert.doesNotMatch(source, /agent-chat-trigger-dot/)
  assert.doesNotMatch(source, /<span>查询助手<\/span>/)
})

test('拉伸热区不可见且仅通过鼠标样式提示', async () => {
  const source = await readPanelSource()

  assert.match(source, /cursor: ns-resize/)
  assert.match(source, /cursor: ew-resize/)
  assert.match(source, /cursor: nesw-resize/)
  assert.match(source, /cursor: nwse-resize/)
  assert.match(source, /background: transparent/)
  assert.doesNotMatch(source, /agent-chat-resize-handle::after/)
  assert.doesNotMatch(source, /border-right: 2px solid #94a3b8/)
})
