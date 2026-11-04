<template>
  <RouterLink class="product-card" :to="{ name: 'product_detail', params: { id: product.id } }">
    <div class="card-image-wrap">
      <ElImage
        v-if="product.mainImage"
        class="card-image"
        :src="assetUrl(product.mainImage)"
        fit="cover"
        lazy
      />
      <div v-else class="card-image image-placeholder">
        <i class="bi bi-image"></i>
      </div>
      <span v-if="product.stock === 0" class="sold-out">已售罄</span>
    </div>

    <div class="card-body">
      <div class="card-name" :title="product.name">{{ product.name }}</div>
      <div class="card-subtitle text-secondary">{{ product.subtitle || product.category }}</div>
      <div class="card-footer">
        <span class="price">{{ formatMoney(product.price) }}</span>
        <span class="text-secondary">已售 {{ product.sales }}</span>
      </div>
    </div>
  </RouterLink>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { ElImage } from 'element-plus'
import type { Product } from '@/types/api'
import { assetUrl, formatMoney } from '@/utils/format'

defineProps<{ product: Product }>()
</script>

<style lang="scss" scoped>
.product-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background-color: var(--el-bg-color);
  text-decoration: none;
  transition:
    box-shadow 0.2s,
    transform 0.2s;

  &:hover {
    box-shadow: var(--el-box-shadow-light);
    transform: translateY(-2px);
  }
}

.card-image-wrap {
  position: relative;
  aspect-ratio: 1;
}

.card-image {
  width: 100%;
  height: 100%;
  font-size: 2.5rem;
}

.sold-out {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 1rem;
  font-weight: 600;
  letter-spacing: 0.1em;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.75rem;
}

.card-name {
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 0.875rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-footer {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 0.25rem;

  .price {
    font-size: 1.0625rem;
  }
}
</style>
