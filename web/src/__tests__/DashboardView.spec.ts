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

const fixture = (days: number): DashboardData => {
  const trend = Array.from({ length: days }, (_, index) => {
    const date = new Date('2026-11-17T00:00:00Z')
    date.setUTCDate(date.getUTCDate() - days + 1 + index)
    return {
      date: date.toISOString().slice(0, 10),
      orderCount: index === 0 ? 2 : index === days - 1 ? 3 : 0,
      salesAmount: index === 0 ? 0.1 : index === days - 1 ? 20.15 : 0,
    }
  })
  return {
    stats: {
      userCount: 8,
      userRoleCounts: { admin: 2, user: 5, super_admin: 1 },
      productCount: 12,
      productStatusCounts: { onSale: 8, offSale: 4 },
      orderCount: 9,
      salesAmount: 1234.56,
      salesAmountsByStatus: { PAID: 200.1, SHIPPED: 300.2, COMPLETED: 734.26 },
    },
    recentOrders: [],
    trend,
    orderStatuses: (
      ['PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED', 'REFUNDED'] as OrderStatus[]
    ).map((status) => ({
      status,
      count: { PENDING_PAYMENT: 1, PAID: 3, SHIPPED: 2, COMPLETED: 1, CANCELLED: 1, REFUNDED: 1 }[
        status
      ],
    })),
    categorySales: ['gpt玩偶', 'claude玩偶', 'deepseek玩偶'].map((category, categoryIndex) => ({
      category,
      soldQuantity: [5, 2, 0][categoryIndex]!,
      trend: trend.map((point, index) => ({
        date: point.date,
        soldQuantity: index === days - 1 ? [5, 2, 0][categoryIndex]! : 0,
      })),
    })),
  }
}

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

const findCategoryChart = (wrapper: ReturnType<typeof mountDashboard>) =>
  wrapper
    .findAllComponents({ name: 'DashboardChart' })
    .find((item) => item.classes('category-sales-chart'))!
const findCategoryTable = (wrapper: ReturnType<typeof mountDashboard>) =>
  wrapper
    .findAllComponents({ name: 'ElTable' })
    .find((item) => item.classes('category-sales-table'))!

