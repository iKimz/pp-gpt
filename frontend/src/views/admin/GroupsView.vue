<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-xl font-bold text-[#1a1b22] font-heading">User Groups</h1>
        <p class="text-sm text-[#4d4732] mt-0.5">Manage groups, AI model access, and selective MCP tool permissions.</p>
      </div>
      <button @click="openCreate()" class="btn-primary flex items-center gap-2">
        <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
        </svg>
        New Group
      </button>
    </div>

    <DataTable
      :columns="columns"
      :rows="groups"
      :loading="loading"
      @edit="openEdit($event)"
      @delete="handleDelete($event)"
    >
      <template #cell-maxDailyCredits="{ value }">
        <span class="font-mono text-brand-400">{{ Number(value).toLocaleString() }}</span>
      </template>
      <template #cell-allowedModelIds="{ value }">
        <span class="badge badge-blue">{{ value?.length ?? 0 }} models</span>
      </template>
      <template #actions="{ row }">
        <button
          @click="openMcpToolsModal(row)"
          class="px-2.5 py-1 text-[11px] bg-purple-50 hover:bg-purple-100 text-purple-700 rounded-lg border border-purple-200 transition-colors font-medium flex items-center gap-1"
          title="Configure MCP Tool Access"
        >
          <span>🛠️ MCP Tools</span>
        </button>
        <button @click="openEdit(row)" class="btn-icon" title="Edit">
          <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/>
          </svg>
        </button>
        <button @click="handleDelete(row)" class="btn-icon text-red-400 hover:text-red-300" title="Delete">
          <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
          </svg>
        </button>
      </template>
    </DataTable>

    <!-- Create / Edit Modal -->
    <ModalForm
      v-if="showModal"
      :title="editing ? 'Edit Group' : 'New Group'"
      :loading="saving"
      @close="showModal = false"
      @confirm="handleSave"
    >
      <div class="space-y-4">
        <div>
          <label class="label">Group Name</label>
          <input v-model="form.groupName" class="input-field" placeholder="e.g. ENGINEERING" />
        </div>
        <div>
          <label class="label">Max Daily Credits</label>
          <input v-model.number="form.maxDailyCredits" type="number" class="input-field" min="0" />
        </div>
        <div>
          <label class="label">Assigned Guardrail Model (Optional)</label>
          <select v-model="form.guardrailModelId" class="input-field">
            <option :value="null">None (No Guardrail)</option>
            <option v-for="gm in guardrailModels" :key="gm.id" :value="gm.id">
              {{ gm.name || (gm.provider + ' / ' + gm.modelName) }}
            </option>
          </select>
          <p class="text-[11px] text-[#4d4732] mt-1">If set, all prompts from this group are evaluated by this model first.</p>
        </div>
        <div>
          <label class="label">Allowed Models</label>
          <div class="space-y-2 max-h-48 overflow-y-auto glass rounded-lg p-3">
            <label
              v-for="model in generationModels"
              :key="model.id"
              class="flex items-center gap-3 cursor-pointer hover:bg-surface-600 rounded-lg px-2 py-1.5 transition-colors"
            >
              <input
                type="checkbox"
                :value="model.id"
                v-model="form.allowedModelIds"
                class="accent-brand-500 w-4 h-4"
              />
              <span class="text-sm font-medium text-[#1a1b22]">{{ model.name || (model.provider + ' / ' + model.modelName) }}</span>
            </label>
            <p v-if="generationModels.length === 0" class="text-xs text-black-500 text-center py-2">No models available</p>
          </div>
        </div>
      </div>
    </ModalForm>

    <!-- MCP Tool Access Selection Matrix Modal -->
    <div v-if="showMcpModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-fade-in">
      <div class="bg-white rounded-2xl max-w-2xl w-full border border-[#e8e7f1] shadow-2xl p-6 relative max-h-[85vh] flex flex-col">
        <!-- Header -->
        <div class="flex items-center justify-between pb-3 border-b border-[#e8e7f1] shrink-0">
          <div>
            <h3 class="text-base font-bold text-[#1a1b22] font-heading flex items-center gap-2">
              <span>🛠️ MCP Tool Permissions</span>
              <span class="text-xs px-2 py-0.5 rounded-full bg-purple-50 text-purple-700 border border-purple-200 font-sans">
                {{ selectedGroup?.groupName }}
              </span>
            </h3>
            <p class="text-xs text-gray-500 mt-0.5">Select specific MCP tools enabled for users in this group to optimize LLM token usage.</p>
          </div>
          <button @click="showMcpModal = false" class="w-8 h-8 rounded-xl bg-gray-100 hover:bg-gray-200 text-gray-600 flex items-center justify-center font-bold text-base transition-colors">&times;</button>
        </div>

        <!-- Search & Filters -->
        <div class="py-3 border-b border-[#e8e7f1] flex items-center justify-between gap-3 text-xs shrink-0">
          <input
            v-model="toolSearch"
            type="text"
            placeholder="Search tools by name or description..."
            class="flex-1 px-3 py-1.5 rounded-xl border border-[#e8e7f1] focus:outline-none focus:ring-2 focus:ring-[#ffd700]"
          />
          <div class="flex items-center gap-2">
            <button @click="selectAllTools(true)" class="px-2.5 py-1 text-[11px] bg-emerald-50 hover:bg-emerald-100 text-emerald-700 rounded-lg border border-emerald-200 font-medium">
              Select All
            </button>
            <button @click="selectAllTools(false)" class="px-2.5 py-1 text-[11px] bg-gray-50 hover:bg-gray-100 text-gray-700 rounded-lg border border-gray-200 font-medium">
              Deselect All
            </button>
          </div>
        </div>

        <!-- Content Area -->
        <div class="flex-1 overflow-y-auto py-3 space-y-4 pr-1">
          <div v-if="mcpLoading" class="text-center py-12 text-gray-400 text-xs">
            Loading MCP tool permissions...
          </div>
          <div v-else-if="filteredGroupTools.length === 0" class="text-center py-8 text-gray-400 text-xs border border-dashed border-gray-200 rounded-xl">
            No active MCP tools found. Add or sync MCP servers in the <RouterLink to="/admin/mcp-servers" class="text-blue-600 underline">MCP Servers</RouterLink> section first.
          </div>
          <div v-else class="space-y-4">
            <div
              v-for="(tools, serverName) in groupedTools"
              :key="serverName"
              class="border border-[#e8e7f1] rounded-xl overflow-hidden bg-gray-50/50"
            >
              <div class="px-3.5 py-2 bg-white border-b border-[#e8e7f1] flex items-center justify-between">
                <span class="font-bold text-xs text-[#1a1b22] flex items-center gap-2">
                  <span>🔌 {{ serverName }}</span>
                  <span class="text-[10px] text-gray-400 font-normal">({{ tools.filter(t => t.isEnabledForGroup).length }}/{{ tools.length }} enabled)</span>
                </span>
                <button
                  @click="toggleServerTools(serverName)"
                  class="text-[10px] text-blue-600 hover:underline font-medium"
                >
                  Toggle Server Tools
                </button>
              </div>

              <div class="p-2 divide-y divide-gray-100 bg-white">
                <label
                  v-for="tool in tools"
                  :key="tool.id"
                  class="flex items-start gap-3 p-2.5 hover:bg-[#fbf8ff] rounded-lg cursor-pointer transition-colors"
                >
                  <input
                    type="checkbox"
                    v-model="tool.isEnabledForGroup"
                    class="mt-0.5 rounded text-[#ffd700] focus:ring-[#ffd700] w-4 h-4"
                  />
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center gap-2">
                      <span class="font-mono text-xs font-bold text-[#1a1b22] truncate">{{ tool.namespacedName }}</span>
                      <span v-if="!tool.isAvailable" class="text-[9px] px-1.5 py-0.2 rounded bg-red-50 text-red-600 border border-red-200">Unreachable</span>
                    </div>
                    <p class="text-[11px] text-gray-500 line-clamp-2 mt-0.5 leading-relaxed">{{ tool.description || 'No description provided.' }}</p>
                  </div>
                </label>
              </div>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="pt-3 border-t border-[#e8e7f1] flex justify-between items-center shrink-0 text-xs">
          <span class="text-gray-400 text-[11px]">
            Selected {{ totalEnabledTools }} of {{ mcpToolsList.length }} tools
          </span>
          <div class="flex gap-2">
            <button @click="showMcpModal = false" class="px-4 py-2 text-gray-600 bg-gray-50 hover:bg-gray-100 rounded-xl font-medium">
              Cancel
            </button>
            <button
              @click="saveMcpToolAccess"
              :disabled="mcpSaving"
              class="px-4 py-2 bg-[#ffd700] hover:bg-[#e9c400] text-[#1a1b22] font-semibold rounded-xl shadow-sm disabled:opacity-50"
            >
              {{ mcpSaving ? 'Saving...' : 'Save Tool Access' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import DataTable from '@/components/DataTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import { adminApi } from '@/api/admin'

const groups    = ref([])
const allModels = ref([])
const loading   = ref(true)
const saving    = ref(false)
const showModal = ref(false)
const editing   = ref(null)

// MCP Tools Modal state
const showMcpModal = ref(false)
const selectedGroup = ref(null)
const mcpToolsList = ref([])
const mcpLoading = ref(false)
const mcpSaving = ref(false)
const toolSearch = ref('')

const guardrailModels  = computed(() => allModels.value.filter(m => m.modelType === 'GUARDRAIL'))
const generationModels = computed(() => allModels.value.filter(m => m.modelType !== 'GUARDRAIL'))

const form = reactive({ groupName: '', maxDailyCredits: 1000, allowedModelIds: [], guardrailModelId: null })

const columns = [
  { key: 'groupName',       label: 'Name'           },
  { key: 'maxDailyCredits', label: 'Daily Credits'  },
  { key: 'allowedModelIds', label: 'Model Access'   },
]

const filteredGroupTools = computed(() => {
  if (!toolSearch.value || !toolSearch.value.trim()) return mcpToolsList.value
  const q = toolSearch.value.toLowerCase()
  return mcpToolsList.value.filter(t => 
    t.namespacedName.toLowerCase().includes(q) || 
    (t.description && t.description.toLowerCase().includes(q))
  )
})

const groupedTools = computed(() => {
  const groupsMap = {}
  for (const t of filteredGroupTools.value) {
    const srv = t.mcpServerName || 'General Server'
    if (!groupsMap[srv]) groupsMap[srv] = []
    groupsMap[srv].push(t)
  }
  return groupsMap
})

const totalEnabledTools = computed(() => mcpToolsList.value.filter(t => t.isEnabledForGroup).length)

onMounted(async () => {
  await Promise.all([loadGroups(), loadModels()])
})

async function loadGroups() {
  loading.value = true
  try { groups.value = (await adminApi.getGroups()).data }
  finally { loading.value = false }
}

async function loadModels() {
  try { allModels.value = (await adminApi.getModels()).data }
  catch {}
}

function openCreate() {
  editing.value = null
  Object.assign(form, { groupName: '', maxDailyCredits: 1000, allowedModelIds: [], guardrailModelId: null })
  showModal.value = true
}

function openEdit(group) {
  editing.value = group
  Object.assign(form, { ...group })
  showModal.value = true
}

async function openMcpToolsModal(group) {
  selectedGroup.value = group
  mcpToolsList.value = []
  toolSearch.value = ''
  showMcpModal.value = true
  mcpLoading.value = true
  try {
    const { data } = await adminApi.getGroupMcpTools(group.id)
    mcpToolsList.value = data.map(t => ({
      ...t,
      isEnabledForGroup: t.isEnabledForGroup ?? t.enabledForGroup ?? true,
      isAvailable: t.isAvailable ?? t.available ?? true
    }))
  } catch (e) {
    alert('Failed to load MCP tool access list')
  } finally {
    mcpLoading.value = false
  }
}

function selectAllTools(enable) {
  for (const tool of mcpToolsList.value) {
    tool.isEnabledForGroup = enable
    tool.enabledForGroup = enable
  }
}

function toggleServerTools(serverName) {
  const tools = groupedTools.value[serverName]
  if (!tools) return
  const allEnabled = tools.every(t => (t.isEnabledForGroup ?? t.enabledForGroup))
  for (const t of tools) {
    t.isEnabledForGroup = !allEnabled
    t.enabledForGroup = !allEnabled
  }
}

async function saveMcpToolAccess() {
  if (!selectedGroup.value) return
  mcpSaving.value = true
  try {
    const updates = mcpToolsList.value.map(t => ({
      mcpToolId: t.id,
      isEnabled: t.isEnabledForGroup ?? t.enabledForGroup ?? true
    }))
    await adminApi.updateGroupMcpTools(selectedGroup.value.id, updates)
    showMcpModal.value = false
    alert(`✅ Tool access matrix for group '${selectedGroup.value.groupName}' saved successfully!`)
  } catch (e) {
    alert(e.response?.data?.message || 'Failed to save MCP tool access settings')
  } finally {
    mcpSaving.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    if (editing.value) {
      await adminApi.updateGroup(editing.value.id, form)
    } else {
      await adminApi.createGroup(form)
    }
    showModal.value = false
    await loadGroups()
  } catch (e) {
    alert(e.response?.data?.message || 'Save failed')
  } finally {
    saving.value = false
  }
}

async function handleDelete(group) {
  if (!confirm(`Delete group "${group.groupName}"?`)) return
  await adminApi.deleteGroup(group.id)
  await loadGroups()
}
</script>
