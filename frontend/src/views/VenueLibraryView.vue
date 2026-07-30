<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  House,
  Plus,
  Search,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import VenueDetailDrawer from '@/components/VenueDetailDrawer.vue'
import VenueInfoDrawer from '@/components/VenueInfoDrawer.vue'
import VenuePreviewDialog from '@/components/VenuePreviewDialog.vue'
import { meetingApi } from '@/api/meeting'
import { venueApi } from '@/api/venue'
import { apiErrorMessage } from '@/api/http'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute()
const router = useRouter()
const store = useWorkspaceStore()
const isSelectMode = computed(() => route.name === 'venue-select')
const loading = ref(false)
const records = ref([])
const total = ref(0)
const campusOptions = ref([])
const query = reactive({
  keyword: '',
  campus: '',
  pageNum: 1,
  pageSize: 10,
  groupByCampus: false,
})
const pageSizes = [10, 20, 50]
const detailVisible = ref(false)
const infoVisible = ref(false)
const detailLoading = ref(false)
const selectedVenue = ref()
const previewVisible = ref(false)
const previewVenueId = ref('')
const meetingVisible = ref(false)
const submitting = ref(false)
const meetingForm = reactive({ name: '', venueTemplateId: '' })
const venueRowActions = ref({})
let searchTimer
let latestLoadId = 0

const tableRows = computed(() => {
  if (!query.groupByCampus) return records.value
  const groups = new Map()
  for (const venue of records.value) {
    const label = venue.campus?.trim() || '未填写园区'
    if (!groups.has(label)) groups.set(label, [])
    groups.get(label).push(venue)
  }
  return [...groups.entries()].flatMap(([campus, venues]) => [
    { id: `group:${campus}`, __group: true, campus },
    ...venues,
  ])
})

onMounted(load)
onBeforeUnmount(() => window.clearTimeout(searchTimer))

watch(
  () => query.keyword,
  () => {
    window.clearTimeout(searchTimer)
    searchTimer = window.setTimeout(() => {
      query.pageNum = 1
      load()
    }, 300)
  },
)

watch(
  () => query.campus,
  () => {
    query.pageNum = 1
    load()
  },
)

watch(
  () => route.name,
  () => {
    meetingVisible.value = false
  },
)

async function load() {
  const loadId = ++latestLoadId
  loading.value = true
  try {
    const page = await venueApi.list({
      keyword: query.keyword.trim(),
      campus: query.campus,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    })
    if (loadId !== latestLoadId) return
    records.value = page.records || []
    total.value = page.total || 0
    query.pageNum = page.pageNum || query.pageNum
    query.pageSize = page.pageSize || query.pageSize
    if (!query.campus) {
      campusOptions.value = [
        ...new Set(records.value.map((venue) => venue.campus?.trim()).filter(Boolean)),
      ].sort((left, right) => left.localeCompare(right, 'zh-CN'))
    }
  } catch (error) {
    if (loadId !== latestLoadId) return
    ElMessage.error(apiErrorMessage(error))
  } finally {
    if (loadId === latestLoadId) loading.value = false
  }
}

function changePage(pageNum) {
  query.pageNum = pageNum
  load()
}

function changePageSize(pageSize) {
  query.pageSize = pageSize
  query.pageNum = 1
  load()
}

function groupSpan({ row, columnIndex }) {
  if (!row.__group) return undefined
  return columnIndex === 0 ? [1, 7] : [0, 0]
}

function rowClassName({ row }) {
  return row.__group ? 'campus-group-row' : ''
}

function venueRowAction(venueId) {
  return venueRowActions.value[venueId] || ''
}

async function runVenueRowAction(venueId, action, task) {
  if (!venueId || venueRowAction(venueId)) return undefined
  venueRowActions.value = {
    ...venueRowActions.value,
    [venueId]: action,
  }
  try {
    return await task()
  } finally {
    const nextActions = { ...venueRowActions.value }
    delete nextActions[venueId]
    venueRowActions.value = nextActions
  }
}

async function loadDetail(venue, mode) {
  await runVenueRowAction(venue.id, mode === 'edit' ? 'edit' : 'detail', async () => {
    selectedVenue.value = venue
    detailLoading.value = true
    if (mode === 'detail') detailVisible.value = true
    try {
      selectedVenue.value = await venueApi.detail(venue.id)
      if (mode === 'edit') infoVisible.value = true
    } catch (error) {
      if (mode === 'detail') detailVisible.value = false
      ElMessage.error(apiErrorMessage(error))
    } finally {
      detailLoading.value = false
    }
  })
}

