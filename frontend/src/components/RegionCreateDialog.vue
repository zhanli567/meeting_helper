<script setup>
import { reactive, ref, watch } from 'vue'
import { Close, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  availableColorSwatches,
  normalizeHexColor,
  removeCustomColor,
  saveCustomColor,
  textColorForBackground,
} from '@/utils/venuePreferences'

const defaultColor = '#fef3c7'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  selectedSeatCount: { type: Number, default: 0 },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'submit', 'cancel'])

const colorSwatches = ref(availableColorSwatches('fillColor'))
const form = reactive({
  label: '',
  backgroundColor: defaultColor,
  textColor: textColorForBackground(defaultColor),
  bold: true,
})

function resetForm() {
  form.label = ''
  form.backgroundColor = defaultColor
  form.textColor = textColorForBackground(defaultColor)
  form.bold = true
  refreshColors()
}

function refreshColors() {
  colorSwatches.value = availableColorSwatches('fillColor')
}

function patchColor(value) {
  const color = normalizeHexColor(value)
  if (!color) return
  form.backgroundColor = color
  form.textColor = textColorForBackground(color)
}

function selectSwatch(swatch) {
  patchColor(swatch.value)
}

function previewCustomColor(value) {
  patchColor(value)
}

function confirmCustomColor(value) {
  const color = saveCustomColor('fillColor', value)
  if (!color) return
  refreshColors()
  patchColor(color)
}

function removeColor(swatch) {
  const removedColor = normalizeHexColor(swatch.value)
  removeCustomColor('fillColor', removedColor)
  refreshColors()
  if (removedColor && normalizeHexColor(form.backgroundColor) === removedColor) {
    patchColor(defaultColor)
  }
}

function closeDialog() {
  emit('cancel')
  emit('update:modelValue', false)
}

function handleVisibleChange(value) {
  if (value) {
    emit('update:modelValue', true)
    return
  }
  closeDialog()
}

function submit() {
  const label = form.label.trim()
  if (!label) {
    ElMessage.warning('请填写区域名称')
    return
  }
  if (!props.selectedSeatCount) {
    ElMessage.warning('请先框选座位')
    return
  }
  emit('submit', {
    label,
    backgroundColor: form.backgroundColor,
    textColor: form.textColor,
    bold: form.bold,
  })
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) resetForm()
  },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="创建区域"
    width="420px"
    class="region-create-dialog"
    :close-on-click-modal="false"
    @update:model-value="handleVisibleChange"
  >
    <div class="region-create-summary">
      <span>已选座位</span>
      <b>{{ selectedSeatCount }}</b>
    </div>

    <el-form label-position="top" class="region-create-form">
      <el-form-item label="区域名称" required>
        <el-input
          v-model="form.label"
          maxlength="20"
          show-word-limit
          clearable
          aria-label="区域名称"
        />
      </el-form-item>

      <el-form-item label="区域颜色">
        <div class="swatch-row">
          <button
            v-for="swatch in colorSwatches"
            :key="swatch.value"
            type="button"
            class="region-swatch"
            :class="{ active: form.backgroundColor === swatch.value }"
            :title="swatch.title || swatch.name"
            :style="{ backgroundColor: swatch.value }"
            @click="selectSwatch(swatch)"
          >
            <el-icon
              v-if="swatch.custom"
              class="swatch-delete"
              @click.stop="removeColor(swatch)"
            >
              <Close />
            </el-icon>
          </button>
          <label class="region-swatch color-add" title="添加自定义颜色">
            <Plus />
            <input
              type="color"
              :value="form.backgroundColor"
              aria-label="添加区域颜色"
              @input="previewCustomColor($event.target.value)"
              @change="confirmCustomColor($event.target.value)"
            >
          </label>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="closeDialog">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">创建区域</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.region-create-summary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding: 7px 10px;
  color: var(--muted);
  background: #f8fafc;
  border: 1px solid var(--line);
  border-radius: 8px;
  font-size: 12px;
}

.region-create-summary b {
  color: var(--ink);
  font-size: 17px;
}

.region-create-form {
  display: grid;
  gap: 4px;
}

.swatch-row {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
}

.region-swatch {
  width: 30px;
  height: 30px;
  position: relative;
  display: grid;
  place-items: center;
  padding: 0;
  color: #64748b;
  background: #fff;
  border: 1px solid rgba(100, 116, 139, 0.26);
  border-radius: 8px;
  cursor: pointer;
}

.region-swatch.active {
  box-shadow:
    0 0 0 2px #fff,
    0 0 0 4px var(--brand);
}

.color-add {
  overflow: hidden;
}

.color-add input {
  width: 100%;
  height: 100%;
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.swatch-delete {
  width: 16px;
  height: 16px;
  position: absolute;
  top: -7px;
  right: -7px;
  display: grid;
  place-items: center;
  color: #64748b;
  background: #fff;
  border: 1px solid #d6e2f3;
  border-radius: 50%;
  font-size: 10px;
}
</style>
