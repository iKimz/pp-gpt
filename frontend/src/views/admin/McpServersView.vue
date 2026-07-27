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

    <!-- Stats summary -->
    <div class="grid grid-cols-1 sm:grid-cols-4 gap-4">
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
          <p class="text-xs text-gray-500 font-medium">Static API Key</p>
          <p class="text-xl font-bold text-[#1a1b22] mt-0.5">{{ servers.filter(s => s.authType === 'STATIC_KEY' && s.hasApiKey).length }}</p>
        </div>
      </div>
      <div class="bg-white border border-[#e8e7f1] rounded-2xl p-4 shadow-sm flex items-center gap-4">
        <div class="w-10 h-10 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center font-bold text-lg">
          🛡️
        </div>
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
      <p class="text-xs text-gray-400 max-w-sm mx-auto mt-1 mb-4">Register internal microservices, database connectors, or public remote MCP servers (Firecrawl, GitHub, Tavily, etc.).</p>
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
                      <span v-if="srv.supportsTools !== false" class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-blue-50 text-blue-700 border border-blue-200">
                        🛠️ Tools
                      </span>
                      <span v-if="srv.supportsResources" class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                        📁 Resources
                      </span>
                      <span v-if="srv.supportsPrompts" class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-purple-50 text-purple-700 border border-purple-200">
                        📝 Prompts
                      </span>
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
                          : 'bg-gray-50 text-gray-500 border-gray-200'
                    ]"
                  >
                    <span v-if="srv.authType === 'OAUTH2'">🛡️ OAuth 2.0 {{ srv.hasOAuthTokens ? '(Connected)' : '(Needs Login)' }}</span>
                    <span v-else>{{ srv.hasApiKey ? '🔑 API Key Set' : '🔓 No Auth' }}</span>
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
                <td class="py-3.5 px-4">
                  <button
                    @click="toggleToolsDrawer(srv)"
                    class="inline-flex items-center gap-1.5 px-2.5 py-1 text-[11px] bg-gray-50 hover:bg-gray-100 text-gray-700 rounded-lg border border-gray-200 transition-colors font-medium"
                  >
                    <span>🧰 Items ({{ toolCounts[srv.id] ?? 'View' }})</span>
                    <span class="text-[10px] text-gray-400">{{ expandedServerId === srv.id ? '▲' : '▼' }}</span>
                  </button>
                </td>
                <td class="py-3.5 px-4 text-right space-x-1.5">
                  <button
                    @click="syncServerTools(srv)"
                    :disabled="syncingId === srv.id"
                    class="px-2.5 py-1 text-[11px] bg-blue-50 hover:bg-blue-100 text-blue-700 rounded-lg border border-blue-200 transition-colors font-medium disabled:opacity-50"
                  >
                    {{ syncingId === srv.id ? 'Syncing...' : '🔄 Sync' }}
                  </button>
                  <button
                    v-if="srv.authType === 'OAUTH2'"
                    @click="startOAuthPopup(srv)"
                    class="px-2.5 py-1 text-[11px] bg-purple-50 hover:bg-purple-100 text-purple-700 rounded-lg border border-purple-200 transition-colors font-medium"
                  >
                    🔑 Login
                  </button>
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

              <!-- Expanded Tools Drawer Row -->
              <tr v-if="expandedServerId === srv.id" class="bg-[#fcfbfe] border-b border-[#e8e7f1]">
                <td colspan="7" class="p-4">
                  <div class="space-y-3">
                    <div class="flex items-center justify-between flex-wrap gap-2">
                      <h4 class="font-bold text-[#1a1b22] text-xs flex items-center gap-2">
                        <span>🧰 Discovered Tools & Items for {{ srv.name }}</span>
                        <span class="text-[10px] text-gray-400 font-mono">(Namespaced: {{ srv.name.toLowerCase().replace(/[^a-z0-9_]/g, '_') }}__*)</span>
                      </h4>
                      <div class="flex items-center gap-2">
                        <button
                          @click="openManualToolModal(srv)"
                          class="px-2.5 py-1 text-[11px] bg-amber-50 hover:bg-amber-100 text-amber-800 rounded-lg border border-amber-300 font-semibold transition-all flex items-center gap-1 shadow-2xs"
                        >
                          ➕ Add Tool Manually
                        </button>
                        <button
                          @click="openOpenApiModal(srv)"
                          class="px-2.5 py-1 text-[11px] bg-indigo-50 hover:bg-indigo-100 text-indigo-800 rounded-lg border border-indigo-300 font-semibold transition-all flex items-center gap-1 shadow-2xs"
                        >
                          📄 Import OpenAPI Spec
                        </button>
                        <button @click="syncServerTools(srv)" class="text-[11px] text-blue-600 hover:underline font-medium ml-2">
                          🔄 Re-sync Now
                        </button>
                      </div>
                    </div>

                    <div v-if="toolsLoading" class="text-xs text-gray-400 py-4 text-center">
                      Loading discovered tools...
                    </div>
                    <div v-else-if="!serverTools[srv.id] || serverTools[srv.id].length === 0" class="text-xs text-gray-400 p-4 border border-dashed border-gray-200 rounded-xl text-center">
                      No tools discovered yet. Click "🔄 Sync" to fetch tools/list from this MCP Server.
                    </div>
                    <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                      <div
                        v-for="tool in serverTools[srv.id]"
                        :key="tool.id"
                        class="p-3 bg-white rounded-xl border border-[#e8e7f1] shadow-2xs space-y-1.5 relative group hover:border-gray-300 transition-all"
                      >
                        <div class="flex items-start justify-between gap-2">
                          <span class="font-bold text-[#1a1b22] font-mono text-[11px] truncate" :title="tool.namespacedName">
                            {{ tool.namespacedName }}
                          </span>
                          <span
                            :class="[
                              'text-[9px] px-2 py-0.5 rounded-full font-semibold border shrink-0',
                              (tool.isAvailable ?? tool.available)
                                ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                                : 'bg-red-50 text-red-600 border-red-200'
                            ]"
                          >
                            {{ (tool.isAvailable ?? tool.available) ? '🟢 Available' : `⚠️ Retry ${tool.failedSyncCount}/3` }}
                          </span>
                        </div>
                        <p class="text-[11px] text-gray-500 line-clamp-2 leading-relaxed">
                          {{ tool.description || 'No description provided.' }}
                        </p>
                        <div class="pt-1 flex items-center justify-between text-[10px]">
                          <button
                            @click="viewToolSchema(tool)"
                            class="text-blue-600 hover:underline font-medium flex items-center gap-1"
                          >
                            <span>🔍 View JSON Schema</span>
                          </button>
                          <span class="text-gray-400 text-[9px]" v-if="tool.lastSyncedAt">
                            Synced: {{ new Date(tool.lastSyncedAt).toLocaleTimeString() }}
                          </span>
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

    <!-- Create/Edit Modal -->
    <div v-if="showModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-fade-in">
      <div class="bg-white rounded-2xl max-w-lg w-full border border-[#e8e7f1] shadow-2xl p-6 relative">
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
              placeholder="e.g. Local MCP"
              class="w-full px-3 py-2 rounded-xl border border-[#e8e7f1] focus:outline-none focus:ring-2 focus:ring-[#ffd700]"
            />
          </div>

          <div>
            <label class="block font-medium text-gray-700 mb-1">Endpoint URL *</label>
            <input
              v-model="form.endpointUrl"
              type="url"
              required
              placeholder="https://example-server.modelcontextprotocol.io/mcp"
              class="w-full px-3 py-2 rounded-xl border border-[#e8e7f1] focus:outline-none focus:ring-2 focus:ring-[#ffd700] font-mono text-[11px]"
            />
          </div>

          <div>
            <label class="block font-medium text-gray-700 mb-1">API Key / Bearer Token (Optional)</label>
            <input
              v-model="form.apiKey"
              type="password"
              placeholder="e.g. Personal API Key (fc-...) or Bearer Token"
              class="w-full px-3 py-2 rounded-xl border border-[#e8e7f1] focus:outline-none focus:ring-2 focus:ring-[#ffd700]"
            />
            <p class="text-[10px] text-gray-400 mt-1">Leave blank if using Interactive OAuth Popup Authorization.</p>
          </div>

          <!-- Advanced OAuth Options (Collapsible) -->
          <details class="text-[#1a1b22] border border-gray-200 rounded-xl p-3 bg-gray-50/50">
            <summary class="font-semibold cursor-pointer text-xs text-gray-600 select-none">
              ⚙️ Optional Custom OAuth Config
            </summary>
            <div class="space-y-3 mt-3 pt-3 border-t border-gray-200">
              <div>
                <label class="block font-medium text-gray-700 mb-1">OAuth Client ID (Optional)</label>
                <input
                  v-model="form.oauthClientId"
                  type="text"
                  placeholder="Only if required by custom OAuth Provider"
                  class="w-full px-3 py-2 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-[#ffd700]"
                />
              </div>
              <div>
                <label class="block font-medium text-gray-700 mb-1">OAuth Authorize URL (Optional)</label>
                <input
                  v-model="form.oauthAuthorizeUrl"
                  type="url"
                  placeholder="Auto-discovered via ⚡ Test connection if left blank"
                  class="w-full px-3 py-2 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-[#ffd700] font-mono text-[11px]"
                />
              </div>
            </div>
          </details>

          <div>
            <label class="block font-medium text-gray-700 mb-1">Description</label>
            <textarea
              v-model="form.description"
              rows="2"
              placeholder="Description of capabilities..."
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

    <!-- Schema Viewer Modal -->
    <div v-if="selectedToolSchema" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-fade-in">
      <div class="bg-white rounded-2xl max-w-xl w-full border border-[#e8e7f1] shadow-2xl p-6 relative">
        <div class="flex items-center justify-between pb-3 mb-3 border-b border-[#e8e7f1]">
          <h3 class="text-sm font-bold text-[#1a1b22] font-heading flex items-center gap-2">
            <span>🔍 Tool Input Schema</span>
            <span class="font-mono text-xs text-gray-500">({{ selectedToolSchema.namespacedName }})</span>
          </h3>
          <button @click="selectedToolSchema = null" class="font-bold text-gray-400 hover:text-gray-600 text-base">&times;</button>
        </div>
        <div class="bg-[#1a1b26] p-4 rounded-xl text-gray-200 font-mono text-xs overflow-auto max-h-[60vh] border border-[#2e3047]">
          <pre class="whitespace-pre-wrap break-all">{{ formatJson(selectedToolSchema.inputSchema) }}</pre>
        </div>
        <div class="pt-3 mt-3 border-t border-[#e8e7f1] flex justify-end">
          <button @click="selectedToolSchema = null" class="px-4 py-1.5 bg-[#ffd700] hover:bg-[#e9c400] text-[#1a1b22] text-xs font-bold rounded-xl shadow-sm">
            Close
          </button>
        </div>
      </div>
    </div>

    <!-- Delete Confirmation Modal (Cascading Notice) -->
    <div v-if="deletingServer" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-fade-in">
      <div class="bg-white rounded-2xl max-w-md w-full border border-[#e8e7f1] shadow-2xl p-6 relative space-y-4 text-xs">
        <div class="w-10 h-10 rounded-xl bg-red-50 text-red-600 flex items-center justify-center font-bold text-lg">
          ⚠️
        </div>
        <div>
          <h3 class="text-base font-bold text-[#1a1b22]">Delete MCP Server "{{ deletingServer.name }}"?</h3>
          <p class="text-gray-500 mt-1 leading-relaxed">
            This will permanently remove the MCP Server connection, all discovered tools, and unbind all associated group tool access configurations (<span class="font-mono text-red-600">ON DELETE CASCADE</span>).
          </p>
        </div>
        <div class="flex justify-end gap-2 pt-2 border-t border-[#e8e7f1]">
          <button @click="deletingServer = null" class="px-4 py-2 text-gray-600 bg-gray-50 hover:bg-gray-100 rounded-xl font-medium">
            Cancel
          </button>
          <button @click="executeDelete" class="px-4 py-2 bg-red-600 hover:bg-red-700 text-white font-semibold rounded-xl shadow-sm">
            Delete Server & Configs
          </button>
        </div>
      </div>
    </div>

    <!-- Connection Test Result Modal -->
    <div v-if="testResult" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-fade-in">
      <div class="bg-white rounded-2xl max-w-2xl w-full border border-[#e8e7f1] shadow-2xl p-6 max-h-[85vh] flex flex-col overflow-hidden">
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
          <button
            @click="testResult = null"
            class="px-5 py-2 bg-[#ffd700] hover:bg-[#e9c400] text-[#1a1b22] text-xs font-bold rounded-xl shadow-sm transition-all"
          >
            Close Result
          </button>
        </div>
      </div>
    </div>

    <!-- Manual Tool Add Modal -->
    <div v-if="showManualModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-fade-in">
      <div class="bg-white rounded-2xl max-w-lg w-full border border-[#e8e7f1] shadow-2xl p-6 relative">
        <div class="flex items-center justify-between pb-3 mb-3 border-b border-[#e8e7f1]">
          <div>
            <h3 class="text-base font-bold text-[#1a1b22] font-heading flex items-center gap-2">
              <span>➕ Add Tool Manually</span>
              <span class="text-[10px] px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 font-medium border border-amber-200">Legacy REST Fallback</span>
            </h3>
            <p class="text-xs text-gray-400 mt-0.5">Define custom tool capabilities for REST endpoints without tools/list protocol support.</p>
          </div>
          <button @click="showManualModal = false" class="w-7 h-7 rounded-lg bg-gray-100 hover:bg-gray-200 text-gray-500 font-bold text-sm flex items-center justify-center">&times;</button>
        </div>

        <form @submit.prevent="saveManualTool" class="space-y-4 text-xs">
          <!-- Tool Name with Hint -->
          <div>
            <div class="flex items-center justify-between mb-1">
              <label class="font-medium text-gray-700">Tool Identifier / Name *</label>
              <span class="text-[10px] text-gray-400 font-mono">e.g. check_order_status</span>
            </div>
            <input
              v-model="manualForm.toolName"
              type="text"
              required
              placeholder="check_order_status"
              class="w-full px-3 py-2 rounded-xl border border-[#e8e7f1] focus:outline-none focus:ring-2 focus:ring-[#ffd700] font-mono text-xs"
            />
            <p class="text-[10px] text-gray-500 mt-1 flex items-center gap-1">
              <span>💡</span>
              <span>Namespacing prefix will be prepended automatically (e.g. <code class="font-mono text-blue-600 bg-blue-50 px-1 py-0.5 rounded">server_name__tool_name</code>).</span>
            </p>
          </div>

          <!-- Description with Hint -->
          <div>
            <label class="block font-medium text-gray-700 mb-1">Function Description for LLM *</label>
            <textarea
              v-model="manualForm.description"
              rows="2"
              required
              placeholder="Describe what this tool does, when to call it, and what it returns..."
              class="w-full px-3 py-2 rounded-xl border border-[#e8e7f1] focus:outline-none focus:ring-2 focus:ring-[#ffd700] text-xs leading-relaxed"
            />
            <p class="text-[10px] text-gray-500 mt-1 flex items-center gap-1">
              <span>💡</span>
              <span>LLMs read this prompt to decide when to automatically trigger this function.</span>
            </p>
          </div>

          <!-- Input Schema with Presets & Realtime Validator -->
          <div>
            <div class="flex items-center justify-between mb-1.5 flex-wrap gap-1">
              <label class="font-medium text-gray-700">JSON Input Schema (Parameters)</label>
              <!-- Realtime Validation Badge -->
              <span
                :class="[
                  'text-[10px] font-semibold px-2 py-0.5 rounded-full border',
                  isManualSchemaValid
                    ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                    : 'bg-red-50 text-red-600 border-red-200'
                ]"
              >
                {{ isManualSchemaValid ? '✅ Valid JSON Schema' : '❌ Invalid JSON Syntax' }}
              </span>
            </div>

            <!-- Preset Buttons -->
            <div class="flex items-center gap-1.5 mb-2 flex-wrap text-[10px]">
              <span class="text-gray-400 font-medium">Quick Presets:</span>
              <button
                type="button"
                @click="applySchemaPreset('EMPTY')"
                class="px-2 py-0.5 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-md transition-colors border border-gray-200"
              >
                ⚡ No Parameters ({})
              </button>
              <button
                type="button"
                @click="applySchemaPreset('QUERY')"
                class="px-2 py-0.5 bg-blue-50 hover:bg-blue-100 text-blue-700 rounded-md transition-colors border border-blue-200"
              >
                📝 Single Text (query)
              </button>
              <button
                type="button"
                @click="applySchemaPreset('ID_LIMIT')"
                class="px-2 py-0.5 bg-purple-50 hover:bg-purple-100 text-purple-700 rounded-md transition-colors border border-purple-200"
              >
                🔢 ID & Limit Params
              </button>
            </div>

            <textarea
              v-model="manualForm.inputSchema"
              rows="5"
              placeholder='{"type":"object","properties":{"user_id":{"type":"string"}},"required":["user_id"]}'
              class="w-full px-3 py-2 rounded-xl border border-[#e8e7f1] focus:outline-none focus:ring-2 focus:ring-[#ffd700] font-mono text-[11px] leading-snug"
            />
          </div>

          <!-- Buttons -->
          <div class="flex justify-end gap-2 pt-3 border-t border-[#e8e7f1]">
            <button @click="showManualModal = false" type="button" class="px-4 py-2 text-gray-600 bg-gray-50 hover:bg-gray-100 rounded-xl font-medium">
              Cancel
            </button>
            <button :disabled="savingManual || !isManualSchemaValid" type="submit" class="px-5 py-2 bg-[#ffd700] hover:bg-[#e9c400] text-[#1a1b22] font-semibold rounded-xl shadow-sm disabled:opacity-50 transition-all">
              {{ savingManual ? 'Saving Tool...' : 'Save Manual Tool' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- OpenAPI Spec Import Modal -->
    <div v-if="showOpenApiModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-fade-in">
      <div class="bg-white rounded-2xl max-w-xl w-full border border-[#e8e7f1] shadow-2xl p-6 relative">
        <h3 class="text-base font-bold text-[#1a1b22] font-heading mb-1 flex items-center gap-2">
          <span>📄 Import OpenAPI / Swagger Spec</span>
          <span class="text-[10px] px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 font-normal border border-indigo-200">OpenAPI 3.0 Auto Importer</span>
        </h3>
        <p class="text-xs text-gray-400 mb-4">Paste raw OpenAPI/Swagger JSON content to automatically parse REST endpoints into tool definitions.</p>

        <form @submit.prevent="submitOpenApiImport" class="space-y-4 text-xs">
          <div>
            <label class="block font-medium text-gray-700 mb-1">OpenAPI Spec (JSON) *</label>
            <textarea
              v-model="openApiSpecText"
              rows="10"
              required
              placeholder='{"openapi":"3.0.0","info":{"title":"Sample API"},"paths":{"/users":{"get":{"summary":"Get list of users"}}}}'
              class="w-full px-3 py-2 rounded-xl border border-[#e8e7f1] focus:outline-none focus:ring-2 focus:ring-[#ffd700] font-mono text-[11px]"
            />
          </div>

          <div class="flex justify-end gap-2 pt-2 border-t border-[#e8e7f1]">
            <button @click="showOpenApiModal = false" type="button" class="px-4 py-2 text-gray-600 bg-gray-50 hover:bg-gray-100 rounded-xl font-medium">
              Cancel
            </button>
            <button :disabled="importingOpenApi" type="submit" class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-semibold rounded-xl shadow-sm disabled:opacity-50">
              {{ importingOpenApi ? 'Parsing & Importing...' : 'Import Endpoints as Tools' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { adminApi } from '@/api/admin'
import apiClient from '@/api/client'

const isManualSchemaValid = computed(() => {
  if (!manualForm.value.inputSchema || !manualForm.value.inputSchema.trim()) return true
  try {
    const parsed = JSON.parse(manualForm.value.inputSchema)
    return typeof parsed === 'object' && parsed !== null
  } catch (e) {
    return false
  }
})

function applySchemaPreset(presetType) {
  if (presetType === 'EMPTY') {
    manualForm.value.inputSchema = '{\n  "type": "object",\n  "properties": {}\n}'
  } else if (presetType === 'QUERY') {
    manualForm.value.inputSchema = '{\n  "type": "object",\n  "properties": {\n    "input_query": {\n      "type": "string",\n      "description": "Target search query or string"\n    }\n  },\n  "required": ["input_query"]\n}'
  } else if (presetType === 'ID_LIMIT') {
    manualForm.value.inputSchema = '{\n  "type": "object",\n  "properties": {\n    "user_id": {\n      "type": "string",\n      "description": "Target user ID"\n    },\n    "limit": {\n      "type": "integer",\n      "description": "Maximum number of records"\n    }\n  },\n  "required": ["user_id"]\n}'
  }
}

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
const editingId = ref(null)
const form = ref({
  name: '',
  endpointUrl: '',
  authType: 'STATIC_KEY',
  apiKey: '',
  oauthAuthorizeUrl: '',
  oauthClientId: '',
  description: '',
  isActive: true
})

// Manual Tool & OpenAPI Import Modal State
const showManualModal = ref(false)
const manualServerId = ref(null)
const savingManual = ref(false)
const manualForm = ref({
  toolName: '',
  description: '',
  inputSchema: ''
})

const showOpenApiModal = ref(false)
const openApiServerId = ref(null)
const importingOpenApi = ref(false)
const openApiSpecText = ref('')

function openManualToolModal(server) {
  manualServerId.value = server.id
  manualForm.value = {
    toolName: '',
    description: '',
    inputSchema: '{\n  "type": "object",\n  "properties": {},\n  "required": []\n}'
  }
  showManualModal.value = true
}

async function saveManualTool() {
  if (!manualServerId.value) return
  savingManual.value = true
  error.value = null
  try {
    const { data } = await adminApi.createManualMcpTool(manualServerId.value, manualForm.value)
    successMsg.value = `Manual tool '${data.toolName}' created successfully!`
    showManualModal.value = false
    loadToolCount(manualServerId.value)
    if (expandedServerId.value === manualServerId.value) {
      toggleToolsDrawer({ id: manualServerId.value })
    }
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to create manual tool'
  } finally {
    savingManual.value = false
  }
}

function openOpenApiModal(server) {
  openApiServerId.value = server.id
  openApiSpecText.value = ''
  showOpenApiModal.value = true
}

async function submitOpenApiImport() {
  if (!openApiServerId.value || !openApiSpecText.value) return
  importingOpenApi.value = true
  error.value = null
  try {
    const { data } = await adminApi.importOpenApiMcpSpec(openApiServerId.value, { openApiSpec: openApiSpecText.value })
    successMsg.value = `Imported ${data.length} endpoints as tools successfully!`
    showOpenApiModal.value = false
    loadToolCount(openApiServerId.value)
    fetchServers()
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to import OpenAPI spec'
  } finally {
    importingOpenApi.value = false
  }
}

async function fetchServers() {
  loading.value = true
  error.value = null
  try {
    const { data } = await apiClient.get('/api/v1/admin/mcp-servers')
    servers.value = data
    // Fetch tool counts for servers
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
  if (server) {
    editingId.value = server.id
    form.value = {
      name: server.name,
      endpointUrl: server.endpointUrl,
      authType: server.authType || 'STATIC_KEY',
      apiKey: '',
      oauthAuthorizeUrl: server.oauthAuthorizeUrl || '',
      oauthClientId: server.oauthClientId || '',
      description: server.description || '',
      isActive: server.isActive
    }
  } else {
    editingId.value = null
    form.value = {
      name: '',
      endpointUrl: '',
      authType: 'STATIC_KEY',
      apiKey: '',
      oauthAuthorizeUrl: '',
      oauthClientId: '',
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
