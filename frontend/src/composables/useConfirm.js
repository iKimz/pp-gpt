import { ref } from 'vue'

const confirmState = ref({
  isOpen: false,
  title: 'Confirm Action',
  message: 'Are you sure you want to proceed?',
  confirmText: 'Confirm',
  cancelText: 'Cancel',
  type: 'danger',
  resolve: null
})

export function useConfirm() {
  function confirm({
    title = 'Confirm Action',
    message = 'Are you sure you want to proceed?',
    confirmText = 'Confirm',
    cancelText = 'Cancel',
    type = 'danger'
  } = {}) {
    return new Promise((resolve) => {
      confirmState.value = {
        isOpen: true,
        title,
        message,
        confirmText,
        cancelText,
        type,
        resolve
      }
    })
  }

  function handleConfirm() {
    if (confirmState.value.resolve) {
      confirmState.value.resolve(true)
    }
    confirmState.value.isOpen = false
  }

  function handleCancel() {
    if (confirmState.value.resolve) {
      confirmState.value.resolve(false)
    }
    confirmState.value.isOpen = false
  }

  return {
    confirmState,
    confirm,
    handleConfirm,
    handleCancel
  }
}