function previewVenue(venue) {
  previewVenueId.value = venue.id
  previewVisible.value = true
}

function startMeeting(venue) {
  if (venue.seatCount === 0) return
  if (venueRowAction(venue.id)) return
  meetingForm.name = ''
  meetingForm.venueTemplateId = venue.id
  meetingVisible.value = true
}

async function createMeeting() {
  if (submitting.value) return
  const name = meetingForm.name.trim()
  if (!name) {
    ElMessage.warning('请输入会议名称')
    return
  }
  submitting.value = true
  try {
    await runVenueRowAction(meetingForm.venueTemplateId, 'meeting', async () => {
      const meeting = await meetingApi.createMeeting(name, meetingForm.venueTemplateId)
      const meetingId = typeof meeting === 'string' ? meeting : meeting.id
      store.rememberMeeting(meetingId)
      await store.initialize()
      ElMessage.success('会议已创建')
      meetingVisible.value = false
      router.push(`/workbench/${meetingId}`)
    })
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    submitting.value = false
  }
}

async function deleteVenue(venue) {
  await runVenueRowAction(venue.id, 'delete', async () => {
    try {
      await ElMessageBox.confirm(
        `删除“${venue.location}”后无法恢复，已有会议不受影响。`,
        '确认删除场馆模板',
        {
          confirmButtonText: '确认删除',
          cancelButtonText: '取消',
          type: 'warning',
        },
      )
      await venueApi.remove(venue.id)
      if (records.value.length === 1 && query.pageNum > 1) query.pageNum -= 1
      ElMessage.success('场馆模板已删除')
      await load()
    } catch (error) {
      if (error === 'cancel' || error === 'close') return
      ElMessage.error(apiErrorMessage(error))
    }
  })
}

