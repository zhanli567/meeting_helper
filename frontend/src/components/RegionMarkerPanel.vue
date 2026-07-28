<script setup>
import { computed, ref } from 'vue'
import { Close, Plus } from '@element-plus/icons-vue'
import {
  availableColorSwatches,
  normalizeHexColor,
  removeCustomColor,
  saveCustomColor,
  textColorForBackground,
} from '@/utils/venuePreferences'

const colorSwatches = ref(availableColorSwatches('fillColor'))

const props = defineProps({
  modelValue: {
    type: Object,
    default: () => ({
      id: '',
      label: '',
      backgroundColor: '#FEF3C7',
      textColor: '#7C2D12',
      bold: true,
    }),
  },
  markers: { type: Array, default: () => [] },
  activeMarkerId: { type: String, default: '' },
  selectedSeatCount: { type: Number, default: 0 },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'select', 'new', 'save', 'delete', 'cancel'])

const isEditing = computed(() => Boolean(props.modelValue?.id))

function patchMarker(value) {
  emit('update:modelValue', {
    ...(props.modelValue || {}),
    ...value,
  })
}

function refreshColors() {
  colorSwatches.value = availableColorSwatches('fillColor')
}

function selectSwatch(swatch) {
  patchMarker({
    backgroundColor: swatch.value,
    textColor: textColorForBackground(swatch.value),
    bold: true,
  })
}

function previewCustomColor(value) {
  const color = normalizeHexColor(value)
  if (!color) return
  patchMarker({
    backgroundColor: color,
    textColor: textColorForBackground(color),
    bold: true,
  })
}

function confirmCustomColor(value) {
  const color = saveCustomColor('fillColor', value)
  if (!color) return
  refreshColors()
  selectSwatch({ value: color })
}

function removeColor(color) {
  removeCustomColor('fillColor', color.value)
  refreshColors()
}
</script>

<template>
  <aside class="region-marker-panel">
    <header class="panel-header">
      <div>
        <h2>{{ isEditing ? '编辑区域' : '新建区域' }}</h2>
        <p>已选座位 {{ selectedSeatCount }}</p>
      </div>
      <el-button text @click="emit('cancel')">重置</el-button>
    </header>

    <section class="marker-list-section">
      <div class="section-title">
        <span>已有区域</span>
        <el-button link size="small" @click="emit('new')">新建区域</el-button>
      </div>
      <div v-if="markers.length" class="marker-list">
        <button
          v-for="marker in markers"
          :key="marker.id"
          type="button"
          class="marker-list-item"
          :class="{ active: marker.id === activeMarkerId }"
          @click="emit('select', marker)"
        >
          <i :style="{ backgroundColor: marker.backgroundColor || '#FEF3C7' }" />
          <span>{{ marker.label || '未命名区域' }}</span>
          <small>{{ (marker.targetElementIds || []).length }} 座</small>
        </button>
      </div>
      <p v-else class="empty-markers">暂无区域</p>
    </section>

    <el-form label-position="top" class="marker-form">
      <el-form-item label="区域名称" required>
        <el-input
          :model-value="modelValue.label"
          maxlength="20"
          show-word-limit
          placeholder="例如：嘉宾"
          @update:model-value="patchMarker({ label: $event })"
        />
      </el-form-item>

      <el-form-item label="区域颜色">
        <div class="swatch-row">
          <button
            v-for="swatch in colorSwatches"
            :key="swatch.value"
            type="button"
            class="marker-swatch"
            :class="{ active: modelValue.backgroundColor === swatch.value }"
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
          <label class="marker-swatch color-add" title="添加自定义颜色">
            <Plus />
            <input
              type="color"
              :value="modelValue.backgroundColor"
              aria-label="添加区域颜色"
              @input="previewCustomColor($event.target.value)"
              @change="confirmCustomColor($event.target.value)"
            >
          </label>
        </div>
      </el-form-item>

    </el-form>

    <div class="panel-actions">
      <el-button
        class="delete-button"
        :disabled="!isEditing || submitting"
        @click="emit('delete')"
      >
        删除区域
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        @click="emit('save')"
      >
        保存区域
      </el-button>
    </div>
  </aside>
</template>

<style scoped>
.region-marker-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 18px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow);
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
}

.panel-header h2 {
  margin: 0;
  color: var(--ink);
  font-size: 18px;
}

.panel-header p {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.marker-list-section {
  padding: 14px 0 4px;
  border-bottom: 1px solid var(--line);
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 650;
}

.marker-list {
  max-height: 156px;
  display: grid;
  gap: 8px;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 2px;
}

.marker-list-item {
  width: 100%;
  min-width: 0;
  display: grid;
  grid-template-columns: 16px 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  color: var(--ink);
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
}

.marker-list-item.active {
  background: #eef5ff;
  border-color: rgba(10, 89, 247, 0.42);
  box-shadow: inset 0 0 0 1px rgba(10, 89, 247, 0.18);
}

.marker-list-item i {
  width: 16px;
  height: 16px;
  border: 1px solid rgba(15, 23, 42, 0.14);
  border-radius: 5px;
}

.marker-list-item span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.marker-list-item small {
  color: var(--muted);
  font-size: 12px;
}

.empty-markers {
  margin: 0 0 10px;
  color: var(--muted);
  font-size: 12px;
}

.marker-form {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding-top: 16px;
  padding-right: 2px;
}

.swatch-row {
  max-height: min(180px, 28vh);
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 2px 2px 2px 0;
}

.marker-swatch {
  width: 30px;
  height: 30px;
  position: relative;
  display: grid;
  place-items: center;
  padding: 0;
  color: #64748b;
  border: 1px solid rgba(100, 116, 139, 0.26);
  border-radius: 8px;
  cursor: pointer;
}

.marker-swatch.active {
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

.panel-actions {
  display: flex;
  gap: 10px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.panel-actions .el-button {
  flex: 1;
}

.delete-button:not(:disabled) {
  color: #dc2626;
}
</style>
