import assert from 'node:assert/strict'
import test from 'node:test'

function createStorage(seed = {}) {
  const values = new Map(Object.entries(seed))
  return {
    getItem(key) {
      return values.has(key) ? values.get(key) : null
    },
    setItem(key, value) {
      values.set(key, String(value))
    },
    removeItem(key) {
      values.delete(key)
    },
  }
}

test('custom element names are deduped, appended after common elements and removable', async () => {
  const preferences = await import('../src/utils/venuePreferences.js').catch(() => ({}))
  assert.equal(typeof preferences.saveCustomElementName, 'function')
  const storage = createStorage()

  preferences.saveCustomElementName('  xx  ', storage)
  preferences.saveCustomElementName('XX', storage)
  preferences.saveCustomElementName('door', storage)

  const suggestions = preferences.availableElementSuggestions(storage)
  const customSuggestions = suggestions.filter((suggestion) => suggestion.custom)

  assert.equal(customSuggestions.length, 2)
  assert.equal(customSuggestions.at(-1).name, 'door')
  assert.equal(customSuggestions[0].name, 'xx')
  assert.equal(customSuggestions[0].kind, 'GENERIC')

  preferences.removeCustomElementName('XX', storage)
  assert.equal(
    preferences.availableElementSuggestions(storage).some((suggestion) => suggestion.name === 'xx'),
    false,
  )
})

