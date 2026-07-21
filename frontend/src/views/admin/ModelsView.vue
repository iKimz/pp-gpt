<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-xl font-bold text-[#1a1b22] font-heading">AI Models</h1>
        <p class="text-sm text-[#4d4732] mt-0.5">Configure AI provider endpoints and credentials</p>
      </div>
      <button @click="openCreate()" class="btn-primary flex items-center gap-2">
        <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
        </svg>
        Add Model
      </button>
    </div>

    <DataTable :columns="columns" :rows="models" :loading="loading" @edit="openEdit" @delete="handleDelete">
      <template #cell-active="{ value }">
        <span :class="value ? 'badge badge-green' : 'badge badge-gray'">{{ value ? 'Active' : 'Inactive' }}</span>
      </template>
      <template #cell-name="{ row, value }">
        <span class="font-bold text-[#1a1b22] text-xs">{{ value || row.modelName }}</span>
      </template>
      <template #cell-provider="{ value }">
        <span :class="providerBadgeClass(value)">{{ value }}</span>
      </template>
      <template #cell-modelType="{ row, value }">
        <span v-if="value === 'GUARDRAIL'" class="badge badge-orange inline-flex items-center gap-1 whitespace-nowrap">🛡️ Guardrail</span>
        <div v-else class="flex items-center gap-1">
          <span class="badge badge-blue inline-flex items-center gap-1 whitespace-nowrap">💬 Generation</span>
          <span v-if="row.supportsVision" class="badge badge-purple inline-flex items-center gap-1 whitespace-nowrap">👁️ Vision</span>
        </div>
      </template>
    </DataTable>

    <ModalForm
      v-if="showModal"
      :title="editing ? 'Edit Model' : 'New Model'"
      :loading="saving"
      :wide="true"
      confirm-text="Save Model"
      @close="showModal = false"
      @confirm="handleSave"
    >
      <!-- Tab Navigation -->
      <div class="flex gap-1 mb-5 p-1 bg-[#f4f2fd] rounded-xl border border-[#e8e7f1]">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          @click="activeTab = tab.id"
          :class="[
            'flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-lg text-sm font-medium transition-all duration-200',
            activeTab === tab.id
              ? 'bg-[#ffd700] text-[#1a1b22] font-semibold shadow-sm'
              : 'text-[#4d4732] hover:text-[#1a1b22] hover:bg-white'
          ]"
        >
          <span>{{ tab.icon }}</span>
          {{ tab.label }}
          <span
            v-if="tab.id === 'general' && form.credentials && !isValidJson"
            class="w-1.5 h-1.5 rounded-full bg-red-500 ml-1"
          />
        </button>
      </div>

      <!-- TAB 1: General -->
      <div v-show="activeTab === 'general'" class="space-y-4">
        
        <div class="space-y-2">
          <label class="label">Model Type</label>
          <div class="flex gap-4 p-2 bg-surface-800 rounded-lg border border-surface-600">
            <label class="flex-1 flex items-center justify-center gap-2 cursor-pointer text-sm text-black-200 hover:bg-surface-700 py-1.5 rounded transition-colors">
              <input type="radio" v-model="form.modelType" value="GENERATION" class="accent-brand-500 w-4 h-4" />
              💬 Generation (Standard Chat)
            </label>
            <label class="flex-1 flex items-center justify-center gap-2 cursor-pointer text-sm text-black-200 hover:bg-surface-700 py-1.5 rounded transition-colors">
              <input type="radio" v-model="form.modelType" value="GUARDRAIL" class="accent-brand-500 w-4 h-4" />
              🛡️ Guardrail (Safety Filter)
            </label>
          </div>
        </div>

        <div v-if="form.modelType === 'GENERATION'" class="p-3 bg-surface-800 rounded-lg border border-surface-600">
          <label class="flex items-center gap-2 cursor-pointer text-sm font-medium text-[#1a1b22]">
            <input type="checkbox" v-model="form.supportsVision" class="accent-brand-500 w-4 h-4" />
            👁️ Supports Image & File Uploads (Multimodal Vision)
          </label>
          <p class="text-[11px] text-[#4d4732] mt-1 pl-6">Enable for models capable of processing images (e.g., GPT-4o, Claude 3.5 Sonnet).</p>
        </div>

        <div>
          <label class="label">Display Name <span class="text-xs text-[#4d4732] font-normal">(Optional friendly name)</span></label>
          <input v-model="form.name" class="input-field" placeholder="e.g. GPT-4o Enterprise, Claude 3.5 Sonnet" />
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="label">Provider</label>
            <select v-model="form.provider" class="input-field" @change="prefillPlaceholder">
              <option value="OPENAI">OPENAI</option>
              <option value="AZURE">AZURE</option>
              <option value="AWS_BEDROCK">AWS_BEDROCK</option>
              <option value="ANTHROPIC">ANTHROPIC</option>
              <option value="GOOGLE">GOOGLE</option>
              <option value="CUSTOM">CUSTOM</option>
            </select>
          </div>
          <div>
            <label class="label">Model Name / Deployment</label>
            <input v-model="form.modelName" class="input-field" :placeholder="modelNamePlaceholder" />
          </div>
        </div>

        <div>
          <label class="label">Endpoint URL</label>
          <input v-model="form.endpointUrl" class="input-field" :placeholder="endpointPlaceholder" />
        </div>

        <div>
          <label class="label">
            Credentials (JSON)
            <span class="text-gray-600 font-normal ml-1">(leave empty to keep existing)</span>
          </label>
          <textarea
            v-model="form.credentials"
            rows="4"
            class="input-field font-mono text-xs resize-y leading-relaxed"
            :placeholder="credentialsPlaceholder"
            spellcheck="false"
          />
          <div class="mt-2 p-3 glass rounded-lg border border-surface-500 space-y-2">
            <p class="text-xs font-semibold text-gray-400 mb-1">📋 Format Reference</p>
            <div
              v-for="fmt in credentialFormats" :key="fmt.label"
              class="flex items-start gap-2 cursor-pointer hover:bg-surface-600 rounded px-2 py-1 transition-colors"
              @click="insertTemplate(fmt.json)"
            >
              <span :class="fmt.badgeClass" class="badge text-[10px] mt-0.5 shrink-0">{{ fmt.label }}</span>
              <code class="text-[11px] text-gray-400 break-all leading-relaxed">{{ fmt.json }}</code>
            </div>
            <p class="text-[10px] text-gray-600 mt-1">↑ Click a format to insert it as a template</p>
          </div>
          <div v-if="form.credentials" class="mt-1.5 flex items-center gap-1.5 text-xs">
            <template v-if="isValidJson">
              <svg class="w-3.5 h-3.5 text-emerald-400" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/>
              </svg>
              <span class="text-emerald-400">Valid JSON</span>
            </template>
            <template v-else>
              <svg class="w-3.5 h-3.5 text-red-400" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
              </svg>
              <span class="text-red-400">Invalid JSON — will not be saved</span>
            </template>
          </div>
        </div>

        <div class="flex items-center gap-3 pt-1">
          <input type="checkbox" v-model="form.active" id="model-active" class="accent-brand-500 w-4 h-4" />
          <label for="model-active" class="text-sm text-black-300 cursor-pointer">Active</label>
        </div>
      </div>

      <!-- TAB 2: Advanced -->
      <div v-show="activeTab === 'advanced'" class="space-y-5">
        <div>
          <label class="label flex items-center justify-between">
            <span>Temperature</span>
            <span class="font-mono text-brand-400 font-bold text-base tabular-nums">
              {{ Number(form.temperature).toFixed(1) }}
            </span>
          </label>
          <input
            type="range" v-model.number="form.temperature"
            min="0" max="2" step="0.1"
            class="w-full accent-brand-500 cursor-pointer mt-1"
          />
          <div class="flex justify-between text-[10px] text-gray-600 mt-0.5">
            <span>0.0 — Precise</span>
            <span>1.0 — Balanced</span>
            <span>2.0 — Creative</span>
          </div>
          <p class="text-[11px] text-gray-500 mt-1.5">Higher values make output more random and creative.</p>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="label">Timeout (ms)</label>
            <input type="number" v-model.number="form.timeoutMs" min="1000" step="1000" class="input-field" />
            <p class="text-[11px] text-gray-500 mt-1">Max wait time before aborting.</p>
          </div>
          <div>
            <label class="label">Max History Messages</label>
            <input type="number" v-model.number="form.maxHistoryMessages" min="0" max="100" class="input-field" />
            <p class="text-[11px] text-gray-500 mt-1">Context turns sent to the provider.</p>
          </div>
        </div>

        <div>
          <label class="label flex items-center gap-2">
            System Prompt
            <span class="text-gray-600 font-normal text-xs">(optional)</span>
            <span v-if="form.systemPrompt" class="badge badge-purple text-[10px] ml-auto">Configured</span>
          </label>
          <textarea
            v-model="form.systemPrompt"
            rows="6"
            class="input-field resize-y leading-relaxed text-sm"
            placeholder="You are a helpful assistant. Answer questions concisely and accurately."
            spellcheck="true"
          />
          <p class="text-[11px] text-gray-500 mt-1">Defines the AI's persona, rules, and behavior globally for this model.</p>
        </div>
      </div>
    </ModalForm>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import DataTable from '@/components/DataTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import { adminApi } from '@/api/admin'

