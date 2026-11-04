<template>
  <div class="shop-container">
    <h1 class="section-title"><i class="bi bi-cart3"></i>购物车</h1>

    <div v-loading="loading" class="cart-body">
      <template v-if="items.length">
        <ElCard shadow="never" class="cart-card">
          <ElTable :data="items" :row-class-name="rowClassName">
            <ElTableColumn width="50">
              <template #header>
                <ElCheckbox
                  :model-value="allChecked"
                  :indeterminate="!allChecked && checkedItems.length > 0"
                  :disabled="!availableItems.length"
                  @change="(checked) => checkAll(Boolean(checked))"
                />
              </template>
              <template #default="{ row }">
                <ElCheckbox
                  :model-value="row.checked === 1"
                  :disabled="!isAvailable(row as CartItem)"
                  @change="(checked) => updateItem(row.id, { checked: checked ? 1 : 0 })"
                />
              </template>
            </ElTableColumn>
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
                  <div class="product-text">
                    <span class="product-name">{{ row.productName }}</span>
                    <ElTag v-if="row.productStatus !== 1" type="info" size="small">已下架</ElTag>
                    <ElTag v-else-if="row.stock < row.quantity" type="danger" size="small">
                      库存不足（剩 {{ row.stock }}）
                    </ElTag>
                  </div>
                </RouterLink>
              </template>
            </ElTableColumn>
            <ElTableColumn label="单价" width="120">
              <template #default="{ row }">{{ formatMoney(row.price) }}</template>
            </ElTableColumn>
            <ElTableColumn label="数量" width="160">
              <template #default="{ row }">
                <ElInputNumber
                  :model-value="row.quantity"
                  :min="1"
                  :max="Math.max(row.stock, 1)"
                  :disabled="row.productStatus !== 1"
                  size="small"
                  @change="(value) => value && updateItem(row.id, { quantity: value })"
                />
              </template>
            </ElTableColumn>
            <ElTableColumn label="小计" width="120">
              <template #default="{ row }">
                <span class="price">{{ formatMoney(row.price * row.quantity) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="80">
              <template #default="{ row }">
                <ElPopconfirm title="确定从购物车删除吗？" @confirm="removeItems([row.id])">
                  <template #reference>
                    <ElButton link type="danger">删除</ElButton>
                  </template>
                </ElPopconfirm>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>

        <!-- 结算栏：吸底，长列表滚动时也能看到合计 -->
        <div class="settle-bar">
          <ElPopconfirm
            title="确定删除选中的商品吗？"
            :disabled="!checkedItems.length"
            @confirm="removeItems(checkedItems.map((item) => item.id))"
          >
            <template #reference>
              <ElButton link :disabled="!checkedItems.length">删除选中</ElButton>
            </template>
          </ElPopconfirm>
          <div class="settle-right">
            <span>
              已选 <b class="settle-count">{{ settleItems.length }}</b> 种商品，合计：
              <span class="price settle-amount">{{ formatMoney(settleAmount) }}</span>
            </span>
            <ElButton
              type="primary"
              size="large"
              :disabled="!settleItems.length"
              @click="router.push({ name: 'checkout' })"
            >
              去结算
            </ElButton>
          </div>
        </div>
      </template>

      <ElEmpty v-else-if="!loading" description="购物车还是空的">
        <ElButton type="primary" @click="router.push({ name: 'products' })">去逛逛</ElButton>
      </ElEmpty>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import {
  ElButton,
  ElCard,
  ElCheckbox,
  ElEmpty,
  ElImage,
  ElInputNumber,
  ElMessage,
  ElPopconfirm,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, CartItem } from '@/types/api'
import { assetUrl, formatMoney } from '@/utils/format'
import { isSettleable, useCartStore } from '@/stores/cart'

const router = useRouter()
const cartStore = useCartStore()

const loading = ref(false)
const items = ref<CartItem[]>([])

// 在售且有库存的商品才能勾选；库存不足的仍可勾选，改小数量后就能结算
const isAvailable = (item: CartItem) => item.productStatus === 1 && item.stock > 0

const availableItems = computed(() => items.value.filter(isAvailable))
const checkedItems = computed(() => items.value.filter((item) => item.checked === 1))
const allChecked = computed(
  () => availableItems.value.length > 0 && availableItems.value.every((item) => item.checked === 1),
)

// 合计只算能结算的商品，和结算页一致
const settleItems = computed(() => items.value.filter(isSettleable))
const settleAmount = computed(() =>
  settleItems.value.reduce((sum, item) => sum + item.price * item.quantity, 0),
)

const rowClassName = ({ row }: { row: CartItem }) => (isAvailable(row) ? '' : 'is-unavailable')

// silent：增删改之后重新拉取时不显示整表 loading，避免闪烁
const fetchItems = async (silent = false) => {
  if (!silent) loading.value = true
  try {
    const { data } = await request.get<ApiResult<{ items: CartItem[] }>>('/cart')
    if (!data.status) {
      ElMessage.error(data.payload.message || '加载购物车失败')
      return
    }
    items.value = data.payload.items
    cartStore.count = items.value.length
  } catch {
    ElMessage.error('加载购物车失败')
  } finally {
    loading.value = false
  }
}

// 改数量、勾选都以服务端为准：请求后重新拉取，失败时界面也会回到真实状态
const updateItem = async (id: number, changes: { quantity?: number; checked?: number }) => {
  try {
    const { data } = await request.post<ApiResult>('/cart/update', { id, ...changes })
    if (!data.status) {
      ElMessage.error(data.payload.message || '更新失败')
    }
  } catch {
    ElMessage.error('更新失败')
  }
  fetchItems(true)
}

const checkAll = async (checked: boolean) => {
  try {
    const { data } = await request.post<ApiResult>('/cart/check-all', { checked: checked ? 1 : 0 })
    if (!data.status) {
      ElMessage.error(data.payload.message || '操作失败')
    }
  } catch {
    ElMessage.error('操作失败')
  }
  fetchItems(true)
}

const removeItems = async (ids: number[]) => {
  try {
    const { data } = await request.post<ApiResult>('/cart/delete', { ids })
    if (!data.status) {
      ElMessage.error(data.payload.message || '删除失败')
      return
    }
    ElMessage.success('已删除')
    fetchItems(true)
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(() => fetchItems())
</script>

<style lang="scss" scoped>
.cart-body {
  min-height: 300px;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  text-decoration: none;
}

.product-image {
  flex-shrink: 0;
  width: 64px;
  height: 64px;
  border-radius: 6px;
}

.product-text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.25rem;
}

.product-name {
  color: var(--el-text-color-primary);

  &:hover {
    color: var(--el-color-primary);
  }
}

// 下架或售罄的商品置灰
:deep(.is-unavailable) {
  opacity: 0.6;
}

.settle-bar {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 1rem;
  padding: 0.75rem 1.25rem;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background-color: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-lighter);
}

.settle-right {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  color: var(--el-text-color-regular);
  font-size: 0.875rem;
}

.settle-count {
  color: var(--el-color-primary);
}

.settle-amount {
  font-size: 1.375rem;
}
</style>
