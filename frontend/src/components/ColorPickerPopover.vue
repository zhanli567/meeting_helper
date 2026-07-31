<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Close, Plus } from '@element-plus/icons-vue'
import {
  availableColorSwatches,
  normalizeHexColor,
  removeCustomColor,
  saveCustomColor,
  textColorForBackground,
} from '@/utils/venuePreferences'

const props = defineProps({
  modelValue: { type: String, default: '#ffffff' },
  label: { type: String, default: '颜色' },
  disabled: { type: Boolean, default: false },
  unavailableColors: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue', 'change'])

const open = ref(false)
const pendingColor = ref(normalizeHexColor(props.modelValue) || '#ffffff')
const customPreview = ref(pendingColor.value)
const swatches = ref(availableColorSwatches())

const currentColor = computed(() => normalizeHexColor(props.modelValue) || '#ffffff')
const currentTextColor = computed(() => textColorForBackground(currentColor.value))
const unavailableColorSet = computed(
  () => new Set(
    (props.unavailableColors || [])
      .map((value) => normalizeHexColor(value))
      .filter((value) => value && value !== currentColor.value),
  ),
)

watch(
  () => props.modelValue,
  (value) => {
    const color = normalizeHexColor(value) || '#ffffff'
    pendingColor.value = color
    customPreview.value = color
  },
)

watch(open, (visible) => {
  if (!visible) {
    return
  }
  refreshColors()
  pendingColor.value = currentColor.value
  customPreview.value = currentColor.value
})

function refreshColors() {
  swatches.value = availableColorSwatches()
}

function toggleOpen() {
  if (props.disabled) {
    return
  }
  refreshColors()
}

function chooseColor(value) {
  const color = normalizeHexColor(value)
  if (!color || isColorUnavailable(color)) {
    return
  }
  pendingColor.value = color
  customPreview.value = color
}

function updateCustomPreview(value) {
  const color = normalizeHexColor(value)
  if (!color) {
    return
  }
  customPreview.value = color
  pendingColor.value = color
}

function confirmSelectedColor() {
  const color = normalizeHexColor(pendingColor.value)
  if (!color || isColorUnavailable(color)) {
    return
  }
  saveCustomColor(pendingColor.value)
  refreshColors()
  emit('update:modelValue', color)
  emit('change', color)
  open.value = false
}

function removeColor(swatch) {
  removeCustomColor(swatch.value)
  if (pendingColor.value === swatch.value) {
    pendingColor.value = currentColor.value
    customPreview.value = currentColor.value
  }
  refreshColors()
}

function isColorUnavailable(value) {
  const color = normalizeHexColor(value)
  return Boolean(color && unavailableColorSet.value.has(color))
}

function closeColorPopover() {
  open.value = false
}

onMounted(() => {
  window.addEventListener('meeting-helper:close-color-popovers', closeColorPopover)
})

onBeforeUnmount(() => {
  window.removeEventListener('meeting-helper:close-color-popovers', closeColorPopover)
})
</script>

<template>
  <div class="color-picker-popover" @pointerdown.stop>
    <el-popover
      v-model:visible="open"
      trigger="click"
      placement="bottom-end"
      :width="236"
      :disabled="disabled"
      :teleported="true"
      popper-class="color-picker-popper"
    >
      <template #reference>
        <button
          type="button"
          class="current-color-button"
          :disabled="disabled"
          :aria-label="`${label}：${currentColor}`"
          :title="currentColor"
          @click="toggleOpen"
        >
          <span
            class="current-color-dot"
            :style="{ backgroundColor: currentColor, color: currentTextColor }"
          />
        </button>
      </template>

      <div class="color-popover-panel" @pointerdown.stop>
        <div class="swatch-grid">
          <span
            v-for="swatch in swatches"
            :key="swatch.value"
            class="swatch-item"
            :class="{ custom: swatch.custom }"
          >
            <button
              type="button"
              class="color-swatch-button"
              :class="{ active: pendingColor === swatch.value, unavailable: isColorUnavailable(swatch.value) }"
              :disabled="isColorUnavailable(swatch.value)"
              :title="swatch.value"
              :aria-label="`${label} ${swatch.value}`"
              :style="{ backgroundColor: swatch.value }"
              @click="chooseColor(swatch.value)"
            />
            <button
              v-if="swatch.custom"
              type="button"
              class="swatch-remove-button"
              :aria-label="`删除自定义颜色 ${swatch.value}`"
              @click.stop="removeColor(swatch)"
            >
              <Close />
            </button>
          </span>
        </div>

        <div class="custom-color-row">
          <label class="custom-color-preview" title="选择自定义颜色">
            <Plus />
            <input
              type="color"
              :value="customPreview"
              :aria-label="`选择${label}`"
              @input="updateCustomPreview($event.target.value)"
            >
          </label>
          <span
            class="custom-preview-dot"
            :style="{ backgroundColor: pendingColor }"
            :title="pendingColor"
          />
          <button type="button" class="custom-confirm-button" @click="confirmSelectedColor">
            确定
          </button>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<style scoped>
.color-picker-popover {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.current-color-button {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
}

.current-color-button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.current-color-dot {
  width: 20px;
  height: 20px;
  display: block;
  border: 0;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.46);
}

.color-popover-panel {
  display: grid;
  gap: 10px;
  background: #fff;
}

.swatch-grid {
  min-height: 30px;
  max-height: 104px;
  display: flex;
  flex-wrap: wrap;
  align-content: start;
  gap: 8px;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 5px 4px 4px;
}

.swatch-item {
  width: 26px;
  height: 26px;
  position: relative;
  flex: none;
}

.color-swatch-button,
.custom-color-preview,
.custom-preview-dot {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  padding: 0;
  background: #fff;
  border: 0;
  border-radius: 7px;
}

.color-swatch-button {
  cursor: pointer;
}

.color-swatch-button:disabled {
  cursor: not-allowed;
  opacity: 0.38;
}

.color-swatch-button.active {
  box-shadow:
    0 0 0 2px #fff,
    0 0 0 4px var(--brand);
}

.swatch-remove-button {
  width: 16px;
  height: 16px;
  position: absolute;
  top: -7px;
  right: -7px;
  display: grid;
  place-items: center;
  padding: 0;
  color: #64748b;
  background: #fff;
  border: 1px solid #d6e2f3;
  border-radius: 50%;
  cursor: pointer;
}

.swatch-remove-button:hover {
  color: var(--danger);
  border-color: #fecaca;
}

.custom-color-row {
  display: grid;
  grid-template-columns: 26px 26px 1fr;
  align-items: center;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid #eef2f7;
}

.custom-color-preview {
  position: relative;
  color: var(--brand);
  cursor: pointer;
  overflow: hidden;
}

.custom-color-preview input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.custom-confirm-button {
  height: 28px;
  color: #fff;
  background: var(--brand);
  border: 0;
  border-radius: 7px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}

.custom-confirm-button:hover {
  background: var(--brand-hover);
}

.custom-color-preview svg,
.swatch-remove-button svg {
  width: 12px;
  height: 12px;
}
</style>
