<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-[#1a1b22] tracking-tight font-heading">🛠️ MCP Servers</h1>
        <p class="text-xs text-gray-500 mt-1">Configure and manage internal tool endpoints, API microservices, and credentials for AI Tool Calling.</p>
      </div>
      <button
        @click="openModal()"
        class="inline-flex items-center gap-2 px-4 py-2 bg-[#ffd700] hover:bg-[#e9c400] text-[#1a1b22] text-xs font-semibold rounded-xl shadow-sm transition-all"
      >
        <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4" />
        </svg>
        Add MCP Server
      </button>
    </div>

    <!-- Stats summary -->
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
      <div class="bg-white border border-[#e8e7f1] rounded-2xl p-4 shadow-sm flex items-center gap-4">
        <div class="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center font-bold text-lg">
          🛠️
        </div>
        <div>
          <p class="text-xs text-gray-500 font-medium">Total Servers</p>
          <p class="text-xl font-bold text-[#1a1b22] mt-0.5">{{ servers.length }}</p>
        </div>
      </div>
      <div class="bg-white border border-[#e8e7f1] rounded-2xl p-4 shadow-sm flex items-center gap-4">
        <div class="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center font-bold text-lg">
          🟢
        </div>
        <div>
          <p class="text-xs text-gray-500 font-medium">Active Servers</p>
          <p class="text-xl font-bold text-[#1a1b22] mt-0.5">{{ servers.filter(s => s.isActive).length }}</p>
        </div>
      </div>
      <div class="bg-white border border-[#e8e7f1] rounded-2xl p-4 shadow-sm flex items-center gap-4">
        <div class="w-10 h-10 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center font-bold text-lg">
          🔑
        </div>
        <div>
          <p class="text-xs text-gray-500 font-medium">Secured with Auth Key</p>
          <p class="text-xl font-bold text-[#1a1b22] mt-0.5">{{ servers.filter(s => s.hasApiKey).length }}</p>
        </div>
      </div>
    </div>

    <!-- Error message -->
    <div v-if="error" class="p-3 text-xs bg-red-50 text-red-600 rounded-xl border border-red-200 flex items-center justify-between">
      <span>{{ error }}</span>
      <button @click="error = null" class="font-bold">&times;</button>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="text-center py-12 text-gray-400 text-xs">
      Loading MCP Servers...
    </div>

    <!-- Empty state -->
    <div v-else-if="servers.length === 0" class="bg-white border border-[#e8e7f1] rounded-2xl p-12 text-center shadow-sm">
      <div class="w-12 h-12 rounded-2xl bg-[#1a1b22]/5 text-gray-400 flex items-center justify-center mx-auto mb-3 text-xl">
        🔌
      </div>
      <h3 class="text-sm font-bold text-[#1a1b22]">No MCP Servers Configured</h3>
      <p class="text-xs text-gray-400 max-w-sm mx-auto mt-1 mb-4">Register your company's internal microservices, database connectors, or search APIs to empower AI Tool Calling.</p>
      <button
        @click="openModal()"
        class="px-4 py-2 bg-[#ffd700] hover:bg-[#e9c400] text-[#1a1b22] text-xs font-semibold rounded-xl shadow-sm"
      >
        + Add First MCP Server
      </button>
    </div>

    <!-- Table -->
    <div v-else class="bg-white border border-[#e8e7f1] rounded-2xl shadow-sm overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs">
          <thead class="bg-[#fcfbfe] border-b border-[#e8e7f1] text-gray-500 font-semibold uppercase tracking-wider text-[11px]">
            <tr>
              <th class="py-3.5 px-4">Server Name</th>
              <th class="py-3.5 px-4">Endpoint URL</th>
              <th class="py-3.5 px-4">Security</th>
              <th class="py-3.5 px-4">Status</th>
              <th class="py-3.5 px-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-[#e8e7f1]">
            <tr v-for="srv in servers" :key="srv.id" class="hover:bg-[#fbf8ff]/60 transition-colors">
              <td class="py-3.5 px-4">
                <p class="font-bold text-[#1a1b22]">{{ srv.name }}</p>
                <p v-if="srv.description" class="text-[11px] text-gray-400 mt-0.5 line-clamp-1">{{ srv.description }}</p>
              </td>
              <td class="py-3.5 px-4 font-mono text-[11px] text-gray-600">
                {{ srv.endpointUrl }}
              </td>
              <td class="py-3.5 px-4">
                <span
                  :class="[
                    'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold border',
                    srv.hasApiKey
                      ? 'bg-blue-50 text-blue-700 border-blue-200'
                      : 'bg-gray-50 text-gray-500 border-gray-200'
                  ]"
                >
                  {{ srv.hasApiKey ? '🔒 Encrypted Key' : '🔓 No Auth' }}
                </span>
              </td>
              <td class="py-3.5 px-4">
                <span
                  :class="[
                    'inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[10px] font-semibold border',
                    srv.isActive
                      ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                      : 'bg-gray-100 text-gray-400 border-gray-200'
                  ]"
                >
                  <span :class="['w-1.5 h-1.5 rounded-full', srv.isActive ? 'bg-emerald-500 animate-pulse' : 'bg-gray-400']" />
                  {{ srv.isActive ? 'Active' : 'Inactive' }}
                </span>
              </td>
              <td class="py-3.5 px-4 text-right space-x-2">
                <button
                  @click="testServer(srv)"
                  :disabled="testingId === srv.id"
                  class="px-2.5 py-1 text-[11px] bg-amber-50 hover:bg-amber-100 text-amber-700 rounded-lg border border-amber-200 transition-colors font-medium disabled:opacity-50"
                >
                  {{ testingId === srv.id ? 'Testing...' : '⚡ Test' }}
                </button>
                <button
                  @click="openModal(srv)"
                  class="px-2.5 py-1 text-[11px] bg-gray-50 hover:bg-gray-100 text-gray-700 rounded-lg border border-gray-200 transition-colors font-medium"
                >
                  Edit
                </button>
                <button
                  @click="confirmDelete(srv)"
                  class="px-2.5 py-1 text-[11px] bg-red-50 hover:bg-red-100 text-red-600 rounded-lg border border-red-200 transition-colors font-medium"
                >
                  Delete
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Create/Edit Modal -->
    <div v-if="showModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-fade-in">
      <div class="bg-white rounded-2xl max-w-md w-full border border-[#e8e7f1] shadow-2xl p-6 relative">
        <h3 class="text-base font-bold text-[#1a1b22] font-heading mb-4">
          {{ editingId ? 'Edit MCP Server' : 'Add New MCP Server' }}
        </h3>

        <form @submit.prevent="saveServer" class="space-y-4 text-xs">
          <div>
            <label class="block font-medium text-gray-700 mb-1">Server Name *</label>
            <input
              v-model="form.name"
              type="text"
              required
              placeholder="e.g. Customer DB MCP"
              class="w-full px-3 py-2 rounded-xl border border-[#e8e7f1] focus:outline-none focus:ring-2 focus:ring-[#ffd700]"
            />
          </div>

          <div>
            <label class="block font-medium text-gray-700 mb-1">Endpoint URL *</label>
            <input
              v-model="form.endpointUrl"
              type="url"
              required
              placeholder="https://mcp-db.internal.co.th/mcp"
              class="w-full px-3 py-2 rounded-xl border border-[#e8e7f1] focus:outline-none focus:ring-2 focus:ring-[#ffd700] font-mono text-[11px]"
            />
          </div>

          <div>
            <label class="block font-medium text-gray-700 mb-1">API Key / Secret Token (Optional)</label>
            <input
              v-model="form.apiKey"
              type="password"
              placeholder="Leave blank to keep current key"
              class="w-full px-3 py-2 rounded-xl border border-[#e8e7f1] focus:outline-none focus:ring-2 focus:ring-[#ffd700]"
            />
            <p class="text-[10px] text-gray-400 mt-1">Encrypted safely with AES-256-GCM before database storage.</p>
          </div>

          <div>
            <label class="block font-medium text-gray-700 mb-1">Description</label>
            <textarea
              v-model="form.description"
              rows="2"
              placeholder="Provides tools for querying internal database schemas..."
              class="w-full px-3 py-2 rounded-xl border border-[#e8e7f1] focus:outline-none focus:ring-2 focus:ring-[#ffd700]"
            />
          </div>

          <div class="flex items-center gap-2 pt-1">
            <input
              v-model="form.isActive"
              type="checkbox"
              id="isActive"
              class="rounded text-[#ffd700] focus:ring-[#ffd700]"
            />
            <label for="isActive" class="font-medium text-gray-700">Enable this MCP Server for AI Tool Calling</label>
          </div>

          <div class="flex justify-end gap-2 pt-4 border-t border-[#e8e7f1]">
            <button
              type="button"
              @click="closeModal"
              class="px-4 py-2 text-gray-600 bg-gray-50 hover:bg-gray-100 rounded-xl font-medium"
            >
              Cancel
            </button>
            <button
              type="submit"
              :disabled="saving"
              class="px-4 py-2 bg-[#ffd700] hover:bg-[#e9c400] text-[#1a1b22] font-semibold rounded-xl shadow-sm disabled:opacity-50"
            >
              {{ saving ? 'Saving...' : 'Save MCP Server' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- Connection Test Result Modal -->
    <div v-if="testResult" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-fade-in">
      <div class="bg-white rounded-2xl max-w-lg w-full border border-[#e8e7f1] shadow-2xl p-6">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-base font-bold text-[#1a1b22] font-heading flex items-center gap-2">
            <span>⚡ Connection Test Result</span>
            <span
              :class="[
                'text-[10px] px-2 py-0.5 rounded-full font-sans font-semibold border',
                testResult.status === 'CONNECTED' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-red-50 text-red-600 border-red-200'
              ]"
            >
              {{ testResult.status }}
            </span>
          </h3>
          <button @click="testResult = null" class="text-gray-400 hover:text-gray-600 font-bold">&times;</button>
        </div>

        <div class="bg-[#1a1b26] p-3 rounded-xl text-gray-200 font-mono text-xs overflow-x-auto border border-[#2e3047]">
          <pre class="whitespace-pre-wrap break-all">{{ JSON.stringify(testResult, null, 2) }}</pre>
        </div>

        <div class="mt-4 flex justify-end">
          <button
            @click="testResult = null"
            class="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 text-xs font-semibold rounded-xl"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'

const servers = ref([])
const loading = ref(false)
const saving = ref(false)
const testingId = ref(null)
const error = ref(null)
const testResult = ref(null)

const showModal = ref(false)
const editingId = ref(null)
const form = ref({
  name: '',
  endpointUrl: '',
  apiKey: '',
  description: '',
  isActive: true
})

async function fetchServers() {
  loading.value = true
  error.value = null
  try {
    const { data } = await apiClient.get('/api/v1/admin/mcp-servers')
    servers.value = data
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load MCP servers'
  } finally {
    loading.value = false
  }
}

function openModal(server = null) {
  if (server) {
    editingId.value = server.id
    form.value = {
      name: server.name,
      endpointUrl: server.endpointUrl,
      apiKey: '',
      description: server.description || '',
      isActive: server.isActive
    }
  } else {
    editingId.value = null
    form.value = {
      name: '',
      endpointUrl: '',
      apiKey: '',
      description: '',
      isActive: true
    }
  }
  showModal.value = true
}

function closeModal() {
  showModal.value = false
  editingId.value = null
}

async function saveServer() {
  saving.value = true
  error.value = null
  try {
    if (editingId.value) {
      await apiClient.put(`/api/v1/admin/mcp-servers/${editingId.value}`, form.value)
    } else {
      await apiClient.post('/api/v1/admin/mcp-servers', form.value)
    }
    closeModal()
    fetchServers()
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to save MCP server'
  } finally {
    saving.value = false
  }
}

async function confirmDelete(server) {
  if (!confirm(`Are you sure you want to delete MCP Server "${server.name}"?`)) return
  try {
    await apiClient.delete(`/api/v1/admin/mcp-servers/${server.id}`)
    fetchServers()
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to delete MCP server'
  }
}

async function testServer(server) {
  testingId.value = server.id
  testResult.value = null
  try {
    const { data } = await apiClient.post(`/api/v1/admin/mcp-servers/${server.id}/test`)
    testResult.value = data
  } catch (e) {
    testResult.value = {
      status: 'DISCONNECTED',
      error: e.response?.data?.message || e.message
    }
  } finally {
    testingId.value = null
  }
}

onMounted(() => {
  fetchServers()
})
</script>
