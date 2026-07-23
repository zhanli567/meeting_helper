import { describe, it, expect } from 'vitest'

import { shallowMount } from '@vue/test-utils'
import App from '../App.vue'

describe('App', () => {
  it('provides the Chinese UI configuration and router outlet', () => {
    const wrapper = shallowMount(App, {
      global: {
        stubs: {
          ElConfigProvider: { template: '<div><slot /></div>' },
          RouterView: { template: '<main data-test="router-view" />' },
        },
      },
    })

    expect(wrapper.find('[data-test="router-view"]').exists()).toBe(true)
  })
})
