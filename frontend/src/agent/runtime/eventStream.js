function parseEventBlock(block) {
  const data = block
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')

  if (!data) {
    return null
  }

  try {
    return JSON.parse(data)
  } catch (error) {
    return {
      type: 'ERROR',
      payload: {
        code: 'SSE_PARSE_ERROR',
        message: error instanceof Error ? error.message : 'Invalid SSE event',
      },
    }
  }
}
export function parseAgentEventStreamChunk(buffer, chunk) {
  const combined = `${buffer || ''}${chunk || ''}`.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  const events = []
  let remaining = combined
  let separatorIndex = remaining.indexOf('\n\n')

  while (separatorIndex >= 0) {
    const block = remaining.slice(0, separatorIndex)
    const event = parseEventBlock(block)
    if (event) {
      events.push(event)
    }
    remaining = remaining.slice(separatorIndex + 2)
    separatorIndex = remaining.indexOf('\n\n')
  }

  return { buffer: remaining, events }
}
