import apiClient from './client'

/**
 * Admin API service module for Models, Groups, Credit Rates, Users, Dashboard Analytics, Audit Logs, and MCP Servers.
 */
export const adminApi = {
  /** Retrieves all AI models */
  getModels: () => apiClient.get('/api/v1/admin/models'),

  /** Creates a new AI model */
  createModel: (data) => apiClient.post('/api/v1/admin/models', data),

  /** Updates an existing AI model */
  updateModel: (id, data) => apiClient.put(`/api/v1/admin/models/${id}`, data),

  /** Deletes an AI model */
  deleteModel: (id) => apiClient.delete(`/api/v1/admin/models/${id}`),

  /** Retrieves all user groups */
  getGroups: () => apiClient.get('/api/v1/admin/groups'),

  /** Creates a new user group */
  createGroup: (data) => apiClient.post('/api/v1/admin/groups', data),

  /** Updates a user group */
  updateGroup: (id, data) => apiClient.put(`/api/v1/admin/groups/${id}`, data),

  /** Deletes a user group */
  deleteGroup: (id) => apiClient.delete(`/api/v1/admin/groups/${id}`),

  /** Retrieves credit rate multipliers */
  getCreditRates: () => apiClient.get('/api/v1/admin/credits'),

  /** Upserts credit rate multipliers for a model */
  upsertCreditRate: (data) => apiClient.post('/api/v1/admin/credits', data),

  /** Deletes credit rate configuration */
  deleteCreditRate: (id) => apiClient.delete(`/api/v1/admin/credits/${id}`),

  /** Retrieves all user accounts */
  getUsers: () => apiClient.get('/api/v1/admin/users'),

  /** Creates a new local user */
  createUser: (data) => apiClient.post('/api/v1/admin/users', data),

  /** Updates a user account */
  updateUser: (id, data) => apiClient.put(`/api/v1/admin/users/${id}`, data),

  /** Deletes a user account */
  deleteUser: (id) => apiClient.delete(`/api/v1/admin/users/${id}`),

  /** Retrieves paginated audit logs */
  getAuditLogs: (params = {}) => apiClient.get('/api/v1/admin/audit-logs', { params }),

  /** Retrieves executive dashboard analytics */
  getAnalytics: (params = {}) => apiClient.get('/api/v1/admin/dashboard/analytics', { params }),

  /** Retrieves active models available for chat selection */
  getActiveModels: () => apiClient.get('/api/v1/admin/models'),

  /** Synchronizes tools from an MCP server */
  syncMcpServerTools: (id) => apiClient.post(`/api/v1/admin/mcp-servers/${id}/sync`),

  /** Synchronizes tools across all active MCP servers */
  syncAllMcpTools: () => apiClient.post('/api/v1/admin/mcp-servers/sync-all'),

  /** Retrieves discovered tools for an MCP server */
  getDiscoveredMcpTools: (id) => apiClient.get(`/api/v1/admin/mcp-servers/${id}/tools`),

  /** Creates a manual REST tool definition */
  createManualMcpTool: (id, data) => apiClient.post(`/api/v1/admin/mcp-servers/${id}/tools/manual`, data),

  /** Updates a manual REST tool definition */
  updateManualMcpTool: (id, toolId, data) => apiClient.put(`/api/v1/admin/mcp-servers/${id}/tools/${toolId}`, data),

  /** Deletes a manual REST tool definition */
  deleteManualMcpTool: (id, toolId) => apiClient.delete(`/api/v1/admin/mcp-servers/${id}/tools/${toolId}`),

  /** Imports an OpenAPI specification as tools */
  importOpenApiMcpSpec: (id, data) => apiClient.post(`/api/v1/admin/mcp-servers/${id}/tools/import-openapi`, data),

  /** Retrieves discovered MCP resources */
  getDiscoveredMcpResources: (id) => apiClient.get(`/api/v1/admin/mcp-servers/${id}/resources`),

  /** Retrieves discovered MCP prompts */
  getDiscoveredMcpPrompts: (id) => apiClient.get(`/api/v1/admin/mcp-servers/${id}/prompts`),

  /** Retrieves tool access permissions for a user group */
  getGroupMcpTools: (groupId) => apiClient.get(`/api/v1/admin/groups/${groupId}/mcp-tools`),

  /** Updates tool access permissions for a user group */
  updateGroupMcpTools: (groupId, updates) => apiClient.put(`/api/v1/admin/groups/${groupId}/mcp-tools`, updates)
}
