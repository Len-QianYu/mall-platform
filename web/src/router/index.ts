import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'index',
      component: () => import('@/views/IndexView.vue'),
      meta: {
        breadcrumbParent: '首页',
        breadcrumbCurrent: '首页',
        requiresAuth: true,
      },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/auth/RegisterView.vue'),
      meta: { guestOnly: true },
    },
    {
      // 管理员首页（与登录页跳转的 name 对应）
      path: '/admin',
      name: 'user_index',
      component: () => import('@/views/admin/DashboardView.vue'),
      meta: {
        breadcrumbParent: '首页',
        breadcrumbCurrent: '管理面板',
        requiresAuth: true,
        requiresAdmin: true,
      },
    },
  ],
})

// 全局前置守卫：未登录跳登录页，已登录访问登录/注册页则跳回首页
router.beforeEach((to) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth && !userStore.logged) {
    return { name: 'login' }
  }

  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    return { name: 'index' }
  }

  if (to.meta.guestOnly && userStore.logged) {
    return { name: 'index' }
  }

  return true
})

export default router
