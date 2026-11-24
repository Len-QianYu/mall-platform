import type { NotificationItem, OrderNotification } from '@/types/api'
import { t } from '@/i18n'
import { formatMoney } from '@/utils/format'
import {
  notificationDescription as productDescription,
  PRODUCT_NOTIFICATION_LABELS,
} from '@/utils/productNotifications'

export const notificationLabel = (item: NotificationItem): string =>
  item.type === 'NEW_ORDER' ? '新订单' : PRODUCT_NOTIFICATION_LABELS[item.type]

export const notificationName = (item: NotificationItem): string =>
  item.type === 'NEW_ORDER' ? t('订单 {orderNo}', { orderNo: item.orderNo }) : item.productName

export const orderBuyer = (item: OrderNotification): string => {
  const name = item.buyerName?.trim()
  return name && name !== item.buyerUsername
    ? `${name} (${item.buyerUsername})`
    : item.buyerUsername
}

export const notificationDescription = (item: NotificationItem): string =>
  item.type === 'NEW_ORDER'
    ? t('买家：{buyer} · 金额：{amount}', {
        buyer: orderBuyer(item),
        amount: formatMoney(item.totalAmount),
      })
    : productDescription(item)

export const notificationTarget = (item: NotificationItem) =>
  item.type === 'NEW_ORDER'
    ? { name: 'admin_orders', query: { orderId: String(item.orderId) } }
    : { name: 'product_detail', params: { id: item.productId } }
