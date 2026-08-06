<script setup>
import { onMounted, ref, watch } from 'vue'
import { scheduleCanvasReset } from '@/utils/canvasSchedule'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  panning: { type: Boolean, default: false },
  resetKey: { type: [String, Number, Array, Object], default: undefined },
  contentClass: { type: [String, Array, Object], default: undefined },
})

const viewportRef = ref()

function getViewportElement() {
  return viewportRef.value
}

function resetViewport() {
  const viewport = viewportRef.value
  if (!viewport) {
    return
  }
  viewport.scrollLeft = Math.max(0, (viewport.scrollWidth - viewport.clientWidth) / 2)
  viewport.scrollTop = Math.max(0, (viewport.scrollHeight - viewport.clientHeight) / 2)
}

function resetViewportAfterRender() {
  scheduleCanvasReset(resetViewport)
}

defineExpose({
  resetViewport,
  getViewportElement,
})

onMounted(resetViewportAfterRender)
watch(() => props.resetKey, resetViewportAfterRender, { deep: true })
</script>

<template>
  <div
    ref="viewportRef"
    class="app-canvas-viewport"
    :class="[{ panning }, $attrs.class]"
    v-bind="{ ...$attrs, class: undefined }"
  >
    <div class="app-canvas-content" :class="contentClass">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.app-canvas-viewport {
  width: 100%;
  height: 100%;
  overflow: auto;
  background:
    linear-gradient(rgba(0, 0, 0, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 0, 0, 0.045) 1px, transparent 1px),
    #f7f8fa;
  background-size: 24px 24px;
  cursor: default;
  scrollbar-gutter: stable;
}

.app-canvas-viewport.panning {
  cursor: grabbing;
  user-select: none;
}

.app-canvas-content {
  width: max-content;
  min-width: 200%;
  height: max-content;
  min-height: 200%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 26px 68px 38px;
}
</style>
