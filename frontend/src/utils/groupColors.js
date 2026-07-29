import { normalizeHexColor, textColorForBackground } from './venuePreferences.js'

export const GROUP_COLOR_PALETTE = [
  { backgroundColor: '#FEF3C7', textColor: '#7C2D12' },
  { backgroundColor: '#DBEAFE', textColor: '#1D4ED8' },
  { backgroundColor: '#DCFCE7', textColor: '#166534' },
  { backgroundColor: '#FCE7F3', textColor: '#BE185D' },
  { backgroundColor: '#EDE9FE', textColor: '#6D28D9' },
  { backgroundColor: '#CCFBF1', textColor: '#0F766E' },
  { backgroundColor: '#FFEDD5', textColor: '#C2410C' },
  { backgroundColor: '#E0F2FE', textColor: '#0369A1' },
  { backgroundColor: '#FDE68A', textColor: '#854D0E' },
  { backgroundColor: '#D9F99D', textColor: '#3F6212' },
]

export const GROUP_COLOR_OVERRIDE_STORAGE_KEY = 'meeting-helper:participant-group-color-overrides'

function browserStorage() {
  try {
    return globalThis.window?.localStorage || globalThis.localStorage
  } catch {
    return undefined
  }
}

function safeFieldKey(fieldCode) {
  return String(fieldCode || '').trim()
}

function readOverrides(storage = browserStorage()) {
  if (!storage) return {}
  try {
    const parsed = JSON.parse(storage.getItem(GROUP_COLOR_OVERRIDE_STORAGE_KEY) || '{}')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    return Object.fromEntries(
      Object.entries(parsed).map(([fieldCode, values]) => [
        fieldCode,
        values && typeof values === 'object' && !Array.isArray(values)
          ? Object.fromEntries(
              Object.entries(values)
                .map(([value, color]) => [value, normalizeHexColor(color)])
                .filter(([, color]) => color),
            )
          : {},
      ]),
    )
  } catch {
    return {}
  }
}

function writeOverrides(overrides, storage = browserStorage()) {
  if (!storage) return
  try {
    storage.setItem(GROUP_COLOR_OVERRIDE_STORAGE_KEY, JSON.stringify(overrides))
  } catch {
    // Coloring still works with defaults if preferences cannot be persisted.
  }
}

export function readGroupColorOverrides(storage = browserStorage()) {
  return readOverrides(storage)
}

export function groupColorOverridesForField(fieldCode, storage = browserStorage()) {
  return readOverrides(storage)[safeFieldKey(fieldCode)] || {}
}

export function saveGroupColorOverride(fieldCode, value, color, storage = browserStorage()) {
  const fieldKey = safeFieldKey(fieldCode)
  const valueKey = String(value || '').trim()
  const normalized = normalizeHexColor(color)
  if (!fieldKey || !valueKey || !normalized) return ''
  const overrides = readOverrides(storage)
  overrides[fieldKey] = {
    ...(overrides[fieldKey] || {}),
    [valueKey]: normalized,
  }
  writeOverrides(overrides, storage)
  return normalized
}

export function removeGroupColorOverride(fieldCode, value, storage = browserStorage()) {
  const fieldKey = safeFieldKey(fieldCode)
  const valueKey = String(value || '').trim()
  if (!fieldKey || !valueKey) return
  const overrides = readOverrides(storage)
  if (!overrides[fieldKey]) return
  delete overrides[fieldKey][valueKey]
  if (!Object.keys(overrides[fieldKey]).length) delete overrides[fieldKey]
  writeOverrides(overrides, storage)
}

export function participantFieldValue(participant, fieldCode) {
  return participantFieldValues(participant, fieldCode)[0] || ''
}

export function participantFieldValues(participant, fieldCode) {
  if (!participant || !fieldCode) return []
  if (fieldCode === 'name') return cleanUniqueValues([participant.name])
  if (fieldCode === 'employeeNo') return cleanUniqueValues([participant.employeeNo])
  return cleanUniqueValues([
    participant.primaryAttributes?.[fieldCode],
    ...(participant.attributeValues?.[fieldCode] || []),
  ])
}

export function buildFieldColorEntries(
  participants,
  fieldCode,
  palette = GROUP_COLOR_PALETTE,
  overrides = {},
) {
  const colorsByValue = new Map()
  if (!fieldCode || !palette?.length) return []
  ;(participants || []).forEach((participant) => {
    participantFieldValues(participant, fieldCode).forEach((value) => {
      styleForValue(colorsByValue, value, palette, overrides)
    })
  })
  return [...colorsByValue.values()]
}

export function buildParticipantColorMap(
  participants,
  fieldCode,
  palette = GROUP_COLOR_PALETTE,
  overrides = {},
) {
  const colorsByValue = new Map()
  const colorsByParticipantId = new Map()
  if (!fieldCode || !palette?.length) return colorsByParticipantId

  ;(participants || []).forEach((participant) => {
    const values = participantFieldValues(participant, fieldCode)
    if (!values.length) return
    const styles = values.map((value) => styleForValue(colorsByValue, value, palette, overrides))
    if (styles.length === 1) {
      colorsByParticipantId.set(participant.id, { ...styles[0], multiValue: false })
      return
    }
    colorsByParticipantId.set(participant.id, {
      backgroundColor: '#f8fafc',
      backgroundImage: multiValueGradient(styles),
      textColor: '#172033',
      value: values.join('、'),
      multiValue: true,
    })
  })
  return colorsByParticipantId
}

function cleanUniqueValues(values) {
  const seen = new Set()
  return (values || [])
    .map((value) => String(value || '').trim())
    .filter((value) => {
      if (!value || seen.has(value)) return false
      seen.add(value)
      return true
    })
}

function styleForValue(colorsByValue, value, palette, overrides) {
  if (!colorsByValue.has(value)) {
    const overrideColor = normalizeHexColor(overrides?.[value])
    colorsByValue.set(value, {
      ...(overrideColor
        ? {
            backgroundColor: overrideColor,
            textColor: textColorForBackground(overrideColor),
            custom: true,
          }
        : palette[colorsByValue.size % palette.length]),
      value,
    })
  }
  return colorsByValue.get(value)
}

function multiValueGradient(styles) {
  const step = 100 / styles.length
  const stops = styles.flatMap((style, index) => {
    const start = (index * step).toFixed(2)
    const end = ((index + 1) * step).toFixed(2)
    return [`${style.backgroundColor} ${start}%`, `${style.backgroundColor} ${end}%`]
  })
  return `linear-gradient(135deg, ${stops.join(', ')})`
}
