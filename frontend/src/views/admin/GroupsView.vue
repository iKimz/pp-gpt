<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-xl font-bold text-[#1a1b22] font-heading">User Groups</h1>
        <p class="text-sm text-[#4d4732] mt-0.5">Manage groups and their AI model access</p>
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

const guardrailModels  = computed(() => allModels.value.filter(m => m.modelType === 'GUARDRAIL'))
const generationModels = computed(() => allModels.value.filter(m => m.modelType !== 'GUARDRAIL'))

const form = reactive({ groupName: '', maxDailyCredits: 1000, allowedModelIds: [], guardrailModelId: null })

const columns = [
  { key: 'groupName',       label: 'Name'           },
  { key: 'maxDailyCredits', label: 'Daily Credits'  },
  { key: 'allowedModelIds', label: 'Model Access'   },
]

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
