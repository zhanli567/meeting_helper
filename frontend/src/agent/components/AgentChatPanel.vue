<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { sendAgentChat } from '../api/agentClient.js'
import { createAgentChatSession } from '../runtime/chatSession.js'
import { renderAgentMarkdown } from '../runtime/markdown.js'
import { reduceAgentMessages } from '../runtime/messages.js'
import assistantAvatarUrl from '../../assets/agent-assistant-avatar.svg'

const DEFAULT_WIDTH = 440
const DEFAULT_HEIGHT = 620
const MIN_WIDTH = 340
const MIN_HEIGHT = 420
const MAX_WIDTH = 760
const MAX_HEIGHT = 780
const VIEWPORT_MARGIN = 12
const LAUNCHER_SIZE = 74
const resizeDirections = ['n', 'e', 's', 'w', 'ne', 'se', 'sw', 'nw']

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
const minimized = ref(false)
const suppressLauncherClick = ref(false)
const messageListRef = ref(null)
const panelRect = reactive(defaultPanelRect())
const launcherRect = reactive(defaultLauncherRect())
const pointerSession = reactive({
  type: '',
  pointerId: undefined,
  direction: '',
  startX: 0,
  startY: 0,
  startLeft: 0,
  startTop: 0,
  startWidth: 0,
  startHeight: 0,
  moved: false,
})
const session = reactive(createAgentChatSession({ sendAgentChat, reduceAgentMessages }))

const panelStyle = computed(() => ({
  left: `${panelRect.x}px`,
  top: `${panelRect.y}px`,
  width: `${panelRect.width}px`,
  height: `${panelRect.height}px`,
}))

const launcherStyle = computed(() => ({
  left: `${launcherRect.x}px`,
  top: `${launcherRect.y}px`,
}))

const isEmptyConversation = computed(() => session.messages.length === 0)

function viewportWidth() {
  return typeof window === 'undefined' ? 1280 : window.innerWidth
}

function viewportHeight() {
  return typeof window === 'undefined' ? 820 : window.innerHeight
}

function clamp(value, min, max) {
  if (max < min) {
    return min
  }
  return Math.min(max, Math.max(min, value))
}

function panelWidth(value) {
  const maxWidth = Math.max(280, Math.min(MAX_WIDTH, viewportWidth() - VIEWPORT_MARGIN * 2))
  return clamp(value, Math.min(MIN_WIDTH, maxWidth), maxWidth)
}

function panelHeight(value) {
  const maxHeight = Math.max(320, Math.min(MAX_HEIGHT, viewportHeight() - VIEWPORT_MARGIN * 2))
  return clamp(value, Math.min(MIN_HEIGHT, maxHeight), maxHeight)
}

function defaultPanelRect() {
  const width = panelWidth(DEFAULT_WIDTH)
  const height = panelHeight(DEFAULT_HEIGHT)
  return {
    width,
    height,
    x: Math.max(VIEWPORT_MARGIN, viewportWidth() - width - 24),
    y: Math.max(VIEWPORT_MARGIN, viewportHeight() - height - 24),
  }
}

function defaultLauncherRect() {
  return {
    x: Math.max(VIEWPORT_MARGIN, viewportWidth() - LAUNCHER_SIZE - 24),
    y: Math.max(VIEWPORT_MARGIN, viewportHeight() - LAUNCHER_SIZE - 24),
  }
}

function clampPanel() {
  panelRect.width = panelWidth(panelRect.width)
  panelRect.height = panelHeight(panelRect.height)
  panelRect.x = clamp(
    panelRect.x,
    VIEWPORT_MARGIN,
    viewportWidth() - panelRect.width - VIEWPORT_MARGIN,
  )
  panelRect.y = clamp(
    panelRect.y,
    VIEWPORT_MARGIN,
    viewportHeight() - panelRect.height - VIEWPORT_MARGIN,
  )
}

function clampLauncher() {
  launcherRect.x = clamp(launcherRect.x, VIEWPORT_MARGIN, viewportWidth() - LAUNCHER_SIZE - VIEWPORT_MARGIN)
  launcherRect.y = clamp(launcherRect.y, VIEWPORT_MARGIN, viewportHeight() - LAUNCHER_SIZE - VIEWPORT_MARGIN)
}

