import assert from 'node:assert/strict'
import test from 'node:test'

test('新增人员入口不再保留旧悬浮菜单定位函数', async () => {
  const layout = await import('../src/utils/workbenchLayout.js').catch(() => ({}))
  assert.equal('placeFloatingMenu' in layout, false)
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
