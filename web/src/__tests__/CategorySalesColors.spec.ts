import { describe, expect, it } from 'vitest'
import { init, setPlatformAPI, use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { AriaComponent, GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import type { LineSeriesOption } from 'echarts'
import type { CategorySales } from '@/types/api'
import { buildCategorySalesChart } from '@/utils/dashboardCharts'
import { setLocale } from '@/i18n'

use([LineChart, AriaComponent, GridComponent, LegendComponent, TooltipComponent, SVGRenderer])
// SVG 渲染测试只验证颜色，文字测量使用固定宽度以适配没有 Canvas 的 jsdom。
setPlatformAPI({ measureText: (text) => ({ width: text.length * 7 }) })

const plushCategories: CategorySales[] = [
  'claude玩偶',
  'deepseek玩偶',
  'gemini玩偶',
  'gpt玩偶',
].map((category, index) => ({
  category,
  soldQuantity: index + 3,
  trend: [
    { date: '2026-11-24', soldQuantity: index + 1 },
    { date: '2026-11-25', soldQuantity: 2 },
  ],
}))

const categories: CategorySales[] = [
  ...plushCategories,
  ...Array.from({ length: 15 }, (_, index) => ({
    category: `分类${String(index).padStart(2, '0')}`,
    soldQuantity: index + 3,
    trend: [
      { date: '2026-11-24', soldQuantity: index + 1 },
      { date: '2026-11-25', soldQuantity: 2 },
    ],
  })),
]

describe('category sales colors', () => {
  it.each([false, true])('uses distinct matching line and marker colors with dark=%s', (dark) => {
    const series = buildCategorySalesChart(categories, dark).series as LineSeriesOption[]
    const colors = series.map((item) => item.lineStyle?.color)

    expect(new Set(colors).size).toBe(categories.length)
    expect(colors).not.toContain(undefined)
    series.forEach((item) => expect(item.itemStyle?.color).toBe(item.lineStyle?.color))
  })

  it('keeps image theme colors when rankings, language or other categories change', () => {
    const data = [...categories, { ...categories[0]!, category: null }]
    const original = buildCategorySalesChart(data, false).series as LineSeriesOption[]
    try {
      setLocale('en')
      const reordered = buildCategorySalesChart([...data].reverse(), false)
        .series as LineSeriesOption[]
      data.forEach((_, index) => {
        expect(reordered[data.length - 1 - index]!.lineStyle?.color).toBe(
          original[index]!.lineStyle?.color,
        )
      })
      const presetsOnly = buildCategorySalesChart(plushCategories, false)
        .series as LineSeriesOption[]
      presetsOnly.forEach((item, index) => {
        expect(item.lineStyle?.color).toBe(original[index]!.lineStyle?.color)
      })
    } finally {
      setLocale('zh-CN')
    }
  })

  it.each([false, true])(
    'renders each actual SVG curve in its assigned color with dark=%s',
    (dark) => {
      const chart = init(null, undefined, { renderer: 'svg', ssr: true, width: 900, height: 320 })
      try {
        const option = buildCategorySalesChart(categories.slice(0, 4), dark)
        chart.setOption({ ...option, animation: false })
        const svg = new DOMParser().parseFromString(chart.renderToSVGString(), 'image/svg+xml')
        for (const series of option.series as LineSeriesOption[]) {
          expect(svg.querySelector(`path[stroke="${series.lineStyle?.color}"]`)).not.toBeNull()
        }
      } finally {
        chart.dispose()
      }
    },
  )
})
