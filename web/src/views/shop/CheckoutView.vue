<template>
  <div class="shop-container">
    <h1 class="section-title"><i class="bi bi-bag-check"></i>确认订单</h1>

    <div v-loading="loading" class="checkout-body">
      <template v-if="items.length">
        <ElCard shadow="never" class="checkout-card">
          <template #header><span class="card-title">收货信息</span></template>
          <ElForm
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="80px"
            class="receiver-form"
          >
            <ElFormItem label="收货人" prop="receiverName">
              <ElInput v-model="form.receiverName" maxlength="32" placeholder="收货人姓名" />
            </ElFormItem>
            <ElFormItem label="手机号" prop="receiverPhone">
              <ElInput v-model="form.receiverPhone" maxlength="11" placeholder="11 位手机号" />
            </ElFormItem>
            <ElFormItem label="收货地址" prop="receiverAddress" class="full-row">
              <ElInput
                v-model="form.receiverAddress"
                maxlength="255"
                placeholder="省 / 市 / 区 / 街道 / 门牌号"
              />
            </ElFormItem>
            <ElFormItem label="备注" prop="remark" class="full-row">
              <ElInput
                v-model="form.remark"
                maxlength="255"
                show-word-limit
                placeholder="选填，给商家的留言"
              />
            </ElFormItem>
          </ElForm>
        </ElCard>

        <ElCard shadow="never" class="checkout-card">
          <template #header><span class="card-title">商品清单</span></template>
          <ElTable :data="items">
            <ElTableColumn label="商品" min-width="280">
              <template #default="{ row }">
                <div class="product-cell">
                  <ElImage v-if="row.image" class="product-image" :src="row.image" fit="cover" />
                  <div v-else class="product-image image-placeholder">
                    <i class="bi bi-image"></i>
                  </div>
                  <span>{{ row.name }}</span>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="单价" width="120">
              <template #default="{ row }">{{ formatMoney(row.price) }}</template>
            </ElTableColumn>
            <ElTableColumn prop="quantity" label="数量" width="80" />
            <ElTableColumn label="小计" width="120">
              <template #default="{ row }">{{ formatMoney(row.price * row.quantity) }}</template>
            </ElTableColumn>
          </ElTable>
        </ElCard>

        <div class="submit-bar">
          <span>
            共 {{ totalQuantity }} 件，应付：
            <span class="price total-amount">{{ formatMoney(totalAmount) }}</span>
          </span>
          <ElButton type="primary" size="large" :loading="submitting" @click="submit">
            提交订单
          </ElButton>
        </div>
      </template>

      <ElEmpty v-else-if="!loading" description="没有可结算的商品">
        <ElButton type="primary" @click="router.push({ name: 'cart' })">返回购物车</ElButton>
      </ElEmpty>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
  type FormInstance,
  type FormRules,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, CartItem, Order, Product } from '@/types/api'
import { formatMoney } from '@/utils/format'
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
const buyNowQuantity = Math.max(Number(route.query.quantity) || 1, 1)

const loading = ref(false)
const items = ref<CheckoutItem[]>([])

// 页面上的金额只是预览，订单金额以服务端按当前价格计算的为准
const totalAmount = computed(() =>
  items.value.reduce((sum, item) => sum + item.price * item.quantity, 0),
)
const totalQuantity = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))

const fetchBuyNowItem = async () => {
  const { data } = await request.get<ApiResult<{ product: Product }>>(`/product/${buyNowProductId}`)
  if (!data.status) {
    ElMessage.error(data.payload.message || '商品不存在或已下架')
    return
  }
  const product = data.payload.product
  if (product.stock < buyNowQuantity) {
    ElMessage.error('库存不足')
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

const fetchCartItems = async () => {
  const { data } = await request.get<ApiResult<{ items: CartItem[] }>>('/cart')
  if (!data.status) {
    ElMessage.error(data.payload.message || '加载购物车失败')
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

onMounted(async () => {
  loading.value = true
  try {
    await (buyNowProductId ? fetchBuyNowItem() : fetchCartItems())
  } catch {
    ElMessage.error('加载商品失败')
  } finally {
    loading.value = false
  }
})

const formRef = ref<FormInstance>()

// 收货人、手机号先用个人资料预填
const form = reactive({
  receiverName: userStore.user?.user.name ?? '',
  receiverPhone: userStore.user?.user.phone ?? '',
  receiverAddress: '',
  remark: '',
})

const rules: FormRules = {
  receiverName: [{ required: true, whitespace: true, message: '请填写收货人', trigger: 'blur' }],
  receiverPhone: [
    { required: true, message: '请填写手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号', trigger: 'blur' },
  ],
  receiverAddress: [
    { required: true, whitespace: true, message: '请填写收货地址', trigger: 'blur' },
  ],
}

const submitting = ref(false)

const submit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    // 只提交商品 ID 和数量，价格由服务端查库计算
    const { data } = await request.post<ApiResult<{ order: Order }>>('/order/create', {
      items: items.value.map(({ productId, quantity }) => ({ productId, quantity })),
      fromCart: !buyNowProductId,
      receiverName: form.receiverName.trim(),
      receiverPhone: form.receiverPhone,
      receiverAddress: form.receiverAddress.trim(),
      remark: form.remark.trim() || null,
    })
    if (!data.status) {
      ElMessage.error(data.payload.message || '下单失败')
      return
    }
    if (!buyNowProductId) {
      cartStore.refresh()
    }
    // replace：支付页后退不再回到结算页，避免重复下单
    router.replace({ name: 'order_pay', params: { id: data.payload.order.id } })
  } catch {
    ElMessage.error('下单失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.checkout-body {
  min-height: 300px;
}

.checkout-card {
  margin-bottom: 1rem;
}

.card-title {
  font-weight: 600;
}

// 收货人、手机号并排，地址、备注占整行
.receiver-form {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  column-gap: 1.5rem;

  .full-row {
    grid-column: 1 / -1;
  }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
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
