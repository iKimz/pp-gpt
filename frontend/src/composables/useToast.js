import { ref } from 'vue'

const toasts = ref([])
let idCounter = 0

export function useToast() {
  function addToast({ title, message, type = 'success', duration = 4000 }) {
    const id = ++idCounter
    const toast = { id, title, message, type, duration }
    toasts.value.push(toast)

    if (duration > 0) {
      setTimeout(() => {
        removeToast(id)
      }, duration)
    }
  }

  function removeToast(id) {
    const index = toasts.value.findIndex(t => t.id === id)
    if (index !== -1) {
      toasts.value.splice(index, 1)
    }
  }

  function success(message, title = 'Success') {
    addToast({ title, message, type: 'success' })
  }

  function error(message, title = 'Error') {
    addToast({ title, message, type: 'error' })
  }

  function warning(message, title = 'Warning') {
    addToast({ title, message, type: 'warning' })
  }

  function info(message, title = 'Notice') {
    addToast({ title, message, type: 'info' })
  }

  return {
    toasts,
    addToast,
    removeToast,
    success,
    error,
    warning,
    info
  }
}
