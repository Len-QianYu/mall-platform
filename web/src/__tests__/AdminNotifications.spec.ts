import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, setActivePinia, type Pinia } from 'pinia'
import { DOMWrapper, enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { setLocale } from '@/i18n'
import { useUserStore } from '@/stores/user'
import { useNotificationsStore } from '@/stores/notifications'
import { useTheme } from '@/utils/useTheme'
import AdminLayout from '@/layouts/AdminLayout.vue'
import NotificationsView from '@/views/shop/NotificationsView.vue'
import OrdersView from '@/views/admin/OrdersView.vue'
import type { Order, OrderNotification } from '@/types/api'

type MockResponse = { data: { status: boolean; payload: object } }
type PopupOptions = { title?: string; message?: string; onClick?: () => unknown }
const { get, post, notify } = vi.hoisted(() => ({
  get: vi.fn<
    (url: string, options?: { params?: { unreadOnly?: boolean } }) => Promise<MockResponse>
  >(),
  post: vi.fn<(url: string, body?: { id?: number }) => Promise<MockResponse>>(),
  notify: vi.fn<(options: PopupOptions) => { close: () => void }>(),
}))
vi.mock('@/utils/request', () => ({ default: { get, post } }))
vi.mock('element-plus', async (original) => ({
  ...(await original<typeof import('element-plus')>()),
  ElNotification: notify,
}))

class TestSocket {
  static OPEN = 1
  static instances: TestSocket[] = []
  readyState = 0
  onopen: ((event: Event) => void) | null = null
  onclose: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent<string>) => void) | null = null
  send = vi.fn()
  constructor() {
    TestSocket.instances.push(this)
  }
  open() {
    this.readyState = 1
    this.onopen?.(new Event('open'))
  }
  close() {
    this.readyState = 3
    this.onclose?.(new Event('close'))
  }
  receive(message: object) {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(message) }))
  }
}

enableAutoUnmount(afterEach)
let pinia: Pinia
let items: OrderNotification[]
const response = (payload: object) => ({ data: { status: true, payload } })
const notification = (id: number, orderId: number): OrderNotification => ({
  id,
  type: 'NEW_ORDER',
  orderId,
  orderNo: `ORDER-${orderId}`,
  buyerId: 8,
  buyerUsername: 'buyer_raw',
  buyerName: '买家 张三',
  totalAmount: 168.8,
  readAt: null,
  createdAt: '2026-11-25T12:00:00',
})
const order = (id: number): Order => ({
  id,
  orderNo: `ORDER-${id}`,
  userId: 8,
  totalAmount: 168.8,
  status: 'PENDING_PAYMENT',
  receiverName: '收货人 李四',
  receiverPhone: '13900139000',
  receiverAddress: '测试地址',
  remark: null,
  paidAt: null,
  shippedAt: null,
  completedAt: null,
  cancelledAt: null,
  createdAt: '2026-11-25T12:00:00',
  updatedAt: '2026-11-25T12:00:00',
})

const mountAdmin = async (path = '/admin') => {
  const dummy = { template: '<p>page</p>' }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'index', component: dummy },
      { path: '/login', name: 'login', component: dummy },
      {
        path: '/admin',
        component: AdminLayout,
        children: [
          { path: '', name: 'admin_dashboard', component: dummy },
          { path: 'profile', name: 'admin_profile', component: dummy },
          { path: 'users', name: 'admin_users', component: dummy },
          { path: 'products', name: 'admin_products', component: dummy },
          { path: 'seckill', name: 'admin_seckill', component: dummy },
          { path: 'payments', name: 'admin_payments', component: dummy },
          { path: 'orders', name: 'admin_orders', component: OrdersView },
          {
            path: 'notifications',
            name: 'admin_notifications',
            component: NotificationsView,
            props: { admin: true },
          },
        ],
      },
    ],
  })
  await router.push(path)
  mount(
    { template: '<RouterView />' },
    {
      attachTo: document.body,
      global: {
        plugins: [pinia, router],
        stubs: { ElImage: true, ElPagination: true, transition: false },
      },
    },
  )
  await flushPromises()
  TestSocket.instances[0]?.open()
  await flushPromises()
  return { wrapper: new DOMWrapper(document.body), router }
}

const openDropdown = async (wrapper: DOMWrapper<Element>) => {
  await wrapper.get('.notification-link').trigger('click')
  await vi.waitFor(() => {
    expect(wrapper.get('.notification-link').attributes('aria-expanded')).toBe('true')
  })
  await flushPromises()
}

