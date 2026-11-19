import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import type {
  ApiResult,
  Order,
  Payment,
  PaymentMethodsResult,
  PaymentQueryResult,
  PaymentStartResult,
} from '@/types/api'
import PaymentView from '@/views/shop/PaymentView.vue'

const { getPayment, postPayment, messageSuccess, messageError, messageWarning } = vi.hoisted(
  () => ({
    getPayment:
      vi.fn<
        (
          url: string,
          config?: { params: { orderId: string | number } },
        ) => Promise<{ data: ApiResult<PaymentMethodsResult | PaymentQueryResult> }>
      >(),
    postPayment:
      vi.fn<(url: string, fields: object) => Promise<{ data: ApiResult<PaymentStartResult> }>>(),
    messageSuccess: vi.fn(),
    messageError: vi.fn(),
    messageWarning: vi.fn(),
  }),
)

vi.mock('@/utils/request', () => ({ default: { get: getPayment, post: postPayment } }))
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: { success: messageSuccess, error: messageError, warning: messageWarning },
  }
})

enableAutoUnmount(afterEach)

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
  createdAt: '2026-11-19T10:00:00',
  updatedAt: '2026-11-19T10:00:00',
}

const payment: Payment = {
  id: 99,
  paymentNo: 'TEST-PAYMENT-99',
  tradeNo: null,
  orderId: 42,
  userId: 7,
  amount: 33.6,
  method: 'ALIPAY',
  status: 'PENDING',
  paidAt: null,
  createdAt: '2026-11-19T10:00:00',
  updatedAt: '2026-11-19T10:00:00',
}
const payUrl = 'https://openapi-sandbox.dl.alipaydev.com/gateway.do?test=payment'
const response = <T>(payload: T) => ({ data: { status: true as const, payload } })
let queryResult: PaymentQueryResult
let methods: PaymentMethodsResult

const defaultGet = async (url: string) => {
  if (url === '/payment/methods') return response(methods)
  if (url === '/payment/query') return response(queryResult)
  throw new Error(`Unexpected payment request: ${url}`)
}

