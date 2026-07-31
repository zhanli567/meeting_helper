<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { venueApi } from '@/api/venue'
import { apiErrorMessage } from '@/api/http'
import VenueLayoutEditor from '@/components/VenueLayoutEditor.vue'
import { DEFAULT_CANVAS, toElementPayload } from '@/utils/venueModel'

const route = useRoute()
const router = useRouter()
const venue = ref()
const layout = reactive({
  gridRows: DEFAULT_CANVAS.rows,
  gridColumns: DEFAULT_CANVAS.columns,
  elements: [],
})
const rowVersion = ref(0)
const loading = ref(true)
const loadFailed = ref(false)
const saving = ref(false)
const dirty = ref(false)
let loadingVenuePromise

function updateLayout(value) {
  Object.assign(layout, value)
  dirty.value = true
}

async function loadVenue() {
  if (loadingVenuePromise) {
    return loadingVenuePromise
  }
  loading.value = true
  loadFailed.value = false
  loadingVenuePromise = (async () => {
    try {
      const [detail, savedLayout] = await Promise.all([
        venueApi.detail(route.params.venueId),
        venueApi.layout(route.params.venueId),
      ])
      venue.value = detail
      Object.assign(layout, {
        gridRows: savedLayout.gridRows,
        gridColumns: savedLayout.gridColumns,
        elements: savedLayout.elements || [],
      })
      rowVersion.value = savedLayout.rowVersion
      dirty.value = false
    } catch (error) {
      loadFailed.value = true
      ElMessage.error(apiErrorMessage(error))
    } finally {
      loading.value = false
      loadingVenuePromise = undefined
    }
  })()
  return loadingVenuePromise
}

async function saveLayout() {
  if (saving.value || loading.value || loadFailed.value) {
    return
  }
  saving.value = true
  try {
    const saved = await venueApi.updateLayout(route.params.venueId, {
      gridRows: layout.gridRows,
      gridColumns: layout.gridColumns,
      elements: layout.elements.map(toElementPayload),
      rowVersion: rowVersion.value,
    })
    if (saved?.rowVersion != null) {
      rowVersion.value = saved.rowVersion
    }
    dirty.value = false
    ElMessage.success('场馆布局已保存')
  } catch (error) {
    const status = error?.response?.status
    if (status === 409) {
      ElMessage.error(
        error?.response?.data?.msg ||
          '该场馆已被其他人更新，本地布局仍保留，请刷新后再处理',
      )
      return
    }
    ElMessage.error(apiErrorMessage(error))
  } finally {
    saving.value = false
  }
}

function beforeUnload(event) {
  if (!dirty.value) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  loadVenue()
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))

onBeforeRouteLeave(() => {
  if (!dirty.value) {
    return true
  }
  return window.confirm('当前场馆布局尚未保存，确认离开吗？')
})
</script>

<template>
  <div v-loading="loading || saving" class="app-page layout-editor-view">
    <VenueLayoutEditor
      v-if="!loading && !loadFailed"
      :model-value="layout"
      :venue-name="venue?.location || '场馆模板'"
      :venue-description="venue?.description || ''"
      :manual-capacity="venue?.manualCapacity"
      title="编辑场馆布局"
      :saving="saving"
      @update:model-value="updateLayout"
      @back="router.push('/venues')"
      @save="saveLayout"
    />
    <div v-else-if="loadFailed" class="load-failed">
      <strong>场馆布局加载失败</strong>
      <div>
        <el-button @click="router.push('/venues')">返回场馆模板</el-button>
        <el-button type="primary" :loading="loading" @click="loadVenue">重新加载</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.layout-editor-view {
  height: 100vh;
  overflow: hidden;
}

.load-failed {
  height: 100%;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 18px;
}
</style>
