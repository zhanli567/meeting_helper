function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

export function placeFabInRegion({ region, button, viewport, gap = 24, minTop = 64, margin = 4 }) {
  return {
    x: clamp(region.right - button.width - gap, margin, viewport.width - button.width - margin),
    y: clamp(region.bottom - button.height - gap, minTop, viewport.height - button.height - margin),
  }
}
