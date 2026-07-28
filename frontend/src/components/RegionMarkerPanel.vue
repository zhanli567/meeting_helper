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
  selectedSeatCount: { type: Number, default: 0 },
  mode: { type: String, default: 'add' },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'mode', 'save', 'delete', 'cancel'])

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

      <el-form-item label="选择方式">
        <el-segmented
          :model-value="mode"
          :options="[
            { label: '添加座位', value: 'add' },
            { label: '移除座位', value: 'remove' },
          ]"
          @update:model-value="emit('mode', $event)"
        />
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
