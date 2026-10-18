import { createRouter, createWebHistory } from 'vue-router'

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
      },
    },
  ],
})

export default router