function restorePanel() {
  if (suppressLauncherClick.value) {
    suppressLauncherClick.value = false
    return
  }
  if (props.disabled) {
    return
  }
  clampPanel()
  open.value = true
  minimized.value = false
  scrollToBottom()
}

function minimizePanel() {
  minimized.value = true
  removePointerListeners()
}

function closePanel() {
  open.value = false
  minimized.value = false
  removePointerListeners()
}

function toggleOpen() {
  if (suppressLauncherClick.value) {
    suppressLauncherClick.value = false
    return
  }
  if (open.value && !minimized.value) {
    closePanel()
    return
  }
  restorePanel()
}

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

function formatActivityTrace(trace) {
  if (trace.type === 'TOOL_CALL') {
    return `${trace.toolName || '查询工具'}：调用中`
  }
  return `${trace.toolName || '查询工具'}：已返回结果`
}

function startDrag(event) {
  startPointerSession(event, 'drag')
}

function startLauncherDrag(event) {
  if (props.disabled) {
    return
  }
  startPointerSession(event, 'launcher-drag')
}

function startResize(event, direction) {
  startPointerSession(event, 'resize', direction)
}

function startPointerSession(event, type, direction = '') {
  if (event.button !== undefined && event.button !== 0) {
    return
  }
  pointerSession.type = type
  pointerSession.pointerId = event.pointerId
  pointerSession.direction = direction
  pointerSession.startX = event.clientX
  pointerSession.startY = event.clientY
  pointerSession.startLeft = panelRect.x
  pointerSession.startTop = panelRect.y
  pointerSession.startWidth = panelRect.width
  pointerSession.startHeight = panelRect.height
  pointerSession.moved = false
  if (type === 'launcher-drag') {
    pointerSession.startLeft = launcherRect.x
    pointerSession.startTop = launcherRect.y
    pointerSession.startWidth = LAUNCHER_SIZE
    pointerSession.startHeight = LAUNCHER_SIZE
  }
  window.addEventListener('pointermove', handlePointerMove)
  window.addEventListener('pointerup', stopPointerSession)
  window.addEventListener('pointercancel', stopPointerSession)
  event.preventDefault()
}

function handlePointerMove(event) {
  if (!pointerSession.type || event.pointerId !== pointerSession.pointerId) {
    return
  }
  const deltaX = event.clientX - pointerSession.startX
  const deltaY = event.clientY - pointerSession.startY
  pointerSession.moved = pointerSession.moved || Math.abs(deltaX) > 4 || Math.abs(deltaY) > 4

  if (pointerSession.type === 'drag') {
    panelRect.x = pointerSession.startLeft + deltaX
    panelRect.y = pointerSession.startTop + deltaY
  }
  if (pointerSession.type === 'launcher-drag') {
    launcherRect.x = pointerSession.startLeft + deltaX
    launcherRect.y = pointerSession.startTop + deltaY
    clampLauncher()
    return
  }
  if (pointerSession.type === 'resize') {
    resizePanel(pointerSession.direction, deltaX, deltaY)
    return
  }
  clampPanel()
}

function resizePanel(direction, deltaX, deltaY) {
  const fromWest = direction.includes('w')
  const fromEast = direction.includes('e')
  const fromNorth = direction.includes('n')
  const fromSouth = direction.includes('s')
  const nextWidth = panelWidth(
    pointerSession.startWidth + (fromEast ? deltaX : 0) - (fromWest ? deltaX : 0),
  )
  const nextHeight = panelHeight(
    pointerSession.startHeight + (fromSouth ? deltaY : 0) - (fromNorth ? deltaY : 0),
  )

  panelRect.width = nextWidth
  panelRect.height = nextHeight
  if (fromWest) {
    panelRect.x = pointerSession.startLeft + pointerSession.startWidth - nextWidth
  }
  if (fromEast) {
    panelRect.x = pointerSession.startLeft
  }
  if (fromNorth) {
    panelRect.y = pointerSession.startTop + pointerSession.startHeight - nextHeight
  }
  if (fromSouth) {
    panelRect.y = pointerSession.startTop
  }
  clampPanel()
}

