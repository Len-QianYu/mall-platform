<template>
  <div class="shop-container pay-container">
    <div v-loading="loading" class="pay-body">
      <template v-if="order">
        <ElCard v-if="order.status === 'PENDING_PAYMENT'" shadow="never">
          <div class="pay-summary">
            <i class="bi bi-check-circle-fill summary-icon"></i>
            <div>
              <div class="summary-title">订单提交成功，请尽快支付</div>
              <div class="text-secondary">订单号：{{ order.orderNo }}</div>
            </div>
            <div class="summary-amount">
              <span class="text-secondary">应付金额</span>
              <span class="price amount">{{ formatMoney(order.totalAmount) }}</span>
            </div>
          </div>

          <div class="receiver-line text-secondary">
            收货信息：{{ order.receiverName }}，{{ order.receiverPhone }}，{{
              order.receiverAddress
            }}
          </div>

          <h3 class="method-title">选择支付方式</h3>
          <ElRadioGroup v-model="method" class="method-list">
            <ElRadio
              v-for="option in METHOD_OPTIONS"
              :key="option.value"
              :value="option.value"
              :disabled="option.disabled"
              border
              class="method-item"
            >
              <i :class="option.icon" class="method-icon"></i>
              {{ PAYMENT_METHOD[option.value] }}
              <span v-if="option.disabled" class="text-secondary">（暂未接入）</span>
            </ElRadio>
          </ElRadioGroup>

          <div class="pay-actions">
            <ElButton size="large" @click="router.push({ name: 'orders' })">稍后支付</ElButton>
            <ElButton type="primary" size="large" :loading="paying" @click="pay">
              确认支付 {{ formatMoney(order.totalAmount) }}
            </ElButton>
          </div>
        </ElCard>

        <!-- 已支付、已取消等状态的订单不能再付款（如支付页被收藏或从历史记录打开） -->
        <ElResult
          v-else
          icon="info"
          :title="`订单${ORDER_STATUS[order.status]?.label ?? order.status}`"
          sub-title="该订单当前无需支付"
        >
          <template #extra>
            <ElButton
              type="primary"
              @click="router.replace({ name: 'order_detail', params: { id: order.id } })"
            >
              查看订单
            </ElButton>
          </template>
        </ElResult>
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
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElButton,
  ElCard,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElResult,
  vLoading,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, Order, PaymentMethod } from '@/types/api'
import { ORDER_STATUS, PAYMENT_METHOD, formatMoney } from '@/utils/format'

// 目前只有模拟支付可用；支付宝、微信先展示出来，接入后去掉 disabled
const METHOD_OPTIONS: { value: PaymentMethod; icon: string; disabled: boolean }[] = [
  { value: 'MOCK', icon: 'bi bi-lightning-charge', disabled: false },
  { value: 'ALIPAY', icon: 'bi bi-alipay', disabled: true },
  { value: 'WECHAT', icon: 'bi bi-wechat', disabled: true },
]

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const order = ref<Order | null>(null)
const method = ref<PaymentMethod>('MOCK')

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await request.get<ApiResult<{ order: Order }>>(`/order/${route.params.id}`)
    if (data.status) {
      order.value = data.payload.order
    }
  } catch {
    ElMessage.error('加载订单失败')
  } finally {
    loading.value = false
  }
})

const paying = ref(false)

const pay = async () => {
  if (!order.value) return

  paying.value = true
  try {
    const { data } = await request.post<ApiResult>('/payment/pay', {
      orderId: order.value.id,
      method: method.value,
    })
    if (!data.status) {
      ElMessage.error(data.payload.message || '支付失败')
      return
    }
    ElMessage.success('支付成功')
    router.replace({ name: 'order_detail', params: { id: order.value.id } })
  } catch {
    ElMessage.error('支付失败')
  } finally {
    paying.value = false
  }
}
</script>

<style lang="scss" scoped>
.pay-container {
  max-width: 800px;
}

.pay-body {
  min-height: 300px;
}

.pay-summary {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding-bottom: 1.25rem;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.summary-icon {
  color: var(--el-color-success);
  font-size: 2.5rem;
}

.summary-title {
  margin-bottom: 0.25rem;
  color: var(--el-text-color-primary);
  font-size: 1.125rem;
  font-weight: 600;
}

.summary-amount {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-left: auto;

  .amount {
    font-size: 1.75rem;
  }
}

.receiver-line {
  margin-top: 1rem;
}

.method-title {
  margin: 1.5rem 0 0.75rem;
  color: var(--el-text-color-primary);
  font-size: 1rem;
  font-weight: 600;
}

.method-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;

  // margin 抵消 .el-radio 的右外边距，间距统一由 gap 控制
  .method-item {
    margin: 0;
  }
}

.method-icon {
  margin-right: 0.25rem;
}

.pay-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 2rem;

  .el-button {
    margin: 0;
  }
}
</style>
