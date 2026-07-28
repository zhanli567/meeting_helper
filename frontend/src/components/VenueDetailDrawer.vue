<script setup>

const visible = defineModel({ required: true })

const props = defineProps({
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
</script>

<template>
  <el-drawer v-model="visible" title="场馆详情" size="620px" destroy-on-close>
    <div v-loading="loading" class="detail-content">
      <template v-if="venue">
        <section>
          <h3>场馆信息</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="地点">{{ display(venue.location) }}</el-descriptions-item>
            <el-descriptions-item label="园区">{{ display(venue.campus) }}</el-descriptions-item>
            <el-descriptions-item label="容纳人数">
              {{ display(venue.manualCapacity) }}
            </el-descriptions-item>
            <el-descriptions-item label="接口人">
              {{ display(venue.contactInfo) }}
            </el-descriptions-item>
            <el-descriptions-item label="备注">
              <span class="multiline">{{ display(venue.remarks) }}</span>
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
