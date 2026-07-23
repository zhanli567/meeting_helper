<script setup lang="ts">
import { Delete, Lock, Unlock } from '@element-plus/icons-vue'
import type { LayoutElement, Participant } from '@/types/workspace'

defineProps<{
  participant?: Participant
  seat?: LayoutElement
}>()
const emit = defineEmits<{
  lock: [participantId: string, locked: boolean]
  unassign: [participantId: string]
  remove: [participantId: string]
}>()
</script>

<template>
  <div v-if="participant" class="detail-bar">
    <div class="detail-identity">
      <span class="avatar">{{ participant.name.slice(-1) }}</span>
      <div>
        <strong>{{ participant.name }}</strong>
        <span>{{ participant.employeeNo }} · {{ participant.department || '未填写部门' }}</span>
      </div>
    </div>
    <el-divider direction="vertical" />
    <div class="detail-facts">
      <span><b>身份</b>{{ participant.participantType || '参会人员' }}</span>
      <span><b>职级</b>{{ participant.level ?? '—' }}</span>
      <span><b>座位</b>{{ seat?.code || '待排' }}</span>
      <span><b>主批次</b>{{ participant.primaryBatchName || '—' }}</span>
      <span v-if="participant.repeatedBatches.length">
        <b>重复批次</b>{{ participant.repeatedBatches.join('、') }}
      </span>
    </div>
    <div class="detail-actions">
      <el-button
        v-if="participant.assignedElementId"
        size="small"
        :icon="participant.locked ? Unlock : Lock"
        @click="emit('lock', participant.id, !participant.locked)"
      >
        {{ participant.locked ? '解除锁定' : '锁定座位' }}
      </el-button>
      <el-button
        v-if="participant.assignedElementId"
        size="small"
        @click="emit('unassign', participant.id)"
      >
        移回待排
      </el-button>
      <el-popconfirm
        title="从会议名单中移除该人员？"
        confirm-button-text="移除"
        cancel-button-text="取消"
        @confirm="emit('remove', participant.id)"
      >
        <template #reference>
          <el-button size="small" type="danger" plain :icon="Delete">移出会议</el-button>
        </template>
      </el-popconfirm>
    </div>
  </div>
</template>

<style scoped>
.detail-bar {
  min-height: 72px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  background: #fff;
  border-top: 1px solid var(--line);
}

.detail-identity {
  min-width: 205px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  color: #fff;
  background: #2f5f9f;
  border-radius: 10px;
  font-weight: 700;
}

.detail-identity > div {
  display: grid;
  gap: 3px;
}

.detail-identity span {
  color: #718096;
  font-size: 11px;
}

.detail-facts {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 20px;
}

.detail-facts span {
  display: grid;
  gap: 3px;
  color: #334155;
  font-size: 12px;
}

.detail-facts b {
  color: #8792a4;
  font-size: 10px;
  font-weight: 500;
}

.detail-actions {
  display: flex;
  gap: 7px;
}
</style>
