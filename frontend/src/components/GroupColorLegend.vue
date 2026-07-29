<script setup>
import { DArrowLeft, DArrowRight } from '@element-plus/icons-vue'
import ColorPickerPopover from '@/components/ColorPickerPopover.vue'

defineProps({
  fieldLabel: { type: String, default: '' },
  entries: { type: Array, default: () => [] },
  collapsed: { type: Boolean, default: false },
})

const emit = defineEmits(['update:collapsed', 'set-color'])
</script>

<template>
  <aside class="group-color-legend" :class="{ collapsed }" @pointerdown.stop>
    <div class="legend-content">
      <header>
        <strong>{{ fieldLabel }}</strong>
        <span>{{ entries.length }} 项</span>
      </header>
      <div class="legend-list">
        <div v-for="entry in entries" :key="entry.value" class="legend-item">
          <span
            class="legend-swatch"
            :style="{ backgroundColor: entry.backgroundColor }"
          />
          <span class="legend-value" :title="entry.value">{{ entry.value }}</span>
          <ColorPickerPopover
            :model-value="entry.backgroundColor"
            :label="`${fieldLabel} ${entry.value}`"
            @update:model-value="emit('set-color', { value: entry.value, color: $event })"
          />
        </div>
      </div>
    </div>

    <button
      type="button"
      class="legend-toggle"
      :title="collapsed ? '展开着色说明' : '收起着色说明'"
      :aria-label="collapsed ? '展开着色说明' : '收起着色说明'"
      @click="emit('update:collapsed', !collapsed)"
    >
      <el-icon>
        <DArrowRight v-if="collapsed" />
        <DArrowLeft v-else />
      </el-icon>
    </button>
  </aside>
</template>

<style scoped>
.group-color-legend {
  width: 258px;
  max-height: min(42vh, 320px);
  position: absolute;
  top: 14px;
  left: 14px;
  z-index: 62;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 28px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: var(--shadow-hover);
  transition:
    width 0.18s ease,
    background 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.group-color-legend.collapsed {
  width: 34px;
  grid-template-columns: 0 34px;
  background: transparent;
  border-color: transparent;
  box-shadow: none;
}

.legend-content {
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 11px 0 11px 12px;
}

.group-color-legend.collapsed .legend-content {
  padding: 0;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
}

header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding-right: 10px;
  color: var(--ink);
}

header strong,
.legend-value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

header strong {
  font-size: 13px;
}

header span {
  color: var(--muted);
  font-size: 11px;
}

.legend-list {
  min-height: 84px;
  max-height: 250px;
  display: grid;
  align-content: start;
  gap: 7px;
  margin-top: 9px;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 8px;
}

.legend-item {
  min-width: 0;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) 32px;
  align-items: center;
  gap: 8px;
}

.legend-swatch {
  width: 18px;
  height: 18px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 50%;
}

.legend-value {
  color: var(--ink);
  font-size: 12px;
}

.legend-toggle {
  width: 100%;
  min-height: 100%;
  display: grid;
  place-items: center;
  padding: 0;
  color: var(--brand);
  background: #f8fbff;
  border: 0;
  border-left: 1px solid var(--line);
  border-radius: 0 8px 8px 0;
  cursor: pointer;
}

.group-color-legend.collapsed .legend-toggle {
  min-height: 84px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: var(--shadow);
}

.legend-toggle:hover {
  background: var(--brand-soft);
}
</style>
