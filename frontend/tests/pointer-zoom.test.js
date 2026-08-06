import assert from 'node:assert/strict'
import test from 'node:test'
import {
  capturePointerZoomAnchor,
  nextWheelZoom,
  scrollToZoomAnchor,
} from '../src/utils/pointerZoom.js'

test('wheel zoom keeps the content anchor under pointer', () => {
  const viewport = {
    scrollLeft: 120,
    scrollTop: 80,
    getBoundingClientRect: () => ({ left: 20, top: 30 }),
  }
  const event = { clientX: 220, clientY: 180 }
  const anchor = capturePointerZoomAnchor(viewport, event, 1)

  scrollToZoomAnchor(viewport, anchor, 1.5)

  assert.equal(viewport.scrollLeft, 280)
  assert.equal(viewport.scrollTop, 195)
})

test('wheel zoom follows direction and bounds', () => {
  assert.equal(nextWheelZoom(0.8, -100, { step: 0.1, min: 0.4, max: 2.5 }), 0.9)
  assert.equal(nextWheelZoom(0.8, 100, { step: 0.1, min: 0.4, max: 2.5 }), 0.7)
  assert.equal(nextWheelZoom(2.5, -100, { step: 0.1, min: 0.4, max: 2.5 }), 2.5)
  assert.equal(nextWheelZoom(0.4, 100, { step: 0.1, min: 0.4, max: 2.5 }), 0.4)
})
