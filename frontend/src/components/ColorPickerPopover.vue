<script setup>
import { computed, ref, watch } from 'vue'
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
})

const emit = defineEmits(['update:modelValue', 'change'])

const open = ref(false)
const customPreview = ref(normalizeHexColor(props.modelValue) || '#ffffff')
const swatches = ref(availableColorSwatches())

const currentColor = computed(() => normalizeHexColor(props.modelValue) || '#ffffff')
const currentTextColor = computed(() => textColorForBackground(currentColor.value))

watch(
  () => props.modelValue,
  (value) => {
    customPreview.value = normalizeHexColor(value) || '#ffffff'
  },
)

function refreshColors() {
  swatches.value = availableColorSwatches()
}

function toggleOpen() {
  if (props.disabled) return
  refreshColors()
  open.value = !open.value
}

function chooseColor(value) {
  const color = normalizeHexColor(value)
  if (!color) return
  saveCustomColor(color)
  refreshColors()
  emit('update:modelValue', color)
  emit('change', color)
  open.value = false
}

function confirmCustomColor() {
  const color = saveCustomColor(customPreview.value)
  if (!color) return
  refreshColors()
  emit('update:modelValue', color)
  emit('change', color)
  open.value = false
}

function removeColor(swatch) {
  removeCustomColor(swatch.value)
  refreshColors()
}
</script>

<template>
  <div class="color-picker-popover" @pointerdown.stop>
    <button
      type="button"
      class="current-color-button"
      :disabled="disabled"
      :aria-label="`${label}：${currentColor}`"
      :title="`${label}：${currentColor}`"
      @click="toggleOpen"
    >
      <span
        class="current-color-dot"
        :style="{ backgroundColor: currentColor, color: currentTextColor }"
      />
    </button>

    <div v-show="open" class="color-popover-panel">
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
            :class="{ active: currentColor === swatch.value }"
            :title="swatch.title || swatch.name"
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
            @input="customPreview = $event.target.value"
          >
        </label>
        <span
          class="custom-preview-dot"
          :style="{ backgroundColor: customPreview }"
          :title="customPreview"
        />
        <button type="button" class="custom-confirm-button" @click="confirmCustomColor">
          确定
        </button>
      </div>
    </div>
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
  background: #fff;
  border: 1px solid #d5dfec;
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
  border: 1px solid rgba(15, 23, 42, 0.14);
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.46);
}

.color-popover-panel {
  width: 216px;
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 100;
  display: grid;
  gap: 10px;
  padding: 10px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: var(--shadow-hover);
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
  padding: 2px 2px 2px 0;
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
  border: 1px solid rgba(100, 116, 139, 0.28);
  border-radius: 7px;
}

.color-swatch-button {
  cursor: pointer;
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
