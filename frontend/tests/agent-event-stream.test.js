import assert from 'node:assert/strict'
import test from 'node:test'

import { parseAgentEventStreamChunk } from '../src/agent/runtime/eventStream.js'
import { reduceAgentMessages } from '../src/agent/runtime/messages.js'

test('SSE parser ignores standalone non-data server lines', () => {
  const raw = ': comment\nevent: ignored\nid: ignored\nretry: 1000\ndata: {"type":"RUN_DONE","payload":{}}\n\n'

  const result = parseAgentEventStreamChunk('', raw)

  assert.equal(result.events.length, 1)
  assert.equal(result.events[0].type, 'RUN_DONE')
  assert.deepEqual(result.events[0].payload, {})
})

test('解析完整 SSE Agent Event', () => {
  const raw = 'event: assistant_text\ndata: {"type":"ASSISTANT_TEXT","payload":{"text":"你好"}}\n\n'

  const result = parseAgentEventStreamChunk('', raw)

  assert.equal(result.buffer, '')
  assert.equal(result.events.length, 1)
  assert.equal(result.events[0].type, 'ASSISTANT_TEXT')
  assert.equal(result.events[0].payload.text, '你好')
})
test('半包 SSE 会保留到下一次解析', () => {
  const first = parseAgentEventStreamChunk('', 'event: run_done\ndata: {"type"')
  const second = parseAgentEventStreamChunk(first.buffer, ':"RUN_DONE"}\n\n')

  assert.equal(first.events.length, 0)
  assert.equal(second.events[0].type, 'RUN_DONE')
})

test('无法解析的 SSE JSON 会转换为 ERROR 事件', () => {
  const result = parseAgentEventStreamChunk('', 'event: error\ndata: {not-json}\n\n')

  assert.equal(result.events.length, 1)
  assert.equal(result.events[0].type, 'ERROR')
  assert.equal(result.events[0].payload.code, 'SSE_PARSE_ERROR')
})

test('reducer 折叠助手文本、工具轨迹和完成状态', () => {
  let messages = reduceAgentMessages([], {
    type: 'RUN_STARTED',
    runId: 'run-1',
    payload: {},
  })
  messages = reduceAgentMessages(messages, {
    type: 'ASSISTANT_TEXT',
    payload: { text: '你好' },
  })
  messages = reduceAgentMessages(messages, {
    type: 'TOOL_CALL',
    payload: { toolName: 'workspace.get_summary', callId: 'call-1' },
  })
  messages = reduceAgentMessages(messages, {
    type: 'TOOL_RESULT',
    payload: { toolName: 'workspace.get_summary', callId: 'call-1', success: true },
  })
  messages = reduceAgentMessages(messages, { type: 'RUN_DONE', payload: {} })

  assert.equal(messages.length, 1)
  assert.equal(messages[0].text, '你好')
  assert.equal(messages[0].status, 'done')
  assert.equal(messages[0].toolTrace.length, 2)
})

test('reducer writes GUARDRAIL_BLOCKED and ERROR into systemPrompts', () => {
  let messages = reduceAgentMessages([], {
    type: 'RUN_STARTED',
    runId: 'run-2',
    payload: {},
  })
  messages = reduceAgentMessages(messages, {
    type: 'GUARDRAIL_BLOCKED',
    payload: { reason: '只允许查询操作' },
  })
  messages = reduceAgentMessages(messages, {
    type: 'ERROR',
    payload: { message: 'Agent runtime failed' },
  })

  assert.deepEqual(messages[0].systemPrompts, ['只允许查询操作', 'Agent runtime failed'])
})
