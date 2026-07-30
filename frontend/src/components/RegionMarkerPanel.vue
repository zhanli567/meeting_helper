<script setup>
import { computed } from 'vue'
import ColorPickerPopover from '@/components/ColorPickerPopover.vue'
import SidePanelEmptyState from '@/components/SidePanelEmptyState.vue'
import { textColorForBackground } from '@/utils/venuePreferences'

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

const emit = defineEmits(['update:modelValue', 'select', 'delete'])

const isEditing = computed(() => Boolean(props.modelValue?.id))
const usedMarkerColors = computed(() =>
  (props.markers || [])
    .filter((marker) => marker.id !== props.modelValue?.id)
    .map((marker) => marker.backgroundColor)
    .filter(Boolean),
)

function patchMarker(value) {
  emit('update:modelValue', {
    ...(props.modelValue || {}),
    ...value,
  })
}

function patchColor(color) {
  patchMarker({
    backgroundColor: color,
    textColor: textColorForBackground(color),
    bold: true,
  })
}
</script>

<template>
  <aside class="region-marker-panel">
    <header class="panel-heading">
      <div>
        <h2 class="panel-title">区域</h2>
        <p>已选座位 {{ selectedSeatCount }}</p>
      </div>
    </header>

    <section class="panel-tools marker-list-section">
      <div class="section-title">
        <span>已有区域</span>
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
      <div v-else class="empty-marker-list">
        <SidePanelEmptyState title="暂无区域" description="框选座位后创建区域" />
      </div>
    </section>

    <section class="marker-detail-section">
      <template v-if="isEditing">
        <el-form label-position="top" class="marker-form">
          <el-form-item label="区域名称" required>
            <el-input
              :model-value="modelValue.label"
              maxlength="20"
              show-word-limit
              @update:model-value="patchMarker({ label: $event })"
            />
          </el-form-item>

          <el-form-item label="区域颜色">
            <div class="color-control-row">
              <span>{{ modelValue.backgroundColor }}</span>
              <ColorPickerPopover
                :model-value="modelValue.backgroundColor"
                label="区域颜色"
                :unavailable-colors="usedMarkerColors"
                @update:model-value="patchColor"
              />
            </div>
          </el-form-item>
        </el-form>

        <div class="panel-actions">
          <el-button
            class="delete-button"
            :disabled="submitting"
            @click="emit('delete')"
          >
            删除区域
          </el-button>
        </div>
      </template>

      <SidePanelEmptyState
        v-else
        title="未选中区域"
        description="单击已有区域查看详情，或框选座位创建区域"
      />
    </section>
  </aside>
</template>

<style scoped>
.region-marker-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow);
}

.panel-heading {
  flex: none;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.panel-heading h2 {
  margin: 0;
  color: var(--ink);
  font-size: 18px;
}

.panel-heading p {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.panel-tools {
  flex: none;
  display: grid;
  gap: 8px;
  padding: 10px;
  background: #fbfcfd;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.marker-list-section {
  min-height: 182px;
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
  min-height: 116px;
  max-height: 156px;
  display: grid;
  align-content: start;
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

.empty-marker-list {
  min-height: 116px;
  display: grid;
  place-items: center;
}

.marker-detail-section {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 10px;
  background: #fbfcfd;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.marker-form {
  flex: 1;
  min-height: 0;
  padding-right: 2px;
}

.color-control-row {
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  color: var(--muted);
  font-size: 12px;
}

.panel-actions {
  flex: none;
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
