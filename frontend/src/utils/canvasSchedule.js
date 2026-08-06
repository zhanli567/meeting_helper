import { nextTick } from 'vue'

export function scheduleCanvasReset(resetViewport) {
  nextTick(resetViewport)
  const frame =
    typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function'
      ? window.requestAnimationFrame.bind(window)
      : undefined
  if (frame) {
    frame(resetViewport)
  }
  const defer =
    typeof window !== 'undefined' && typeof window.setTimeout === 'function'
      ? window.setTimeout.bind(window)
      : globalThis.setTimeout
  if (typeof defer === 'function') {
    defer(resetViewport, 0)
  }
}
