<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Close, DArrowLeft, DArrowRight, Fold, Setting } from '@element-plus/icons-vue'
import ColorPickerPopover from '@/components/ColorPickerPopover.vue'
import { ELEMENT_KINDS } from '@/utils/venueModel'
import { validElementProperties } from '@/utils/designerGeometry'
import {
  availableElementSuggestions,
  BASIC_COLOR_SWATCHES,
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
const basicColorSwatches = BASIC_COLOR_SWATCHES
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

function confirm() {
  if (!draftValid.value) return
  if (draft.kind === ELEMENT_KINDS.GENERIC) {
    saveCustomElementName(draft.name)
  }
  saveCustomColor(draft.fillColor)
  saveCustomColor(draft.borderColor)
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
              aria-label="显示名称"
            />
          </el-form-item>

          <el-form-item label="填充色">
            <div class="color-control-row">
              <span>{{ draft.fillColor }}</span>
              <ColorPickerPopover v-model="draft.fillColor" label="填充色" />
              <input
                class="sr-only-color-value"
                :value="draft.fillColor"
                readonly
                aria-hidden="true"
                tabindex="-1"
              >
              <button
                v-for="color in basicColorSwatches"
                :key="`fill-${color.value}`"
                type="button"
                class="sr-only-color-action"
                :aria-label="`填充色 ${color.value}`"
                @click="draft.fillColor = color.value"
              />
            </div>
          </el-form-item>

          <el-form-item label="边框色">
            <div class="color-control-row">
              <span>{{ draft.borderColor }}</span>
              <ColorPickerPopover v-model="draft.borderColor" label="边框色" />
              <input
                class="sr-only-color-value"
                :value="draft.borderColor"
                readonly
                aria-hidden="true"
                tabindex="-1"
              >
              <button
                v-for="color in basicColorSwatches"
                :key="`border-${color.value}`"
                type="button"
                class="sr-only-color-action"
                :aria-label="`边框色 ${color.value}`"
                @click="draft.borderColor = color.value"
              />
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
  overflow-x: hidden;
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

.color-control-row {
  min-height: 32px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  color: var(--muted);
  font-size: 12px;
}

.sr-only-color-action {
  width: 1px;
  height: 1px;
  position: absolute;
  left: 0;
  bottom: 0;
  padding: 0;
  opacity: 0;
  pointer-events: none;
}

.sr-only-color-value {
  width: 1px;
  height: 1px;
  position: absolute;
  left: 0;
  bottom: 0;
  opacity: 0;
  pointer-events: none;
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
