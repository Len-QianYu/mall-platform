<template>
  <div class="page-heading">
    <h1 class="page-title">仪表盘</h1>
  </div>

  <!-- 统计卡片 -->
  <ElRow :gutter="16">
    <ElCol v-for="card in statCards" :key="card.label" :xs="24" :sm="12" :lg="6">
      <ElCard shadow="never" class="stat-card">
        <div class="stat-icon" :class="card.tone">
          <i :class="card.icon"></i>
        </div>
        <div>
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ card.value }}</div>
        </div>
      </ElCard>
    </ElCol>
  </ElRow>

  <!-- 最近订单 -->
  <ElCard shadow="never">
    <template #header>
      <div class="card-header">
        <span>最近订单</span>
        <RouterLink :to="{ name: 'admin_orders' }" class="more-link">查看全部</RouterLink>
      </div>
    </template>

    <ElTable v-loading="loading" :data="recentOrders">
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
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ElCard,
  ElCol,
  ElMessage,
  ElRow,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, DashboardStats, Order } from '@/types/api'
import { ORDER_STATUS, formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const stats = ref<DashboardStats | null>(null)
const recentOrders = ref<Order[]>([])

// 统计还没加载出来时显示占位符
const statCards = computed(() => [
  {
    label: '用户总数',
    icon: 'bi bi-people',
    tone: 'tone-primary',
    value: stats.value?.userCount.toLocaleString() ?? '-',
  },
  {
    label: '商品总数',
    icon: 'bi bi-box-seam',
    tone: 'tone-success',
    value: stats.value?.productCount.toLocaleString() ?? '-',
  },
  {
    label: '订单总数',
    icon: 'bi bi-receipt',
    tone: 'tone-warning',
    value: stats.value?.orderCount.toLocaleString() ?? '-',
  },
  {
    label: '累计销售额',
    icon: 'bi bi-currency-yen',
    tone: 'tone-danger',
    value: formatMoney(stats.value?.salesAmount),
  },
])

const fetchDashboard = async () => {
  loading.value = true
  try {
    const { data } =
      await request.get<ApiResult<{ stats: DashboardStats; recentOrders: Order[] }>>(
        '/admin/dashboard',
      )
    if (!data.status) {
      ElMessage.error(data.payload.message || '加载仪表盘数据失败')
      return
    }
    stats.value = data.payload.stats
    recentOrders.value = data.payload.recentOrders
  } catch {
    ElMessage.error('加载仪表盘数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(fetchDashboard)
</script>

<style lang="scss" scoped>
.stat-card {
  margin-bottom: 1rem;

  :deep(.el-card__body) {
    display: flex;
    align-items: center;
    gap: 1rem;
  }
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
  font-size: 1.5rem;
  font-weight: 600;
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
</style>