describe('admin new order notifications', () => {
  beforeEach(() => {
    localStorage.clear()
    setLocale('zh-CN')
    useTheme().isDark.value = false
    pinia = createPinia()
    setActivePinia(pinia)
    useUserStore().login({
      token: 'admin-token',
      user: { id: 7, username: 'admin', role: 'admin' },
    })
    vi.stubGlobal('WebSocket', TestSocket)
    TestSocket.instances = []
    items = [notification(1, 42), notification(2, 43)]
    get.mockReset()
    post.mockReset()
    notify.mockReset().mockReturnValue({ close: vi.fn() })
    get.mockImplementation(async (url, options) => {
      if (url === '/notification/unread-count') return response({ unreadCount: 9 })
      const unreadCount = items.filter((item) => item.readAt === null).length
      if (url === '/admin/notification/unread-count') return response({ unreadCount })
      if (url === '/admin/notification') {
        const selected = items.filter(
          (item) => !options?.params?.unreadOnly || item.readAt === null,
        )
        return response({
          notifications: selected.map((item) => ({ ...item })),
          unreadCount,
          pagination: { total: selected.length, pageSize: 20, currentPage: 1, pageCount: 1 },
        })
      }
      if (url === '/admin/order')
        return response({
          orders: [order(42), order(43)],
          pagination: { total: 2, pageSize: 10, currentPage: 1, pageCount: 1 },
        })
      const detail = /^\/admin\/order\/(\d+)$/.exec(url)
      if (detail) return response({ order: order(Number(detail[1])), items: [] })
      throw new Error(`Unexpected request: ${url}`)
    })
    post.mockImplementation(async (url, body) => {
      if (url === '/notification/socket-ticket') return response({ ticket: 'short-ticket' })
      if (url === '/admin/notification/read') {
        const item = items.find((item) => item.id === body?.id)
        if (item) item.readAt = '2026-11-25T12:01:00'
      }
      if (url === '/admin/notification/read-all')
        items.forEach((item) => {
          item.readAt = '2026-11-25T12:01:00'
        })
      return response({})
    })
  })
  afterEach(() => {
    useNotificationsStore().stop()
    disposePinia(pinia)
    vi.unstubAllGlobals()
    setLocale('zh-CN')
    document.body.innerHTML = ''
  })

  it.each(['admin', 'super_admin'] as const)(
    'shows the order number, buyer and amount for %s',
    async (role) => {
      useUserStore().updateUser({ role })
      const { wrapper, router } = await mountAdmin()
      expect(wrapper.get('.notification-link .el-badge__content').text()).toBe('2')
      await openDropdown(wrapper)
      expect(router.currentRoute.value.name).toBe('admin_dashboard')
      expect(wrapper.findAll('.dropdown-notification-item')).toHaveLength(2)
      expect(wrapper.get('.dropdown-product').text()).toContain('ORDER-42')
      expect(wrapper.get('.dropdown-product').text()).toContain('买家 张三 (buyer_raw)')
      expect(wrapper.get('.dropdown-product').text()).toContain('¥168.80')
      expect(get).toHaveBeenCalledWith('/admin/notification', {
        params: { page: 1, pageSize: 8, unreadOnly: false },
      })
      expect(useNotificationsStore().unreadCount).toBe(9)
    },
  )

  it('marks a clicked notification read and opens the matching order drawer', async () => {
    const { wrapper, router } = await mountAdmin()
    await openDropdown(wrapper)
    await wrapper.get('.dropdown-product').trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/admin/notification/read', { id: 1 })
    expect(router.currentRoute.value.fullPath).toBe('/admin/orders?orderId=42')
    expect(get).toHaveBeenCalledWith('/admin/order/42')
    await vi.waitFor(() => expect(wrapper.get('.el-drawer').text()).toContain('ORDER-42'))
    expect(wrapper.get('.el-drawer').text()).toContain('¥168.80')
    expect(wrapper.get('.notification-link').attributes('aria-expanded')).toBe('false')
    await wrapper.get('.el-drawer__close-btn').trigger('click')
    await flushPromises()
    await vi.waitFor(() => expect(router.currentRoute.value.query.orderId).toBeUndefined())
    await openDropdown(wrapper)
    await wrapper.get('.dropdown-product').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.orderId).toBe('42')
    expect(get.mock.calls.filter(([url]) => url === '/admin/order/42')).toHaveLength(2)
  })

  it('filters and marks order notifications read without changing product counts', async () => {
    const { wrapper } = await mountAdmin()
    await openDropdown(wrapper)
    await wrapper.findAll('.dropdown-tabs [role="tab"]')[1]?.trigger('click')
    await flushPromises()
    await wrapper.get('.dropdown-mark-read').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.dropdown-notification-item')).toHaveLength(1)
    expect(wrapper.get('.notification-link .el-badge__content').text()).toBe('1')
    expect(useNotificationsStore().unreadCount).toBe(9)
    await wrapper.get('.dropdown-read-all').trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/admin/notification/read-all')
    expect(wrapper.text()).toContain('暂时没有未读通知')
    await wrapper.findAll('.dropdown-tabs [role="tab"]')[0]?.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.dropdown-notification-item')).toHaveLength(2)
    expect(wrapper.findAll('.dropdown-notification-item.unread')).toHaveLength(0)
    expect(useNotificationsStore().orderUnreadCount).toBe(0)
  })

  it('keeps failed reads visible and allows a notification list refresh', async () => {
    const { wrapper, router } = await mountAdmin()
    await openDropdown(wrapper)
    post.mockResolvedValueOnce({ data: { status: false, payload: { message: '标记已读失败' } } })
    await wrapper.get('.dropdown-product').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('admin_dashboard')
    expect(wrapper.get('.notification-link').attributes('aria-expanded')).toBe('true')
    items.unshift(notification(3, 44))
    await wrapper.get('.dropdown-refresh').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.dropdown-notification-item')).toHaveLength(3)
    expect(wrapper.get('.notification-link .el-badge__content').text()).toBe('3')
  })

  it('opens admin notification history and translates controls without translating buyer data', async () => {
    const { wrapper, router } = await mountAdmin()
    await openDropdown(wrapper)
    setLocale('en')
    await flushPromises()
    expect(wrapper.get('.dropdown-product').text()).toContain('New order')
    expect(wrapper.get('.dropdown-description').text()).toContain('Buyer: 买家 张三 (buyer_raw)')
    await wrapper.get('.dropdown-view-all').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('admin_notifications')
    expect(wrapper.findAll('.notification-item')).toHaveLength(2)
    expect(wrapper.get('.notification-item').text()).toContain('View order')
    expect(wrapper.get('.notification-item').text()).toContain('买家 张三 (buyer_raw)')
  })

  it('shows an appropriate empty state and a link to order management', async () => {
    items = []
    const { wrapper } = await mountAdmin('/admin/notifications')
    expect(wrapper.get('.admin-notifications').text()).toContain('暂时没有新订单通知')
    expect(wrapper.get('.browse-link').attributes('href')).toBe('/admin/orders')
    await openDropdown(wrapper)
    expect(wrapper.get('.dropdown-state').text()).toContain('暂时没有新订单通知')
    expect(wrapper.get('.dropdown-state a').attributes('href')).toBe('/admin/orders')
  })

  it('refreshes an open dropdown after a live order and lets the popup open that order', async () => {
    const { wrapper, router } = await mountAdmin()
    await openDropdown(wrapper)
    const created = notification(3, 44)
    items.unshift(created)
    TestSocket.instances[0]!.receive({ type: 'order_notification', notification: created })
    await flushPromises()
    expect(wrapper.findAll('.dropdown-notification-item')).toHaveLength(3)
    expect(wrapper.get('.notification-link .el-badge__content').text()).toBe('3')
    expect(notify).toHaveBeenCalledTimes(1)
    const popup = notify.mock.calls[0]![0]
    expect(popup.title).toBe('新订单')
    expect(popup.message).toContain('ORDER-44')
    expect(popup.message).toContain('买家 张三 (buyer_raw)')
    expect(popup.message).toContain('¥168.80')
    TestSocket.instances[0]!.receive({ type: 'order_notification', notification: created })
    await flushPromises()
    expect(notify).toHaveBeenCalledTimes(1)
    await popup.onClick?.()
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/admin/notification/read', { id: 3 })
    expect(router.currentRoute.value.query.orderId).toBe('44')
  })

  it('ignores an old detail response when selecting a different order', async () => {
    const original = get.getMockImplementation()!
    let complete!: (value: MockResponse) => void
    get.mockImplementation((url, options) =>
      url === '/admin/order/42'
        ? new Promise((resolve) => {
            complete = resolve
          })
        : original(url, options),
    )
    const { wrapper, router } = await mountAdmin('/admin/orders?orderId=42')
    await router.push('/admin/orders?orderId=43')
    await flushPromises()
    expect(wrapper.get('.el-drawer').text()).toContain('ORDER-43')
    complete(response({ order: order(42), items: [] }))
    await flushPromises()
    expect(wrapper.get('.el-drawer').text()).toContain('ORDER-43')
    expect(wrapper.get('.el-drawer').text()).not.toContain('ORDER-42')
  })

  it('shows an order detail error with a working retry', async () => {
    const original = get.getMockImplementation()!
    let fail = true
    get.mockImplementation(async (url, options) => {
      if (url === '/admin/order/42' && fail) {
        fail = false
        return { data: { status: false, payload: { message: '订单不存在' } } }
      }
      return original(url, options)
    })
    const { wrapper } = await mountAdmin('/admin/orders?orderId=42')
    expect(wrapper.get('.order-detail-error').text()).toContain('订单不存在')
    await wrapper.get('.order-detail-error button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.order-detail-error').exists()).toBe(false)
    expect(wrapper.get('.el-drawer').text()).toContain('ORDER-42')
  })

  it('discards pending order notification lists and clears the badge when logging out', async () => {
    const original = get.getMockImplementation()!
    let complete!: (value: MockResponse) => void
    get.mockImplementation((url, options) =>
      url === '/admin/notification'
        ? new Promise((resolve) => {
            complete = resolve
          })
        : original(url, options),
    )
    const { wrapper } = await mountAdmin()
    await openDropdown(wrapper)
    useUserStore().logout()
    complete(response({ notifications: items }))
    await flushPromises()
    expect(wrapper.get('.notification-link').attributes('aria-expanded')).toBe('false')
    expect(useNotificationsStore().orderUnreadCount).toBe(0)
    expect(wrapper.find('.dropdown-notification-item').exists()).toBe(false)
  })
})