function stopPointerSession() {
  if (pointerSession.type === 'launcher-drag' && pointerSession.moved) {
    suppressLauncherClick.value = true
  }
  pointerSession.type = ''
  pointerSession.pointerId = undefined
  pointerSession.direction = ''
  pointerSession.moved = false
  removePointerListeners()
}

function removePointerListeners() {
  if (typeof window === 'undefined') {
    return
  }
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerup', stopPointerSession)
  window.removeEventListener('pointercancel', stopPointerSession)
}

function scrollToBottom() {
  nextTick(() => {
    const list = messageListRef.value
    if (list) {
      list.scrollTop = list.scrollHeight
    }
  })
}

function handleViewportResize() {
  clampPanel()
  clampLauncher()
}

watch(
  () => session.messages.map((message) => `${message.id}:${message.text}:${message.status}`).join('|'),
  () => scrollToBottom(),
)

onMounted(() => {
  handleViewportResize()
  window.addEventListener('resize', handleViewportResize)
})

onBeforeUnmount(() => {
  session.dispose()
  removePointerListeners()
  window.removeEventListener('resize', handleViewportResize)
})
</script>

<template>
  <div class="agent-chat-panel">
    <button
      v-if="!open"
      class="agent-chat-trigger"
      :class="{ 'is-dragging': pointerSession.type === 'launcher-drag' }"
      type="button"
      :disabled="disabled"
      :style="launcherStyle"
      aria-label="打开 AI 查询助手"
      @pointerdown="startLauncherDrag"
      @click="toggleOpen"
    >
      <img class="agent-chat-launcher-image" :src="assistantAvatarUrl" alt="" draggable="false">
    </button>

    <button
      v-if="open && minimized"
      class="agent-chat-minimized"
      :class="{ 'is-dragging': pointerSession.type === 'launcher-drag' }"
      type="button"
      :disabled="disabled"
      :style="launcherStyle"
      aria-label="还原 AI 查询助手"
      @pointerdown="startLauncherDrag"
      @click="restorePanel"
    >
      <img class="agent-chat-launcher-image" :src="assistantAvatarUrl" alt="" draggable="false">
      <span v-if="session.loading" class="agent-chat-loading-dot" aria-hidden="true" />
    </button>

    <section
      v-if="open && !minimized"
      class="agent-chat-window"
      :class="{ 'is-dragging': pointerSession.type === 'drag', 'is-resizing': pointerSession.type === 'resize' }"
      :style="panelStyle"
      aria-label="AI 查询助手"
    >
      <header class="agent-chat-header">
        <div class="agent-chat-drag-handle" title="拖动移动对话框" @pointerdown="startDrag">
          <img class="agent-chat-header-avatar" :src="assistantAvatarUrl" alt="" draggable="false">
          <span>排座助手</span>
          <em class="agent-chat-mode-chip">只读查询</em>
        </div>
        <div class="agent-chat-actions">
          <button
            type="button"
            class="agent-chat-icon-button"
            aria-label="最小化"
            @click.stop="minimizePanel"
          >
            −
          </button>
          <button type="button" class="agent-chat-icon-button" aria-label="关闭" @click.stop="closePanel">
            ×
          </button>
        </div>
      </header>

      <main class="agent-chat-body" :class="{ 'is-empty': isEmptyConversation }">
        <div v-if="isEmptyConversation" class="agent-chat-welcome">
          <img class="agent-chat-welcome-avatar" :src="assistantAvatarUrl" alt="" draggable="false">
          <h3>我们从哪里开始排座？</h3>
          <p>可以问我参会人员、座位和排座情况。</p>
          <div class="agent-chat-suggestions">
            <span>查看排座情况</span>
            <span>现在有谁坐在座位上</span>
            <span>获取工作区摘要</span>
          </div>
        </div>

        <div v-if="!isEmptyConversation" ref="messageListRef" class="agent-chat-messages" aria-live="polite">
          <article
            v-for="message in session.messages"
            :key="message.id"
            class="agent-chat-message"
            :class="`is-${message.role}`"
            :data-status="message.status"
          >
            <div
              v-if="message.role === 'assistant'"
              class="agent-chat-markdown"
              v-html="renderAgentMarkdown(message.text)"
            />
            <p v-else class="agent-chat-message-text">{{ message.text }}</p>
            <div v-if="message.systemPrompts?.length" class="agent-chat-warning">
              <p v-for="prompt in message.systemPrompts" :key="prompt">{{ prompt }}</p>
            </div>
            <details v-if="message.toolTrace?.length" class="agent-chat-activity">
              <summary>
                <span>查询过程</span>
                <em>{{ message.toolTrace.length }}</em>
              </summary>
              <ol>
                <li v-for="(trace, index) in message.toolTrace" :key="`${trace.type}-${index}`">
                  {{ formatActivityTrace(trace) }}
                </li>
              </ol>
            </details>
          </article>
        </div>

        <form
          class="agent-chat-composer"
          :class="{ 'is-centered': isEmptyConversation }"
          @submit.prevent="submitMessage"
        >
          <el-input
            v-model="session.input"
            type="textarea"
            :rows="2"
            :disabled="disabled || session.loading"
            aria-label="输入查询"
            resize="none"
            placeholder="问我会议排座相关问题，Enter 发送，Shift+Enter 换行"
            @keydown.enter.exact.prevent="handleEnter"
          />
          <div class="agent-chat-submit-group">
            <el-button
              v-if="session.loading"
              type="default"
              plain
              @click="session.cancel()"
            >
              停止
            </el-button>
            <el-button
              type="primary"
              native-type="submit"
              :loading="session.loading"
              :disabled="disabled || session.loading || !session.input.trim() || !meetingId"
            >
              发送
            </el-button>
          </div>
        </form>
      </main>

      <div
        v-for="direction in resizeDirections"
        :key="direction"
        class="agent-chat-resize-zone"
        :class="`is-${direction}`"
        role="separator"
        aria-orientation="both"
        :aria-label="`拖动${direction}方向调整对话框大小`"
        @pointerdown="startResize($event, direction)"
      />
    </section>
  </div>
