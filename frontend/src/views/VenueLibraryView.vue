<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Delete, EditPen, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { meetingApi } from '@/api/meeting'
import { apiErrorMessage } from '@/api/http'
import type { VenueSummary } from '@/types/workspace'
import { useWorkspaceStore } from '@/stores/workspace'

const router = useRouter()
const store = useWorkspaceStore()
const venues = ref<VenueSummary[]>([])
const meetingNames = ref<string[]>([])
const loading = ref(false)
const meetingVisible = ref(false)
const submitting = ref(false)
const form = reactive({ name: '', venueTemplateId: '' })

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [venueList, meetings] = await Promise.all([meetingApi.venues(), meetingApi.meetings()])
    venues.value = venueList
    meetingNames.value = meetings.map((meeting) => meeting.name.trim().toLocaleLowerCase())
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    loading.value = false
  }
}

function startMeeting(venue: VenueSummary) {
  form.venueTemplateId = venue.id
  form.name = ''
  meetingVisible.value = true
}

async function createMeeting() {
  const name = form.name.trim()
  if (!name) {
    ElMessage.warning('请输入会议名称')
    return
  }
  if (meetingNames.value.includes(name.toLocaleLowerCase())) {
    ElMessage.warning('会议名称已存在，请换一个名称')
    return
  }
  submitting.value = true
  try {
    const meeting = await meetingApi.createMeeting(name, form.venueTemplateId)
    store.activeMeetingId = meeting.id
    await store.initialize()
    ElMessage.success('会议已创建，场馆布局已生成独立快照')
    router.push(`/workbench/${meeting.id}`)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    submitting.value = false
  }
}

async function deleteVenue(venue: VenueSummary) {
  try {
    await ElMessageBox.confirm(
      `删除“${venue.name}”后不可再用它创建新会议，已创建会议不会受影响。`,
      '删除自定义场馆',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    await meetingApi.deleteVenue(venue.id)
    ElMessage.success('场馆已删除')
    await load()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error))
  }
}
</script>

<template>
  <div class="app-page venue-page">
    <header class="app-header">
      <el-button text class="back-button" :icon="ArrowLeft" @click="router.push('/')">
        返回首页
      </el-button>
      <span class="header-divider" />
      <div class="brand-copy">
        <strong>场馆模板库</strong>
        <span>选择预置场馆，或创建可复用的自定义布局</span>
      </div>
      <span class="header-spacer" />
      <el-button type="primary" :icon="Plus" @click="router.push('/venues/new')">
        新建场馆
      </el-button>
    </header>

    <main v-loading="loading" class="venue-content">
      <section class="venue-intro">
        <div>
          <span class="eyebrow">REUSABLE VENUE TEMPLATES</span>
          <h1>从真实空间开始安排会议</h1>
          <p>模板保存物理布局；创建会议后会复制为独立快照，临时设备和禁用座位不会修改原场馆。</p>
        </div>
        <div class="venue-count">
          <strong>{{ venues.length }}</strong
          ><span>个可用模板</span>
        </div>
      </section>

      <div class="venue-scroll">
        <section class="venue-grid">
          <article v-for="venue in venues" :key="venue.id" class="venue-card">
            <div class="venue-preview">
              <span class="mini-stage">舞台 / 主席区</span>
              <div class="mini-seats">
                <i v-for="index in 28" :key="index" :class="{ aisle: index % 9 === 0 }" />
              </div>
              <el-tag v-if="venue.preset" size="small" type="primary" effect="dark"
                >系统预置</el-tag
              >
              <el-tag v-else size="small" effect="dark">自定义</el-tag>
            </div>
            <div class="venue-card-body">
              <div>
                <h2>{{ venue.name }}</h2>
                <p>{{ venue.description || '未填写场馆说明' }}</p>
              </div>
              <dl>
                <div>
                  <dt>网格</dt>
                  <dd>{{ venue.gridRows }} × {{ venue.gridColumns }}</dd>
                </div>
                <div>
                  <dt>座席</dt>
                  <dd>{{ venue.seatCount }}</dd>
                </div>
                <div>
                  <dt>版本</dt>
                  <dd>V{{ venue.versionNo }}</dd>
                </div>
              </dl>
              <div class="venue-actions">
                <el-button type="primary" plain @click="startMeeting(venue)">
                  使用该场馆创建会议
                </el-button>
                <template v-if="!venue.preset">
                  <el-button
                    :icon="EditPen"
                    aria-label="编辑场馆"
                    @click="router.push(`/venues/${venue.id}/edit`)"
                  />
                  <el-button
                    type="danger"
                    plain
                    :icon="Delete"
                    aria-label="删除场馆"
                    @click="deleteVenue(venue)"
                  />
                </template>
              </div>
            </div>
          </article>

          <button class="create-card" @click="router.push('/venues/new')">
            <span><Plus /></span>
            <strong>设计新的场馆模板</strong>
            <small>设置网格大小，绘制舞台、座位、走廊、墙和桌子</small>
          </button>
        </section>
      </div>
    </main>

    <el-dialog v-model="meetingVisible" title="使用场馆创建会议" width="460px">
      <el-form label-position="top">
        <el-form-item label="会议名称" required>
          <el-input v-model="form.name" placeholder="例如：2026年度表彰大会" />
        </el-form-item>
        <el-alert
          title="创建后会生成独立场馆快照，后续调整不会影响模板。"
          type="info"
          :closable="false"
          show-icon
        />
      </el-form>
      <template #footer>
        <el-button @click="meetingVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="createMeeting"
          >创建并进入排座</el-button
        >
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

