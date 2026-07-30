<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import VenueInfoForm from '@/components/VenueInfoForm.vue'
import { venueApi } from '@/api/venue'
import { apiErrorMessage } from '@/api/http'
import { emptyVenueInfo, normalizeVenueInfo } from '@/utils/venueModel'

const visible = defineModel({ required: true })
const props = defineProps({
  venue: {
    type: Object,
    default: undefined,
  },
})
const emit = defineEmits(['saved'])

const formRef = ref()
const form = reactive(emptyVenueInfo())
const submitting = ref(false)

watch(
  () => [visible.value, props.venue],
  ([isVisible, venue]) => {
    if (isVisible && venue) Object.assign(form, emptyVenueInfo(), normalizeVenueInfo(venue))
  },
  { immediate: true },
)

async function submit() {
  if (!props.venue || submitting.value) return
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const updated = await venueApi.updateInfo(props.venue.id, {
      ...normalizeVenueInfo(form),
      rowVersion: props.venue.rowVersion,
    })
    ElMessage.success('场馆信息已更新')
    emit('saved', updated)
    visible.value = false
  } catch (error) {
    const message = apiErrorMessage(error)
    ElMessage.error(
      /已存在|重复|unique|duplicate/i.test(message) ? '该地点已存在场馆模板' : message,
    )
  } finally {
    submitting.value = false
  }
}

function updateForm(value) {
  Object.assign(form, value)
}
</script>

<template>
  <el-drawer
    v-model="visible"
    :title="venue ? `编辑信息：${venue.location}` : '编辑场馆信息'"
    size="720px"
    destroy-on-close
  >
    <div class="drawer-form-scroll">
      <VenueInfoForm
        ref="formRef"
        :model-value="form"
        :exclude-venue-id="venue?.id || ''"
        :disabled="submitting"
        @update:model-value="updateForm"
      />
    </div>
    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存信息</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.drawer-form-scroll {
  height: 100%;
  overflow-y: auto;
  padding-right: 6px;
}
</style>
