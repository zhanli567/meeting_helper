import assert from 'node:assert/strict'
import test from 'node:test'

test('加号位于左上角时菜单显示在按钮下方且不遮挡拖动入口', async () => {
  const layout = await import('../src/utils/workbenchLayout.js').catch(() => ({}))
  assert.equal(typeof layout.placeFloatingMenu, 'function')

  assert.deepEqual(
    layout.placeFloatingMenu({
      anchor: { x: 8, y: 72, width: 48, height: 48 },
      menu: { width: 220, height: 132 },
      viewport: { width: 1280, height: 720 },
      gap: 10,
      margin: 10,
    }),
    { left: 10, top: 130 },
  )
})

test('加号位于右下角时菜单显示在按钮上方并保持在视口内', async () => {
  const layout = await import('../src/utils/workbenchLayout.js').catch(() => ({}))
  assert.equal(typeof layout.placeFloatingMenu, 'function')

  assert.deepEqual(
    layout.placeFloatingMenu({
      anchor: { x: 1220, y: 650, width: 48, height: 48 },
      menu: { width: 220, height: 132 },
      viewport: { width: 1280, height: 720 },
      gap: 10,
      margin: 10,
    }),
    { left: 1050, top: 508 },
  )
})

test('排座工作台新增人员加号初始放在左下角', async () => {
  const { readFile } = await import('node:fs/promises')
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /fab\.x\s*=\s*24/)
  assert.match(source, /window\.innerHeight - 96/)
})
