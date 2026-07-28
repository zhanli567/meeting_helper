<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Close, DArrowLeft, DArrowRight, Fold, Plus, Setting } from '@element-plus/icons-vue'
import { ELEMENT_KINDS } from '@/utils/venueModel'
import { validElementProperties } from '@/utils/designerGeometry'
import {
  availableColorSwatches,
  availableElementSuggestions,
  normalizeHexColor,
  removeCustomColor,
  saveCustomColor,
  saveCustomElementName,
} from '@/utils/venuePreferences'

const props = defineProps({
  element: {
    type: Object,
    default: undefined,
  },
  venueName: {
    type: String,
    default: '场馆模板',
  },
  venueDescription: {
    type: String,
    default: '',
  },
  gridRows: {
    type: Number,
    required: true,
  },
  gridColumns: {
    type: Number,
    required: true,
  },
  collapsed: {
    type: Boolean,
    default: false,
  },
  dock: {
    type: String,
    default: 'right',
  },
})

const emit = defineEmits(['preview', 'confirm', 'cancel', 'toggle', 'dock'])
const draft = reactive({
  kind: ELEMENT_KINDS.GENERIC,
  name: '',
  fillColor: '#dbeafe',
  borderColor: '#93c5fd',
})
const elementSuggestions = ref(availableElementSuggestions())
const fillColorSwatches = ref(availableColorSwatches('fillColor'))
const borderColorSwatches = ref(availableColorSwatches('borderColor'))
const draftValid = computed(() => validElementProperties(draft))

watch(
  () => props.element,
  (element) => {
    if (!element) return
    Object.assign(draft, {
      kind: element.kind,
      name: element.name,
      fillColor: element.fillColor,
      borderColor: element.borderColor,
    })
  },
  { immediate: true },
)

watch(
  draft,
  (value) => {
    if (
      props.element &&
      value.kind &&
      value.name != null &&
      value.fillColor &&
      value.borderColor
    ) {
      emit('preview', {
        kind: value.kind,
        name: value.name,
        fillColor: value.fillColor,
        borderColor: value.borderColor,
      })
    }
  },
  { deep: true },
)

function refreshPreferences() {
  elementSuggestions.value = availableElementSuggestions()
  fillColorSwatches.value = availableColorSwatches('fillColor')
  borderColorSwatches.value = availableColorSwatches('borderColor')
}

function queryNames(query, callback) {
  const keyword = String(query || '').trim().toLowerCase()
  callback(
    elementSuggestions.value.filter(
      (suggestion) =>
        !keyword || suggestion.name.toLowerCase().includes(keyword),
    ).map((suggestion) => ({ value: suggestion.name })),
  )
}

function chooseColor(field, color) {
  draft[field] = color.value
}

function previewCustomColor(field, value) {
  const color = normalizeHexColor(value)
  if (color) draft[field] = color
}

function confirmCustomColor(field, value) {
  const color = saveCustomColor(field, value)
  if (color) {
    refreshPreferences()
    draft[field] = color
  }
}

function removeColor(field, color) {
  removeCustomColor(field, color.value)
  refreshPreferences()
}

function confirm() {
  if (!draftValid.value) return
  if (draft.kind === ELEMENT_KINDS.GENERIC) {
    saveCustomElementName(draft.name)
  }
  saveCustomColor('fillColor', draft.fillColor)
  saveCustomColor('borderColor', draft.borderColor)
  refreshPreferences()
  emit('confirm', {
    kind: draft.kind,
    name: draft.name.trim(),
    fillColor: draft.fillColor,
    borderColor: draft.borderColor,
  })
}
</script>

