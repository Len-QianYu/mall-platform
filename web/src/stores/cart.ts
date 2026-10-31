import { ref } from 'vue'
import { defineStore } from 'pinia'
import request from '@/utils/request'
import type { ApiResult, CartItem } from '@/types/api'

// 能否结算：勾选、在售、库存够。购物车页和结算页用同一条规则，避免两边算出的商品不一致
export const isSettleable = (item: CartItem): boolean =>
  item.checked === 1 && item.productStatus === 1 && item.stock >= item.quantity

// 只存顶栏角标要用的商品种数；购物车明细由购物车页自己拉取
export const useCartStore = defineStore('cart', () => {
  const count = ref(0)

  // 加购、删除、下单后调用；失败时保留旧值，角标不准不影响下单
  const refresh = async (): Promise<void> => {
    try {
      const { data } = await request.get<ApiResult<{ items: CartItem[] }>>('/cart')
      if (data.status) {
        count.value = data.payload.items.length
      }
    } catch {
      // 忽略：角标只是提示
    }
  }

  const reset = (): void => {
    count.value = 0
  }

  return { count, refresh, reset }
})
