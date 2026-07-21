import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const rawBase = import.meta.env.BASE_URL || '/'
const baseURL = rawBase.endsWith('/') ? rawBase : rawBase + '/'

const apiClient = axios.create({
  baseURL,
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' }
})

// ─── Request interceptor: inject JWT ─────────────────────────────────
apiClient.interceptors.request.use(config => {
  if (config.url && config.url.startsWith('/')) {
    config.url = config.url.substring(1)
  }
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
}, error => Promise.reject(error))

// ─── Response interceptor: handle 401 / 403 ──────────────────────────
apiClient.interceptors.response.use(
  response => response,
  error => {
    const status = error.response?.status
    if (status === 401) {
      const auth = useAuthStore()
      auth.logout()
      router.push({ name: 'login' })
    }
    return Promise.reject(error)
  }
)

export default apiClient
