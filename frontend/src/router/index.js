import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('@/views/chat/ChatView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '',          redirect: '/admin/analytics' },
      { path: 'analytics', name: 'admin-analytics', component: () => import('@/views/admin/AnalyticsView.vue') },
      { path: 'groups',    name: 'admin-groups',    component: () => import('@/views/admin/GroupsView.vue') },
      { path: 'models',    name: 'admin-models',    component: () => import('@/views/admin/ModelsView.vue') },
      { path: 'mcp-servers', name: 'admin-mcp-servers', component: () => import('@/views/admin/McpServersView.vue') },
      { path: 'credits',   name: 'admin-credits',   component: () => import('@/views/admin/CreditsView.vue') },
      { path: 'users',     name: 'admin-users',     component: () => import('@/views/admin/UsersView.vue') },
      { path: 'logs',      name: 'admin-logs',      component: () => import('@/views/admin/AuditLogsView.vue') }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/chat'
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

// ─── Global Navigation Guard ──────────────────────────────────────────
router.beforeEach((to, from, next) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    // Already logged in → redirect away from /login
    if (auth.isLoggedIn && to.name === 'login') return next('/chat')
    return next()
  }

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return next({ name: 'login', query: { redirect: to.fullPath } })
  }

  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return next('/chat')
  }

  next()
})

export default router
