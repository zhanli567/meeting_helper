<script setup>
import { computed, ref, watch } from 'vue'
import {
  CircleCheckFilled,
  CircleCloseFilled,
  Delete,
  Edit,
  Search,
  UploadFilled,
} from '@element-plus/icons-vue'
import SidePanelEmptyState from '@/components/SidePanelEmptyState.vue'
import {
  filteredParticipants,
  groupParticipants,
  groupableFields,
  paginateParticipants,
  participantSummary,
} from '@/utils/participantFields'
import { attendingPendingCount, isTemporarilyAbsent } from '@/utils/participantRules'
import {
  dropParticipantToPending,
  requestParticipantAttendance,
  requestParticipantRemoval,
  resetParticipantPage,
  resolveParticipantPage,
  startParticipantDrag,
} from '@/utils/participantActions'
const props = defineProps({
  participants: { type: Array, required: true },
  fieldDefinitions: { type: Array, required: true },
  selectedId: { type: String, default: undefined },
  saving: { type: Boolean, required: true },
  readonly: { type: Boolean, default: false },
})
const emit = defineEmits(['select', 'unassign', 'dragState', 'attendance', 'remove', 'edit', 'add'])
const tab = ref(props.readonly ? 'all' : 'pending')
const search = ref('')
const groupField = ref('')
const currentPage = ref(1)
const pageSize = ref(8)
const dropActive = ref(false)
const groupFields = computed(() => groupableFields(props.fieldDefinitions))
const pendingCount = computed(() => attendingPendingCount(props.participants))
const absentCount = computed(
  () => props.participants.filter((person) => isTemporarilyAbsent(person)).length,
)
const filtered = computed(() => {
  const keyword = search.value.trim().toLocaleLowerCase()
  return filteredParticipants(props.participants, tab.value, keyword)
})
const paged = computed(() => {
  return paginateParticipants(filtered.value, currentPage.value, pageSize.value)
})
const grouped = computed(() => {
  if (!groupField.value) return [{ key: '', label: '', people: paged.value }]
  return groupParticipants(paged.value, groupField.value)
})
watch([tab, search, groupField], () => {
  currentPage.value = resetParticipantPage()
})
watch(
  () => props.readonly,
  (readonly) => {
    if (readonly) tab.value = 'all'
  },
  { immediate: true },
)
watch(
  () => filtered.value.length,
  (total) => {
    currentPage.value = resolveParticipantPage(currentPage.value, total, pageSize.value)
  },
)
function dragStart(event, participant) {
  startParticipantDrag({
    event,
    participant,
    readonly: props.readonly,
    locked: participant.locked,
    onSelect: (person) => emit('select', person),
    onDragState: (participantId) => emit('dragState', participantId),
  })
}
function dropToPending(event) {
  dropParticipantToPending({
    event,
    readonly: props.readonly,
    onUnassign: (participantId) => emit('unassign', participantId),
    onDrop: () => {
      dropActive.value = false
    },
  })
}
function changeAttendance(participant) {
  requestParticipantAttendance({ readonly: props.readonly, participant, emit })
}
function removeParticipant(participant) {
  requestParticipantRemoval({ readonly: props.readonly, participant, emit })
}
function participantDynamicSummary(person) {
  return participantSummary(person, props.fieldDefinitions)
}
function participantStatus(person) {
  if (isTemporarilyAbsent(person)) return 'absent'
  if (person.assignedElementId) return 'assigned'
  return 'pending'
}
function participantStatusTitle(person) {
  return {
    assigned: '已排座',
    absent: '临时不出席',
    pending: '待排',
  }[participantStatus(person)]
}
function dragOverPanel(event) {
  if (props.readonly) return
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dropActive.value = true
}
function leavePanel(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) dropActive.value = false
}
</script>

