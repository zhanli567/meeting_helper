const DEFAULT_ERROR_MESSAGE = '查询失败，请稍后重试'
const DEFAULT_ASSISTANT_TEXT_SLICE_LENGTH = 4
const DEFAULT_ASSISTANT_TEXT_DELAY_MS = 14

function defaultConversationId() {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }
  return `conversation-${Date.now()}`
}

function defaultMessageId() {
  return `user-${Date.now()}`
}

function defaultAbortController() {
  return new AbortController()
}

function defaultWait(ms) {
  return new Promise((resolve) => {
    globalThis.setTimeout(resolve, ms)
  })
}

function abortError() {
  const error = new Error('Agent chat request aborted')
  error.name = 'AbortError'
  return error
}

function createUserMessage(id, text) {
  return {
    id,
    role: 'user',
    text,
  }
}

function isPlainEnter(event) {
  return (
    event?.key === 'Enter' &&
    !event.shiftKey &&
    !event.ctrlKey &&
    !event.altKey &&
    !event.metaKey &&
    !event.isComposing
  )
}

export function createAgentChatSession({
  sendAgentChat,
  reduceAgentMessages,
  createAbortController = defaultAbortController,
  createConversationId = defaultConversationId,
  createMessageId = defaultMessageId,
  assistantTextSliceLength = DEFAULT_ASSISTANT_TEXT_SLICE_LENGTH,
  assistantTextDelayMs = DEFAULT_ASSISTANT_TEXT_DELAY_MS,
  wait = defaultWait,
} = {}) {
  if (typeof sendAgentChat !== 'function') {
    throw new TypeError('sendAgentChat is required')
  }
  if (typeof reduceAgentMessages !== 'function') {
    throw new TypeError('reduceAgentMessages is required')
  }

  let activeController
  let activeRequestId = 0
  const textSliceLength = Math.max(1, Number(assistantTextSliceLength) || DEFAULT_ASSISTANT_TEXT_SLICE_LENGTH)
  const textDelayMs = Math.max(0, Number(assistantTextDelayMs) || 0)

  async function applyAssistantTextEvent(target, event, signal) {
    const text = String(event?.payload?.text || '')
    if (!text) {
      target.messages = reduceAgentMessages(target.messages, event)
      return
    }

    for (let index = 0; index < text.length; index += textSliceLength) {
      if (signal?.aborted) {
        throw abortError()
      }
      const nextEvent = {
        ...event,
        payload: {
          ...event.payload,
          text: text.slice(index, index + textSliceLength),
        },
      }
      target.messages = reduceAgentMessages(target.messages, nextEvent)
      if (index + textSliceLength < text.length && textDelayMs > 0) {
        await wait(textDelayMs)
      }
    }
  }

  async function applyAgentEvent(target, event, signal) {
    if (event?.type === 'ASSISTANT_TEXT') {
      await applyAssistantTextEvent(target, event, signal)
      return
    }
    target.messages = reduceAgentMessages(target.messages, event)
  }

  return {
    input: '',
    messages: [],
    loading: false,
    conversationId: createConversationId(),

    get streaming() {
      return this.loading
    },

    async send({ meetingId = '', workspaceRevision = '', disabled = false } = {}) {
      const message = this.input.trim()
      if (!message || this.loading || disabled || !meetingId) {
        return false
      }

      const controller = createAbortController()
      const requestId = activeRequestId + 1
      activeRequestId = requestId
      activeController = controller

      this.messages = [...this.messages, createUserMessage(createMessageId(), message)]
      this.input = ''
      this.loading = true

      try {
        await sendAgentChat({
          meetingId,
          conversationId: this.conversationId,
          message,
          workspaceRevision,
          mode: 'QUERY',
          signal: controller.signal,
          onEvent: async (event) => {
            await applyAgentEvent(this, event, controller.signal)
          },
        })
        return true
      } catch (error) {
        if (error?.name !== 'AbortError') {
          this.messages = reduceAgentMessages(this.messages, {
            type: 'ERROR',
            payload: { message: error?.message || DEFAULT_ERROR_MESSAGE },
          })
        }
        return false
      } finally {
        if (activeController === controller && activeRequestId === requestId) {
          activeController = undefined
          this.loading = false
        }
      }
    },

    handleEnter(event, context) {
      if (!isPlainEnter(event)) {
        return false
      }
      event.preventDefault?.()
      return this.send(context)
    },

    cancel() {
      if (!activeController) {
        this.loading = false
        return false
      }
      activeController.abort()
      activeController = undefined
      this.loading = false
      return true
    },

    dispose() {
      return this.cancel()
    },
  }
}
