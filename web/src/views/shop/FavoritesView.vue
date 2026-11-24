<template>
  <div class="shop-container">
    <div class="favorites-heading">
      <div>
        <h1 class="section-title"><i class="bi bi-heart"></i>{{ t('我的收藏') }}</h1>
        <p>{{ t('喜欢的好物留在这里，上架、降价和涨价都会提醒你。') }}</p>
      </div>
      <RouterLink :to="{ name: 'notifications' }">{{ t('查看消息通知') }}</RouterLink>
    </div>
    <div v-loading="loading" class="favorites-list">
      <div v-if="error" role="alert" class="load-error">
        <span>{{ t(error) }}</span
        ><ElButton @click="fetchFavorites">{{ t('重新加载') }}</ElButton>
      </div>
      <ElEmpty
        v-else-if="!loading && items.length === 0"
        :description="t('还没有收藏，点商品详情页上的爱心即可收藏')"
      >
        <RouterLink :to="{ name: 'products' }" class="browse-link">{{ t('去逛逛') }}</RouterLink>
      </ElEmpty>
      <div v-else class="product-grid">
        <article v-for="item in items" :key="item.productId" class="favorite-card">
          <button
            type="button"
            class="favorite-open"
            :disabled="item.productStatus !== 1"
            :aria-label="t('查看{productName}', { productName: item.productName })"
            @click="router.push({ name: 'product_detail', params: { id: item.productId } })"
          >
            <div class="favorite-image-wrap">
              <ElImage
                v-if="item.productImage"
                class="favorite-image"
                :src="assetUrl(item.productImage)"
                fit="cover"
                lazy
              />
              <div v-else class="favorite-image image-placeholder"><i class="bi bi-image"></i></div>
              <span v-if="item.productStatus !== 1" class="product-state">{{
                t('已下架 · 上架后提醒')
              }}</span>
              <span v-else-if="item.stock === 0" class="product-state">{{ t('已售罄') }}</span>
            </div>
            <div class="favorite-body">
              <h2>{{ item.productName }}</h2>
              <span class="text-secondary">{{ t(item.category) }}</span>
              <strong class="price">{{ formatMoney(item.price) }}</strong>
            </div>
          </button>
          <FavoriteButton
            class="favorite-heart"
            :product-id="item.productId"
            :product-name="item.productName"
          />
        </article>
      </div>
    </div>
    <ElPagination
      v-if="pagination.total > 0"
      v-model:current-page="page"
      class="page-pagination"
      layout="prev, pager, next"
      :page-size="pagination.pageSize"
      :total="pagination.total"
    />
  </div>
</template>

<script setup lang="ts">
import { t } from '@/i18n'
import { onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElButton, ElEmpty, ElImage, ElPagination, vLoading } from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, Pagination, ProductFavorite } from '@/types/api'
import { assetUrl, formatMoney } from '@/utils/format'
import { useFavoritesStore } from '@/stores/favorites'
import { useUserStore } from '@/stores/user'
import FavoriteButton from '@/components/shop/FavoriteButton.vue'

const favorites = useFavoritesStore()
const user = useUserStore()
const router = useRouter()
const items = ref<ProductFavorite[]>([])
const page = ref(1)
const loading = ref(false)
const error = ref('')
const pagination = ref<Pagination>({ total: 0, pageSize: 12, currentPage: 1, pageCount: 1 })
let sequence = 0

const fetchFavorites = async () => {
  const current = ++sequence
  const token = user.user?.token
  loading.value = true
  error.value = ''
  try {
    const { data } = await request.get<
      ApiResult<{ favorites: ProductFavorite[]; pagination: Pagination }>
    >('/favorite', {
      params: { page: page.value, pageSize: 12 },
    })
    if (current !== sequence || token !== user.user?.token) return
    if (!data.status) throw new Error(t(data.payload.message || '加载收藏失败'))
    items.value = data.payload.favorites
    pagination.value = data.payload.pagination
    page.value = data.payload.pagination.currentPage
  } catch {
    if (current === sequence) error.value = '加载收藏失败，请稍后重试'
  } finally {
    if (current === sequence) loading.value = false
  }
}

watch([page, () => favorites.revision], fetchFavorites, { immediate: true })
onBeforeUnmount(() => {
  sequence += 1
})
</script>

<style lang="scss" scoped>
.favorites-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.5rem;
  .section-title {
    margin-bottom: 0.5rem;
  }
  p {
    margin: 0;
    color: var(--el-text-color-secondary);
    font-size: 0.875rem;
  }
  a {
    color: var(--el-color-primary);
    font-size: 0.875rem;
    text-decoration: none;
  }
}
.favorites-list {
  min-height: 240px;
}
.favorite-card {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}
.favorite-open {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  font: inherit;
  cursor: pointer;
  &:disabled {
    cursor: default;
  }
  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: -2px;
  }
}
.favorite-image-wrap {
  position: relative;
  aspect-ratio: 1;
}
.favorite-image {
  width: 100%;
  height: 100%;
  font-size: 2.5rem;
}
.product-state {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 0.5rem;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  text-align: center;
  font-size: 0.8125rem;
}
.favorite-body {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  padding: 0.875rem;
  h2 {
    margin: 0;
    color: var(--el-text-color-primary);
    font-size: 0.875rem;
    font-weight: 500;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .price {
    margin-top: 0.25rem;
    font-size: 1.0625rem;
  }
}
.favorite-heart {
  position: absolute;
  top: 0.625rem;
  right: 0.625rem;
}
.load-error {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 3rem 0;
  color: var(--el-color-danger);
}
.browse-link {
  color: var(--el-color-primary);
  text-decoration: none;
}
</style>
