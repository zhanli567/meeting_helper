function assistantMessage(messages, event) {
  const current = messages[messages.length - 1]
  if (current?.role === 'assistant' && current.status === 'streaming') {
    return { messages, index: messages.length - 1 }
  }

  const message = {
    id: event.runId || `assistant-${messages.length + 1}`,
    role: 'assistant',
    text: '',
    status: 'streaming',
    toolTrace: [],
    systemPrompts: [],
  }
  return { messages: [...messages, message], index: messages.length }
}
function appendToAssistant(messages, event, update) {
  const target = assistantMessage(messages, event)
  const next = [...target.messages]
  next[target.index] = update(next[target.index])
  return next
}

export function reduceAgentMessages(messages, event) {
  const currentMessages = Array.isArray(messages) ? messages : []
  const payload = event?.payload || {}
  const type = event?.type

  if (type === 'RUN_STARTED') {
    return assistantMessage(currentMessages, event).messages
  }
  if (type === 'ASSISTANT_TEXT') {
    return appendToAssistant(currentMessages, event, (message) => ({
      ...message,
      text: `${message.text}${payload.text || ''}`,
    }))
  }
  if (type === 'TOOL_CALL' || type === 'TOOL_RESULT') {
    return appendToAssistant(currentMessages, event, (message) => ({
      ...message,
      toolTrace: [...message.toolTrace, { type, ...payload }],
    }))
  }
  if (type === 'GUARDRAIL_BLOCKED' || type === 'ERROR') {
    const prompt = payload.message || payload.reason || payload.code || 'Agent request failed'
    return appendToAssistant(currentMessages, event, (message) => ({
      ...message,
      systemPrompts: [...message.systemPrompts, prompt],
    }))
  }
  if (type === 'RUN_DONE') {
    return appendToAssistant(currentMessages, event, (message) => ({
      ...message,
      status: 'done',
    }))
  }
  return currentMessages
}
