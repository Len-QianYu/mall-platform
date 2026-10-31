<template>
  <div class="shop-container">
    <!-- 横幅 -->
    <section class="hero">
      <div class="hero-text">
        <h1 class="hero-title">品质好物，一站购齐</h1>
        <p class="hero-desc">精选全品类商品，下单即享极速发货与无忧售后</p>
        <ElButton type="primary" size="large" @click="router.push({ name: 'products' })">
          去逛逛 <i class="bi bi-arrow-right hero-btn-icon"></i>
        </ElButton>
      </div>
      <i class="bi bi-bag-heart hero-icon"></i>
    </section>

    <!-- 分类入口 -->
    <section v-if="categories.length" class="home-section">
      <h2 class="se ction-title"><i class="bi bi-grid"></i>商品分类</h2>
      <div class="category-list">
        <RouterLink
          v-for="category in categories"
          :key="category"
          class="category-item"
          :to="{ name: 'products', query: { category } }"
        >
          {{ category }}
        </RouterLink>
      </div>
    </section>

    <section v-for="section in sections" :key="section.sort" class="home-section">
      <div class="section-heading">
        <h2 class="section-title"><i :class="section.icon"></i>{{ section.title }}</h2>
        <RouterLink class="more-link" :to="{ name: 'products', query: { sort: section.sort } }">
          查看更多 <i class="bi bi-chevron-right"></i>
        </RouterLink>
      </div>
      <div v-loading="section.loading" class="section-body">
        <div v-if="section.products.length" class="product-grid">
          <ProductCard v-for="product in section.products" :key="product.id" :product="product" />
        </div>
        <ElEmpty v-else-if="!section.loading" description="暂无商品" />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElButton, ElEmpty, ElMessage, vLoading } from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, Pagination, Product, ProductSort } from '@/types/api'
import ProductCard from '@/components/shop/ProductCard.vue'

const router = useRouter()

const categories = ref<string[]>([])

// 首页两个商品区块，都是商品列表接口换个排序取前几条
const sections = reactive([
  {
    title: '热销榜单',
    icon: 'bi bi-fire',
    sort: 'sales' as ProductSort,
    products: [] as Product[],
    loading: true,
  },
  {
    title: '新品上架',
    icon: 'bi bi-stars',
    sort: 'newest' as ProductSort,
    products: [] as Product[],
    loading: true,
  },
])

const fetchCategories = async () => {
  try {
    const { data } = await request.get<ApiResult<{ categories: string[] }>>('/product/categories')
    if (data.status) {
      categories.value = data.payload.categories
    }
  } catch {
    // 分类入口只是导航，加载失败就不显示
  }
}

const fetchSection = async (section: (typeof sections)[number]) => {
  try {
    const { data } = await request.get<ApiResult<{ products: Product[]; pagination: Pagination }>>(
      '/product',
      { params: { page: 1, pageSize: 10, sort: section.sort } },
    )
    if (!data.status) {
      ElMessage.error(data.payload.message || '加载商品失败')
      return
    }
    section.products = data.payload.products
  } catch {
    ElMessage.error('加载商品失败')
  } finally {
    section.loading = false
  }
}

onMounted(() => {
  fetchCategories()
  sections.forEach(fetchSection)
})
</script>

<style lang="scss" scoped>
.hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  overflow: hidden;
  margin-bottom: 2rem;
  padding: 3rem;
  border-radius: 12px;
  background: linear-gradient(
    120deg,
    var(--el-color-primary-dark-2),
    var(--el-color-primary) 55%,
    var(--el-color-primary-light-3)
  );
  color: #fff;
}

.hero-title {
  margin: 0 0 0.75rem;
  font-size: 2rem;
  font-weight: 700;
}

.hero-desc {
  margin: 0 0 1.5rem;
  opacity: 0.9;
}

// 横幅底色就是主题色，按钮反色显示
.hero .el-button {
  border-color: #fff;
  background-color: #fff;
  color: var(--el-color-primary);
}

.hero-btn-icon {
  margin-left: 0.25rem;
}

.hero-icon {
  font-size: 8rem;
  line-height: 1;
  opacity: 0.25;
}

.home-section {
  margin-bottom: 2rem;
}

.section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.more-link {
  color: var(--el-text-color-secondary);
  font-size: 0.875rem;
  text-decoration: none;

  &:hover {
    color: var(--el-color-primary);
  }
}

.section-body {
  min-height: 120px;
}

.category-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.category-item {
  padding: 0.5rem 1.25rem;
  border: 1px solid var(--el-border-color);
  border-radius: 9999px;
  background-color: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font-size: 0.875rem;
  text-decoration: none;
  transition: all 0.15s;

  &:hover {
    border-color: var(--el-color-primary);
    color: var(--el-color-primary);
  }
}

@media (max-width: 768px) {
  .hero {
    padding: 2rem 1.5rem;
  }

  .hero-title {
    font-size: 1.5rem;
  }

  .hero-icon {
    display: none;
  }
}
</style>
