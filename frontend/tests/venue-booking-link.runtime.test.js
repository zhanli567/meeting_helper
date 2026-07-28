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

async function loadDetailDrawer(t) {
  const server = await createServer({
    root: frontendRoot,
    appType: 'custom',
    server: { middlewareMode: true, hmr: false },
  })
  t.after(() => server.close())
  return (await server.ssrLoadModule('/src/components/VenueDetailDrawer.vue')).default
}

async function renderVenueDetail(component, venue = {}) {
  const app = createSSRApp(component, {
    modelValue: true,
    venue: {
      location: 'A101',
      campus: '总部园区',
      manualCapacity: 36,
      contactInfo: '张三',
      remarks: '靠近南门',
      bookingUrl: 'https://example.test/booking?q=1',
      mainScreenResolution: '3840×2160',
      stageDimensions: '8m×3m',
      meetingRoomFunctions: '视频会议',
      servicesProvided: '会务支持',
      description: '旧说明',
      seatCount: 0,
      ...venue,
    },
  })
  app.config.warnHandler = () => {}
  app.directive('loading', { getSSRProps: () => ({}) })
  app.component('el-drawer', Passthrough)
  app.component('el-descriptions', Passthrough)
  app.component('el-descriptions-item', Passthrough)
  return renderToString(app)
}

test('场馆详情只渲染新建表单保留的高频字段', async (t) => {
  const VenueDetailDrawer = await loadDetailDrawer(t)

  const html = await renderVenueDetail(VenueDetailDrawer)

  for (const value of ['A101', '总部园区', '36', '张三', '靠近南门']) {
    assert.match(html, new RegExp(value))
  }
  for (const hidden of [
    'https://example.test/booking',
    '3840×2160',
    '8m×3m',
    '视频会议',
    '会务支持',
    '旧说明',
  ]) {
    assert.doesNotMatch(html, new RegExp(hidden.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
  }
  assert.doesNotMatch(html, /<a(?:\s|>)/)
})
