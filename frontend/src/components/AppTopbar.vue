<template>
  <header class="h-14 flex items-center gap-3 px-4 border-b border-[#e8e7f1] bg-white shrink-0 shadow-sm">
    <!-- Model selector -->
    <div class="flex items-center gap-2">
      <label class="text-xs font-semibold text-[#4d4732] whitespace-nowrap uppercase tracking-wider">Model</label>
      <select
        v-model="chatStore.selectedModelId"
        class="input-field py-1.5 text-xs w-52"
        :disabled="chatStore.isStreaming"
      >
        <option value="" disabled>Select a model…</option>
        <option v-for="m in models" :key="m.id" :value="m.id">
          {{ m.name || (m.provider + ' / ' + m.modelName) }}
        </option>
      </select>
    </div>

    <!-- Spacer -->
    <div class="flex-1" />

    <!-- Stop generation button -->
    <Transition name="fade">
      <button
        v-if="chatStore.isStreaming"
        @click="chatStore.stopGeneration()"
        class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium
               bg-red-900/40 text-red-400 border border-red-700/40
               hover:bg-red-700/60 hover:text-red-200 transition-all duration-150"
      >
        <svg class="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24">
          <rect x="4" y="4" width="16" height="16" rx="2"/>
        </svg>
        Stop
      </button>
    </Transition>

    <!-- Quota bar -->
    <div class="flex items-center gap-3 min-w-[180px]">
      <QuotaBar />
    </div>

    <!-- User avatar -->
    <div class="w-7 h-7 rounded-full bg-gradient-to-br from-brand-500 to-purple-600 flex items-center justify-center text-white text-xs font-bold shrink-0">
      {{ authStore.username?.charAt(0).toUpperCase() }}
    </div>
  </header>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import apiClient from '@/api/client'
import QuotaBar from './QuotaBar.vue'

const chatStore = useChatStore()
const authStore = useAuthStore()
const models    = ref([])

onMounted(async () => {
  try {
    const { data } = await apiClient.get('/api/v1/chat/models')
    models.value = data // Already filtered by backend to be active and group-assigned
    if (models.value.length && !chatStore.selectedModelId) {
      chatStore.selectedModelId = models.value[0].id
    }
  } catch (e) {
    console.error('Failed to load models', e)
  }
})
</script>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s, transform 0.2s; }
.fade-enter-from { opacity: 0; transform: scale(0.9); }
.fade-leave-to   { opacity: 0; transform: scale(0.9); }
</style>
