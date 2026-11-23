import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { ApiResult, CartItem, Order, Product, SeckillActivity, User } from '@/types/api'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import CheckoutView from '@/views/shop/CheckoutView.vue'

type CheckoutPayload =
  { user: User } | { product: Product } | { items: CartItem[] } | { activity: SeckillActivity }

const { getCheckout, postCheckout } = vi.hoisted(() => ({
  getCheckout: vi.fn<(url: string) => Promise<{ data: ApiResult<CheckoutPayload> }>>(),
  postCheckout:
    vi.fn<
      (
        url: string,
        fields: Record<string, unknown>,
      ) => Promise<{ data: ApiResult<{ order: Order }> }>
    >(),
}))
vi.mock('@/utils/request', () => ({ default: { get: getCheckout, post: postCheckout } }))

enableAutoUnmount(afterEach)

const user: User = {
  id: 7,
  username: 'checkout-user',
  name: '张三',
  gender: 'M',
  role: 'user',
  email: null,
  phone: '13900139000',
  province: '浙江省',
  city: '杭州市',
  receiverAddress: '浙江省杭州市西湖区文三路1号',
  avatarUrl: null,
  status: 1,
  registeredAt: '2026-11-19T09:00:00',
  updatedAt: '2026-11-19T09:00:00',
}

const product: Product = {
  id: 11,
  name: '测试商品',
  subtitle: null,
  description: null,
  category: '测试分类',
  price: 16.8,
  stock: 20,
  sales: 0,
  mainImage: null,
  status: 1,
  createdAt: '2026-11-19T09:00:00',
  updatedAt: '2026-11-19T09:00:00',
}

const selectedItem: CartItem = {
  id: 1,
  productId: 11,
  quantity: 2,
  checked: 1,
  productName: product.name,
  productImage: null,
  price: product.price,
  stock: product.stock,
  productStatus: 1,
}
const cartItems: CartItem[] = [
  selectedItem,
  { ...selectedItem, id: 2, productId: 12, checked: 0 },
  { ...selectedItem, id: 3, productId: 13, stock: 1 },
]

const order: Order = {
  id: 42,
  orderNo: 'TEST-ORDER-42',
  userId: 7,
  totalAmount: 33.6,
  status: 'PENDING_PAYMENT',
  receiverName: '张三',
  receiverPhone: '13900139000',
  receiverAddress: '浙江省杭州市西湖区文三路1号',
  remark: null,
  paidAt: null,
  shippedAt: null,
  completedAt: null,
  cancelledAt: null,
  createdAt: '2026-11-19T09:00:00',
  updatedAt: '2026-11-19T09:00:00',
}

const response = <T>(payload: T) => ({ data: { status: true as const, payload } })
const defaultGet = async (url: string) => {
  if (url === '/user/profile') return response({ user })
  if (url === '/product/11') return response({ product })
  if (url === '/cart') return response({ items: cartItems })
  throw new Error(`Unexpected request: ${url}`)
}

const mountCheckout = async (
  query: Record<string, string> = { productId: '11', quantity: '2' },
) => {
  const pinia = createPinia()
  const store = useUserStore(pinia)
  store.login({
    token: 'checkout-test-token',
    user: { ...user, name: '旧收货人', phone: '13800138000', receiverAddress: '旧收货地址' },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/checkout', name: 'checkout', component: CheckoutView },
      { path: '/profile', name: 'profile', component: { template: '<p>profile</p>' } },
      { path: '/cart', name: 'cart', component: { template: '<p>cart</p>' } },
      { path: '/seckill', name: 'seckill', component: { template: '<p>seckill</p>' } },
      { path: '/orders/:id/pay', name: 'order_pay', component: { template: '<p>payment</p>' } },
    ],
  })
  await router.push({ name: 'checkout', query })
  const wrapper = mount(CheckoutView, {
    global: {
      plugins: [pinia, router],
      stubs: {
        ElCard: { template: '<div><slot name="header" /><slot /></div>' },
        ElTable: true,
        ElTableColumn: true,
        ElImage: true,
      },
    },
  })
  await flushPromises()
  return { wrapper, store, router, cart: useCartStore(pinia) }
}

