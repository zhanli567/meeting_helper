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

export function pointerDeltaToGrid(deltaX, deltaY, cellSize, zoom) {
  const scaledUnit = Math.max(1, cellSize * zoom)
  return {
    rows: Math.round(deltaY / scaledUnit),
    columns: Math.round(deltaX / scaledUnit),
  }
}

export function normalizeGridRect(start, current) {
  const row = Math.min(start.row, current.row)
  const column = Math.min(start.column, current.column)
  return {
    row,
    column,
    rowSpan: Math.max(start.row, current.row) - row + 1,
    columnSpan: Math.max(start.column, current.column) - column + 1,
  }
}

export function canvasSizeFromPointer(
  start,
  direction,
  deltaX,
  deltaY,
  cellSize,
  zoom,
  minimumSize,
) {
  const delta = pointerDeltaToGrid(deltaX, deltaY, cellSize, zoom)
  let rows = start.rows
  let columns = start.columns

  if (direction.includes('east')) columns += delta.columns
  if (direction.includes('west')) columns -= delta.columns
  if (direction.includes('south')) rows += delta.rows
  if (direction.includes('north')) rows -= delta.rows

  return {
    rows: Math.max(minimumSize, rows),
    columns: Math.max(minimumSize, columns),
  }
}

export function placePanelBesideRect(rect, viewport, panel, gap = 12) {
  const margin = 12
  const maximumLeft = Math.max(margin, viewport.width - panel.width - margin)
  const maximumTop = Math.max(margin, viewport.height - panel.height - margin)
  const candidates = [
    {
      left: rect.left + rect.width + gap,
      top: clamp(rect.top, margin, maximumTop),
    },
    {
      left: rect.left - panel.width - gap,
      top: clamp(rect.top, margin, maximumTop),
    },
    {
      left: clamp(rect.left, margin, maximumLeft),
      top: rect.top + rect.height + gap,
    },
    {
      left: clamp(rect.left, margin, maximumLeft),
      top: rect.top - panel.height - gap,
    },
  ]
  const withinViewport = (candidate) =>
    candidate.left >= margin &&
    candidate.top >= margin &&
    candidate.left + panel.width <= viewport.width - margin &&
    candidate.top + panel.height <= viewport.height - margin
  const coversSelection = (candidate) =>
    !(
      candidate.left + panel.width <= rect.left ||
      rect.left + rect.width <= candidate.left ||
      candidate.top + panel.height <= rect.top ||
      rect.top + rect.height <= candidate.top
    )
  const available = candidates.find(
    (candidate) => withinViewport(candidate) && !coversSelection(candidate),
  )
  if (available) return available

  return { dock: 'right' }
}

export function appendHistorySnapshot(history, snapshot, limit = 50) {
  const plainSnapshot = {
    gridRows: Number(snapshot.gridRows),
    gridColumns: Number(snapshot.gridColumns),
    elements: (snapshot.elements || []).map((element) => ({ ...element })),
  }
  return [...history, plainSnapshot].slice(-limit)
}

export function canvasAnchorCorrection(direction, startBounds, currentBounds) {
  let x = 0
  let y = 0
  if (direction.includes('west')) x = currentBounds.right - startBounds.right
  else if (direction.includes('east')) x = currentBounds.left - startBounds.left
  if (direction.includes('north')) y = currentBounds.bottom - startBounds.bottom
  else if (direction.includes('south')) y = currentBounds.top - startBounds.top
  return { x, y }
}

export function canvasAnchorAdjustment(
  direction,
  startBounds,
  currentBounds,
  scroll,
) {
  const correction = canvasAnchorCorrection(direction, startBounds, currentBounds)
  const scrollLeft = clamp(
    scroll.left + correction.x,
    0,
    Math.max(0, scroll.maximumLeft),
  )
  const scrollTop = clamp(
    scroll.top + correction.y,
    0,
    Math.max(0, scroll.maximumTop),
  )
  return {
    scrollLeft,
    scrollTop,
    offsetX: scrollLeft - scroll.left - correction.x,
    offsetY: scrollTop - scroll.top - correction.y,
  }
}

export function validElementProperties(properties) {
  const name = String(properties?.name ?? '').trim()
  const colorPattern = /^#[0-9a-fA-F]{6}$/
  return (
    name.length > 0 &&
    name.length <= 80 &&
    colorPattern.test(properties?.fillColor || '')
  )
}
