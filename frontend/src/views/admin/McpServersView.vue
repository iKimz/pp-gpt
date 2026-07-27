<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-[#1a1b22] tracking-tight font-heading">🛠️ MCP Servers & Tool Discovery</h1>
        <p class="text-xs text-gray-500 mt-1">Manage MCP servers, discover capabilities, and configure selective tool schemas for LLM context optimization.</p>
      </div>
      <div class="flex items-center gap-2">
        <button
          @click="syncAllTools"
          :disabled="syncingAll"
          class="inline-flex items-center gap-2 px-3.5 py-2 bg-white hover:bg-gray-50 text-gray-700 text-xs font-semibold rounded-xl border border-[#e8e7f1] shadow-sm transition-all disabled:opacity-50"
        >
          <span :class="['text-sm', syncingAll ? 'animate-spin' : '']">🔄</span>
          {{ syncingAll ? 'Syncing All...' : 'Sync All Tools' }}
        </button>
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
    </div>

    <!-- Stats Summary -->
    <div class="grid grid-cols-1 sm:grid-cols-4 gap-4">
      <div class="bg-white border border-[#e8e7f1] rounded-2xl p-4 shadow-sm flex items-center gap-4">
        <div class="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center font-bold text-lg">🛠️</div>
        <div>
          <p class="text-xs text-gray-500 font-medium">Total Servers</p>
          <p class="text-xl font-bold text-[#1a1b22] mt-0.5">{{ servers.length }}</p>
        </div>
      </div>
      <div class="bg-white border border-[#e8e7f1] rounded-2xl p-4 shadow-sm flex items-center gap-4">
        <div class="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center font-bold text-lg">🟢</div>
        <div>
          <p class="text-xs text-gray-500 font-medium">Active Servers</p>
          <p class="text-xl font-bold text-[#1a1b22] mt-0.5">{{ servers.filter(s => s.isActive).length }}</p>
        </div>
      </div>
      <div class="bg-white border border-[#e8e7f1] rounded-2xl p-4 shadow-sm flex items-center gap-4">
        <div class="w-10 h-10 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center font-bold text-lg">🔑</div>
        <div>
          <p class="text-xs text-gray-500 font-medium">Static API Key</p>
          <p class="text-xl font-bold text-[#1a1b22] mt-0.5">{{ servers.filter(s => s.authType === 'STATIC_KEY' && s.hasApiKey).length }}</p>
        </div>
      </div>
      <div class="bg-white border border-[#e8e7f1] rounded-2xl p-4 shadow-sm flex items-center gap-4">
        <div class="w-10 h-10 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center font-bold text-lg">🛡️</div>
        <div>
          <p class="text-xs text-gray-500 font-medium">OAuth 2.0 Auth</p>
          <p class="text-xl font-bold text-[#1a1b22] mt-0.5">{{ servers.filter(s => s.authType === 'OAUTH2').length }}</p>
        </div>
      </div>
    </div>

    <!-- Error / Success Notice -->
    <div v-if="error" class="p-3 text-xs bg-red-50 text-red-600 rounded-xl border border-red-200 flex items-center justify-between">
      <span>{{ error }}</span>
      <button @click="error = null" class="font-bold">&times;</button>
    </div>
    <div v-if="successMsg" class="p-3 text-xs bg-emerald-50 text-emerald-700 rounded-xl border border-emerald-200 flex items-center justify-between">
      <span>{{ successMsg }}</span>
      <button @click="successMsg = null" class="font-bold">&times;</button>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="text-center py-12 text-gray-400 text-xs">
      Loading MCP Servers...
    </div>

    <!-- Empty State -->
    <div v-else-if="servers.length === 0" class="bg-white border border-[#e8e7f1] rounded-2xl p-12 text-center shadow-sm">
      <div class="w-12 h-12 rounded-2xl bg-[#1a1b22]/5 text-gray-400 flex items-center justify-center mx-auto mb-3 text-xl">🔌</div>
      <h3 class="text-sm font-bold text-[#1a1b22]">No MCP Servers Configured</h3>
      <p class="text-xs text-gray-400 max-w-sm mx-auto mt-1 mb-4">Register internal microservices, database connectors, or public remote MCP servers (Firecrawl, GitHub, Tavily, etc.).</p>
      <button @click="openModal()" class="px-4 py-2 bg-[#ffd700] hover:bg-[#e9c400] text-[#1a1b22] text-xs font-semibold rounded-xl shadow-sm">
        + Add First MCP Server
      </button>
    </div>

    <!-- Servers Table -->
    <div v-else class="bg-white border border-[#e8e7f1] rounded-2xl shadow-sm overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs">
          <thead class="bg-[#fcfbfe] border-b border-[#e8e7f1] text-gray-500 font-semibold uppercase tracking-wider text-[11px]">
            <tr>
              <th class="py-3.5 px-4">Server Name</th>
              <th class="py-3.5 px-4">Endpoint URL</th>
              <th class="py-3.5 px-4">Protocol Capabilities</th>
              <th class="py-3.5 px-4">Auth Type</th>
              <th class="py-3.5 px-4">Status</th>
              <th class="py-3.5 px-4">Discovered Items</th>
              <th class="py-3.5 px-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-[#e8e7f1]">
            <template v-for="srv in servers" :key="srv.id">
              <tr class="hover:bg-[#fbf8ff]/60 transition-colors">
                <td class="py-3.5 px-4">
                  <p class="font-bold text-[#1a1b22]">{{ srv.name }}</p>
                  <p v-if="srv.description" class="text-[11px] text-gray-400 mt-0.5 line-clamp-1">{{ srv.description }}</p>
                </td>
                <td class="py-3.5 px-4 font-mono text-[11px] text-gray-600 max-w-xs truncate">
                  {{ srv.endpointUrl }}
                </td>
                <td class="py-3.5 px-4">
                  <div class="flex items-center gap-1 flex-wrap">
                    <span v-if="srv.capabilityStatus === 'NON_MCP_REST'" class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                      ⚠️ Legacy REST / Manual
                    </span>
                    <template v-else>
                      <span v-if="srv.supportsTools !== false" class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-blue-50 text-blue-700 border border-blue-200">🛠️ Tools</span>
                      <span v-if="srv.supportsResources" class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">📁 Resources</span>
                      <span v-if="srv.supportsPrompts" class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-purple-50 text-purple-700 border border-purple-200">📝 Prompts</span>
                    </template>
                  </div>
                </td>
                <td class="py-3.5 px-4">
                  <span
                    :class="[
                      'inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold border',
                      srv.authType === 'OAUTH2'
                        ? 'bg-purple-50 text-purple-700 border-purple-200'
                        : srv.hasApiKey
                          ? 'bg-blue-50 text-blue-700 border-blue-200'
                          : 'bg-gray-50 text-gray-600 border-gray-200'
                    ]"
                  >
                    <span>{{ srv.authType === 'OAUTH2' ? '🛡️ OAuth 2.0' : srv.hasApiKey ? '🔑 API Key' : '🔓 None' }}</span>
                  </span>
                </td>
                <td class="py-3.5 px-4">
                  <span
                    :class="[
                      'inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[10px] font-semibold border',
                      srv.isActive ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-gray-50 text-gray-500 border-gray-200'
                    ]"
                  >
                    <span :class="['w-1.5 h-1.5 rounded-full', srv.isActive ? 'bg-emerald-500 animate-pulse' : 'bg-gray-400']"></span>
                    {{ srv.isActive ? 'Active' : 'Disabled' }}
                  </span>
                </td>
                <td class="py-3.5 px-4">
                  <div class="flex items-center gap-2">
                    <button
                      @click="toggleToolsDrawer(srv)"
                      class="inline-flex items-center gap-1 text-[11px] font-semibold text-blue-600 hover:text-blue-800 hover:underline"
                    >
                      <span>🛠️ {{ toolCounts[srv.id] ?? 0 }} Tools</span>
                      <span class="text-[9px]">{{ expandedServerId === srv.id ? '▲' : '▼' }}</span>
                    </button>
                  </div>
                </td>
                <td class="py-3.5 px-4 text-right">
                  <div class="flex items-center justify-end gap-1.5">
                    <button
                      @click="testServer(srv)"
                      :disabled="testingId === srv.id"
                      title="Test Connection"
                      class="px-2.5 py-1 text-purple-700 hover:bg-purple-50 rounded-lg transition-colors border border-purple-200 font-semibold text-[11px] flex items-center gap-1 disabled:opacity-50"
                    >
                      <span :class="[testingId === srv.id ? 'animate-spin' : '']">⚡</span>
                      {{ testingId === srv.id ? 'Testing...' : 'Test' }}
                    </button>
                    <button
                      @click="syncServerTools(srv)"
                      :disabled="syncingId === srv.id"
                      title="Discover & Sync Tools"
                      class="px-2.5 py-1 text-blue-700 hover:bg-blue-50 rounded-lg transition-colors border border-blue-200 font-semibold text-[11px] flex items-center gap-1 disabled:opacity-50"
                    >
                      <span :class="[syncingId === srv.id ? 'animate-spin' : '']">🔄</span>
                      {{ syncingId === srv.id ? 'Syncing...' : 'Sync' }}
                    </button>
                    <button
                      @click="openModal(srv)"
                      title="Edit Configuration"
                      class="p-1.5 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
                    >
                      ✏️
                    </button>
                    <button
                      @click="confirmDelete(srv)"
                      title="Delete Server"
                      class="p-1.5 text-red-500 hover:text-red-700 hover:bg-red-50 rounded-lg transition-colors"
                    >
                      🗑️
                    </button>
                  </div>
                </td>
              </tr>

              <!-- Expanded Tools Drawer -->
              <tr v-if="expandedServerId === srv.id" class="bg-[#fcfbfe]">
                <td colspan="7" class="p-4 border-b border-[#e8e7f1]">
                  <div class="bg-white rounded-xl border border-[#e8e7f1] p-4 space-y-3">
                    <div class="flex items-center justify-between border-b border-[#e8e7f1] pb-2">
                      <div class="flex items-center gap-2">
                        <h4 class="text-xs font-bold text-[#1a1b22]">Discovered Tools for {{ srv.name }}</h4>
                        <span class="text-[10px] px-2 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200 font-medium">
                          {{ (serverTools[srv.id] || []).length }} registered
                        </span>
                      </div>
                      <div class="flex items-center gap-2">
                        <button
                          @click="openManualToolModal(srv)"
                          class="px-2.5 py-1 bg-amber-50 hover:bg-amber-100 text-amber-800 text-[11px] font-semibold rounded-lg border border-amber-200 transition-all flex items-center gap-1"
                        >
                          ➕ Add Manual Tool
                        </button>
                        <button
                          @click="openOpenApiModal(srv)"
                          class="px-2.5 py-1 bg-indigo-50 hover:bg-indigo-100 text-indigo-800 text-[11px] font-semibold rounded-lg border border-indigo-200 transition-all flex items-center gap-1"
                        >
                          📄 Import OpenAPI Spec
                        </button>
                      </div>
                    </div>

                    <div v-if="toolsLoading" class="text-center py-4 text-gray-400 text-xs">
                      Loading tools...
                    </div>

                    <div v-else-if="!serverTools[srv.id] || serverTools[srv.id].length === 0" class="text-center py-6 text-gray-400 text-xs">
                      No tools discovered or registered yet. Click "Sync" or "Add Manual Tool" to populate capabilities.
                    </div>

                    <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-3">
                      <div
                        v-for="tool in serverTools[srv.id]"
                        :key="tool.id"
                        class="p-3 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl flex items-start justify-between gap-2 hover:border-[#ffd700] transition-colors"
                      >
                        <div class="space-y-1 overflow-hidden">
                          <div class="flex items-center gap-1.5 flex-wrap">
                            <span class="font-bold font-mono text-xs text-[#1a1b22]">{{ tool.namespacedName }}</span>
                            <span v-if="tool.isManual" class="px-1.5 py-0.2 rounded text-[9px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">Manual</span>
                          </div>
                          <p class="text-[11px] text-gray-500 line-clamp-2">{{ tool.description || 'No description available' }}</p>
                        </div>
                        <div class="flex items-center gap-1 shrink-0">
                          <button
                            @click="viewToolSchema(tool)"
                            title="View Schema"
                            class="px-2 py-1 text-[10px] bg-white hover:bg-gray-100 border border-gray-200 rounded-md font-semibold text-gray-700"
                          >
                            Schema
                          </button>
                          <button
                            v-if="tool.isManual"
                            @click="removeManualTool(srv, tool)"
                            title="Delete Manual Tool"
                            class="p-1 text-red-500 hover:bg-red-50 rounded-md"
                          >
                            🗑️
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Modals -->
    <McpServerModal
      :show="showModal"
      :editing-server="editingServer"
      :submitting="saving"
      @close="closeModal"
      @save="saveServer"
    />

    <ManualToolModal
      :show="showManualModal"
      :server="currentManualServer"
      :submitting="savingManual"
      @close="showManualModal = false"
      @save="saveManualTool"
    />

    <OpenApiImportModal
      :show="showOpenApiModal"
      :server="currentOpenApiServer"
      :submitting="importingOpenApi"
      @close="showOpenApiModal = false"
      @save="submitOpenApiImport"
    />

    <!-- Delete Confirmation Modal -->
    <div v-if="deletingServer" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
      <div class="bg-white rounded-2xl max-w-sm w-full border border-[#e8e7f1] shadow-2xl p-6 space-y-4">
        <h3 class="text-base font-bold text-[#1a1b22]">Delete MCP Server?</h3>
        <p class="text-xs text-gray-500">
          Are you sure you want to delete <strong>{{ deletingServer.name }}</strong>? All discovered tools and configurations will be removed.
        </p>
        <div class="flex justify-end gap-2 pt-2 border-t border-[#e8e7f1]">
          <button @click="deletingServer = null" class="px-4 py-2 text-gray-600 bg-gray-50 hover:bg-gray-100 rounded-xl font-medium text-xs">
            Cancel
          </button>
          <button @click="executeDelete" class="px-4 py-2 bg-red-600 hover:bg-red-700 text-white font-semibold rounded-xl shadow-sm text-xs">
            Delete Server
          </button>
        </div>
      </div>
    </div>

    <!-- Tool Schema Viewer Modal -->
    <div v-if="selectedToolSchema" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
      <div class="bg-white rounded-2xl max-w-lg w-full border border-[#e8e7f1] shadow-2xl p-6 max-h-[80vh] flex flex-col">
        <div class="flex items-center justify-between pb-3 mb-3 border-b border-[#e8e7f1] shrink-0">
          <h3 class="text-sm font-bold font-mono text-[#1a1b22]">{{ selectedToolSchema.namespacedName }}</h3>
          <button @click="selectedToolSchema = null" class="text-gray-400 hover:text-gray-600 text-lg leading-none">&times;</button>
        </div>
        <div class="flex-1 overflow-y-auto bg-[#1a1b26] p-4 rounded-xl text-gray-200 font-mono text-xs border border-[#2e3047]">
          <pre class="whitespace-pre-wrap break-all">{{ formatJson(selectedToolSchema.inputSchema) }}</pre>
        </div>
        <div class="pt-3 mt-3 border-t border-[#e8e7f1] flex justify-end shrink-0">
          <button @click="selectedToolSchema = null" class="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 text-xs font-semibold rounded-xl">
            Close
          </button>
        </div>
      </div>
    </div>

    <!-- Connection Test Result Modal -->
    <div v-if="testResult" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
      <div class="bg-white rounded-2xl max-w-xl w-full border border-[#e8e7f1] shadow-2xl p-6 max-h-[85vh] flex flex-col overflow-hidden">
        <div class="flex items-center justify-between pb-3 mb-3 border-b border-[#e8e7f1] shrink-0">
          <h3 class="text-base font-bold text-[#1a1b22] font-heading flex items-center gap-2">
            <span>⚡ Connection Test Result</span>
            <span
              :class="[
                'text-[10px] px-2.5 py-0.5 rounded-full font-sans font-semibold border',
                testResult.status === 'CONNECTED' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-red-50 text-red-600 border-red-200'
              ]"
            >
              {{ testResult.status }}
            </span>
          </h3>
          <button @click="testResult = null" class="w-8 h-8 rounded-xl bg-gray-100 hover:bg-gray-200 text-gray-600 flex items-center justify-center font-bold text-base transition-colors">&times;</button>
        </div>

        <div class="flex-1 overflow-y-auto space-y-4 pr-1">
          <div v-if="testResult.requiresOAuth" class="p-3 bg-purple-50 text-purple-900 rounded-xl border border-purple-200 text-xs flex items-center justify-between">
            <div>
              <p class="font-bold">🔑 OAuth Authentication Required</p>
              <p class="text-[11px] text-purple-700 mt-0.5">Discovered OAuth Authorize Endpoint: {{ testResult.discoveredAuthorizeUrl }}</p>
            </div>
            <button
              @click="startOAuthPopup({ id: testResult.serverId, oauthAuthorizeUrl: testResult.discoveredAuthorizeUrl, oauthClientId: testResult.oauthClientId })"
              class="px-3 py-1.5 bg-purple-600 text-white rounded-lg font-bold hover:bg-purple-700 text-xs shrink-0"
            >
              Popup Login
            </button>
          </div>

          <div class="bg-[#1a1b26] p-4 rounded-xl text-gray-200 font-mono text-xs overflow-auto max-h-[50vh] border border-[#2e3047] shadow-inner">
            <pre class="whitespace-pre-wrap break-all">{{ JSON.stringify(testResult, null, 2) }}</pre>
          </div>
        </div>

        <div class="pt-3 mt-3 border-t border-[#e8e7f1] flex justify-end shrink-0">
          <button @click="testResult = null" class="px-5 py-2 bg-[#ffd700] hover:bg-[#e9c400] text-[#1a1b22] text-xs font-bold rounded-xl shadow-sm transition-all">
            Close Result
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { adminApi } from '@/api/admin'
import apiClient from '@/api/client'
import McpServerModal from '@/components/admin/mcp/McpServerModal.vue'
import ManualToolModal from '@/components/admin/mcp/ManualToolModal.vue'
import OpenApiImportModal from '@/components/admin/mcp/OpenApiImportModal.vue'

