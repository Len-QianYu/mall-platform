import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { init } from 'echarts/core'
import DashboardChart from '@/components/admin/DashboardChart.vue'
import { buildTrendChart } from '@/utils/dashboardCharts'

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
