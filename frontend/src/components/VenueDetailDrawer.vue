<script setup>
const visible = defineModel({ required: true })

defineProps({
  venue: {
    type: Object,
    default: undefined,
  },
  loading: {
    type: Boolean,
    default: false,
  },
})

function display(value) {
  return value === null || value === undefined || value === '' ? '未填写' : value
}

function formatTime(value) {
  if (!value) return '未填写'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <el-drawer v-model="visible" title="场馆详情" size="620px" destroy-on-close>
    <div v-loading="loading" class="detail-content">
      <template v-if="venue">
        <section>
          <h3>基本信息</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="地点">{{ display(venue.location) }}</el-descriptions-item>
            <el-descriptions-item label="园区">{{ display(venue.campus) }}</el-descriptions-item>
            <el-descriptions-item label="容纳人数">
              {{ display(venue.manualCapacity) }}
            </el-descriptions-item>
            <el-descriptions-item label="布局座位数">
              {{ venue.seatCount || 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="接口人">
              {{ display(venue.contactInfo) }}
            </el-descriptions-item>
            <el-descriptions-item label="预定链接">
              <el-link
                v-if="venue.bookingUrl"
                :href="venue.bookingUrl"
                target="_blank"
                type="primary"
                :underline="false"
              >
                打开链接
              </el-link>
              <span v-else>未填写</span>
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <section>
          <h3>设备信息</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="主屏分辨率">
              {{ display(venue.mainScreenResolution) }}
            </el-descriptions-item>
            <el-descriptions-item label="舞台尺寸">
              {{ display(venue.stageDimensions) }}
            </el-descriptions-item>
            <el-descriptions-item label="会议室功能" :span="2">
              <span class="multiline">{{ display(venue.meetingRoomFunctions) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="服务提供" :span="2">
              <span class="multiline">{{ display(venue.servicesProvided) }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <section>
          <h3>补充信息</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="说明">
              <span class="multiline">{{ display(venue.description) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="备注">
              <span class="multiline">{{ display(venue.remarks) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="创建">
              {{ venue.createdByName }} · {{ formatTime(venue.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="最近更新">
              {{ venue.updatedByName }} · {{ formatTime(venue.updatedAt) }}
            </el-descriptions-item>
          </el-descriptions>
        </section>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.detail-content {
  min-height: 180px;
  display: grid;
  align-content: start;
  gap: 22px;
}

.detail-content h3 {
  margin: 0 0 10px;
  color: var(--ink);
  font-size: 14px;
}

.multiline {
  white-space: pre-wrap;
}
</style>
