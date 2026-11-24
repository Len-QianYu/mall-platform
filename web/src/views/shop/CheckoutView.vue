<template>
  <div class="shop-container">
    <div class="checkout-heading">
      <h1 class="section-title"><i class="bi bi-bag-check"></i>{{ t('确认订单') }}</h1>
      <ElButton class="checkout-back" :disabled="submitting" @click="returnToSource">
        <i class="bi bi-arrow-left back-icon" aria-hidden="true"></i>{{ t('返回商品详情页面') }}
      </ElButton>
    </div>

    <div v-loading="loading" class="checkout-body">
      <template v-if="items.length">
        <ElCard shadow="never" class="checkout-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">{{ t('收货信息') }}</span>
              <RouterLink
                :to="{ name: 'profile', query: { redirect: route.fullPath } }"
                class="address-link"
              >
                {{ t('管理收货地址') }}
              </RouterLink>
            </div>
          </template>
          <div v-if="shippingAddress" class="saved-address">
            <p class="receiver-contact">
              <strong>{{ shippingAddress.receiverName || t('收货人未填写') }}</strong>
              <span>{{ shippingAddress.receiverPhone || t('手机号未填写') }}</span>
            </p>
            <p class="receiver-address">
              {{ shippingAddress.receiverAddress || t('尚未设置收货地址') }}
            </p>
          </div>
          <div v-if="addressError" class="address-error" role="alert">
            <span>{{ t(addressError) }}</span>
            <ElButton :loading="addressLoading" @click="fetchShippingAddress">{{
              t('重试')
            }}</ElButton>
          </div>
          <p v-else-if="!addressLoading && !hasShippingAddress" class="address-hint">
            {{ t('请先在个人中心完善名字、手机号和收货地址。') }}
          </p>
          <ElForm :label-width="formLabelWidth('80px')" class="remark-form">
            <ElFormItem :label="t('备注')">
              <ElInput
                v-model="remark"
                maxlength="255"
                show-word-limit
                :placeholder="t('选填，给商家的留言')"
              />
            </ElFormItem>
          </ElForm>
        </ElCard>

        <ElCard shadow="never" class="checkout-card">
          <template #header>
            <span class="card-title">{{
              seckillActivity ? t('秒杀商品清单') : t('商品清单')
            }}</span>
          </template>
          <p v-if="seckillActivity" class="seckill-checkout-hint">
            {{ seckillActivity.title }} {{ t('· 本次按秒杀价结算，每人限购') }}
            {{ seckillActivity.limitPerUser }} {{ t('件。') }}
          </p>
          <ElTable :data="items">
            <ElTableColumn :label="t('商品')" min-width="280">
              <template #default="{ row }">
                <div class="product-cell">
                  <ElImage
                    v-if="row.image"
                    class="product-image"
                    :src="assetUrl(row.image)"
                    fit="cover"
                  />
                  <div v-else class="product-image image-placeholder">
                    <i class="bi bi-image"></i>
                  </div>
                  <span>{{ row.name }}</span>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn :label="t('单价')" width="120">
              <template #default="{ row }">{{ formatMoney(row.price) }}</template>
            </ElTableColumn>
            <ElTableColumn prop="quantity" :label="t('数量')" width="80" />
            <ElTableColumn :label="t('小计')" width="120">
              <template #default="{ row }">{{ formatMoney(row.price * row.quantity) }}</template>
            </ElTableColumn>
          </ElTable>
        </ElCard>

        <div class="submit-bar">
          <span>
            {{ t('共') }} {{ totalQuantity }} {{ t('件，应付：') }}
            <span class="price total-amount">{{ formatMoney(totalAmount) }}</span>
          </span>
          <ElButton
            class="submit-button"
            type="primary"
            size="large"
            :loading="submitting"
            :disabled="loading || addressLoading || !hasShippingAddress"
            @click="submit"
          >
            {{ t('提交订单') }}
          </ElButton>
        </div>
      </template>

      <ElEmpty v-else-if="!loading" :description="t('没有可结算的商品')">
        <ElButton
          type="primary"
          @click="router.push({ name: seckillActivityId ? 'seckill' : 'cart' })"
        >
          {{ seckillActivityId ? t('返回秒杀活动') : t('返回购物车') }}
        </ElButton>
      </ElEmpty>
    </div>
  </div>
</template>

<script setup lang="ts">
import { t, formLabelWidth } from '@/i18n'
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  ElButton,
  ElCard,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElTable,
  ElTableColumn,
  vLoading,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, CartItem, Order, Product, SeckillActivity, User } from '@/types/api'
import { assetUrl, formatMoney } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import { isSettleable, useCartStore } from '@/stores/cart'

