<template>
  <div class="fixed top-5 right-5 z-50 flex flex-col gap-2.5 max-w-sm w-full pointer-events-none">
    <TransitionGroup name="toast">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        :class="[
          'pointer-events-auto relative overflow-hidden rounded-xl border p-4 shadow-lg backdrop-blur-md transition-all duration-300 flex items-start gap-3.5',
          toastStyles[toast.type]?.container || toastStyles.info.container
        ]"
      >
        <!-- Icon Badge -->
        <div
          :class="[
            'shrink-0 w-8 h-8 rounded-lg flex items-center justify-center font-bold text-sm shadow-sm',
            toastStyles[toast.type]?.iconBg || toastStyles.info.iconBg
          ]"
        >
          <svg v-if="toast.type === 'success'" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7" />
          </svg>
          <svg v-else-if="toast.type === 'error'" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M6 18L18 6M6 6l12 12" />
          </svg>
          <svg v-else-if="toast.type === 'warning'" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>

        <!-- Content -->
        <div class="flex-1 pr-2 min-w-0">
          <h4 class="text-xs font-bold uppercase tracking-wider mb-0.5 text-[#1a1b22]">
            {{ toast.title }}
          </h4>
          <p class="text-xs font-medium text-[#4d4732] leading-relaxed break-words">
            {{ toast.message }}
          </p>
        </div>

        <!-- Dismiss Button -->
        <button
          @click="removeToast(toast.id)"
          class="shrink-0 p-1 rounded-lg text-[#948f7d] hover:text-[#1a1b22] hover:bg-black/5 transition-colors"
        >
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        <!-- Progress Bar -->
        <div
          v-if="toast.duration > 0"
          class="absolute bottom-0 left-0 right-0 h-1 bg-black/5 overflow-hidden"
        >
          <div
            :class="[
              'h-full animate-toast-progress',
              toastStyles[toast.type]?.progress || toastStyles.info.progress
            ]"
            :style="{ animationDuration: `${toast.duration}ms` }"
          ></div>
        </div>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup>
import { useToast } from '@/composables/useToast'

const { toasts, removeToast } = useToast()

const toastStyles = {
  success: {
    container: 'bg-emerald-50/95 border-emerald-200 text-emerald-950 shadow-emerald-950/5',
    iconBg: 'bg-emerald-500 text-white',
    progress: 'bg-emerald-500'
  },
  error: {
    container: 'bg-rose-50/95 border-rose-200 text-rose-950 shadow-rose-950/5',
    iconBg: 'bg-rose-500 text-white',
    progress: 'bg-rose-500'
  },
  warning: {
    container: 'bg-amber-50/95 border-amber-200 text-amber-950 shadow-amber-950/5',
    iconBg: 'bg-amber-500 text-white',
    progress: 'bg-amber-500'
  },
  info: {
    container: 'bg-sky-50/95 border-sky-200 text-sky-950 shadow-sky-950/5',
    iconBg: 'bg-sky-500 text-white',
    progress: 'bg-sky-500'
  }
}
</script>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.toast-enter-from {
  opacity: 0;
  transform: translateX(40px) scale(0.95);
}
.toast-leave-to {
  opacity: 0;
  transform: translateY(-20px) scale(0.9);
}

@keyframes toastProgress {
  from {
    width: 100%;
  }
  to {
    width: 0%;
  }
}
.animate-toast-progress {
  animation: toastProgress linear forwards;
}
</style>
