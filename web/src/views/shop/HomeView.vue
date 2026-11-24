<template>
  <div class="shop-container">
    <!-- 横幅 -->
    <section class="hero">
      <div class="hero-text">
        <h1 class="hero-title">{{ t('品质好物，一站购齐') }}</h1>
        <p class="hero-desc">{{ t('精选全品类商品，下单即享极速发货与无忧售后') }}</p>
      </div>
      <div class="hero-actions">
        <ElButton type="primary" size="large" @click="router.push({ name: 'products' })">
          {{ t('去逛逛') }} <i class="bi bi-arrow-right hero-btn-icon"></i>
        </ElButton>
        <ElButton type="danger" plain size="large" @click="router.push({ name: 'seckill' })">
          <i class="bi bi-lightning-charge hero-btn-icon"></i>{{ t('限时秒杀') }}
        </ElButton>
      </div>
    </section>

    <section class="home-section categories-section" aria-labelledby="categories-title">
      <div class="section-heading">
        <div>
          <h2 id="categories-title" class="section-title">
            {{ t('商品分类') }} <i class="bi bi-stars" aria-hidden="true"></i>
          </h2>
          <p class="section-description">{{ t('找到与你合拍的 AI 伙伴') }}</p>
        </div>
        <RouterLink class="more-link" :to="{ name: 'products' }">
          {{ t('查看全部') }} <i class="bi bi-arrow-right" aria-hidden="true"></i>
        </RouterLink>
      </div>
      <div class="category-grid">
        <RouterLink
          v-for="category in PLUSH_CATEGORIES"
          :key="category.key"
          class="category-card"
          :class="category.key"
          :to="{ name: 'products', query: { category: category.name } }"
          :aria-label="t('浏览{collection}', { collection: t(category.title) })"
        >
          <div class="category-art">
            <img
              :src="categoryImages[category.key]"
              :alt="t('{collection}系列', { collection: t(category.title) })"
              class="category-image"
              loading="lazy"
              decoding="async"
            />
          </div>
          <div class="category-info">
            <h3>{{ t(category.title) }}</h3>
            <p>{{ t(category.description) }}</p>
            <i class="bi bi-arrow-right category-arrow" aria-hidden="true"></i>
          </div>
        </RouterLink>
      </div>
    </section>

    <section class="home-section hot-section" aria-labelledby="hot-title">
      <div class="section-heading">
        <div>
          <h2 id="hot-title" class="section-title">
            {{ t('热销榜单') }} <i class="bi bi-stars" aria-hidden="true"></i>
          </h2>
          <p class="section-description">{{ t('看看大家喜欢的 AI 伙伴') }}</p>
        </div>
        <RouterLink class="more-link" :to="{ name: 'products', query: { sort: 'sales' } }">
          {{ t('查看全部') }} <i class="bi bi-arrow-right" aria-hidden="true"></i>
        </RouterLink>
      </div>
      <div v-if="loadingHot" class="hot-grid" aria-busy="true" :aria-label="t('正在加载热销玩偶')">
        <ElSkeleton v-for="index in 4" :key="index" class="hot-skeleton" :rows="5" animated />
      </div>
      <div v-else-if="hotProducts.length" class="hot-grid">
        <ProductCard v-for="product in hotProducts" :key="product.id" :product="product" />
      </div>
      <div v-else class="hot-empty" :role="hotError ? 'alert' : undefined">
        <i class="bi bi-box2-heart" aria-hidden="true"></i>
        <h3>{{ t(hotError || '热销玩偶正在准备中') }}</h3>
        <p v-if="!hotError">{{ t('暂时还没有在售玩偶，先看看你喜欢的系列吧。') }}</p>
        <ElButton v-if="hotError" @click="fetchHotProducts">{{ t('重新加载') }}</ElButton>
        <RouterLink v-else class="more-link" :to="{ name: 'products' }">
          {{ t('浏览全部玩偶') }} <i class="bi bi-arrow-right" aria-hidden="true"></i>
        </RouterLink>
      </div>
    </section>

    <section class="brand-banner" aria-labelledby="brand-title">
      <div class="brand-visual">
        <img :src="brandImage" :alt="t('五只玩偶并排坐在一起')" loading="lazy" decoding="async" />
      </div>
      <div class="brand-copy">
        <span class="brand-kicker"><i class="bi bi-heart" aria-hidden="true"></i> PROMPT PALS</span>
        <h2 id="brand-title">{{ t('小小玩偶，大大陪伴') }}</h2>
        <p>
          {{
            t(
              '在 Prompt Pals，发现 Claude、DeepSeek、GPT 与 Grok 玩偶， 把喜欢的 AI 伙伴带进你的日常。',
            )
          }}
        </p>
        <RouterLink class="brand-link" :to="{ name: 'about' }">
          {{ t('了解我们') }} <i class="bi bi-arrow-right" aria-hidden="true"></i>
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { t } from '@/i18n'
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElButton, ElSkeleton } from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, Pagination, Product } from '@/types/api'
import ProductCard from '@/components/shop/ProductCard.vue'
import { PLUSH_CATEGORIES } from '@/constants/productCategories'
import claudeImage from '@/assets/ai-pals/claude/claude分类.png'
import deepseekImage from '@/assets/ai-pals/deepseek/deepseek分类.png'
import gptImage from '@/assets/ai-pals/gpt/gpt分类.png'
import grokImage from '@/assets/ai-pals/grok/gork分类.png'
import brandImage from '@/assets/images/five pal.png'

