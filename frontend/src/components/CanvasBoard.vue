<script setup>
import { ref } from 'vue'

defineOptions({ inheritAttrs: false })

defineProps({
  grid: { type: Boolean, default: false },
})

const boardRef = ref()

function getElement() {
  return boardRef.value
}

function getBoundingClientRect() {
  return boardRef.value?.getBoundingClientRect?.()
}

function setPointerCapture(pointerId) {
  boardRef.value?.setPointerCapture?.(pointerId)
}

function hasPointerCapture(pointerId) {
  return boardRef.value?.hasPointerCapture?.(pointerId) || false
}

function releasePointerCapture(pointerId) {
  boardRef.value?.releasePointerCapture?.(pointerId)
}

defineExpose({
  getElement,
  getBoundingClientRect,
  setPointerCapture,
  hasPointerCapture,
  releasePointerCapture,
})
</script>

<template>
  <div
    ref="boardRef"
    class="app-canvas-board"
    :class="[{ 'grid-board': grid }, $attrs.class]"
    v-bind="{ ...$attrs, class: undefined }"
  >
    <slot />
  </div>
</template>

<style scoped>
.app-canvas-board {
  flex: none;
  position: relative;
  box-sizing: border-box;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  box-shadow: var(--shadow);
}

.app-canvas-board.grid-board {
  background:
    linear-gradient(rgba(34, 73, 122, 0.09) 1px, transparent 1px),
    linear-gradient(90deg, rgba(34, 73, 122, 0.09) 1px, transparent 1px),
    #fff;
  background-size: var(--editor-cell, var(--unit, 44px)) var(--editor-cell, var(--unit, 44px));
}
</style>
