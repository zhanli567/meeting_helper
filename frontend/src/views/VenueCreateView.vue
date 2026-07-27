<script setup>
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import VenueInfoForm from '@/components/VenueInfoForm.vue'
import { emptyVenueInfo } from '@/utils/venueModel'

const router = useRouter()
const step = ref('info')
const info = reactive(emptyVenueInfo())
const layout = reactive({ gridRows: 20, gridColumns: 30, elements: [] })
const formRef = ref()
const dirty = ref(false)

watch(info, () => (dirty.value = true), { deep: true })
watch(layout, () => (dirty.value = true), { deep: true })

function updateInfo(value) {
  Object.assign(info, value)
}

async function continueToLayout() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  step.value = 'layout'
}

function back() {
  if (step.value === 'layout') {
    step.value = 'info'
    return
  }
  router.push('/venues')
}

function beforeUnload(event) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))

onBeforeRouteLeave(() => {
  if (!dirty.value) return true
  return window.confirm('当前场馆信息尚未保存，确认离开吗？')
})
</script>

<template>
  <div class="app-page create-page">
    <header class="app-header">
      <el-button text class="back-button" :icon="ArrowLeft" @click="back">
        {{ step === 'info' ? '返回场馆模板' : '返回场馆信息' }}
      </el-button>
      <span class="header-divider" />
      <div class="brand-copy">
        <strong>新建场馆模板</strong>
      </div>
      <div class="step-strip">
        <span :class="{ active: step === 'info' }"><i>1</i>场馆信息</span>
        <b />
        <span :class="{ active: step === 'layout' }"><i>2</i>场馆布局</span>
      </div>
      <span class="header-spacer" />
      <el-button
        v-if="step === 'info'"
        type="primary"
        :icon="ArrowRight"
        @click="continueToLayout"
      >
        继续编辑布局
      </el-button>
    </header>

    <main class="create-main">
      <div v-if="step === 'info'" class="info-scroll">
        <div class="info-shell">
          <div class="page-title">
            <h1>填写场馆信息</h1>
          </div>
          <VenueInfoForm
            ref="formRef"
            :model-value="info"
            @update:model-value="updateInfo"
          />
        </div>
      </div>

      <section v-else class="layout-step">
        <div class="layout-placeholder">
          <strong>{{ info.location }}</strong>
          <span>{{ layout.gridRows }} × {{ layout.gridColumns }} 默认画布</span>
          <el-button :icon="ArrowLeft" @click="step = 'info'">返回修改信息</el-button>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.create-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.back-button {
  color: var(--muted);
}

.step-strip {
  position: absolute;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 12px;
  transform: translateX(-50%);
}

.step-strip span {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--tertiary);
  font-size: 12px;
  font-weight: 600;
}

.step-strip i {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  background: #eef2f7;
  border-radius: 50%;
  font-style: normal;
}

.step-strip span.active {
  color: var(--brand);
}

.step-strip span.active i {
  color: #fff;
  background: var(--brand);
}

.step-strip b {
  width: 42px;
  height: 1px;
  background: var(--line-strong);
}

.create-main {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.info-scroll {
  height: 100%;
  overflow-y: auto;
}

.info-shell {
  width: min(960px, calc(100% - 64px));
  margin: 0 auto;
  padding: 28px 0 48px;
}

.page-title {
  margin-bottom: 18px;
}

.page-title h1 {
  margin: 0;
  font-size: 26px;
}

.layout-step {
  height: 100%;
  display: grid;
  place-items: center;
  padding: 32px;
}

.layout-placeholder {
  min-width: 360px;
  display: grid;
  justify-items: center;
  gap: 14px;
  padding: 42px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow);
}

.layout-placeholder strong {
  font-size: 20px;
}

.layout-placeholder span {
  color: var(--muted);
  font-size: 13px;
}
</style>
