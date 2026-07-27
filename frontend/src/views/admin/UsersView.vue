<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-xl font-bold text-[#1a1b22] font-heading">Users</h1>
        <p class="text-sm text-[#4d4732] mt-0.5">Manage user accounts and group assignments</p>
      </div>
      <button @click="openCreate()" class="btn-primary flex items-center gap-2">
        <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
        </svg>
        New User
      </button>
    </div>

    <DataTable :columns="columns" :rows="users" :loading="loading" @edit="openEdit" @delete="handleDelete">
      <template #cell-authSource="{ value }">
        <span :class="value === 'AZURE_AD' ? 'badge badge-blue' : 'badge badge-purple'">{{ value }}</span>
      </template>
    </DataTable>

    <ModalForm
      v-if="showModal"
      :title="editing ? 'Edit User' : 'New User'"
      :loading="saving"
      @close="showModal = false"
      @confirm="handleSave"
    >
      <div class="space-y-4">
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="label">Username</label>
            <input v-model="form.username" class="input-field" :disabled="!!editing" />
          </div>
          <div>
            <label class="label">Email</label>
            <input v-model="form.email" type="email" class="input-field" />
          </div>
        </div>
        <div v-if="!editing">
          <label class="label">Password</label>
          <input v-model="form.password" type="password" class="input-field" autocomplete="new-password" />
        </div>
        <div>
          <label class="label">Auth Source</label>
          <select v-model="form.authSource" class="input-field">
            <option value="LOCAL">LOCAL</option>
            <option value="AZURE_AD">AZURE_AD</option>
          </select>
        </div>
        <div>
          <label class="label">Group</label>
          <select v-model="form.groupId" class="input-field">
            <option v-for="g in groups" :key="g.id" :value="g.id">{{ g.groupName }}</option>
          </select>
        </div>
      </div>
    </ModalForm>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import DataTable from '@/components/DataTable.vue'
import ModalForm from '@/components/ModalForm.vue'
import { adminApi } from '@/api/admin'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'

const toast = useToast()
const { confirm } = useConfirm()
const users     = ref([])
const groups    = ref([])
const loading   = ref(true)
const saving    = ref(false)
const showModal = ref(false)
const editing   = ref(null)

const form = reactive({ username: '', email: '', password: '', authSource: 'LOCAL', groupId: '' })

const columns = [
  { key: 'username',   label: 'Username'  },
  { key: 'email',      label: 'Email'     },
  { key: 'authSource', label: 'Auth'      },
  { key: 'groupName',  label: 'Group'     },
]

onMounted(async () => {
  loading.value = true
  const [u, g] = await Promise.all([adminApi.getUsers(), adminApi.getGroups()])
  users.value  = u.data
  groups.value = g.data
  loading.value = false
})

function openCreate() {
  editing.value = null
  Object.assign(form, { username: '', email: '', password: '', authSource: 'LOCAL', groupId: groups.value[0]?.id ?? '' })
  showModal.value = true
}

function openEdit(user) {
  editing.value = user
  Object.assign(form, { ...user, password: '' })
  showModal.value = true
}

async function handleSave() {
  saving.value = true
  try {
    if (editing.value) {
      await adminApi.updateUser(editing.value.id, form)
      toast.success(`User '${form.username}' updated successfully!`)
    } else {
      await adminApi.createUser(form)
      toast.success(`User '${form.username}' created successfully!`)
    }
    showModal.value = false
    users.value = (await adminApi.getUsers()).data
  } catch (e) {
    toast.error(e.response?.data?.message || 'Save failed')
  } finally {
    saving.value = false
  }
}

async function handleDelete(user) {
  const isConfirmed = await confirm({
    title: 'Delete User',
    message: `Are you sure you want to delete user "${user.username}"? This action cannot be undone.`,
    confirmText: 'Delete User',
    type: 'danger'
  })
  if (!isConfirmed) return

  try {
    await adminApi.deleteUser(user.id)
    toast.success(`User '${user.username}' deleted successfully!`)
    users.value = (await adminApi.getUsers()).data
  } catch (e) {
    toast.error(e.response?.data?.message || 'Delete failed')
  }
}
</script>