const router = useRouter()

const categoryImages = {
  claude: claudeImage,
  deepseek: deepseekImage,
  gpt: gptImage,
  grok: grokImage,
}
const hotProducts = ref<Product[]>([])
const loadingHot = ref(true)
const hotError = ref('')

const fetchHotProducts = async () => {
  loadingHot.value = true
  hotError.value = ''
  try {
    const { data } = await request.get<ApiResult<{ products: Product[]; pagination: Pagination }>>(
      '/product',
      { params: { page: 1, pageSize: 4, sort: 'sales' } },
    )
    if (!data.status) {
      hotError.value = data.payload.message || '热销玩偶加载失败，请重试'
      return
    }
    hotProducts.value = data.payload.products
  } catch {
    hotError.value = '热销玩偶加载失败，请检查网络后重试'
  } finally {
    loadingHot.value = false
  }
}

onMounted(fetchHotProducts)
</script>

<style lang="scss" scoped>
.hero {
  position: relative;
  display: flex;
  box-sizing: border-box;
  align-items: flex-start;
  aspect-ratio: 1152 / 681;
  min-height: 360px;
  overflow: hidden;
  margin-bottom: 2rem;
  padding: 3rem;
  border-radius: 12px;
  background-color: #bccad8;
  background-image:
    linear-gradient(
      180deg,
      rgba(13, 24, 39, 0.7),
      rgba(13, 24, 39, 0.2) 42%,
      rgba(13, 24, 39, 0) 68%
    ),
    url('@/assets/images/five pals.png');
  background-repeat: no-repeat;
  background-position: center;
  background-size: cover;
  color: #fff;
}

.hero-text {
  max-width: 520px;
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

.hero-actions {
  position: absolute;
  right: 3rem;
  bottom: 3rem;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.75rem;
  max-width: calc(100% - 6rem);

  .el-button {
    margin: 0;
  }
}

// 图片背景使用白色按钮，提高文字和操作入口的对比度。
.hero .el-button {
  border-color: #fff;
  background-color: #fff;
  color: var(--el-color-primary);
}

.hero-btn-icon {
  margin-left: 0.25rem;
}

.home-section {
  margin-bottom: 2.5rem;
}

.home-section .section-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;

  i {
    color: #ddb94c;
    font-size: 1.1rem;
  }
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.25rem;
}

.section-description {
  margin: 0.5rem 0 0;
  color: var(--el-text-color-secondary);
  font-size: 0.875rem;
}

.more-link {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--el-text-color-regular);
  font-size: 0.875rem;
  text-decoration: none;
  white-space: nowrap;

  &:hover {
    color: var(--el-color-primary);
  }
}

.category-grid,
.hot-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1.25rem;
}

.category-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: 16px;
  background-color: var(--category-background);
  color: var(--el-text-color-primary);
  text-decoration: none;
  transition:
    transform 0.2s,
    border-color 0.2s,
    box-shadow 0.2s;

  &.claude {
    --category-background: #f4e4d9;
  }

  &.deepseek {
    --category-background: #e1ecf7;
  }

  &.gpt {
    --category-background: #e8eddf;
  }

  &.grok {
    --category-background: #e8e5f4;
  }

  &:hover {
    transform: translateY(-4px);
    border-color: var(--el-color-primary-light-5);
    box-shadow: 0 8px 24px #25334814;
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 4px;
  }
}

html.dark .category-card {
  background-color: color-mix(in srgb, var(--category-background) 16%, var(--el-bg-color));
}

.category-art {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: 230px;
  padding: 1.25rem 0.75rem 0.5rem;
}

