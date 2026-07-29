<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { DArrowLeft, DArrowRight } from '@element-plus/icons-vue'
import ColorPickerPopover from '@/components/ColorPickerPopover.vue'

defineProps({
  fieldLabel: { type: String, default: '' },
  entries: { type: Array, default: () => [] },
  collapsed: { type: Boolean, default: false },
})

const emit = defineEmits(['update:collapsed', 'set-color'])
const legendRef = ref()
const legendTop = ref(14)
const dragState = ref()

const legendStyle = computed(() => ({
  top: `${legendTop.value}px`,
}))

function clampLegendTop(value) {
  const containerHeight = legendRef.value?.parentElement?.clientHeight || window.innerHeight || 360
  const legendHeight = Math.min(legendRef.value?.offsetHeight || 180, containerHeight - 20)
  return Math.min(Math.max(10, value), Math.max(10, containerHeight - legendHeight - 10))
}

function stopLegendDrag() {
  dragState.value = undefined
  window.removeEventListener('pointermove', dragLegend)
  window.removeEventListener('pointerup', stopLegendDrag)
}

function dragLegend(event) {
  if (!dragState.value) return
  legendTop.value = clampLegendTop(
    dragState.value.startTop + event.clientY - dragState.value.startY,
  )
}

function startLegendDrag(event) {
  if (event.button !== 0) return
  dragState.value = {
    startY: event.clientY,
    startTop: legendTop.value,
  }
  window.addEventListener('pointermove', dragLegend)
  window.addEventListener('pointerup', stopLegendDrag)
}

onBeforeUnmount(stopLegendDrag)
</script>

<template>
  <aside
    ref="legendRef"
    class="group-color-legend"
    :class="{ collapsed }"
    :style="legendStyle"
    @pointerdown.stop
  >
    <div class="legend-card">
      <header class="legend-header" @pointerdown.left="startLegendDrag">
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
  position: absolute;
  left: 14px;
  z-index: 62;
  overflow: visible;
  transition:
    width 0.18s ease,
    opacity 0.18s ease;
}

.group-color-legend.collapsed {
  width: 28px;
}

.legend-card {
  max-height: min(42vh, 320px);
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: visible;
  padding: 11px 0 11px 12px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: var(--shadow-hover);
}

.group-color-legend.collapsed .legend-card {
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
}

.legend-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding-right: 10px;
  color: var(--ink);
  cursor: grab;
  user-select: none;
}

.legend-header:active {
  cursor: grabbing;
}

.legend-header strong,
.legend-value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.legend-header strong {
  font-size: 13px;
}

.legend-header span {
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
  position: absolute;
  width: 28px;
  height: 52px;
  top: calc(50% - 26px);
  right: -28px;
  z-index: 2;
  display: grid;
  place-items: center;
  padding: 0;
  color: var(--brand);
  background: #f8fbff;
  border: 1px solid var(--line);
  border-radius: 0 14px 14px 0;
  box-shadow: 5px 4px 12px rgba(37, 85, 151, 0.12);
  cursor: pointer;
}

.group-color-legend.collapsed .legend-toggle {
  right: 0;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--shadow);
}

.legend-toggle:hover {
  background: var(--brand-soft);
}
</style>
