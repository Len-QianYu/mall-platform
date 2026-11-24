<template>
  <button
    type="button"
    class="favorite-button"
    :class="{ 'is-favorite': favorites.isFavorite(productId), 'with-label': showLabel }"
    :aria-label="
      t(favorites.isFavorite(productId) ? '取消收藏{name}' : '收藏{name}', {
        name: productName || t('这件商品'),
      })
    "
    :aria-pressed="favorites.isFavorite(productId)"
    :aria-busy="favorites.pending.has(productId)"
    :disabled="favorites.pending.has(productId)"
    :title="favorites.isFavorite(productId) ? t('取消收藏') : t('收藏后接收上架、降价和涨价提醒')"
    @click.stop.prevent="toggle"
  >
    <i
      aria-hidden="true"
      :class="
        favorites.pending.has(productId)
          ? 'bi bi-arrow-repeat is-spinning'
          : favorites.isFavorite(productId)
            ? 'bi bi-heart-fill'
            : 'bi bi-heart'
      "
    ></i>
    <span v-if="showLabel">{{
      favorites.isFavorite(productId) ? t('已收藏') : t('收藏商品')
    }}</span>
  </button>
</template>

<script setup lang="ts">
import { t } from '@/i18n'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useFavoritesStore } from '@/stores/favorites'

const props = defineProps<{ productId: number; productName?: string; showLabel?: boolean }>()
const favorites = useFavoritesStore()
const user = useUserStore()
const route = useRoute()
const router = useRouter()

const toggle = async () => {
  if (!user.logged) {
    ElMessage.info(t('登录后即可收藏商品并接收提醒'))
    await router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    const favorite = await favorites.toggle(props.productId)
    if (favorite !== null)
      ElMessage.success(t(favorite ? '已收藏，上架、降价和涨价都会提醒你' : '已取消收藏'))
  } catch (error) {
    ElMessage.error(t(error instanceof Error ? error.message : '更新收藏失败，请重试'))
  }
}
</script>

<style lang="scss" scoped>
.favorite-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 50%;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font: inherit;
  cursor: pointer;
  transition:
    color 0.15s,
    border-color 0.15s;

  &:hover,
  &.is-favorite {
    border-color: var(--el-color-danger-light-5);
    color: var(--el-color-danger);
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
  }

  &:disabled {
    cursor: wait;
    opacity: 0.6;
  }

  &.with-label {
    width: auto;
    height: 40px;
    padding: 0 1rem;
    border-radius: var(--el-border-radius-base);
    font-size: 0.875rem;
  }
}

.is-spinning {
  animation: favorite-spin 1s linear infinite;
}
@keyframes favorite-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .favorite-button {
    transition: none;
  }
  .is-spinning {
    animation: none;
  }
}
</style>
