<template>
  <Teleport to="body">
    <Transition name="confirm-fade">
      <div
        v-if="confirmState.isOpen"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm"
        @click.self="handleCancel"
      >
        <div
          class="glass bg-white/95 border border-[#e8e7f1] shadow-2xl rounded-2xl p-6 max-w-md w-full transform transition-all duration-300 scale-100"
          role="dialog"
          aria-modal="true"
        >
          <div class="flex items-start gap-4">
            <!-- Icon Badge -->
            <div
              :class="[
                'shrink-0 w-12 h-12 rounded-xl flex items-center justify-center shadow-inner',
                iconStyles[confirmState.type]?.bg || iconStyles.danger.bg
              ]"
            >
              <svg
                v-if="confirmState.type === 'danger'"
                class="w-6 h-6 text-rose-600"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                />
              </svg>
              <svg
                v-else-if="confirmState.type === 'warning'"
                class="w-6 h-6 text-amber-600"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
                />
              </svg>
              <svg
                v-else
                class="w-6 h-6 text-purple-600"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
            </div>

            <!-- Text Content -->
            <div class="flex-1 min-w-0">
              <h3 class="text-base font-bold text-[#1a1b22] font-heading mb-1">
                {{ confirmState.title }}
              </h3>
              <p class="text-xs text-[#4d4732] leading-relaxed break-words">
                {{ confirmState.message }}
              </p>
            </div>
          </div>

          <!-- Buttons -->
          <div class="flex items-center justify-end gap-3 mt-6 pt-4 border-t border-[#eeedf7]">
            <button
              type="button"
              @click="handleCancel"
              class="px-4 py-2 text-xs font-semibold text-[#4d4732] bg-[#f4f2fd] hover:bg-[#e8e4f8] border border-[#e8e7f1] rounded-xl transition-colors"
            >
              {{ confirmState.cancelText }}
            </button>

            <button
              type="button"
              @click="handleConfirm"
              :class="[
                'px-4 py-2 text-xs font-semibold text-white rounded-xl shadow-md transition-all transform active:scale-95',
                buttonStyles[confirmState.type] || buttonStyles.danger
              ]"
            >
              {{ confirmState.confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { useConfirm } from '@/composables/useConfirm'

const { confirmState, handleConfirm, handleCancel } = useConfirm()

const iconStyles = {
  danger: { bg: 'bg-rose-100 border border-rose-200' },
  warning: { bg: 'bg-amber-100 border border-amber-200' },
  info: { bg: 'bg-purple-100 border border-purple-200' }
}

const buttonStyles = {
  danger: 'bg-gradient-to-r from-rose-600 to-red-600 hover:from-rose-700 hover:to-red-700 shadow-rose-600/20',
  warning: 'bg-gradient-to-r from-amber-600 to-yellow-600 hover:from-amber-700 hover:to-yellow-700 shadow-amber-600/20 text-white',
  info: 'bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-700 hover:to-indigo-700 shadow-purple-600/20'
}
</script>

<style scoped>
.confirm-fade-enter-active,
.confirm-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.confirm-fade-enter-from,
.confirm-fade-leave-to {
  opacity: 0;
}
</style>