</template>

<style scoped>
.agent-chat-panel {
  position: fixed;
  inset: 0;
  z-index: 80;
  pointer-events: none;
}

.agent-chat-trigger,
.agent-chat-minimized,
.agent-chat-window {
  pointer-events: auto;
}

.agent-chat-trigger,
.agent-chat-minimized {
  position: fixed;
  width: 74px;
  height: 74px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 5px;
  background: rgba(255, 255, 255, 0.88);
  border: 0;
  border-radius: 26px;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.22);
  cursor: grab;
  backdrop-filter: blur(12px);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.agent-chat-trigger:hover,
.agent-chat-minimized:hover {
  transform: translateY(-2px);
  box-shadow: 0 22px 50px rgba(15, 23, 42, 0.26);
}

.agent-chat-trigger.is-dragging,
.agent-chat-minimized.is-dragging {
  cursor: grabbing;
  transform: scale(1.02);
}

.agent-chat-launcher-image {
  width: 64px;
  height: 64px;
  display: block;
  border-radius: 22px;
  pointer-events: none;
  user-select: none;
}

.agent-chat-loading-dot {
  position: absolute;
  right: 9px;
  top: 9px;
  width: 11px;
  height: 11px;
  background: #22c55e;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 5px rgba(34, 197, 94, 0.16);
}

.agent-chat-trigger:disabled,
.agent-chat-minimized:disabled {
  cursor: not-allowed;
  opacity: 0.55;
  transform: none;
}

.agent-chat-window {
  position: fixed;
  min-width: 280px;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: var(--ink);
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 18px;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.2);
}

.agent-chat-window.is-dragging,
.agent-chat-window.is-resizing {
  user-select: none;
}

.agent-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 50px;
  padding: 8px 10px 8px 12px;
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid var(--line);
}

.agent-chat-drag-handle {
  min-width: 0;
  flex: 1;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: grab;
}

.agent-chat-window.is-dragging .agent-chat-drag-handle {
  cursor: grabbing;
}

.agent-chat-header-avatar {
  width: 25px;
  height: 25px;
  display: block;
  border-radius: 9px;
  pointer-events: none;
  user-select: none;
}

