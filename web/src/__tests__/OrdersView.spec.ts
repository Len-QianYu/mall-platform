import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ElPopconfirm } from 'element-plus'
import type { ApiResult, OrderStatus, OrderWithItems, Pagination, Product } from '@/types/api'
import OrdersView from '@/views/shop/OrdersView.vue'
import { setLocale } from '@/i18n'

type GetResponse = {
  data: ApiResult<{ orders: OrderWithItems[]; pagination: Pagination } | { product: Product }>
}

const { getOrders, postOrder, messageSuccess, messageError, messageWarning } = vi.hoisted(() => ({
  getOrders:
    vi.fn<
      (
        url: string,
        config?: { params: { page: number; pageSize: number; status?: OrderStatus } },
      ) => Promise<GetResponse>
    >(),
  postOrder: vi.fn<(url: string, fields: object) => Promise<{ data: ApiResult }>>(),
  messageSuccess: vi.fn(),
  messageError: vi.fn(),
  messageWarning: vi.fn(),
}))

vi.mock('@/utils/request', () => ({ default: { get: getOrders, post: postOrder } }))
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: { success: messageSuccess, error: messageError, warning: messageWarning },
  }
})

enableAutoUnmount(afterEach)

const product: Product = {
  id: 11,
  name: '现在的商品名称',
  subtitle: null,
  description: null,
  category: 'AI 伙伴',
  price: 20,
  stock: 5,
  sales: 10,
  mainImage: null,
  status: 1,
  createdAt: '2026-11-19T10:00:00',
  updatedAt: '2026-11-27T10:00:00',
}

const order: OrderWithItems = {
  id: 42,
  orderNo: 'TEST-ORDER-42',
  userId: 7,
  totalAmount: 33.6,
  status: 'SHIPPED',
  receiverName: '张三',
  receiverPhone: '13900139000',
  receiverAddress: '浙江省杭州市西湖区文三路1号',
  remark: null,
  paidAt: '2026-11-19T10:01:00',
  shippedAt: '2026-11-20T10:00:00',
  completedAt: null,
  cancelledAt: null,
  createdAt: '2026-11-19T10:00:00',
  updatedAt: '2026-11-20T10:00:00',
  items: [
    {
      id: 101,
      orderId: 42,
      productId: 11,
      productName: '购买时的商品名称',
      productImage: '/products/previous.png',
      unitPrice: 16.8,
      quantity: 2,
      createdAt: '2026-11-19T10:00:00',
    },
  ],
}

const response = <T>(payload: T) => ({ data: { status: true as const, payload } })
let orders: OrderWithItems[]

