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
      // 管理端：AdminLayout 提供侧边栏、顶栏和页脚，子路由渲染在内容区；
      // meta 会合并到子路由，守卫按 requiresAuth / requiresAdmin 拦截整个 /admin
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: {
        requiresAuth: true,
        requiresAdmin: true,
      },
      children: [
        {
          // 管理员首页（与登录页跳转的 name 对应）
          path: '',
          name: 'admin_dashboard',
          component: () => import('@/views/admin/DashboardView.vue'),
          meta: { breadcrumbCurrent: '仪表盘' },
        },
        {
          path: 'users',
          name: 'admin_users',
          component: () => import('@/views/admin/UsersView.vue'),
          meta: { breadcrumbCurrent: '用户管理' },
        },
        {
          path: 'products',
          name: 'admin_products',
          component: () => import('@/views/admin/ProductsView.vue'),
          meta: { breadcrumbCurrent: '商品管理' },
        },
        {
          path: 'orders',
          name: 'admin_orders',
          component: () => import('@/views/admin/OrdersView.vue'),
          meta: { breadcrumbParent: '交易管理', breadcrumbCurrent: '订单管理' },
        },
        {
          path: 'payments',
          name: 'admin_payments',
          component: () => import('@/views/admin/PaymentsView.vue'),
          meta: { breadcrumbParent: '交易管理', breadcrumbCurrent: '支付流水' },
        },
      ],
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
