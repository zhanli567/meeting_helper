import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc'
import * as Vue from 'vue'
import {
  createRenderer,
  createSSRApp,
  defineComponent,
  h,
  nextTick,
  reactive,
} from 'vue'
import { renderToString } from '@vue/server-renderer'
import { createServer } from 'vite'

const frontendRoot = fileURLToPath(new URL('..', import.meta.url))

async function loadEditor(t) {
  const server = await createServer({
    root: frontendRoot,
    appType: 'custom',
    server: { middlewareMode: true },
  })
  t.after(() => server.close())
  const componentPaths = [
    '/src/components/VenueLayoutEditor.vue',
    '/src/components/VenueElementPanel.vue',
    '/src/components/VenueElementPicker.vue',
  ]
  const components = []
  for (const componentPath of componentPaths) {
    const component = (await server.ssrLoadModule(componentPath)).default
    const filename = fileURLToPath(new URL(`..${componentPath}`, import.meta.url))
    const source = await readFile(filename, 'utf8')
    const { descriptor } = parse(source, { filename })
    const script = compileScript(descriptor, {
      id: `runtime-${componentPath.replace(/\W/g, '-')}`,
    })
    const compiled = compileTemplate({
      source: descriptor.template.content,
      filename,
      id: `runtime-${componentPath.replace(/\W/g, '-')}`,
      compilerOptions: { bindingMetadata: script.bindings },
    })
    assert.deepEqual(compiled.errors, [])
    const executable = compiled.code
      .replace(
        /import \{([^}]+)\} from "vue"/,
        (_, bindings) => `const {${bindings.replaceAll(' as ', ': ')}} = Vue`,
      )
      .replace('export function render', 'function render')
    component.render = Function('Vue', `${executable}\nreturn render`)(Vue)
    components.push(component)
  }
  return components[0]
}

class HostElement {
  constructor(tag) {
    this.tag = tag
    this.props = {}
    this.children = []
    this.parent = undefined
    this.scrollLeft = 0
    this.scrollTop = 0
    this.clientWidth = tag === 'root' ? 1200 : 824
    this.clientHeight = tag === 'root' ? 800 : 648
    this.scrollWidth = this.clientWidth
    this.scrollHeight = this.clientHeight
  }

  get dataset() {
    return Object.fromEntries(
      Object.entries(this.props)
        .filter(([key]) => key.startsWith('data-'))
        .map(([key, value]) => [
          key
            .slice(5)
            .replace(/-([a-z])/g, (_, letter) => letter.toUpperCase()),
          String(value),
        ]),
    )
  }

  contains(target) {
    for (let node = target; node; node = node.parent) {
      if (node === this) return true
    }
    return false
  }

  closest(selector) {
    if (selector !== '[data-editor-id]') return undefined
    for (let node = this; node; node = node.parent) {
      if (node.props?.['data-editor-id'] != null) return node
    }
    return undefined
  }

  getBoundingClientRect() {
    const classes = String(this.props.class || '').split(/\s+/)
    if (classes.includes('canvas-pane') || classes.includes('canvas-viewport')) {
      this.clientWidth = 800
      this.clientHeight = 600
      this.scrollWidth = 1200
      this.scrollHeight = 900
      return { left: 0, top: 0, right: 800, bottom: 600, width: 800, height: 600 }
    }
    if (classes.includes('designer-canvas')) {
      const width = Number.parseFloat(this.props.style?.width) || 704
      const height = Number.parseFloat(this.props.style?.height) || 528
      return {
        left: 48,
        top: 36,
        right: 48 + width,
        bottom: 36 + height,
        width,
        height,
      }
    }
    if (classes.includes('picker-popover')) {
      return { left: 0, top: 0, right: 390, bottom: 430, width: 390, height: 430 }
    }
    const width = Number.parseFloat(this.props.style?.width) || 0
    const height = Number.parseFloat(this.props.style?.height) || 0
    const left = Number.parseFloat(this.props.style?.left) || 0
    const top = Number.parseFloat(this.props.style?.top) || 0
    return { left, top, right: left + width, bottom: top + height, width, height }
  }

  setPointerCapture() {}

  hasPointerCapture() {
    return false
  }

  releasePointerCapture() {}
}

