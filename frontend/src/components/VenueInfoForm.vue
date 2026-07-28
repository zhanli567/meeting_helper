<script setup>
import { reactive, ref, watch } from 'vue'
import { venueApi } from '@/api/venue'
import { emptyVenueInfo, normalizeVenueInfo } from '@/utils/venueModel'

const props = defineProps({
  modelValue: {
    type: Object,
    required: true,
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  excludeVenueId: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['update:modelValue'])

const formRef = ref()
const form = reactive(emptyVenueInfo())

async function validateLocationAvailability(_rule, value, callback) {
  const location = String(value ?? '').trim()
  if (!location || location.length > 200) {
    callback()
    return
  }
  try {
    const result = await venueApi.locationAvailability(location, props.excludeVenueId || undefined)
    callback(result.available ? undefined : new Error('该地点已存在场馆模板'))
  } catch {
    callback(new Error('地点可用性校验失败，请稍后重试'))
  }
}

const rules = {
  location: [
    { required: true, message: '请输入地点', trigger: ['blur', 'change'] },
    { max: 200, message: '地点不能超过 200 个字符', trigger: 'blur' },
    { validator: validateLocationAvailability, trigger: 'blur' },
  ],
  campus: [{ max: 120, message: '园区不能超过 120 个字符', trigger: 'blur' }],
  contactInfo: [{ max: 500, message: '接口人不能超过 500 个字符', trigger: 'blur' }],
}

watch(
  () => props.modelValue,
  (value) => Object.assign(form, emptyVenueInfo(), value || {}),
  { immediate: true, deep: true },
)

watch(
  form,
  () => emit('update:modelValue', normalizeVenueInfo(form)),
  { deep: true },
)

defineExpose({
  validate: () => formRef.value?.validate(),
})
</script>

<template>
  <el-form
    ref="formRef"
    :model="form"
    :rules="rules"
    :disabled="disabled"
    label-position="top"
    class="venue-info-form"
  >
    <section class="form-section">
      <h3>基本信息</h3>
      <div class="form-grid">
        <el-form-item label="地点" prop="location">
          <el-input
            v-model="form.location"
            maxlength="200"
            placeholder="例如：A 座 201 会议室"
          />
        </el-form-item>
        <el-form-item label="园区" prop="campus">
          <el-input v-model="form.campus" maxlength="120" placeholder="例如：科技园区" />
        </el-form-item>
        <el-form-item label="容纳人数">
          <el-input-number
            v-model="form.manualCapacity"
            :min="0"
            :precision="0"
            controls-position="right"
            placeholder="选填"
          />
        </el-form-item>
        <el-form-item label="接口人" prop="contactInfo">
          <el-input v-model="form.contactInfo" maxlength="500" placeholder="姓名或联系方式" />
        </el-form-item>
        <el-form-item label="备注" class="form-grid-wide">
          <el-input
            v-model="form.remarks"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
      </div>
    </section>
  </el-form>
</template>

<style scoped>
.venue-info-form {
  display: grid;
  gap: 18px;
}

.form-section {
  padding: 18px 20px 4px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
}

.form-section h3 {
  margin: 0 0 16px;
  color: var(--ink);
  font-size: 15px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 20px;
}

.form-grid-wide {
  grid-column: 1 / -1;
}

.form-grid :deep(.el-input-number) {
  width: 100%;
}

.form-grid :deep(.el-form-item__label) {
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
}
</style>