const models    = ref([])
const loading   = ref(true)
const saving    = ref(false)
const showModal = ref(false)
const editing   = ref(null)
const activeTab = ref('general')

const tabs = [
  { id: 'general',  label: 'General',  icon: '⚙️' },
  { id: 'advanced', label: 'Advanced', icon: '🧠' },
]

const form = reactive({
  name:                '',
  provider:            'OPENAI',
  modelName:           '',
  endpointUrl:         '',
  credentials:         '',
  active:              true,
  temperature:         0.7,
  timeoutMs:           30000,
  maxHistoryMessages:  10,
  systemPrompt:        '',
  modelType:           'GENERATION',
  supportsVision:      false
})

// ─── Credential format reference cards ────────────────────────────────
const credentialFormats = [
  { label: 'OpenAI',            badgeClass: 'badge-purple', json: '{"apiKey": "sk-..."}' },
  { label: 'Azure',             badgeClass: 'badge-blue',   json: '{"apiKey": "...", "apiVersion": "2024-02-01"}' },
  { label: 'AWS Bedrock (Key)', badgeClass: 'badge-green',  json: '{"apiKey": "ABSK...", "region": "us-east-1"}' },
  { label: 'AWS Bedrock (IAM)', badgeClass: 'badge-green',  json: '{"accessKeyId": "...", "secretAccessKey": "...", "region": "us-east-1"}' },
  { label: 'Anthropic',         badgeClass: 'badge-gray',   json: '{"apiKey": "sk-ant-..."}' },
]

