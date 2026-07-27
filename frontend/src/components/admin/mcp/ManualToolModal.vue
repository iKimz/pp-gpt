<template>
  <div v-if="show" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
    <div class="bg-white rounded-2xl border border-[#e8e7f1] shadow-2xl w-full max-w-lg overflow-hidden animate-in fade-in zoom-in-95 duration-200">
      <!-- Header -->
      <div class="px-6 py-4 border-b border-[#e8e7f1] flex items-center justify-between bg-[#fcfbfe]">
        <h3 class="text-sm font-bold text-[#1a1b22] font-heading flex items-center gap-2">
          <span>⚙️</span> Add Manual Tool / REST Endpoint Preset
        </h3>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600 text-lg leading-none">&times;</button>
      </div>

      <!-- Form -->
      <form @submit.prevent="handleSubmit" class="p-6 space-y-4 text-xs">
        <div>
          <label class="block font-semibold text-gray-700 mb-1">Quick Presets</label>
          <div class="grid grid-cols-2 gap-2">
            <button
              type="button"
              @click="applyPreset('REST_API')"
              class="px-3 py-2 bg-amber-50 hover:bg-amber-100 border border-amber-200 text-amber-800 rounded-xl font-medium text-left flex items-center gap-2 transition-all"
            >
              <span class="text-base">🌐</span>
              <div>
                <p class="font-bold text-[11px]">REST API (Method/Path)</p>
                <p class="text-[9px] opacity-80">POST / GET / PUT subpaths</p>
              </div>
            </button>

            <button
              type="button"
              @click="applyPreset('CUSTOM')"
              class="px-3 py-2 bg-gray-50 hover:bg-gray-100 border border-gray-200 text-gray-700 rounded-xl font-medium text-left flex items-center gap-2 transition-all"
            >
              <span class="text-base">🛠️</span>
              <div>
                <p class="font-bold text-[11px]">Custom Schema Tool</p>
                <p class="text-[9px] opacity-80">Freeform JSON Parameters</p>
              </div>
            </button>
          </div>
        </div>

        <div>
          <label class="block font-semibold text-gray-700 mb-1">Tool Name *</label>
          <input
            v-model="form.toolName"
            type="text"
            required
            placeholder="e.g. process_payment / query_database"
            class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono"
          />
        </div>

        <div>
          <label class="block font-semibold text-gray-700 mb-1">Description (LLM Function Prompt Guidance)</label>
          <textarea
            v-model="form.description"
            rows="2"
            placeholder="Describe what this tool does so LLM models know when to call it..."
            class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs resize-none"
          ></textarea>
        </div>

        <div>
          <label class="block font-semibold text-gray-700 mb-1">JSON Input Schema (Parameters)</label>
          <textarea
            v-model="form.inputSchema"
            rows="6"
            placeholder='{"type":"object","properties":{...}}'
            class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono leading-relaxed"
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
            :disabled="submitting"
            class="px-5 py-2 bg-[#ffd700] hover:bg-[#e9c400] text-[#1a1b22] font-semibold rounded-xl text-xs shadow-sm transition-all disabled:opacity-50 flex items-center gap-1.5"
          >
            <span v-if="submitting" class="animate-spin text-sm">⏳</span>
            Create Manual Tool
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
  toolName: '',
  description: '',
  inputSchema: ''
})

const REST_PRESET_SCHEMA = JSON.stringify({
  type: 'object',
  properties: {
    method: {
      type: 'string',
      description: 'HTTP method (GET, POST, PUT, DELETE, PATCH)'
    },
    path: {
      type: 'string',
      description: 'Subpath relative to server base URL (e.g. /api/v1/payments)'
    },
    payload: {
      type: 'object',
      description: 'JSON request payload body or query parameters'
    }
  },
  required: ['method']
}, null, 2)

watch(() => props.show, (val) => {
  if (val) {
    applyPreset('REST_API')
  }
})

function applyPreset(presetType) {
  if (presetType === 'REST_API') {
    form.toolName = form.toolName || 'rest_api_call'
    form.description = 'Executes a legacy REST API request with HTTP method, subpath, and JSON payload.'
    form.inputSchema = REST_PRESET_SCHEMA
  } else {
    form.toolName = ''
    form.description = ''
    form.inputSchema = '{\n  "type": "object",\n  "properties": {}\n}'
  }
}

function handleSubmit() {
  emit('save', { ...form })
}
</script>
