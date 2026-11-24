import type { EChartsOption } from 'echarts'
import type { DashboardTrendPoint, OrderStatus, OrderStatusCount } from '@/types/api'
import { ORDER_STATUS, formatMoney } from '@/utils/format'
import { formatNumber, t, useLocale } from '@/i18n'

const palette = (dark: boolean) => ({
  text: dark ? '#cfd3dc' : '#606266',
  muted: dark ? '#a3a6ad' : '#909399',
  border: dark ? '#363637' : '#ebeef5',
  background: dark ? '#1d1e1f' : '#ffffff',
  primary: dark ? '#79bbff' : '#409eff',
  green: dark ? '#5cc9b5' : '#2fb39b',
})

const compactNumber = (value: number) => {
  if (useLocale().locale.value === 'en')
    return formatNumber(value, { notation: 'compact', maximumFractionDigits: 1 })
  return Math.abs(value) >= 10000 ? `${(value / 10000).toFixed(1)}万` : formatNumber(value)
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
      axisPointer: { type: 'line' },
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
        nameTextStyle: { color: colors.muted },
        axisLabel: { color: colors.muted, formatter: compactNumber },
        splitLine: { lineStyle: { color: colors.border, type: 'dashed' } },
      },
      {
        type: 'value',
        name: t('订单量（笔）'),
        min: 0,
        minInterval: 1,
        nameTextStyle: { color: colors.muted },
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
        lineStyle: { width: 3 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: dark ? 'rgba(121,187,255,0.24)' : 'rgba(64,158,255,0.2)' },
              { offset: 1, color: 'rgba(64,158,255,0.01)' },
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
        itemStyle: { color: colors.green, opacity: 0.65, borderRadius: [4, 4, 0, 0] },
        tooltip: {
          valueFormatter: (value) => t('{count} 笔', { count: formatNumber(Number(value)) }),
        },
      },
    ],
  }
}

export function buildStatusChart(counts: OrderStatusCount[], dark: boolean): EChartsOption {
  const colors = palette(dark)
  const statusColors: Record<OrderStatus, string> = {
    PENDING_PAYMENT: colors.muted,
    PAID: '#e6a23c',
    SHIPPED: colors.primary,
    COMPLETED: colors.green,
    CANCELLED: '#f56c6c',
    REFUNDED: '#a78bfa',
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
          fill: colors.text,
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
