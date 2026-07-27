<template>
  <div v-if="show" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
    <div class="bg-white rounded-2xl border border-[#e8e7f1] shadow-2xl w-full max-w-xl overflow-hidden animate-in fade-in zoom-in-95 duration-200">
      <!-- Header -->
      <div class="px-6 py-4 border-b border-[#e8e7f1] flex items-center justify-between bg-[#fcfbfe]">
        <div>
          <h3 class="text-sm font-bold text-[#1a1b22] font-heading flex items-center gap-2">
            <span>🛡️</span> Group Tool Authorization Matrix
          </h3>
          <p class="text-[11px] text-gray-500 mt-0.5">Configure selective MCP tool permissions for user group: <strong class="text-[#1a1b22]">{{ group?.groupName }}</strong></p>
        </div>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600 text-lg leading-none">&times;</button>
      </div>

      <!-- Content -->
      <div class="p-6 space-y-4 text-xs max-h-[60vh] overflow-y-auto">
        <div v-if="loading" class="text-center py-8 text-gray-400">
          Loading tool permissions...
        </div>

        <div v-else-if="toolsList.length === 0" class="text-center py-8 text-gray-400">
          No discovered tools available. Register an MCP server or add manual tools first.
        </div>

        <div v-else class="space-y-3">
          <div class="flex items-center justify-between px-3 py-2 bg-gray-50 rounded-xl border border-gray-200 text-gray-700">
            <span class="font-semibold text-xs">Select All Tools for {{ group?.groupName }}</span>
            <div class="flex items-center gap-2">
              <button type="button" @click="toggleAll(true)" class="px-2.5 py-1 bg-white hover:bg-emerald-50 text-emerald-700 font-semibold rounded-lg border border-emerald-200 text-[10px]">
                Enable All
              </button>
              <button type="button" @click="toggleAll(false)" class="px-2.5 py-1 bg-white hover:bg-red-50 text-red-700 font-semibold rounded-lg border border-red-200 text-[10px]">
                Disable All
              </button>
            </div>
          </div>

          <div class="divide-y divide-[#e8e7f1] border border-[#e8e7f1] rounded-xl overflow-hidden">
            <div
              v-for="tool in toolsList"
              :key="tool.mcpToolId"
              class="p-3.5 flex items-center justify-between hover:bg-[#fbf8ff]/60 transition-colors"
            >
              <div class="space-y-0.5 max-w-md">
                <div class="flex items-center gap-2">
                  <p class="font-bold text-[#1a1b22] font-mono text-xs">{{ tool.namespacedName }}</p>
                  <span v-if="tool.isManual" class="px-1.5 py-0.5 rounded text-[9px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">Manual</span>
                </div>
                <p class="text-[11px] text-gray-500 line-clamp-1">{{ tool.description || 'No description' }}</p>
                <p class="text-[10px] text-gray-400">Server: {{ tool.serverName }}</p>
              </div>

              <label class="relative inline-flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  v-model="tool.isEnabled"
                  class="sr-only peer"
                />
                <div class="w-9 h-5 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-emerald-500"></div>
              </label>
            </div>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div class="px-6 py-4 border-t border-[#e8e7f1] bg-[#fcfbfe] flex items-center justify-end gap-3">
        <button
          type="button"
          @click="$emit('close')"
          class="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 font-semibold rounded-xl text-xs transition-all"
        >
          Cancel
        </button>
        <button
          type="button"
          @click="handleSubmit"
          :disabled="submitting || loading"
          class="px-5 py-2 bg-[#ffd700] hover:bg-[#e9c400] text-[#1a1b22] font-semibold rounded-xl text-xs shadow-sm transition-all disabled:opacity-50 flex items-center gap-1.5"
        >
          <span v-if="submitting" class="animate-spin text-sm">⏳</span>
          Save Group Permissions
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  show: Boolean,
  group: Object,
  tools: Array,
  loading: Boolean,
  submitting: Boolean
})

const emit = defineEmits(['close', 'save'])

const toolsList = ref([])

watch(() => props.tools, (newTools) => {
  toolsList.value = (newTools || []).map(t => ({ ...t }))
}, { immediate: true, deep: true })

function toggleAll(enabled) {
  toolsList.value.forEach(t => {
    t.isEnabled = enabled
  })
}

function handleSubmit() {
  const updates = toolsList.value.map(t => ({
    mcpToolId: t.mcpToolId,
    isEnabled: t.isEnabled
  }))
  emit('save', updates)
}
</script>
