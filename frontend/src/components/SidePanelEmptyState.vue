<script setup>
const props = defineProps({
  icon: { type: Object, default: undefined },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  clickable: { type: Boolean, default: false },
})

const emit = defineEmits(['activate'])

function activate() {
  if (props.clickable) {
    emit('activate')
  }
}

function onKeydown(event) {
  if (!props.clickable || !['Enter', ' '].includes(event.key)) {
    return
  }
  event.preventDefault()
  emit('activate')
}
</script>

<template>
  <div
    class="side-panel-empty-state"
    :class="{ clickable }"
    :role="clickable ? 'button' : undefined"
    :tabindex="clickable ? 0 : undefined"
    @click="activate"
    @keydown="onKeydown"
  >
    <el-icon v-if="icon" size="28">
      <component :is="icon" />
    </el-icon>
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
  </div>
</template>

<style scoped>
.side-panel-empty-state {
  width: 100%;
  min-height: 156px;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 8px;
  padding: 20px;
  color: var(--tertiary);
  text-align: center;
}

.side-panel-empty-state strong {
  color: var(--muted);
  font-size: 13px;
  font-weight: 650;
}

.side-panel-empty-state p {
  max-width: 220px;
  margin: 0;
  color: var(--tertiary);
  font-size: 12px;
  line-height: 1.6;
}

.side-panel-empty-state.clickable {
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition:
    color 0.15s ease,
    background-color 0.15s ease;
}

.side-panel-empty-state.clickable:hover,
.side-panel-empty-state.clickable:focus-visible {
  color: var(--brand);
  background: var(--brand-soft);
  outline: 0;
}
</style>
