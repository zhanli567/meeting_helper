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

async function loadEditor(t, componentIndex = 0, includeColorPicker = false) {
  const server = await createServer({
    root: frontendRoot,
    appType: 'custom',
    server: { middlewareMode: true, hmr: false },
  })
  t.after(() => server.close())
  const componentPaths = [
    '/src/components/VenueLayoutEditor.vue',
    '/src/components/VenueElementPanel.vue',
    '/src/components/VenueElementPicker.vue',
    '/src/components/CanvasViewport.vue',
  ]
  if (includeColorPicker) componentPaths.push('/src/components/ColorPickerPopover.vue')
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
  return components[componentIndex]
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
    if (selector.startsWith('.')) {
      const className = selector.slice(1)
      for (let node = this; node; node = node.parent) {
        if (String(node.props?.class || '').split(/\s+/).includes(className)) return node
      }
      return undefined
    }
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

async function mountEditor(t, Editor, propOverrides = {}) {
  const previousWindow = globalThis.window
  const previousElement = globalThis.Element
  const previousDocument = globalThis.document
  const listeners = new Map()
  globalThis.Element = HostElement
  globalThis.document = { body: {} }
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
  const saves = []
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
        },
        {
          id: 'stage-1',
          kind: 'GENERIC',
          name: '舞台',
          row: 6,
          column: 8,
          rowSpan: 2,
          columnSpan: 3,
          fillColor: '#dbeafe',
        },
      ],
    }),
    showBack: false,
    'onUpdate:modelValue': (layout) => updates.push(layout),
    onSave: () => saves.push(true),
    ...propOverrides,
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
    globalThis.document = previousDocument
  })
  await settle()
  return { root, updates, saves, window: globalThis.window }
}

async function mountElementPanel(t, Panel) {
  const previousWindow = globalThis.window
  const previousElement = globalThis.Element
  const previousDocument = globalThis.document
  globalThis.Element = HostElement
  globalThis.document = { body: {} }
  globalThis.window = {
    addEventListener() {},
    removeEventListener() {},
  }
  const root = new HostElement('root')
  const previews = []
  const elements = [
    {
      id: 'seat-1',
      kind: 'SEAT',
      name: '座位',
      row: 1,
      column: 1,
      rowSpan: 1,
      columnSpan: 1,
      fillColor: '#ffffff',
    },
    {
      id: 'stage-1',
      kind: 'GENERIC',
      name: '舞台',
      row: 1,
      column: 2,
      rowSpan: 1,
      columnSpan: 1,
      fillColor: '#dbeafe',
    },
  ]
  const renderer = createHostRenderer()
  const app = renderer.createApp(Panel, {
    element: elements[0],
    elements,
    gridRows: 5,
    gridColumns: 5,
    onPreview: (changes) => previews.push(changes),
  })
  app.config.warnHandler = () => {}
  app.provide(Vue.ssrContextKey, { modules: new Set() })
  for (const name of ['el-form', 'el-form-item', 'el-icon', 'el-popover']) {
    app.component(name, Passthrough)
  }
  app.component('el-button', ButtonStub)
  app.component('el-autocomplete', InputStub)
  app.mount(root)
  t.after(() => {
    app.unmount()
    globalThis.window = previousWindow
    globalThis.Element = previousElement
    globalThis.document = previousDocument
  })
  await settle()
  return { root, previews }
}

function selectedNameInput(root) {
  return allNodes(root).find(
    (node) => node.tag === 'input' && node.props?.['aria-label'] === '显示名称',
  )
}

function colorButton(root, label) {
  return allNodes(root).find((node) => node.props?.['aria-label'] === label)
}

async function selectElement(mounted, element) {
  element.props.onPointerdown(pointerEvent(element))
  mounted.window.dispatch('pointerup', pointerEvent(element))
  await settle()
}

async function previewProperties(mounted, name, fillColor) {
  selectedNameInput(mounted.root).props.onInput({ target: { value: name } })
  colorButton(mounted.root, `填充色 ${fillColor}`).props.onClick()
  await settle()
}

