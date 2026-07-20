<template>
  <div class="flex flex-col gap-1 w-full">
    <!-- Label row -->
    <div class="flex items-center justify-between text-xs">
      <span class="text-[#4d4732] font-medium">Daily Quota</span>
      <span :class="labelColor" class="font-mono font-semibold">
        {{ formatNumber(authStore.creditsUsed) }} / {{ formatNumber(authStore.maxDailyCredits) }}
      </span>
    </div>

    <!-- Progress bar track -->
    <div class="h-2 w-full rounded-full bg-[#f4f2fd] border border-[#e8e7f1] overflow-hidden">
      <div
        class="h-full rounded-full transition-all duration-700 ease-out"
        :class="barColor"
        :style="{ width: authStore.quotaPercent + '%' }"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const barColor = computed(() => {
  const p = authStore.quotaPercent
  if (p >= 90) return 'bg-red-500'
  if (p >= 70) return 'bg-amber-500'
  return 'bg-[#ffd700]'
})

const labelColor = computed(() => {
  const p = authStore.quotaPercent
  if (p >= 90) return 'text-red-600'
  if (p >= 70) return 'text-amber-600'
  return 'text-[#705d00]'
})

function formatNumber(n) {
  return new Intl.NumberFormat('en', { maximumFractionDigits: 0 }).format(n ?? 0)
}
</script>
