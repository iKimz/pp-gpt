import apiClient from './client'

// ─── Models ──────────────────────────────────────────────────────────
export const adminApi = {
  // Models
  getModels:   ()         => apiClient.get('/api/v1/admin/models'),
  createModel: (data)     => apiClient.post('/api/v1/admin/models', data),
  updateModel: (id, data) => apiClient.put(`/api/v1/admin/models/${id}`, data),
  deleteModel: (id)       => apiClient.delete(`/api/v1/admin/models/${id}`),

  // Groups
  getGroups:   ()         => apiClient.get('/api/v1/admin/groups'),
  createGroup: (data)     => apiClient.post('/api/v1/admin/groups', data),
  updateGroup: (id, data) => apiClient.put(`/api/v1/admin/groups/${id}`, data),
  deleteGroup: (id)       => apiClient.delete(`/api/v1/admin/groups/${id}`),

  // Credit Rates
  getCreditRates:  ()     => apiClient.get('/api/v1/admin/credits'),
  upsertCreditRate:(data) => apiClient.post('/api/v1/admin/credits', data),
  deleteCreditRate:(id)   => apiClient.delete(`/api/v1/admin/credits/${id}`),

  // Users
  getUsers:    ()         => apiClient.get('/api/v1/admin/users'),
  createUser:  (data)     => apiClient.post('/api/v1/admin/users', data),
  updateUser:  (id, data) => apiClient.put(`/api/v1/admin/users/${id}`, data),
  deleteUser:  (id)       => apiClient.delete(`/api/v1/admin/users/${id}`),

  // Audit Logs
  getAuditLogs:(params = {}) =>
    apiClient.get('/api/v1/admin/audit-logs', { params }),

  // Dashboard Analytics
  getAnalytics: (params = {}) => apiClient.get('/api/v1/admin/dashboard/analytics', { params }),

  // Public model list (for chat model selector)
  getActiveModels: () => apiClient.get('/api/v1/admin/models')
}
