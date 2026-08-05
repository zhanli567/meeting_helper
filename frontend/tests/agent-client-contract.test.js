import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

import { sendAgentChat } from '../src/agent/api/agentClient.js'

test('agent client 只调用本项目后端接口', async () => {
  const source = await readFile(new URL('../src/agent/api/agentClient.js', import.meta.url), 'utf8')

  assert.match(source, /fetch\(/)
  assert.match(source, /\/agent\/chat/)
  assert.doesNotMatch(source, /deepseek/i)
  assert.doesNotMatch(source, /openai/i)
})
test('agent client sends the chat contract and forwards streamed events', async () => {
  const originalFetch = globalThis.fetch
  const requests = []
  const encoder = new TextEncoder()
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return {
      ok: true,
      body: {
        getReader() {
          let read = false
          return {
            async read() {
              if (read) return { done: true, value: undefined }
              read = true
              return {
                done: false,
                value: encoder.encode('data: {"type":"RUN_DONE","payload":{}}\n\n'),
              }
            },
          }
        },
      },
    }
  }

  try {
    const events = []
    await sendAgentChat({
      meetingId: 'meeting-1',
      conversationId: 'conversation-1',
      message: '当前会议概况',
      workspaceRevision: 'rev-1',
      mode: 'QUERY',
      onEvent: (event) => events.push(event),
    })

    assert.match(requests[0].url, /\/agent\/chat$/)
    assert.deepEqual(JSON.parse(requests[0].options.body), {
      conversationId: 'conversation-1',
      meetingId: 'meeting-1',
      workspaceRevision: 'rev-1',
      message: '当前会议概况',
      stream: true,
      mode: 'QUERY',
    })
    assert.deepEqual(events, [{ type: 'RUN_DONE', payload: {} }])
  } finally {
    globalThis.fetch = originalFetch
  }
})
