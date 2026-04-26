import axios from 'axios'

const api = axios.create({ baseURL: '/api/v1', timeout: 8000 })

export const Authz = {
  check: (params) => api.get('/authz/check', { params }).then(r => r.data),
  checkBatch: (body) => api.post('/authz/check-batch', body).then(r => r.data),
  menuActions: (params) => api.get('/authz/menu-actions', { params }).then(r => r.data),
  menuTree: (body) => api.post('/authz/menu-tree', body).then(r => r.data),
  cacheStats: () => api.get('/authz/cache/stats').then(r => r.data),
  warmupSystem: (body) => api.post('/authz/warmup/system', body).then(r => r.data),
  warmupMenu: (body) => api.post('/authz/warmup/menu', body).then(r => r.data),
  simulateGrant: (body) => api.post('/authz/simulate/grant', body).then(r => r.data),
  simulateRevoke: (body) => api.post('/authz/simulate/revoke', body).then(r => r.data)
}

export const Master = {
  systems: () => api.get('/systems').then(r => r.data),
  shardConfig: (cd) => api.get(`/systems/${cd}/shard-config`).then(r => r.data),
  saveShardConfig: (cd, body) => api.put(`/systems/${cd}/shard-config`, body).then(r => r.data),
  companies: () => api.get('/companies').then(r => r.data),
  depts: (cd) => api.get(`/companies/${cd}/depts`).then(r => r.data),
  users: () => api.get('/users').then(r => r.data),
  menus: (system) => api.get(`/systems/${system}/menus`).then(r => r.data),
  apis: (system) => api.get(`/systems/${system}/apis`).then(r => r.data),
  actions: (system) => api.get(`/systems/${system}/actions`).then(r => r.data),
  permissionsBySubject: (params) => api.get('/permissions/by-subject', { params }).then(r => r.data),
  permissionsByTarget: (params) => api.get('/permissions/by-target', { params }).then(r => r.data),
  grant: (body, actor='leecy@hd.com') => api.post('/permissions', body, { headers: { 'X-User-Id': actor } }).then(r => r.data),
  revoke: (id, actor='leecy@hd.com') => api.delete(`/permissions/${id}`, { headers: { 'X-User-Id': actor } }).then(r => r.data)
}

export const Audit = {
  changes: (params) => api.get('/audit/changes', { params }).then(r => r.data),
  byUser: (params) => api.get('/audit/permissions/by-user', { params }).then(r => r.data),
  byMenu: (params) => api.get('/audit/permissions/by-menu', { params }).then(r => r.data)
}

export default api
