<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { sendAgentChat } from '../api/agentClient.js'
import { createAgentChatSession } from '../runtime/chatSession.js'
import { reduceAgentMessages } from '../runtime/messages.js'

const props = defineProps({
  meetingId: {
    type: String,
    default: '',
  },
  workspaceRevision: {
    type: String,
    default: '',
  },
  disabled: {
    type: Boolean,
    default: false,
  },
})

const open = ref(false)
const session = reactive(createAgentChatSession({ sendAgentChat, reduceAgentMessages }))

function submitMessage() {
  return session.send({
    meetingId: props.meetingId,
    workspaceRevision: props.workspaceRevision,
    disabled: props.disabled,
  })
}

function handleEnter(event) {
  return session.handleEnter(event, {
    meetingId: props.meetingId,
    workspaceRevision: props.workspaceRevision,
    disabled: props.disabled,
  })
}

function formatToolTrace(trace) {
  if (trace.type === 'TOOL_CALL') {
    return `${trace.toolName || '查询工具'}：调用中`
  }
  return `${trace.toolName || '查询工具'}：已返回结果`
}

function toggleOpen() {
  if (!props.disabled) {
    open.value = !open.value
  }
}

onBeforeUnmount(() => session.dispose())
</script>

<template>
  <div class="agent-chat-panel">
    <button
      class="agent-chat-trigger"
      type="button"
      :disabled="disabled"
      aria-label="打开 AI 查询助手"
      @click="toggleOpen"
    >
      AI 查询助手
    </button>

    <section v-if="open" class="agent-chat-window" aria-label="AI 查询助手">
      <header class="agent-chat-header">
        <div>
          <h2>AI 查询助手</h2>
          <p>当前阶段只执行查询，不会保存或发布排座。</p>
        </div>
        <button type="button" class="agent-chat-close" aria-label="关闭" @click="open = false">
          ×
        </button>
      </header>

      <div class="agent-chat-messages" aria-live="polite">
        <p v-if="!session.messages.length" class="agent-chat-empty">
          可以问我参会人员、座位和排座情况。
        </p>
        <article
          v-for="message in session.messages"
          :key="message.id"
          class="agent-chat-message"
          :class="`is-${message.role}`"
        >
          <p>{{ message.text }}</p>
          <div v-if="message.systemPrompts?.length" class="agent-chat-warning">
            <p v-for="prompt in message.systemPrompts" :key="prompt">{{ prompt }}</p>
          </div>
          <details v-if="message.toolTrace?.length" class="agent-chat-tools">
            <summary>工具轨迹（{{ message.toolTrace.length }}）</summary>
            <p v-for="(trace, index) in message.toolTrace" :key="`${trace.type}-${index}`">
              {{ formatToolTrace(trace) }}
            </p>
          </details>
        </article>
      </div>

      <form class="agent-chat-composer" @submit.prevent="submitMessage">
        <el-input
          v-model="session.input"
          type="textarea"
          :rows="2"
          :disabled="disabled || session.loading"
          aria-label="输入查询"
          @keydown.enter.exact.prevent="handleEnter"
        />
        <el-button
          type="primary"
          native-type="submit"
          :loading="session.loading"
          :disabled="disabled || session.loading || !session.input.trim() || !meetingId"
        >
          发送
        </el-button>
      </form>
    </section>
  </div>
</template>

<style scoped>
.agent-chat-panel {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 80;
  display: flex;
  align-items: flex-end;
  flex-direction: column;
  gap: 10px;
}

.agent-chat-trigger {
  padding: 12px 18px;
  color: #fff;
  background: var(--brand);
  border: 0;
  border-radius: 999px;
  box-shadow: 0 6px 18px rgba(10, 89, 247, 0.24);
  cursor: pointer;
  font-size: 13px;
}

.agent-chat-trigger:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.agent-chat-window {
  width: min(380px, calc(100vw - 32px));
  max-height: min(600px, calc(100vh - 110px));
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow);
}

.agent-chat-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  background: #fff;
  border-bottom: 1px solid var(--line);
}

.agent-chat-header h2 {
  margin: 0;
  color: var(--ink);
  font-size: 15px;
}

.agent-chat-header p {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 11px;
}

.agent-chat-close {
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--muted);
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 22px;
  line-height: 1;
}

.agent-chat-messages {
  min-height: 180px;
  flex: 1;
  overflow-y: auto;
  padding: 14px;
  background: var(--workspace);
}

.agent-chat-empty {
  margin: 42px 18px;
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}

.agent-chat-message {
  max-width: 88%;
  margin-bottom: 10px;
  padding: 9px 11px;
  color: var(--ink);
  background: #fff;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
}

.agent-chat-message p {
  margin: 0;
  white-space: pre-wrap;
}

.agent-chat-message.is-user {
  margin-left: auto;
  color: #fff;
  background: var(--brand);
}

.agent-chat-warning {
  margin-top: 8px;
  color: #b45309;
  font-size: 12px;
}

.agent-chat-tools {
  margin-top: 9px;
  color: var(--muted);
  font-size: 11px;
}

.agent-chat-tools summary {
  cursor: pointer;
}

.agent-chat-tools p {
  margin-top: 4px;
}

.agent-chat-composer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px;
  background: #fff;
  border-top: 1px solid var(--line);
}

.agent-chat-composer .el-input {
  flex: 1;
}
</style>
