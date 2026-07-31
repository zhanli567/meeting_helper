import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc'
import * as Vue from 'vue'
import {
  createRenderer,
  defineComponent,
  h,
  inject,
  nextTick,
  provide,
  reactive,
} from 'vue'
import { createServer } from 'vite'

const frontendRoot = fileURLToPath(new URL('..', import.meta.url))
const checkboxGroupKey = Symbol('checkbox-group')
const tabsKey = Symbol('tabs')

async function loadDialog(t) {
  const server = await createServer({
    root: frontendRoot,
    appType: 'custom',
    server: { middlewareMode: true, hmr: false },
  })
  t.after(() => server.close())
  const componentPath = '/src/components/ExportOptionsDialog.vue'
  const component = (await server.ssrLoadModule(componentPath)).default
  const filename = fileURLToPath(new URL(`..${componentPath}`, import.meta.url))
  const source = await readFile(filename, 'utf8')
  const { descriptor } = parse(source, { filename })
  const script = compileScript(descriptor, { id: 'export-options-dialog-runtime' })
  const compiled = compileTemplate({
    source: descriptor.template.content,
    filename,
    id: 'export-options-dialog-runtime',
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
  return component
}

class HostElement {
  constructor(tag) {
    this.tag = tag
    this.props = {}
    this.children = []
    this.parent = undefined
  }
}

function createHostRenderer() {
  const insert = (child, parent, anchor) => {
    child.parent = parent
    const index = anchor ? parent.children.indexOf(anchor) : -1
    if (index < 0) {
      parent.children.push(child)
    } else {
      parent.children.splice(index, 0, child)
    }
  }
  return createRenderer({
    patchProp(element, key, _previous, next) {
      element.props[key] = next
    },
    insert,
    remove(child) {
      const index = child.parent?.children.indexOf(child) ?? -1
      if (index >= 0) {
        child.parent.children.splice(index, 1)
      }
      child.parent = undefined
    },
    createElement: (tag) => new HostElement(tag),
    createText: (text) => ({ tag: '#text', text, parent: undefined }),
    createComment: (text) => ({ tag: '#comment', text, parent: undefined }),
    setText(node, text) {
      node.text = text
    },
    setElementText(element, text) {
      element.children = [{ tag: '#text', text, parent: element }]
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

const DialogStub = defineComponent({
  inheritAttrs: false,
  setup(_props, { attrs, slots }) {
    return () => h('div', attrs, Object.values(slots).flatMap((slot) => slot()))
  },
})

const TabsStub = defineComponent({
  props: { modelValue: { type: String, default: '' } },
  emits: ['update:modelValue'],
  setup(props, { emit, slots }) {
    provide(tabsKey, {
      active: () => props.modelValue,
      activate: (name) => emit('update:modelValue', name),
    })
    return () => h('div', slots.default?.())
  },
})

const TabPaneStub = defineComponent({
  props: {
    label: { type: String, default: '' },
    name: { type: String, default: '' },
  },
  setup(props, { slots }) {
    const tabs = inject(tabsKey, null)
    return () => {
      if (tabs?.active() === props.name) {
        return h('section', { 'data-tab': props.name, 'data-label': props.label }, slots.default?.())
      }
      return h('button', { onClick: () => tabs?.activate(props.name) }, props.label)
    }
  },
})

const CollapseItemStub = defineComponent({
  props: { title: { type: String, default: '' } },
  setup(props, { slots }) {
    return () => h('section', { 'data-label': props.title }, slots.default?.())
  },
})

const FormItemStub = defineComponent({
  props: { label: { type: String, default: '' } },
  setup(props, { slots }) {
    return () => h('section', { 'data-label': props.label }, slots.default?.())
  },
})

const ButtonStub = defineComponent({
  inheritAttrs: false,
  setup(_props, { attrs, slots }) {
    return () => h('button', attrs, slots.default?.())
  },
})

const CheckboxGroupStub = defineComponent({
  props: { modelValue: { type: Array, default: () => [] } },
  emits: ['update:modelValue'],
  setup(props, { emit, slots }) {
    provide(checkboxGroupKey, {
      selected: (label) => props.modelValue.includes(label),
      toggle(label) {
        const next = props.modelValue.includes(label)
          ? props.modelValue.filter((value) => value !== label)
          : [...props.modelValue, label]
        emit('update:modelValue', next)
      },
    })
    return () => h('div', slots.default?.())
  },
})

const CheckboxStub = defineComponent({
  props: {
    modelValue: { type: Boolean, default: false },
    label: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  setup(props, { emit, slots }) {
    const group = inject(checkboxGroupKey, null)
    return () =>
      h(
        'button',
        {
          'aria-checked': group ? group.selected(props.label) : props.modelValue,
          onClick: () => {
            if (group) {
              group.toggle(props.label)
            } else {
              emit('update:modelValue', !props.modelValue)
            }
          },
        },
        slots.default?.(),
      )
  },
})

function allNodes(node) {
  return [node, ...(node.children || []).flatMap(allNodes)]
}

function nodeText(node) {
  if (node.text != null) {
    return String(node.text)
  }
  return (node.children || []).map(nodeText).join('')
}

function buttonsByText(root, text) {
  return allNodes(root).filter((node) => node.tag === 'button' && nodeText(node).trim() === text)
}

function summarySection(root, label) {
  const section = allNodes(root).find((node) => node.props?.['data-label'] === label)
  assert.ok(section, `missing summary section: ${label}`)
  return section
}

async function mountDialog(t, Dialog) {
  const renderer = createHostRenderer()
  const root = new HostElement('root')
  const state = reactive({ visible: false })
  const Wrapper = defineComponent({
    setup() {
      return () =>
        h(Dialog, {
          modelValue: state.visible,
          'onUpdate:modelValue': (value) => {
            state.visible = value
          },
          fieldDefinitions: [
            { code: 'department', label: '部门' },
            { code: 'batch', label: '批次' },
          ],
        })
    },
  })
  const app = renderer.createApp(Wrapper)
  app.config.warnHandler = () => {}
  app.provide(Vue.ssrContextKey, { modules: new Set() })
  app.component('ElDialog', DialogStub)
  app.component('ElTabs', TabsStub)
  app.component('ElTabPane', TabPaneStub)
  app.component('ElCollapse', Passthrough)
  app.component('ElCollapseItem', CollapseItemStub)
  app.component('ElForm', Passthrough)
  app.component('ElFormItem', FormItemStub)
  app.component('ElCheckbox', CheckboxStub)
  app.component('ElCheckboxGroup', CheckboxGroupStub)
  app.component('ElTag', Passthrough)
  app.component('ElButton', ButtonStub)
  app.mount(root)
  t.after(() => app.unmount())
  state.visible = true
  await nextTick()
  return root
}

test('confirmation summarizes enabled sheets, color fields, and static choices', async (t) => {
  const Dialog = await loadDialog(t)
  const root = await mountDialog(t, Dialog)

  buttonsByText(root, '人员名单')[0].props.onClick()
  buttonsByText(root, '排座图')[0].props.onClick()
  buttonsByText(root, '下一步')[0].props.onClick()
  await nextTick()

  buttonsByText(root, '出席情况')[0].props.onClick()
  buttonsByText(root, '下一步')[0].props.onClick()
  await nextTick()

  buttonsByText(root, '部门')[0].props.onClick()
  await nextTick()
  buttonsByText(root, '部门')[1].props.onClick()
  buttonsByText(root, '下一步')[0].props.onClick()
  await nextTick()

  const sheetSummary = nodeText(summarySection(root, '导出子表'))
  const participantSummary = nodeText(summarySection(root, '人员名单配置'))
  const layoutSummary = nodeText(summarySection(root, '排座图配置'))
  const summaryLabels = allNodes(root)
    .map((node) => node.props?.['data-label'])
    .filter(Boolean)

  assert.match(sheetSummary, /人员名单/)
  assert.match(sheetSummary, /排座图/)
  assert.doesNotMatch(sheetSummary, /座位明细/)
  assert.match(participantSummary, /出席情况\s*不包含/)
  assert.match(participantSummary, /座位编号\s*包含/)
  assert.match(layoutSummary, /排座字段\s*部门/)
  assert.match(layoutSummary, /着色字段\s*部门/)
  assert.ok(!summaryLabels.includes('座位明细配置'))
})

test('only selected sheets appear as configuration tabs', async (t) => {
  const Dialog = await loadDialog(t)
  const root = await mountDialog(t, Dialog)

  buttonsByText(root, '排座图')[0].props.onClick()
  await nextTick()

  assert.equal(buttonsByText(root, '人员名单配置').length, 0)
  assert.equal(buttonsByText(root, '座位明细配置').length, 0)
  assert.equal(buttonsByText(root, '排座图配置').length, 1)

  buttonsByText(root, '下一步')[0].props.onClick()
  await nextTick()

  assert.equal(summarySection(root, '排座图配置').props['data-tab'], 'layout')
  assert.doesNotMatch(nodeText(root), /固定字段/)

  buttonsByText(root, '下一步')[0].props.onClick()
  await nextTick()

  const summaryLabels = allNodes(root)
    .map((node) => node.props?.['data-label'])
    .filter(Boolean)

  assert.ok(summaryLabels.includes('排座图配置'))
  assert.ok(!summaryLabels.includes('人员名单配置'))
  assert.ok(!summaryLabels.includes('座位明细配置'))
})

test('sheet choices start empty and configuration tabs do not appear before selection', async (t) => {
  const Dialog = await loadDialog(t)
  const root = await mountDialog(t, Dialog)
  const checked = allNodes(root).filter((node) => node.props?.['aria-checked'] === true)

  assert.equal(checked.length, 0)
  assert.equal(buttonsByText(root, '人员名单配置').length, 0)
  assert.equal(buttonsByText(root, '排座图配置').length, 0)
  assert.equal(buttonsByText(root, '座位明细配置').length, 0)
  assert.match(nodeText(root), /人员名单/)
  assert.match(nodeText(root), /排座图/)
  assert.match(nodeText(root), /座位明细/)
  assert.match(nodeText(root), /确认导出/)
})