const servers = ref([])
const loading = ref(false)
const saving = ref(false)
const syncingId = ref(null)
const syncingAll = ref(false)
const testingId = ref(null)
const error = ref(null)
const successMsg = ref(null)
const testResult = ref(null)
const deletingServer = ref(null)

const expandedServerId = ref(null)
const serverTools = ref({})
const toolCounts = ref({})
const toolsLoading = ref(false)
const selectedToolSchema = ref(null)

const showModal = ref(false)
const editingServer = ref(null)

const showManualModal = ref(false)
const currentManualServer = ref(null)
const savingManual = ref(false)

const showOpenApiModal = ref(false)
const currentOpenApiServer = ref(null)
const importingOpenApi = ref(false)

async function fetchServers() {
  loading.value = true
  error.value = null
  try {
    const { data } = await apiClient.get('/api/v1/admin/mcp-servers')
    servers.value = data
    for (const srv of data) {
      loadToolCount(srv.id)
    }
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load MCP servers'
  } finally {
    loading.value = false
  }
}

async function loadToolCount(serverId) {
  try {
    const { data } = await adminApi.getDiscoveredMcpTools(serverId)
    toolCounts.value[serverId] = data.length
    serverTools.value[serverId] = data
  } catch (e) {}
}

async function toggleToolsDrawer(server) {
  if (expandedServerId.value === server.id) {
    expandedServerId.value = null
    return
  }
  expandedServerId.value = server.id
  toolsLoading.value = true
  try {
    const { data } = await adminApi.getDiscoveredMcpTools(server.id)
    serverTools.value[server.id] = data
    toolCounts.value[server.id] = data.length
  } catch (e) {
    error.value = 'Failed to fetch discovered tools'
  } finally {
    toolsLoading.value = false
  }
}

