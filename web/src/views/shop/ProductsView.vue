<template>
  <div class="shop-container">
    <!-- 筛选：条件都放在地址栏里，刷新、分享链接、浏览器后退都能还原 -->
    <ElCard shadow="never" class="filter-card">
      <div class="filter-row">
        <span class="filter-label">分类</span>
        <div class="filter-options">
          <ElCheckTag :checked="!query.category" @change="updateQuery({ category: undefined })">
            全部
          </ElCheckTag>
          <ElCheckTag
            v-for="category in categories"
            :key="category"
            :checked="query.category === category"
            @change="updateQuery({ category })"
          >
            {{ category }}
          </ElCheckTag>
        </div>
      </div>
      <div class="filter-row">
        <span class="filter-label">排序</span>
        <ElRadioGroup
          :model-value="query.sort"
          size="small"
          @change="(sort) => updateQuery({ sort: sort as ProductSort })"
        >
          <ElRadioButton v-for="(label, sort) in SORT_OPTIONS" :key="sort" :value="sort">
            {{ label }}
          </ElRadioButton>
        </ElRadioGroup>
      </div>
      <div v-if="query.keyword" class="filter-row">
        <span class="filter-label">搜索</span>
        <ElTag closable @close="updateQuery({ keyword: undefined })">{{ query.keyword }}</ElTag>
      </div>
    </ElCard>

    <div v-loading="loading" class="list-body">
      <div v-if="products.length" class="product-grid">
        <ProductCard v-for="product in products" :key="product.id" :product="product" />
      </div>
      <ElEmpty v-else-if="!loading" description="没有找到相关商品" />
    </div>

    <ElPagination
      v-if="total > PAGE_SIZE"
      class="page-pagination"
      :current-page="query.page"
      :page-size="PAGE_SIZE"
      :total="total"
      layout="total, prev, pager, next, jumper"
      background
      @current-change="(page: number) => updateQuery({ page }, false)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElCard,
  ElCheckTag,
  ElEmpty,
  ElMessage,
  ElPagination,
  ElRadioButton,
  ElRadioGroup,
  ElTag,
  vLoading,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, Pagination, Product, ProductSort } from '@/types/api'
import ProductCard from '@/components/shop/ProductCard.vue'

const PAGE_SIZE = 20

const SORT_OPTIONS: Record<ProductSort, string> = {
  default: '综合',
  sales: '销量',
  newest: '新品',
  price_asc: '价格从低到高',
  price_desc: '价格从高到低',
}

const route = useRoute()
const router = useRouter()

// 从地址栏解析筛选条件；非法值按默认处理
const query = computed(() => {
  const { keyword, category, sort, page } = route.query
  return {
    keyword: typeof keyword === 'string' ? keyword : undefined,
    category: typeof category === 'string' ? category : undefined,
    sort: typeof sort === 'string' && sort in SORT_OPTIONS ? (sort as ProductSort) : 'default',
    page: Math.max(Number(page) || 1, 1),
  }
})

type ProductQuery = {
  keyword?: string
  category?: string
  sort?: ProductSort
  page?: number
}

// 改筛选条件：默认回到第一页；翻页时 resetPage 传 false。
// 值为 undefined 或默认值的参数不写进地址栏，保持链接干净
const updateQuery = (changes: ProductQuery, resetPage = true) => {
  const next = { ...query.value, ...(resetPage ? { page: 1 } : {}), ...changes }
  router.push({
    name: 'products',
    query: {
      keyword: next.keyword || undefined,
      category: next.category || undefined,
      sort: next.sort === 'default' ? undefined : next.sort,
      page: next.page > 1 ? String(next.page) : undefined,
    },
  })
}

const categories = ref<string[]>([])
const products = ref<Product[]>([])
const total = ref(0)
const loading = ref(false)

const fetchCategories = async () => {
  try {
    const { data } = await request.get<ApiResult<{ categories: string[] }>>('/product/categories')
    if (data.status) {
      categories.value = data.payload.categories
    }
  } catch {
    // 分类加载失败时只剩「全部」，不影响浏览
  }
}

const fetchProducts = async () => {
  loading.value = true
  try {
    const { data } = await request.get<ApiResult<{ products: Product[]; pagination: Pagination }>>(
      '/product',
      {
        params: {
          page: query.value.page,
          pageSize: PAGE_SIZE,
          keyword: query.value.keyword,
          category: query.value.category,
          sort: query.value.sort,
        },
      },
    )
    if (!data.status) {
      ElMessage.error(data.payload.message || '加载商品失败')
      return
    }
    products.value = data.payload.products
    total.value = data.payload.pagination.total
  } catch {
    ElMessage.error('加载商品失败')
  } finally {
    loading.value = false
  }
}

// 地址栏条件变化（含顶栏搜索、浏览器前进后退）时重新查询，并回到页面顶部；
// 离开本页时 route 先变、组件后卸载，这时不查询
watch(
  query,
  () => {
    if (route.name !== 'products') return
    fetchProducts()
    window.scrollTo({ top: 0 })
  },
  { immediate: true },
)

onMounted(fetchCategories)
</script>

<style lang="scss" scoped>
.filter-card {
  margin-bottom: 1.25rem;

  :deep(.el-card__body) {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.filter-label {
  flex-shrink: 0;
  width: 2.5rem;
  color: var(--el-text-color-secondary);
  font-size: 0.875rem;
}

.filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.list-body {
  min-height: 300px;
}
</style>
