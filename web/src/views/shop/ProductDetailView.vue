<template>
  <div class="shop-container">
    <div v-loading="loading" class="detail-page">
      <template v-if="product">
        <ElBreadcrumb class="detail-breadcrumb">
          <ElBreadcrumbItem :to="{ name: 'index' }">首页</ElBreadcrumbItem>
          <ElBreadcrumbItem :to="{ name: 'products', query: { category: product.category } }">
            {{ product.category }}
          </ElBreadcrumbItem>
          <ElBreadcrumbItem>{{ product.name }}</ElBreadcrumbItem>
        </ElBreadcrumb>

        <ElCard shadow="never">
          <div class="detail-main">
            <ElImage
              v-if="product.mainImage"
              class="detail-image"
              :src="assetUrl(product.mainImage)"
              :preview-src-list="[assetUrl(product.mainImage)]"
              fit="cover"
            />
            <div v-else class="detail-image image-placeholder">
              <i class="bi bi-image"></i>
            </div>

            <div class="detail-info">
              <h1 class="detail-name">{{ product.name }}</h1>
              <p v-if="product.subtitle" class="detail-subtitle">{{ product.subtitle }}</p>

              <div class="price-box">
                <span class="price-label">售价</span>
                <span class="price detail-price">{{ formatMoney(product.price) }}</span>
              </div>

              <dl class="detail-meta">
                <dt>销量</dt>
                <dd>{{ product.sales }}</dd>
                <dt>库存</dt>
                <dd>{{ product.stock > 0 ? product.stock : '已售罄' }}</dd>
                <dt>数量</dt>
                <dd>
                  <ElInputNumber
                    v-model="quantity"
                    :min="1"
                    :max="Math.max(product.stock, 1)"
                    :disabled="product.stock === 0"
                  />
                </dd>
              </dl>

              <div class="detail-actions">
                <ElButton
                  size="large"
                  :disabled="product.stock === 0"
                  :loading="adding"
                  @click="addToCart"
                >
                  <i class="bi bi-cart-plus btn-icon"></i>加入购物车
                </ElButton>
                <ElButton
                  type="primary"
                  size="large"
                  :disabled="product.stock === 0"
                  @click="buyNow"
                >
                  立即购买
                </ElButton>
              </div>
            </div>
          </div>
        </ElCard>

        <ElCard shadow="never" class="description-card">
          <template #header><span class="card-title">商品详情</span></template>
          <!-- 纯文本展示（不用 v-html），保留换行 -->
          <p v-if="product.description" class="description">{{ product.description }}</p>
          <ElEmpty v-else description="暂无详情" :image-size="80" />
        </ElCard>
      </template>

      <ElResult
        v-else-if="!loading"
        icon="warning"
        title="商品不存在或已下架"
        sub-title="看看其他商品吧"
      >
        <template #extra>
          <ElButton type="primary" @click="router.push({ name: 'products' })">去逛逛</ElButton>
        </template>
      </ElResult>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElBreadcrumb,
  ElBreadcrumbItem,
  ElButton,
  ElCard,
  ElEmpty,
  ElImage,
  ElInputNumber,
  ElMessage,
  ElResult,
  vLoading,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, Product } from '@/types/api'
import { assetUrl, formatMoney } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

const loading = ref(false)
const product = ref<Product | null>(null)
const quantity = ref(1)

const fetchProduct = async (id: string) => {
  loading.value = true
  product.value = null
  quantity.value = 1
  try {
    const { data } = await request.get<ApiResult<{ product: Product }>>(`/product/${id}`)
    // 不存在或已下架都显示同一个空状态，不弹错误
    if (data.status) {
      product.value = data.payload.product
    }
  } catch {
    ElMessage.error('加载商品失败')
  } finally {
    loading.value = false
  }
}

// 同一组件在不同商品间跳转时（如从详情页点到另一个商品）组件会复用，按 id 重新加载
watch(
  () => route.params.id,
  (id) => {
    if (route.name === 'product_detail' && typeof id === 'string') {
      fetchProduct(id)
    }
  },
  { immediate: true },
)

// 加购、购买都要登录；游客先去登录，登录后回到本页
const requireLogin = (): boolean => {
  if (userStore.logged) return true
  ElMessage.info('请先登录')
  router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

const adding = ref(false)

const addToCart = async () => {
  if (!product.value || !requireLogin()) return

  adding.value = true
  try {
    const { data } = await request.post<ApiResult>('/cart/create', {
      productId: product.value.id,
      quantity: quantity.value,
    })
    if (!data.status) {
      ElMessage.error(data.payload.message || '加入购物车失败')
      return
    }
    ElMessage.success('已加入购物车')
    cartStore.refresh()
  } catch {
    ElMessage.error('加入购物车失败')
  } finally {
    adding.value = false
  }
}

// 立即购买不经过购物车：把商品和数量带到结算页
const buyNow = () => {
  if (!product.value || !requireLogin()) return

  router.push({
    name: 'checkout',
    query: { productId: String(product.value.id), quantity: String(quantity.value) },
  })
}
</script>

<style lang="scss" scoped>
.detail-page {
  min-height: 400px;
}

.detail-breadcrumb {
  margin-bottom: 1rem;
}

.detail-main {
  display: flex;
  gap: 2rem;
}

.detail-image {
  flex-shrink: 0;
  width: 400px;
  height: 400px;
  border-radius: 8px;
  font-size: 4rem;
}

.detail-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.detail-name {
  margin: 0 0 0.5rem;
  color: var(--el-text-color-primary);
  font-size: 1.5rem;
  font-weight: 600;
}

.detail-subtitle {
  margin: 0 0 1rem;
  color: var(--el-text-color-secondary);
}

.price-box {
  display: flex;
  align-items: baseline;
  gap: 1rem;
  padding: 1rem 1.25rem;
  border-radius: 8px;
  background-color: var(--el-fill-color-light);
}

.price-label {
  color: var(--el-text-color-secondary);
  font-size: 0.875rem;
}

.detail-price {
  font-size: 1.75rem;
}

.detail-meta {
  display: grid;
  grid-template-columns: 3rem 1fr;
  align-items: center;
  gap: 1rem;
  margin: 1.5rem 0;

  dt {
    color: var(--el-text-color-secondary);
    font-size: 0.875rem;
  }

  dd {
    margin: 0;
    color: var(--el-text-color-primary);
  }
}

.detail-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: auto;

  .el-button {
    min-width: 140px;
    margin: 0;
  }
}

.btn-icon {
  margin-right: 0.375rem;
}

.description-card {
  margin-top: 1.25rem;
}

.card-title {
  font-weight: 600;
}

.description {
  margin: 0;
  color: var(--el-text-color-regular);
  line-height: 1.8;
  white-space: pre-wrap;
}

@media (max-width: 768px) {
  .detail-main {
    flex-direction: column;
  }

  .detail-image {
    width: 100%;
    height: auto;
    aspect-ratio: 1;
  }
}
</style>