async function syncServerTools(server) {
  syncingId.value = server.id
  error.value = null
  try {
    const { data } = await adminApi.syncMcpServerTools(server.id)
    serverTools.value[server.id] = data
    toolCounts.value[server.id] = data.length
    successMsg.value = `Successfully synced ${data.length} tool(s) for ${server.name}`
    expandedServerId.value = server.id
  } catch (e) {
    error.value = e.response?.data?.message || `Failed to sync tools for ${server.name}`
  } finally {
    syncingId.value = null
  }
}

async function syncAllTools() {
  syncingAll.value = true
  error.value = null
  try {
    await adminApi.syncAllMcpTools()
    successMsg.value = 'All active MCP server tools synced successfully'
    fetchServers()
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to sync all MCP tools'
  } finally {
    syncingAll.value = false
  }
}

function viewToolSchema(tool) {
  selectedToolSchema.value = tool
}

function formatJson(jsonStr) {
  if (!jsonStr) return '{}'
  try {
    return JSON.stringify(JSON.parse(jsonStr), null, 2)
  } catch (e) {
    return jsonStr
  }
}

function openModal(server = null) {
  editingServer.value = server
  showModal.value = true
}

function closeModal() {
  showModal.value = false
  editingServer.value = null
}

async function saveServer(formData) {
  saving.value = true
  error.value = null
  try {
    if (editingServer.value?.id) {
      await apiClient.put(`/api/v1/admin/mcp-servers/${editingServer.value.id}`, formData)
    } else {
      await apiClient.post('/api/v1/admin/mcp-servers', formData)
    }
    closeModal()
    fetchServers()
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to save MCP server'
  } finally {
    saving.value = false
  }
}

