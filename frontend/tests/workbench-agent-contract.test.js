import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('工作台挂载智能体面板并传入完整只读上下文', async () => {
  const source = await readFile(
    new URL('../src/views/WorkbenchView.vue', import.meta.url),
    'utf8',
  )

  const panelMarkup = source.match(/<AgentChatPanel[\s\S]*?\/>/)?.[0] || ''

  assert.match(panelMarkup, /AgentChatPanel/)
  assert.match(panelMarkup, /:meeting-id="workspace\?\.meeting\?\.id"/)
  assert.match(panelMarkup, /:workspace-revision="String\(workspace\?\.meeting\?\.layoutVersion \|\| ''\)"/)
  assert.match(panelMarkup, /:disabled="!workspace\?\.meeting\?\.id"/)
})

test('工作台不向智能体面板传入写操作能力', async () => {
  const source = await readFile(
    new URL('../src/views/WorkbenchView.vue', import.meta.url),
    'utf8',
  )
  const panelMarkup = source.match(/<AgentChatPanel[\s\S]*?\/>/)?.[0] || ''

  assert.doesNotMatch(
    panelMarkup,
    /\b(?:delete|deleteMeeting|deleteParticipant|restore|save|publish|saveCurrentMode|createVersion)\b/i,
  )
})
