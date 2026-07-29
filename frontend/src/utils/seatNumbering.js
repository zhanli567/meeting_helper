const isSeat = (element) => element?.kind === 'SEAT'

export function computeElementColumnBounds(elements, fallback = { minColumn: 1, maxColumn: 1 }) {
  const initial = {
    minColumn: Number(fallback.minColumn || 1),
    maxColumn: Number(fallback.maxColumn || fallback.minColumn || 1),
  }
  const bounds = (elements || []).reduce((current, element) => {
    const column = Number(element?.column)
    if (!Number.isFinite(column)) return current
    const columnSpan = Math.max(1, Number(element?.columnSpan || 1))
    return {
      minColumn: Math.min(current.minColumn, column),
      maxColumn: Math.max(current.maxColumn, column + columnSpan - 1),
      hasElement: true,
    }
  }, { minColumn: Infinity, maxColumn: -Infinity, hasElement: false })

  return bounds.hasElement
    ? { minColumn: bounds.minColumn, maxColumn: bounds.maxColumn }
    : initial
}

export function computeSeatLabels(elements) {
  const seats = (elements || [])
    .filter(isSeat)
    .map((element) => ({
      id: element.id || element.editorId,
      row: Number(element.row),
      column: Number(element.column),
    }))
    .filter((element) => element.id && Number.isFinite(element.row) && Number.isFinite(element.column))

  const sortedRows = [...new Set(seats.map((seat) => seat.row))].sort((left, right) => left - right)
  const displayRowBySource = new Map(sortedRows.map((row, index) => [row, index + 1]))
  const labelsByElementId = new Map()

  sortedRows.forEach((sourceRow) => {
    seats
      .filter((seat) => seat.row === sourceRow)
      .sort((left, right) => left.column - right.column || String(left.id).localeCompare(String(right.id)))
      .forEach((seat, index) => {
        labelsByElementId.set(seat.id, `${displayRowBySource.get(sourceRow)}排${index + 1}`)
      })
  })

  return {
    labelsByElementId,
    rows: sortedRows.map((sourceRow) => ({
      sourceRow,
      displayRow: displayRowBySource.get(sourceRow),
    })),
  }
}