function confirmDelete(server) {
  deletingServer.value = server
}

async function executeDelete() {
  if (!deletingServer.value) return
  const srv = deletingServer.value
  deletingServer.value = null
  try {
    await apiClient.delete(`/api/v1/admin/mcp-servers/${srv.id}`)
    successMsg.value = `Deleted MCP Server "${srv.name}" and all associated tools`
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
    testResult.value = {
      serverId: server.id,
      ...data
    }
  } catch (e) {
    testResult.value = {
      serverId: server.id,
      status: 'DISCONNECTED',
      error: e.response?.data?.message || e.message
    }
  } finally {
    testingId.value = null
  }
}

function openManualToolModal(server) {
  currentManualServer.value = server
  showManualModal.value = true
}

async function saveManualTool(formData) {
  if (!currentManualServer.value?.id) return
  savingManual.value = true
  error.value = null
  try {
    const { data } = await adminApi.createManualMcpTool(currentManualServer.value.id, formData)
    successMsg.value = `Manual tool '${data.toolName}' created successfully!`
    showManualModal.value = false
    loadToolCount(currentManualServer.value.id)
    if (expandedServerId.value === currentManualServer.value.id) {
      toggleToolsDrawer(currentManualServer.value)
    }
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to create manual tool'
  } finally {
    savingManual.value = false
  }
}

