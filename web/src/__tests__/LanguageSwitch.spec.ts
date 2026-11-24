import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import App from '@/App.vue'
import ShopHeader from '@/components/shop/ShopHeader.vue'
import AdminHeader from '@/components/admin/AdminHeader.vue'
import HomeView from '@/views/shop/HomeView.vue'
import ProductsView from '@/views/shop/ProductsView.vue'
import ProfileView from '@/views/shop/ProfileView.vue'
import { LOCALE_STORAGE_KEY, setLocale } from '@/i18n'
import { useUserStore } from '@/stores/user'
import type { Product, User } from '@/types/api'

const { get, post } = vi.hoisted(() => ({
  get: vi.fn<(url: string) => Promise<unknown>>(),
  post: vi.fn<(url: string, data?: unknown) => Promise<unknown>>(),
}))
vi.mock('@/utils/request', () => ({ default: { get, post } }))
enableAutoUnmount(afterEach)

const user: User = {
  id: 7,
  username: 'qianyu',
  name: '千羽',
  gender: 'F',
  role: 'user',
  email: 'qianyu@example.com',
  phone: '13800138000',
  province: '北京市',
  city: '海淀区',
  receiverAddress: '北京市海淀区中关村大街1号',
  avatarUrl: null,
  status: 1,
  registeredAt: '2026-11-17T09:00:00',
  updatedAt: '2026-11-17T09:00:00',
}
const product: Product = {
  id: 11,
  name: '中文商品原名',
  subtitle: null,
  description: '商品原始介绍',
  category: 'claude玩偶',
  price: 99,
  stock: 8,
  sales: 3,
  mainImage: null,
  status: 1,
  createdAt: '2026-11-17T09:00:00',
  updatedAt: '2026-11-17T09:00:00',
}
const pagination = { total: 21, pageSize: 20, pageCount: 2, currentPage: 1 }
const Shell = defineComponent({ setup: () => () => h('div', [h(ShopHeader), h(RouterView)]) })
const EmptyPage = { template: '<p>page</p>' }

const mountApplication = async (path = '/', signedIn = false) => {
  const pinia = createPinia()
  const store = useUserStore(pinia)
  if (signedIn) store.login({ token: 'language-test-token', user })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/',
        component: Shell,
        children: [
          { path: '', name: 'index', component: HomeView },
          { path: 'products', name: 'products', component: ProductsView },
          { path: 'products/:id', name: 'product_detail', component: EmptyPage },
          { path: 'profile', name: 'profile', component: ProfileView },
          { path: 'seckill', name: 'seckill', component: EmptyPage },
          { path: 'about', name: 'about', component: EmptyPage },
          { path: 'cart', name: 'cart', component: EmptyPage },
          { path: 'orders', name: 'orders', component: EmptyPage },
          { path: 'notifications', name: 'notifications', component: EmptyPage },
          { path: 'favorites', name: 'favorites', component: EmptyPage },
          { path: 'login', name: 'login', component: EmptyPage },
          { path: 'admin', name: 'admin_dashboard', component: EmptyPage },
        ],
      },
    ],
  })
  await router.push(path)
  const wrapper = mount(App, { attachTo: document.body, global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router, store }
}

describe('language controls', () => {
  beforeEach(() => {
    setLocale('zh-CN')
    localStorage.removeItem('user')
    get.mockReset()
    post.mockReset()
    vi.stubEnv('APP_BASE_URL', 'http://localhost:8080')
    get.mockImplementation(async (url: string) => ({
      data: {
        status: true,
        payload:
          url === '/user/profile'
            ? { user }
            : url === '/cart'
              ? { items: [] }
              : url === '/product/categories'
                ? { categories: ['claude玩偶'] }
                : { products: [product], pagination },
      },
    }))
    post.mockResolvedValue({ data: { status: true, payload: {} } })
  })

  afterEach(() => {
    setLocale('zh-CN')
    localStorage.removeItem(LOCALE_STORAGE_KEY)
    localStorage.removeItem('user')
    vi.unstubAllEnvs()
  })

  it('switches navigation, page content and component labels across routes without changing product data', async () => {
    const { wrapper, router } = await mountApplication()
    expect(wrapper.get('.header-right .language-switch').attributes('aria-label')).toBe(
      'Switch to English',
    )
    expect(wrapper.get('.header-nav').text()).toContain('首页')
    await wrapper.get('.language-switch').trigger('click')
    await flushPromises()

    expect(document.documentElement.lang).toBe('en')
    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('en')
    expect(wrapper.get('.header-nav').text()).toContain('Home')
    expect(wrapper.get('.hero-title').text()).toBe('Quality finds, all in one place')
    expect(wrapper.get('.card-name').text()).toBe(product.name)
    expect(wrapper.get('.category-info h3').text()).toBe('Claude plushies')

    await router.push({ name: 'products' })
    await flushPromises()
    expect(wrapper.text()).toContain('Category')
    expect(wrapper.text()).toContain('Recommended')
    expect(wrapper.get('.el-pagination').text()).toContain('Total 21')
    expect(wrapper.get('.card-name').text()).toBe(product.name)
    await wrapper.get('.language-switch').trigger('click')
    await flushPromises()
    expect(document.documentElement.lang).toBe('zh-CN')
    expect(wrapper.text()).toContain('综合')
  })

  it('preserves the route and unsaved profile input when switching languages', async () => {
    const { wrapper, router, store } = await mountApplication('/profile', true)
    const profile = wrapper.getComponent(ProfileView)
    const name = profile.get('.profile-form').findAll('input.el-input__inner')[1]!
    await name.setValue('未保存的名字')
    const nameElement = name.element
    const profileElement = profile.element
    const route = router.currentRoute.value.fullPath
    await wrapper.get('.language-switch').trigger('click')
    await flushPromises()

    const currentProfile = wrapper.getComponent(ProfileView)
    const currentName = currentProfile.get('.profile-form').findAll('input.el-input__inner')[1]!
    expect(currentProfile.element).toBe(profileElement)
    expect(currentName.element).toBe(nameElement)
    expect((currentName.element as HTMLInputElement).value).toBe('未保存的名字')
    expect(router.currentRoute.value.fullPath).toBe(route)
    expect(profile.text()).toContain('Account details')
    expect(store.user?.user.name).toBe('千羽')
    expect(get.mock.calls.filter(([url]) => url === '/user/profile')).toHaveLength(1)
    expect(post).not.toHaveBeenCalled()
  })

  it('shares the same language control with the admin header and role labels', async () => {
    const pinia = createPinia()
    useUserStore(pinia).login({
      token: 'admin-language-test-token',
      user: { ...user, role: 'admin' },
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/admin',
          name: 'admin_dashboard',
          component: EmptyPage,
          meta: { breadcrumbCurrent: '用户管理' },
        },
      ],
    })
    await router.push('/admin')
    const wrapper = mount(AdminHeader, {
      props: { collapsed: false },
      global: { plugins: [pinia, router] },
    })
    await wrapper.get('.header-right .language-switch').trigger('click')
    expect(wrapper.get('.header-breadcrumb').text()).toContain('Users')
    expect(wrapper.get('.user-role-tag').text()).toBe('Admin')
    expect(wrapper.get('.language-switch').attributes('aria-label')).toBe('切换到中文')
  })
})
