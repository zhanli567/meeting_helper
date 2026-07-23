<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Filter, Rank, Search, Star, UploadFilled } from '@element-plus/icons-vue'
import type { FieldDefinition, Participant } from '@/types/workspace'

const props = defineProps<{
  participants: Participant[]
  fieldDefinitions: FieldDefinition[]
  selectedId?: string
  saving: boolean
}>()

const emit = defineEmits<{
  select: [participant: Participant]
  unassign: [participantId: string]
  queueChange: [participantIds: string[]]
  dragState: [participantId?: string]
}>()

const tab = ref<'pending' | 'all'>('pending')
const search = ref('')
const sortField = ref('level')
const sortDirection = ref<'asc' | 'desc'>('desc')
const groupField = ref('')
const continuous = ref(false)
const currentPage = ref(1)
const pageSize = ref(8)
const filters = reactive<Record<string, string[]>>({})
const savedViews = ref<
  Array<{
    name: string
    filters: Record<string, string[]>
    sortField: string
    sortDirection: 'asc' | 'desc'
    group: string
  }>
>(JSON.parse(localStorage.getItem('meeting-helper-views') || '[]'))

const filterFields = computed(() =>
  props.fieldDefinitions.filter(
    (field) => field.filterable && ['ENUM', 'MULTI_ENUM', 'TEXT'].includes(field.type),
  ),
)
const sortableFields = computed(() => props.fieldDefinitions.filter((field) => field.sortable))

function fieldValue(
  person: Participant,
  fieldCode: string,
): string | string[] | number | undefined {
  if (fieldCode === 'name') return person.name
  if (fieldCode === 'employeeNo') return person.employeeNo
  if (fieldCode === 'level') return person.level
  if (fieldCode === 'department') return person.department
  if (fieldCode === 'participantType') return person.participantType
  if (fieldCode === 'primaryBatchName') return person.primaryBatchName
  if (fieldCode === 'tags') return person.tags
  return person.attributes[fieldCode]
}

function optionsFor(field: FieldDefinition) {
  const values = new Set<string>()
  props.participants.forEach((person) => {
    const value = fieldValue(person, field.code)
    if (Array.isArray(value)) value.forEach((item) => item && values.add(String(item)))
    else if (value !== undefined && value !== '') values.add(String(value))
  })
  return Array.from(values).sort((left, right) => left.localeCompare(right, 'zh-CN'))
}

const filtered = computed(() => {
  const keyword = search.value.trim().toLocaleLowerCase()
  const result = props.participants.filter((person) => {
    if (tab.value === 'pending' && person.assignedElementId) return false
    if (keyword) {
      const searchable = [
        person.name,
        person.employeeNo,
        person.department,
        person.participantType,
        person.primaryBatchName,
        ...person.tags,
        ...person.awards.flatMap((award) => [award.awardName, award.projectName]),
      ]
        .filter(Boolean)
        .join(' ')
        .toLocaleLowerCase()
      if (!searchable.includes(keyword)) return false
    }
    return Object.entries(filters).every(([fieldCode, selected]) => {
      if (!selected.length) return true
      const value = fieldValue(person, fieldCode)
      return Array.isArray(value)
        ? selected.some((item) => value.includes(item))
        : selected.includes(String(value ?? ''))
    })
  })
  return result.sort((left, right) => {
    const leftValue =
      sortField.value === 'primaryBatchName'
        ? left.primaryBatchOrder
        : fieldValue(left, sortField.value)
    const rightValue =
      sortField.value === 'primaryBatchName'
        ? right.primaryBatchOrder
        : fieldValue(right, sortField.value)
    const leftComparable = Array.isArray(leftValue) ? leftValue.join('、') : leftValue
    const rightComparable = Array.isArray(rightValue) ? rightValue.join('、') : rightValue
    let comparison = 0
    if (typeof leftComparable === 'number' || typeof rightComparable === 'number') {
      comparison = Number(leftComparable ?? -1) - Number(rightComparable ?? -1)
    } else {
      comparison = String(leftComparable ?? '').localeCompare(
        String(rightComparable ?? ''),
        'zh-CN',
        { numeric: true },
      )
    }
    return (sortDirection.value === 'asc' ? comparison : -comparison) ||
      left.name.localeCompare(right.name, 'zh-CN')
  })
})

