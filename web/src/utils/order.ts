import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import type { ApiResult } from '@/types/api'
import { t } from '@/i18n'

// 用户能对自己订单做的状态变更：取消（待支付 → 已取消）、确认收货（已发货 → 已完成）、退款（已支付 → 已退款）
export type UserOrderAction = 'cancel' | 'confirm' | 'refund'

const SUCCESS_MESSAGE: Record<UserOrderAction, string> = {
  cancel: '订单已取消',
  confirm: '已确认收货',
  refund: '退款成功，款项已原路退回',
}

// 订单列表和订单详情共用；返回是否成功，调用方据此刷新数据
export const changeOrderStatus = async (id: number, action: UserOrderAction): Promise<boolean> => {
  try {
    const { data } = await request.post<ApiResult>(`/order/${action}`, { id })
    if (!data.status) {
      ElMessage.error(t(data.payload.message || '操作失败'))
      return false
    }
    ElMessage.success(t(SUCCESS_MESSAGE[action]))
    return true
  } catch {
    ElMessage.error(t('操作失败'))
    return false
  }
}
