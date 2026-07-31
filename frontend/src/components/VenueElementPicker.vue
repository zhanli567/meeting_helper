<script setup>
import { computed, ref, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { COMMON_ELEMENT_SUGGESTIONS, ELEMENT_KINDS } from '@/utils/venueModel'
import {
  availableElementSuggestions,
  genericElementColorMap,
  nextAvailableSemanticColor,
  removeCustomElementName,
  saveCustomElementName,
  usedGenericElementColors,
} from '@/utils/venuePreferences'

const props = defineProps({
  rect: {
    type: Object,
    required: true,
  },
  elements: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['choose', 'cancel'])
const customName = ref('')
const seatPending = ref(false)
const suggestions = ref(availableElementSuggestions(undefined, props.elements))
const isMultiCell = computed(() => props.rect.rowSpan * props.rect.columnSpan > 1)

watch(
  () => props.rect,
  () => {
    customName.value = ''
    seatPending.value = false
    refreshSuggestions()
  },
  { deep: true },
)

function refreshSuggestions() {
  suggestions.value = availableElementSuggestions(undefined, props.elements)
}

function choicePayload(suggestion) {
  const colorsByName = genericElementColorMap(props.elements)
  const fillColor = suggestion.kind === ELEMENT_KINDS.GENERIC
    ? colorsByName.get(suggestion.name)
      || nextAvailableSemanticColor(usedGenericElementColors(props.elements, suggestion.name))
    : suggestion.fillColor
  return {
    kind:
      suggestion.name === '座位' ? ELEMENT_KINDS.SEAT : ELEMENT_KINDS.GENERIC,
    name: suggestion.name,
    fillColor,
  }
}

function chooseSuggestion(suggestion) {
  if (suggestion.kind === ELEMENT_KINDS.SEAT && isMultiCell.value) {
    seatPending.value = true
    return
  }
  emit('choose', {
    ...choicePayload(suggestion),
    mode: suggestion.kind === ELEMENT_KINDS.SEAT ? 'merge' : undefined,
  })
}

function chooseSeat(mode) {
  const seat = COMMON_ELEMENT_SUGGESTIONS[0]
  emit('choose', { ...seat, kind: ELEMENT_KINDS.SEAT, mode })
}

function createCustom() {
  const name = saveCustomElementName(customName.value)
  if (!name) {
    return
  }
  refreshSuggestions()
  emit('choose', choicePayload(suggestions.value.find((suggestion) => suggestion.name === name)))
}

function removeCustomElement(suggestion) {
  removeCustomElementName(suggestion.name)
  refreshSuggestions()
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
        <div
          v-for="suggestion in suggestions"
          :key="suggestion.name"
          class="suggestion-card"
        >
          <button
            type="button"
            class="suggestion-choice"
            :title="suggestion.name"
            @click="chooseSuggestion(suggestion)"
          >
            <i
              :style="{
                backgroundColor: suggestion.fillColor,
              }"
            />
            <span>{{ suggestion.name }}</span>
          </button>
          <button
            v-if="suggestion.custom"
            type="button"
            class="custom-delete"
            :aria-label="`删除自定义元素 ${suggestion.name}`"
            @click="removeCustomElement(suggestion)"
          >
            <Close />
          </button>
        </div>
      </div>

      <form class="custom-row" @submit.prevent="createCustom">
        <el-input
          v-model="customName"
          maxlength="30"
          clearable
          aria-label="自定义元素名称"
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
  min-height: 182px;
  max-height: 292px;
  display: grid;
  align-content: start;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px 6px;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 2px 8px 2px 2px;
}

.suggestion-card {
  min-width: 0;
  position: relative;
}

.suggestion-choice {
  width: 100%;
  min-height: 38px;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 24px 6px 8px;
  color: #35445a;
  background: #fff;
  border: 1px solid #dce5f1;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
}

.suggestion-choice:hover {
  color: var(--brand);
  background: #f4f8ff;
  border-color: #98bdf2;
}

.suggestion-choice i {
  width: 18px;
  height: 18px;
  flex: none;
  border: 1px solid #9fb3c8;
  border-radius: 5px;
}

.suggestion-choice span {
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.custom-delete {
  width: 16px;
  height: 16px;
  position: absolute;
  top: 2px;
  right: 2px;
  display: grid;
  place-items: center;
  padding: 0;
  color: #9aa8ba;
  background: #fff;
  border: 1px solid #d7e1ee;
  border-radius: 50%;
  cursor: pointer;
}

.custom-delete:hover {
  color: var(--danger);
  border-color: #fecaca;
}

.custom-delete svg {
  width: 11px;
  height: 11px;
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
