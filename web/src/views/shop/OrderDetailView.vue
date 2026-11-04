<template>
  <div class="shop-container">
    <div v-loading="loading" class="detail-body">
      <template v-if="detail">
        <div class="detail-heading">
          <h1 class="section-title"><i class="bi bi-receipt"></i>订单详情</h1>
          <ElButton link @click="router.push({ name: 'orders' })">
            <i class="bi bi-arrow-left back-icon"></i>返回我的订单
          </ElButton>
        </div>

        <!-- 进度：已取消的订单没有后续步骤，改为提示条 -->
        <ElCard shadow="never" class="detail-card">
          <ElAlert
            v-if="order.status === 'CANCELLED'"
            type="info"
            :title="`订单已于 ${formatDateTime(order.cancelledAt)} 取消`"
            :closable="false"
            show-icon
          />
          <ElSteps v-else :active="activeStep" finish-status="success" align-center>
            <ElStep title="提交订单" :description="formatDateTime(order.createdAt)" />
            <ElStep title="付款" :description="stepTime(order.paidAt)" />
            <ElStep title="发货" :description="stepTime(order.shippedAt)" />
            <ElStep title="确认收货" :description="stepTime(order.completedAt)" />
          </ElSteps>

          <div v-if="hasActions" class="detail-actions">
            <template v-if="order.status === 'PENDING_PAYMENT'">
              <ElPopconfirm title="确定取消该订单吗？" @confirm="changeStatus('cancel')">
                <template #reference>
                  <ElButton>取消订单</ElButton>
                </template>
              </ElPopconfirm>
              <ElButton
                type="primary"
                @click="router.push({ name: 'order_pay', params: { id: order.id } })"
              >
                去支付
              </ElButton>
            </template>
            <ElPopconfirm
              v-if="order.status === 'SHIPPED'"
              title="确认已收到商品吗？"
              @confirm="changeStatus('confirm')"
            >
              <template #reference>
                <ElButton type="primary">确认收货</ElButton>
              </template>
            </ElPopconfirm>
          </div>
        </ElCard>

        <ElCard shadow="never" class="detail-card">
          <ElDescriptions :column="2" border>
            <ElDescriptionsItem label="订单号">{{ order.orderNo }}</ElDescriptionsItem>
            <ElDescriptionsItem label="状态">
              <ElTag :type="ORDER_STATUS[order.status]?.type ?? 'info'">
                {{ ORDER_STATUS[order.status]?.label ?? order.status }}
              </ElTag>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="收货人">{{ order.receiverName }}</ElDescriptionsItem>
            <ElDescriptionsItem label="联系电话">{{ order.receiverPhone }}</ElDescriptionsItem>
            <ElDescriptionsItem label="收货地址" :span="2">
              {{ order.receiverAddress }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="买家备注" :span="2">
              {{ order.remark || '-' }}
            </ElDescriptionsItem>
          </ElDescriptions>
        </ElCard>

        <ElCard shadow="never" class="detail-card">
          <template #header><span class="card-title">商品明细</span></template>
          <!-- 商品名、主图、单价是下单时的快照；商品已下架时链接会显示「商品不存在或已下架」 -->
          <ElTable :data="detail.items">
            <ElTableColumn label="商品" min-width="280">
              <template #default="{ row }">
                <RouterLink
                  class="product-cell"
                  :to="{ name: 'product_detail', params: { id: row.productId } }"
                >
                  <ElImage
                    v-if="row.productImage"
                    class="product-image"
                    :src="assetUrl(row.productImage)"
                    fit="cover"
                  />
                  <div v-else class="product-image image-placeholder">
                    <i class="bi bi-image"></i>
                  </div>
                  <span class="product-name">{{ row.productName }}</span>
                </RouterLink>
              </template>
            </ElTableColumn>
            <ElTableColumn label="单价" width="120">
              <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
            </ElTableColumn>
            <ElTableColumn prop="quantity" label="数量" width="80" />
            <ElTableColumn label="小计" width="120">
              <template #default="{ row }">{{
                formatMoney(row.unitPrice * row.quantity)
              }}</template>
            </ElTableColumn>
          </ElTable>
          <div class="amount-line">
            实付金额：<span class="price total-amount">{{ formatMoney(order.totalAmount) }}</span>
          </div>
        </ElCard>
      </template>

      <ElResult v-else-if="!loading" icon="warning" title="订单不存在">
        <template #extra>
          <ElButton type="primary" @click="router.push({ name: 'orders' })">我的订单</ElButton>
        </template>
      </ElResult>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElImage,
  ElMessage,
  ElPopconfirm,
  ElResult,
  ElStep,
  ElSteps,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, Order, OrderItem, OrderStatus } from '@/types/api'
import { ORDER_STATUS, assetUrl, formatDateTime, formatMoney } from '@/utils/format'
import { changeOrderStatus, type UserOrderAction } from '@/utils/order'

// 各状态已完成的步骤数（ElSteps 的 active）：已完成时 4 步全部打勾
const STEP_BY_STATUS: Record<OrderStatus, number> = {
  PENDING_PAYMENT: 1,
  PAID: 2,
  SHIPPED: 3,
  COMPLETED: 4,
  CANCELLED: 0,
}

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const detail = ref<{ order: Order; items: OrderItem[] } | null>(null)

// 模板里只在 detail 存在时使用
const order = computed(() => detail.value!.order)
const activeStep = computed(() => STEP_BY_STATUS[order.value.status] ?? 0)
// 待支付可取消、去支付，已发货可确认收货，其余状态没有操作
const hasActions = computed(() => ['PENDING_PAYMENT', 'SHIPPED'].includes(order.value.status))

// 还没走到的步骤不显示时间
const stepTime = (value: string | null) => (value ? formatDateTime(value) : '')

const fetchDetail = async () => {
  loading.value = true
  try {
    const { data } = await request.get<ApiResult<{ order: Order; items: OrderItem[] }>>(
      `/order/${route.params.id}`,
    )
    if (data.status) {
      detail.value = { order: data.payload.order, items: data.payload.items }
    }
  } catch {
    ElMessage.error('加载订单失败')
  } finally {
    loading.value = false
  }
}

const changeStatus = async (action: UserOrderAction) => {
  if (await changeOrderStatus(order.value.id, action)) {
    fetchDetail()
  }
}

onMounted(fetchDetail)
</script>

<style lang="scss" scoped>
.detail-body {
  min-height: 300px;
}

.detail-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.back-icon {
  margin-right: 0.25rem;
}

.detail-card {
  margin-bottom: 1rem;
}

.card-title {
  font-weight: 600;
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.25rem;

  .el-button {
    margin: 0;
  }
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  text-decoration: none;
}

.product-image {
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  border-radius: 6px;
}

.product-name {
  color: var(--el-text-color-primary);

  &:hover {
    color: var(--el-color-primary);
  }
}

.amount-line {
  margin-top: 1rem;
  color: var(--el-text-color-regular);
  font-size: 0.875rem;
  text-align: right;
}

.total-amount {
  font-size: 1.375rem;
}
</style>
