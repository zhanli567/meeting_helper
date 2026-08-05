import { apiBaseUrl } from '../../utils/apiPath.js'
import { parseAgentEventStreamChunk } from '../runtime/eventStream.js'

async function readAgentStream(response, onEvent) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finished = false

  while (!finished) {
    const result = await reader.read()
    finished = result.done
    if (!result.value) {
      continue
    }
    const parsed = parseAgentEventStreamChunk(buffer, decoder.decode(result.value, { stream: true }))
    buffer = parsed.buffer
    parsed.events.forEach(onEvent)
  }

  const tail = decoder.decode()
  const parsed = parseAgentEventStreamChunk(buffer, `${tail}\n\n`)
  parsed.events.forEach(onEvent)
}
export async function sendAgentChat({
  meetingId,
  conversationId,
  message,
  workspaceRevision,
  mode,
  onEvent,
  signal,
}) {
  const response = await fetch(`${apiBaseUrl(Boolean(import.meta.env?.DEV))}/agent/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ conversationId, meetingId, workspaceRevision, message, stream: true, mode }),
    signal,
  })

  if (!response.ok) {
    throw new Error(`Agent chat request failed (${response.status})`)
  }
  if (!response.body) {
    throw new Error('Agent chat response has no stream body')
  }

  await readAgentStream(response, onEvent)
}