const mountOrders = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/orders', name: 'orders', component: OrdersView },
      { path: '/orders/:id', name: 'order_detail', component: { template: '<p>order</p>' } },
      { path: '/orders/:id/pay', name: 'order_pay', component: { template: '<p>pay</p>' } },
      { path: '/products/:id', name: 'product_detail', component: { template: '<p>product</p>' } },
      { path: '/products', name: 'products', component: { template: '<p>products</p>' } },
    ],
  })
  await router.push('/orders')
  const wrapper = mount(OrdersView, {
    global: {
      plugins: [router],
      stubs: {
        ElCard: { template: '<div><slot name="header" /><slot /></div>' },
        ElImage: true,
        ElPopconfirm: {
          props: ['title'],
          emits: ['confirm'],
          template: '<div><slot name="reference" /></div>',
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('my orders product navigation', () => {
  beforeEach(() => {
    setLocale('zh-CN')
    orders = [{ ...order, items: order.items.map((item) => ({ ...item })) }]
    getOrders.mockReset()
    postOrder.mockReset()
    messageSuccess.mockReset()
    messageError.mockReset()
    messageWarning.mockReset()
    getOrders.mockImplementation(async (url) => {
      if (url === '/order') {
        return response({
          orders,
          pagination: { total: orders.length, pageSize: 10, pageCount: 1, currentPage: 1 },
        })
      }
      if (url.startsWith('/product/')) {
        return response({ product: { ...product, id: Number(url.slice('/product/'.length)) } })
      }
      throw new Error('Unexpected request: ' + url)
    })
    postOrder.mockResolvedValue(response({}))
  })

  afterEach(() => {
    setLocale('zh-CN')
  })

  it.each(['.order-item', '.item-name', '.product-image'])(
    'checks availability and opens the purchased product when clicking %s',
    async (selector) => {
      const { wrapper, router } = await mountOrders()
      expect(wrapper.get('.item-name').text()).toBe('购买时的商品名称')
      await wrapper.get(selector).trigger('click')
      await flushPromises()

      expect(getOrders).toHaveBeenLastCalledWith('/product/11')
      expect(router.currentRoute.value.name).toBe('product_detail')
      expect(router.currentRoute.value.params.id).toBe('11')
      expect(messageWarning).not.toHaveBeenCalled()
    },
  )

  it('opens the clicked item when an order contains multiple products', async () => {
    orders[0]!.items.push({ ...order.items[0]!, id: 102, productId: 12, productName: '第二件商品' })
    const { wrapper, router } = await mountOrders()
    await wrapper.findAll('.order-item')[1]!.trigger('click')
    await flushPromises()

    expect(getOrders).toHaveBeenLastCalledWith('/product/12')
    expect(router.currentRoute.value.params.id).toBe('12')
  })

  it.each<GetResponse>([
    { data: { status: false, payload: { message: '商品不存在或已下架' } } },
    response({ product: { ...product, status: 0 } }),
  ])('stays on the order list and warns when the product is unavailable (%j)', async (result) => {
    const { wrapper, router } = await mountOrders()
    getOrders.mockResolvedValueOnce(result)
    await wrapper.get('.order-item').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('orders')
    expect(messageWarning).toHaveBeenCalledWith('该商品已经下架')
  })

  it('allows viewing an on-sale product whose stock is sold out', async () => {
    const { wrapper, router } = await mountOrders()
    getOrders.mockResolvedValueOnce(response({ product: { ...product, stock: 0 } }))
    await wrapper.get('.order-item').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('product_detail')
    expect(messageWarning).not.toHaveBeenCalled()
  })

  it('reports a failed availability check and allows retrying', async () => {
    const { wrapper, router } = await mountOrders()
    getOrders.mockRejectedValueOnce(new Error('offline'))
    await wrapper.get('.order-item').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('orders')
    expect(messageError).toHaveBeenCalledWith('加载商品失败')
    expect(messageWarning).not.toHaveBeenCalled()
    await wrapper.get('.order-item').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('product_detail')
  })

  it('does not send duplicate availability requests while a product is opening', async () => {
    const { wrapper, router } = await mountOrders()
    let complete: (result: GetResponse) => void = () => undefined
    getOrders.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          complete = resolve
        }),
    )
    await wrapper.get('.order-item').trigger('click')
    await wrapper.get('.order-item').trigger('click')

    expect(getOrders.mock.calls.filter(([url]) => url === '/product/11')).toHaveLength(1)
    complete(response({ product }))
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('product_detail')
  })

  it('keeps receipt confirmation separate from product navigation and refreshes the order', async () => {
    const { wrapper, router } = await mountOrders()
    const confirmation = wrapper.getComponent(ElPopconfirm)
    await confirmation.get('button').trigger('click')
    expect(getOrders.mock.calls.filter(([url]) => url.startsWith('/product/'))).toHaveLength(0)
    expect(postOrder).not.toHaveBeenCalled()

    orders = [{ ...order, status: 'COMPLETED' }]
    confirmation.vm.$emit('confirm')
    await flushPromises()

    expect(postOrder).toHaveBeenCalledWith('/order/confirm', { id: 42 })
    expect(router.currentRoute.value.name).toBe('orders')
    expect(wrapper.get('.order-status').text()).toBe('已完成')
    expect(wrapper.get('.order-actions').text()).not.toContain('确认收货')
  })

  it('opens order details without checking or navigating to a product', async () => {
    const { wrapper, router } = await mountOrders()
    await wrapper.get('.order-actions .el-button.is-link').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('order_detail')
    expect(router.currentRoute.value.params.id).toBe('42')
    expect(getOrders.mock.calls.filter(([url]) => url.startsWith('/product/'))).toHaveLength(0)
  })

  it('keeps the payment button navigating to checkout', async () => {
    orders = [{ ...order, status: 'PENDING_PAYMENT' }]
    const { wrapper, router } = await mountOrders()
    await wrapper.get('.order-actions .el-button--primary').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('order_pay')
    expect(getOrders.mock.calls.filter(([url]) => url.startsWith('/product/'))).toHaveLength(0)
  })

  it('does not override order details when a pending product check finishes', async () => {
    const { wrapper, router } = await mountOrders()
    let complete: (result: GetResponse) => void = () => undefined
    getOrders.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          complete = resolve
        }),
    )
    await wrapper.get('.order-item').trigger('click')
    await wrapper.get('.order-actions .el-button.is-link').trigger('click')
    await flushPromises()
    complete(response({ product }))
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('order_detail')
  })

  it('translates the unavailable-product warning in English', async () => {
    const { wrapper } = await mountOrders()
    setLocale('en')
    getOrders.mockResolvedValueOnce({
      data: { status: false, payload: { message: '商品不存在或已下架' } },
    })
    await wrapper.get('.order-item').trigger('click')
    await flushPromises()

    expect(messageWarning).toHaveBeenCalledWith('This product is no longer available')
  })
})
