<template>
  <div class="shop-container">
    <h1 class="section-title"><i class="bi bi-receipt"></i>我的订单</h1>

    <ElTabs v-model="activeTab" class="status-tabs">
      <ElTabPane v-for="tab in TABS" :key="tab.name" :label="tab.label" :name="tab.name" />
    </ElTabs>

    <div v-loading="loading" class="orders-body">
      <template v-if="orders.length">
        <ElCard v-for="order in orders" :key="order.id" shadow="never" class="order-card">
          <template #header>
            <div class="order-header">
              <span class="text-secondary">{{ formatDateTime(order.createdAt) }}</span>
              <span class="text-secondary">订单号：{{ order.orderNo }}</span>
              <ElTag :type="ORDER_STATUS[order.status]?.type ?? 'info'" class="order-status">
                {{ ORDER_STATUS[order.status]?.label ?? order.status }}
              </ElTag>
            </div>
          </template>

          <div class="order-content">
            <!-- 商品名、主图、单价是下单时的快照 -->
            <div class="order-items">
              <div v-for="item in order.items" :key="item.id" class="order-item">
                <ElImage
                  v-if="item.productImage"
                  class="product-image"
                  :src="item.productImage"
                  fit="cover"
                />
                <div v-else class="product-image image-placeholder">
                  <i class="bi bi-image"></i>
                </div>
                <span class="item-name">{{ item.productName }}</span>
                <span class="text-secondary">
                  {{ formatMoney(item.unitPrice) }} × {{ item.quantity }}
                </span>
              </div>
            </div>

            <div class="order-side">
              <div>
                实付 <span class="price order-amount">{{ formatMoney(order.totalAmount) }}</span>
              </div>
              <div class="order-actions">
                <template v-if="order.status === 'PENDING_PAYMENT'">
                  <ElButton
                    type="primary"
                    size="small"
                    @click="router.push({ name: 'order_pay', params: { id: order.id } })"
                  >
                    去支付
                  </ElButton>
                  <ElPopconfirm
                    title="确定取消该订单吗？"
                    @confirm="changeStatus(order.id, 'cancel')"
                  >
                    <template #reference>
                      <ElButton size="small">取消订单</ElButton>
                    </template>
                  </ElPopconfirm>
                </template>
                <ElPopconfirm
                  v-if="order.status === 'SHIPPED'"
                  title="确认已收到商品吗？"
                  @confirm="changeStatus(order.id, 'confirm')"
                >
                  <template #reference>
                    <ElButton type="primary" size="small">确认收货</ElButton>
                  </template>
                </ElPopconfirm>
                <ElButton
                  link
                  type="primary"
                  size="small"
                  @click="router.push({ name: 'order_detail', params: { id: order.id } })"
                >
                  订单详情
                </ElButton>
              </div>
            </div>
          </div>
        </ElCard>
      </template>

      <ElEmpty v-else-if="!loading" description="暂无相关订单">
        <ElButton type="primary" @click="router.push({ name: 'products' })">去逛逛</ElButton>
      </ElEmpty>
    </div>

    <ElPagination
      v-if="pagination.total > pagination.pageSize"
      v-model:current-page="pagination.currentPage"
      class="page-pagination"
      :page-size="pagination.pageSize"
      :total="pagination.total"
      layout="total, prev, pager, next"
      background
    />
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElCard,
  ElEmpty,
  ElImage,
  ElMessage,
  ElPagination,
  ElPopconfirm,
  ElTabPane,
  ElTabs,
  ElTag,
  vLoading,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, OrderStatus, OrderWithItems, Pagination } from '@/types/api'
import { ORDER_STATUS, formatDateTime, formatMoney } from '@/utils/format'
import { changeOrderStatus, type UserOrderAction } from '@/utils/order'

// 标签页用买家视角的叫法：已支付即「待发货」，已发货即「待收货」
const TABS: { name: OrderStatus | 'ALL'; label: string }[] = [
  { name: 'ALL', label: '全部' },
  { name: 'PENDING_PAYMENT', label: '待支付' },
  { name: 'PAID', label: '待发货' },
  { name: 'SHIPPED', label: '待收货' },
  { name: 'COMPLETED', label: '已完成' },
  { name: 'CANCELLED', label: '已取消' },
]

const router = useRouter()

const activeTab = ref<OrderStatus | 'ALL'>('ALL')
const loading = ref(false)
const orders = ref<OrderWithItems[]>([])

const pagination = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0,
})

const fetchOrders = async () => {
  loading.value = true
  try {
    const { data } = await request.get<
      ApiResult<{ orders: OrderWithItems[]; pagination: Pagination }>
    >('/order', {
      params: {
        page: pagination.currentPage,
        pageSize: pagination.pageSize,
        status: activeTab.value === 'ALL' ? undefined : activeTab.value,
      },
    })
    if (!data.status) {
      ElMessage.error(data.payload.message || '加载订单失败')
      return
    }
    orders.value = data.payload.orders
    pagination.total = data.payload.pagination.total
  } catch {
    ElMessage.error('加载订单失败')
  } finally {
    loading.value = false
  }
}

watch(() => pagination.currentPage, fetchOrders, { immediate: true })

// 切换标签从第一页查；本来就在第一页时上面的 watch 不会触发，直接查
watch(activeTab, () => {
  if (pagination.currentPage === 1) {
    fetchOrders()
  } else {
    pagination.currentPage = 1
  }
})

const changeStatus = async (id: number, action: UserOrderAction) => {
  if (await changeOrderStatus(id, action)) {
    fetchOrders()
  }
}
</script>

<style lang="scss" scoped>
.status-tabs {
  margin-bottom: 0.5rem;
}

.orders-body {
  min-height: 300px;
}

.order-card {
  margin-bottom: 1rem;

  :deep(.el-card__header) {
    padding: 0.625rem 1.25rem;
    background-color: var(--el-fill-color-lighter);
  }
}

.order-header {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.order-status {
  margin-left: auto;
}

.order-content {
  display: flex;
  gap: 1.5rem;
}

.order-items {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.75rem;
  min-width: 0;
}

.order-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.product-image {
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  border-radius: 6px;
}

.item-name {
  flex: 1;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 0.875rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-side {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 0.75rem;
  padding-left: 1.5rem;
  border-left: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-regular);
  font-size: 0.875rem;
}

.order-amount {
  font-size: 1.125rem;
}

.order-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;

  .el-button {
    margin: 0;
  }
}

@media (max-width: 768px) {
  .order-content {
    flex-direction: column;
  }

  .order-side {
    padding: 0.75rem 0 0;
    border-top: 1px solid var(--el-border-color-lighter);
    border-left: none;
  }
}
</style>