function createHostRenderer() {
  const insert = (child, parent, anchor) => {
    child.parent = parent
    const index = anchor ? parent.children.indexOf(anchor) : -1
    if (index < 0) parent.children.push(child)
    else parent.children.splice(index, 0, child)
  }
  return createRenderer({
    patchProp(element, key, _previous, next) {
      element.props[key] = next
    },
    insert,
    remove(child) {
      const index = child.parent?.children.indexOf(child) ?? -1
      if (index >= 0) child.parent.children.splice(index, 1)
      child.parent = undefined
    },
    createElement: (tag) => new HostElement(tag),
    createText: (text) => ({ tag: '#text', text, parent: undefined }),
    createComment: (text) => ({ tag: '#comment', text, parent: undefined }),
    setText(node, text) {
      node.text = text
    },
    setElementText(element, text) {
      const node = { tag: '#text', text, parent: element }
      element.children = [node]
    },
    parentNode: (node) => node.parent,
    nextSibling(node) {
      const siblings = node.parent?.children || []
      return siblings[siblings.indexOf(node) + 1]
    },
    querySelector: () => undefined,
    setScopeId(element, id) {
      element.props[id] = ''
    },
    insertStaticContent(content, parent, anchor) {
      const node = { tag: '#static', text: content, parent: undefined }
      insert(node, parent, anchor)
      return [node, node]
    },
  })
}

const Passthrough = defineComponent({
  inheritAttrs: false,
  setup(_props, { attrs, slots }) {
    return () => h('div', attrs, slots.default?.())
  },
})

const ButtonStub = defineComponent({
  inheritAttrs: false,
  setup(_props, { attrs, slots }) {
    return () => h('button', attrs, slots.default?.())
  },
})

const InputStub = defineComponent({
  inheritAttrs: false,
  props: { modelValue: { type: [String, Number], default: '' } },
  emits: ['update:modelValue', 'change'],
  setup(props, { attrs, emit }) {
    return () =>
      h('input', {
        ...attrs,
        value: props.modelValue,
        onInput: (event) => emit('update:modelValue', event.target.value),
        onChange: (event) => emit('change', event.target.value),
      })
  },
})

function allNodes(root) {
  const result = []
  const visit = (node) => {
    if (!node) return
    result.push(node)
    for (const child of node.children || []) visit(child)
  }
  visit(root)
  return result
}

function nodeText(node) {
  if (node.text != null) return String(node.text)
  return (node.children || []).map(nodeText).join('')
}

function byClass(root, className) {
  return allNodes(root).filter((node) =>
    String(node.props?.class || '').split(/\s+/).includes(className),
  )
}

function byText(root, text) {
  return allNodes(root).find(
    (node) => node.tag === 'button' && nodeText(node).includes(text),
  )
}

function pointerEvent(target, overrides = {}) {
  return {
    button: 0,
    pointerId: 1,
    clientX: 180,
    clientY: 140,
    currentTarget: target,
    target,
    preventDefault() {},
    stopPropagation() {},
    ...overrides,
  }
}

async function settle() {
  await nextTick()
  await nextTick()
}

async function mountEditor(t, Editor) {
  const previousWindow = globalThis.window
  const previousElement = globalThis.Element
  const listeners = new Map()
  globalThis.Element = HostElement
  globalThis.window = {
    addEventListener(type, listener) {
      const current = listeners.get(type) || new Set()
      current.add(listener)
      listeners.set(type, current)
    },
    removeEventListener(type, listener) {
      listeners.get(type)?.delete(listener)
    },
    dispatch(type, event) {
      for (const listener of listeners.get(type) || []) listener(event)
    },
  }
  const root = new HostElement('root')
  const updates = []
  const renderer = createHostRenderer()
  const app = renderer.createApp(Editor, {
    modelValue: reactive({
      gridRows: 12,
      gridColumns: 16,
      elements: [
        {
          id: 'seat-1',
          kind: 'SEAT',
          name: '座位',
          row: 2,
          column: 3,
          rowSpan: 1,
          columnSpan: 1,
          fillColor: '#ffffff',
          borderColor: '#8fb4e8',
        },
      ],
    }),
    showBack: false,
    'onUpdate:modelValue': (layout) => updates.push(layout),
  })
  app.config.warnHandler = () => {}
  app.provide(Vue.ssrContextKey, { modules: new Set() })
  for (const name of [
    'el-button-group',
    'el-form',
    'el-form-item',
    'el-icon',
    'el-option',
    'el-select',
    'el-tag',
  ]) {
    app.component(name, Passthrough)
  }
  app.component('el-button', ButtonStub)
  app.component('el-autocomplete', InputStub)
  app.component('el-color-picker', InputStub)
  app.mount(root)
  t.after(() => {
    app.unmount()
    globalThis.window = previousWindow
    globalThis.Element = previousElement
  })
  await settle()
  return { root, updates, window: globalThis.window }
}

test('挂载已有响应式布局不会因克隆 Proxy 崩溃', async (t) => {
  const VenueLayoutEditor = await loadEditor(t)
  const layout = reactive({
    gridRows: 12,
    gridColumns: 16,
    elements: [
      {
        id: 'seat-1',
        kind: 'SEAT',
        name: '座位',
        row: 2,
        column: 3,
        rowSpan: 1,
        columnSpan: 1,
        fillColor: '#ffffff',
        borderColor: '#8fb4e8',
      },
    ],
  })
  const app = createSSRApp({
    render: () =>
      h(VenueLayoutEditor, {
        modelValue: layout,
        showBack: false,
      }),
  })
  app.config.warnHandler = () => {}

  await assert.doesNotReject(() => renderToString(app))
})

