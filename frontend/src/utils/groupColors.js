import {
  SEMANTIC_COLOR_SWATCHES,
  SYSTEM_LAYOUT_COLOR,
  normalizeHexColor,
  textColorForBackground,
} from './venuePreferences.js'

export const GROUP_COLOR_PALETTE = SEMANTIC_COLOR_SWATCHES
  .filter((color) => color.value !== SYSTEM_LAYOUT_COLOR)
  .map((color) => ({
    backgroundColor: color.value,
    textColor: textColorForBackground(color.value),
  }))

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
  if (!storage) {
    return {}
  }
  try {
    const parsed = JSON.parse(storage.getItem(GROUP_COLOR_OVERRIDE_STORAGE_KEY) || '{}')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return {}
    }
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
  if (!storage) {
    return
  }
  try {
    storage.setItem(GROUP_COLOR_OVERRIDE_STORAGE_KEY, JSON.stringify(overrides))
  } catch {
    // 本地偏好无法保存时，仍使用默认颜色保证着色可用。
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
  if (!fieldKey || !valueKey || !normalized) {
    return ''
  }
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
  if (!fieldKey || !valueKey) {
    return
  }
  const overrides = readOverrides(storage)
  if (!overrides[fieldKey]) {
    return
  }
  delete overrides[fieldKey][valueKey]
  if (!Object.keys(overrides[fieldKey]).length) {
    delete overrides[fieldKey]
  }
  writeOverrides(overrides, storage)
}

export function participantFieldValue(participant, fieldCode) {
  return participantFieldValues(participant, fieldCode)[0] || ''
}

export function participantFieldValues(participant, fieldCode) {
  if (!participant || !fieldCode) {
    return []
  }
  if (fieldCode === 'name') {
    return cleanUniqueValues([participant.name])
  }
  if (fieldCode === 'employeeNo') {
    return cleanUniqueValues([participant.employeeNo])
  }
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
  reservedColors = [],
) {
  const colorsByValue = new Map()
  const usedColors = normalizedColorSet(reservedColors)
  if (!fieldCode || !palette?.length) {
    return []
  }
  ;(participants || []).forEach((participant) => {
    participantFieldValues(participant, fieldCode).forEach((value) => {
      styleForValue(colorsByValue, value, palette, overrides, usedColors)
    })
  })
  return [...colorsByValue.values()]
}

export function buildParticipantColorMap(
  participants,
  fieldCode,
  palette = GROUP_COLOR_PALETTE,
  overrides = {},
  reservedColors = [],
) {
  const colorsByValue = new Map()
  const colorsByParticipantId = new Map()
  const usedColors = normalizedColorSet(reservedColors)
  if (!fieldCode || !palette?.length) {
    return colorsByParticipantId
  }

  ;(participants || []).forEach((participant) => {
    const values = participantFieldValues(participant, fieldCode)
    if (!values.length) {
      return
    }
    const styles = values.map((value) => styleForValue(colorsByValue, value, palette, overrides, usedColors))
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
      if (!value || seen.has(value)) {
        return false
      }
      seen.add(value)
      return true
    })
}

function normalizedColorSet(values) {
  return new Set((values || []).map((value) => normalizeHexColor(value)).filter(Boolean))
}

function nextPaletteColor(palette, usedColors) {
  const color = (palette || [])
    .map((style) => normalizeHexColor(style.backgroundColor))
    .find((value) => value && !usedColors.has(value))
  if (color) {
    return color
  }

  for (let index = 0; index <= 0xffffff; index += 1) {
    const slot = (index * 40503) & 0x3ffff
    const red = 192 + ((slot >>> 12) & 0x3f)
    const green = 192 + ((slot >>> 6) & 0x3f)
    const blue = 192 + (slot & 0x3f)
    const generated = `#${red.toString(16).padStart(2, '0')}${green
      .toString(16)
      .padStart(2, '0')}${blue.toString(16).padStart(2, '0')}`
    if (!usedColors.has(generated)) {
      return generated
    }
  }
  return '#ffffff'
}

function paletteStyleForColor(color, palette) {
  const normalized = normalizeHexColor(color)
  const paletteStyle = (palette || []).find(
    (style) => normalizeHexColor(style.backgroundColor) === normalized,
  )
  return paletteStyle
    ? { ...paletteStyle, backgroundColor: normalized }
    : {
        backgroundColor: normalized,
        textColor: textColorForBackground(normalized),
      }
}

function styleForValue(colorsByValue, value, palette, overrides, usedColors) {
  if (!colorsByValue.has(value)) {
    const overrideColor = normalizeHexColor(overrides?.[value])
    const color = overrideColor && !usedColors.has(overrideColor)
      ? overrideColor
      : nextPaletteColor(palette, usedColors)
    usedColors.add(color)
    colorsByValue.set(value, {
      ...paletteStyleForColor(color, palette),
      custom: Boolean(overrideColor && overrideColor === color),
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