// 结算页统一的商品行：购物车和立即购买两种来源都转成这个结构
interface CheckoutItem {
  productId: number
  name: string
  image: string | null
  price: number
  quantity: number
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

// 地址栏带 productId 是立即购买，否则结算购物车里勾选的商品
const buyNowProductId = typeof route.query.productId === 'string' ? route.query.productId : ''
const seckillActivityId =
  typeof route.query.seckillActivityId === 'string' ? route.query.seckillActivityId : ''
const buyNowQuantity = Math.max(Number(route.query.quantity) || 1, 1)
const seckillActivity = ref<SeckillActivity>()

const returnToSource = () => {
  const destination = seckillActivityId
    ? { name: 'seckill' }
    : buyNowProductId
      ? { name: 'product_detail', params: { id: buyNowProductId } }
      : { name: 'cart' }
  const previous = router.options.history.state.back
  if (typeof previous === 'string' && router.resolve(previous).name === destination.name) {
    router.back()
    return
  }
  void router.push(destination)
}

const loading = ref(false)
const items = ref<CheckoutItem[]>([])
const shippingAddress = ref<Pick<
  Order,
  'receiverName' | 'receiverPhone' | 'receiverAddress'
> | null>(null)
const addressLoading = ref(false)
const addressError = ref('')
const hasShippingAddress = computed(() => {
  const address = shippingAddress.value
  return Boolean(
    address &&
    address.receiverName &&
    /^1[3-9]\d{9}$/.test(address.receiverPhone) &&
    address.receiverAddress,
  )
})
const remark = ref('')

// 页面上的金额只是预览，订单金额以服务端按当前价格计算的为准
const totalAmount = computed(() =>
  items.value.reduce((sum, item) => sum + item.price * item.quantity, 0),
)
const totalQuantity = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))

const fetchBuyNowItem = async () => {
  const { data } = await request.get<ApiResult<{ product: Product }>>(`/product/${buyNowProductId}`)
  if (!data.status) {
    ElMessage.error(t(data.payload.message || '商品不存在或已下架'))
    return
  }
  const product = data.payload.product
  if (product.stock < buyNowQuantity) {
    ElMessage.error(t('库存不足'))
    return
  }
  items.value = [
    {
      productId: product.id,
      name: product.name,
      image: product.mainImage,
      price: product.price,
      quantity: buyNowQuantity,
    },
  ]
}

const fetchSeckillItem = async () => {
  if (
    !/^[1-9]\d*$/.test(seckillActivityId) ||
    !Number.isSafeInteger(Number(seckillActivityId)) ||
    !Number.isInteger(buyNowQuantity)
  ) {
    ElMessage.error(t('秒杀活动或购买数量不正确'))
    return
  }
  const { data } = await request.get<ApiResult<{ activity: SeckillActivity }>>(
    '/seckill/' + seckillActivityId,
  )
  if (!data.status) {
    ElMessage.error(t(data.payload.message || '秒杀活动不存在'))
    return
  }
  const activity = data.payload.activity
  if (buyNowQuantity > activity.limitPerUser) {
    ElMessage.error(t('超过本场活动的限购数量'))
    return
  }
  // 即使配额刚好售罄，也允许携带原请求号确认丢失响应的订单；新下单由服务端校验。
  seckillActivity.value = activity
  items.value = [
    {
      productId: activity.productId,
      name: activity.productName,
      image: activity.productImage,
      price: activity.seckillPrice,
      quantity: buyNowQuantity,
    },
  ]
}

const fetchCartItems = async () => {
  const { data } = await request.get<ApiResult<{ items: CartItem[] }>>('/cart')
  if (!data.status) {
    ElMessage.error(t(data.payload.message || '加载购物车失败'))
    return
  }
  items.value = data.payload.items.filter(isSettleable).map((item) => ({
    productId: item.productId,
    name: item.productName,
    image: item.productImage,
    price: item.price,
    quantity: item.quantity,
  }))
}

// 进入下单页时读取最新资料，避免使用登录缓存中的旧地址
const fetchShippingAddress = async () => {
  if (addressLoading.value) return
  addressLoading.value = true
  addressError.value = ''
  shippingAddress.value = null
  try {
    const { data } = await request.get<ApiResult<{ user: User }>>('/user/profile')
    if (!data.status) {
      addressError.value = data.payload.message || '加载收货地址失败，请重试'
      return
    }
    const user = data.payload.user
    shippingAddress.value = {
      receiverName: user.name?.trim() ?? '',
      receiverPhone: user.phone?.trim() ?? '',
      receiverAddress: user.receiverAddress?.trim() ?? '',
    }
    userStore.updateUser({
      name: user.name,
      phone: user.phone,
      receiverAddress: user.receiverAddress,
    })
  } catch {
    addressError.value = '加载收货地址失败，请重试'
  } finally {
    addressLoading.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await Promise.all([
      seckillActivityId
        ? fetchSeckillItem()
        : buyNowProductId
          ? fetchBuyNowItem()
          : fetchCartItems(),
      fetchShippingAddress(),
    ])
  } catch {
    ElMessage.error(t('加载商品失败'))
  } finally {
    loading.value = false
  }
})

