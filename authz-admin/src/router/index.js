import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/grant' },
  { path: '/grant', component: () => import('@/views/GrantView.vue') },
  { path: '/groups', component: () => import('@/views/GroupsView.vue') },
  { path: '/audit', component: () => import('@/views/AuditView.vue') },
  { path: '/simulate', component: () => import('@/views/SimulateView.vue') },
  { path: '/menu-tree', component: () => import('@/views/MenuTreeView.vue') },
  { path: '/master/systems', component: () => import('@/views/master/SystemsView.vue') },
  { path: '/master/shard', component: () => import('@/views/master/ShardView.vue') },
  { path: '/master/menus', component: () => import('@/views/master/MenusView.vue') },
  { path: '/master/apis', component: () => import('@/views/master/ApisView.vue') },
  { path: '/master/actions', component: () => import('@/views/master/ActionsView.vue') },
  { path: '/perftest', component: () => import('@/views/PerfTestView.vue') },
  { path: '/proptest', component: () => import('@/views/PropTestView.vue') }
]
export default createRouter({ history: createWebHistory(), routes })
