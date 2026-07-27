export const MIN_DISPLAY_CELL_SIZE = 44

export function displayCellUnit(zoom = 1) {
  const numericZoom = Number(zoom)
  const safeZoom = Number.isFinite(numericZoom) && numericZoom > 0 ? numericZoom : 1
  return MIN_DISPLAY_CELL_SIZE * safeZoom
}

export function elementBox(element, unit) {
  const inset = element?.kind === 'SEAT' ? Math.max(1.5, Math.round(unit * 0.08) / 2) : 0
  return {
    left: (element.column - 1) * unit + inset,
    top: (element.row - 1) * unit + inset,
    width: Math.max(1, element.columnSpan * unit - inset * 2),
    height: Math.max(1, element.rowSpan * unit - inset * 2),
  }
}

export function previewFitZoom({
  gridColumns,
  gridRows,
  viewportWidth,
  viewportHeight,
}) {
  const unit = displayCellUnit()
  const fittedZoom = Math.min(
    (viewportWidth - 80) / (Math.max(1, gridColumns) * unit),
    (viewportHeight - 80) / (Math.max(1, gridRows) * unit),
  )
  const minimumReadableZoom = 38 / unit
  return Number(Math.min(1.2, Math.max(0.25, minimumReadableZoom, fittedZoom)).toFixed(2))
}
