import { onBeforeUnmount, onMounted } from 'vue'

export function useBeforeUnloadGuard(shouldWarn) {
  function beforeUnload(event) {
    if (!shouldWarn()) {
      return
    }
    event.preventDefault()
    event.returnValue = ''
  }

  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
}