.agent-chat-drag-handle span {
  color: #111827;
  font-size: 14px;
  font-weight: 600;
}

.agent-chat-mode-chip {
  padding: 2px 7px;
  color: #64748b;
  background: #f1f5f9;
  border-radius: 999px;
  font-size: 11px;
  font-style: normal;
  font-weight: 500;
}

.agent-chat-actions {
  display: inline-flex;
  gap: 6px;
}

.agent-chat-icon-button {
  width: 30px;
  height: 30px;
  padding: 0;
  color: var(--muted);
  background: transparent;
  border: 0;
  border-radius: 9px;
  cursor: pointer;
  font-size: 20px;
  line-height: 1;
}

.agent-chat-icon-button:hover {
  color: var(--ink);
  background: #eef2ff;
}

.agent-chat-body {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.agent-chat-body.is-empty {
  justify-content: center;
  padding: 30px 28px 28px;
}

.agent-chat-messages {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px;
  background: #fff;
}

.agent-chat-welcome {
  max-width: 420px;
  width: 100%;
  margin: 0 auto 20px;
  color: #111827;
  text-align: center;
}

.agent-chat-welcome-avatar {
  width: 42px;
  height: 42px;
  display: block;
  margin: 0 auto 14px;
  border-radius: 15px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.16);
}

.agent-chat-welcome h3 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.agent-chat-welcome p {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.agent-chat-suggestions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 18px;
}

.agent-chat-suggestions span {
  padding: 5px 9px;
  color: #64748b;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  font-size: 12px;
}

.agent-chat-message {
  max-width: 88%;
  margin-bottom: 18px;
  padding: 10px 12px;
  color: var(--ink);
  background: #f8fafc;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.65;
}

.agent-chat-message-text {
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  word-break: break-word;
}

.agent-chat-message.is-user {
  margin-left: auto;
  color: #fff;
  background: var(--brand);
  border-bottom-right-radius: 5px;
}

.agent-chat-message.is-assistant {
  max-width: 100%;
  margin-right: auto;
  padding: 2px 0 0;
  background: transparent;
  border-radius: 0;
}

.agent-chat-message.is-assistant[data-status='streaming'] .agent-chat-markdown::after {
  display: inline-block;
  width: 6px;
  height: 14px;
  margin-left: 3px;
  background: var(--brand);
  border-radius: 999px;
  content: '';
  opacity: 0.75;
  vertical-align: -2px;
  animation: agent-cursor-pulse 1s ease-in-out infinite;
}

.agent-chat-markdown {
  overflow-wrap: anywhere;
  color: #111827;
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;
}

.agent-chat-markdown :deep(p) {
  margin: 0 0 12px;
}

.agent-chat-markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.agent-chat-markdown :deep(h1),
.agent-chat-markdown :deep(h2),
.agent-chat-markdown :deep(h3),
.agent-chat-markdown :deep(h4) {
  margin: 18px 0 10px;
  color: #0f172a;
  font-weight: 700;
  line-height: 1.35;
}

.agent-chat-markdown :deep(h1:first-child),
.agent-chat-markdown :deep(h2:first-child),
.agent-chat-markdown :deep(h3:first-child),
.agent-chat-markdown :deep(h4:first-child) {
  margin-top: 0;
}

.agent-chat-markdown :deep(h1) {
  font-size: 18px;
}

.agent-chat-markdown :deep(h2) {
  font-size: 16px;
}

.agent-chat-markdown :deep(h3),
.agent-chat-markdown :deep(h4) {
  font-size: 15px;
}

.agent-chat-markdown :deep(strong) {
  font-weight: 700;
}

.agent-chat-markdown :deep(ul),
.agent-chat-markdown :deep(ol) {
  margin: 8px 0 12px;
  padding-left: 20px;
}

.agent-chat-markdown :deep(li) {
  margin: 3px 0;
}

.agent-chat-markdown :deep(code) {
  padding: 1px 5px;
  background: #f1f5f9;
  border-radius: 5px;
  color: #334155;
  font-family: Consolas, 'SFMono-Regular', monospace;
  font-size: 0.92em;
}

