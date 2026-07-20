<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-xl font-bold text-[#1a1b22] font-heading">Audit Logs</h1>
        <p class="text-sm text-[#4d4732] mt-0.5">Read-only view of all AI interactions (Thailand GMT+7)</p>
      </div>
      <div class="flex items-center gap-2">
        <span class="badge badge-gray text-xs">{{ totalElements }} records</span>
        <button @click="exportCsv" class="btn-secondary text-xs flex items-center gap-1.5" :disabled="loading || !filteredLogs.length">
          <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          Export CSV
        </button>
        <button @click="loadLogs()" class="btn-secondary text-xs flex items-center gap-1.5">
          <svg class="w-3.5 h-3.5" :class="loading && 'animate-spin'" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
          </svg>
          Refresh
        </button>
      </div>
    </div>

    <!-- Filter Bar -->
    <div class="glass rounded-xl p-4 mb-6 border border-[#e8e7f1] grid grid-cols-1 sm:grid-cols-2 md:grid-cols-5 gap-3 items-end">
      <div>
        <label class="label">Search Keyword</label>
        <input v-model="searchQuery" type="text" placeholder="User, prompt, session..." class="input-field py-1.5 text-xs" />
      </div>

      <div>
        <label class="label">Filter by Model</label>
        <select v-model="filterModelId" class="input-field py-1.5 text-xs" @change="handleFilterChange">
          <option value="">All Models</option>
          <option v-for="m in modelsList" :key="m.id" :value="m.id">
            {{ m.name || (m.provider + ' / ' + m.modelName) }}
          </option>
        </select>
      </div>

      <div>
        <label class="label">Start Date</label>
        <input v-model="startDate" type="date" class="input-field py-1.5 text-xs" @change="handleFilterChange" />
      </div>

      <div>
        <label class="label">End Date</label>
        <input v-model="endDate" type="date" class="input-field py-1.5 text-xs" @change="handleFilterChange" />
      </div>

      <div class="flex items-center gap-2">
        <button @click="resetFilters" class="btn-secondary py-1.5 text-xs flex-1">Reset</button>
        <button @click="loadLogs" class="btn-primary py-1.5 text-xs flex-1 flex items-center justify-center gap-1">
          <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
          </svg>
          Search
        </button>
      </div>
    </div>

    <!-- Log table -->
    <DataTable
      :columns="columns"
      :rows="filteredLogs"
      :loading="loading"
      :show-actions="true"
      :current-page="page"
      :total-pages="totalPages"
      @page="changePage"
    >
      <template #cell-username="{ value }">
        <span class="font-bold text-xs text-[#1a1b22]">{{ value || 'Unknown User' }}</span>
      </template>

      <template #cell-modelDisplayName="{ value }">
        <span
          class="font-medium text-xs text-[#705d00] truncate block max-w-[150px] cursor-help"
          :title="value || 'Unknown Model'"
        >
          {{ value || 'Unknown Model' }}
        </span>
      </template>

      <template #cell-prompt="{ row, value }">
        <div 
          @click="openDetails(row)"
          class="text-xs text-[#1a1b22] line-clamp-2 cursor-pointer hover:text-amber-700 transition-colors leading-relaxed"
          title="Click to view full details"
        >
          {{ value }}
        </div>
      </template>

      <template #cell-response="{ row, value }">
        <div 
          @click="openDetails(row)"
          class="text-xs text-[#4d4732] line-clamp-2 cursor-pointer hover:text-amber-700 transition-colors leading-relaxed"
          title="Click to view full details"
        >
          {{ value ?? '—' }}
        </div>
      </template>

      <template #cell-createdAt="{ value }">
        <div class="flex flex-col text-xs text-[#4d4732] font-mono leading-tight whitespace-nowrap">
          <span>{{ formatDateTH(value).split(' ')[0] }}</span>
          <span class="text-[10px] text-[#4d4732] mt-0.5">{{ formatDateTH(value).split(' ')[1] }}</span>
        </div>
      </template>

      <template #actions="{ row }">
        <button 
          @click="openDetails(row)" 
          class="btn-secondary py-1 px-2.5 text-xs flex items-center gap-1 font-medium hover:border-[#ffd700]"
          title="View full prompt & response"
        >
          <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
          </svg>
          View
        </button>
      </template>
    </DataTable>

    <!-- Detail Inspection Modal -->
    <ModalForm
      v-if="selectedLog"
      title="Audit Log Details"
      :wide="true"
      :show-cancel="false"
      confirm-text="Close"
      @close="selectedLog = null"
      @confirm="selectedLog = null"
    >
      <div class="space-y-4 text-xs">
        <!-- Metadata Header Bar -->
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 p-3 bg-[#f4f2fd] rounded-xl border border-[#e8e7f1]">
          <div>
            <span class="text-[#4d4732] block text-[10px] uppercase tracking-wider font-semibold">User</span>
            <span class="font-bold text-[#1a1b22] text-sm">{{ selectedLog.username || 'Unknown' }}</span>
          </div>
          <div>
            <span class="text-[#4d4732] block text-[10px] uppercase tracking-wider font-semibold">Model</span>
            <span class="font-bold text-[#705d00] text-sm mt-0.5 block">{{ selectedLog.modelDisplayName || 'Unknown Model' }}</span>
          </div>
          <div>
            <span class="text-[#4d4732] block text-[10px] uppercase tracking-wider font-semibold">Session ID</span>
            <span class="font-mono text-[#1a1b22] text-xs">{{ selectedLog.sessionId || '—' }}</span>
          </div>
          <div>
            <span class="text-[#4d4732] block text-[10px] uppercase tracking-wider font-semibold">Timestamp (GMT+7)</span>
            <span class="font-mono text-[#1a1b22] text-xs">{{ formatDateTH(selectedLog.createdAt) }}</span>
          </div>
        </div>

        <!-- Full Prompt Container -->
        <div>
          <div class="flex items-center justify-between mb-1.5">
            <label class="font-bold text-[#1a1b22] text-xs flex items-center gap-1.5">
              <span>👤</span> Prompt Message
            </label>
            <button @click="copyText(selectedLog.prompt)" class="text-[11px] text-[#705d00] hover:underline flex items-center gap-1">
              📋 Copy Prompt
            </button>
          </div>
          <div class="p-3 bg-[#fbf8ff] rounded-xl border border-[#e8e7f1] text-[#1a1b22] whitespace-pre-wrap font-sans leading-relaxed max-h-48 overflow-y-auto select-text">
            {{ selectedLog.prompt }}
          </div>
        </div>

        <!-- Full Response Container -->
        <div>
          <div class="flex items-center justify-between mb-1.5">
            <label class="font-bold text-[#1a1b22] text-xs flex items-center gap-1.5">
              <span>🤖</span> LLM Response
            </label>
            <button @click="copyText(selectedLog.response)" class="text-[11px] text-[#705d00] hover:underline flex items-center gap-1">
              📋 Copy Response
            </button>
          </div>
          <div class="p-3 bg-[#ffffff] rounded-xl border border-[#e8e7f1] text-[#1a1b22] whitespace-pre-wrap font-sans leading-relaxed max-h-64 overflow-y-auto select-text shadow-inner">
            {{ selectedLog.response || 'No response recorded' }}
          </div>
        </div>
      </div>
    </ModalForm>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import DataTable from '@/components/DataTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import { adminApi } from '@/api/admin'

const logs          = ref([])
const modelsList    = ref([])
const loading       = ref(true)
const page          = ref(0)
const size          = 20
const totalPages    = ref(1)
const totalElements = ref(0)

const searchQuery   = ref('')
const filterModelId = ref('')
const startDate     = ref('')
const endDate       = ref('')
const selectedLog   = ref(null)

const filteredLogs = computed(() => {
  if (!searchQuery.value.trim()) return logs.value
  const q = searchQuery.value.toLowerCase().trim()
  return logs.value.filter(log =>
    (log.username && log.username.toLowerCase().includes(q)) ||
    (log.prompt && log.prompt.toLowerCase().includes(q)) ||
    (log.response && log.response.toLowerCase().includes(q)) ||
    (log.sessionId && log.sessionId.toLowerCase().includes(q)) ||
    (log.modelDisplayName && log.modelDisplayName.toLowerCase().includes(q))
  )
})

const columns = [
  { key: 'username',         label: 'User',         class: 'w-[10%] min-w-[70px] pr-2' },
  { key: 'modelDisplayName', label: 'Model',        class: 'w-[18%] min-w-[150px] pr-2' },
  { key: 'prompt',           label: 'Prompt',       class: 'w-[25%] min-w-[160px] pr-2' },
  { key: 'response',         label: 'Response',     class: 'w-[37%] min-w-[280px] pr-2' },
  { key: 'createdAt',        label: 'Time (GMT+7)', class: 'w-[10%] min-w-[100px]' },
]

onMounted(async () => {
  try {
    const modelsRes = await adminApi.getModels()
    modelsList.value = modelsRes.data
  } catch (e) {
    console.error('Failed to load models list:', e)
  }
  loadLogs()
})

async function loadLogs() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size,
      modelId: filterModelId.value || undefined,
      startDate: startDate.value || undefined,
      endDate: endDate.value || undefined,
    }
    const res = (await adminApi.getAuditLogs(params)).data
    logs.value          = res.content || []
    totalPages.value    = res.totalPages || 1
    totalElements.value = res.totalElements || 0
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  page.value = 0
  loadLogs()
}

