export function reservedItems(items) {
  return (items || []).filter((item) => item?.type === 'RESERVED')
}

export function toggleSeatSelection(selection, elementId) {
  const next = new Set(selection || [])
  if (next.has(elementId)) {
    next.delete(elementId)
  } else {
    next.add(elementId)
  }
  return next
}

function seatElementById(elements) {
  return new Map(
    (elements || [])
      .filter((element) => element?.kind === 'SEAT')
      .map((element) => [element.id, element]),
  )
}

function occupiedCells(element) {
  const cells = []
  const rowSpan = Math.max(1, Number(element.rowSpan || 1))
  const columnSpan = Math.max(1, Number(element.columnSpan || 1))
  for (let row = Number(element.row); row < Number(element.row) + rowSpan; row++) {
    for (let column = Number(element.column); column < Number(element.column) + columnSpan; column++) {
      cells.push(`${row}:${column}`)
    }
  }
  return cells
}

function areAdjacent(left, right, cellsById) {
  const leftCells = cellsById.get(left.id) || []
  const rightCells = new Set(cellsById.get(right.id) || [])
  return leftCells.some((cell) => {
    const [row, column] = cell.split(':').map(Number)
    return (
      rightCells.has(`${row - 1}:${column}`) ||
      rightCells.has(`${row + 1}:${column}`) ||
      rightCells.has(`${row}:${column - 1}`) ||
      rightCells.has(`${row}:${column + 1}`)
    )
  })
}

export function connectedSeatGroups(elementIds, elements) {
  const seats = seatElementById(elements)
  const orderedIds = (elementIds || []).filter((id) => seats.has(id))
  const cellsById = new Map(orderedIds.map((id) => [id, occupiedCells(seats.get(id))]))
  const remaining = new Set(orderedIds)
  const groups = []

  for (const startId of orderedIds) {
    if (!remaining.has(startId)) {
      continue
    }
    const queue = [startId]
    const group = []
    remaining.delete(startId)
    while (queue.length) {
      const currentId = queue.shift()
      group.push(currentId)
      for (const candidateId of [...remaining]) {
        if (areAdjacent(seats.get(currentId), seats.get(candidateId), cellsById)) {
          remaining.delete(candidateId)
          queue.push(candidateId)
        }
      }
    }
    groups.push(group)
  }

  return groups
}

export function regionLabelAnchors(regionItem, elements) {
  const seats = seatElementById(elements)
  return connectedSeatGroups(regionItem?.targetElementIds || [], elements).map((elementIds, index) => {
    const selected = elementIds.map((id) => seats.get(id)).filter(Boolean)
    const minRow = Math.min(...selected.map((element) => Number(element.row)))
    const maxRow = Math.max(
      ...selected.map((element) => Number(element.row) + Math.max(1, Number(element.rowSpan || 1)) - 1),
    )
    const minColumn = Math.min(...selected.map((element) => Number(element.column)))
    const maxColumn = Math.max(
      ...selected.map(
        (element) => Number(element.column) + Math.max(1, Number(element.columnSpan || 1)) - 1,
      ),
    )
    return {
      id: `${regionItem?.id || regionItem?.label || 'region'}-${index}`,
      label: regionItem?.label || '',
      elementIds,
      row: minRow,
      column: minColumn,
      rowSpan: maxRow - minRow + 1,
      columnSpan: maxColumn - minColumn + 1,
      centerRow: (minRow + maxRow) / 2,
      centerColumn: (minColumn + maxColumn) / 2,
      backgroundColor: regionItem?.backgroundColor,
      textColor: regionItem?.textColor,
      bold: regionItem?.bold,
      source: regionItem,
    }
  })
}
