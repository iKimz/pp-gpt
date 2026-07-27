<template>
  <div v-if="show" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
    <div class="bg-white rounded-2xl border border-[#e8e7f1] shadow-2xl w-full max-w-xl overflow-hidden animate-in fade-in zoom-in-95 duration-200">
      <!-- Header -->
      <div class="px-6 py-4 border-b border-[#e8e7f1] flex items-center justify-between bg-[#fcfbfe]">
        <div>
          <h3 class="text-sm font-bold text-[#1a1b22] font-heading flex items-center gap-2">
            <span>⚙️</span> Add Manual Tool / REST Endpoint
          </h3>
          <p class="text-[11px] text-gray-500 mt-0.5">Define REST API endpoints for AI LLMs to call as function tools.</p>
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
          <span>🌐</span> Visual REST Builder (ง่ายที่สุด)
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
          <div class="p-3.5 bg-blue-50/70 border border-blue-200 rounded-xl space-y-1.5 text-blue-950">
            <p class="font-bold flex items-center gap-1.5 text-xs">
              <span>💡</span> ตัวอย่างการตั้งค่าสำหรับ POST Method
            </p>
            <p class="text-[11px] leading-relaxed text-blue-900">
              สมมติคุณมี REST API ชื่อ <strong>POST /api/v1/payments</strong><br/>
              - เลือก HTTP Method เป็น <strong>POST</strong><br/>
              - กรอก Default Subpath เป็น <strong>/api/v1/payments</strong><br/>
              - ระบบจะสร้าง Schema ให้ AI ส่ง HTTP Request เข้ามาที่ Endpoint นี้ให้อัตโนมัติ!
            </p>
          </div>

          <div>
            <label class="block font-semibold text-gray-700 mb-1">Tool Identifier / Name *</label>
            <input
              v-model="form.toolName"
              type="text"
              required
              placeholder="e.g. process_payment / create_order"
              class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono"
            />
          </div>

          <div>
            <label class="block font-semibold text-gray-700 mb-1">Tool Description for AI *</label>
            <textarea
              v-model="form.description"
              rows="2"
              required
              placeholder="อธิบายหน้าที่ของ API นี้เพื่อให้ AI รู้ว่าต้องเรียกใช้งานเมื่อใด (เช่น ใช้ชำระเงินตามจำนวนและสกุลเงินที่กำหนด)"
              class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs resize-none"
            ></textarea>
          </div>

          <div class="grid grid-cols-3 gap-3">
            <div>
              <label class="block font-semibold text-gray-700 mb-1">HTTP Method *</label>
              <select
                v-model="restForm.method"
                @change="updateRestSchema"
                class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-bold text-[#1a1b22]"
              >
                <option value="POST">POST (แนะนำสำหรับ Body Payload)</option>
                <option value="GET">GET (คำขออ่านข้อมูล)</option>
                <option value="PUT">PUT (อัปเดตข้อมูล)</option>
                <option value="DELETE">DELETE (ลบข้อมูล)</option>
                <option value="PATCH">PATCH (อัปเดตบางส่วน)</option>
              </select>
            </div>

            <div class="col-span-2">
              <label class="block font-semibold text-gray-700 mb-1">Subpath Endpoint *</label>
              <input
                v-model="restForm.path"
                @input="updateRestSchema"
                type="text"
                placeholder="e.g. /api/v1/payments"
                class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono"
              />
            </div>
          </div>

          <div>
            <label class="block font-semibold text-gray-700 mb-1">
              Sample Request JSON Body / Payload (Optional)
            </label>
            <textarea
              v-model="restForm.samplePayload"
              @input="updateRestSchema"
              rows="3"
              placeholder='{"amount": 100, "currency": "THB", "customer_id": "CUST_123"}'
              class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono resize-none leading-relaxed"
            ></textarea>
            <p class="text-[10px] text-gray-400 mt-1">ใส่ตัวอย่าง JSON Payload เพื่อให้ AI เข้าใจโครงสร้างฟิลด์ข้อมูลที่ต้องส่งไปกับ Body</p>
          </div>
        </template>

        <!-- Advanced JSON Schema Mode -->
        <template v-else>
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
            <label class="block font-semibold text-gray-700 mb-1">Description</label>
            <textarea
              v-model="form.description"
              rows="2"
              placeholder="Describe tool capability..."
              class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs resize-none"
            ></textarea>
          </div>

          <div>
            <label class="block font-semibold text-gray-700 mb-1">JSON Input Schema (OpenAI Spec)</label>
            <textarea
              v-model="form.inputSchema"
              rows="8"
              placeholder='{"type":"object","properties":{...}}'
              class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono leading-relaxed"
            ></textarea>
          </div>
        </template>

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
import { ref, reactive, watch } from 'vue'

const props = defineProps({
  show: Boolean,
  server: Object,
  submitting: Boolean
})

const emit = defineEmits(['close', 'save'])

const activeMode = ref('REST_FORM')

const form = reactive({
  toolName: '',
  description: '',
  inputSchema: ''
})

const restForm = reactive({
  method: 'POST',
  path: '/api/v1/payments',
  samplePayload: '{\n  "amount": 100,\n  "currency": "THB"\n}'
})

watch(() => props.show, (val) => {
  if (val) {
    form.toolName = 'process_payment'
    form.description = 'ส่งคำขอชำระเงินไปยัง REST API Endpoint ผ่าน POST request'
    restForm.method = 'POST'
    restForm.path = '/api/v1/payments'
    restForm.samplePayload = '{\n  "amount": 100,\n  "currency": "THB"\n}'
    updateRestSchema()
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

  const generatedSchema = {
    type: 'object',
    properties: {
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
    },
    required: ['method']
  }

  form.inputSchema = JSON.stringify(generatedSchema, null, 2)
}

function handleSubmit() {
  emit('save', { ...form })
}
</script>
