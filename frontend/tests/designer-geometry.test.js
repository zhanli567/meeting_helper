import assert from 'node:assert/strict'
import test from 'node:test'

import * as designerGeometry from '../src/utils/designerGeometry.js'

const { createSeatElements, moveRect, resizeRect } = designerGeometry

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

test('矩形重叠按一基网格边界判定', () => {
  assert.equal(typeof designerGeometry.rectsOverlap, 'function')

  assert.equal(
    designerGeometry.rectsOverlap(
      { row: 2, column: 3, rowSpan: 2, columnSpan: 2 },
      { row: 3, column: 4, rowSpan: 1, columnSpan: 1 },
    ),
    true,
  )
  assert.equal(
    designerGeometry.rectsOverlap(
      { row: 2, column: 3, rowSpan: 2, columnSpan: 2 },
      { row: 4, column: 3, rowSpan: 1, columnSpan: 2 },
    ),
    false,
  )
})

test('放置矩形时排除自身并拒绝与其他元素重叠', () => {
  assert.equal(typeof designerGeometry.canPlaceRect, 'function')
  const elements = [
    { id: 'current', row: 2, column: 2, rowSpan: 2, columnSpan: 2 },
    { id: 'other', row: 5, column: 5, rowSpan: 2, columnSpan: 2 },
  ]

  assert.equal(
    designerGeometry.canPlaceRect(
      elements,
      { row: 2, column: 2, rowSpan: 2, columnSpan: 2 },
      'current',
    ),
    true,
  )
  assert.equal(
    designerGeometry.canPlaceRect(
      elements,
      { row: 5, column: 5, rowSpan: 1, columnSpan: 1 },
      'current',
    ),
    false,
  )
})

test('画布缩小时返回越出新边界的元素', () => {
  assert.equal(typeof designerGeometry.canvasResizeConflict, 'function')

  assert.deepEqual(
    designerGeometry.canvasResizeConflict(
      [
        { id: 'inside', row: 1, column: 1, rowSpan: 2, columnSpan: 2 },
        { id: 'row-overflow', row: 4, column: 1, rowSpan: 2, columnSpan: 1 },
        { id: 'column-overflow', row: 1, column: 5, rowSpan: 1, columnSpan: 2 },
      ],
      5,
      5,
    ),
    [
      { id: 'column-overflow', row: 1, column: 5, rowSpan: 1, columnSpan: 2 },
    ],
  )
})

test('多格座位支持逐格生成和合并生成', () => {
  assert.equal(typeof createSeatElements, 'function')
  const rect = { row: 2, column: 3, rowSpan: 2, columnSpan: 3 }

  assert.equal(createSeatElements(rect, 'merge').length, 1)
  assert.equal(createSeatElements(rect, 'cells').length, 6)
  assert.deepEqual(createSeatElements(rect, 'merge')[0], {
    kind: 'SEAT',
    name: '座位',
    fillColor: '#ffffff',
    borderColor: '#8fb4e8',
    row: 2,
    column: 3,
    rowSpan: 2,
    columnSpan: 3,
  })
})

test('指针位移按缩放后的单元格换算为网格位移', () => {
  assert.equal(typeof designerGeometry.pointerDeltaToGrid, 'function')

  assert.deepEqual(designerGeometry.pointerDeltaToGrid(103, -52, 44, 0.5), {
    rows: -2,
    columns: 5,
  })
})

test('反向框选仍生成一基坐标的完整矩形', () => {
  assert.equal(typeof designerGeometry.normalizeGridRect, 'function')

  assert.deepEqual(
    designerGeometry.normalizeGridRect(
      { row: 6, column: 8 },
      { row: 3, column: 4 },
    ),
    {
      row: 3,
      column: 4,
      rowSpan: 4,
      columnSpan: 5,
    },
  )
})