function assertPanelDraft(root, { name, fillColor }) {
  const colorInputs = allNodes(root).filter(
    (node) =>
      node.tag === 'input' &&
      String(node.props?.value || '').startsWith('#'),
  )
  assert.equal(selectedNameInput(root).props.value, name)
  assert.equal(colorInputs[0].props.value, fillColor)
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

test('编辑器实时显示座位与人工容量差异且警告不阻止保存', async (t) => {
  const VenueLayoutEditor = await loadEditor(t)
  const mounted = await mountEditor(t, VenueLayoutEditor, { manualCapacity: 1 })

  assert.match(nodeText(mounted.root), /布局座位数\s*1/)
  assert.match(nodeText(mounted.root), /人工容纳人数\s*1/)
  assert.equal(byClass(mounted.root, 'capacity-warning').length, 0)

  byClass(mounted.root, 'layout-element')[0].props.onDblclick({ stopPropagation() {} })
  await settle()

  assert.match(nodeText(mounted.root), /布局座位数\s*0/)
  assert.equal(byClass(mounted.root, 'capacity-warning').length, 1)
  assert.match(nodeText(byClass(mounted.root, 'capacity-warning')[0]), /不一致/)

  byText(mounted.root, '保存布局').props.onClick()
  assert.equal(mounted.saves.length, 1)
})

test('座位颜色选择器允许选择已被通用元素使用的填充色', async (t) => {
  const VenueElementPanel = await loadEditor(t, 1, true)
  const mounted = await mountElementPanel(t, VenueElementPanel)
  const genericUsedColor = allNodes(mounted.root).find(
    (node) => String(node.props?.class || '').includes('color-swatch-button')
      && node.props?.style?.backgroundColor === '#dbeafe',
  )

  assert.equal(genericUsedColor.props.disabled, false)
  genericUsedColor.props.onClick()
  byClass(mounted.root, 'custom-confirm-button')[0].props.onClick()
  await settle()

  assert.equal(mounted.previews.at(-1).fillColor, '#dbeafe')
})

test('取消重叠元素选择后清除该选择器来源的红显和错误缩小提示', async (t) => {
  const VenueLayoutEditor = await loadEditor(t)
  const mounted = await mountEditor(t, VenueLayoutEditor)
  const canvas = byClass(mounted.root, 'designer-canvas')[0]
  const overlapPoint = pointerEvent(canvas, {
    clientX: 140,
    clientY: 102,
    currentTarget: canvas,
    target: canvas,
  })

  canvas.props.onPointerdown(overlapPoint)
  mounted.window.dispatch('pointerup', overlapPoint)
  await settle()
  byText(mounted.root, '座位').props.onClick()
  await settle()

  assert.equal(byClass(mounted.root, 'layout-element')[0].props.class.includes('conflict'), true)
  assert.equal(byClass(mounted.root, 'conflict-banner').length, 0)

  allNodes(mounted.root)
    .find((node) => node.props?.['aria-label'] === '关闭元素选择')
    .props.onClick()
  await settle()

  assert.equal(byClass(mounted.root, 'layout-element')[0].props.class.includes('conflict'), false)
  assert.equal(byClass(mounted.root, 'conflict-banner').length, 0)
})

test('布局编辑器在画布中展示座位排号且按所有元素范围定位', async () => {
  const source = await readFile(new URL('../src/components/VenueLayoutEditor.vue', import.meta.url), 'utf8')

  assert.match(source, /computeSeatLabels/)
  assert.match(source, /computeElementColumnBounds/)
  assert.match(source, /layoutRowLabels/)
  assert.match(source, /rowLabelStyle/)
  assert.match(source, /row-label row-label-left/)
  assert.match(source, /第\{\{ rowLabel\.displayRow \}\}排/)
})

test('真实组件运行时保持属性预览并支持移动缩放与撤销重做', async (t) => {
  const VenueLayoutEditor = await loadEditor(t)
  const mounted = await mountEditor(t, VenueLayoutEditor)
  let element = byClass(mounted.root, 'layout-element')[0]

  element.props.onPointerdown(pointerEvent(element))
  mounted.window.dispatch('pointerup', pointerEvent(element))
  await settle()

  let nameInput = selectedNameInput(mounted.root)
  nameInput.props.onInput({ target: { value: '贵宾席' } })
  allNodes(mounted.root)
    .find((node) => node.props?.['aria-label'] === '填充色 #fef3c7')
    .props.onClick()
  await settle()

  element = byClass(mounted.root, 'layout-element')[0]
  assert.match(nodeText(element), /贵宾席/)
  assert.equal(element.props.style.backgroundColor, '#fef3c7')

  element.props.onPointerdown(pointerEvent(element))
  mounted.window.dispatch('pointerup', pointerEvent(element))
  await settle()
  element = byClass(mounted.root, 'layout-element')[0]
  assert.match(nodeText(element), /贵宾席/)
  assert.equal(element.props.style.backgroundColor, '#fef3c7')

  byText(mounted.root, '取消').props.onClick()
  await settle()
  element = byClass(mounted.root, 'layout-element')[0]
  assert.match(nodeText(element), /座位/)
  assert.equal(element.props.style.backgroundColor, '#ffffff')

  element.props.onPointerdown(pointerEvent(element))
  mounted.window.dispatch('pointerup', pointerEvent(element))
  await settle()
  nameInput = selectedNameInput(mounted.root)
  nameInput.props.onInput({ target: { value: '贵宾席' } })
  allNodes(mounted.root)
    .find((node) => node.props?.['aria-label'] === '填充色 #fef3c7')
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

  byText(mounted.root, '回退').props.onClick()
  await settle()
  assert.deepEqual(
    {
      rowSpan: mounted.updates.at(-1).elements[0].rowSpan,
      columnSpan: mounted.updates.at(-1).elements[0].columnSpan,
    },
    { rowSpan: 1, columnSpan: 1 },
  )
  byText(mounted.root, '前进').props.onClick()
  await settle()
  assert.deepEqual(
    {
      rowSpan: mounted.updates.at(-1).elements[0].rowSpan,
      columnSpan: mounted.updates.at(-1).elements[0].columnSpan,
    },
    { rowSpan: 2, columnSpan: 2 },
  )
})

test('未确认属性草稿穿过真实移动并在确认时和已提交几何合并', async (t) => {
  const VenueLayoutEditor = await loadEditor(t)
  const mounted = await mountEditor(t, VenueLayoutEditor)
  let element = byClass(mounted.root, 'layout-element')[0]
  await selectElement(mounted, element)
  await previewProperties(mounted, '移动中的贵宾席', '#fef3c7')

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

  element = byClass(mounted.root, 'layout-element')[0]
  assert.match(nodeText(element), /移动中的贵宾席/)
  assert.equal(element.props.style.backgroundColor, '#fef3c7')
  assertPanelDraft(mounted.root, {
    name: '移动中的贵宾席',
    fillColor: '#fef3c7',
  })
  assert.deepEqual(
    {
      column: mounted.updates.at(-1).elements[0].column,
      name: mounted.updates.at(-1).elements[0].name,
      fillColor: mounted.updates.at(-1).elements[0].fillColor,
    },
    {
      column: 4,
      name: '座位',
      fillColor: '#ffffff',
    },
  )

  byText(mounted.root, '确认').props.onClick()
  await settle()
  assert.deepEqual(
    {
      column: mounted.updates.at(-1).elements[0].column,
      name: mounted.updates.at(-1).elements[0].name,
      fillColor: mounted.updates.at(-1).elements[0].fillColor,
    },
    {
      column: 4,
      name: '移动中的贵宾席',
      fillColor: '#fef3c7',
    },
  )
})

test('未确认属性草稿穿过真实缩放，取消只恢复属性且切换元素不串草稿', async (t) => {
  const VenueLayoutEditor = await loadEditor(t)
  const mounted = await mountEditor(t, VenueLayoutEditor)
  let element = byClass(mounted.root, 'layout-element')[0]
  await selectElement(mounted, element)
  await previewProperties(mounted, '缩放中的贵宾席', '#fef3c7')

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

  element = byClass(mounted.root, 'layout-element')[0]
  assert.match(nodeText(element), /缩放中的贵宾席/)
  assert.equal(element.props.style.backgroundColor, '#fef3c7')
  assertPanelDraft(mounted.root, {
    name: '缩放中的贵宾席',
    fillColor: '#fef3c7',
  })
  assert.deepEqual(
    {
      rowSpan: mounted.updates.at(-1).elements[0].rowSpan,
      columnSpan: mounted.updates.at(-1).elements[0].columnSpan,
      name: mounted.updates.at(-1).elements[0].name,
    },
    { rowSpan: 2, columnSpan: 2, name: '座位' },
  )

  byText(mounted.root, '取消').props.onClick()
  await settle()
  element = byClass(mounted.root, 'layout-element')[0]
  assert.match(nodeText(element), /座位/)
  assert.equal(element.props.style.backgroundColor, '#ffffff')
  assert.deepEqual(
    {
      rowSpan: mounted.updates.at(-1).elements[0].rowSpan,
      columnSpan: mounted.updates.at(-1).elements[0].columnSpan,
      name: mounted.updates.at(-1).elements[0].name,
    },
    { rowSpan: 2, columnSpan: 2, name: '座位' },
  )

  await selectElement(mounted, byClass(mounted.root, 'layout-element')[0])
  await previewProperties(mounted, '不得串到舞台', '#fef3c7')
  await selectElement(mounted, byClass(mounted.root, 'layout-element')[1])
  assertPanelDraft(mounted.root, {
    name: '舞台',
    fillColor: '#dbeafe',
  })
  assert.match(nodeText(byClass(mounted.root, 'layout-element')[0]), /座位/)
  assert.match(nodeText(byClass(mounted.root, 'layout-element')[1]), /舞台/)
})

test('画布缩小遇到边界冲突时保持候选尺寸预览直到松手', async (t) => {
  const VenueLayoutEditor = await loadEditor(t)
  const mounted = await mountEditor(t, VenueLayoutEditor)
  const eastHandle = byClass(mounted.root, 'canvas-resize-east')[0]

  eastHandle.props.onPointerdown(pointerEvent(eastHandle, {
    clientX: 700,
    clientY: 300,
    currentTarget: eastHandle,
    target: eastHandle,
  }))
  mounted.window.dispatch('pointermove', pointerEvent(eastHandle, {
    clientX: 300,
    clientY: 300,
  }))
  await settle()

  assert.equal(byClass(mounted.root, 'conflict-banner').length, 1)
  assert.equal(byClass(mounted.root, 'designer-canvas')[0].props.style.width, '220px')
  assert.equal(mounted.updates.length, 0)

  mounted.window.dispatch('pointerup', pointerEvent(eastHandle, {
    clientX: 300,
    clientY: 300,
  }))
  await settle()

  assert.equal(byClass(mounted.root, 'conflict-banner').length, 0)
  assert.equal(byClass(mounted.root, 'designer-canvas')[0].props.style.width, '704px')
  assert.equal(mounted.updates.length, 0)
})

test('属性面板内打开的浮层交互不会被全局外部点击关闭', async (t) => {
  const VenueLayoutEditor = await loadEditor(t)
  const mounted = await mountEditor(t, VenueLayoutEditor)
  await selectElement(mounted, byClass(mounted.root, 'layout-element')[0])

  assert.ok(selectedNameInput(mounted.root))
  const floatingControl = new HostElement('div')
  floatingControl.props.class = 'el-popper'
  mounted.window.dispatch('pointerdown', { target: floatingControl })
  await settle()

  assert.ok(selectedNameInput(mounted.root))
})
