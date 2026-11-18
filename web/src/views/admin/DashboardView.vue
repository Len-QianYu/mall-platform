<template>
  <section class="dashboard">
    <div class="page-heading dashboard-heading">
      <div>
        <h1 class="page-title">仪表盘</h1>
      </div>
      <div class="heading-actions">
        <span v-if="updatedAt" class="updated-at">更新于 {{ updatedAt }}</span>
        <ElButton
          class="refresh-button"
          circle
          :disabled="loading"
          :aria-busy="loading"
          aria-label="刷新数据"
          :title="loading ? '正在刷新数据' : '刷新数据'"
          @click="fetchDashboard"
        >
          <i
            class="bi bi-arrow-clockwise refresh-icon"
            :class="{ 'is-spinning': loading }"
            aria-hidden="true"
          ></i>
        </ElButton>
      </div>
    </div>

    <ElAlert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      class="dashboard-error"
    />

    <div class="stat-grid">
      <ElCard v-for="card in statCards" :key="card.label" shadow="never" class="stat-card">
        <div class="stat-icon" :class="card.tone">
          <i :class="card.icon"></i>
        </div>
        <div class="stat-content">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-description">{{ card.description }}</div>
        </div>
      </ElCard>
    </div>

    <div class="charts-grid">
      <ElCard v-loading="loading" shadow="never" class="chart-card trend-card">
        <template #header>
          <div class="card-header trend-header">
            <div>
              <span>销售与订单趋势</span>
              <p class="chart-caption">{{ dateRange }}</p>
            </div>
            <ElRadioGroup
              v-model="days"
              size="small"
              :disabled="loading"
              aria-label="趋势统计时间范围"
              @change="fetchDashboard"
            >
              <ElRadioButton :value="7">近 7 天</ElRadioButton>
              <ElRadioButton :value="30">近 30 天</ElRadioButton>
            </ElRadioGroup>
          </div>
        </template>

        <div class="trend-summary">
          <div>
            <span class="summary-label">期间销售额</span>
            <strong>{{ dashboard ? formatMoney(periodSummary.salesAmount) : '-' }}</strong>
          </div>
          <div>
            <span class="summary-label">期间新增订单</span>
            <strong>{{
              dashboard ? periodSummary.orderCount.toLocaleString('zh-CN') : '-'
            }}</strong>
          </div>
        </div>
        <DashboardChart
          v-if="dashboard"
          :option="trendOption"
          :label="`近 ${displayedDays} 天销售额和新增订单趋势`"
        />
        <ElSkeleton v-else :rows="6" animated class="chart-placeholder" />
        <p class="chart-note">销售额按支付日期统计，已退款订单不计入；订单量按下单日期统计。</p>
      </ElCard>

      <ElCard v-loading="loading" shadow="never" class="chart-card status-card">
        <template #header>
          <div class="card-header">
            <div>
              <span>订单状态分布</span>
              <p class="chart-caption">全部订单的当前状态</p>
            </div>
            <ElTag type="info" effect="plain" size="small">全部订单</ElTag>
          </div>
        </template>

        <DashboardChart
          v-if="dashboard && statusTotal > 0"
          :option="statusOption"
          label="全部订单的当前状态分布"
          class="status-chart"
        />
        <ElEmpty
          v-else-if="dashboard"
          description="暂无订单数据"
          :image-size="90"
          class="empty-chart"
        />
        <ElSkeleton v-else :rows="6" animated class="chart-placeholder" />
        <p class="chart-note">悬停查看各状态的订单数量与占比。</p>
      </ElCard>
    </div>

    <ElCard shadow="never">
      <template #header>
        <div class="card-header">
          <span>最近订单</span>
          <RouterLink :to="{ name: 'admin_orders' }" class="more-link">查看全部</RouterLink>
        </div>
      </template>

      <ElTable v-loading="loading" :data="dashboard?.recentOrders ?? []" empty-text="暂无订单">
        <ElTableColumn prop="orderNo" label="订单号" min-width="200" />
        <ElTableColumn prop="receiverName" label="收货人" min-width="100" />
        <ElTableColumn label="订单金额" min-width="110">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="100">
          <template #default="{ row }">
            <ElTag :type="ORDER_STATUS[row.status]?.type ?? 'info'">
              {{ ORDER_STATUS[row.status]?.label ?? row.status }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="下单时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </ElTableColumn>
      </ElTable>
    </ElCard>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElEmpty,
  ElRadioButton,
  ElRadioGroup,
  ElSkeleton,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
} from 'element-plus'
import request from '@/utils/request'
import DashboardChart from '@/components/admin/DashboardChart.vue'
import type { ApiResult, DashboardData } from '@/types/api'
import { buildStatusChart, buildTrendChart } from '@/utils/dashboardCharts'
import { ORDER_STATUS, formatDateTime, formatMoney } from '@/utils/format'
import { useTheme } from '@/utils/useTheme'

const loading = ref(false)
const dashboard = ref<DashboardData | null>(null)
const stats = computed(() => dashboard.value?.stats)
const days = ref(30)
const displayedDays = ref(30)
const updatedAt = ref('')
const error = ref('')
const { isDark } = useTheme()
let requestId = 0

