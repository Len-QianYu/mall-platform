import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, setActivePinia, type Pinia } from 'pinia'
import { DOMWrapper, enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { setLocale } from '@/i18n'
import { useUserStore } from '@/stores/user'
import { useNotificationsStore } from '@/stores/notifications'
import NotificationsView from '@/views/shop/NotificationsView.vue'
import ShopHeader from '@/components/shop/ShopHeader.vue'
import ShopLayout from '@/layouts/ShopLayout.vue'
import type { OrderShipmentNotification, ProductNotification, UserNotification } from '@/types/api'

type MockResponse = { data: { status: boolean; payload: object } }
type PopupOptions = { title?: string; message?: string; onClick?: () => unknown }

const { get, post, notify } = vi.hoisted(() => ({
  get: vi.fn<
    (url: string, options?: { params?: { unreadOnly?: boolean } }) => Promise<MockResponse>
  >(),
  post: vi.fn<(url: string, body?: { id?: number; type?: string }) => Promise<MockResponse>>(),
  notify: vi.fn<(options: PopupOptions) => { close: () => void }>(),
}))
vi.mock('@/utils/request', () => ({ default: { get, post } }))
vi.mock('element-plus', async (original) => ({
  ...(await original<typeof import('element-plus')>()),
  ElNotification: notify,
}))
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
const shipmentNotification: OrderShipmentNotification = {
  id: 1,
  type: 'ORDER_SHIPPED',
  orderId: 99,
  orderNo: 'ORDER-99',
  totalAmount: 168.8,
  shippedAt: '2026-11-25T12:30:00',
  readAt: null,
  createdAt: '2026-11-25T12:30:00',
}
const response = (payload: object) => ({ data: { status: true, payload } })
let pinia: Pinia
let items: UserNotification[]

const mountPage = async (header = false, layout = false) => {
  const routes = [
    'index',
    'products',
    'seckill',
    'about',
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
      { path: '/orders/:id', name: 'order_detail', component: { template: '<p>order</p>' } },
    ],
  })
  await router.push(header ? '/' : '/notifications')
  const wrapper = mount(layout ? ShopLayout : header ? ShopHeader : NotificationsView, {
    attachTo: document.body,
    global: {
      plugins: [pinia, router],
      stubs: { ElImage: true, ElPagination: true },
    },
  })
  await flushPromises()
  return { wrapper: header ? new DOMWrapper(document.body) : wrapper, router }
}

const openDropdown = async (wrapper: Awaited<ReturnType<typeof mountPage>>['wrapper']) => {
  await wrapper.get('.notification-link').trigger('click')
  await vi.waitFor(() => {
    expect(wrapper.get('.notification-link').attributes('aria-expanded')).toBe('true')
  })
  await flushPromises()
}

