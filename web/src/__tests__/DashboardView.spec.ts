import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import type { ApiResult, DashboardData, OrderStatus } from '@/types/api'
import DashboardView from '@/views/admin/DashboardView.vue'
import { useTheme } from '@/utils/useTheme'
import { setLocale } from '@/i18n'

const { getDashboard } = vi.hoisted(() => ({
  getDashboard:
    vi.fn<
      (
        url: string,
        config: { params: { days: number } },
      ) => Promise<{ data: ApiResult<DashboardData> }>
    >(),
}))
vi.mock('@/utils/request', () => ({ default: { get: getDashboard } }))

enableAutoUnmount(afterEach)

const fixture = (days: number): DashboardData => ({
  stats: { userCount: 8, productCount: 12, orderCount: 9, salesAmount: 1234.56 },
  recentOrders: [],
  trend: Array.from({ length: days }, (_, index) => {
    const date = new Date('2026-11-17T00:00:00Z')
    date.setUTCDate(date.getUTCDate() - days + 1 + index)
    return {
      date: date.toISOString().slice(0, 10),
      orderCount: index === 0 ? 2 : index === days - 1 ? 3 : 0,
      salesAmount: index === 0 ? 0.1 : index === days - 1 ? 20.15 : 0,
    }
  }),
  orderStatuses: (
    ['PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED', 'REFUNDED'] as OrderStatus[]
  ).map((status) => ({ status, count: status === 'PAID' ? 9 : 0 })),
})

const response = (payload: DashboardData) => ({ data: { status: true as const, payload } })

const mountDashboard = () =>
  mount(DashboardView, {
    global: {
      stubs: {
        ElCard: { template: '<div><slot name="header" /><slot /></div>' },
        ElTable: true,
        ElTableColumn: true,
        ElSkeleton: true,
        ElTag: true,
        ElEmpty: { props: ['description'], template: '<p>{{ description }}</p>' },
        RouterLink: RouterLinkStub,
        DashboardChart: {
          name: 'DashboardChart',
          props: ['option', 'label'],
          template: '<div class="chart-stub" :aria-label="label" />',
        },
      },
    },
  })

describe('DashboardView', () => {
  beforeEach(() => {
    getDashboard.mockReset()
    getDashboard.mockResolvedValue(response(fixture(30)))
  })

  afterEach(() => {
    useTheme().isDark.value = false
  })

  it('loads thirty days and shows the period totals alongside both charts', async () => {
    const wrapper = mountDashboard()
    await flushPromises()

    expect(getDashboard).toHaveBeenCalledWith('/admin/dashboard', { params: { days: 30 } })
    expect(wrapper.findAll('.chart-stub')).toHaveLength(2)
    expect(wrapper.get('.chart-caption').text()).toBe('2026-10-19 — 2026-11-17')
    expect(wrapper.findAll('.trend-summary strong').map((item) => item.text())).toEqual([
      '¥20.25',
      '5',
    ])
    expect(wrapper.text()).toContain('¥1234.56')
  })

  it('fetches the selected seven-day range and updates the chart label and dates', async () => {
    const wrapper = mountDashboard()
    await flushPromises()
    getDashboard.mockResolvedValueOnce(response(fixture(7)))

    await wrapper.get('input[value="7"]').setValue(true)
    await flushPromises()

    expect(getDashboard).toHaveBeenLastCalledWith('/admin/dashboard', { params: { days: 7 } })
    expect(wrapper.get('.chart-caption').text()).toBe('2026-11-11 — 2026-11-17')
    expect(wrapper.get('.chart-stub').attributes('aria-label')).toContain('近 7 天')
  })

  it('keeps the last successful range after an error and recovers when refreshed', async () => {
    const wrapper = mountDashboard()
    await flushPromises()
    getDashboard.mockRejectedValueOnce(new Error('offline'))

    await wrapper.get('input[value="7"]').setValue(true)
    await flushPromises()

    expect(wrapper.get('.dashboard-error').text()).toContain('加载仪表盘数据失败')
    expect((wrapper.get('input[value="30"]').element as HTMLInputElement).checked).toBe(true)
    expect(wrapper.get('.chart-stub').attributes('aria-label')).toContain('近 30 天')

    await wrapper.get('.heading-actions button').trigger('click')
    await flushPromises()

    expect(getDashboard).toHaveBeenLastCalledWith('/admin/dashboard', { params: { days: 30 } })
    expect(wrapper.find('.dashboard-error').exists()).toBe(false)
  })

  it('keeps the zero trend visible and shows an empty state when there are no orders', async () => {
    const empty = fixture(30)
    empty.stats.orderCount = 0
    empty.stats.salesAmount = 0
    empty.trend = empty.trend.map((point) => ({ ...point, orderCount: 0, salesAmount: 0 }))
    empty.orderStatuses = empty.orderStatuses.map((item) => ({ ...item, count: 0 }))
    getDashboard.mockResolvedValueOnce(response(empty))
    const wrapper = mountDashboard()
    await flushPromises()

    expect(wrapper.findAll('.chart-stub')).toHaveLength(1)
    expect(wrapper.text()).toContain('暂无订单数据')
    expect(wrapper.findAll('.trend-summary strong').map((item) => item.text())).toEqual([
      '¥0.00',
      '0',
    ])
  })

  it('updates chart labels when the language changes without refetching dashboard data', async () => {
    const wrapper = mountDashboard()
    await flushPromises()
    try {
      setLocale('en')
      await flushPromises()
      expect(wrapper.get('h1').text()).toBe('Dashboard')
      const charts = wrapper.findAllComponents({ name: 'DashboardChart' })
      expect(charts[0]!.props('option').legend.data).toEqual(['Sales', 'New orders'])
      expect(
        charts[1]!.props('option').series[0].data.map((item: { name: string }) => item.name),
      ).toContain('Awaiting payment')
      expect(getDashboard).toHaveBeenCalledOnce()
    } finally {
      setLocale('zh-CN')
    }
  })

  it('updates chart colors when the shared theme changes', async () => {
    const wrapper = mountDashboard()
    await flushPromises()
    const before = wrapper.findComponent({ name: 'DashboardChart' }).props('option').textStyle.color

    useTheme().toggleTheme()
    await flushPromises()

    const after = wrapper.findComponent({ name: 'DashboardChart' }).props('option').textStyle.color
    expect(after).not.toBe(before)
    expect(getDashboard).toHaveBeenCalledTimes(1)
  })
})