const paged = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filtered.value.slice(start, start + pageSize.value)
})

const grouped = computed(() => {
  if (!groupField.value) return [{ key: '', label: '', people: paged.value }]
  const result = new Map<string, Participant[]>()
  paged.value.forEach((person) => {
    const raw = fieldValue(person, groupField.value)
    const key = Array.isArray(raw) ? raw.join('、') : String(raw || '未分类')
    result.set(key, [...(result.get(key) || []), person])
  })
  return Array.from(result.entries()).map(([key, people]) => ({ key, label: key, people }))
})

watch(
  filtered,
  (people) => emit('queueChange', continuous.value ? people.map((person) => person.id) : []),
  { immediate: true },
)
watch(continuous, () =>
  emit('queueChange', continuous.value ? filtered.value.map((person) => person.id) : []),
)
watch(
  [tab, search, sortField, sortDirection, groupField, () => JSON.stringify(filters)],
  () => (currentPage.value = 1),
)

function dragStart(event: DragEvent, participant: Participant) {
  event.dataTransfer?.setData('text/participant-id', participant.id)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
  emit('select', participant)
  emit('dragState', participant.id)
}

function dropToPending(event: DragEvent) {
  event.preventDefault()
  const participantId = event.dataTransfer?.getData('text/participant-id')
  if (participantId) emit('unassign', participantId)
}

async function saveView() {
  try {
    const { value } = await ElMessageBox.prompt('为当前筛选、排序和分组条件命名', '保存常用视图', {
      inputPlaceholder: '例如：先排特邀嘉宾',
      confirmButtonText: '保存',
      cancelButtonText: '取消',
    })
    savedViews.value.push({
      name: value,
      filters: JSON.parse(JSON.stringify(filters)),
      sortField: sortField.value,
      sortDirection: sortDirection.value,
      group: groupField.value,
    })
    localStorage.setItem('meeting-helper-views', JSON.stringify(savedViews.value))
    ElMessage.success('视图已保存')
  } catch {
    // 用户取消。
  }
}