describe('checkout shipping information', () => {
  const seckillActivity: SeckillActivity = {
    id: 3,
    productId: 11,
    title: '限时秒杀',
    productName: product.name,
    productSubtitle: null,
    productImage: null,
    productStatus: 1,
    originalPrice: product.price,
    seckillPrice: 4.5,
    totalStock: 10,
    stock: 5,
    returnedStock: 0,
    limitPerUser: 2,
    status: 1,
    phase: 'LIVE',
    startsAt: '2026-11-22T11:00:00',
    endsAt: '2026-11-22T13:00:00',
    startsAtEpochMs: 1795316400000,
    endsAtEpochMs: 1795323600000,
    serverTime: 1795320000000,
  }
  beforeEach(() => {
    localStorage.removeItem('user')
    sessionStorage.clear()
    vi.stubEnv('APP_BASE_URL', 'http://localhost:8080')
    getCheckout.mockReset()
    postCheckout.mockReset()
    getCheckout.mockImplementation(defaultGet)
    postCheckout.mockResolvedValue(response({ order }))
  })

  afterEach(() => {
    localStorage.removeItem('user')
    sessionStorage.clear()
    vi.unstubAllEnvs()
  })

  it('displays the latest saved address as text and updates the stale login cache', async () => {
    const { wrapper, store } = await mountCheckout()

    expect(getCheckout).toHaveBeenCalledWith('/user/profile')
    expect(wrapper.get('.receiver-contact').text()).toContain('张三')
    expect(wrapper.get('.receiver-contact').text()).toContain('13900139000')
    expect(wrapper.get('.receiver-address').text()).toBe(user.receiverAddress)
    expect(wrapper.findAll('input')).toHaveLength(1)
    expect(wrapper.get('.remark-form input').attributes('placeholder')).toBe('选填，给商家的留言')
    expect(wrapper.get('.submit-button').attributes('disabled')).toBeUndefined()
    expect(store.user?.user.receiverAddress).toBe(user.receiverAddress)
  })

  it('submits a buy-now order with the displayed address and optional remark', async () => {
    const { wrapper, router } = await mountCheckout()
    await wrapper.get('.remark-form input').setValue(' 请下午送达 ')
    await wrapper.get('.submit-button').trigger('click')
    await flushPromises()

    expect(postCheckout).toHaveBeenCalledWith('/order/create', {
      requestId: expect.stringMatching(/^[A-Za-z0-9_-]{16,64}$/),
      items: [{ productId: 11, quantity: 2 }],
      fromCart: false,
      receiverName: '张三',
      receiverPhone: '13900139000',
      receiverAddress: '浙江省杭州市西湖区文三路1号',
      remark: '请下午送达',
    })
    expect(router.currentRoute.value.name).toBe('order_pay')
    expect(router.currentRoute.value.params.id).toBe('42')
    expect(getCheckout).not.toHaveBeenCalledWith('/cart')
  })

  it('uses the saved address when checking out selected available cart items', async () => {
    const { wrapper, cart } = await mountCheckout({})
    await wrapper.get('.submit-button').trigger('click')
    await flushPromises()

    expect(postCheckout).toHaveBeenCalledWith('/order/create', {
      requestId: expect.stringMatching(/^[A-Za-z0-9_-]{16,64}$/),
      items: [{ productId: 11, quantity: 2 }],
      fromCart: true,
      receiverName: '张三',
      receiverPhone: '13900139000',
      receiverAddress: '浙江省杭州市西湖区文三路1号',
      remark: null,
    })
    expect(getCheckout.mock.calls.filter(([url]) => url === '/cart')).toHaveLength(2)
    expect(cart.count).toBe(cartItems.length)
  })

  it('reuses the request ID after a lost response and ignores clicks while submitting', async () => {
    let failRequest: (error: Error) => void = () => undefined
    postCheckout.mockImplementationOnce(
      () =>
        new Promise((_, reject) => {
          failRequest = reject
        }),
    )
    const { wrapper, router } = await mountCheckout()
    const button = wrapper.get('.submit-button')
    await button.trigger('click')
    await button.trigger('click')
    expect(postCheckout).toHaveBeenCalledTimes(1)
    const requestId = postCheckout.mock.calls[0]?.[1].requestId

    failRequest(new Error('response lost'))
    await flushPromises()
    await button.trigger('click')
    await flushPromises()

    expect(postCheckout).toHaveBeenCalledTimes(2)
    expect(postCheckout.mock.calls[1]?.[1].requestId).toBe(requestId)
    expect(router.currentRoute.value.name).toBe('order_pay')
  })

  it('restores an unresolved request after reloading the same checkout', async () => {
    postCheckout.mockRejectedValueOnce(new Error('response lost'))
    const first = await mountCheckout()
    await first.wrapper.get('.submit-button').trigger('click')
    await flushPromises()
    const requestId = postCheckout.mock.calls[0]?.[1].requestId
    first.wrapper.unmount()

    const second = await mountCheckout()
    await second.wrapper.get('.submit-button').trigger('click')
    await flushPromises()

    expect(postCheckout.mock.calls[1]?.[1].requestId).toBe(requestId)
  })

  it('uses a new request ID when order content changes after a failed attempt', async () => {
    postCheckout.mockRejectedValueOnce(new Error('offline'))
    const { wrapper } = await mountCheckout()
    await wrapper.get('.submit-button').trigger('click')
    await flushPromises()
    const requestId = postCheckout.mock.calls[0]?.[1].requestId

    await wrapper.get('.remark-form input').setValue('新的送货备注')
    await wrapper.get('.submit-button').trigger('click')
    await flushPromises()

    expect(postCheckout.mock.calls[1]?.[1].requestId).not.toBe(requestId)
  })

  it('uses a new request ID for another purchase after the previous order succeeded', async () => {
    const first = await mountCheckout()
    await first.wrapper.get('.submit-button').trigger('click')
    await flushPromises()
    const requestId = postCheckout.mock.calls[0]?.[1].requestId
    first.wrapper.unmount()

    const second = await mountCheckout()
    await second.wrapper.get('.submit-button').trigger('click')
    await flushPromises()

    expect(postCheckout.mock.calls[1]?.[1].requestId).not.toBe(requestId)
  })

  it.each([
    { field: 'recipient', missing: { name: null } },
    { field: 'phone', missing: { phone: null } },
    { field: 'valid phone', missing: { phone: '12345' } },
    { field: 'address', missing: { receiverAddress: null } },
    { field: 'nonblank address', missing: { receiverAddress: '   ' } },
  ])('requires a saved $field before submitting an order', async ({ missing }) => {
    getCheckout.mockImplementation((url) =>
      url === '/user/profile'
        ? Promise.resolve(response({ user: { ...user, ...missing } }))
        : defaultGet(url),
    )
    const { wrapper } = await mountCheckout()

    expect(wrapper.get('.address-hint').text()).toContain('个人中心')
    expect(wrapper.get('.submit-button').attributes('disabled')).toBeDefined()
    await wrapper.get('.submit-button').trigger('click')
    await flushPromises()
    expect(postCheckout).not.toHaveBeenCalled()
  })

  it('keeps the original product and quantity when opening personal information to edit the address', async () => {
    const { wrapper, router } = await mountCheckout()
    const checkoutPath = router.currentRoute.value.fullPath
    await wrapper.get('.address-link').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('profile')
    expect(router.currentRoute.value.query.redirect).toBe(checkoutPath)
  })

  it('waits for the saved address to load before enabling submission', async () => {
    let completeProfile: (result: { data: ApiResult<{ user: User }> }) => void = () => undefined
    getCheckout.mockImplementation((url) =>
      url === '/user/profile'
        ? new Promise<{ data: ApiResult<{ user: User }> }>((resolve) => {
            completeProfile = resolve
          })
        : defaultGet(url),
    )
    const { wrapper } = await mountCheckout()

    expect(wrapper.get('.submit-button').attributes('disabled')).toBeDefined()
    await wrapper.get('.submit-button').trigger('click')
    await flushPromises()
    expect(postCheckout).not.toHaveBeenCalled()

    completeProfile(response({ user }))
    await flushPromises()
    expect(wrapper.get('.receiver-address').text()).toBe(user.receiverAddress)
    expect(wrapper.get('.submit-button').attributes('disabled')).toBeUndefined()
  })

  it('allows retrying a failed address load and does not submit the cached old address', async () => {
    let profileAttempts = 0
    getCheckout.mockImplementation(async (url) => {
      if (url === '/user/profile' && profileAttempts++ === 0) throw new Error('offline')
      return defaultGet(url)
    })
    const { wrapper } = await mountCheckout()

    expect(wrapper.get('.address-error').text()).toContain('加载收货地址失败')
    expect(wrapper.find('.saved-address').exists()).toBe(false)
    expect(wrapper.get('.submit-button').attributes('disabled')).toBeDefined()
    await wrapper.get('.submit-button').trigger('click')
    await flushPromises()
    expect(postCheckout).not.toHaveBeenCalled()

    await wrapper.get('.address-error button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.address-error').exists()).toBe(false)
    expect(wrapper.get('.receiver-address').text()).toBe(user.receiverAddress)
    expect(wrapper.get('.submit-button').attributes('disabled')).toBeUndefined()
  })

  it('previews the campaign price and submits the activity ID through the existing order endpoint', async () => {
    getCheckout.mockImplementation((url) =>
      url === '/seckill/3'
        ? Promise.resolve(response({ activity: seckillActivity }))
        : defaultGet(url),
    )
    const { wrapper } = await mountCheckout({ seckillActivityId: '3', quantity: '2' })
    expect(wrapper.get('.seckill-checkout-hint').text()).toContain('每人限购 2 件')
    expect(wrapper.get('.total-amount').text()).toBe('¥9.00')
    await wrapper.get('.submit-button').trigger('click')
    await flushPromises()
    expect(postCheckout).toHaveBeenCalledWith(
      '/order/create',
      expect.objectContaining({
        seckillActivityId: 3,
        fromCart: false,
        items: [{ productId: 11, quantity: 2 }],
      }),
    )
    expect(getCheckout).not.toHaveBeenCalledWith('/product/11')
    expect(getCheckout).not.toHaveBeenCalledWith('/cart')
  })

  it('can confirm an unresolved campaign order after reloading even when the quota is sold out', async () => {
    let currentActivity = seckillActivity
    getCheckout.mockImplementation((url) =>
      url === '/seckill/3'
        ? Promise.resolve(response({ activity: currentActivity }))
        : defaultGet(url),
    )
    postCheckout.mockRejectedValueOnce(new Error('response lost'))
    const first = await mountCheckout({ seckillActivityId: '3', quantity: '1' })
    await first.wrapper.get('.submit-button').trigger('click')
    await flushPromises()
    const requestId = postCheckout.mock.calls[0]?.[1].requestId
    first.wrapper.unmount()
    currentActivity = { ...seckillActivity, stock: 0, phase: 'SOLD_OUT' }
    const second = await mountCheckout({ seckillActivityId: '3', quantity: '1' })
    await second.wrapper.get('.submit-button').trigger('click')
    await flushPromises()
    expect(postCheckout.mock.calls[1]?.[1].requestId).toBe(requestId)
    expect(second.router.currentRoute.value.name).toBe('order_pay')
  })

  it('rejects quantities above the campaign limit before allowing checkout', async () => {
    getCheckout.mockImplementation((url) =>
      url === '/seckill/3'
        ? Promise.resolve(response({ activity: seckillActivity }))
        : defaultGet(url),
    )
    const { wrapper } = await mountCheckout({ seckillActivityId: '3', quantity: '3' })
    expect(wrapper.find('.submit-bar').exists()).toBe(false)
    expect(postCheckout).not.toHaveBeenCalled()
  })
})
