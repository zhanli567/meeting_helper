<script setup>
import { computed, ref, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import {
  COMMON_ELEMENT_SUGGESTIONS,
  ELEMENT_KINDS,
} from '@/utils/venueModel'

const props = defineProps({
  rect: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['choose', 'cancel'])
const customName = ref('')
const seatPending = ref(false)
const isMultiCell = computed(() => props.rect.rowSpan * props.rect.columnSpan > 1)

watch(
  () => props.rect,
  () => {
    customName.value = ''
    seatPending.value = false
  },
  { deep: true },
)

function chooseSuggestion(suggestion) {
  if (suggestion.kind === ELEMENT_KINDS.SEAT && isMultiCell.value) {
    seatPending.value = true
    return
  }
  emit('choose', {
    ...suggestion,
    kind:
      suggestion.name === '座位' ? ELEMENT_KINDS.SEAT : ELEMENT_KINDS.GENERIC,
    mode: suggestion.kind === ELEMENT_KINDS.SEAT ? 'merge' : undefined,
  })
}

function chooseSeat(mode) {
  const seat = COMMON_ELEMENT_SUGGESTIONS[0]
  emit('choose', { ...seat, kind: ELEMENT_KINDS.SEAT, mode })
}

function createCustom() {
  const name = customName.value.trim()
  if (!name) return
  emit('choose', {
    kind: ELEMENT_KINDS.GENERIC,
    name,
    fillColor: '#dbeafe',
    borderColor: '#93c5fd',
  })
}
</script>

<template>
  <section class="venue-element-picker" @pointerdown.stop>
    <header>
      <div>
        <strong>添加元素</strong>
        <span>{{ rect.rowSpan }} × {{ rect.columnSpan }} 格</span>
      </div>
      <el-button text circle :icon="Close" aria-label="关闭元素选择" @click="emit('cancel')" />
    </header>

    <div v-if="seatPending" class="seat-mode">
      <strong>所选区域包含多个网格</strong>
      <div>
        <el-button type="primary" plain @click="chooseSeat('cells')">逐格生成座位</el-button>
        <el-button type="primary" @click="chooseSeat('merge')">合并为一个座位</el-button>
      </div>
      <el-button text @click="seatPending = false">返回元素列表</el-button>
    </div>

    <template v-else>
      <div class="suggestion-grid">
        <button
          v-for="suggestion in COMMON_ELEMENT_SUGGESTIONS"
          :key="suggestion.name"
          type="button"
          @click="chooseSuggestion(suggestion)"
        >
          <i
            :style="{
              backgroundColor: suggestion.fillColor,
              borderColor: suggestion.borderColor,
            }"
          />
          <span>{{ suggestion.name }}</span>
        </button>
      </div>

      <form class="custom-row" @submit.prevent="createCustom">
        <el-input
          v-model="customName"
          maxlength="30"
          placeholder="自定义元素名称"
          clearable
        />
        <el-button type="primary" native-type="submit" :disabled="!customName.trim()">
          添加
        </el-button>
      </form>
    </template>
  </section>
</template>

<style scoped>
.venue-element-picker {
  width: 390px;
  max-height: min(430px, calc(100vh - 120px));
  overflow: hidden;
  padding: 12px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid #cbd9ec;
  border-radius: 12px;
  box-shadow: 0 18px 42px rgba(30, 64, 110, 0.2);
}

header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

header > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

header strong {
  color: var(--ink);
  font-size: 14px;
}

header span {
  color: var(--muted);
  font-size: 11px;
}

.suggestion-grid {
  max-height: 292px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  overflow-y: auto;
  padding-right: 3px;
}

.suggestion-grid button {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px;
  color: #35445a;
  background: #fff;
  border: 1px solid #dce5f1;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
}

.suggestion-grid button:hover {
  color: var(--brand);
  background: #f4f8ff;
  border-color: #98bdf2;
}

.suggestion-grid i {
  width: 18px;
  height: 18px;
  flex: none;
  border: 1px solid;
  border-radius: 5px;
}

.suggestion-grid span {
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.custom-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  padding-top: 10px;
  margin-top: 10px;
  border-top: 1px solid #e5ebf3;
}

.seat-mode {
  min-height: 178px;
  display: grid;
  place-items: center;
  gap: 18px;
  padding: 20px 8px 8px;
}

.seat-mode > div {
  display: flex;
  gap: 8px;
}
</style>
