import type { EChartsOption, LineSeriesOption } from 'echarts'
import type { CategorySales, DashboardTrendPoint, OrderStatus, OrderStatusCount } from '@/types/api'
import { ORDER_STATUS, formatMoney } from '@/utils/format'
import { formatNumber, t, useLocale } from '@/i18n'
import { PLUSH_CATEGORIES } from '@/constants/productCategories'

// ECharts 需要实际色值，读取后台主题变量，让图表与卡片、按钮和状态标签保持一致。
const palette = (dark: boolean) => {
  const root = typeof document === 'undefined' ? undefined : document.documentElement
  const style =
    root && root.classList.contains('dark') === dark ? getComputedStyle(root) : undefined
  const token = (name: string, fallback: string) =>
    style?.getPropertyValue(`--el-${name}`).trim() || fallback

  return {
    text: token('text-color-regular', dark ? '#cfd3dc' : '#606266'),
    heading: token('text-color-primary', dark ? '#e5eaf3' : '#303133'),
    muted: token('text-color-secondary', dark ? '#a3a6ad' : '#909399'),
    border: token('border-color-lighter', dark ? '#363637' : '#ebeef5'),
    background: token('bg-color-overlay', dark ? '#1d1e1f' : '#ffffff'),
    primary: token('color-primary', '#0da5aa'),
    success: token('color-success', '#67c23a'),
    warning: token('color-warning', '#e6a23c'),
    danger: token('color-danger', '#f56c6c'),
    info: token('color-info', '#909399'),
    infoSoft: token('color-info-light-3', dark ? '#6b6d71' : '#b1b3b8'),
  }
}

const compactNumber = (value: number) => {
  if (useLocale().locale.value === 'en')
    return formatNumber(value, { notation: 'compact', maximumFractionDigits: 1 })
  return Math.abs(value) >= 10000 ? `${(value / 10000).toFixed(1)}万` : formatNumber(value)
}

// 给峰值留出余量，再按易读的刻度向上取整；低数据量保留基础量程。
const paddedAxisMax = (peak: number, minimum: number): number => {
  const target = Math.max(minimum, (Number.isFinite(peak) ? Math.max(0, peak) : 0) * 1.4)
  const magnitude = 10 ** Math.floor(Math.log10(target / 5))
  const normalizedStep = target / 5 / magnitude
  const step =
    (normalizedStep <= 1 ? 1 : normalizedStep <= 2 ? 2 : normalizedStep <= 5 ? 5 : 10) * magnitude
  return Math.ceil(target / step) * step
}

export function buildTrendChart(points: DashboardTrendPoint[], dark: boolean): EChartsOption {
  const colors = palette(dark)
  return {
    animationDuration: 350,
    textStyle: { color: colors.text, fontFamily: 'system-ui, sans-serif' },
    aria: { enabled: true, label: { description: t('按天展示销售额和新增订单数的趋势图') } },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: colors.background,
      borderColor: colors.border,
      textStyle: { color: colors.text },
      axisPointer: { type: 'line', lineStyle: { color: colors.info, type: 'dashed' } },
    },
    legend: {
      top: 0,
      right: 0,
      data: [t('销售额'), t('新增订单')],
      textStyle: { color: colors.text },
      itemWidth: 16,
      itemHeight: 8,
    },
    grid: { top: 58, left: 4, right: 4, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: points.map((point) => point.date),
      axisLine: { lineStyle: { color: colors.border } },
      axisTick: { show: false },
      axisLabel: {
        color: colors.muted,
        hideOverlap: true,
        formatter: (value: string) => value.slice(5).replace('-', '/'),
      },
    },
    yAxis: [
      {
        type: 'value',
        name: t('销售额（元）'),
        min: 0,
        max: (extent) => paddedAxisMax(extent.max, 100),
        splitNumber: 5,
        // 标题朝绘图区对齐，避免居中时首尾文字超出 SVG 边界。
        nameTextStyle: { color: colors.muted, align: 'left' },
        axisLabel: { color: colors.muted, formatter: compactNumber },
        splitLine: { lineStyle: { color: colors.border, type: 'dashed' } },
      },
      {
        type: 'value',
        name: t('订单量（笔）'),
        min: 0,
        max: (extent) => paddedAxisMax(extent.max, 5),
        splitNumber: 5,
        minInterval: 1,
        nameTextStyle: { color: colors.muted, align: 'right' },
        axisLabel: { color: colors.muted, formatter: compactNumber },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: t('销售额'),
        type: 'line',
        yAxisIndex: 0,
        data: points.map((point) => point.salesAmount),
        // 使用直线连接，避免平滑曲线在零销售额附近产生负值的视觉误差。
        smooth: false,
        showSymbol: points.length <= 7,
        symbolSize: 7,
        itemStyle: { color: colors.primary },
        lineStyle: { width: 3, color: colors.primary },
        areaStyle: {
          opacity: dark ? 0.24 : 0.18,
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: colors.primary },
              { offset: 1, color: colors.background },
            ],
          },
        },
        tooltip: { valueFormatter: (value) => formatMoney(Number(value)) },
        z: 2,
      },
      {
        name: t('新增订单'),
        type: 'bar',
        yAxisIndex: 1,
        data: points.map((point) => point.orderCount),
        barMaxWidth: 18,
        itemStyle: { color: colors.warning, opacity: 0.75, borderRadius: [4, 4, 0, 0] },
        tooltip: {
          valueFormatter: (value) => t('{count} 笔', { count: formatNumber(Number(value)) }),
        },
      },
    ],
  }
}

