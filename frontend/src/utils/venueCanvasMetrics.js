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

function positiveNumber(value, fallback) {
  const numericValue = Number(value)
  if (Number.isFinite(numericValue) && numericValue > 0) {
    return numericValue
  }
  return fallback
}

function nonNegativeNumber(value, fallback) {
  const numericValue = Number(value)
  if (Number.isFinite(numericValue) && numericValue >= 0) {
    return numericValue
  }
  return fallback
}

function fittedZoomValue(options = {}) {
  const horizontalPadding = nonNegativeNumber(options.horizontalPadding ?? options.padding, 136)
  const verticalPadding = nonNegativeNumber(options.verticalPadding ?? options.padding, 64)
  const unit = displayCellUnit()
  return Math.min(
    (positiveNumber(options.viewportWidth, horizontalPadding + unit) - horizontalPadding) /
      (positiveNumber(options.gridColumns, 1) * unit),
    (positiveNumber(options.viewportHeight, verticalPadding + unit) - verticalPadding) /
      (positiveNumber(options.gridRows, 1) * unit),
  )
}

export function fitCanvasZoom(options) {
  const fittedZoom = fittedZoomValue(options)
  const minZoom = positiveNumber(options?.minZoom, 0.25)
  const maxZoom = positiveNumber(options?.maxZoom, 1)
  return Number(Math.min(maxZoom, Math.max(minZoom, fittedZoom)).toFixed(2))
}

export function previewFitZoom(options) {
  const unit = displayCellUnit()
  const fittedZoom = fittedZoomValue({
    ...options,
    horizontalPadding: options?.horizontalPadding ?? options?.padding ?? 80,
    verticalPadding: options?.verticalPadding ?? options?.padding ?? 80,
  })
  const minimumReadableZoom = 38 / unit
  return Number(Math.min(1.2, Math.max(0.25, minimumReadableZoom, fittedZoom)).toFixed(2))
}