async function removeManualTool(server, tool) {
  if (!confirm(`Are you sure you want to delete tool '${tool.toolName}' (${tool.namespacedName})?`)) return
  error.value = null
  try {
    await adminApi.deleteManualMcpTool(server.id, tool.id)
    successMsg.value = `Tool '${tool.toolName}' deleted successfully!`
    loadToolCount(server.id)
    if (serverTools.value[server.id]) {
      serverTools.value[server.id] = serverTools.value[server.id].filter(t => t.id !== tool.id)
    }
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to delete tool'
  }
}

function openOpenApiModal(server) {
  currentOpenApiServer.value = server
  showOpenApiModal.value = true
}

async function submitOpenApiImport(formData) {
  if (!currentOpenApiServer.value?.id) return
  importingOpenApi.value = true
  error.value = null
  try {
    const payload = formData.openApiUrl
      ? { openApiUrl: formData.openApiUrl }
      : { openApiSpec: formData.openApiContent }
    const { data } = await adminApi.importOpenApiMcpSpec(currentOpenApiServer.value.id, payload)
    successMsg.value = `Imported ${data.length} endpoints as tools successfully!`
    showOpenApiModal.value = false
    loadToolCount(currentOpenApiServer.value.id)
    fetchServers()
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to import OpenAPI spec'
  } finally {
    importingOpenApi.value = false
  }
}