<template>
  <aside class="venue-element-panel" :class="{ collapsed }" @pointerdown.stop>
    <header>
      <div v-if="!collapsed">
        <el-icon><Setting /></el-icon>
        <strong>布局信息</strong>
      </div>
      <el-button
        text
        circle
        :icon="Fold"
        :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
        @click="emit('toggle')"
      />
    </header>

    <template v-if="!collapsed">
      <section class="venue-summary">
        <strong>{{ venueName }}</strong>
        <span>{{ gridRows }} × {{ gridColumns }}</span>
        <p v-if="venueDescription">{{ venueDescription }}</p>
      </section>

      <section v-if="element" class="element-editor">
        <div class="section-title">
          <strong>元素属性</strong>
          <el-button text circle :icon="Close" aria-label="关闭元素属性" @click="emit('cancel')" />
        </div>

        <el-form label-position="top" size="small">
          <el-form-item label="显示名称">
            <el-autocomplete
              v-model="draft.name"
              :fetch-suggestions="queryNames"
              maxlength="80"
              clearable
              placeholder="选择常用名称或直接输入"
            />
          </el-form-item>

          <el-form-item label="填充色">
            <div class="color-row">
              <span
                v-for="color in fillColorSwatches"
                :key="`fill-${color.value}`"
                class="color-swatch"
                :class="{ active: draft.fillColor === color.value, custom: color.custom }"
                :title="color.title"
              >
                <button
                  type="button"
                  :style="{ backgroundColor: color.value }"
                  :aria-label="`填充色 ${color.value}`"
                  @click="chooseColor('fillColor', color)"
                />
                <button
                  v-if="color.custom"
                  type="button"
                  class="color-delete"
                  :aria-label="`删除自定义颜色 ${color.value}`"
                  @click.stop="removeColor('fillColor', color)"
                >
                  <Close />
                </button>
              </span>
              <label class="color-add" title="添加自定义颜色">
                <input
                  type="color"
                  :value="draft.fillColor"
                  aria-label="添加填充色"
                  @input="previewCustomColor('fillColor', $event.target.value)"
                  @change="confirmCustomColor('fillColor', $event.target.value)"
                />
                <Plus />
              </label>
            </div>
          </el-form-item>

          <el-form-item label="边框色">
            <div class="color-row">
              <span
                v-for="color in borderColorSwatches"
                :key="`border-${color.value}`"
                class="color-swatch"
                :class="{ active: draft.borderColor === color.value, custom: color.custom }"
                :title="color.title"
              >
                <button
                  type="button"
                  :style="{ backgroundColor: color.value }"
                  :aria-label="`边框色 ${color.value}`"
                  @click="chooseColor('borderColor', color)"
                />
                <button
                  v-if="color.custom"
                  type="button"
                  class="color-delete"
                  :aria-label="`删除自定义颜色 ${color.value}`"
                  @click.stop="removeColor('borderColor', color)"
                >
                  <Close />
                </button>
              </span>
              <label class="color-add" title="添加自定义颜色">
                <input
                  type="color"
                  :value="draft.borderColor"
                  aria-label="添加边框色"
                  @input="previewCustomColor('borderColor', $event.target.value)"
                  @change="confirmCustomColor('borderColor', $event.target.value)"
                />
                <Plus />
              </label>
            </div>
          </el-form-item>
        </el-form>

        <footer>
          <el-button @click="emit('cancel')">取消</el-button>
          <el-button type="primary" :disabled="!draftValid" @click="confirm">
            确认
          </el-button>
        </footer>
      </section>

      <div class="dock-actions">
        <el-button
          text
          :icon="DArrowLeft"
          :disabled="dock === 'left'"
          @click="emit('dock', 'left')"
        >
          停靠左侧
        </el-button>
        <el-button
          text
          :icon="DArrowRight"
          :disabled="dock === 'right'"
          @click="emit('dock', 'right')"
        >
          停靠右侧
        </el-button>
      </div>
    </template>
  </aside>
</template>

<style scoped>
.venue-element-panel {
  width: 310px;
  min-width: 310px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border-left: 1px solid #dbe4f0;
  border-right: 1px solid #dbe4f0;
}

.venue-element-panel.collapsed {
  width: 52px;
  min-width: 52px;
}

header {
  min-height: 52px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 10px 0 14px;
  background: #f8fbff;
  border-bottom: 1px solid #e2e9f2;
}

header > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.collapsed header {
  justify-content: center;
  padding: 0;
}

.venue-summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 5px 10px;
  padding: 14px;
  border-bottom: 1px solid #e5ebf3;
}

.venue-summary strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.venue-summary span {
  color: var(--brand);
  font-weight: 700;
}

.venue-summary p {
  grid-column: 1 / -1;
  margin: 0;
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.element-editor {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.element-editor :deep(.el-select),
.element-editor :deep(.el-autocomplete) {
  width: 100%;
}

.color-row {
  min-height: 32px;
  max-height: 92px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 2px 2px 2px 0;
}

.color-swatch {
  width: 25px;
  height: 25px;
  position: relative;
  display: inline-flex;
  flex: none;
}

.color-swatch > button:first-child,
.color-add {
  width: 25px;
  height: 25px;
  display: grid;
  place-items: center;
  padding: 0;
  background: #fff;
  border: 1px solid #aebed2;
  border-radius: 6px;
  cursor: pointer;
}

.color-swatch.active > button:first-child {
  box-shadow:
    0 0 0 2px #fff,
    0 0 0 4px var(--brand);
}

.color-delete {
  width: 15px;
  height: 15px;
  position: absolute;
  top: -6px;
  right: -6px;
  display: grid;
  place-items: center;
  padding: 0;
  color: #9aa8ba;
  background: #fff;
  border: 1px solid #d7e1ee;
  border-radius: 50%;
  cursor: pointer;
}

.color-delete:hover {
  color: var(--danger);
  border-color: #fecaca;
}

.color-delete svg,
.color-add svg {
  width: 11px;
  height: 11px;
}

.color-add {
  position: relative;
  color: var(--brand);
  overflow: hidden;
}

.color-add input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid #e5ebf3;
}

.dock-actions {
  display: flex;
  justify-content: center;
  margin-top: auto;
  padding: 8px;
  border-top: 1px solid #e5ebf3;
}

.dock-actions :deep(.el-button) {
  margin: 0;
  font-size: 11px;
}
</style>