.category-image {
  display: block;
  width: auto;
  max-width: 100%;
  height: 100%;
  border-radius: 12px;
  object-fit: contain;
  filter: drop-shadow(0 8px 7px #2933411a);
  transition: transform 0.2s;
}

.category-card:hover .category-image {
  transform: translateY(-3px) rotate(-2deg);
}

.category-info {
  position: relative;
  flex: 1;
  padding: 0.25rem 1.25rem 1.25rem;

  h3 {
    margin: 0 0 0.4rem;
    font-size: 1.125rem;
    font-weight: 700;
  }

  p {
    margin: 0;
    padding-right: 1.6rem;
    color: var(--el-text-color-regular);
    font-size: 0.8rem;
    line-height: 1.6;
  }
}

.category-arrow {
  position: absolute;
  right: 1.25rem;
  bottom: 1.45rem;
  font-size: 1.1rem;
}

.hot-grid :deep(.product-card) {
  border-radius: 14px;
}

.hot-grid :deep(.card-image-wrap) {
  aspect-ratio: 1.15;
  background-color: var(--el-fill-color-extra-light);
}

.hot-grid :deep(.card-body) {
  padding: 1rem;
}

.hot-grid :deep(.card-name) {
  font-size: 0.95rem;
  font-weight: 600;
}

.hot-skeleton {
  box-sizing: border-box;
  min-height: 290px;
  padding: 1.5rem;
  border-radius: 14px;
  background-color: var(--el-bg-color);
}

.hot-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  min-height: 230px;
  padding: 2rem 1.5rem;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  background-color: var(--el-bg-color);
  text-align: center;

  > i {
    color: var(--el-color-primary);
    font-size: 2.25rem;
  }

  h3 {
    margin: 0.9rem 0 0.5rem;
    color: var(--el-text-color-primary);
    font-size: 1rem;
    font-weight: 600;
  }

  p {
    margin: 0 0 1rem;
    color: var(--el-text-color-secondary);
    font-size: 0.875rem;
    line-height: 1.7;
  }
}

.brand-banner {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  overflow: hidden;
  border-radius: 18px;
  background-color: var(--el-color-primary-light-9);
}

.brand-visual {
  position: relative;
  min-height: 280px;

  img {
    position: absolute;
    inset: 0;
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
    object-position: center 68%;
  }
}

.brand-copy {
  align-self: center;
  padding: 2.25rem 2.5rem;

  h2 {
    margin: 0.9rem 0 0.75rem;
    color: var(--el-text-color-primary);
    font-size: 1.5rem;
    font-weight: 700;
  }

  p {
    margin: 0 0 1.3rem;
    color: var(--el-text-color-regular);
    font-size: 0.9375rem;
    line-height: 1.85;
  }
}

.brand-kicker {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  color: var(--el-text-color-regular);
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.12em;

  i {
    font-size: 1.3rem;
  }
}

.brand-link {
  display: inline-flex;
  align-items: center;
  gap: 1rem;
  padding: 0.8rem 1.4rem;
  border-radius: 999px;
  background-color: var(--el-text-color-primary);
  color: var(--el-bg-color);
  font-size: 0.875rem;
  font-weight: 600;
  text-decoration: none;
  transition: background-color 0.2s;

  &:hover {
    background-color: var(--el-color-primary);
    color: #fff;
  }
}

@media (max-width: 900px) {
  .category-grid,
  .hot-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .hero {
    aspect-ratio: auto;
    min-height: 420px;
    padding: 2rem 1.5rem;
    background-position:
      center,
      center bottom;
    background-size:
      cover,
      100% auto;
  }

  .hero-title {
    font-size: 1.5rem;
  }

  .hero-actions {
    right: 1.5rem;
    bottom: 1.5rem;
    gap: 0.5rem;
    max-width: calc(100% - 3rem);

    .el-button {
      padding-right: 12px;
      padding-left: 12px;
    }
  }

  .home-section {
    margin-bottom: 2rem;
  }

  .home-section .section-title {
    font-size: 1.25rem;
  }

  .section-description,
  .section-heading .more-link {
    font-size: 0.8rem;
  }

  .category-grid,
  .hot-grid {
    gap: 0.75rem;
  }

  .category-art {
    height: 190px;
    padding: 1rem 0.5rem 0.5rem;
  }

  .category-info {
    padding: 0.25rem 1rem 1rem;

    h3 {
      font-size: 1rem;
    }

    p {
      font-size: 0.75rem;
    }
  }

  .category-arrow {
    right: 1rem;
    bottom: 1.2rem;
    font-size: 1rem;
  }

  .hot-grid :deep(.card-body) {
    padding: 0.75rem;
  }

  .hot-grid :deep(.card-name) {
    font-size: 0.875rem;
  }

  .brand-banner {
    grid-template-columns: 1fr;
  }

  .brand-visual {
    min-height: 220px;
  }

  .brand-copy {
    padding: 1.5rem;

    h2 {
      font-size: 1.35rem;
    }
  }
}

@media (max-width: 380px) {
  .category-art {
    height: 170px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .category-card,
  .category-image,
  .brand-link {
    transition: none;
  }

  .category-card:hover,
  .category-card:hover .category-image {
    transform: none;
  }
}
</style>