describe('DashboardView', () => {
  beforeEach(() => {
    getDashboard.mockReset()
    getDashboard.mockResolvedValue(response(fixture(30)))
  })

  afterEach(() => {
    useTheme().isDark.value = false
  })

  it('loads thirty days and shows period totals alongside the dashboard charts', async () => {
    const wrapper = mountDashboard()
    expect(
      wrapper.findAll('.user-stat-card .stat-breakdown dd').map((item) => item.text()),
    ).toEqual(['-', '-', '-'])
    await flushPromises()

    expect(getDashboard).toHaveBeenCalledWith('/admin/dashboard', { params: { days: 30 } })
    expect(wrapper.findAll('.chart-stub')).toHaveLength(3)
    expect(wrapper.get('.chart-caption').text()).toBe('2026-10-19 — 2026-11-17')
    expect(wrapper.findAll('.trend-summary strong').map((item) => item.text())).toEqual([
      '¥20.25',
      '5',
    ])
    expect(wrapper.text()).toContain('¥1234.56')
    expect(wrapper.get('.user-stat-card .stat-value').text()).toBe('8')
    expect(
      wrapper.findAll('.user-stat-card .stat-breakdown dt').map((item) => item.text()),
    ).toEqual(['管理员', '用户', '超级管理员'])
    expect(
      wrapper.findAll('.user-stat-card .stat-breakdown dd').map((item) => item.text()),
    ).toEqual(['2', '5', '1'])
    const cards = wrapper.findAll('.stat-card')
    expect(
      cards.map((card) => card.findAll('.stat-breakdown dt').map((item) => item.text())),
    ).toEqual([
      ['管理员', '用户', '超级管理员'],
      ['上架', '下架'],
      ['待支付', '已支付', '已发货'],
      ['已支付', '已发货', '已完成'],
    ])
    expect(
      cards.map((card) => card.findAll('.stat-breakdown dd').map((item) => item.text())),
    ).toEqual([
      ['2', '5', '1'],
      ['8', '4'],
      ['1', '3', '2'],
      ['¥200.10', '¥300.20', '¥734.26'],
    ])
    expect(wrapper.get('.category-sales-total strong').text()).toBe('7 件')
    expect(findCategoryTable(wrapper).props('data')).toEqual(fixture(30).categorySales)
    const categoryChart = findCategoryChart(wrapper).props('option')
    expect(categoryChart.series.map((series: { name: string }) => series.name)).toEqual([
      'gpt玩偶',
      'claude玩偶',
      'deepseek玩偶',
    ])
    expect(categoryChart.series[0].data).toEqual([...Array<number>(29).fill(0), 5])
    expect(categoryChart.series[2].data).toEqual(Array<number>(30).fill(0))
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
    expect(wrapper.get('.category-sales-chart').attributes('aria-label')).toContain('近 7 天')
    expect(findCategoryChart(wrapper).props('option').xAxis.data).toHaveLength(7)
  })

  it('switches the shared date range from the category sales controls and refreshes both views', async () => {
    const wrapper = mountDashboard()
    await flushPromises()
    getDashboard.mockResolvedValueOnce(response(fixture(7)))

    await wrapper.get('.category-sales-card input[value="7"]').setValue(true)
    await flushPromises()

    expect(getDashboard).toHaveBeenLastCalledWith('/admin/dashboard', { params: { days: 7 } })
    expect(getDashboard).toHaveBeenCalledTimes(2)
    expect((wrapper.get('input[value="7"]').element as HTMLInputElement).checked).toBe(true)
    expect(wrapper.get('.category-sales-card .chart-caption').text()).toBe(
      '2026-11-11 — 2026-11-17',
    )
    expect(findCategoryChart(wrapper).props('option').series[0].data).toEqual([0, 0, 0, 0, 0, 0, 5])
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
    expect(wrapper.get('.category-sales-chart').attributes('aria-label')).toContain('近 30 天')

    await wrapper.get('.heading-actions button').trigger('click')
    await flushPromises()

    expect(getDashboard).toHaveBeenLastCalledWith('/admin/dashboard', { params: { days: 30 } })
    expect(wrapper.find('.dashboard-error').exists()).toBe(false)
  })

  it('keeps the zero trend visible and shows an empty state when there are no orders', async () => {
    const empty = fixture(30)
    empty.stats.orderCount = 0
    empty.stats.salesAmount = 0
    empty.stats.salesAmountsByStatus = { PAID: 0, SHIPPED: 0, COMPLETED: 0 }
    empty.trend = empty.trend.map((point) => ({ ...point, orderCount: 0, salesAmount: 0 }))
    empty.orderStatuses = empty.orderStatuses.map((item) => ({ ...item, count: 0 }))
    empty.categorySales = []
    getDashboard.mockResolvedValueOnce(response(empty))
    const wrapper = mountDashboard()
    await flushPromises()

    expect(wrapper.findAll('.chart-stub')).toHaveLength(1)
    expect(wrapper.text()).toContain('暂无订单数据')
    expect(wrapper.get('.category-sales-card').text()).toContain('暂无分类商品')
    expect(wrapper.findAll('.trend-summary strong').map((item) => item.text())).toEqual([
      '¥0.00',
      '0',
    ])
  })

  it('keeps categories and zero daily lines visible for a period without sales', async () => {
    const empty = fixture(30)
    empty.categorySales = empty.categorySales.map((item) => ({
      ...item,
      soldQuantity: 0,
      trend: item.trend.map((point) => ({ ...point, soldQuantity: 0 })),
    }))
    getDashboard.mockResolvedValueOnce(response(empty))
    const wrapper = mountDashboard()
    await flushPromises()

    expect(wrapper.get('.category-sales-total strong').text()).toBe('0 件')
    expect(wrapper.get('.category-sales-card').text()).toContain('所选时间范围暂无销量')
    expect(findCategoryTable(wrapper).props('data')).toHaveLength(3)
    expect(findCategoryChart(wrapper).props('option').series[0].data).toEqual(
      Array<number>(30).fill(0),
    )
  })

  it('shows an uncategorized label for sales with no current product category', async () => {
    const data = fixture(7)
    data.categorySales[0]!.category = null
    getDashboard.mockResolvedValueOnce(response(data))
    const wrapper = mountDashboard()
    await flushPromises()

    expect(findCategoryChart(wrapper).props('option').series[0].name).toBe('未分类')
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
      expect(charts[2]!.props('option').yAxis.name).toBe('Units sold')
      expect(charts[2]!.props('option').series[0].name).toBe('gpt玩偶')
      expect(wrapper.get('.category-sales-total strong').text()).toBe('7 units')
      expect(
        wrapper.findAll('.user-stat-card .stat-breakdown dt').map((item) => item.text()),
      ).toEqual(['Admin', 'User', 'Super admin'])
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
