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
        <span>Meeting Seating Workspace</span>
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
            <span class="eyebrow">MEETING WORKSPACE</span>
            <h1>今天要安排哪场会议？</h1>
            <p>
              每场会议拥有独立的人员名单、场馆快照和排座版本，后续接入统一认证后按用户与团队隔离。
            </p>
          </div>
          <div class="hero-actions">
            <el-button
              v-if="recentMeeting"
              type="primary"
              size="large"
              :icon="ArrowRight"
              @click="openMeeting(recentMeeting.id)"
            >
              继续最近会议
            </el-button>
            <el-button size="large" :icon="Plus" @click="router.push('/venues')">
              创建新会议
            </el-button>
          </div>
        </section>

        <section class="home-section">
          <div class="section-heading">
            <div>
              <span class="eyebrow">MY MEETINGS</span>
              <h2>我的会议</h2>
            </div>
            <el-button type="primary" plain :icon="Plus" @click="router.push('/venues')">
              从场馆创建
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
            <p>先从场馆模板创建一场会议，再导入人员并开始排座。</p>
            <el-button type="primary" @click="router.push('/venues')">选择场馆</el-button>
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
  color: rgba(255, 255, 255, 0.72);
  font-size: 10px;
}

.user-avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 50%;
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
  padding: 54px 0 72px;
}

.home-hero {
  min-height: 245px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 48px;
  padding: 48px 52px;
  color: #12325f;
  background:
    radial-gradient(circle at 86% 15%, rgba(96, 165, 250, 0.28), transparent 30%),
    linear-gradient(135deg, #ffffff, #eaf3ff);
  border: 1px solid #cfe1f8;
  border-radius: 24px;
  box-shadow: 0 20px 48px rgba(37, 99, 235, 0.1);
}

.home-hero h1 {
  margin: 10px 0 12px;
  font-size: 34px;
}

.home-hero p {
  max-width: 700px;
  margin: 0;
  color: #5d718e;
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
  border: 1px solid #d9e5f4;
  border-radius: 16px;
  box-shadow: 0 9px 26px rgba(30, 80, 150, 0.07);
  cursor: pointer;
  transition: 0.18s ease;
}

.meeting-card:hover {
  border-color: #87afe4;
  box-shadow: 0 14px 32px rgba(30, 80, 150, 0.13);
  transform: translateY(-2px);
}

.meeting-icon {
  width: 42px;
  height: 42px;
  flex: none;
  display: grid;
  place-items: center;
  color: #2563eb;
  background: #eaf2ff;
  border-radius: 12px;
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
  color: #5f718a;
  font-size: 12px;
}

.meeting-copy > span {
  color: #8a98aa;
  font-size: 10px;
}

.home-empty {
  padding: 56px;
  color: #6b7c93;
  background: #fff;
  border: 1px dashed #b9cce5;
  border-radius: 18px;
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
