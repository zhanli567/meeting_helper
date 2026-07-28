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

test('本地自定义元素去重后追加到常用元素列表并可移除', async () => {
  const preferences = await import('../src/utils/venuePreferences.js').catch(() => ({}))
  assert.equal(typeof preferences.saveCustomElementName, 'function')
  const storage = createStorage()

  preferences.saveCustomElementName('  xx  ', storage)
  preferences.saveCustomElementName('XX', storage)
  preferences.saveCustomElementName('门', storage)

  assert.deepEqual(
    preferences.availableElementSuggestions(storage).map((suggestion) => ({
      name: suggestion.name,
      kind: suggestion.kind,
      custom: Boolean(suggestion.custom),
    })),
    [
      { name: '座位', kind: 'SEAT', custom: false },
      { name: '门', kind: 'GENERIC', custom: false },
      { name: '墙', kind: 'GENERIC', custom: false },
      { name: '桌子', kind: 'GENERIC', custom: false },
      { name: '摄像', kind: 'GENERIC', custom: false },
      { name: '舞台', kind: 'GENERIC', custom: false },
      { name: '显示屏', kind: 'GENERIC', custom: false },
      { name: 'xx', kind: 'GENERIC', custom: true },
    ],
  )

  preferences.removeCustomElementName('XX', storage)
  assert.equal(
    preferences.availableElementSuggestions(storage).some((suggestion) => suggestion.name === 'xx'),
    false,
  )
})

test('本地自定义颜色按填充和边框分别保存并用 RGB 文案提示', async () => {
  const preferences = await import('../src/utils/venuePreferences.js').catch(() => ({}))
  assert.equal(typeof preferences.saveCustomColor, 'function')
  const storage = createStorage()

  preferences.saveCustomColor('fillColor', '#12ABc0', storage)
  preferences.saveCustomColor('fillColor', '12abc0', storage)
  preferences.saveCustomColor('borderColor', '#2f855a', storage)
  preferences.saveCustomColor('fillColor', '#ffffff', storage)
  preferences.saveCustomColor('fillColor', 'not-a-color', storage)

  const colors = preferences.availableColorSwatches('fillColor', storage)
  assert.deepEqual(
    colors.map((color) => ({
      name: color.name,
      value: color.value,
      custom: Boolean(color.custom),
      title: color.title,
    })),
    [
      { name: '白色', value: '#ffffff', custom: false, title: '白色' },
      { name: '蓝色', value: '#dbeafe', custom: false, title: '蓝色' },
      { name: '绿色', value: '#dcfce7', custom: false, title: '绿色' },
      { name: '红色', value: '#fee2e2', custom: false, title: '红色' },
      { name: '黄色', value: '#fef3c7', custom: false, title: '黄色' },
      { name: 'RGB(18, 171, 192)', value: '#12abc0', custom: true, title: 'RGB(18, 171, 192)' },
    ],
  )
  assert.deepEqual(
    preferences.availableColorSwatches('borderColor', storage).map((color) => color.value),
    ['#ffffff', '#dbeafe', '#dcfce7', '#fee2e2', '#fef3c7', '#2f855a'],
  )

  preferences.removeCustomColor('fillColor', '#12ABC0', storage)
  assert.deepEqual(
    preferences.availableColorSwatches('fillColor', storage).map((color) => color.value),
    ['#ffffff', '#dbeafe', '#dcfce7', '#fee2e2', '#fef3c7'],
  )
  assert.deepEqual(
    preferences.availableColorSwatches('borderColor', storage).map((color) => color.value),
    ['#ffffff', '#dbeafe', '#dcfce7', '#fee2e2', '#fef3c7', '#2f855a'],
  )
})