const submitting = ref(false)

// 同一结算内容的失败重试、刷新后重试复用请求号；成功跳转后清除，下次购买生成新号。
const submissionStorageKey = `mall:checkout:${userStore.user?.user.id ?? userStore.name}:${route.fullPath}`
let pendingSubmission: { content: string; requestId: string } | null = null

const requestIdFor = (content: string) => {
  if (pendingSubmission?.content === content) return pendingSubmission.requestId
  try {
    const stored = sessionStorage.getItem(submissionStorageKey)
    const saved = stored
      ? (JSON.parse(stored) as Partial<NonNullable<typeof pendingSubmission>>)
      : null
    if (
      saved?.content === content &&
      typeof saved.requestId === 'string' &&
      /^[A-Za-z0-9_-]{16,64}$/.test(saved.requestId)
    ) {
      pendingSubmission = { content, requestId: saved.requestId }
      return saved.requestId
    }
  } catch {
    // 浏览器禁用存储时仍在当前页面复用请求号。
  }
  pendingSubmission = { content, requestId: crypto.randomUUID() }
  try {
    sessionStorage.setItem(submissionStorageKey, JSON.stringify(pendingSubmission))
  } catch {
    // 当前页面的内存状态仍可保护失败重试。
  }
  return pendingSubmission.requestId
}

const submit = async () => {
  if (loading.value || addressLoading.value || submitting.value || !items.value.length) return
  const receiver = shippingAddress.value
  if (!receiver || !hasShippingAddress.value) {
    ElMessage.warning(t('请先在个人中心完善收货信息'))
    return
  }

  submitting.value = true
  try {
    // 只提交商品 ID 和数量，价格由服务端查库计算
    const fields = {
      items: items.value.map(({ productId, quantity }) => ({ productId, quantity })),
      fromCart: !buyNowProductId && !seckillActivityId,
      ...(seckillActivityId ? { seckillActivityId: Number(seckillActivityId) } : {}),
      receiverName: receiver.receiverName,
      receiverPhone: receiver.receiverPhone,
      receiverAddress: receiver.receiverAddress,
      remark: remark.value.trim() || null,
    }
    const { data } = await request.post<ApiResult<{ order: Order }>>('/order/create', {
      requestId: requestIdFor(JSON.stringify(fields)),
      ...fields,
    })
    if (!data.status) {
      ElMessage.error(t(data.payload.message || '下单失败'))
      return
    }
    if (!buyNowProductId && !seckillActivityId) {
      cartStore.refresh()
    }
    // replace：支付页后退不再回到结算页，避免重复下单
    await router.replace({ name: 'order_pay', params: { id: data.payload.order.id } })
    if (router.currentRoute.value.name === 'order_pay') {
      try {
        sessionStorage.removeItem(submissionStorageKey)
      } catch {
        // 页面内仍保留已完成请求号，避免导航重试时重新下单。
      }
    }
  } catch {
    ElMessage.error(t('下单失败'))
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.checkout-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.25rem;

  .section-title {
    margin-bottom: 0;
  }
}

.checkout-back {
  flex-shrink: 0;
  margin-left: auto;
}

.back-icon {
  margin-right: 0.25rem;
}

.checkout-body {
  min-height: 300px;
}

.checkout-card {
  margin-bottom: 1rem;
}

.seckill-checkout-hint {
  color: var(--el-color-danger);
  font-size: 0.875rem;
}

.card-title {
  font-weight: 600;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.address-link {
  color: var(--el-color-primary);
  font-size: 0.875rem;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}

.receiver-contact {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
  margin: 0;
}

.receiver-address {
  margin: 0.5rem 0 0;
  line-height: 1.6;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.address-hint {
  color: var(--el-color-warning);
  font-size: 0.875rem;
}

.address-error {
  display: flex;
  align-items: center;
  gap: 1rem;
  color: var(--el-color-danger);
  font-size: 0.875rem;
}

.remark-form {
  margin-top: 1rem;

  :deep(.el-form-item) {
    margin-bottom: 0;
  }
}

.product-cell {
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

.submit-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 1.25rem;
  padding: 0.75rem 1.25rem;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background-color: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font-size: 0.875rem;
}

.total-amount {
  font-size: 1.5rem;
}
</style>