export function getCategorySalesColors(categories: CategorySales[], dark: boolean) {
  const seriesColors = dark
    ? [
        '#32c5c9',
        '#f6b65c',
        '#8dcc58',
        '#ac92f4',
        '#70a5ff',
        '#ef83c3',
        '#d19a66',
        '#63c9ea',
        '#f88888',
        '#bdcf62',
      ]
    : [
        '#0da5aa',
        '#ea8b2d',
        '#5ca52f',
        '#8b5cf6',
        '#3b82f6',
        '#db2777',
        '#a66b31',
        '#0891b2',
        '#dc4b4b',
        '#8c9f28',
      ]
  // 四个玩偶分类使用图片主题色，其余分类按名称分配；排名与时间范围不会改变分类配色。
  return new Map(
    [...categories]
      .sort((left, right) => (left.category ?? '').localeCompare(right.category ?? '', 'zh-CN'))
      .map((item, index) => {
        const categoryName = item.category?.replace(/\s+/g, '').toLowerCase()
        const plushCategory = PLUSH_CATEGORIES.find(
          (category) => category.name === categoryName || category.key === categoryName,
        )
        const color =
          plushCategory?.chartColors[dark ? 'dark' : 'light'] ??
          seriesColors[index] ??
          `hsl(${((index - seriesColors.length) * 137.508 + 24) % 360}, 65%, ${dark ? 68 : 43}%)`
        return [item.category, color] as const
      }),
  )
}

export function buildCategorySalesChart(categories: CategorySales[], dark: boolean): EChartsOption {
  const colors = palette(dark)
  const colorsByCategory = getCategorySalesColors(categories, dark)
  const dates = categories[0]?.trend.map((point) => point.date) ?? []

  return {
    animationDuration: 350,
    textStyle: { color: colors.text, fontFamily: 'system-ui, sans-serif' },
    aria: { enabled: true, label: { description: t('各分类每日销量') } },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: colors.background,
      borderColor: colors.border,
      textStyle: { color: colors.text },
      axisPointer: { type: 'line', lineStyle: { color: colors.info, type: 'dashed' } },
    },
    legend: {
      type: 'scroll',
      top: 0,
      left: 0,
      right: 0,
      textStyle: { color: colors.text },
      pageTextStyle: { color: colors.text },
      pageIconColor: colors.primary,
      pageIconInactiveColor: colors.border,
      itemWidth: 16,
      itemHeight: 8,
    },
    grid: { top: 58, left: 4, right: 4, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLine: { lineStyle: { color: colors.border } },
      axisTick: { show: false },
      axisLabel: {
        color: colors.muted,
        hideOverlap: true,
        formatter: (value: string) => value.slice(5).replace('-', '/'),
      },
    },
    yAxis: {
      type: 'value',
      name: t('销量（件）'),
      min: 0,
      max: (extent) => paddedAxisMax(extent.max, 5),
      splitNumber: 5,
      minInterval: 1,
      nameTextStyle: { color: colors.muted, align: 'left' },
      axisLabel: { color: colors.muted, formatter: compactNumber },
      splitLine: { lineStyle: { color: colors.border, type: 'dashed' } },
    },
    series: categories.map<LineSeriesOption>((item) => {
      const color = colorsByCategory.get(item.category)
      return {
        name: item.category ?? t('未分类'),
        type: 'line',
        data: item.trend.map((point) => point.soldQuantity),
        smooth: false,
        showSymbol: dates.length <= 7,
        symbolSize: 6,
        lineStyle: { color, width: 2 },
        itemStyle: { color },
        emphasis: { focus: 'series' },
        tooltip: {
          valueFormatter: (value) => t('{count} 件', { count: formatNumber(Number(value)) }),
        },
      }
    }),
  }
}

export function buildStatusChart(counts: OrderStatusCount[], dark: boolean): EChartsOption {
  const colors = palette(dark)
  const statusColors: Record<OrderStatus, string> = {
    PENDING_PAYMENT: colors.info,
    PAID: colors.warning,
    SHIPPED: colors.primary,
    COMPLETED: colors.success,
    CANCELLED: colors.danger,
    REFUNDED: colors.infoSoft,
  }
  const total = counts.reduce((sum, item) => sum + item.count, 0)
  const data = counts.map((item) => ({
    name: t(ORDER_STATUS[item.status]?.label ?? item.status),
    value: item.count,
    itemStyle: { color: statusColors[item.status] },
  }))

  return {
    animationDuration: 350,
    textStyle: { color: colors.text, fontFamily: 'system-ui, sans-serif' },
    aria: { enabled: true, label: { description: t('全部订单的当前状态分布环形图') } },
    tooltip: {
      trigger: 'item',
      confine: true,
      backgroundColor: colors.background,
      borderColor: colors.border,
      textStyle: { color: colors.text },
      formatter: t('{b}<br />{c} 笔 · {d}%'),
    },
    legend: {
      bottom: 0,
      left: 'center',
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 14,
      textStyle: { color: colors.text },
      formatter: (name) => `${name}  ${data.find((item) => item.name === name)?.value ?? 0}`,
    },
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '34%',
        style: {
          text: formatNumber(total),
          fill: colors.heading,
          fontSize: 28,
          fontWeight: 600,
          fontFamily: 'system-ui, sans-serif',
        },
        silent: true,
      },
      {
        type: 'text',
        left: 'center',
        top: '46%',
        style: { text: t('订单总数'), fill: colors.muted, fontSize: 12 },
        silent: true,
      },
    ],
    series: [
      {
        type: 'pie',
        radius: ['52%', '70%'],
        center: ['50%', '43%'],
        stillShowZeroSum: false,
        label: { show: false },
        labelLine: { show: false },
        itemStyle: { borderRadius: 5, borderColor: colors.background, borderWidth: 3 },
        emphasis: { scaleSize: 6 },
        data,
      },
    ],
  }
}
