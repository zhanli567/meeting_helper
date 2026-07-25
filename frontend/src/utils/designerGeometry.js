function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

export function moveRect(rect, deltaRows, deltaColumns, bounds) {
  return {
    ...rect,
    row: clamp(rect.row + deltaRows, 1, bounds.rows - rect.rowSpan + 1),
    column: clamp(rect.column + deltaColumns, 1, bounds.columns - rect.columnSpan + 1),
  }
}

export function resizeRect(rect, handle, deltaRows, deltaColumns, bounds) {
  let top = rect.row
  let bottom = rect.row + rect.rowSpan - 1
  let left = rect.column
  let right = rect.column + rect.columnSpan - 1

  if (handle.includes('n')) top = clamp(top + deltaRows, 1, bottom)
  if (handle.includes('s')) bottom = clamp(bottom + deltaRows, top, bounds.rows)
  if (handle.includes('w')) left = clamp(left + deltaColumns, 1, right)
  if (handle.includes('e')) right = clamp(right + deltaColumns, left, bounds.columns)

  return {
    row: top,
    column: left,
    rowSpan: bottom - top + 1,
    columnSpan: right - left + 1,
  }
}

export function activeSelectionRect(drawingRect, pendingRect) {
  return drawingRect || pendingRect
}

export function shouldDismissDesignerOverlays({ hasOverlay, insideOverlay }) {
  return Boolean(hasOverlay) && !insideOverlay
}
