<template>
  <div v-if="show" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
    <div class="bg-white rounded-2xl border border-[#e8e7f1] shadow-2xl w-full max-w-lg overflow-hidden animate-in fade-in zoom-in-95 duration-200">
      <!-- Header -->
      <div class="px-6 py-4 border-b border-[#e8e7f1] flex items-center justify-between bg-[#fcfbfe]">
        <h3 class="text-sm font-bold text-[#1a1b22] font-heading flex items-center gap-2">
          <span>📑</span> Import OpenAPI 3.0 / Swagger Spec
        </h3>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600 text-lg leading-none">&times;</button>
      </div>

      <!-- Form -->
      <form @submit.prevent="handleSubmit" class="p-6 space-y-4 text-xs">
        <div>
          <label class="block font-semibold text-gray-700 mb-1">OpenAPI Specification URL</label>
          <input
            v-model="form.openApiUrl"
            type="url"
            placeholder="http://localhost:8088/v3/api-docs or https://petstore.swagger.io/v2/swagger.json"
            class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono"
          />
        </div>

        <div class="text-center text-gray-400 font-semibold text-[10px] uppercase tracking-wider">
          — OR Paste Raw JSON / YAML Content —
        </div>

        <div>
          <label class="block font-semibold text-gray-700 mb-1">Raw OpenAPI Spec Content</label>
          <textarea
            v-model="form.openApiContent"
            rows="7"
            placeholder='{"openapi": "3.0.0", "paths": {...}}'
            class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono resize-none leading-relaxed"
          ></textarea>
        </div>

        <!-- Footer -->
        <div class="pt-4 border-t border-[#e8e7f1] flex items-center justify-end gap-3">
          <button
            type="button"
            @click="$emit('close')"
            class="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 font-semibold rounded-xl text-xs transition-all"
          >
            Cancel
          </button>
          <button
            type="submit"
            :disabled="submitting || (!form.openApiUrl && !form.openApiContent)"
            class="px-5 py-2 bg-[#ffd700] hover:bg-[#e9c400] text-[#1a1b22] font-semibold rounded-xl text-xs shadow-sm transition-all disabled:opacity-50 flex items-center gap-1.5"
          >
            <span v-if="submitting" class="animate-spin text-sm">⏳</span>
            Import OpenAPI Spec
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue'

const props = defineProps({
  show: Boolean,
  server: Object,
  submitting: Boolean
})

const emit = defineEmits(['close', 'save'])

const form = reactive({
  openApiUrl: '',
  openApiContent: ''
})

watch(() => props.show, (val) => {
  if (val) {
    form.openApiUrl = ''
    form.openApiContent = ''
  }
})

function handleSubmit() {
  emit('save', { ...form })
}
</script>
