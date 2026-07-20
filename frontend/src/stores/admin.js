import { defineStore } from 'pinia'
import { ref } from 'vue'
import { adminApi } from '@/api/admin'

export const useAdminStore = defineStore('admin', () => {
  const models      = ref([])
  const groups      = ref([])
  const creditRates = ref([])
  const users       = ref([])
  const loading     = ref(false)

  async function fetchAll() {
    loading.value = true
    try {
      const [m, g, c, u] = await Promise.all([
        adminApi.getModels(),
        adminApi.getGroups(),
        adminApi.getCreditRates(),
        adminApi.getUsers()
      ])
      models.value      = m.data
      groups.value      = g.data
      creditRates.value = c.data
      users.value       = u.data
    } finally {
      loading.value = false
    }
  }

  async function fetchModels() {
    models.value = (await adminApi.getModels()).data
  }

  async function fetchGroups() {
    groups.value = (await adminApi.getGroups()).data
  }

  return { models, groups, creditRates, users, loading, fetchAll, fetchModels, fetchGroups }
})
