import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { init } from 'echarts/core'
import type { EChartsOption } from 'echarts'
import DashboardChart from '@/components/admin/DashboardChart.vue'
import { buildCategorySalesChart, buildTrendChart } from '@/utils/dashboardCharts'

vi.mock('echarts/core', () => ({
  init: vi.fn<typeof init>(),
  use: vi.fn<typeof import('echarts/core').use>(),
}))
enableAutoUnmount(afterEach)

const chart = {
  setOption: vi.fn<ReturnType<typeof init>['setOption']>(),
  resize: vi.fn<ReturnType<typeof init>['resize']>(),
  dispose: vi.fn<ReturnType<typeof init>['dispose']>(),
}
const observe = vi.fn<ResizeObserver['observe']>()
const disconnect = vi.fn<ResizeObserver['disconnect']>()
let resized: ResizeObserverCallback

describe('DashboardChart', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(init).mockReturnValue(chart as unknown as ReturnType<typeof init>)
    vi.stubGlobal(
      'ResizeObserver',
      class {
        constructor(callback: ResizeObserverCallback) {
          resized = callback
        }

        observe = observe
        disconnect = disconnect
      },
    )
  })

  afterEach(() => vi.unstubAllGlobals())

  it('resizes when its container changes, including sidebar width changes', () => {
    const wrapper = mount(DashboardChart, {
      props: { option: buildTrendChart([], false), label: '销售趋势' },
    })

    expect(observe).toHaveBeenCalledWith(wrapper.element)
    resized([], {} as ResizeObserver)
    expect(chart.resize).toHaveBeenCalledTimes(1)
  })

  it('updates data and theme without recreating the chart and releases resources on leave', async () => {
    const wrapper = mount(DashboardChart, {
      props: { option: buildTrendChart([], false), label: '销售趋势' },
    })
    const updated = buildTrendChart(
      [{ date: '2026-11-17', orderCount: 2, salesAmount: 12.34 }],
      true,
    )
    await wrapper.setProps({ option: updated })

    expect(init).toHaveBeenCalledTimes(1)
    expect(chart.setOption).toHaveBeenLastCalledWith(updated, { notMerge: true })

    wrapper.unmount()
    expect(disconnect).toHaveBeenCalledTimes(1)
    expect(chart.dispose).toHaveBeenCalledTimes(1)
  })
})

const axisMaximum = (option: EChartsOption, peak: number, index = 0): number => {
  const axes = Array.isArray(option.yAxis) ? option.yAxis : [option.yAxis]
  const maximum = axes[index]?.max
  if (typeof maximum !== 'function') throw new Error('Expected a dynamic axis maximum')
  return Number(maximum({ min: 0, max: peak }))
}

describe('dashboard axis ranges', () => {
  it('keeps a single order below the middle of the chart', () => {
    const option = buildTrendChart([{ date: '2026-11-25', orderCount: 1, salesAmount: 57 }], false)
    const maximum = axisMaximum(option, 1, 1)

    expect(maximum).toBeGreaterThanOrEqual(5)
    expect(1 / maximum).toBeLessThan(0.5)
  })

  it.each([30, 57])('keeps a sales peak of %s yuan below the top of a sensible range', (peak) => {
    const option = buildTrendChart(
      [{ date: '2026-11-25', orderCount: 1, salesAmount: peak }],
      false,
    )
    const maximum = axisMaximum(option, peak)

    expect(maximum).toBeGreaterThanOrEqual(100)
    expect(peak / maximum).toBeLessThan(0.6)
  })

  it('grows beyond the base ranges and leaves headroom when larger values arrive', () => {
    const option = buildTrendChart([], false)

    expect(axisMaximum(option, 1500)).toBeGreaterThan(1500)
    expect(axisMaximum(option, 20, 1)).toBeGreaterThan(20)
    expect(axisMaximum(option, 1500)).toBeGreaterThan(axisMaximum(option, 57))
  })

  it('adapts category sales to the visible peak when the date range or legend selection changes', () => {
    const option = buildCategorySalesChart(
      [{ category: 'gpt玩偶', soldQuantity: 1, trend: [{ date: '2026-11-25', soldQuantity: 1 }] }],
      false,
    )

    expect(axisMaximum(option, 1)).toBeGreaterThanOrEqual(5)
    expect(axisMaximum(option, 40)).toBeGreaterThan(40)
    expect(axisMaximum(option, 40)).toBeGreaterThan(axisMaximum(option, 1))
  })

  it('keeps empty charts on finite nonzero ranges', () => {
    expect(axisMaximum(buildTrendChart([], false), Number.NEGATIVE_INFINITY)).toBe(100)
    expect(axisMaximum(buildCategorySalesChart([], false), 0)).toBe(5)
  })
})