async function handleSaved(updated) {
  selectedVenue.value = updated
  await load()
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()} ${hours}:${minutes}`
}

function displayText(value) {
  const text = String(value ?? '').trim()
  if (!text) return '-'
  return text
}
</script>

<template>
  <div class="app-page venue-page">
    <header class="app-header">
      <el-button text class="header-home header-home-left" :icon="House" @click="router.push('/')">
        首页
      </el-button>
      <span class="header-divider" />
      <div class="brand-copy">
        <strong>{{ isSelectMode ? '选择场馆模板' : '场馆模板管理' }}</strong>
      </div>
      <span class="header-spacer" />
      <el-button
        v-if="!isSelectMode"
        type="primary"
        :icon="Plus"
        @click="router.push('/venues/new')"
      >
        新建场馆
      </el-button>
    </header>

    <main class="venue-content">
      <section class="content-heading">
        <h1>{{ isSelectMode ? '选择场馆，开始排座' : '场馆模板' }}</h1>
        <span>{{ total }} 个模板</span>
      </section>

      <section class="venue-panel">
        <div class="venue-toolbar">
          <el-input
            v-model="query.keyword"
            :prefix-icon="Search"
            clearable
            class="search-input"
          />
          <el-select v-model="query.campus" clearable class="campus-select">
            <el-option
              v-for="campus in campusOptions"
              :key="campus"
              :label="campus"
              :value="campus"
            />
          </el-select>
          <span class="toolbar-spacer" />
          <el-switch v-model="query.groupByCampus" active-text="按园区分组" />
        </div>

        <div class="table-scroll" v-loading="loading">
          <el-table
            :data="tableRows"
            height="100%"
            :fit="true"
            class="venue-table"
            :span-method="groupSpan"
            :row-class-name="rowClassName"
            empty-text="暂无场馆模板"
          >
            <el-table-column prop="location" label="地点" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                <strong v-if="row.__group" class="group-title">{{ displayText(row.campus) }}</strong>
                <button
                  v-else
                  class="location-link cell-ellipsis"
                  :title="displayText(row.location)"
                  @click="loadDetail(row, 'detail')"
                >
                  {{ displayText(row.location) }}
                </button>
              </template>
            </el-table-column>
            <el-table-column prop="campus" label="园区" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="cell-ellipsis" :title="displayText(row.campus)">
                  {{ displayText(row.campus) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="manualCapacity" label="容纳人数" width="110" align="right">
              <template #default="{ row }">{{ displayText(row.manualCapacity) }}</template>
            </el-table-column>
            <el-table-column prop="seatCount" label="布局座位数" width="130" align="right">
              <template #default="{ row }">
                <el-tag v-if="row.seatCount === 0" type="warning" effect="plain">
                  布局未完成
                </el-tag>
                <span v-else>{{ displayText(row.seatCount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="updatedByName" label="更新人" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="cell-ellipsis" :title="displayText(row.updatedByName)">
                  {{ displayText(row.updatedByName) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" min-width="170">
              <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="420" fixed="right">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-tooltip
                    :disabled="row.seatCount !== 0"
                    content="请先完成场馆布局"
                    placement="top"
                  >
                    <span>
                      <el-button
                        link
                        size="small"
                        :loading="venueRowAction(row.id) === 'meeting'"
                        :disabled="row.seatCount === 0"
                        @click="startMeeting(row)"
                      >
                        使用模板
                      </el-button>
                    </span>
                  </el-tooltip>
                  <el-button
                    link
                    size="small"
                    :loading="venueRowAction(row.id) === 'detail'"
                    :disabled="Boolean(venueRowAction(row.id))"
                    @click="loadDetail(row, 'detail')"
                  >
                    详情
                  </el-button>
                  <el-button
                    link
                    size="small"
                    :disabled="Boolean(venueRowAction(row.id))"
                    @click="previewVenue(row)"
                  >
                    预览
                  </el-button>
                  <template v-if="!isSelectMode">
                    <el-button
                      link
                      size="small"
                      :loading="venueRowAction(row.id) === 'edit'"
                      :disabled="Boolean(venueRowAction(row.id))"
                      @click="loadDetail(row, 'edit')"
                    >
                      编辑信息
                    </el-button>
                    <el-button
                      link
                      size="small"
                      :disabled="Boolean(venueRowAction(row.id))"
                      @click="router.push(`/venues/${row.id}/layout/edit`)"
                    >
                      编辑布局
                    </el-button>
                    <el-button
                      link
                      size="small"
                      class="danger-action"
                      :loading="venueRowAction(row.id) === 'delete'"
                      :disabled="Boolean(venueRowAction(row.id))"
                      @click="deleteVenue(row)"
                    >
                      删除
                    </el-button>
                  </template>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <footer class="pagination-bar">
          <el-pagination
            :current-page="query.pageNum"
            :page-size="query.pageSize"
            :page-sizes="pageSizes"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="changePage"
            @size-change="changePageSize"
          />
        </footer>
      </section>
    </main>

    <VenueDetailDrawer
      v-model="detailVisible"
      :venue="selectedVenue"
      :loading="detailLoading"
    />
    <VenueInfoDrawer
      v-model="infoVisible"
      :venue="selectedVenue"
      @saved="handleSaved"
    />
    <VenuePreviewDialog v-model="previewVisible" :venue-id="previewVenueId" />

    <el-dialog v-model="meetingVisible" title="创建会议" width="440px">
      <el-form label-position="top" @submit.prevent="createMeeting">
        <el-form-item label="会议名称" required>
          <el-input
            v-model="meetingForm.name"
            maxlength="200"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="meetingVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="createMeeting">
          创建并进入工作台
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.venue-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header-home {
  color: #4b5563;
  font-weight: 650;
}

.header-home:hover {
  color: var(--brand);
  background: var(--brand-soft);
}

.header-home-left {
  flex: none;
}

.venue-content {
  width: min(1460px, calc(100% - 64px));
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  margin: 0 auto;
  padding: 26px 0 32px;
  overflow: hidden;
}

.content-heading {
  flex: none;
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.content-heading h1 {
  margin: 0;
  font-size: 26px;
}

.content-heading span {
  color: var(--tertiary);
  font-size: 12px;
}

.venue-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow);
}

.venue-toolbar {
  min-height: 68px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
}

.search-input {
  width: 290px;
}

.campus-select {
  width: 180px;
}

.toolbar-spacer {
  flex: 1;
}

.table-scroll {
  flex: 1;
  min-height: 0;
}

.venue-table {
  width: 100%;
}

.location-link {
  width: 100%;
  padding: 0;
  color: var(--brand);
  background: transparent;
  border: 0;
  cursor: pointer;
  font-weight: 650;
  text-align: left;
}

.group-title {
  color: var(--ink);
  font-size: 13px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.row-actions :deep(.el-button) {
  margin-left: 0;
  padding: 0;
  color: var(--brand);
  font-size: 12px;
}

.danger-action {
  color: var(--danger);
}

.row-actions :deep(.danger-action) {
  color: var(--danger);
}

.cell-ellipsis {
  min-width: 0;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination-bar {
  min-height: 62px;
  flex: none;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 10px 16px;
  border-top: 1px solid var(--line);
}

:deep(.campus-group-row td.el-table__cell) {
  background: #f5f8fc;
}

:deep(.campus-group-row:hover > td.el-table__cell) {
  background: #f5f8fc !important;
}
</style>
