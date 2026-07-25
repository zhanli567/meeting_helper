import assert from 'node:assert/strict'
import test from 'node:test'

test('发布版本名称会去除首尾空格并限制为空名称', async () => {
  const rules = await import('../src/utils/versionRules.js').catch(() => ({}))
  assert.equal(typeof rules.normalizeVersionName, 'function')

  assert.equal(rules.normalizeVersionName('  领导确认版  '), '领导确认版')
  assert.equal(rules.normalizeVersionName('   '), '')
})

test('发布版本名称在同一方案内忽略大小写和首尾空格判重', async () => {
  const rules = await import('../src/utils/versionRules.js').catch(() => ({}))
  assert.equal(typeof rules.hasDuplicateVersionName, 'function')

  const versions = [{ versionName: 'Final' }, { versionName: '领导确认版' }]
  assert.equal(rules.hasDuplicateVersionName(' final ', versions), true)
  assert.equal(rules.hasDuplicateVersionName('领导确认版', versions), true)
  assert.equal(rules.hasDuplicateVersionName('会务确认版', versions), false)
})
