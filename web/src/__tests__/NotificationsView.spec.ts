import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, setActivePinia, type Pinia } from 'pinia'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useNotificationsStore } from '@/stores/notifications'
import NotificationsView from '@/views/shop/NotificationsView.vue'
import ShopHeader from '@/components/shop/ShopHeader.vue'
import type { ProductNotification } from '@/types/api'

type MockResponse = { data: { status: boolean; payload: object } }

const { get, post } = vi.hoisted(() => ({
  get: vi.fn<
    (url: string, options?: { params?: { unreadOnly?: boolean } }) => Promise<MockResponse>
  >(),
  post: vi.fn<(url: string, body?: { id?: number }) => Promise<MockResponse>>(),
}))
vi.mock('@/utils/request', () => ({ default: { get, post } }))
enableAutoUnmount(afterEach)

const notification = (id: number, type: ProductNotification['type']): ProductNotification => ({
  id,
  type,
  productId: 11,
  productName: '测试商品',
  productImage: null,
  oldPrice: 100,
  newPrice: type === 'PRICE_DROP' ? 80 : 120,
  readAt: null,
  createdAt: '2026-11-22T12:00:00',
})
const response = (payload: object) => ({ data: { status: true, payload } })
let pinia: Pinia
let items: ProductNotification[]

const mountPage = async (header = false) => {
  const routes = [
    'index',
    'products',
    'seckill',
    'cart',
    'favorites',
    'notifications',
    'orders',
    'profile',
    'admin_dashboard',
    'login',
    'register',
  ].map((name) => ({
    path: name === 'index' ? '/' : `/${name}`,
    name,
    component: { template: '<p>page</p>' },
  }))
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      ...routes,
      { path: '/products/:id', name: 'product_detail', component: { template: '<p>product</p>' } },
    ],
  })
  await router.push('/notifications')
  const wrapper = mount(header ? ShopHeader : NotificationsView, {
    global: { plugins: [pinia, router], stubs: { ElImage: true, ElPagination: true } },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('notification center', () => {
  beforeEach(() => {
    localStorage.clear()
    pinia = createPinia()
    setActivePinia(pinia)
    useUserStore().login({ token: 'test-token', user: { id: 7, username: 'test', role: 'user' } })
    items = [
      notification(1, 'LISTED'),
      notification(2, 'PRICE_DROP'),
      notification(3, 'PRICE_RISE'),
    ]
    get.mockReset()
    post.mockReset()
    get.mockImplementation(async (url: string, options?: { params?: { unreadOnly?: boolean } }) => {
      if (url === '/cart') return response({ items: [] })
      const unreadCount = items.filter((item) => item.readAt === null).length
      if (url === '/notification/unread-count') return response({ unreadCount })
      const notifications = options?.params?.unreadOnly
        ? items.filter((item) => item.readAt === null)
        : items
      return response({
        notifications: notifications.map((item) => ({ ...item })),
        unreadCount,
        pagination: { total: notifications.length, pageSize: 20, pageCount: 1, currentPage: 1 },
      })
    })
    post.mockImplementation(async (url: string, body?: { id?: number }) => {
      if (url === '/notification/read-all')
        items.forEach((item) => {
          item.readAt = '2026-11-22T12:01:00'
        })
      if (url === '/notification/read') {
        const item = items.find((item) => item.id === body?.id)
        if (item) item.readAt = '2026-11-22T12:01:00'
      }
      return response({})
    })
  })
  afterEach(() => {
    disposePinia(pinia)
  })

  it('shows relisting, price drop and price rise snapshots', async () => {
    const { wrapper } = await mountPage()
    const cards = wrapper.findAll('.notification-item')
    expect(cards).toHaveLength(3)
    expect(cards[0]?.text()).toContain('商品上架')
    expect(cards[1]?.text()).toContain('降价提醒')
    expect(cards[1]?.get('.notification-prices').text()).toContain('¥100.00')
    expect(cards[1]?.get('.notification-prices').text()).toContain('¥80.00')
    expect(cards[2]?.text()).toContain('涨价提醒')
    expect(cards[2]?.get('.notification-prices').classes()).toContain('price-rise')
  })

  it('marks notifications read and updates the unread filter and badge', async () => {
    const { wrapper } = await mountPage()
    await wrapper.findAll('[role="tab"]')[1]?.trigger('click')
    await flushPromises()
    const buttons = wrapper.findAll('.notification-item')[0]!.findAll('button')
    await buttons[1]?.trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/notification/read', { id: 1 })
    expect(wrapper.findAll('.notification-item')).toHaveLength(2)
    expect(useNotificationsStore().unreadCount).toBe(2)
    expect(get).toHaveBeenCalledWith('/notification', {
      params: { page: 1, pageSize: 20, unreadOnly: true },
    })
  })

  it('marks all notifications read and keeps their history available', async () => {
    const { wrapper } = await mountPage()
    await wrapper.findAll('.heading-actions button')[1]?.trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/notification/read-all')
    expect(wrapper.findAll('.notification-item')).toHaveLength(3)
    expect(wrapper.findAll('.notification-item.unread')).toHaveLength(0)
    expect(useNotificationsStore().unreadCount).toBe(0)
  })

  it('reads a clicked notification before opening its product', async () => {
    const { wrapper, router } = await mountPage()
    await wrapper.findAll('.notification-item')[1]!.findAll('button')[0]?.trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/notification/read', { id: 2 })
    expect(router.currentRoute.value.name).toBe('product_detail')
    expect(router.currentRoute.value.params.id).toBe('11')
  })

  it('allows retrying failed loads and shows a link to start collecting products', async () => {
    get.mockRejectedValueOnce(new Error('offline'))
    items = []
    const { wrapper } = await mountPage()
    expect(wrapper.get('[role="alert"]').text()).toContain('加载通知失败')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('先收藏喜欢的商品')
    expect(wrapper.get('.browse-link').attributes('href')).toBe('/products')
  })

  it('places the bell between the theme and cart buttons and displays unread notifications', async () => {
    useNotificationsStore().unreadCount = 3
    const { wrapper } = await mountPage(true)
    expect(wrapper.find('.header-btn + .notification-link + .cart-link').exists()).toBe(true)
    expect(wrapper.get('.notification-link').attributes('href')).toBe('/notifications')
    expect(wrapper.get('.notification-link .el-badge__content').text()).toBe('3')
  })
})
