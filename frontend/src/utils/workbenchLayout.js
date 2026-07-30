function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

export function placeFloatingMenu({ anchor, menu, viewport, gap = 10, margin = 10 }) {
  const enoughBelow = anchor.y + anchor.height + gap + menu.height <= viewport.height - margin
  const top = enoughBelow
    ? anchor.y + anchor.height + gap
    : anchor.y - menu.height - gap
  const left = clamp(anchor.x, margin, viewport.width - menu.width - margin)

  return {
    left,
    top: clamp(top, margin, viewport.height - menu.height - margin),
  }
}

export function placeFabInRegion({ region, button, viewport, gap = 24, minTop = 64, margin = 4 }) {
  return {
    x: clamp(region.right - button.width - gap, margin, viewport.width - button.width - margin),
    y: clamp(region.bottom - button.height - gap, minTop, viewport.height - button.height - margin),
  }
}
