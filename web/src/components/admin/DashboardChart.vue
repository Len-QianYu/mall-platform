<template>
  <div ref="container" class="dashboard-chart" role="img" :aria-label="label"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use, type ECharts } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  AriaComponent,
  GraphicComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'

// 按需加载图表和组件；SVG 在不同屏幕像素密度下都能保持清晰。
use([
  BarChart,
  LineChart,
  PieChart,
  AriaComponent,
  GraphicComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  SVGRenderer,
])

const props = defineProps<{ option: EChartsOption; label: string }>()
const container = ref<HTMLDivElement>()
let chart: ECharts | undefined
let observer: ResizeObserver | undefined

onMounted(() => {
  if (!container.value) return
  chart = init(container.value, undefined, { renderer: 'svg' })
  chart.setOption(props.option)

  // 监听容器而非窗口，侧边栏收起或展开时也能重新计算图表宽度。
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(container.value)
})

watch(
  () => props.option,
  (option) => chart?.setOption(option, { notMerge: true }),
  { deep: true, flush: 'post' },
)

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = undefined
})
</script>

<style scoped>
.dashboard-chart {
  width: 100%;
  height: 320px;
}
</style>
