import { COMMON_ELEMENT_SUGGESTIONS, ELEMENT_KINDS } from './venueModel.js'

export const CUSTOM_ELEMENT_STORAGE_KEY = 'meeting-helper:venue-custom-elements'
export const CUSTOM_COLOR_STORAGE_KEY = 'meeting-helper:venue-custom-colors'
export const RECENT_CUSTOM_COLOR_LIMIT = 5
export const SYSTEM_LAYOUT_COLOR = '#e5edf8'

export const SEMANTIC_COLOR_SWATCHES = Object.freeze([
  Object.freeze({ name: '淡红', value: '#fee2e2', title: '#fee2e2' }),
  Object.freeze({ name: '粉红', value: '#ffe4e6', title: '#ffe4e6' }),
  Object.freeze({ name: '橙杏', value: '#ffedd5', title: '#ffedd5' }),
  Object.freeze({ name: '浅黄', value: '#fef3c7', title: '#fef3c7' }),
  Object.freeze({ name: '柠黄', value: '#fef9c3', title: '#fef9c3' }),
  Object.freeze({ name: '嫩绿', value: '#ecfccb', title: '#ecfccb' }),
  Object.freeze({ name: '薄荷', value: '#dcfce7', title: '#dcfce7' }),
  Object.freeze({ name: '石绿', value: '#d1fae5', title: '#d1fae5' }),
  Object.freeze({ name: '青绿', value: '#ccfbf1', title: '#ccfbf1' }),
  Object.freeze({ name: '湖蓝', value: '#cffafe', title: '#cffafe' }),
  Object.freeze({ name: '天蓝', value: '#e0f2fe', title: '#e0f2fe' }),
  Object.freeze({ name: '蓝灰', value: '#dbeafe', title: '#dbeafe' }),
  Object.freeze({ name: '靛蓝', value: '#e0e7ff', title: '#e0e7ff' }),
  Object.freeze({ name: '淡紫', value: '#ede9fe', title: '#ede9fe' }),
  Object.freeze({ name: '浅紫', value: '#f3e8ff', title: '#f3e8ff' }),
  Object.freeze({ name: '玫粉', value: '#fce7f3', title: '#fce7f3' }),
  Object.freeze({ name: '柔橘', value: '#fed7aa', title: '#fed7aa' }),
  Object.freeze({ name: '浅橙', value: '#fde68a', title: '#fde68a' }),
  Object.freeze({ name: '浅绿', value: '#bbf7d0', title: '#bbf7d0' }),
  Object.freeze({ name: '浅青', value: '#a7f3d0', title: '#a7f3d0' }),
  Object.freeze({ name: '浅蓝', value: '#bfdbfe', title: '#bfdbfe' }),
  Object.freeze({ name: '浅靛', value: '#c7d2fe', title: '#c7d2fe' }),
  Object.freeze({ name: '浅玫', value: '#fbcfe8', title: '#fbcfe8' }),
  Object.freeze({ name: '暖灰', value: '#f1f5f9', title: '#f1f5f9' }),
])

const SEMANTIC_COLOR_VALUES = new Set(SEMANTIC_COLOR_SWATCHES.map((color) => color.value))

function browserStorage() {
  try {
    return globalThis.window?.localStorage || globalThis.localStorage
  } catch {
    return undefined
  }
}

function readArray(key, storage = browserStorage()) {
  if (!storage) {
    return []
  }
  try {
    const parsed = JSON.parse(storage.getItem(key) || '[]')
    return Array.isArray(parsed) ? parsed.filter((value) => typeof value === 'string') : []
  } catch {
    return []
  }
}

function writeArray(key, values, storage = browserStorage()) {
  if (!storage) {
    return
  }
  try {
    storage.setItem(key, JSON.stringify(values))
  } catch {
    // Local preferences are nice to have; editing should keep working without storage.
  }
}

function isStorageLike(value) {
  return Boolean(value && typeof value.getItem === 'function' && typeof value.setItem === 'function')
}

function normalizeName(value) {
  return String(value ?? '').trim()
}

function nameKey(value) {
  return normalizeName(value).toLowerCase()
}

export function isCommonElementName(name) {
  const key = nameKey(name)
  return COMMON_ELEMENT_SUGGESTIONS.some((suggestion) => nameKey(suggestion.name) === key)
}

export function customElementNames(storage = browserStorage()) {
  const seen = new Set(COMMON_ELEMENT_SUGGESTIONS.map((suggestion) => nameKey(suggestion.name)))
  const names = []
  for (const value of readArray(CUSTOM_ELEMENT_STORAGE_KEY, storage)) {
    const name = normalizeName(value)
    const key = nameKey(name)
    if (!name || seen.has(key)) {
      continue
    }
    seen.add(key)
    names.push(name)
  }
  return names
}

export function customElementSuggestions(storage = browserStorage(), elements = []) {
  const colorsByName = genericElementColorMap(elements)
  const usedColors = COMMON_ELEMENT_SUGGESTIONS
    .filter((suggestion) => suggestion.kind === ELEMENT_KINDS.GENERIC)
    .map((suggestion) => suggestion.fillColor)
  usedColors.push(...colorsByName.values())
  return customElementNames(storage).map((name) => {
    const fillColor = colorsByName.get(name) || nextAvailableSemanticColor(usedColors)
    usedColors.push(fillColor)
    return {
      name,
      kind: ELEMENT_KINDS.GENERIC,
      fillColor,
      custom: true,
    }
  })
}

