import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { ApiResult, Pagination, SeckillActivity } from '@/types/api'
import SeckillView from '@/views/shop/SeckillView.vue'

const { getActivities } = vi.hoisted(() => ({
  getActivities:
    vi.fn<
      (
        url: string,
        options: { params: { page: number; pageSize: number; phase?: string } },
      ) => Promise<{ data: ApiResult<{ activities: SeckillActivity[]; pagination: Pagination }> }>
    >(),
}))
vi.mock('@/utils/request', () => ({ default: { get: getActivities } }))
enableAutoUnmount(afterEach)

const serverTime = Date.parse('2026-11-22T12:00:00+08:00')
const activity = (overrides: Partial<SeckillActivity> = {}): SeckillActivity => ({
  id: 3,
  productId: 11,
  title: '周末限时好价',
  productName: '秒杀测试商品',
  productSubtitle: '限量抢购',
  productImage: null,
  productStatus: 1,
  originalPrice: 100,
  seckillPrice: 30,
  totalStock: 10,
  stock: 5,
  returnedStock: 0,
  limitPerUser: 2,
  status: 1,
  phase: 'LIVE',
  startsAt: '2026-11-22T11:00:00',
  endsAt: '2026-11-22T13:00:00',
  startsAtEpochMs: serverTime - 3600000,
  endsAtEpochMs: serverTime + 3600000,
  serverTime,
  ...overrides,
})
const response = (activities: SeckillActivity[]) => ({
  data: {
    status: true as const,
    payload: {
      activities,
      pagination: { total: activities.length, pageSize: 12, pageCount: 1, currentPage: 1 },
    },
  },
})
const mountPage = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/seckill', name: 'seckill', component: SeckillView },
      { path: '/products', name: 'products', component: { template: '<p>products</p>' } },
      { path: '/products/:id', name: 'product_detail', component: { template: '<p>detail</p>' } },
      { path: '/checkout', name: 'checkout', component: { template: '<p>checkout</p>' } },
    ],
  })
  await router.push('/seckill')
  const wrapper = mount(SeckillView, {
    global: {
      plugins: [router],
      stubs: { ElImage: true, ElSkeleton: true, ElPagination: true },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('seckill activities', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] })
    vi.setSystemTime(serverTime + 3600000)
    getActivities.mockReset()
    getActivities.mockResolvedValue(response([activity()]))
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows campaign prices and only enables live activities', async () => {
    getActivities.mockResolvedValue(
      response([
        activity(),
        activity({ id: 4, startsAtEpochMs: serverTime + 10000, phase: 'UPCOMING' }),
        activity({ id: 5, stock: 0, phase: 'SOLD_OUT' }),
        activity({ id: 6, endsAtEpochMs: serverTime - 1000, status: 2, phase: 'ENDED' }),
      ]),
    )
    const { wrapper } = await mountPage()
    const cards = wrapper.findAll('.seckill-card')
    expect(cards).toHaveLength(4)
    expect(cards[0]?.get('.card-prices').text()).toContain('¥30.00')
    expect(cards[0]?.get('.quota-meta').text()).toContain('每人限购 2 件')
    expect(cards[0]?.get('.buy-button').attributes('disabled')).toBeUndefined()
    for (const card of cards.slice(1))
      expect(card.get('.buy-button').attributes('disabled')).toBeDefined()
  })

  it('uses server time despite a wrong client clock and opens the activity on time', async () => {
    getActivities.mockResolvedValue(
      response([activity({ startsAtEpochMs: serverTime + 2000, phase: 'UPCOMING' })]),
    )
    const { wrapper } = await mountPage()
    expect(wrapper.get('.buy-button').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.card-countdown').text()).toContain('00:00:02')
    await vi.advanceTimersByTimeAsync(2000)
    expect(wrapper.get('.buy-button').text()).toBe('立即抢购')
    expect(wrapper.get('.buy-button').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('routes purchases through campaign checkout without supplying a client price', async () => {
    const { wrapper, router } = await mountPage()
    await wrapper.get('.buy-button').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('checkout')
    expect(router.currentRoute.value.query).toEqual({ seckillActivityId: '3', quantity: '1' })
  })

  it('loads each status filter from the server', async () => {
    const { wrapper } = await mountPage()
    await wrapper.findAll('[role="tab"]')[1]?.trigger('click')
    await flushPromises()
    expect(getActivities).toHaveBeenLastCalledWith('/seckill', {
      params: { page: 1, pageSize: 12, phase: 'LIVE' },
    })
  })

  it('allows retrying a failed load and displays a usable empty state', async () => {
    getActivities.mockRejectedValueOnce(new Error('offline'))
    getActivities.mockResolvedValue(response([]))
    const { wrapper } = await mountPage()
    expect(wrapper.get('[role="alert"]').text()).toContain('加载失败')
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('秒杀好物正在准备中')
    expect(wrapper.find('a[href="/products"]').exists()).toBe(true)
  })
})
