import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import apiClient from '@/api/client'

const STORAGE_KEY = 'ppgpt_auth'

export const useAuthStore = defineStore('auth', () => {
  // ─── State ───────────────────────────────────────────────────────────────
  const token           = ref(null)
  const userId          = ref(null)
  const username        = ref(null)
  const email           = ref(null)
  const role            = ref(null)
  const groupName       = ref(null)
  const maxDailyCredits = ref(0)
  const creditsUsed     = ref(0)
  const expiresAt       = ref(null)

  // ─── Getters ─────────────────────────────────────────────────────────────
  const isLoggedIn = computed(() => !!token.value && Date.now() < (expiresAt.value ?? 0))
  const isAdmin    = computed(() => role.value === 'ROLE_ADMIN')
  const quotaPercent = computed(() => {
    if (!maxDailyCredits.value) return 0
    return Math.min(100, (creditsUsed.value / maxDailyCredits.value) * 100)
  })

  // ─── Actions ─────────────────────────────────────────────────────────────
  async function login(credentials) {
    const { data } = await apiClient.post('/api/v1/auth/login', credentials)
    applyAuthData(data)
    persist()
    return data
  }

  async function refreshMe() {
    try {
      const { data } = await apiClient.get('/api/v1/auth/me')
      applyAuthData(data)
      persist()
    } catch {
      logout()
    }
  }

  function refreshFromStorage() {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return
    try {
      const data = JSON.parse(raw)
      if (!data.token || Date.now() >= data.expiresAt) {
        logout()
        return
      }
      applyAuthData(data)
    } catch {
      logout()
    }
  }

  function logout() {
    token.value           = null
    userId.value          = null
    username.value        = null
    email.value           = null
    role.value            = null
    groupName.value       = null
    maxDailyCredits.value = 0
    creditsUsed.value     = 0
    expiresAt.value       = null
    localStorage.removeItem(STORAGE_KEY)
  }

  function updateCreditsUsed(newUsed) {
    creditsUsed.value = newUsed
    persist()
  }

  // ─── Internal ─────────────────────────────────────────────────────────────
  function applyAuthData(data) {
    token.value           = data.token
    userId.value          = data.userId
    username.value        = data.username
    email.value           = data.email
    role.value            = data.role
    groupName.value       = data.groupName
    maxDailyCredits.value = data.maxDailyCredits ?? 0
    creditsUsed.value     = data.creditsUsedToday ?? 0
    expiresAt.value       = data.expiresAt
  }

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      token:           token.value,
      userId:          userId.value,
      username:        username.value,
      email:           email.value,
      role:            role.value,
      groupName:       groupName.value,
      maxDailyCredits: maxDailyCredits.value,
      creditsUsedToday: creditsUsed.value,
      expiresAt:       expiresAt.value
    }))
  }

  return {
    token, userId, username, email, role,
    groupName, maxDailyCredits, creditsUsed, expiresAt,
    isLoggedIn, isAdmin, quotaPercent,
    login, logout, refreshMe, refreshFromStorage, updateCreditsUsed
  }
})