test('画布四边拖拽换算尺寸并遵守最小边长', () => {
  assert.equal(typeof designerGeometry.canvasSizeFromPointer, 'function')
  const size = { rows: 20, columns: 30 }

  assert.deepEqual(
    designerGeometry.canvasSizeFromPointer(size, 'east', 88, 0, 44, 1, 5),
    { rows: 20, columns: 32 },
  )
  assert.deepEqual(
    designerGeometry.canvasSizeFromPointer(size, 'north', 0, 880, 44, 1, 5),
    { rows: 5, columns: 30 },
  )
  assert.deepEqual(
    designerGeometry.canvasSizeFromPointer(size, 'west', -88, 0, 44, 1, 5),
    { rows: 20, columns: 32 },
  )
})

test('浮动选择器优先停在选区旁且不覆盖选区', () => {
  assert.equal(typeof designerGeometry.placePanelBesideRect, 'function')

  const viewport = { width: 800, height: 600 }
  const panel = { width: 300, height: 240 }
  assert.deepEqual(
    designerGeometry.placePanelBesideRect(
      { left: 100, top: 120, width: 80, height: 88 },
      viewport,
      panel,
    ),
    { left: 192, top: 120 },
  )
  assert.deepEqual(
    designerGeometry.placePanelBesideRect(
      { left: 700, top: 520, width: 60, height: 60 },
      viewport,
      panel,
    ),
    { left: 388, top: 348 },
  )
})

test('选区左右空间不足时选择器改停在下方', () => {
  assert.equal(typeof designerGeometry.placePanelBesideRect, 'function')

  assert.deepEqual(
    designerGeometry.placePanelBesideRect(
      { left: 50, top: 50, width: 400, height: 80 },
      { width: 500, height: 700 },
      { width: 300, height: 240 },
    ),
    { left: 50, top: 142 },
  )
})

test('画布四边变化时计算保持相对边固定所需的滚动修正', () => {
  assert.equal(typeof designerGeometry.canvasAnchorCorrection, 'function')
  const start = { left: 100, top: 80, right: 540, bottom: 520 }

  assert.deepEqual(
    designerGeometry.canvasAnchorCorrection(
      'west',
      start,
      { left: 12, top: 80, right: 540, bottom: 520 },
    ),
    { x: 0, y: 0 },
  )
  assert.deepEqual(
    designerGeometry.canvasAnchorCorrection(
      'east-south',
      start,
      { left: 92, top: 74, right: 628, bottom: 608 },
    ),
    { x: -8, y: -6 },
  )
  assert.deepEqual(
    designerGeometry.canvasAnchorCorrection(
      'north-west',
      start,
      { left: 12, top: -8, right: 548, bottom: 528 },
    ),
    { x: 8, y: 8 },
  )
})

test('元素属性只接受八十字符内名称和六位十六进制颜色', () => {
  assert.equal(typeof designerGeometry.validElementProperties, 'function')
  const valid = {
    kind: 'GENERIC',
    name: '主舞台',
    fillColor: '#dbeafe',
    borderColor: '#93C5FD',
  }

  assert.equal(designerGeometry.validElementProperties(valid), true)
  assert.equal(designerGeometry.validElementProperties({ ...valid, name: ' ' }), false)
  assert.equal(
    designerGeometry.validElementProperties({ ...valid, name: '台'.repeat(81) }),
    false,
  )
  assert.equal(
    designerGeometry.validElementProperties({ ...valid, fillColor: null }),
    false,
  )
  assert.equal(
    designerGeometry.validElementProperties({ ...valid, borderColor: '#fff' }),
    false,
  )
})

test('历史栈使用深拷贝并最多保留五十个撤销状态', () => {
  assert.equal(typeof designerGeometry.appendHistorySnapshot, 'function')

  let history = []
  const first = { gridRows: 20, gridColumns: 30, elements: [{ id: 'seat-1', row: 1 }] }
  history = designerGeometry.appendHistorySnapshot(history, first)
  first.elements[0].row = 9
  assert.equal(history[0].elements[0].row, 1)

  for (let index = 1; index <= 50; index += 1) {
    history = designerGeometry.appendHistorySnapshot(history, {
      gridRows: 20,
      gridColumns: 30 + index,
      elements: [],
    })
  }
  assert.equal(history.length, 50)
  assert.equal(history[0].gridColumns, 31)
  assert.equal(history[49].gridColumns, 80)
})
