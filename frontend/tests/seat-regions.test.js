import assert from 'node:assert/strict'
import test from 'node:test'
import {
  connectedSeatGroups,
  regionLabelAnchors,
  reservedItems,
  toggleSeatSelection,
} from '../src/utils/seatRegions.js'

const elements = [
  { id: 'a', kind: 'SEAT', row: 1, column: 1, rowSpan: 1, columnSpan: 1 },
  { id: 'b', kind: 'SEAT', row: 1, column: 2, rowSpan: 1, columnSpan: 1 },
  { id: 'c', kind: 'SEAT', row: 4, column: 4, rowSpan: 1, columnSpan: 1 },
]

test('区域标记只读取 RESERVED 项', () => {
  assert.deepEqual(
    reservedItems([{ type: 'PERSON' }, { type: 'RESERVED', id: 'r1' }]).map((item) => item.id),
    ['r1'],
  )
})

test('分散区域拆成多个连续标签组', () => {
  assert.deepEqual(connectedSeatGroups(['a', 'b', 'c'], elements), [['a', 'b'], ['c']])
})

test('区域标签锚点按连续区域中心计算', () => {
  const anchors = regionLabelAnchors({ label: '嘉宾', targetElementIds: ['a', 'b', 'c'] }, elements)
  assert.equal(anchors.length, 2)
  assert.equal(anchors[0].label, '嘉宾')
})

test('双击和单击都复用座位选择切换', () => {
  assert.deepEqual([...toggleSeatSelection(new Set(['a']), 'a')], [])
  assert.deepEqual([...toggleSeatSelection(new Set(['a']), 'b')].sort(), ['a', 'b'])
})