export function availableElementSuggestions(storage = browserStorage(), elements = []) {
  return [
    ...COMMON_ELEMENT_SUGGESTIONS.map((suggestion) => ({ ...suggestion })),
    ...customElementSuggestions(storage, elements),
  ]
}

export function saveCustomElementName(value, storage = browserStorage()) {
  const name = normalizeName(value)
  if (!name || isCommonElementName(name)) {
    return ''
  }
  const names = customElementNames(storage)
  if (!names.some((current) => nameKey(current) === nameKey(name))) {
    names.push(name)
    writeArray(CUSTOM_ELEMENT_STORAGE_KEY, names.slice(-30), storage)
  }
  return name
}

export function removeCustomElementName(value, storage = browserStorage()) {
  const key = nameKey(value)
  const names = customElementNames(storage).filter((name) => nameKey(name) !== key)
  writeArray(CUSTOM_ELEMENT_STORAGE_KEY, names, storage)
}

export function normalizeHexColor(value) {
  const source = String(value ?? '').trim()
  const match = /^#?([0-9a-fA-F]{6})$/.exec(source)
  return match ? `#${match[1].toLowerCase()}` : ''
}

export function textColorForBackground(value) {
  const color = normalizeHexColor(value)
  if (!color) {
    return '#172033'
  }
  const red = Number.parseInt(color.slice(1, 3), 16)
  const green = Number.parseInt(color.slice(3, 5), 16)
  const blue = Number.parseInt(color.slice(5, 7), 16)
  const luminance = (red * 299 + green * 587 + blue * 114) / 1000
  return luminance > 170 ? '#172033' : '#ffffff'
}

export function semanticColorValues() {
  return SEMANTIC_COLOR_SWATCHES.map((color) => color.value)
}

export function nextAvailableSemanticColor(usedColors = []) {
  const used = new Set((usedColors || []).map(normalizeHexColor).filter(Boolean))
  const semanticColor = SEMANTIC_COLOR_SWATCHES.find((color) => !used.has(color.value))?.value
  if (semanticColor) {
    return semanticColor
  }

  for (let index = 0; index <= 0xffffff; index += 1) {
    const value = (0x345678 + index * 0x9e3779) & 0xffffff
    const color = `#${value.toString(16).padStart(6, '0')}`
    if (color !== '#ffffff' && color !== SYSTEM_LAYOUT_COLOR && !used.has(color)) {
      return color
    }
  }
  return '#000000'
}

export function genericElementColorMap(elements = []) {
  const colorsByName = new Map()
  for (const element of elements || []) {
    if (element?.kind !== ELEMENT_KINDS.GENERIC) {
      continue
    }
    const name = normalizeName(element.name)
    const color = normalizeHexColor(element.fillColor)
    if (!name || !color || colorsByName.has(name)) {
      continue
    }
    colorsByName.set(name, color)
  }
  return colorsByName
}

export function usedGenericElementColors(elements = [], excludedName = '') {
  const excludedKey = nameKey(excludedName)
  return [...genericElementColorMap(elements)]
    .filter(([name]) => nameKey(name) !== excludedKey)
    .map(([, color]) => color)
}

export function conflictingGenericColorNames(elements = []) {
  const namesByColor = new Map()
  for (const [name, color] of genericElementColorMap(elements)) {
    const names = namesByColor.get(color) || []
    names.push(name)
    namesByColor.set(color, names)
  }
  return [...namesByColor]
    .filter(([, names]) => names.length > 1)
    .map(([color, names]) => ({ color, names }))
}

function normalizedCustomColorList(values) {
  const seen = new Set(SEMANTIC_COLOR_VALUES)
  const colors = []
  for (const value of values) {
    const color = normalizeHexColor(value)
    if (!color || seen.has(color)) {
      continue
    }
    seen.add(color)
    colors.push(color)
  }
  return colors.slice(0, RECENT_CUSTOM_COLOR_LIMIT)
}

export function customColorValues(storage = browserStorage()) {
  return normalizedCustomColorList(readArray(CUSTOM_COLOR_STORAGE_KEY, storage))
}

export function availableColorSwatches(storage = browserStorage()) {
  return [
    ...SEMANTIC_COLOR_SWATCHES.map((color) => ({ ...color })),
    ...customColorValues(storage).map((value) => ({
      name: value,
      value,
      title: value,
      custom: true,
    })),
  ]
}

export function saveCustomColor(value, storage = browserStorage()) {
  const color = normalizeHexColor(value)
  if (!color || SEMANTIC_COLOR_VALUES.has(color)) {
    return ''
  }
  const colors = customColorValues(isStorageLike(storage) ? storage : browserStorage())
    .filter((current) => current !== color)
  writeArray(CUSTOM_COLOR_STORAGE_KEY, [color, ...colors].slice(0, RECENT_CUSTOM_COLOR_LIMIT), storage)
  return color
}

export function removeCustomColor(value, storage = browserStorage()) {
  const color = normalizeHexColor(value)
  const colors = customColorValues(isStorageLike(storage) ? storage : browserStorage())
    .filter((current) => current !== color)
  writeArray(CUSTOM_COLOR_STORAGE_KEY, colors, storage)
}
