const DEFAULT_ERROR_MESSAGE = '查询失败，请稍后重试'

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
} = {}) {
  if (typeof sendAgentChat !== 'function') {
    throw new TypeError('sendAgentChat is required')
  }
  if (typeof reduceAgentMessages !== 'function') {
    throw new TypeError('reduceAgentMessages is required')
  }

  let activeController
  let activeRequestId = 0

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
          onEvent: (event) => {
            this.messages = reduceAgentMessages(this.messages, event)
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
