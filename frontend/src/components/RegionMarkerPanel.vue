<script setup>
import { computed } from 'vue'

const markerSwatches = [
  { name: '浅黄', value: '#FEF3C7', textColor: '#7C2D12' },
  { name: '浅蓝', value: '#DBEAFE', textColor: '#1D4ED8' },
  { name: '浅绿', value: '#DCFCE7', textColor: '#166534' },
  { name: '浅粉', value: '#FCE7F3', textColor: '#BE185D' },
  { name: '浅紫', value: '#EDE9FE', textColor: '#6D28D9' },
  { name: '浅橙', value: '#FFEDD5', textColor: '#C2410C' },
  { name: '浅青', value: '#CCFBF1', textColor: '#0F766E' },
]

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

function selectSwatch(swatch) {
  patchMarker({
    backgroundColor: swatch.value,
    textColor: swatch.textColor,
    bold: true,
  })
}
</script>

<template>
  <aside class="region-marker-panel">
    <header class="panel-header">
      <div>
        <h2>{{ isEditing ? '编辑区域标记' : '新建区域标记' }}</h2>
        <p>已选座位 {{ selectedSeatCount }}</p>
      </div>
      <el-button text @click="emit('cancel')">重置</el-button>
    </header>

    <section class="marker-list-section">
      <div class="section-title">
        <span>已有标记</span>
        <el-button link size="small" @click="emit('new')">新建标记</el-button>
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
          <span>{{ marker.label || '未命名标记' }}</span>
          <small>{{ (marker.targetElementIds || []).length }} 座</small>
        </button>
      </div>
      <p v-else class="empty-markers">暂无区域标记</p>
    </section>

    <el-form label-position="top" class="marker-form">
      <el-form-item label="标记名称" required>
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
            v-for="swatch in markerSwatches"
            :key="swatch.value"
            type="button"
            class="marker-swatch"
            :class="{ active: modelValue.backgroundColor === swatch.value }"
            :title="swatch.name"
            :style="{ backgroundColor: swatch.value, color: swatch.textColor }"
            @click="selectSwatch(swatch)"
          />
        </div>
      </el-form-item>

    </el-form>

    <div class="panel-actions">
      <el-button
        class="delete-button"
        :disabled="!isEditing || submitting"
        @click="emit('delete')"
      >
        删除标记
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        @click="emit('save')"
      >
        保存标记
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
  padding-top: 16px;
}

.swatch-row {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
}

.marker-swatch {
  width: 30px;
  height: 30px;
  padding: 0;
  border: 1px solid rgba(100, 116, 139, 0.26);
  border-radius: 8px;
  cursor: pointer;
}

.marker-swatch.active {
  box-shadow:
    0 0 0 2px #fff,
    0 0 0 4px var(--brand);
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
