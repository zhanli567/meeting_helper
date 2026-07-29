<script setup>
import { computed } from 'vue'
import { DArrowLeft, DArrowRight, Edit } from '@element-plus/icons-vue'

const fixedFieldCodes = new Set(['employeeNo', 'name'])

const props = defineProps({
  participant: { type: Object, required: true },
  fieldDefinitions: { type: Array, default: () => [] },
  seatLabel: { type: String, default: '' },
  collapsed: { type: Boolean, default: false },
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['update:collapsed', 'edit'])

function cleanValue(value) {
  if (value === undefined || value === null) return ''
  return String(value).trim()
}

const dynamicFields = computed(() =>
  (props.fieldDefinitions || []).filter((field) => !fixedFieldCodes.has(field.code)),
)

const statusText = computed(() => {
  if (props.participant.attendanceStatus === 'TEMPORARILY_ABSENT') return '临时不出席'
  if (props.participant.assignedElementId) return '已排'
  return '待排'
})

const displaySeatLabel = computed(() => props.seatLabel || '未排座')

const participantRecords = computed(() => {
  const records = props.participant.records?.length
    ? props.participant.records
    : [{ id: 'primary', recordOrder: 1, attributes: props.participant.primaryAttributes || {} }]

  return records
    .map((record, index) => ({
      key: record.id || `record-${index}`,
      rows: dynamicFields.value
        .map((field) => ({
          label: field.label || field.code,
          value: cleanValue(record.attributes?.[field.code]),
        }))
        .filter((row) => row.value),
    }))
    .filter((record) => record.rows.length)
})

function toggleCollapsed() {
  emit('update:collapsed', !props.collapsed)
}
</script>

<template>
  <aside
    class="participant-info-panel"
    :class="{ collapsed }"
    @pointerdown.stop
    @click.stop
  >
    <section class="info-card">
      <header class="info-header">
        <div>
          <strong>人员信息</strong>
          <span :title="participant.name">{{ participant.name }}</span>
        </div>
        <el-button
          v-if="!readonly"
          text
          circle
          size="small"
          :icon="Edit"
          title="编辑人员"
          @click.stop="emit('edit', participant)"
        />
      </header>

      <div class="fixed-info-grid">
        <span>姓名</span>
        <b :title="participant.name">{{ participant.name || '-' }}</b>
        <span>工号</span>
        <b :title="participant.employeeNo">{{ participant.employeeNo || '-' }}</b>
        <span>状态</span>
        <b>{{ statusText }}</b>
        <span>座位</span>
        <b>{{ displaySeatLabel }}</b>
      </div>

      <div class="record-list">
        <template v-if="participantRecords.length">
          <section
            v-for="(record, index) in participantRecords"
            :key="record.key"
            class="record-group"
          >
            <strong>记录 {{ index + 1 }}</strong>
            <div class="record-fields">
              <span
                v-for="row in record.rows"
                :key="`${record.key}-${row.label}`"
                class="record-field"
              >
                <em>{{ row.label }}</em>
                <b :title="row.value">{{ row.value }}</b>
              </span>
            </div>
          </section>
        </template>
        <p v-else>暂无扩展信息</p>
      </div>
    </section>

    <button
      type="button"
      class="info-toggle"
      :title="collapsed ? '展开人员信息' : '收起人员信息'"
      :aria-label="collapsed ? '展开人员信息' : '收起人员信息'"
      @click="toggleCollapsed"
    >
      <el-icon>
        <DArrowRight v-if="collapsed" />
        <DArrowLeft v-else />
      </el-icon>
    </button>
  </aside>
</template>

<style scoped>
.participant-info-panel {
  width: 288px;
  position: absolute;
  top: 14px;
  left: 14px;
  z-index: 63;
  overflow: visible;
  transition:
    width 0.18s ease,
    opacity 0.18s ease;
}

.participant-info-panel.collapsed {
  width: 28px;
}

.info-card {
  max-height: min(46vh, 360px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: var(--shadow-hover);
}

.participant-info-panel.collapsed .info-card {
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
}

.info-header {
  min-height: 48px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
}

.info-header div {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.info-header strong {
  color: var(--ink);
  font-size: 14px;
}

.info-header span {
  min-width: 0;
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fixed-info-grid {
  flex: none;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 8px 10px;
  padding: 12px;
  border-bottom: 1px solid var(--line);
}

.fixed-info-grid span,
.record-field em,
.record-list p {
  color: var(--muted);
  font-size: 12px;
  font-style: normal;
}

.fixed-info-grid b,
.record-field b {
  min-width: 0;
  overflow: hidden;
  color: var(--ink);
  font-size: 12px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-list {
  flex: 1;
  min-height: 86px;
  display: grid;
  align-content: start;
  gap: 10px;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 12px;
}

.record-list p {
  margin: 0;
}

.record-group {
  display: grid;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid rgba(226, 232, 240, 0.9);
}

.record-group:first-child {
  padding-top: 0;
  border-top: 0;
}

.record-group > strong {
  color: var(--ink);
  font-size: 12px;
}

.record-fields {
  display: grid;
  gap: 6px;
}

.record-field {
  min-width: 0;
  display: grid;
  grid-template-columns: 74px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.info-toggle {
  width: 28px;
  height: 52px;
  position: absolute;
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

.participant-info-panel.collapsed .info-toggle {
  right: 0;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--shadow);
}

.info-toggle:hover {
  background: var(--brand-soft);
}
</style>
