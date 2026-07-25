import assert from 'node:assert/strict'
import test from 'node:test'

import * as designerGeometry from '../src/utils/designerGeometry.js'

const { moveRect, resizeRect } = designerGeometry

const bounds = { rows: 10, columns: 12 }
const origin = { row: 3, column: 4, rowSpan: 2, columnSpan: 3 }

test('拖动元素时按网格移动并限制在画布范围内', () => {
  assert.deepEqual(moveRect(origin, 2, -1, bounds), {
    row: 5,
    column: 3,
    rowSpan: 2,
    columnSpan: 3,
  })
  assert.deepEqual(moveRect(origin, -20, 20, bounds), {
    row: 1,
    column: 10,
    rowSpan: 2,
    columnSpan: 3,
  })
})

test('八方向缩放保持最小一格并限制在画布范围内', () => {
  assert.deepEqual(resizeRect(origin, 'se', 2, 3, bounds), {
    row: 3,
    column: 4,
    rowSpan: 4,
    columnSpan: 6,
  })
  assert.deepEqual(resizeRect(origin, 'nw', 1, 2, bounds), {
    row: 4,
    column: 6,
    rowSpan: 1,
    columnSpan: 1,
  })
  assert.deepEqual(resizeRect(origin, 'w', 0, -10, bounds), {
    row: 3,
    column: 1,
    rowSpan: 2,
    columnSpan: 6,
  })
})

test('松开鼠标后继续显示待选择的框选区域', () => {
  assert.equal(typeof designerGeometry.activeSelectionRect, 'function')
  const pending = { row: 4, column: 7, rowSpan: 3, columnSpan: 5 }

  assert.deepEqual(designerGeometry.activeSelectionRect(undefined, pending), pending)
})

test('有浮动卡片时点击外部先关闭卡片而不立即开始新框选', () => {
  assert.equal(typeof designerGeometry.shouldDismissDesignerOverlays, 'function')

  assert.equal(
    designerGeometry.shouldDismissDesignerOverlays({
      hasOverlay: true,
      insideOverlay: false,
    }),
    true,
  )
  assert.equal(
    designerGeometry.shouldDismissDesignerOverlays({
      hasOverlay: true,
      insideOverlay: true,
    }),
    false,
  )
})
