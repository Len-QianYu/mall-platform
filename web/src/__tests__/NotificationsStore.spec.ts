import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, setActivePinia, type Pinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { useUserStore } from '@/stores/user'
import { useNotificationsStore } from '@/stores/notifications'
import { notificationSocketUrl } from '@/utils/productNotifications'
import type { OrderNotification, ProductNotification } from '@/types/api'

type MockResponse = { data: { status: boolean; payload: object } }

const { get, post } = vi.hoisted(() => ({
  get: vi.fn<(url: string) => Promise<MockResponse>>(),
  post: vi.fn<(url: string, body?: { id: number }) => Promise<MockResponse>>(),
}))
vi.mock('@/utils/request', () => ({ default: { get, post } }))

class TestSocket {
  static OPEN = 1
  static instances: TestSocket[] = []
  readyState = 0
  onopen: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent<string>) => void) | null = null
  onerror: ((event: Event) => void) | null = null
  onclose: ((event: Event) => void) | null = null
  send = vi.fn<(data: string) => void>()
  constructor(public url: string) {
    TestSocket.instances.push(this)
  }
  open() {
    this.readyState = 1
    this.onopen?.(new Event('open'))
  }
  receive(message: object) {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(message) }))
  }
  close() {
    this.readyState = 3
    this.onclose?.(new Event('close'))
  }
}

const notification: ProductNotification = {
  id: 42,
  productId: 11,
  type: 'PRICE_DROP',
  productName: '测试商品',
  productImage: null,
  oldPrice: 100,
  newPrice: 80,
  readAt: null,
  createdAt: '2026-11-22T12:00:00',
}
const orderNotification: OrderNotification = {
  id: 42,
  type: 'NEW_ORDER',
  orderId: 99,
  orderNo: 'ORDER-99',
  buyerId: 8,
  buyerUsername: 'buyer',
  buyerName: '买家名字',
  totalAmount: 168.8,
  readAt: null,
  createdAt: '2026-11-25T12:00:00',
}
const response = (payload: object) => ({ data: { status: true, payload } })
let pinia: Pinia
let serverCount: number
let ticketCount: number

const start = async () => {
  const store = useNotificationsStore()
  store.start()
  await flushPromises()
  const socket = TestSocket.instances[0]!
  socket.open()
  await flushPromises()
  return { store, socket }
}

