<script setup>
import { computed, ref, watch } from 'vue'
import { CircleCheckFilled, CircleCloseFilled, Delete, Search, UploadFilled } from '@element-plus/icons-vue'
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
const emit = defineEmits(['select', 'unassign', 'dragState', 'attendance', 'remove'])
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
        placeholder="姓名模糊搜索 / 8或9位工号精确搜索"
        aria-label="搜索参会人员"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-select
        v-model="groupField"
        clearable
        placeholder="不分组"
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
              <div>
                <strong>{{ person.name }}</strong>
              </div>
              <span>{{ person.employeeNo }}</span>
              <small>
                <el-tag v-if="isTemporarilyAbsent(person)" size="small" type="info">临时不出席</el-tag>
                <template v-if="participantSummary(person, fieldDefinitions).length">
                  {{ participantSummary(person, fieldDefinitions).join(' · ') }}
                </template>
                <template v-if="person.records?.length > 1">
                  {{ participantSummary(person, fieldDefinitions).length ? ' · ' : '' }}
                  共 {{ person.records.length }} 条记录
                </template>
              </small>
            </div>
            <div v-if="!readonly" class="person-actions" @click.stop>
              <el-button
                link
                size="small"
                :type="isTemporarilyAbsent(person) ? 'success' : 'warning'"
                :icon="isTemporarilyAbsent(person) ? CircleCheckFilled : CircleCloseFilled"
                @click="changeAttendance(person)"
              >
                {{ isTemporarilyAbsent(person) ? '恢复出席' : '临时不来' }}
              </el-button>
              <el-button
                link
                size="small"
                type="danger"
                :icon="Delete"
                @click="removeParticipant(person)"
              >
                移出会议
              </el-button>
            </div>
            <span v-if="person.assignedElementId" class="assigned-dot" title="已排座" />
          </article>
        </section>
      </template>
      <div v-else class="empty-copy">
        <el-icon size="26"><UploadFilled /></el-icon>
        <p>{{ tab === 'pending' ? '当前没有待排人员' : '没有匹配的参会人员' }}</p>
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
  padding-right: 4px;
  scrollbar-gutter: stable;
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
  align-content: center;
  gap: 3px;
  padding: 7px 9px;
}

.person-actions {
  width: 78px;
  flex: none;
  display: grid;
  align-content: center;
  justify-items: start;
  gap: 2px;
  padding: 5px 6px 5px 0;
  border-left: 1px solid var(--line);
}

.person-actions .el-button {
  height: 23px;
  margin: 0;
  padding: 0 3px;
  font-size: 10px;
}

.person-main > div {
  display: flex;
  align-items: center;
  gap: 7px;
}

.person-main strong {
  font-size: 13px;
}

.person-main > span,
.person-main small {
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assigned-dot {
  width: 7px;
  height: 7px;
  position: absolute;
  top: 9px;
  right: 9px;
  background: var(--success);
  border-radius: 50%;
}

.empty-copy {
  height: 100%;
  display: grid;
  place-content: center;
  justify-items: center;
  color: var(--tertiary);
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
