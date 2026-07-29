import assert from 'node:assert/strict'
import test from 'node:test'

import {
  computeElementColumnBounds,
  computeSeatLabels,
} from '../src/utils/seatNumbering.js'

test('座位编号只统计有座位的行且同排从左到右', () => {
  const result = computeSeatLabels([
    { id: 'stage', kind: 'GENERIC', row: 1, column: 1 },
    { id: 'seat-b', kind: 'SEAT', row: 3, column: 4 },
    { id: 'seat-a', kind: 'SEAT', row: 3, column: 2 },
    { id: 'seat-c', kind: 'SEAT', row: 6, column: 1 },
  ])

  assert.equal(result.labelsByElementId.get('seat-a'), '1排1')
  assert.equal(result.labelsByElementId.get('seat-b'), '1排2')
  assert.equal(result.labelsByElementId.get('seat-c'), '2排1')
  assert.deepEqual(result.rows, [
    { sourceRow: 3, displayRow: 1 },
    { sourceRow: 6, displayRow: 2 },
  ])
})

test('排号锚点按所有元素的最左和最右列计算', () => {
  const result = computeElementColumnBounds([
    { id: 'door', kind: 'GENERIC', row: 1, column: 1, columnSpan: 2 },
    { id: 'seat-a', kind: 'SEAT', row: 3, column: 6, columnSpan: 1 },
    { id: 'stage', kind: 'GENERIC', row: 6, column: 12, columnSpan: 4 },
  ], { minColumn: 1, maxColumn: 30 })

  assert.deepEqual(result, { minColumn: 1, maxColumn: 15 })
})
