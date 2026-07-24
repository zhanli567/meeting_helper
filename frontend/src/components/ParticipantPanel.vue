<script setup>
import { computed, ref, watch } from 'vue'
import { Search, UploadFilled } from '@element-plus/icons-vue'
const props = withDefaults(defineProps(), { readonly: false })
const emit = defineEmits()
const tab = ref('pending')
const search = ref('')
const groupField = ref('')
const currentPage = ref(1)
const pageSize = ref(8)
function fieldValue(person, fieldCode) {
  if (fieldCode === 'name') return person.name
  if (fieldCode === 'employeeNo') return person.employeeNo
  if (fieldCode === 'level') return person.level
  if (fieldCode === 'department') return person.department
  if (fieldCode === 'participantType') return person.participantType
  if (fieldCode === 'primaryBatchName') return person.primaryBatchName
  if (fieldCode === 'tags') return person.tags
  return person.attributes[fieldCode]
}
const groupFields = computed(() =>
  props.fieldDefinitions.filter(
    (field) => field.filterable && !['name', 'employeeNo'].includes(field.code),
  ),
)
const pendingCount = computed(
  () => props.participants.filter((person) => !person.assignedElementId).length,
)
const filtered = computed(() => {
  const keyword = search.value.trim().toLocaleLowerCase()
  return props.participants.filter((person) => {
    if (tab.value === 'pending' && person.assignedElementId) return false
    if (!keyword) return true
    const fuzzyName = person.name.toLocaleLowerCase().includes(keyword)
    const exactEmployeeNo = person.employeeNo.toLocaleLowerCase() === keyword
    return fuzzyName || exactEmployeeNo
  })
})
const paged = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filtered.value.slice(start, start + pageSize.value)
})
const grouped = computed(() => {
  if (!groupField.value) return [{ key: '', label: '', people: paged.value }]
  const result = new Map()
  paged.value.forEach((person) => {
    const raw = fieldValue(person, groupField.value)
    const key = Array.isArray(raw) ? raw.join('、') : String(raw || '未分组')
    result.set(key, [...(result.get(key) || []), person])
  })
  return Array.from(result.entries()).map(([key, people]) => ({ key, label: key, people }))
})
watch([tab, search, groupField], () => {
  currentPage.value = 1
})
watch(
  () => filtered.value.length,
  (total) => {
    const lastPage = Math.max(1, Math.ceil(total / pageSize.value))
    if (currentPage.value > lastPage) currentPage.value = lastPage
  },
)
function dragStart(event, participant) {
  if (props.readonly || participant.locked) {
    event.preventDefault()
    return
  }
  event.dataTransfer?.setData('text/participant-id', participant.id)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
  emit('select', participant)
  emit('dragState', participant.id)
}
function dropToPending(event) {
  if (props.readonly) return
  event.preventDefault()
  const participantId = event.dataTransfer?.getData('text/participant-id')
  if (participantId) emit('unassign', participantId)
}
</script>

<template>
  <aside class="participant-panel" @dragover.prevent @drop="dropToPending">
    <div class="panel-heading">
      <div>
        <span class="eyebrow">SEATING QUEUE</span>
        <h2 class="panel-title">人员安排</h2>
      </div>
      <el-tag size="small" effect="plain">{{ filtered.length }} 人</el-tag>
    </div>

    <div class="panel-tabs">
      <button :class="{ active: tab === 'pending' }" @click="tab = 'pending'">
        待排 <b>{{ pendingCount }}</b>
      </button>
      <button :class="{ active: tab === 'all' }" @click="tab = 'all'">
        全部 <b>{{ participants.length }}</b>
      </button>
    </div>

    <el-input
      v-model="search"
      clearable
      placeholder="姓名模糊搜索 / 9位工号精确搜索"
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
              readonly,
            }"
            :draggable="!readonly && !person.locked"
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
                <el-tag v-if="person.primaryBatchName" size="small" effect="plain">
                  {{ person.primaryBatchName }}
                </el-tag>
              </div>
              <span>{{ person.employeeNo }} · {{ person.department || '未填写部门' }}</span>
              <small>
                {{ person.participantType || '参会人员' }}
                <template v-if="person.level"> · 职级{{ person.level }}</template>
                <template v-if="person.repeatedBatches.length">
                  · 复{{ person.repeatedBatches.join('、') }}
                </template>
              </small>
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

    <div v-if="!readonly" class="pending-drop">将已排人员拖到这里，可移回待排列表</div>
    <div v-else class="readonly-note">当前为已发布版本，仅供查看</div>
  </aside>
</template>

<style scoped>
.participant-panel {
  min-width: 350px;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 11px;
  padding: 18px 16px 14px;
  overflow: hidden;
  background: #fff;
  border-left: 1px solid var(--line);
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
  background: #eff3f8;
  border-radius: 9px;
}

.panel-tabs button {
  padding: 7px 10px;
  color: #667085;
  background: transparent;
  border: 0;
  border-radius: 7px;
  cursor: pointer;
}

.panel-tabs button.active {
  color: #1e3a66;
  background: #fff;
  box-shadow: 0 1px 4px rgba(30, 58, 102, 0.12);
}

.panel-tabs b {
  margin-left: 4px;
  font-size: 11px;
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
  color: #667085;
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
  border: 1px solid #e1e6ee;
  border-radius: 10px;
  cursor: grab;
  transition:
    border-color 0.15s,
    box-shadow 0.15s,
    transform 0.15s;
}

.person-card:hover,
.person-card.selected {
  border-color: #5e82b8;
  box-shadow: 0 5px 14px rgba(39, 79, 147, 0.1);
  transform: translateY(-1px);
}

.person-card.assigned {
  background: #fbfcfe;
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
  color: #64748b;
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
  background: #22a06b;
  border-radius: 50%;
}

.empty-copy {
  height: 100%;
  display: grid;
  place-content: center;
  justify-items: center;
  color: #7b8798;
}

.pagination-row {
  min-height: 30px;
  flex: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
  color: #7a899d;
  font-size: 10px;
}

.pagination-row :deep(.el-pagination) {
  --el-pagination-button-width: 24px;
  --el-pagination-button-height: 24px;
}

.pending-drop,
.readonly-note {
  flex: none;
  padding: 8px 10px;
  color: #748094;
  background: #f7f9fc;
  border: 1px dashed #c9d2df;
  border-radius: 8px;
  font-size: 11px;
  text-align: center;
}

.readonly-note {
  color: #3565a6;
  background: #eef5ff;
  border-style: solid;
  border-color: #c7daf4;
}
</style>
