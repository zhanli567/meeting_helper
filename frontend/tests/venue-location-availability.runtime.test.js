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

test('地点失焦规则调用精确可用性接口并把重名结果显示为字段错误', async (t) => {
  const server = await createServer({
    root: frontendRoot,
    appType: 'custom',
    server: { middlewareMode: true, hmr: false },
  })
  t.after(() => server.close())

  const { venueApi } = await server.ssrLoadModule('/src/api/venue.js')
  const originalCheck = venueApi.locationAvailability
  const calls = []
  venueApi.locationAvailability = async (location, excludeId) => {
    calls.push({ location, excludeId })
    return { available: false }
  }
  t.after(() => {
    venueApi.locationAvailability = originalCheck
  })

  const VenueInfoForm = (await server.ssrLoadModule('/src/components/VenueInfoForm.vue')).default
  let capturedRules
  const FormStub = defineComponent({
    inheritAttrs: false,
    props: {
      rules: { type: Object, required: true },
    },
    setup(props, { attrs, slots }) {
      capturedRules = props.rules
      return () => h('form', attrs, slots.default?.())
    },
  })
  const app = createSSRApp(VenueInfoForm, {
    modelValue: { location: '原地点' },
    excludeVenueId: 'venue-1',
  })
  app.config.warnHandler = () => {}
  app.component('el-form', FormStub)
  app.component('el-form-item', Passthrough)
  app.component('el-input', Passthrough)
  app.component('el-input-number', Passthrough)
  await renderToString(app)

  const availabilityRule = capturedRules.location.find((rule) => rule.validator)
  assert.ok(availabilityRule)
  assert.equal(availabilityRule.trigger, 'blur')

  let fieldError
  await availabilityRule.validator({}, '  EXISTING HALL  ', (error) => {
    fieldError = error
  })

  assert.deepEqual(calls, [{ location: 'EXISTING HALL', excludeId: 'venue-1' }])
  assert.equal(fieldError?.message, '该地点已存在场馆模板')
})