const trendOption = computed(() => buildTrendChart(dashboard.value?.trend ?? [], isDark.value))
const statusOption = computed(() =>
  buildStatusChart(dashboard.value?.orderStatuses ?? [], isDark.value),
)
const statusTotal = computed(() =>
  (dashboard.value?.orderStatuses ?? []).reduce((total, item) => total + item.count, 0),
)
const periodSummary = computed(() => {
  const trend = dashboard.value?.trend ?? []
  return {
    salesAmount:
      trend.reduce((total, point) => total + Math.round(point.salesAmount * 100), 0) / 100,
    orderCount: trend.reduce((total, point) => total + point.orderCount, 0),
  }
})
const dateRange = computed(() => {
  const trend = dashboard.value?.trend ?? []
  const start = trend[0]?.date
  const end = trend[trend.length - 1]?.date
  return start && end ? `${start} — ${end}` : `最近 ${displayedDays.value} 天（含今天）`
})

// 统计还没加载出来时显示占位符
const statCards = computed(() => [
  {
    label: '用户总数',
    icon: 'bi bi-people',
    tone: 'tone-primary',
    value: stats.value?.userCount.toLocaleString() ?? '-',
    description: '全部注册账号',
  },
  {
    label: '商品总数',
    icon: 'bi bi-box-seam',
    tone: 'tone-success',
    value: stats.value?.productCount.toLocaleString() ?? '-',
    description: '含上架和下架商品',
  },
  {
    label: '订单总数',
    icon: 'bi bi-receipt',
    tone: 'tone-warning',
    value: stats.value?.orderCount.toLocaleString() ?? '-',
    description: '包含所有订单状态',
  },
  {
    label: '累计销售额',
    icon: 'bi bi-currency-yen',
    tone: 'tone-danger',
    value: formatMoney(stats.value?.salesAmount),
    description: '已支付且未退款的订单',
  },
])

const fetchDashboard = async () => {
  const currentRequest = ++requestId
  const requestedDays = days.value
  loading.value = true
  error.value = ''
  try {
    const { data } = await request.get<ApiResult<DashboardData>>('/admin/dashboard', {
      params: { days: requestedDays },
    })
    if (currentRequest !== requestId) return
    if (!data.status) {
      error.value = data.payload.message || '加载仪表盘数据失败，请稍后重试'
      days.value = displayedDays.value
      return
    }
    dashboard.value = data.payload
    displayedDays.value = requestedDays
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch {
    if (currentRequest === requestId) {
      error.value = '加载仪表盘数据失败，请稍后重试'
      days.value = displayedDays.value
    }
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}

onMounted(fetchDashboard)
onBeforeUnmount(() => requestId++)
</script>

<style lang="scss" scoped>
.page-description,
.chart-caption,
.chart-note,
.updated-at,
.stat-description {
  color: var(--el-text-color-secondary);
  font-size: 0.75rem;
}

.page-description {
  margin: 0.5rem 0 0;
  font-size: 0.875rem;
}

.heading-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.refresh-button {
  width: 36px;
  height: 36px;
  font-size: 1.125rem;
}

.refresh-icon {
  display: inline-block;

  &.is-spinning {
    animation: dashboard-refresh 0.8s linear infinite;
  }
}

@keyframes dashboard-refresh {
  to {
    transform: rotate(360deg);
  }
}

.dashboard-error {
  margin-bottom: 1rem;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
  margin-bottom: 1rem;
}

.stat-card {
  :deep(.el-card__body) {
    display: flex;
    align-items: center;
    gap: 1rem;
  }
}

.stat-content {
  min-width: 0;
}

.stat-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  border-radius: 12px;
  font-size: 1.5rem;

  &.tone-primary {
    background-color: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }

  &.tone-success {
    background-color: var(--el-color-success-light-9);
    color: var(--el-color-success);
  }

  &.tone-warning {
    background-color: var(--el-color-warning-light-9);
    color: var(--el-color-warning);
  }

  &.tone-danger {
    background-color: var(--el-color-danger-light-9);
    color: var(--el-color-danger);
  }
}

.stat-label {
  color: var(--el-text-color-secondary);
  font-size: 0.875rem;
}

.stat-value {
  margin-top: 0.25rem;
  color: var(--el-text-color-primary);
  font-size: clamp(1.25rem, 1.7vw, 1.75rem);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.stat-description {
  margin-top: 0.375rem;
}

.charts-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
  gap: 1rem;
  margin-bottom: 1rem;
}

.chart-card {
  min-width: 0;
}

.chart-caption {
  margin: 0.5rem 0 0;
  font-weight: normal;
}

.trend-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem 2rem;
  margin-bottom: 1rem;

  strong {
    display: block;
    margin-top: 0.375rem;
    color: var(--el-text-color-primary);
    font-size: 1.375rem;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }
}

.summary-label {
  color: var(--el-text-color-secondary);
  font-size: 0.75rem;
}

.status-chart {
  max-width: 420px;
  margin: 1rem auto 0;
}

.empty-chart,
.chart-placeholder {
  box-sizing: border-box;
  min-height: 320px;
}

.chart-placeholder {
  padding-top: 1rem;
}

.chart-note {
  margin: 1rem 0 0;
  line-height: 1.6;
}

.status-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 418px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.more-link {
  color: var(--el-color-primary);
  font-size: 0.875rem;
  font-weight: normal;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}

@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .charts-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .status-card :deep(.el-card__body) {
    min-height: auto;
  }
}

@media (max-width: 600px) {
  .dashboard-heading,
  .trend-header {
    flex-wrap: wrap;
    gap: 1rem;
  }

  .stat-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .updated-at {
    display: none;
  }
}
</style>
