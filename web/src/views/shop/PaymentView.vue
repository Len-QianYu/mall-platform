<template>
  <div class="shop-container pay-container">
    <div v-loading="loading" class="pay-body">
      <template v-if="order">
        <ElCard v-if="order.status === 'PENDING_PAYMENT'" shadow="never">
          <div class="pay-summary">
            <i class="bi bi-check-circle-fill summary-icon"></i>
            <div>
              <div class="summary-title">{{ t('订单提交成功，请尽快支付') }}</div>
              <div class="text-secondary">{{ t('订单号：') }}{{ order.orderNo }}</div>
            </div>
            <div class="summary-amount">
              <span class="text-secondary">{{ t('应付金额') }}</span>
              <span class="price amount">{{ formatMoney(order.totalAmount) }}</span>
            </div>
          </div>

          <div class="receiver-line text-secondary">
            {{ t('收货信息：') }}{{ order.receiverName }}，{{ order.receiverPhone }}，{{
              order.receiverAddress
            }}
          </div>

          <h3 class="method-title">{{ t('选择支付方式') }}</h3>
          <ElRadioGroup v-model="method" class="method-list" :disabled="paying || querying">
            <ElRadio
              v-for="option in METHOD_OPTIONS"
              :key="option.value"
              :value="option.value"
              :disabled="methodDisabled(option.value)"
              border
              class="method-item"
            >
              <i :class="option.icon" class="method-icon"></i>
              {{
                t(
                  option.value === 'ALIPAY' && alipaySandbox
                    ? '支付宝（沙箱）'
                    : PAYMENT_METHOD[option.value],
                )
              }}
              <span v-if="methodDisabled(option.value)" class="text-secondary">
                {{ option.value === 'WECHAT' ? t('（暂未接入）') : t('（暂不可用）') }}
              </span>
            </ElRadio>
          </ElRadioGroup>
          <p v-if="method === 'ALIPAY' && alipaySandbox" class="payment-hint text-secondary">
            {{ t('请使用支付宝沙箱买家账号付款。') }}
          </p>
          <ElAlert
            v-if="pendingAlipay"
            class="payment-notice"
            type="info"
            :title="t('已发起支付宝支付。完成付款后可查询支付结果，或继续前往收银台。')"
            :closable="false"
            show-icon
          />
          <p v-if="!enabledMethods.length" class="payment-hint">
            {{ t('暂无可用支付方式，请稍后重试。') }}
          </p>

          <div class="pay-actions">
            <ElButton size="large" @click="router.push({ name: 'orders' })">{{
              t('稍后支付')
            }}</ElButton>
            <ElButton
              v-if="pendingAlipay"
              class="query-payment"
              size="large"
              :loading="querying"
              :disabled="paying"
              @click="queryPayment"
            >
              {{ t('查询支付结果') }}
            </ElButton>
            <ElButton
              class="confirm-payment"
              type="primary"
              size="large"
              :loading="paying"
              :disabled="querying || methodDisabled(method)"
              @click="pay"
            >
              {{ method === 'ALIPAY' ? t('前往支付宝支付') : t('确认支付') }}
              {{ formatMoney(order.totalAmount) }}
            </ElButton>
          </div>
        </ElCard>

        <ElResult
          v-else
          :icon="isPaid ? 'success' : 'info'"
          :title="
            t('订单{status}', { status: t(ORDER_STATUS[order.status]?.label ?? order.status) })
          "
          :sub-title="t('该订单当前无需支付')"
        >
          <template #extra>
            <ElButton
              type="primary"
              @click="router.replace({ name: 'order_detail', params: { id: order.id } })"
            >
              {{ t('查看订单') }}
            </ElButton>
          </template>
        </ElResult>
      </template>

      <ElResult v-else-if="!loading" icon="warning" :title="t(loadError || '订单不存在')">
        <template #extra>
          <ElButton v-if="orderId" class="load-retry" @click="load">{{ t('重试') }}</ElButton>
          <ElButton type="primary" @click="router.push({ name: 'orders' })">{{
            t('我的订单')
          }}</ElButton>
        </template>
      </ElResult>
    </div>
  </div>
</template>

<script setup lang="ts">
import { t } from '@/i18n'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElResult,
  vLoading,
} from 'element-plus'
import request from '@/utils/request'
import type {
  ApiResult,
  Order,
  Payment,
  PaymentMethod,
  PaymentMethodsResult,
  PaymentQueryResult,
  PaymentStartResult,
} from '@/types/api'
import { ORDER_STATUS, PAYMENT_METHOD, formatMoney } from '@/utils/format'

const METHOD_OPTIONS: { value: PaymentMethod; icon: string }[] = [
  { value: 'ALIPAY', icon: 'bi bi-alipay' },
  { value: 'MOCK', icon: 'bi bi-lightning-charge' },
  { value: 'WECHAT', icon: 'bi bi-wechat' },
]