describe('notification center', () => {
  beforeEach(() => {
    localStorage.clear()
    setLocale('zh-CN')
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
    notify.mockReset()
    notify.mockReturnValue({ close: vi.fn() })
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
    post.mockImplementation(async (url: string, body?: { id?: number; type?: string }) => {
      if (url === '/notification/read-all')
        items.forEach((item) => {
          item.readAt = '2026-11-22T12:01:00'
        })
      if (url === '/notification/read') {
        const item = items.find(
          (item) =>
            item.id === body?.id &&
            (body?.type === 'ORDER_SHIPPED'
              ? item.type === 'ORDER_SHIPPED'
              : item.type !== 'ORDER_SHIPPED'),
        )
        if (item) item.readAt = '2026-11-22T12:01:00'
      }
      return response({})
    })
  })
  afterEach(() => {
    useNotificationsStore().stop()
    vi.unstubAllGlobals()
    setLocale('zh-CN')
    disposePinia(pinia)
    document.body.innerHTML = ''
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
    expect(wrapper.text()).toContain('暂时没有消息通知')
    expect(wrapper.get('.browse-link').attributes('href')).toBe('/products')
  })

  it.each([false, true])(
    'opens the buyer order and only reads its shipment when header=%s and product IDs collide',
    async (header) => {
      items.unshift({ ...shipmentNotification })
      const { wrapper, router } = await mountPage(header)
      if (header) await openDropdown(wrapper)
      const cards = wrapper.findAll(header ? '.dropdown-notification-item' : '.notification-item')
      expect(cards).toHaveLength(4)
      expect(cards[0]!.text()).toContain('已发货')
      expect(cards[0]!.text()).toContain('ORDER-99')
      expect(cards[0]!.find('.bi-truck').exists()).toBe(true)
      if (header) await cards[0]!.get('.dropdown-product').trigger('click')
      else await cards[0]!.findAll('button')[0]!.trigger('click')
      await flushPromises()
      expect(post).toHaveBeenCalledWith('/notification/read', {
        id: 1,
        type: 'ORDER_SHIPPED',
      })
      expect(router.currentRoute.value.name).toBe('order_detail')
      expect(router.currentRoute.value.params.id).toBe('99')
      expect(items.find((item) => item.type === 'LISTED')?.readAt).toBeNull()
      expect(useNotificationsStore().unreadCount).toBe(3)
    },
  )

  it('marks both shipment and product notifications read and keeps their history', async () => {
    items.unshift({ ...shipmentNotification })
    const { wrapper } = await mountPage()
    await wrapper.findAll('.heading-actions button')[1]!.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.notification-item')).toHaveLength(4)
    expect(wrapper.findAll('.notification-item.unread')).toHaveLength(0)
    expect(useNotificationsStore().unreadCount).toBe(0)
  })

  it('translates shipment messages and opens the order from its live popup', async () => {
    vi.stubGlobal(
      'WebSocket',
      class {
        static OPEN = 1
        readyState = 0
        close() {}
      },
    )
    items.unshift({ ...shipmentNotification })
    setLocale('en')
    const { router } = await mountPage(false, true)
    useNotificationsStore().latestNotification = { ...shipmentNotification }
    await flushPromises()
    const popup = notify.mock.calls[notify.mock.calls.length - 1]![0]
    expect(popup.title).toBe('Shipped')
    expect(popup.message).toContain('Your order ORDER-99 has shipped')
    await popup.onClick?.()
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/notification/read', { id: 1, type: 'ORDER_SHIPPED' })
    expect(router.currentRoute.value.name).toBe('order_detail')
    expect(router.currentRoute.value.params.id).toBe('99')
  })

  it('opens recent notifications from the aligned toolbar without navigating away', async () => {
    useNotificationsStore().unreadCount = 3
    const { wrapper, router } = await mountPage(true)
    expect(wrapper.find('.language-switch + .notification-link').exists()).toBe(true)
    expect(wrapper.get('.notification-link .el-badge__content').text()).toBe('3')
    expect(wrapper.get('.notification-link').attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('.notification-dropdown').exists()).toBe(false)

    await openDropdown(wrapper)

    expect(router.currentRoute.value.name).toBe('index')
    expect(wrapper.get('.notification-link').attributes('aria-expanded')).toBe('true')
    expect(wrapper.findAll('.dropdown-notification-item')).toHaveLength(3)
    expect(wrapper.get('.notification-dropdown').text()).toContain('3 条未读')
    expect(get).toHaveBeenCalledWith('/notification', {
      params: { page: 1, pageSize: 8, unreadOnly: false },
    })
  })

  it('filters unread notifications and marks one read while keeping the dropdown open', async () => {
    const { wrapper, router } = await mountPage(true)
    await openDropdown(wrapper)
    await wrapper.findAll('.dropdown-tabs [role="tab"]')[1]?.trigger('click')
    await flushPromises()
    await wrapper.get('.dropdown-mark-read').trigger('click')
    await flushPromises()

    expect(post).toHaveBeenCalledWith('/notification/read', { id: 1 })
    expect(wrapper.findAll('.dropdown-notification-item')).toHaveLength(2)
    expect(wrapper.get('.notification-link .el-badge__content').text()).toBe('2')
    expect(wrapper.get('.notification-link').attributes('aria-expanded')).toBe('true')
    expect(router.currentRoute.value.name).toBe('index')
  })

  it('marks all dropdown notifications read and preserves their history', async () => {
    const { wrapper } = await mountPage(true)
    await openDropdown(wrapper)
    await wrapper.get('.dropdown-read-all').trigger('click')
    await flushPromises()

    expect(post).toHaveBeenCalledWith('/notification/read-all')
    expect(wrapper.findAll('.dropdown-notification-item')).toHaveLength(3)
    expect(wrapper.findAll('.dropdown-notification-item.unread')).toHaveLength(0)
    expect(useNotificationsStore().unreadCount).toBe(0)
    expect(wrapper.get('.dropdown-read-all').attributes('disabled')).toBeDefined()
  })

  it('marks a dropdown notification read before opening its product and closes the dropdown', async () => {
    const { wrapper, router } = await mountPage(true)
    await openDropdown(wrapper)
    await wrapper.findAll('.dropdown-product')[1]?.trigger('click')
    await flushPromises()

    expect(post).toHaveBeenCalledWith('/notification/read', { id: 2 })
    expect(router.currentRoute.value.name).toBe('product_detail')
    expect(router.currentRoute.value.params.id).toBe('11')
    expect(wrapper.get('.notification-link').attributes('aria-expanded')).toBe('false')
  })

  it('keeps a failed read notification visible without opening its product', async () => {
    const { wrapper, router } = await mountPage(true)
    await openDropdown(wrapper)
    post.mockRejectedValueOnce(new Error('标记已读失败'))
    await wrapper.get('.dropdown-product').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('index')
    expect(wrapper.get('.notification-link').attributes('aria-expanded')).toBe('true')
    expect(wrapper.findAll('.dropdown-notification-item.unread')).toHaveLength(3)
  })

  it('refreshes the dropdown after a live notification and avoids loading lists while closed', async () => {
    const { wrapper } = await mountPage(true)
    const store = useNotificationsStore()
    get.mockClear()
    store.revision += 1
    await flushPromises()
    expect(get).not.toHaveBeenCalled()

    await openDropdown(wrapper)
    items.unshift({ ...notification(4, 'PRICE_DROP'), productName: '刚降价的玩偶' })
    store.revision += 1
    await flushPromises()

    expect(wrapper.findAll('.dropdown-notification-item')).toHaveLength(4)
    expect(wrapper.get('.notification-dropdown').text()).toContain('刚降价的玩偶')
  })

  it('shows a sign-in prompt without requesting private notifications for guests', async () => {
    useUserStore().logout()
    const { wrapper, router } = await mountPage(true)
    get.mockClear()
    await openDropdown(wrapper)

    expect(wrapper.get('.dropdown-login').text()).toContain('登录后查看消息通知')
    expect(get).not.toHaveBeenCalled()
    await wrapper.get('.dropdown-login a').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/')
  })

  it('discards a delayed notification response after logging out', async () => {
    let complete!: (result: MockResponse) => void
    const original = get.getMockImplementation()!
    get.mockImplementation((url, options) =>
      url === '/notification'
        ? new Promise((resolve) => {
            complete = resolve
          })
        : original(url, options),
    )
    const { wrapper } = await mountPage(true)
    await openDropdown(wrapper)
    expect(wrapper.get('[role="status"]').text()).toContain('正在加载通知')

    useUserStore().logout()
    complete(response({ notifications: items }))
    await flushPromises()
    expect(wrapper.get('.notification-link').attributes('aria-expanded')).toBe('false')
    await openDropdown(wrapper)
    expect(wrapper.find('.dropdown-login').exists()).toBe(true)
    expect(wrapper.find('.dropdown-notification-item').exists()).toBe(false)
  })

  it('retries failed dropdown loads and opens the full notification history from its footer', async () => {
    const original = get.getMockImplementation()!
    let fail = true
    get.mockImplementation(async (url, options) => {
      if (url === '/notification' && fail) {
        fail = false
        throw new Error('offline')
      }
      return original(url, options)
    })
    const { wrapper, router } = await mountPage(true)
    await openDropdown(wrapper)
    expect(wrapper.get('.dropdown-error').text()).toContain('加载通知失败')
    await wrapper.get('.dropdown-error button').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.dropdown-notification-item')).toHaveLength(3)
    await wrapper.get('.dropdown-view-all').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('notifications')
    expect(wrapper.get('.notification-link').attributes('aria-expanded')).toBe('false')
  })

  it('closes the dropdown with Escape or a click outside', async () => {
    const { wrapper } = await mountPage(true)
    await openDropdown(wrapper)
    await wrapper.get('.notification-dropdown').trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(wrapper.get('.notification-link').attributes('aria-expanded')).toBe('false')

    await openDropdown(wrapper)
    await wrapper.get('.logo-link').trigger('pointerdown')
    await wrapper.get('.logo-link').trigger('click')
    await vi.waitFor(() => {
      expect(wrapper.get('.notification-link').attributes('aria-expanded')).toBe('false')
    })
  })

  it('translates the open notification dropdown without translating product data', async () => {
    const { wrapper } = await mountPage(true)
    await openDropdown(wrapper)
    setLocale('en')
    await flushPromises()

    expect(wrapper.get('.dropdown-heading').text()).toContain('3 unread')
    expect(wrapper.get('.dropdown-read-all').text()).toBe('Mark all as read')
    expect(wrapper.get('.dropdown-view-all').text()).toContain('View all notifications')
    expect(wrapper.get('.dropdown-product-name').text()).toBe('测试商品')
  })
})
