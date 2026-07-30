<script setup>
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { venueApi } from '@/api/venue'
import { apiErrorMessage } from '@/api/http'
import VenueInfoForm from '@/components/VenueInfoForm.vue'
import VenueLayoutEditor from '@/components/VenueLayoutEditor.vue'
import { emptyVenueInfo, toCreateVenuePayload } from '@/utils/venueModel'

const router = useRouter()
const step = ref('info')
const info = reactive(emptyVenueInfo())
const layout = reactive({ gridRows: 20, gridColumns: 30, elements: [] })
const formRef = ref()
const dirty = ref(false)
const saving = ref(false)
const validatingInfo = ref(false)

watch(info, () => (dirty.value = true), { deep: true })
watch(layout, () => (dirty.value = true), { deep: true })

function updateInfo(value) {
  Object.assign(info, value)
}

function updateLayout(value) {
  Object.assign(layout, value)
}

async function continueToLayout() {
  if (validatingInfo.value) return
  validatingInfo.value = true
  try {
    await formRef.value?.validate()
  } catch {
    return
  } finally {
    validatingInfo.value = false
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

async function saveVenue() {
  if (saving.value) return
  saving.value = true
  try {
    await venueApi.create(toCreateVenuePayload(info, layout))
    dirty.value = false
    ElMessage.success('场馆模板已创建')
    await router.push('/venues')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    saving.value = false
  }
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
    <header v-if="step === 'info'" class="app-header">
      <el-button text class="back-button" :icon="ArrowLeft" @click="back">
        返回场馆模板
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
        type="primary"
        :icon="ArrowRight"
        :loading="validatingInfo"
        :disabled="saving"
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
            :disabled="validatingInfo"
            @update:model-value="updateInfo"
          />
        </div>
      </div>

      <section v-else class="layout-step">
        <VenueLayoutEditor
          :model-value="layout"
          :venue-name="info.location"
          :venue-description="info.description || ''"
          :manual-capacity="info.manualCapacity"
          title="新建场馆布局"
          save-label="创建场馆模板"
          :saving="saving"
          @update:model-value="updateLayout"
          @back="step = 'info'"
          @save="saveVenue"
        />
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
  min-height: 0;
  overflow: hidden;
}
</style>