.agent-chat-markdown :deep(pre) {
  overflow-x: auto;
  margin: 10px 0 12px;
  padding: 12px;
  background: #0f172a;
  border-radius: 10px;
}

.agent-chat-markdown :deep(pre code) {
  padding: 0;
  color: #e2e8f0;
  background: transparent;
}

.agent-chat-markdown :deep(table) {
  width: 100%;
  margin: 10px 0 14px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-collapse: separate;
  border-radius: 10px;
  border-spacing: 0;
  font-size: 13px;
}

.agent-chat-markdown :deep(th),
.agent-chat-markdown :deep(td) {
  padding: 8px 10px;
  border-right: 1px solid #e2e8f0;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
  vertical-align: top;
}

.agent-chat-markdown :deep(th:last-child),
.agent-chat-markdown :deep(td:last-child) {
  border-right: 0;
}

.agent-chat-markdown :deep(tr:last-child td) {
  border-bottom: 0;
}

.agent-chat-markdown :deep(th) {
  background: #f8fafc;
  color: #475569;
  font-weight: 600;
}

.agent-chat-markdown :deep(hr) {
  margin: 16px 0;
  border: 0;
  border-top: 1px solid #e2e8f0;
}

.agent-chat-warning {
  margin-top: 8px;
  color: #b45309;
  font-size: 12px;
}

.agent-chat-warning p {
  margin: 0;
}

.agent-chat-activity {
  margin-top: 8px;
  color: #64748b;
  font-size: 11px;
}

.agent-chat-activity summary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  cursor: pointer;
  list-style: none;
}

.agent-chat-activity summary::-webkit-details-marker {
  display: none;
}

.agent-chat-activity em {
  min-width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #475569;
  background: #e2e8f0;
  border-radius: 50%;
  font-style: normal;
  line-height: 1;
}

.agent-chat-activity ol {
  margin: 7px 0 0;
  padding-left: 18px;
}

.agent-chat-activity li {
  margin: 3px 0;
}

.agent-chat-composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px;
  background: #fff;
  border-top: 1px solid var(--line);
}

.agent-chat-composer.is-centered {
  width: min(100%, 470px);
  margin: 0 auto;
  padding: 11px;
  border: 1px solid #e5e7eb;
  border-radius: 22px;
  box-shadow: 0 14px 42px rgba(15, 23, 42, 0.08);
}

.agent-chat-composer .el-input {
  flex: 1;
}

.agent-chat-submit-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.agent-chat-resize-zone {
  position: absolute;
  z-index: 2;
  background: transparent;
}

.agent-chat-resize-zone.is-n,
.agent-chat-resize-zone.is-s {
  right: 12px;
  left: 12px;
  height: 10px;
  cursor: ns-resize;
}

.agent-chat-resize-zone.is-n {
  top: -3px;
}

.agent-chat-resize-zone.is-s {
  bottom: -3px;
}

.agent-chat-resize-zone.is-e,
.agent-chat-resize-zone.is-w {
  top: 12px;
  bottom: 12px;
  width: 10px;
  cursor: ew-resize;
}

.agent-chat-resize-zone.is-e {
  right: -3px;
}

.agent-chat-resize-zone.is-w {
  left: -3px;
}

.agent-chat-resize-zone.is-ne,
.agent-chat-resize-zone.is-sw {
  width: 16px;
  height: 16px;
  cursor: nesw-resize;
}

.agent-chat-resize-zone.is-ne {
  top: -4px;
  right: -4px;
}

.agent-chat-resize-zone.is-sw {
  bottom: -4px;
  left: -4px;
}

.agent-chat-resize-zone.is-nw,
.agent-chat-resize-zone.is-se {
  width: 16px;
  height: 16px;
  cursor: nwse-resize;
}

.agent-chat-resize-zone.is-nw {
  top: -4px;
  left: -4px;
}

.agent-chat-resize-zone.is-se {
  right: -4px;
  bottom: -4px;
}

@keyframes agent-cursor-pulse {
  0%,
  100% {
    opacity: 0.25;
  }

  50% {
    opacity: 0.9;
  }
}
</style>
