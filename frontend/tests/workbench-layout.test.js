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

test('排座工作台新增人员加号按左侧画布区域右下角定位', async () => {
  const layout = await import('../src/utils/workbenchLayout.js').catch(() => ({}))
  assert.equal(typeof layout.placeFabInRegion, 'function')

  assert.deepEqual(
    layout.placeFabInRegion({
      region: { left: 14, top: 78, right: 862, bottom: 706 },
      button: { width: 48, height: 48 },
      viewport: { width: 1280, height: 720 },
      gap: 24,
      minTop: 64,
    }),
    { x: 790, y: 634 },
  )
})

test('排座工作台新增人员加号绑定画布区域而不是屏幕左半区', async () => {
  const { readFile } = await import('node:fs/promises')
  const source = await readFile(new URL('../src/views/WorkbenchView.vue', import.meta.url), 'utf8')

  assert.match(source, /ref="canvasShellRef"/)
  assert.match(source, /canvasShellRef\.value\?\.getBoundingClientRect/)
  assert.doesNotMatch(source, /window\.innerWidth \/ 2 - FAB_SIZE - FAB_EDGE_GAP/)
})
