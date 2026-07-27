import assert from 'node:assert/strict'
import test from 'node:test'

test('场馆设计与预览使用和排座画布一致的最小单元格尺寸', async () => {
  const metrics = await import('../src/utils/venueCanvasMetrics.js').catch(() => ({}))
  assert.equal(typeof metrics.displayCellUnit, 'function')

  assert.equal(metrics.displayCellUnit(), 44)
  assert.equal(metrics.displayCellUnit(0.5), 22)
})

test('座位元素在网格内保留可见间隙而连续区域不留缝', async () => {
  const metrics = await import('../src/utils/venueCanvasMetrics.js').catch(() => ({}))
  assert.equal(typeof metrics.elementBox, 'function')

  assert.deepEqual(
    metrics.elementBox(
      { kind: 'SEAT', row: 2, column: 3, rowSpan: 1, columnSpan: 1 },
      44,
    ),
    { left: 90, top: 46, width: 40, height: 40 },
  )
  assert.deepEqual(
    metrics.elementBox(
      { kind: 'GENERIC', row: 2, column: 3, rowSpan: 2, columnSpan: 4 },
      44,
    ),
    { left: 88, top: 44, width: 176, height: 88 },
  )
})

test('多格座位仍按一个通用元素占据完整矩形', async () => {
  const metrics = await import('../src/utils/venueCanvasMetrics.js').catch(() => ({}))

  assert.deepEqual(
    metrics.elementBox(
      { kind: 'SEAT', row: 2, column: 3, rowSpan: 2, columnSpan: 3 },
      44,
    ),
    { left: 90, top: 46, width: 128, height: 84 },
  )
})

test('场馆预览适应窗口时仍保持可辨识的单元格尺寸', async () => {
  const metrics = await import('../src/utils/venueCanvasMetrics.js').catch(() => ({}))
  assert.equal(typeof metrics.previewFitZoom, 'function')

  assert.equal(
    metrics.previewFitZoom({
      gridColumns: 43,
      gridRows: 18,
      viewportWidth: 1120,
      viewportHeight: 680,
    }),
    0.86,
  )
})