describe('live product notifications', () => {
  beforeEach(() => {
    localStorage.clear()
    pinia = createPinia()
    setActivePinia(pinia)
    vi.useFakeTimers({
      toFake: ['Date', 'setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'],
    })
    vi.stubGlobal('WebSocket', TestSocket)
    vi.stubEnv('APP_BASE_URL', 'http://localhost:8080')
    TestSocket.instances = []
    get.mockReset()
    post.mockReset()
    serverCount = 1
    ticketCount = 0
    get.mockImplementation(async () => response({ unreadCount: serverCount }))
    post.mockImplementation(async (url: string) =>
      response(
        url === '/notification/socket-ticket' ? { ticket: `short-ticket-${++ticketCount}` } : {},
      ),
    )
    useUserStore().login({ token: 'private-jwt', user: { id: 7, username: 'test', role: 'user' } })
  })
  afterEach(() => {
    useNotificationsStore().stop()
    disposePinia(pinia)
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
  })

  it('authenticates with a temporary ticket and never puts the JWT in the socket URL', async () => {
    const { store, socket } = await start()
    expect(post).toHaveBeenCalledWith('/notification/socket-ticket')
    expect(socket.url).toBe('ws://localhost:8080/ws/notifications?ticket=short-ticket-1')
    expect(socket.url).not.toContain('private-jwt')
    expect(store.connected).toBe(true)
    expect(store.unreadCount).toBe(1)
  })

  it('receives live price changes and ignores duplicate notification popups', async () => {
    const { store, socket } = await start()
    serverCount = 2
    socket.receive({ type: 'notification', notification })
    await flushPromises()
    expect(store.latestNotification?.id).toBe(42)
    expect(store.unreadCount).toBe(2)
    const received = store.latestNotification
    socket.receive({
      type: 'notification',
      notification: { ...notification, productName: 'duplicate' },
    })
    await flushPromises()
    expect(store.latestNotification).toBe(received)
  })

  it('reconnects with a fresh ticket and restores the unread count after a dropped connection', async () => {
    const { store, socket } = await start()
    socket.close()
    expect(store.connected).toBe(false)
    serverCount = 3
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    const replacement = TestSocket.instances[1]!
    expect(replacement.url).toContain('short-ticket-2')
    replacement.open()
    await flushPromises()
    expect(store.connected).toBe(true)
    expect(store.unreadCount).toBe(3)
  })

  it('retries a failed ticket request without opening an unauthenticated socket', async () => {
    post.mockRejectedValueOnce(new Error('offline'))
    const store = useNotificationsStore()
    store.start()
    await flushPromises()
    expect(TestSocket.instances).toHaveLength(0)
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(TestSocket.instances).toHaveLength(1)
    store.stop()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('closes the previous account socket and discards late messages when stopping', async () => {
    const { store, socket } = await start()
    socket.close()
    useUserStore().logout()
    store.stop()
    socket.receive({ type: 'notification', notification })
    await vi.advanceTimersByTimeAsync(30000)
    expect(TestSocket.instances).toHaveLength(1)
    expect(store.latestNotification).toBeNull()
    expect(store.unreadCount).toBe(0)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('does not let an older unread response overwrite a newer count', async () => {
    let complete!: (value: ReturnType<typeof response>) => void
    get.mockReturnValueOnce(
      new Promise((resolve) => {
        complete = resolve
      }),
    )
    serverCount = 3
    const { store } = await start()
    expect(store.unreadCount).toBe(3)
    complete(response({ unreadCount: 1 }))
    await flushPromises()
    expect(store.unreadCount).toBe(3)
  })

  it('keeps a newly arrived unread notification when marking earlier notifications read', async () => {
    const { store } = await start()
    serverCount = 1
    await store.markAllRead()
    expect(post).toHaveBeenCalledWith('/notification/read-all')
    expect(store.unreadCount).toBe(1)
    await store.markRead(42)
    expect(post).toHaveBeenCalledWith('/notification/read', { id: 42 })
  })

  it('uses secure WebSocket transport and preserves a configured API path', () => {
    vi.stubEnv('APP_BASE_URL', 'https://mall.example/api')
    expect(notificationSocketUrl('temporary-ticket')).toBe(
      'wss://mall.example/api/ws/notifications?ticket=temporary-ticket',
    )
  })

  it.each(['admin', 'super_admin'] as const)(
    'keeps order notifications and product notifications independent for %s',
    async (role) => {
      useUserStore().updateUser({ role })
      get.mockImplementation(async (url) =>
        response({ unreadCount: url === '/admin/notification/unread-count' ? 5 : 1 }),
      )
      const { store, socket } = await start()
      socket.receive({ type: 'notification', notification })
      socket.receive({ type: 'order_notification', notification: orderNotification })
      await flushPromises()
      expect(store.latestNotification?.id).toBe(42)
      expect(store.latestOrderNotification?.orderNo).toBe('ORDER-99')
      expect(store.unreadCount).toBe(1)
      expect(store.orderUnreadCount).toBe(5)
      const received = store.latestOrderNotification
      socket.receive({
        type: 'order_notification',
        notification: { ...orderNotification, orderNo: 'duplicate' },
      })
      await flushPromises()
      expect(store.latestOrderNotification).toBe(received)
      await store.markRead(42, 'order')
      await store.markAllRead('order')
      expect(post).toHaveBeenCalledWith('/admin/notification/read', { id: 42 })
      expect(post).toHaveBeenCalledWith('/admin/notification/read-all')
      expect(store.unreadCount).toBe(1)
    },
  )

  it('ignores order messages and does not request admin counts for ordinary buyers', async () => {
    const { store, socket } = await start()
    socket.receive({ type: 'order_notification', notification: orderNotification })
    socket.receive({ type: 'order_refresh' })
    await flushPromises()
    expect(store.latestOrderNotification).toBeNull()
    expect(store.orderUnreadCount).toBe(0)
    expect(get).not.toHaveBeenCalledWith('/admin/notification/unread-count')
  })

  it('restores offline order counts on reconnect and clears them on logout', async () => {
    useUserStore().updateUser({ role: 'admin' })
    const { store, socket } = await start()
    socket.close()
    serverCount = 3
    await vi.advanceTimersByTimeAsync(1000)
    const replacement = TestSocket.instances[1]!
    replacement.open()
    replacement.receive({ type: 'order_notification', notification: orderNotification })
    await flushPromises()
    expect(store.orderUnreadCount).toBe(3)
    useUserStore().logout()
    store.stop()
    replacement.receive({ type: 'order_notification', notification: orderNotification })
    expect(store.orderUnreadCount).toBe(0)
    expect(store.latestOrderNotification).toBeNull()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('does not let a delayed order count overwrite the latest count', async () => {
    useUserStore().updateUser({ role: 'super_admin' })
    let complete!: (value: ReturnType<typeof response>) => void
    let delayed = false
    get.mockImplementation(async (url) => {
      if (url === '/admin/notification/unread-count' && !delayed) {
        delayed = true
        return new Promise((resolve) => {
          complete = resolve
        })
      }
      return response({ unreadCount: 4 })
    })
    const { store } = await start()
    expect(store.orderUnreadCount).toBe(4)
    complete(response({ unreadCount: 1 }))
    await flushPromises()
    expect(store.orderUnreadCount).toBe(4)
  })

  it('discards a previous account read confirmation after switching accounts', async () => {
    useUserStore().updateUser({ role: 'admin' })
    const { store } = await start()
    let complete!: (value: ReturnType<typeof response>) => void
    post.mockReturnValueOnce(
      new Promise((resolve) => {
        complete = resolve
      }),
    )
    const pending = store.markRead(42, 'order')
    useUserStore().login({
      token: 'other-token',
      user: { id: 8, username: 'other', role: 'super_admin' },
    })
    store.start()
    await flushPromises()
    const countRequests = get.mock.calls.length
    const revision = store.orderRevision
    complete(response({}))
    await pending
    expect(get.mock.calls).toHaveLength(countRequests)
    expect(store.orderRevision).toBe(revision)
  })
})
