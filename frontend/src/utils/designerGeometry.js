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

export function rectsOverlap(left, right) {
  return !(
    left.row + left.rowSpan <= right.row ||
    right.row + right.rowSpan <= left.row ||
    left.column + left.columnSpan <= right.column ||
    right.column + right.columnSpan <= left.column
  )
}

export function canPlaceRect(elements, candidate, ignoredId) {
  return elements
    .filter((element) => element.id !== ignoredId)
    .every((element) => !rectsOverlap(element, candidate))
}

export function canvasResizeConflict(elements, rows, columns) {
  return elements.filter(
    (element) =>
      element.row + element.rowSpan - 1 > rows ||
      element.column + element.columnSpan - 1 > columns,
  )
}

export function createSeatElements(rect, mode, defaults = {}) {
  const base = {
    kind: 'SEAT',
    name: defaults.name || '座位',
    fillColor: defaults.fillColor || '#ffffff',
    borderColor: defaults.borderColor || '#8fb4e8',
  }
  if (mode === 'merge') return [{ ...base, ...rect }]

  const seats = []
  for (let row = rect.row; row < rect.row + rect.rowSpan; row += 1) {
    for (let column = rect.column; column < rect.column + rect.columnSpan; column += 1) {
      seats.push({ ...base, row, column, rowSpan: 1, columnSpan: 1 })
    }
  }
  return seats
}
