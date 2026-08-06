function normalizeZoom(value, fallback = 1) {
  const numericValue = Number(value)
  if (Number.isFinite(numericValue) && numericValue > 0) {
    return numericValue
  }
  return fallback
}

function clampZoom(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

export function nextWheelZoom(currentZoom, deltaY, options = {}) {
  const step = Number(options.step || 0.1)
  const min = Number(options.min || 0.25)
  const max = Number(options.max || 2.5)
  const direction = deltaY < 0 ? 1 : -1
  const nextZoom = normalizeZoom(currentZoom) + direction * step
  return Number(clampZoom(nextZoom, min, max).toFixed(2))
}

export function capturePointerZoomAnchor(viewport, event, oldZoom) {
  const bounds = viewport.getBoundingClientRect()
  const localX = event.clientX - bounds.left
  const localY = event.clientY - bounds.top
  return {
    oldZoom: normalizeZoom(oldZoom),
    localX,
    localY,
    contentX: localX + viewport.scrollLeft,
    contentY: localY + viewport.scrollTop,
  }
}

export function captureElementZoomAnchor(element, event) {
  const bounds = element.getBoundingClientRect()
  const width = Math.max(1, bounds.width)
  const height = Math.max(1, bounds.height)
  return {
    clientX: event.clientX,
    clientY: event.clientY,
    ratioX: (event.clientX - bounds.left) / width,
    ratioY: (event.clientY - bounds.top) / height,
  }
}

export function scrollToZoomAnchor(viewport, anchor, newZoom) {
  const safeZoom = normalizeZoom(newZoom, anchor.oldZoom)
  viewport.scrollLeft = (anchor.contentX / anchor.oldZoom) * safeZoom - anchor.localX
  viewport.scrollTop = (anchor.contentY / anchor.oldZoom) * safeZoom - anchor.localY
}

export function scrollToElementZoomAnchor(viewport, element, anchor) {
  const bounds = element.getBoundingClientRect()
  const nextClientX = bounds.left + bounds.width * anchor.ratioX
  const nextClientY = bounds.top + bounds.height * anchor.ratioY
  viewport.scrollLeft += nextClientX - anchor.clientX
  viewport.scrollTop += nextClientY - anchor.clientY
}