test('semantic colors expose hex swatches and a reserved layout color', async () => {
  const preferences = await import('../src/utils/venuePreferences.js').catch(() => ({}))

  assert.equal(typeof preferences.SYSTEM_LAYOUT_COLOR, 'string')
  assert.match(preferences.SYSTEM_LAYOUT_COLOR, /^#[0-9a-f]{6}$/)
  assert.ok(preferences.SEMANTIC_COLOR_SWATCHES.length >= 20)
  assert.equal(
    new Set(preferences.SEMANTIC_COLOR_SWATCHES.map((color) => color.value)).size,
    preferences.SEMANTIC_COLOR_SWATCHES.length,
  )
  assert.ok(!preferences.SEMANTIC_COLOR_SWATCHES.some(
    (color) => color.value === preferences.SYSTEM_LAYOUT_COLOR,
  ))
})

test('next available semantic color avoids used colors', async () => {
  const preferences = await import('../src/utils/venuePreferences.js').catch(() => ({}))
  const used = preferences.SEMANTIC_COLOR_SWATCHES.slice(0, 3).map((color) => color.value)

  assert.equal(preferences.nextAvailableSemanticColor(used), preferences.SEMANTIC_COLOR_SWATCHES[3].value)
})

test('generic element color helpers group by element name', async () => {
  const preferences = await import('../src/utils/venuePreferences.js')
  const elements = [
    { kind: 'GENERIC', name: '门', fillColor: '#dbeafe' },
    { kind: 'GENERIC', name: '门', fillColor: '#dbeafe' },
    { kind: 'GENERIC', name: '桌子', fillColor: '#fef3c7' },
    { kind: 'SEAT', name: '座位', fillColor: '#dbeafe' },
  ]

  assert.deepEqual(
    [...preferences.genericElementColorMap(elements)],
    [
      ['门', '#dbeafe'],
      ['桌子', '#fef3c7'],
    ],
  )
  assert.deepEqual(preferences.usedGenericElementColors(elements, '门'), ['#fef3c7'])
  assert.deepEqual(preferences.conflictingGenericColorNames(elements), [])
})

test('generic element color conflicts report different names sharing a color', async () => {
  const preferences = await import('../src/utils/venuePreferences.js')

  assert.deepEqual(preferences.conflictingGenericColorNames([
    { kind: 'GENERIC', name: '门', fillColor: '#dbeafe' },
    { kind: 'GENERIC', name: '桌子', fillColor: '#dbeafe' },
  ]), [{ color: '#dbeafe', names: ['门', '桌子'] }])
})

test('custom suggestions avoid built-in generic fill colors', async () => {
  const preferences = await import('../src/utils/venuePreferences.js')
  const storage = createStorage({
    [preferences.CUSTOM_ELEMENT_STORAGE_KEY]: JSON.stringify(['讲台', '灯光']),
  })
  const builtInColors = new Set(
    preferences.availableElementSuggestions()
      .filter((suggestion) => suggestion.kind === 'GENERIC' && !suggestion.custom)
      .map((suggestion) => suggestion.fillColor),
  )
  const customColors = preferences.customElementSuggestions(storage).map((suggestion) => suggestion.fillColor)

  assert.equal(customColors.every((color) => !builtInColors.has(color)), true)
  assert.equal(new Set(customColors).size, customColors.length)
})

test('custom suggestions reuse same-name colors and avoid colors used by different names', async () => {
  const preferences = await import('../src/utils/venuePreferences.js')
  const storage = createStorage({
    [preferences.CUSTOM_ELEMENT_STORAGE_KEY]: JSON.stringify(['讲台', '灯光']),
  })
  const suggestions = preferences.availableElementSuggestions(storage, [
    { id: 'lectern-1', kind: 'GENERIC', name: '讲台', fillColor: '#f97316' },
    { id: 'door-1', kind: 'GENERIC', name: '门', fillColor: '#dbeafe' },
  ])
  const lectern = suggestions.find((suggestion) => suggestion.name === '讲台')
  const lighting = suggestions.find((suggestion) => suggestion.name === '灯光')

  assert.equal(lectern.fillColor, '#f97316')
  assert.notEqual(lighting.fillColor, lectern.fillColor)
  assert.notEqual(lighting.fillColor, '#dbeafe')
})

test('semantic color fallback remains non-seat and unused after the palette is exhausted', async () => {
  const preferences = await import('../src/utils/venuePreferences.js')
  const used = preferences.semanticColorValues()
  const fallback = preferences.nextAvailableSemanticColor(used)

  assert.match(fallback, /^#[0-9a-f]{6}$/)
  assert.notEqual(fallback, '#ffffff')
  assert.equal(used.includes(fallback), false)
})

test('custom colors use one shared LRU list with at most five recent entries', async () => {
  const preferences = await import('../src/utils/venuePreferences.js').catch(() => ({}))
  assert.equal(typeof preferences.saveCustomColor, 'function')
  const storage = createStorage()

  preferences.saveCustomColor('#12ABc0', storage)
  preferences.saveCustomColor('#2f855a', storage)
  preferences.saveCustomColor('#805ad5', storage)
  preferences.saveCustomColor('#f97316', storage)
  preferences.saveCustomColor('#14b8a6', storage)
  preferences.saveCustomColor('#e11d48', storage)
  preferences.saveCustomColor('12abc0', storage)
  preferences.saveCustomColor('#ffffff', storage)
  preferences.saveCustomColor('not-a-color', storage)

  const fillColors = preferences.availableColorSwatches(storage)

  assert.deepEqual(fillColors.slice(-5).map((color) => color.value), [
    '#ffffff',
    '#12abc0',
    '#e11d48',
    '#14b8a6',
    '#f97316',
  ])
  assert.deepEqual(
    fillColors.slice(-5).map((color) => ({
      name: color.name,
      custom: Boolean(color.custom),
      title: color.title,
    })),
    [
      { name: '#ffffff', custom: true, title: '#ffffff' },
      { name: '#12abc0', custom: true, title: '#12abc0' },
      { name: '#e11d48', custom: true, title: '#e11d48' },
      { name: '#14b8a6', custom: true, title: '#14b8a6' },
      { name: '#f97316', custom: true, title: '#f97316' },
    ],
  )

  preferences.removeCustomColor('#12ABC0', storage)
  assert.deepEqual(
    preferences.availableColorSwatches(storage).slice(-4).map((color) => color.value),
    ['#ffffff', '#e11d48', '#14b8a6', '#f97316'],
  )
})