test('真实组件运行时保持属性预览并支持移动缩放与撤销重做', async (t) => {
  const VenueLayoutEditor = await loadEditor(t)
  const mounted = await mountEditor(t, VenueLayoutEditor)
  let element = byClass(mounted.root, 'layout-element')[0]

  element.props.onPointerdown(pointerEvent(element))
  mounted.window.dispatch('pointerup', pointerEvent(element))
  await settle()

  let nameInput = allNodes(mounted.root).find(
    (node) => node.tag === 'input' && node.props?.placeholder,
  )
  nameInput.props.onInput({ target: { value: '贵宾席' } })
  allNodes(mounted.root)
    .find((node) => node.props?.['aria-label'] === '填充色 #fde68a')
    .props.onClick()
  allNodes(mounted.root)
    .find((node) => node.props?.['aria-label'] === '边框色 #fed7aa')
    .props.onClick()
  await settle()

  element = byClass(mounted.root, 'layout-element')[0]
  assert.match(nodeText(element), /贵宾席/)
  assert.equal(element.props.style.backgroundColor, '#fde68a')
  assert.equal(element.props.style.borderColor, '#fed7aa')

  element.props.onPointerdown(pointerEvent(element))
  mounted.window.dispatch('pointerup', pointerEvent(element))
  await settle()
  element = byClass(mounted.root, 'layout-element')[0]
  assert.match(nodeText(element), /贵宾席/)
  assert.equal(element.props.style.backgroundColor, '#fde68a')
  assert.equal(element.props.style.borderColor, '#fed7aa')

  byText(mounted.root, '取消').props.onClick()
  await settle()
  element = byClass(mounted.root, 'layout-element')[0]
  assert.match(nodeText(element), /座位/)
  assert.equal(element.props.style.backgroundColor, '#ffffff')
  assert.equal(element.props.style.borderColor, '#8fb4e8')

  element.props.onPointerdown(pointerEvent(element))
  mounted.window.dispatch('pointerup', pointerEvent(element))
  await settle()
  nameInput = allNodes(mounted.root).find(
    (node) => node.tag === 'input' && node.props?.placeholder,
  )
  nameInput.props.onInput({ target: { value: '贵宾席' } })
  allNodes(mounted.root)
    .find((node) => node.props?.['aria-label'] === '填充色 #fde68a')
    .props.onClick()
  allNodes(mounted.root)
    .find((node) => node.props?.['aria-label'] === '边框色 #fed7aa')
    .props.onClick()
  await settle()
  byText(mounted.root, '确认').props.onClick()
  await settle()
  assert.match(nodeText(byClass(mounted.root, 'layout-element')[0]), /贵宾席/)

  element = byClass(mounted.root, 'layout-element')[0]
  element.props.onPointerdown(pointerEvent(element))
  mounted.window.dispatch(
    'pointermove',
    pointerEvent(element, { clientX: 224, clientY: 140 }),
  )
  mounted.window.dispatch(
    'pointerup',
    pointerEvent(element, { clientX: 224, clientY: 140 }),
  )
  await settle()
  assert.equal(mounted.updates.at(-1).elements[0].column, 4)

  element = byClass(mounted.root, 'layout-element')[0]
  const southEast = byClass(element, 'handle-se')[0]
  southEast.props.onPointerdown(pointerEvent(southEast))
  mounted.window.dispatch(
    'pointermove',
    pointerEvent(southEast, { clientX: 224, clientY: 184 }),
  )
  mounted.window.dispatch(
    'pointerup',
    pointerEvent(southEast, { clientX: 224, clientY: 184 }),
  )
  await settle()
  assert.deepEqual(
    {
      rowSpan: mounted.updates.at(-1).elements[0].rowSpan,
      columnSpan: mounted.updates.at(-1).elements[0].columnSpan,
    },
    { rowSpan: 2, columnSpan: 2 },
  )

  byText(mounted.root, '撤销').props.onClick()
  await settle()
  assert.deepEqual(
    {
      rowSpan: mounted.updates.at(-1).elements[0].rowSpan,
      columnSpan: mounted.updates.at(-1).elements[0].columnSpan,
    },
    { rowSpan: 1, columnSpan: 1 },
  )
  byText(mounted.root, '重做').props.onClick()
  await settle()
  assert.deepEqual(
    {
      rowSpan: mounted.updates.at(-1).elements[0].rowSpan,
      columnSpan: mounted.updates.at(-1).elements[0].columnSpan,
    },
    { rowSpan: 2, columnSpan: 2 },
  )
})
