<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Calendar, Plus, User } from '@element-plus/icons-vue'
import { currentUser } from '@/auth/session'
import { useWorkspaceStore } from '@/stores/workspace'
const router = useRouter()
const store = useWorkspaceStore()
const recentMeeting = computed(
  () =>
    store.meetings.find((meeting) => meeting.id === store.activeMeetingId) || store.meetings[0],
)
onMounted(() => store.initialize())
function openMeeting(meetingId) {
  store.rememberMeeting(meetingId)
  router.push(`/workbench/${meetingId}`)
}
function formatTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <div class="app-page home-page" v-loading="store.loading">
    <header class="app-header home-header">
      <div class="brand-mark">席</div>
      <div class="brand-copy">
        <strong>会议排座助手</strong>
      </div>
      <span class="header-spacer" />
      <div class="user-context">
        <span class="user-avatar"><User /></span>
        <span
          ><strong>{{ currentUser.name }}</strong
          ><small>{{ currentUser.tenantName }}</small></span
        >
      </div>
    </header>

    <main class="home-scroll">
      <div class="home-content">
        <section class="home-hero">
          <div>
            <h1>今天要安排哪场会议？</h1>
            <p>
              每场会议拥有独立的人员名单、场馆快照和排座版本，后续接入统一认证后按用户与团队隔离。
            </p>
          </div>
          <div class="hero-actions">
            <el-button
              v-if="recentMeeting"
              size="large"
              :icon="ArrowRight"
              @click="openMeeting(recentMeeting.id)"
            >
              继续最近会议
            </el-button>
            <el-button
              type="primary"
              size="large"
              :icon="Plus"
              @click="router.push('/venues/select')"
            >
              开始排座
            </el-button>
          </div>
        </section>

        <section class="home-section">
          <div class="section-heading">
            <div>
              <h2>我的会议</h2>
            </div>
            <el-button plain @click="router.push('/venues')">
              场馆模板
            </el-button>
          </div>

          <div v-if="store.meetings.length" class="meeting-grid">
            <article
              v-for="meeting in store.meetings"
              :key="meeting.id"
              class="meeting-card"
              @click="openMeeting(meeting.id)"
            >
              <div class="meeting-icon"><Calendar /></div>
              <div class="meeting-copy">
                <el-tag size="small" effect="light">{{ meeting.status }}</el-tag>
                <h3>{{ meeting.name }}</h3>
                <p>{{ meeting.layoutName }}</p>
                <span
                  >最近更新：{{ formatTime(meeting.updatedAt) }} · {{ meeting.updatedByName }}</span
                >
              </div>
              <el-button circle text :icon="ArrowRight" aria-label="进入排座工作台" />
            </article>
          </div>

          <div v-else class="home-empty">
            <Calendar />
            <h3>还没有会议</h3>
            <p>选择一个可用场馆模板，创建会议后即可导入人员并排座。</p>
            <el-button type="primary" @click="router.push('/venues/select')">开始排座</el-button>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.home-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.home-header {
  flex: none;
}

.user-context {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-context > span:last-child {
  display: grid;
}

.user-context strong {
  font-size: 13px;
}

.user-context small {
  color: var(--muted);
  font-size: 10px;
}

.user-avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  color: var(--brand);
  background: var(--brand-soft);
  border: 1px solid rgba(10, 89, 247, 0.16);
  border-radius: var(--radius-sm);
}

.user-avatar svg {
  width: 15px;
}

.home-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.home-content {
  width: min(1280px, calc(100% - 64px));
  margin: 0 auto;
  padding: 32px 0 64px;
}

.home-hero {
  min-height: 220px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 48px;
  padding: 48px 52px;
  color: var(--ink);
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow);
}

.home-hero h1 {
  margin: 10px 0 12px;
  font-size: 34px;
}

.home-hero p {
  max-width: 700px;
  margin: 0;
  color: var(--muted);
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  flex: none;
}

.home-section {
  margin-top: 34px;
}

.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: end;
  margin-bottom: 18px;
}

.section-heading h2 {
  margin: 5px 0 0;
  font-size: 23px;
}

.meeting-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.meeting-card {
  min-height: 168px;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 22px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow);
  cursor: pointer;
  transition: 0.18s ease;
}

.meeting-card:hover {
  border-color: var(--line-strong);
  box-shadow: var(--shadow-hover);
}

.meeting-icon {
  width: 42px;
  height: 42px;
  flex: none;
  display: grid;
  place-items: center;
  color: var(--brand);
  background: var(--brand-soft);
  border-radius: var(--radius-sm);
}

.meeting-icon svg {
  width: 20px;
}

.meeting-copy {
  flex: 1;
  min-width: 0;
}

.meeting-copy h3 {
  margin: 10px 0 6px;
  overflow: hidden;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meeting-copy p {
  margin: 0 0 18px;
  color: var(--muted);
  font-size: 12px;
}

.meeting-copy > span {
  color: var(--tertiary);
  font-size: 10px;
}

.home-empty {
  padding: 56px;
  color: var(--muted);
  background: #fff;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-md);
  text-align: center;
}

.home-empty > svg {
  width: 36px;
}

@media (max-width: 1280px) {
  .meeting-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
