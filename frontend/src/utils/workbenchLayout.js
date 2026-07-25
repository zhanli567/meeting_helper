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
