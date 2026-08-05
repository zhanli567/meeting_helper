import assert from 'node:assert/strict'
import test from 'node:test'

import { createAgentChatSession } from '../src/agent/runtime/chatSession.js'
import { reduceAgentMessages } from '../src/agent/runtime/messages.js'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

function createControllerSpy() {
  const controller = {
    signal: { aborted: false },
    abortCalls: 0,
    abort() {
      this.abortCalls += 1
      this.signal.aborted = true
    },
  }
  return controller
}

test('send submits trimmed input and clears loading after request settles', async () => {
  const request = deferred()
  const calls = []
  const session = createAgentChatSession({
    sendAgentChat(options) {
      calls.push(options)
      return request.promise
    },
    reduceAgentMessages,
    createAbortController: createControllerSpy,
    createConversationId: () => 'conversation-1',
    createMessageId: () => 'user-1',
  })

  session.input = '  Who is seated at table 1?  '
  const pending = session.send({ meetingId: 'meeting-1', workspaceRevision: 'rev-7' })

  assert.equal(session.loading, true)
  assert.equal(session.input, '')
  assert.deepEqual(session.messages[0], {
    id: 'user-1',
    role: 'user',
    text: 'Who is seated at table 1?',
  })
  assert.equal(calls.length, 1)
  assert.equal(calls[0].meetingId, 'meeting-1')
  assert.equal(calls[0].conversationId, 'conversation-1')
  assert.equal(calls[0].message, 'Who is seated at table 1?')
  assert.equal(calls[0].workspaceRevision, 'rev-7')
  assert.equal(calls[0].mode, 'QUERY')
  assert.equal(typeof calls[0].onEvent, 'function')
  assert.equal(calls[0].signal.aborted, false)

  request.resolve()
  await pending

  assert.equal(session.loading, false)
})

test('stream events are reduced into assistant text and tool trace', async () => {
  const request = deferred()
  const session = createAgentChatSession({
    sendAgentChat({ onEvent }) {
      onEvent({ type: 'RUN_STARTED', runId: 'run-1', payload: {} })
      onEvent({
        type: 'TOOL_CALL',
        runId: 'run-1',
        payload: { toolName: 'queryWorkspace', args: { tableId: 'T1' } },
      })
      onEvent({
        type: 'TOOL_RESULT',
        runId: 'run-1',
        payload: { toolName: 'queryWorkspace', result: { count: 3 } },
      })
      onEvent({ type: 'ASSISTANT_TEXT', runId: 'run-1', payload: { text: 'Table 1 has ' } })
      onEvent({ type: 'ASSISTANT_TEXT', runId: 'run-1', payload: { text: '3 people.' } })
      onEvent({ type: 'RUN_DONE', runId: 'run-1', payload: {} })
      return request.promise
    },
    reduceAgentMessages,
    createAbortController: createControllerSpy,
    createConversationId: () => 'conversation-1',
    createMessageId: () => 'user-1',
  })

  session.input = 'Show table 1'
  const pending = session.send({ meetingId: 'meeting-1', workspaceRevision: 'rev-7' })

  assert.equal(session.messages.length, 2)
  assert.equal(session.messages[1].role, 'assistant')
  assert.equal(session.messages[1].text, 'Table 1 has 3 people.')
  assert.equal(session.messages[1].status, 'done')
  assert.deepEqual(
    session.messages[1].toolTrace.map((trace) => [trace.type, trace.toolName]),
    [
      ['TOOL_CALL', 'queryWorkspace'],
      ['TOOL_RESULT', 'queryWorkspace'],
    ],
  )

  request.resolve()
  await pending
})

test('enter submits the current input once', async () => {
  let calls = 0
  let prevented = 0
  const session = createAgentChatSession({
    sendAgentChat() {
      calls += 1
      return Promise.resolve()
    },
    reduceAgentMessages,
    createAbortController: createControllerSpy,
    createConversationId: () => 'conversation-1',
    createMessageId: () => `user-${calls + 1}`,
  })

  session.input = 'Ask from keyboard'
  await session.handleEnter(
    {
      key: 'Enter',
      preventDefault() {
        prevented += 1
      },
    },
    { meetingId: 'meeting-1', workspaceRevision: 'rev-7' },
  )

  assert.equal(prevented, 1)
  assert.equal(calls, 1)
})

test('empty input and in-flight requests do not submit again', async () => {
  const request = deferred()
  let calls = 0
  const session = createAgentChatSession({
    sendAgentChat() {
      calls += 1
      return request.promise
    },
    reduceAgentMessages,
    createAbortController: createControllerSpy,
    createConversationId: () => 'conversation-1',
    createMessageId: () => `user-${calls + 1}`,
  })

  session.input = '   '
  assert.equal(await session.send({ meetingId: 'meeting-1', workspaceRevision: 'rev-7' }), false)
  assert.equal(calls, 0)

  session.input = 'First real query'
  const pending = session.send({ meetingId: 'meeting-1', workspaceRevision: 'rev-7' })
  session.input = 'Second query'
  assert.equal(await session.send({ meetingId: 'meeting-1', workspaceRevision: 'rev-7' }), false)
  assert.equal(calls, 1)

  request.resolve()
  await pending
})

test('dispose aborts the active request', async () => {
  const request = deferred()
  const controller = createControllerSpy()
  const session = createAgentChatSession({
    sendAgentChat() {
      return request.promise
    },
    reduceAgentMessages,
    createAbortController: () => controller,
    createConversationId: () => 'conversation-1',
    createMessageId: () => 'user-1',
  })

  session.input = 'Cancel this query'
  const pending = session.send({ meetingId: 'meeting-1', workspaceRevision: 'rev-7' })

  session.dispose()

  assert.equal(controller.abortCalls, 1)
  assert.equal(controller.signal.aborted, true)
  assert.equal(session.loading, false)

  request.resolve()
  await pending
})