// ─── Table columns ─────────────────────────────────────────────────────
const columns = [
  { key: 'modelType',            label: 'Type'          },
  { key: 'name',                 label: 'Display Name'  },
  { key: 'provider',             label: 'Provider'      },
  { key: 'modelName',            label: 'API Model'     },
  { key: 'endpointUrl',          label: 'Endpoint'      },
  { key: 'active',               label: 'Status'        },
]

// ─── Computed placeholders ─────────────────────────────────────────────
const endpointPlaceholder = computed(() => {
  switch (form.provider) {
    case 'AZURE':       return 'https://<resource>.openai.azure.com/openai/deployments/<deployment>/chat/completions'
    case 'AWS_BEDROCK': return 'Leave blank to use AWS default region endpoint, or enter a custom proxy URL.'
    case 'ANTHROPIC':   return 'https://api.anthropic.com/v1/messages'
    default:            return 'https://api.openai.com/v1/chat/completions'
  }
})

const modelNamePlaceholder = computed(() => {
  switch (form.provider) {
    case 'AZURE':       return 'my-deployment-name'
    case 'AWS_BEDROCK': return 'anthropic.claude-3-5-sonnet-20241022-v2:0'
    case 'ANTHROPIC':   return 'claude-3-5-sonnet-20241022'
    default:            return 'gpt-4o'
  }
})

const credentialsPlaceholder = computed(() => {
  switch (form.provider) {
    case 'AZURE':       return '{\n  "apiKey": "your-azure-key",\n  "apiVersion": "2024-02-01"\n}'
    case 'AWS_BEDROCK': return 'Option 1: API Key\n{\n  "apiKey": "ABSK...",\n  "region": "us-east-1"\n}\n\nOption 2: IAM Credentials\n{\n  "accessKeyId": "...",\n  "secretAccessKey": "...",\n  "region": "us-east-1"\n}'
    default:            return '{\n  "apiKey": "sk-..."\n}'
  }
})

const isValidJson = computed(() => {
  if (!form.credentials || !form.credentials.trim()) return true
  try { JSON.parse(form.credentials); return true }
  catch { return false }
})

function providerBadgeClass(provider) {
  const map = {
    OPENAI:      'badge badge-purple',
    AZURE:       'badge badge-blue',
    AWS_BEDROCK: 'badge badge-green',
    ANTHROPIC:   'badge badge-gray',
    GOOGLE:      'badge badge-blue',
  }
  return map[provider] || 'badge badge-gray'
}

onMounted(async () => {
  loading.value = true
  try { models.value = (await adminApi.getModels()).data }
  finally { loading.value = false }
})

function openCreate() {
  editing.value = null
  activeTab.value = 'general'
  Object.assign(form, {
    name: '', provider: 'OPENAI', modelName: '', endpointUrl: '', credentials: '', active: true,
    temperature: 0.7, timeoutMs: 30000, maxHistoryMessages: 10, systemPrompt: '', modelType: 'GENERATION',
    supportsVision: false
  })
  showModal.value = true
}

function openEdit(model) {
  editing.value = model
  activeTab.value = 'general'
  Object.assign(form, {
    ...model,
    name: model.name || '',
    credentials: '',
    systemPrompt: model.systemPrompt || '',
    modelType: model.modelType || 'GENERATION',
    supportsVision: !!model.supportsVision
  })
  showModal.value = true
}

function insertTemplate(json) {
  form.credentials = json
}

function prefillPlaceholder() {
  form.credentials = ''
}

async function handleSave() {
  if (form.credentials && !isValidJson.value) {
    alert('Credentials field contains invalid JSON. Please fix it before saving.')
    activeTab.value = 'general'
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (editing.value) await adminApi.updateModel(editing.value.id, payload)
    else await adminApi.createModel(payload)
    showModal.value = false
    models.value = (await adminApi.getModels()).data
  } catch (e) {
    alert(e.response?.data?.message || 'Save failed')
  } finally {
    saving.value = false
  }
}

async function handleDelete(m) {
  if (!confirm(`Delete model "${m.modelName}"?`)) return
  await adminApi.deleteModel(m.id)
  models.value = (await adminApi.getModels()).data
}
</script>