<template>
  <aside
    class="participant-panel"
    :class="{ 'drop-active': dropActive }"
    @dragover="dragOverPanel"
    @dragleave="leavePanel"
    @drop="dropToPending"
  >
    <div class="panel-heading">
      <h2 class="panel-title">人员安排</h2>
      <el-tag size="small" effect="plain">{{ filtered.length }} 人</el-tag>
    </div>

    <div class="panel-tabs" :class="{ single: readonly }">
      <button v-if="!readonly" :class="{ active: tab === 'pending' }" @click="tab = 'pending'">
        待排 <b>{{ pendingCount }}</b>
        <small v-if="absentCount">临时不来 {{ absentCount }}</small>
      </button>
      <button :class="{ active: tab === 'all' || readonly }" @click="tab = 'all'">
        全部 <b>{{ participants.length }}</b>
      </button>
    </div>

    <div class="panel-tools">
      <el-input
        v-model="search"
        clearable
        aria-label="搜索参会人员"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-select
        v-model="groupField"
        clearable
        class="group-select"
        aria-label="人员分组方式"
      >
        <el-option
          v-for="field in groupFields"
          :key="field.code"
          :label="`按${field.label}分组`"
          :value="field.code"
        />
      </el-select>
    </div>

    <div class="participant-list" :class="{ saving }">
      <template v-if="filtered.length">
        <section v-for="group in grouped" :key="group.key || 'all'" class="person-group">
          <header v-if="group.label">
            <span>{{ group.label }}</span>
            <b>{{ group.people.length }}</b>
          </header>
          <article
            v-for="person in group.people"
            :key="person.id"
            class="person-card"
            :class="{
              selected: selectedId === person.id,
              assigned: person.assignedElementId,
              absent: isTemporarilyAbsent(person),
              readonly,
            }"
            :draggable="!readonly && !person.locked && !isTemporarilyAbsent(person)"
            @dragstart="dragStart($event, person)"
            @dragend="emit('dragState', undefined)"
            @click="emit('select', person)"
          >
            <span
              class="person-color"
              :style="{ backgroundColor: person.displayColor || '#d5dbe5' }"
            />
            <div class="person-main">
              <div class="person-fixed">
                <strong>{{ person.name }}</strong>
                <span class="person-employee-no">工号 {{ person.employeeNo }}</span>
              </div>
              <div class="person-dynamic">
                <el-tag v-if="isTemporarilyAbsent(person)" size="small" type="info">临时不出席</el-tag>
                <span
                  v-for="summary in participantDynamicSummary(person)"
                  :key="summary"
                >
                  {{ summary }}
                </span>
                <template v-if="person.records?.length > 1">
                  <span>共 {{ person.records.length }} 条记录</span>
                </template>
              </div>
            </div>
            <div v-if="!readonly" class="person-actions icon-actions" @click.stop>
              <el-button
                text
                circle
                size="small"
                :icon="Edit"
                title="编辑人员"
                @click="emit('edit', person)"
              />
              <el-button
                text
                circle
                size="small"
                :type="isTemporarilyAbsent(person) ? 'success' : 'warning'"
                :icon="isTemporarilyAbsent(person) ? CircleCheckFilled : CircleCloseFilled"
                :title="isTemporarilyAbsent(person) ? '恢复出席' : '临时不来'"
                @click="changeAttendance(person)"
              />
              <el-button
                text
                circle
                size="small"
                type="danger"
                :icon="Delete"
                title="移出会议"
                @click="removeParticipant(person)"
              />
            </div>
            <span
              class="participant-status-dot"
              :class="`status-${participantStatus(person)}`"
              :title="participantStatusTitle(person)"
            />
          </article>
        </section>
      </template>
      <div v-else class="empty-copy">
        <SidePanelEmptyState
          :icon="UploadFilled"
          :title="tab === 'pending' ? '当前没有待排人员' : '没有匹配的参会人员'"
          :description="readonly ? '' : '点击新增人员'"
          :clickable="!readonly"
          @activate="emit('add')"
        />
      </div>
    </div>

    <div class="pagination-row">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        size="small"
        background
        layout="prev, pager, next"
        :pager-count="5"
        :total="filtered.length"
      />
      <span>共 {{ filtered.length }} 人</span>
    </div>
  </aside>
</template>