function applyView(index: number) {
  const view = savedViews.value[index]
  if (!view) return
  Object.keys(filters).forEach((key) => delete filters[key])
  Object.assign(filters, JSON.parse(JSON.stringify(view.filters)))
  sortField.value = view.sortField || 'level'
  sortDirection.value = view.sortDirection || 'desc'
  groupField.value = view.group
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
        待排 <b>{{ participants.filter((person) => !person.assignedElementId).length }}</b>
      </button>
      <button :class="{ active: tab === 'all' }" @click="tab = 'all'">
        全部 <b>{{ participants.length }}</b>
      </button>
    </div>

    <el-input v-model="search" clearable placeholder="搜索姓名、工号、部门等">
      <template #prefix
        ><el-icon><Search /></el-icon
      ></template>
    </el-input>

    <div class="query-toolbar">
      <el-popover placement="bottom-start" :width="300" trigger="click">
        <template #reference>
          <el-button size="small" :icon="Filter">筛选</el-button>
        </template>
        <div class="filter-list">
          <label v-for="field in filterFields" :key="field.code">
            <span>{{ field.label }}</span>
            <el-select
              v-model="filters[field.code]"
              multiple
              collapse-tags
              clearable
              :placeholder="`选择${field.label}`"
            >
              <el-option
                v-for="option in optionsFor(field)"
                :key="option"
                :label="option"
                :value="option"
              />
            </el-select>
          </label>
        </div>
      </el-popover>

      <el-select v-model="sortField" size="small" class="sort-select" placeholder="排序字段">
        <template #prefix
          ><el-icon><Rank /></el-icon
        ></template>
        <el-option
          v-for="field in sortableFields"
          :key="field.code"
          :label="field.label"
          :value="field.code"
        />
      </el-select>

      <el-select v-model="sortDirection" size="small" class="direction-select">
        <el-option label="升序" value="asc" />
        <el-option label="降序" value="desc" />
      </el-select>

      <el-select
        v-model="groupField"
        size="small"
        clearable
        placeholder="不分组"
        class="group-select"
      >
        <el-option
          v-for="field in filterFields"
          :key="field.code"
          :label="`按${field.label}`"
          :value="field.code"
        />
      </el-select>
    </div>

    <div class="view-row">
      <el-dropdown v-if="savedViews.length" trigger="click" @command="applyView">
        <el-button link size="small"
          ><el-icon><Star /></el-icon> 筛选方案</el-button
        >
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="(view, index) in savedViews" :key="index" :command="index">
              {{ view.name }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-tooltip content="保存当前筛选、排序和分组条件，方便下次一键恢复" placement="bottom">
        <el-button link size="small" @click="saveView">保存筛选方案</el-button>
      </el-tooltip>
      <el-tooltip content="开启后，按当前排序逐人点击空座位安排；关闭时使用拖拽排座" placement="bottom">
        <label class="continuous-switch">
          <span>连续排座</span>
          <el-switch v-model="continuous" size="small" />
        </label>
      </el-tooltip>
    </div>

    <div v-if="continuous && filtered[0]" class="continuous-card">
      <span>下一位</span>
      <strong>{{ filtered[0].name }}</strong>
      <small>点击左侧空座位完成安排</small>
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
            :class="{ selected: selectedId === person.id, assigned: person.assignedElementId }"
            :draggable="!person.locked"
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
              <span>{{ person.department || '未填写部门' }}</span>
              <small>
                {{ person.participantType || '参会人员' }}
                <template v-if="person.level"> · 职级{{ person.level }}</template>
                <template v-if="person.repeatedBatches.length">
                  · 复 {{ person.repeatedBatches.join('、') }}
                </template>
              </small>
            </div>
            <span v-if="person.assignedElementId" class="assigned-dot" title="已排座" />
          </article>
        </section>
      </template>
      <div v-else class="empty-copy">
        <el-icon size="26"><UploadFilled /></el-icon>
        <p>当前条件下没有待排人员</p>
      </div>
    </div>

    <div v-if="filtered.length > pageSize" class="pagination-row">
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

    <div class="pending-drop">将已排人员拖到这里，可移回待排列表</div>
  </aside>
</template>

<style scoped>
.participant-panel {
  min-width: 350px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px 16px 14px;
  background: #fff;
  border-left: 1px solid var(--line);
}

.panel-heading,
.view-row,
.query-toolbar {
  display: flex;
  align-items: center;
}

.panel-heading {
  justify-content: space-between;
}

.panel-tabs {
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

.query-toolbar {
  gap: 7px;
  flex-wrap: wrap;
}

.sort-select {
  width: 112px;
}

.direction-select {
  width: 78px;
}

.group-select {
  flex: 1;
  min-width: 100px;
}

.filter-list {
  display: grid;
  gap: 12px;
}

.filter-list label {
  display: grid;
  gap: 5px;
  color: #475569;
  font-size: 12px;
}

.view-row {
  min-height: 22px;
  gap: 10px;
  flex-wrap: wrap;
}

.continuous-switch {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #52647c;
  font-size: 11px;
  cursor: help;
}

.continuous-card {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 2px 10px;
  padding: 10px 12px;
  color: #214b87;
  background: #eaf2ff;
  border: 1px solid #cbdcf8;
  border-radius: 10px;
}

.continuous-card span {
  grid-row: 1 / 3;
  align-self: center;
  font-size: 11px;
}

.continuous-card small {
  color: #6681a8;
}

.participant-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-right: 3px;
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
  min-height: 58px;
  margin-bottom: 6px;
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
  padding: 6px 9px;
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

.pending-drop {
  padding: 8px 10px;
  color: #748094;
  background: #f7f9fc;
  border: 1px dashed #c9d2df;
  border-radius: 8px;
  font-size: 11px;
  text-align: center;
}

.pagination-row {
  min-height: 30px;
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
</style>
