import { COMMON_ELEMENT_SUGGESTIONS, ELEMENT_KINDS } from './venueModel.js'

export const CUSTOM_ELEMENT_STORAGE_KEY = 'meeting-helper:venue-custom-elements'
export const CUSTOM_COLOR_STORAGE_KEY = 'meeting-helper:venue-custom-colors'
export const CUSTOM_FILL_COLOR_STORAGE_KEY = 'meeting-helper:venue-custom-fill-colors'
export const CUSTOM_BORDER_COLOR_STORAGE_KEY = 'meeting-helper:venue-custom-border-colors'
export const CUSTOM_COLOR_STORAGE_KEYS = Object.freeze({
  fillColor: CUSTOM_FILL_COLOR_STORAGE_KEY,
  borderColor: CUSTOM_BORDER_COLOR_STORAGE_KEY,
})
export const RECENT_CUSTOM_COLOR_LIMIT = 5

export const BASIC_COLOR_SWATCHES = Object.freeze([
  Object.freeze({ name: '白色', value: '#ffffff', title: '白色' }),
  Object.freeze({ name: '蓝色', value: '#dbeafe', title: '蓝色' }),
  Object.freeze({ name: '绿色', value: '#dcfce7', title: '绿色' }),
  Object.freeze({ name: '红色', value: '#fee2e2', title: '红色' }),
  Object.freeze({ name: '黄色', value: '#fef3c7', title: '黄色' }),
])

const BASIC_COLOR_VALUES = new Set(BASIC_COLOR_SWATCHES.map((color) => color.value))

function browserStorage() {
  try {
    return globalThis.window?.localStorage || globalThis.localStorage
  } catch {
    return undefined
  }
}

function readArray(key, storage = browserStorage()) {
  if (!storage) return []
  try {
    const parsed = JSON.parse(storage.getItem(key) || '[]')
    return Array.isArray(parsed) ? parsed.filter((value) => typeof value === 'string') : []
  } catch {
    return []
  }
}

function writeArray(key, values, storage = browserStorage()) {
  if (!storage) return
  try {
    storage.setItem(key, JSON.stringify(values))
  } catch {
    // Local preferences are nice to have; editing should keep working without storage.
  }
}

function removeStoredArray(key, storage = browserStorage()) {
  if (!storage || typeof storage.removeItem !== 'function') return
  try {
    storage.removeItem(key)
  } catch {
    // Ignore unavailable storage cleanup.
  }
}

function isStorageLike(value) {
  return Boolean(value && typeof value.getItem === 'function' && typeof value.setItem === 'function')
}

function normalizeColorField(value) {
  return value === 'borderColor' ? 'borderColor' : 'fillColor'
}

function resolveColorListArgs(scopeOrStorage = 'fillColor', storage) {
  if (isStorageLike(scopeOrStorage)) {
    return { field: 'fillColor', storage: scopeOrStorage }
  }
  return { field: normalizeColorField(scopeOrStorage), storage }
}

function resolveColorValueArgs(scopeOrValue, valueOrStorage, storage) {
  if (normalizeHexColor(scopeOrValue)) {
    return {
      field: 'fillColor',
      value: scopeOrValue,
      storage: isStorageLike(valueOrStorage) ? valueOrStorage : storage,
    }
  }
  return {
    field: normalizeColorField(scopeOrValue),
    value: valueOrStorage,
    storage,
  }
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
    if (!name || seen.has(key)) continue
    seen.add(key)
    names.push(name)
  }
  return names
}

export function customElementSuggestions(storage = browserStorage()) {
  return customElementNames(storage).map((name) => ({
    name,
    kind: ELEMENT_KINDS.GENERIC,
    fillColor: '#dbeafe',
    borderColor: '#93c5fd',
    custom: true,
  }))
}

export function availableElementSuggestions(storage = browserStorage()) {
  return [
    ...COMMON_ELEMENT_SUGGESTIONS.map((suggestion) => ({ ...suggestion })),
    ...customElementSuggestions(storage),
  ]
}

export function saveCustomElementName(value, storage = browserStorage()) {
  const name = normalizeName(value)
  if (!name || isCommonElementName(name)) return ''
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

export function rgbLabel(value) {
  const color = normalizeHexColor(value)
  if (!color) return ''
  const red = Number.parseInt(color.slice(1, 3), 16)
  const green = Number.parseInt(color.slice(3, 5), 16)
  const blue = Number.parseInt(color.slice(5, 7), 16)
  return `RGB(${red}, ${green}, ${blue})`
}

export function textColorForBackground(value) {
  const color = normalizeHexColor(value)
  if (!color) return '#172033'
  const red = Number.parseInt(color.slice(1, 3), 16)
  const green = Number.parseInt(color.slice(3, 5), 16)
  const blue = Number.parseInt(color.slice(5, 7), 16)
  const luminance = (red * 299 + green * 587 + blue * 114) / 1000
  return luminance > 170 ? '#172033' : '#ffffff'
}

function normalizedCustomColorList(values) {
  const seen = new Set(BASIC_COLOR_VALUES)
  const colors = []
  for (const value of values) {
    const color = normalizeHexColor(value)
    if (!color || seen.has(color)) continue
    seen.add(color)
    colors.push(color)
  }
  return colors.slice(0, RECENT_CUSTOM_COLOR_LIMIT)
}

function readUnifiedCustomColors(storage = browserStorage()) {
  return normalizedCustomColorList([
    ...readArray(CUSTOM_COLOR_STORAGE_KEY, storage),
    ...readArray(CUSTOM_FILL_COLOR_STORAGE_KEY, storage),
    ...readArray(CUSTOM_BORDER_COLOR_STORAGE_KEY, storage),
  ])
}

function writeUnifiedCustomColors(colors, storage = browserStorage()) {
  writeArray(CUSTOM_COLOR_STORAGE_KEY, colors.slice(0, RECENT_CUSTOM_COLOR_LIMIT), storage)
  removeStoredArray(CUSTOM_FILL_COLOR_STORAGE_KEY, storage)
  removeStoredArray(CUSTOM_BORDER_COLOR_STORAGE_KEY, storage)
}

export function customColorValues(scopeOrStorage = 'fillColor', storage = browserStorage()) {
  const args = resolveColorListArgs(scopeOrStorage, storage)
  return readUnifiedCustomColors(args.storage)
}

export function availableColorSwatches(scopeOrStorage = 'fillColor', storage = browserStorage()) {
  const args = resolveColorListArgs(scopeOrStorage, storage)
  return [
    ...BASIC_COLOR_SWATCHES.map((color) => ({ ...color })),
    ...customColorValues(args.field, args.storage).map((value) => ({
      name: rgbLabel(value),
      value,
      title: rgbLabel(value),
      custom: true,
    })),
  ]
}

export function saveCustomColor(scopeOrValue, valueOrStorage, storage = browserStorage()) {
  const args = resolveColorValueArgs(scopeOrValue, valueOrStorage, storage)
  const color = normalizeHexColor(args.value)
  if (!color || BASIC_COLOR_VALUES.has(color)) return ''
  const colors = readUnifiedCustomColors(args.storage).filter((current) => current !== color)
  writeUnifiedCustomColors([color, ...colors], args.storage)
  return color
}

export function removeCustomColor(scopeOrValue, valueOrStorage, storage = browserStorage()) {
  const args = resolveColorValueArgs(scopeOrValue, valueOrStorage, storage)
  const color = normalizeHexColor(args.value)
  const colors = readUnifiedCustomColors(args.storage).filter((current) => current !== color)
  writeUnifiedCustomColors(colors, args.storage)
}
