import assert from 'node:assert/strict'
import test from 'node:test'
import {
  captureElementZoomAnchor,
  capturePointerZoomAnchor,
  nextWheelZoom,
  scrollToElementZoomAnchor,
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

test('wheel zoom keeps the actual canvas point under pointer after centered reflow', () => {
  const viewport = {
    scrollLeft: 120,
    scrollTop: 80,
  }
  let elementBounds = {
    left: 70,
    top: 40,
    width: 600,
    height: 480,
  }
  const element = {
    getBoundingClientRect: () => elementBounds,
  }
  const event = { clientX: 220, clientY: 160 }
  const anchor = captureElementZoomAnchor(element, event)

  elementBounds = {
    left: 35,
    top: 10,
    width: 900,
    height: 720,
  }
  scrollToElementZoomAnchor(viewport, element, anchor)

  assert.equal(viewport.scrollLeft, 160)
  assert.equal(viewport.scrollTop, 110)
})

test('wheel zoom follows direction and bounds', () => {
  assert.equal(nextWheelZoom(0.8, -100, { step: 0.1, min: 0.4, max: 2.5 }), 0.9)
  assert.equal(nextWheelZoom(0.8, 100, { step: 0.1, min: 0.4, max: 2.5 }), 0.7)
  assert.equal(nextWheelZoom(2.5, -100, { step: 0.1, min: 0.4, max: 2.5 }), 2.5)
  assert.equal(nextWheelZoom(0.4, 100, { step: 0.1, min: 0.4, max: 2.5 }), 0.4)
})