.back-button {
  color: rgba(255, 255, 255, 0.82);
}

.venue-content {
  width: min(1380px, calc(100% - 64px));
  flex: 1;
  min-height: 0;
  margin: 0 auto;
  padding: 38px 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.venue-intro {
  flex: none;
  display: flex;
  justify-content: space-between;
  align-items: end;
  margin-bottom: 28px;
}

.venue-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 12px 64px 0;
  scrollbar-gutter: stable;
}

.venue-intro h1 {
  margin: 6px 0 8px;
  font-size: 28px;
}

.venue-intro p {
  max-width: 720px;
  margin: 0;
  color: #667085;
}

.venue-count {
  display: grid;
  text-align: right;
}

.venue-count strong {
  font-size: 28px;
}

.venue-count span {
  color: #718096;
  font-size: 12px;
}

.venue-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

.venue-card,
.create-card {
  min-height: 390px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #dde4ed;
  border-radius: 16px;
  box-shadow: var(--shadow);
}

.venue-preview {
  height: 185px;
  position: relative;
  display: grid;
  align-content: center;
  gap: 20px;
  padding: 28px 34px;
  background:
    linear-gradient(rgba(255, 255, 255, 0.7), rgba(255, 255, 255, 0.7)),
    linear-gradient(#dce4ef 1px, transparent 1px),
    linear-gradient(90deg, #dce4ef 1px, transparent 1px), #eef3f8;
  background-size:
    auto,
    18px 18px,
    18px 18px,
    auto;
}

.venue-preview > .el-tag {
  position: absolute;
  top: 12px;
  right: 12px;
}

.mini-stage {
  padding: 9px;
  color: #fff;
  background: #293a55;
  border-radius: 0 0 8px 8px;
  font-size: 10px;
  text-align: center;
}

.mini-seats {
  display: grid;
  grid-template-columns: repeat(9, 1fr);
  gap: 5px;
}

.mini-seats i {
  height: 11px;
  background: #8ca7ce;
  border-radius: 2px;
}

.mini-seats i.aisle {
  visibility: hidden;
}

.venue-card-body {
  display: grid;
  gap: 16px;
  padding: 20px;
}

.venue-card-body h2 {
  margin: 0 0 6px;
  font-size: 18px;
}

.venue-card-body p {
  height: 38px;
  margin: 0;
  overflow: hidden;
  color: #718096;
  font-size: 12px;
}

.venue-card-body dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin: 0;
}

.venue-actions {
  display: flex;
  gap: 8px;
}

.venue-actions > .el-button:first-child {
  flex: 1;
}

.venue-actions > .el-button {
  margin-left: 0;
}

.venue-card-body dl div {
  display: grid;
  gap: 4px;
  padding: 8px;
  border-left: 1px solid #e6ebf1;
}

.venue-card-body dl div:first-child {
  border-left: 0;
}

.venue-card-body dt {
  color: #8a94a5;
  font-size: 10px;
}

.venue-card-body dd {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
}

.create-card {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  padding: 40px;
  color: #53657c;
  border-style: dashed;
  cursor: pointer;
}

.create-card:hover {
  color: #285596;
  border-color: #6b8bbb;
}

.create-card > span {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  color: #2f5f9f;
  background: #eaf1fb;
  border-radius: 16px;
}

.create-card svg {
  width: 24px;
}

.create-card small {
  max-width: 250px;
  line-height: 1.6;
  text-align: center;
}
</style>