const route = useRoute()
const router = useRouter()
const orderId = computed(() => {
  const id = route.params.id || route.query.orderId
  return typeof id === 'string' && /^[1-9]\d*$/.test(id) ? id : ''
})

const loading = ref(false)
const loadError = ref('')
const order = ref<Order | null>(null)
const payment = ref<Payment | null>(null)
const enabledMethods = ref<PaymentMethod[]>([])
const alipaySandbox = ref(false)
const method = ref<PaymentMethod>('ALIPAY')
const paying = ref(false)
const querying = ref(false)
const pendingAlipay = computed(
  () => payment.value?.method === 'ALIPAY' && payment.value.status === 'PENDING',
)
const isPaid = computed(() =>
  Boolean(order.value && ['PAID', 'SHIPPED', 'COMPLETED'].includes(order.value.status)),
)
const methodDisabled = (value: PaymentMethod) =>
  !enabledMethods.value.includes(value) || (pendingAlipay.value && value !== 'ALIPAY')

const showOrder = () => {
  if (order.value) {
    router.replace({ name: 'order_detail', params: { id: order.value.id } })
  }
}

const load = async () => {
  const id = orderId.value
  order.value = null
  payment.value = null
  loadError.value = ''
  if (!id) return

  loading.value = true
  try {
    const [methodsResponse, queryResponse] = await Promise.all([
      request.get<ApiResult<PaymentMethodsResult>>('/payment/methods'),
      request.get<ApiResult<PaymentQueryResult>>('/payment/query', { params: { orderId: id } }),
    ])
    // 路由变化时丢弃上一个订单的请求结果。
    if (id !== orderId.value) return
    if (!methodsResponse.data.status) {
      loadError.value = methodsResponse.data.payload.message || '加载支付方式失败'
      return
    }
    if (!queryResponse.data.status) {
      loadError.value = queryResponse.data.payload.message || '加载订单失败'
      return
    }

    enabledMethods.value = methodsResponse.data.payload.methods
    alipaySandbox.value = methodsResponse.data.payload.alipaySandbox
    order.value = queryResponse.data.payload.order
    payment.value = queryResponse.data.payload.payment
    method.value = pendingAlipay.value ? 'ALIPAY' : (enabledMethods.value[0] ?? 'ALIPAY')
    // 同步返回的 URL 参数不代表支付成功，只有后端查询确认后才展示成功。
    if (route.name === 'order_pay_return' && queryResponse.data.payload.paid) {
      ElMessage.success(t('支付成功'))
      showOrder()
    }
  } catch {
    if (id === orderId.value) loadError.value = '加载订单或支付方式失败，请重试'
  } finally {
    if (id === orderId.value) loading.value = false
  }
}

watch(orderId, load, { immediate: true })

const queryPayment = async () => {
  if (!order.value || paying.value || querying.value) return
  const id = order.value.id
  querying.value = true
  try {
    const { data } = await request.get<ApiResult<PaymentQueryResult>>('/payment/query', {
      params: { orderId: id },
    })
    if (order.value?.id !== id) return
    if (!data.status) {
      ElMessage.error(t(data.payload.message || '查询支付结果失败'))
      return
    }
    order.value = data.payload.order
    payment.value = data.payload.payment
    if (data.payload.paid) {
      ElMessage.success(t('支付成功'))
      showOrder()
    } else if (order.value.status === 'PENDING_PAYMENT') {
      ElMessage.warning(t('尚未查询到支付成功，请完成付款后重试'))
    }
  } catch {
    ElMessage.error(t('查询支付结果失败，请稍后重试'))
  } finally {
    querying.value = false
  }
}

const pay = async () => {
  if (!order.value || paying.value || querying.value || methodDisabled(method.value)) return
  const id = order.value.id
  paying.value = true
  try {
    const { data } = await request.post<ApiResult<PaymentStartResult>>('/payment/pay', {
      orderId: id,
      method: method.value,
    })
    if (order.value?.id !== id) return
    if (!data.status) {
      ElMessage.error(t(data.payload.message || '支付失败'))
      return
    }
    payment.value = data.payload.payment
    if (payment.value.status === 'SUCCESS') {
      ElMessage.success(t('支付成功'))
      showOrder()
    } else if (payment.value.method === 'ALIPAY' && data.payload.payUrl) {
      // 同一标签页打开，付款后由 return-url 回到当前商城；不依赖弹窗权限。
      window.open(data.payload.payUrl, '_self')
    } else {
      ElMessage.error(t('获取支付链接失败，请重试'))
    }
  } catch {
    ElMessage.error(t('发起支付失败，请稍后重试'))
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

  .method-item {
    margin: 0;
  }
}

.method-icon {
  margin-right: 0.25rem;
}

.payment-hint,
.payment-notice {
  margin-top: 1rem;
}

.pay-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 2rem;

  .el-button {
    margin: 0;
  }
}

@media (max-width: 576px) {
  .pay-summary {
    flex-wrap: wrap;
  }

  .summary-amount {
    width: 100%;
  }
}
</style>
