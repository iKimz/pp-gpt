<template>
  <div v-if="show" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
    <div class="bg-white rounded-2xl border border-[#e8e7f1] shadow-2xl w-full max-w-xl overflow-hidden animate-in fade-in zoom-in-95 duration-200">
      <!-- Header -->
      <div class="px-6 py-4 border-b border-[#e8e7f1] flex items-center justify-between bg-[#fcfbfe]">
        <div>
          <h3 class="text-sm font-bold text-[#1a1b22] font-heading flex items-center gap-2">
            <span>{{ editingTool ? '✏️' : '⚙️' }}</span> {{ editingTool ? 'Edit Manual Tool' : 'Add Manual Tool / REST Endpoint' }}
          </h3>
          <p class="text-[11px] text-gray-500 mt-0.5">Register or update REST API endpoints as AI function tools.</p>
        </div>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600 text-lg leading-none">&times;</button>
      </div>

      <!-- Mode Switcher Tabs -->
      <div class="px-6 pt-3 flex items-center gap-2 border-b border-[#e8e7f1] bg-[#f8f7fa]">
        <button
          type="button"
          @click="activeMode = 'REST_FORM'"
          :class="[
            'px-3.5 py-2 font-semibold text-xs rounded-t-xl border-b-2 transition-all flex items-center gap-1.5',
            activeMode === 'REST_FORM'
              ? 'border-[#ffd700] text-[#1a1b22] bg-white shadow-sm'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          ]"
        >
          <span>🌐</span> REST API Form
        </button>
        <button
          type="button"
          @click="activeMode = 'JSON_SCHEMA'"
          :class="[
            'px-3.5 py-2 font-semibold text-xs rounded-t-xl border-b-2 transition-all flex items-center gap-1.5',
            activeMode === 'JSON_SCHEMA'
              ? 'border-[#ffd700] text-[#1a1b22] bg-white shadow-sm'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          ]"
        >
          <span>🛠️</span> Advanced JSON Schema
        </button>
      </div>

      <!-- Form -->
      <form @submit.prevent="handleSubmit" class="p-6 space-y-4 text-xs max-h-[75vh] overflow-y-auto">
        <!-- Visual REST Builder Mode -->
        <template v-if="activeMode === 'REST_FORM'">
          <div>
            <label class="block font-semibold text-gray-700 mb-1">Tool Name *</label>
            <input
              v-model="form.toolName"
              type="text"
              required
              placeholder="e.g. process_payment / create_order"
              class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono"
            />
          </div>

          <div>
            <label class="block font-semibold text-gray-700 mb-1">Description for AI *</label>
            <textarea
              v-model="form.description"
              rows="2"
              required
              placeholder="Describe what this API endpoint does so the AI model knows when to invoke it..."
              class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs resize-none"
            ></textarea>
          </div>

          <div class="grid grid-cols-4 gap-3">
            <div class="col-span-1">
              <label class="block font-semibold text-gray-700 mb-1">Method *</label>
              <select
                v-model="restForm.method"
                @change="updateRestSchema"
                class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-bold text-[#1a1b22]"
              >
                <option value="POST">POST</option>
                <option value="GET">GET</option>
                <option value="PUT">PUT</option>
                <option value="DELETE">DELETE</option>
                <option value="PATCH">PATCH</option>
              </select>
            </div>

            <div class="col-span-3">
              <label class="block font-semibold text-gray-700 mb-1">Subpath Endpoint *</label>
              <input
                v-model="restForm.path"
                @input="updateRestSchema"
                type="text"
                required
                placeholder="e.g. /api/v1/payments"
                class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono"
              />
            </div>
          </div>

          <div>
            <label class="block font-semibold text-gray-700 mb-1">
              Sample Request JSON Body / Parameters (Optional)
            </label>
            <textarea
              v-model="restForm.samplePayload"
              @input="updateRestSchema"
              rows="3"
              placeholder='{"amount": 100, "currency": "THB"}'
              class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono resize-none leading-relaxed"
            ></textarea>
            <p class="text-[10px] text-gray-400 mt-1">Provide sample JSON parameters to infer property types for the AI function schema.</p>
          </div>

          <div>
            <label class="block font-semibold text-gray-700 mb-1">
              Custom HTTP Headers (JSON, Optional)
            </label>
            <textarea
              v-model="restForm.customHeaders"
              @input="updateRestSchema"
              rows="2"
              placeholder='{"X-Tenant-Id": "tenant_1", "X-Custom-Header": "value"}'
              class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono resize-none leading-relaxed"
            ></textarea>
            <p class="text-[10px] text-gray-400 mt-1">Specify additional HTTP headers to include when forwarding requests to the target server.</p>
          </div>
        </template>

        <!-- Advanced JSON Schema Mode -->
        <template v-else>
          <div class="p-3 bg-purple-50/70 border border-purple-200 rounded-xl space-y-1 text-purple-950">
            <p class="font-bold flex items-center gap-1.5 text-xs">
              <span>💡</span> Custom Headers in JSON Schema
            </p>
            <p class="text-[11px] leading-relaxed text-purple-900">
              Add a <code>"headers"</code> property object under <code>"properties"</code> with a <code>"default"</code> map containing your key-value headers.
            </p>
          </div>

          <div>
            <label class="block font-semibold text-gray-700 mb-1">Tool Name *</label>
            <input
              v-model="form.toolName"
              type="text"
              required
              placeholder="e.g. process_payment"
              class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono"
            />
          </div>

          <div>
            <label class="block font-semibold text-gray-700 mb-1">Description *</label>
            <textarea
              v-model="form.description"
              rows="2"
              required
              placeholder="Describe tool capability..."
              class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs resize-none"
            ></textarea>
          </div>

          <div>
            <label class="block font-semibold text-gray-700 mb-1">JSON Input Schema (OpenAI Spec)</label>
            <textarea
              v-model="form.inputSchema"
              rows="10"
              placeholder='{\n  "type": "object",\n  "properties": {\n    "method": { "type": "string", "default": "POST" },\n    "path": { "type": "string", "default": "/api/v1/payments" },\n    "headers": {\n      "type": "object",\n      "default": {\n        "X-Tenant-Id": "tenant_001"\n      }\n    }\n  }\n}'
              class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono leading-relaxed"
            ></textarea>
          </div>
        </template>

        <!-- Test Result Box -->
        <div v-if="testResult" class="p-3.5 rounded-xl border text-xs space-y-2" :class="testResult.status === 'SUCCESS' ? 'bg-emerald-50/90 border-emerald-200 text-emerald-950' : 'bg-rose-50/90 border-rose-200 text-rose-950'">
          <div class="flex items-center justify-between font-bold">
            <div class="flex items-center gap-2 overflow-hidden">
              <span class="px-2 py-0.5 rounded text-[10px] font-mono text-white shrink-0" :class="testResult.status === 'SUCCESS' ? 'bg-emerald-600' : 'bg-rose-600'">
                {{ testResult.statusCode }} {{ testResult.status }}
              </span>
              <span class="font-mono text-[11px] truncate">{{ testResult.method }} {{ testResult.targetUrl }}</span>
            </div>
            <span class="text-[10px] font-mono text-gray-500 shrink-0">{{ testResult.durationMs }} ms</span>
          </div>
          <div v-if="testResult.responseBody" class="bg-[#1a1b26] text-gray-200 p-2.5 rounded-lg font-mono text-[11px] max-h-36 overflow-y-auto whitespace-pre-wrap break-all border border-[#2e3047]">
            {{ formatJsonStr(testResult.responseBody) }}
          </div>
          <div v-else-if="testResult.error" class="text-rose-700 font-mono text-[11px]">
            {{ testResult.error }}
          </div>
        </div>

        <!-- Footer -->
        <div class="pt-4 border-t border-[#e8e7f1] flex items-center justify-between gap-3">
          <button
            type="button"
            @click="handleTestEndpoint"
            :disabled="testing || !server?.id"
            class="px-4 py-2 bg-purple-50 hover:bg-purple-100 text-purple-800 border border-purple-200 font-semibold rounded-xl text-xs transition-all flex items-center gap-1.5 disabled:opacity-50"
            title="Test HTTP request to target REST endpoint"
          >
            <span v-if="testing" class="animate-spin text-sm">⏳</span>
            <span>⚡</span> {{ testing ? 'Testing Endpoint...' : 'Test Endpoint' }}
          </button>

          <div class="flex items-center gap-2">
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
              {{ editingTool ? 'Save Changes' : 'Create Manual Tool' }}
            </button>
          </div>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { adminApi } from '@/api/admin'
import { useToast } from '@/composables/useToast'

const toast = useToast()

const props = defineProps({
  show: Boolean,
  server: Object,
  editingTool: Object,
  submitting: Boolean
})

const emit = defineEmits(['close', 'save'])

const activeMode = ref('REST_FORM')
const testing = ref(false)
const testResult = ref(null)

const form = reactive({
  toolName: '',
  description: '',
  inputSchema: ''
})

const restForm = reactive({
  method: 'POST',
  path: '/api/v1/payments',
  samplePayload: '{\n  "amount": 100,\n  "currency": "THB"\n}',
  customHeaders: '{\n  "X-Tenant-Id": "tenant_001"\n}'
})

watch(() => props.show, (val) => {
  if (val) {
    if (props.editingTool) {
      form.toolName = props.editingTool.toolName || ''
      form.description = props.editingTool.description || ''
      form.inputSchema = props.editingTool.inputSchema || ''
      try {
        const schemaObj = JSON.parse(props.editingTool.inputSchema || '{}')
        const propsObj = schemaObj.properties || {}
        if (propsObj.method) restForm.method = propsObj.method.default || 'POST'
        if (propsObj.path) restForm.path = propsObj.path.default || '/api/v1/endpoint'
        if (propsObj.headers && propsObj.headers.default) {
          restForm.customHeaders = JSON.stringify(propsObj.headers.default, null, 2)
        }
      } catch (e) {}
    } else {
      form.toolName = 'process_payment'
      form.description = 'Executes a payment request to the REST API endpoint via POST'
      restForm.method = 'POST'
      restForm.path = '/api/v1/payments'
      restForm.samplePayload = '{\n  "amount": 100,\n  "currency": "THB"\n}'
      restForm.customHeaders = '{\n  "X-Tenant-Id": "tenant_001"\n}'
      updateRestSchema()
    }
  }
})

function updateRestSchema() {
  let payloadSchema = {
    type: 'object',
    description: 'JSON request payload body or query parameters'
  }

  if (restForm.samplePayload && restForm.samplePayload.trim()) {
    try {
      const parsed = JSON.parse(restForm.samplePayload)
      if (typeof parsed === 'object' && parsed !== null) {
        const propsObj = {}
        Object.keys(parsed).forEach(key => {
          const val = parsed[key]
          const valType = typeof val === 'number' ? 'number' : typeof val === 'boolean' ? 'boolean' : 'string'
          propsObj[key] = {
            type: valType,
            description: `Field ${key}`
          }
        })
        payloadSchema = {
          type: 'object',
          properties: propsObj
        }
      }
    } catch (e) {}
  }

  const generatedProperties = {
    method: {
      type: 'string',
      description: 'HTTP Method for legacy endpoint',
      default: restForm.method || 'POST'
    },
    path: {
      type: 'string',
      description: 'Subpath relative to server base URL',
      default: restForm.path || '/api/v1/payments'
    },
    payload: payloadSchema
  }

  if (restForm.customHeaders && restForm.customHeaders.trim()) {
    try {
      const parsedHeaders = JSON.parse(restForm.customHeaders)
      if (typeof parsedHeaders === 'object' && parsedHeaders !== null) {
        generatedProperties.headers = {
          type: 'object',
          description: 'Custom HTTP Request Headers',
          default: parsedHeaders
        }
      }
    } catch (e) {}
  }

  const generatedSchema = {
    type: 'object',
    properties: generatedProperties,
    required: ['method']
  }

  form.inputSchema = JSON.stringify(generatedSchema, null, 2)
}

function handleSubmit() {
  emit('save', { ...form })
}

async function handleTestEndpoint() {
  if (!props.server?.id) {
    toast.error('Server context is missing')
    return
  }
  if (activeMode.value === 'REST_FORM') {
    updateRestSchema()
  }
  testing.value = true
  testResult.value = null
  try {
    const res = await adminApi.testManualMcpTool(props.server.id, form)
    testResult.value = res.data
    if (res.data.status === 'SUCCESS') {
      toast.success(`Endpoint tested successfully! (${res.data.statusCode} in ${res.data.durationMs}ms)`)
    } else {
      toast.error(`Endpoint test returned ${res.data.statusCode} ${res.data.status}`)
    }
  } catch (e) {
    const errMsg = e.response?.data?.message || e.message
    testResult.value = {
      status: 'FAILED',
      statusCode: 500,
      targetUrl: props.server.endpointUrl,
      method: restForm.method || 'POST',
      durationMs: 0,
      error: errMsg
    }
    toast.error(`Test failed: ${errMsg}`)
  } finally {
    testing.value = false
  }
}

function formatJsonStr(str) {
  if (!str) return ''
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch (e) {
    return str
  }
}
</script>