function resetFilters() {
  searchQuery.value   = ''
  filterModelId.value = ''
  startDate.value     = ''
  endDate.value       = ''
  page.value          = 0
  loadLogs()
}

function exportCsv() {
  if (!filteredLogs.value || filteredLogs.value.length === 0) {
    alert('No audit logs available to export')
    return
  }
  const headers = ['Log ID', 'User ID', 'Username', 'Model ID', 'Model Name', 'Session ID', 'Prompt', 'Response', 'Created At (GMT+7)']
  const rows = filteredLogs.value.map(log => [
    `"${log.id || ''}"`,
    `"${log.userId || ''}"`,
    `"${(log.username || '').replace(/"/g, '""')}"`,
    `"${log.modelId || ''}"`,
    `"${(log.modelDisplayName || '').replace(/"/g, '""')}"`,
    `"${log.sessionId || ''}"`,
    `"${(log.prompt || '').replace(/"/g, '""')}"`,
    `"${(log.response || '').replace(/"/g, '""')}"`,
    `"${formatDateTH(log.createdAt)}"`
  ])
  const csvContent = 'data:text/csv;charset=utf-8,\uFEFF' + [headers.join(','), ...rows.map(e => e.join(','))].join('\n')
  const encodedUri = encodeURI(csvContent)
  const link = document.createElement('a')
  link.setAttribute('href', encodedUri)
  link.setAttribute('download', `audit_logs_${startDate.value || 'all'}_to_${endDate.value || 'all'}.csv`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

function changePage(p) {
  page.value = p
  loadLogs()
}

function openDetails(log) {
  selectedLog.value = log
}

function copyText(text) {
  if (!text) return
  navigator.clipboard.writeText(text)
}

function formatDateTH(iso) {
  if (!iso) return '—'
  // Ensure ISO string is treated as UTC if timezone suffix is absent
  const utcStr = (iso.endsWith('Z') || iso.includes('+')) ? iso : (iso + 'Z')
  const date = new Date(utcStr)
  
  const dateStr = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Bangkok',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(date)

  const timeStr = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Bangkok',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(date)

  return `${dateStr} ${timeStr}`
}
</script>
