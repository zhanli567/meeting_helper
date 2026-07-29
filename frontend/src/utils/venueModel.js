export const DEFAULT_CANVAS = Object.freeze({ rows: 20, columns: 30 })
export const MIN_CANVAS_SIZE = 5
export const ELEMENT_KINDS = Object.freeze({ SEAT: 'SEAT', GENERIC: 'GENERIC' })

const genericSuggestions = [
  ['门', '#dbeafe'],
  ['墙', '#dcfce7'],
  ['桌子', '#fef3c7'],
  ['摄像', '#fce7f3'],
  ['舞台', '#ede9fe'],
  ['显示屏', '#ccfbf1'],
]

export function emptyVenueInfo() {
  return {
    location: '',
    campus: '',
    mainScreenResolution: '',
    stageDimensions: '',
    manualCapacity: null,
    contactInfo: '',
    bookingUrl: '',
    meetingRoomFunctions: '',
    servicesProvided: '',
    description: '',
    remarks: '',
  }
}

export const COMMON_ELEMENT_SUGGESTIONS = Object.freeze([
  Object.freeze({
    name: '座位',
    kind: ELEMENT_KINDS.SEAT,
    fillColor: '#ffffff',
  }),
  ...genericSuggestions.map(([name, fillColor]) =>
    Object.freeze({
      name,
      kind: ELEMENT_KINDS.GENERIC,
      fillColor,
    }),
  ),
])

function blankToNull(value) {
  const normalized = String(value ?? '').trim()
  return normalized || null
}

function normalizedFillColor(value) {
  const match = /^#?([0-9a-f]{6})$/i.exec(String(value ?? '').trim())
  return match ? `#${match[1].toLowerCase()}` : '#ffffff'
}

export function safeHttpUrl(value) {
  const source = String(value ?? '')
  if (/[\u0000-\u001f\u007f-\u009f]/u.test(source)) return null
  const normalized = source.trim()
  if (!normalized) return null
  if (!/^https?:\/\//iu.test(normalized)) return null
  try {
    const url = new URL(normalized)
    if (!['http:', 'https:'].includes(url.protocol) || !url.hostname) return null
    return normalized
  } catch {
    return null
  }
}

export function normalizeVenueInfo(form) {
  return {
    location: String(form.location ?? '').trim(),
    campus: blankToNull(form.campus),
    mainScreenResolution: blankToNull(form.mainScreenResolution),
    stageDimensions: blankToNull(form.stageDimensions),
    manualCapacity:
      form.manualCapacity === '' || form.manualCapacity == null ? null : Number(form.manualCapacity),
    contactInfo: blankToNull(form.contactInfo),
    bookingUrl: blankToNull(form.bookingUrl),
    meetingRoomFunctions: blankToNull(form.meetingRoomFunctions),
    servicesProvided: blankToNull(form.servicesProvided),
    description: blankToNull(form.description),
    remarks: blankToNull(form.remarks),
  }
}

export function toElementPayload(element) {
  return {
    kind: element.kind,
    name: String(element.name).trim(),
    row: element.row,
    column: element.column,
    rowSpan: element.rowSpan,
    columnSpan: element.columnSpan,
    fillColor: normalizedFillColor(element.fillColor),
  }
}

export function toCreateVenuePayload(info, layout) {
  return {
    ...normalizeVenueInfo(info),
    gridRows: layout.gridRows,
    gridColumns: layout.gridColumns,
    elements: layout.elements.map(toElementPayload),
  }
}