const mountPayment = async (path = '/orders/42/pay') => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/orders/:id/pay', name: 'order_pay', component: PaymentView },
      { path: '/payment/return', name: 'order_pay_return', component: PaymentView },
      { path: '/orders/:id', name: 'order_detail', component: { template: '<p>order</p>' } },
      { path: '/orders', name: 'orders', component: { template: '<p>orders</p>' } },
    ],
  })
  await router.push(path)
  const wrapper = mount(PaymentView, {
    global: {
      plugins: [router],
      stubs: { ElCard: { template: '<div><slot name="header" /><slot /></div>' } },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('payment checkout', () => {
  beforeEach(() => {
    getPayment.mockReset()
    postPayment.mockReset()
    messageSuccess.mockReset()
    messageError.mockReset()
    messageWarning.mockReset()
    queryResult = { order: { ...order }, payment: null, paid: false }
    methods = { methods: ['ALIPAY', 'MOCK'], alipaySandbox: true }
    getPayment.mockImplementation(defaultGet)
    postPayment.mockResolvedValue(response({ payment: { ...payment }, payUrl }))
    vi.spyOn(window, 'open').mockReturnValue(null)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('uses enabled Alipay checkout without reporting payment success before confirmation', async () => {
    const { wrapper, router } = await mountPayment()
    expect(wrapper.text()).toContain('支付宝（沙箱）')
    expect(wrapper.text()).toContain('沙箱买家账号')
    await wrapper.get('.confirm-payment').trigger('click')
    await flushPromises()

    expect(postPayment).toHaveBeenCalledWith('/payment/pay', { orderId: 42, method: 'ALIPAY' })
    expect(window.open).toHaveBeenCalledWith(payUrl, '_self')
    expect(messageSuccess).not.toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('order_pay')
    expect(wrapper.find('.query-payment').exists()).toBe(true)
  })

  it('queries and navigates to the order only after the server confirms payment', async () => {
    const { wrapper, router } = await mountPayment()
    await wrapper.get('.confirm-payment').trigger('click')
    await flushPromises()
    queryResult = {
      order: { ...order, status: 'PAID' },
      payment: { ...payment, status: 'SUCCESS', tradeNo: 'ALIPAY-TRADE-99' },
      paid: true,
    }
    await wrapper.get('.query-payment').trigger('click')
    await flushPromises()

    expect(getPayment).toHaveBeenLastCalledWith('/payment/query', { params: { orderId: 42 } })
    expect(messageSuccess).toHaveBeenCalledWith('支付成功')
    expect(router.currentRoute.value.name).toBe('order_detail')
  })

  it('uses active server configuration to keep mock payment available when Alipay is disabled', async () => {
    methods = { methods: ['MOCK'], alipaySandbox: false }
    postPayment.mockResolvedValue(
      response<PaymentStartResult>({
        payment: { ...payment, method: 'MOCK', status: 'SUCCESS' },
        payUrl: null,
      }),
    )
    const { wrapper, router } = await mountPayment()
    expect(wrapper.get('input[value="ALIPAY"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('input[value="WECHAT"]').attributes('disabled')).toBeDefined()
    await wrapper.get('.confirm-payment').trigger('click')
    await flushPromises()

    expect(postPayment).toHaveBeenCalledWith('/payment/pay', { orderId: 42, method: 'MOCK' })
    expect(router.currentRoute.value.name).toBe('order_detail')
    expect(window.open).not.toHaveBeenCalled()
  })

  it('restores a pending Alipay payment and prevents switching it to mock payment', async () => {
    queryResult.payment = { ...payment }
    const { wrapper } = await mountPayment()
    expect(wrapper.find('.query-payment').exists()).toBe(true)
    expect(wrapper.get('input[value="MOCK"]').attributes('disabled')).toBeDefined()
    await wrapper.get('.query-payment').trigger('click')
    await flushPromises()
    expect(messageWarning).toHaveBeenCalled()
    expect(messageSuccess).not.toHaveBeenCalled()
  })

  it('never trusts a successful trade status in the browser return URL', async () => {
    queryResult.payment = { ...payment }
    const { router } = await mountPayment('/payment/return?orderId=42&trade_status=TRADE_SUCCESS')
    expect(getPayment).toHaveBeenCalledWith('/payment/query', { params: { orderId: '42' } })
    expect(router.currentRoute.value.name).toBe('order_pay_return')
    expect(messageSuccess).not.toHaveBeenCalled()
    expect(postPayment).not.toHaveBeenCalled()
  })

  it('automatically queries a genuine return and opens the confirmed paid order', async () => {
    queryResult = { order: { ...order, status: 'PAID' }, payment: null, paid: true }
    const { router } = await mountPayment('/payment/return?orderId=42')
    expect(router.currentRoute.value.name).toBe('order_detail')
    expect(messageSuccess).toHaveBeenCalledWith('支付成功')
    expect(postPayment).not.toHaveBeenCalled()
  })

  it('keeps failed creation on the checkout page and allows retrying', async () => {
    postPayment.mockResolvedValue({
      data: { status: false, payload: { message: '订单支付已超时' } },
    })
    const { wrapper, router } = await mountPayment()
    await wrapper.get('.confirm-payment').trigger('click')
    await flushPromises()
    expect(messageError).toHaveBeenCalledWith('订单支付已超时')
    expect(router.currentRoute.value.name).toBe('order_pay')
    expect(window.open).not.toHaveBeenCalled()
    expect(wrapper.get('.confirm-payment').attributes('disabled')).toBeUndefined()
  })

  it('prevents duplicate submissions while checkout creation is pending', async () => {
    let finish: (result: { data: ApiResult<PaymentStartResult> }) => void = () => undefined
    postPayment.mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const { wrapper } = await mountPayment()
    await wrapper.get('.confirm-payment').trigger('click')
    await wrapper.get('.confirm-payment').trigger('click')
    expect(postPayment).toHaveBeenCalledTimes(1)
    finish(response({ payment: { ...payment }, payUrl }))
    await flushPromises()
    expect(window.open).toHaveBeenCalledTimes(1)
  })

  it('shows a retryable load error and never initiates payment automatically', async () => {
    getPayment.mockRejectedValue(new Error('offline'))
    const { wrapper } = await mountPayment()
    expect(wrapper.text()).toContain('加载订单或支付方式失败')
    expect(postPayment).not.toHaveBeenCalled()
    getPayment.mockImplementation(defaultGet)
    await wrapper.get('.load-retry').trigger('click')
    await flushPromises()
    expect(wrapper.find('.confirm-payment').exists()).toBe(true)
  })

  it('does not offer payment on a cancelled order', async () => {
    queryResult.order.status = 'CANCELLED'
    const { wrapper } = await mountPayment()
    expect(wrapper.text()).toContain('已取消')
    expect(wrapper.find('.confirm-payment').exists()).toBe(false)
    expect(postPayment).not.toHaveBeenCalled()
  })
})
