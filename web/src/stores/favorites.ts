import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import type { ApiResult } from '@/types/api'

export const useFavoritesStore = defineStore('favorites', () => {
  const userStore = useUserStore()
  const productIds = ref(new Set<number>())
  const pending = ref(new Set<number>())
  const revision = ref(0)
  const loaded = ref(false)
  let generation = 0
  let loading: Promise<void> | null = null

  const refresh = (): Promise<void> => {
    if (!userStore.logged) return Promise.resolve()
    if (loading) return loading
    const current = generation
    loading = (async () => {
      try {
        const { data } = await request.get<ApiResult<{ productIds: number[] }>>('/favorite/ids')
        if (current !== generation) return
        if (!data.status) throw new Error(data.payload.message || '加载收藏失败')
        productIds.value = new Set(data.payload.productIds)
        loaded.value = true
      } finally {
        if (current === generation) loading = null
      }
    })()
    return loading
  }

  watch(
    () => userStore.user?.token,
    () => {
      generation += 1
      productIds.value = new Set()
      pending.value = new Set()
      loaded.value = false
      loading = null
      revision.value += 1
      if (userStore.logged) void refresh().catch(() => undefined)
    },
    { immediate: true, flush: 'sync' },
  )

  const isFavorite = (productId: number): boolean => productIds.value.has(productId)

  const toggle = async (productId: number): Promise<boolean | null> => {
    if (!userStore.logged || pending.value.has(productId)) return null
    const current = generation
    pending.value.add(productId)
    try {
      // 等收藏状态加载完成再切换，避免刚登录时把已有收藏误当成未收藏。
      if (!loaded.value) await refresh()
      if (current !== generation) return null
      const favorite = !isFavorite(productId)
      const { data } = await request.post<ApiResult<{ favorite: boolean }>>('/favorite', {
        productId,
        favorite,
      })
      if (current !== generation) return null
      if (!data.status) throw new Error(data.payload.message || '更新收藏失败')
      if (data.payload.favorite) productIds.value.add(productId)
      else productIds.value.delete(productId)
      revision.value += 1
      return data.payload.favorite
    } finally {
      if (current === generation) pending.value.delete(productId)
    }
  }

  return { productIds, pending, revision, loaded, isFavorite, refresh, toggle }
})
