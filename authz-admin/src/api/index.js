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
  // systems
  systems: () => api.get('/systems').then(r => r.data),
  getSystem: (cd) => api.get(`/systems/${cd}`).then(r => r.data),
  saveSystem: (s) => api.post('/systems', s).then(r => r.data),
  updateSystem: (cd, s) => api.put(`/systems/${cd}`, s).then(r => r.data),
  deleteSystem: (cd) => api.delete(`/systems/${cd}`).then(r => r.data),
  systemStats: (cd) => api.get(`/systems/${cd}/stats`).then(r => r.data),
  // attrs
  attrs: (cd) => api.get(`/systems/${cd}/attrs`).then(r => r.data),
  setAttr: (cd, key, value) => api.put(`/systems/${cd}/attrs/${key}`, { attr_value: value }).then(r => r.data),
  deleteAttr: (cd, key) => api.delete(`/systems/${cd}/attrs/${key}`).then(r => r.data),
  // shard cfg
  shardConfig: (cd) => api.get(`/systems/${cd}/shard-config`).then(r => r.data),
  saveShardConfig: (cd, body) => api.put(`/systems/${cd}/shard-config`, body).then(r => r.data),
  // org
  companies: () => api.get('/companies').then(r => r.data),
  saveCompany: (c) => api.post('/companies', c).then(r => r.data),
  updateCompany: (cd, c) => api.put(`/companies/${cd}`, c).then(r => r.data),
  deleteCompany: (cd) => api.delete(`/companies/${cd}`).then(r => r.data),
  depts: (cd) => api.get(`/companies/${cd}/depts`).then(r => r.data),
  saveDept: (d) => api.post('/depts', d).then(r => r.data),
  deleteDept: (companyCd, deptId) => api.delete(`/depts/${companyCd}/${deptId}`).then(r => r.data),
  users: () => api.get('/users').then(r => r.data),
  saveUser: (u) => api.post('/users', u).then(r => r.data),
  updateUser: (id, u) => api.put(`/users/${id}`, u).then(r => r.data),
  deleteUser: (id) => api.delete(`/users/${id}`).then(r => r.data),
  // actions
  actions: (system) => api.get(`/systems/${system}/actions`).then(r => r.data),
  saveAction: (system, a) => api.post(`/systems/${system}/actions`, a).then(r => r.data),
  updateAction: (system, cd, a) => api.put(`/systems/${system}/actions/${cd}`, a).then(r => r.data),
  deleteAction: (system, cd) => api.delete(`/systems/${system}/actions/${cd}`).then(r => r.data),
  // menus
  menus: (system) => api.get(`/systems/${system}/menus`).then(r => r.data),
  getMenu: (id) => api.get(`/menus/${id}`).then(r => r.data),
  saveMenu: (m) => api.post('/menus', m).then(r => r.data),
  updateMenu: (id, m) => api.put(`/menus/${id}`, m).then(r => r.data),
  deleteMenu: (id) => api.delete(`/menus/${id}`).then(r => r.data),
  getMenuImpl: (id) => api.get(`/menus/${id}/impl`).then(r => r.data),
  saveMenuImpl: (id, impl) => api.put(`/menus/${id}/impl`, impl).then(r => r.data),
  menuActions: (id) => api.get(`/menus/${id}/actions`).then(r => r.data),
  enableMenuAction: (id, action) => api.post(`/menus/${id}/actions/${action}`).then(r => r.data),
  disableMenuAction: (id, action) => api.delete(`/menus/${id}/actions/${action}`).then(r => r.data),
  menuMappings: (id) => api.get(`/menus/${id}/mappings`).then(r => r.data),
  mapMenuApi: (id, action, apiId) => api.post(`/menus/${id}/actions/${action}/apis/${apiId}`).then(r => r.data),
  unmapMenuApi: (id, action, apiId) => api.delete(`/menus/${id}/actions/${action}/apis/${apiId}`).then(r => r.data),
  recommendApis: (id, params) => api.get(`/menus/${id}/recommend-apis`, { params }).then(r => r.data),
  // apis
  apis: (system) => api.get(`/systems/${system}/apis`).then(r => r.data),
  saveApi: (a) => api.post('/apis', a).then(r => r.data),
  updateApi: (id, a) => api.put(`/apis/${id}`, a).then(r => r.data),
  deleteApi: (id) => api.delete(`/apis/${id}`).then(r => r.data),
  apiUsages: (id) => api.get(`/apis/${id}/usages`).then(r => r.data),
  // permissions
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
