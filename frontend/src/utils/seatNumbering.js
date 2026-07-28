const isSeat = (element) => element?.kind === 'SEAT'

export function computeSeatLabels(elements) {
  const seats = (elements || [])
    .filter(isSeat)
    .map((element) => ({
      id: element.id,
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
