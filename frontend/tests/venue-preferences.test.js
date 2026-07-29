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

test('custom colors use one shared LRU list with at most five recent entries', async () => {
  const preferences = await import('../src/utils/venuePreferences.js').catch(() => ({}))
  assert.equal(typeof preferences.saveCustomColor, 'function')
  const storage = createStorage()

  preferences.saveCustomColor('fillColor', '#12ABc0', storage)
  preferences.saveCustomColor('borderColor', '#2f855a', storage)
  preferences.saveCustomColor('fillColor', '#805ad5', storage)
  preferences.saveCustomColor('fillColor', '#f97316', storage)
  preferences.saveCustomColor('borderColor', '#14b8a6', storage)
  preferences.saveCustomColor('fillColor', '#e11d48', storage)
  preferences.saveCustomColor('borderColor', '12abc0', storage)
  preferences.saveCustomColor('fillColor', '#ffffff', storage)
  preferences.saveCustomColor('fillColor', 'not-a-color', storage)

  const expectedValues = [
    '#ffffff',
    '#dbeafe',
    '#dcfce7',
    '#fee2e2',
    '#fef3c7',
    '#12abc0',
    '#e11d48',
    '#14b8a6',
    '#f97316',
    '#805ad5',
  ]
  const fillColors = preferences.availableColorSwatches('fillColor', storage)

  assert.deepEqual(fillColors.map((color) => color.value), expectedValues)
  assert.deepEqual(
    preferences.availableColorSwatches('borderColor', storage).map((color) => color.value),
    expectedValues,
  )
  assert.deepEqual(
    fillColors.slice(5).map((color) => ({
      name: color.name,
      custom: Boolean(color.custom),
      title: color.title,
    })),
    [
      { name: 'RGB(18, 171, 192)', custom: true, title: 'RGB(18, 171, 192)' },
      { name: 'RGB(225, 29, 72)', custom: true, title: 'RGB(225, 29, 72)' },
      { name: 'RGB(20, 184, 166)', custom: true, title: 'RGB(20, 184, 166)' },
      { name: 'RGB(249, 115, 22)', custom: true, title: 'RGB(249, 115, 22)' },
      { name: 'RGB(128, 90, 213)', custom: true, title: 'RGB(128, 90, 213)' },
    ],
  )

  preferences.removeCustomColor('fillColor', '#12ABC0', storage)
  assert.deepEqual(
    preferences.availableColorSwatches('borderColor', storage).map((color) => color.value),
    ['#ffffff', '#dbeafe', '#dcfce7', '#fee2e2', '#fef3c7', '#e11d48', '#14b8a6', '#f97316', '#805ad5'],
  )
})