async function generatePkce() {
  const array = new Uint8Array(32)
  window.crypto.getRandomValues(array)
  const verifier = Array.from(array, byte => byte.toString(16).padStart(2, '0')).join('')
  
  const encoder = new TextEncoder()
  const data = encoder.encode(verifier)
  const hash = await window.crypto.subtle.digest('SHA-256', data)
  
  const challenge = btoa(String.fromCharCode(...new Uint8Array(hash)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
    
  return { verifier, challenge }
}

async function startOAuthPopup(server) {
  const baseUrl = server.oauthAuthorizeUrl || server.discoveredAuthorizeUrl
  if (!baseUrl) {
    error.value = 'OAuth Authorization URL is missing. Please click "⚡ Test" connection to auto-discover or enter Authorize URL in Edit modal.'
    return
  }

  const redirectUri = `${window.location.origin}${import.meta.env.BASE_URL}api/v1/mcp/oauth/callback`
  const serverId = server.id || server.serverId || ''
  
  const pkce = await generatePkce()
  sessionStorage.setItem(`pkce_verifier_${serverId}`, pkce.verifier)

  const params = new URLSearchParams()
  params.append('response_type', 'code')
  params.append('redirect_uri', redirectUri)
  params.append('state', serverId)
  params.append('code_challenge', pkce.challenge)
  params.append('code_challenge_method', 'S256')

  const clientId = server.oauthClientId || (testResult.value && testResult.value.oauthClientId)
  if (clientId && clientId.trim() !== '') {
    params.append('client_id', clientId.trim())
  }

  const fullAuthUrl = baseUrl.includes('?') 
    ? `${baseUrl}&${params.toString()}` 
    : `${baseUrl}?${params.toString()}`

  const width = 600
  const height = 750
  const left = window.screen.width / 2 - width / 2
  const top = window.screen.height / 2 - height / 2

  const popup = window.open(fullAuthUrl, 'mcp_oauth_popup', `width=${width},height=${height},left=${left},top=${top},scrollbars=yes`)
  if (!popup || popup.closed || typeof popup.closed === 'undefined') {
    error.value = 'Popup window was blocked by your browser settings. Please allow popups for this site to complete OAuth authorization.'
  }
}

function handleOAuthMessage(event) {
  if (event.data && event.data.type === 'MCP_OAUTH_RESPONSE') {
    if (event.data.success) {
      successMsg.value = event.data.message || 'OAuth Authentication Successful!'
      fetchServers()
    } else {
      error.value = event.data.message || 'OAuth Authentication Failed'
    }
    if (testResult.value) testResult.value = null
  }
}

onMounted(() => {
  fetchServers()
  window.addEventListener('message', handleOAuthMessage)
})

onUnmounted(() => {
  window.removeEventListener('message', handleOAuthMessage)
})
</script>
