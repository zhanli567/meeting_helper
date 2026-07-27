import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

import { createSSRApp, defineComponent, h } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { createServer } from 'vite'

const frontendRoot = fileURLToPath(new URL('..', import.meta.url))

const Passthrough = defineComponent({
  inheritAttrs: false,
  setup(_props, { attrs, slots }) {
    return () => h('div', attrs, slots.default?.())
  },
})

const LinkStub = defineComponent({
  inheritAttrs: false,
  setup(_props, { attrs, slots }) {
    return () => h('a', attrs, slots.default?.())
  },
})

async function loadDetailDrawer(t) {
  const server = await createServer({
    root: frontendRoot,
    appType: 'custom',
    server: { middlewareMode: true, hmr: false },
  })
  t.after(() => server.close())
  return (await server.ssrLoadModule('/src/components/VenueDetailDrawer.vue')).default
}

async function renderBookingUrl(component, bookingUrl) {
  const app = createSSRApp(component, {
    modelValue: true,
    venue: {
      location: 'A101',
      bookingUrl,
      seatCount: 0,
    },
  })
  app.config.warnHandler = () => {}
  app.directive('loading', { getSSRProps: () => ({}) })
  app.component('el-drawer', Passthrough)
  app.component('el-descriptions', Passthrough)
  app.component('el-descriptions-item', Passthrough)
  app.component('el-link', LinkStub)
  return renderToString(app)
}

test('安全的绝对 HTTPS 预定链接才渲染为带隔离关系的锚点', async (t) => {
  const VenueDetailDrawer = await loadDetailDrawer(t)

  const html = await renderBookingUrl(
    VenueDetailDrawer,
    '  https://example.test/booking?q=1  ',
  )

  assert.match(html, /<a[^>]+href="https:\/\/example\.test\/booking\?q=1"/)
  assert.match(html, /target="_blank"/)
  assert.match(html, /rel="noopener noreferrer"/)
  assert.match(html, />\s*打开链接\s*<\/a>/)
})

test('危险或非绝对预定链接只渲染普通文本且不产生锚点', async (t) => {
  const VenueDetailDrawer = await loadDetailDrawer(t)
  const unsafeUrls = [
    'javascript:alert(1)',
    'data:text/html,unsafe',
    'file:///tmp/unsafe',
    '//example.test/booking',
    'https:/missing-host',
    'https://example.test/\nunsafe',
  ]

  for (const unsafeUrl of unsafeUrls) {
    const html = await renderBookingUrl(VenueDetailDrawer, unsafeUrl)
    assert.doesNotMatch(html, /<a(?:\s|>)/)
    assert.match(html, new RegExp(unsafeUrl.split('\n')[0].replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
  }
})