<style scoped>
.participant-panel {
  min-width: 350px;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow);
  transition:
    background-color 0.15s,
    box-shadow 0.15s;
}

.participant-panel.drop-active {
  background: #f8fbff;
  box-shadow:
    var(--shadow),
    inset 0 0 0 2px var(--brand);
}

.panel-heading {
  flex: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-tabs {
  flex: none;
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 3px;
  background: var(--workspace);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.panel-tabs.single {
  grid-template-columns: 1fr;
}

.panel-tabs button {
  padding: 7px 10px;
  color: var(--muted);
  background: transparent;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
}

.panel-tabs button.active {
  color: var(--brand);
  background: #fff;
  box-shadow: var(--shadow-soft);
}

.panel-tabs b {
  margin-left: 4px;
  font-size: 11px;
}

.panel-tabs small {
  margin-left: 6px;
  color: var(--tertiary);
  font-size: 9px;
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

.group-select {
  flex: none;
  width: 100%;
}

.participant-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 0;
  transition: opacity 0.15s;
}

.participant-list.saving {
  opacity: 0.58;
  pointer-events: none;
}

.person-group header {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  padding: 7px 3px 5px;
  color: var(--muted);
  background: #fff;
  font-size: 11px;
  font-weight: 700;
}

.person-card {
  position: relative;
  min-height: 62px;
  margin-bottom: 7px;
  display: flex;
  align-items: stretch;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  cursor: grab;
  transition:
    border-color 0.15s,
    box-shadow 0.15s,
    transform 0.15s;
}

.person-card:hover,
.person-card.selected {
  border-color: rgba(10, 89, 247, 0.34);
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.person-card.assigned {
  background: #fbfcfd;
}

.person-card.absent {
  background: #f6f7f8;
  border-color: var(--line);
  cursor: default;
  filter: grayscale(0.35);
}

.person-card.absent .person-main {
  opacity: 0.72;
}

.person-card.readonly {
  cursor: pointer;
}

.person-color {
  width: 7px;
  flex: none;
}

.person-main {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-rows: auto minmax(20px, auto);
  align-content: start;
  gap: 6px;
  padding: 8px 10px;
}

.person-actions {
  position: absolute;
  inset: 0;
  z-index: 6;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 0;
  background: rgba(255, 255, 255, 0.82);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;
}

.person-card:hover .person-actions,
.person-card:focus-within .person-actions {
  opacity: 1;
}

.person-actions .el-button {
  width: 30px;
  height: 30px;
  margin: 0;
  padding: 0;
  background: #fff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.12);
  pointer-events: none;
}

.person-card:hover .person-actions .el-button,
.person-card:focus-within .person-actions .el-button {
  pointer-events: auto;
}

.person-fixed {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}

.person-main strong {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.person-fixed span {
  flex: none;
  color: var(--muted);
  font-size: 11px;
}

.person-employee-no {
  margin-right: 16px;
}

.person-dynamic {
  min-height: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
}

.person-dynamic span {
  max-width: 100%;
  padding: 2px 6px;
  overflow: hidden;
  background: #f3f6fb;
  border: 1px solid rgba(226, 232, 240, 0.86);
  border-radius: 6px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.participant-status-dot {
  width: 7px;
  height: 7px;
  position: absolute;
  top: 9px;
  right: 9px;
  border-radius: 50%;
}

.participant-status-dot.status-assigned {
  background: #86efac;
  border: 1px solid #bbf7d0;
}

.participant-status-dot.status-absent {
  background: #fecaca;
  border: 1px solid #fee2e2;
}

.participant-status-dot.status-pending {
  background: #fde68a;
  border: 1px solid #fef3c7;
}

.empty-copy {
  height: 100%;
  display: grid;
  place-content: center;
  justify-items: center;
}

.pagination-row {
  min-height: 30px;
  flex: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
  color: var(--tertiary);
  font-size: 10px;
}

.pagination-row :deep(.el-pagination) {
  --el-pagination-button-width: 24px;
  --el-pagination-button-height: 24px;
}

</style>
