import { nextTick } from 'vue'

export function scheduleCanvasCenter(centerCanvas) {
  nextTick(centerCanvas)
  const frame =
    typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function'
      ? window.requestAnimationFrame.bind(window)
      : undefined
  if (frame) {
    frame(centerCanvas)
  }
  const defer =
    typeof window !== 'undefined' && typeof window.setTimeout === 'function'
      ? window.setTimeout.bind(window)
      : globalThis.setTimeout
  if (typeof defer === 'function') {
    defer(centerCanvas, 0)
  }
}
