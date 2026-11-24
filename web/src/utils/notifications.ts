import type { NotificationItem, OrderNotification, ProductNotification } from '@/types/api'
import { t } from '@/i18n'
import { formatMoney } from '@/utils/format'
import {
  notificationDescription as productDescription,
  PRODUCT_NOTIFICATION_LABELS,
} from '@/utils/productNotifications'

export const isProductNotification = (item: NotificationItem): item is ProductNotification =>
  item.type === 'LISTED' || item.type === 'PRICE_DROP' || item.type === 'PRICE_RISE'

export const notificationKey = (item: NotificationItem): string => `${item.type}:${item.id}`

export const notificationLabel = (item: NotificationItem): string => {
  if (item.type === 'NEW_ORDER') return '新订单'
  if (item.type === 'ORDER_SHIPPED') return '已发货'
  return PRODUCT_NOTIFICATION_LABELS[item.type]
}

export const notificationName = (item: NotificationItem): string =>
  isProductNotification(item) ? item.productName : t('订单 {orderNo}', { orderNo: item.orderNo })

export const orderBuyer = (item: OrderNotification): string => {
  const name = item.buyerName?.trim()
  return name && name !== item.buyerUsername
    ? `${name} (${item.buyerUsername})`
    : item.buyerUsername
}

export const notificationDescription = (item: NotificationItem): string => {
  if (item.type === 'NEW_ORDER')
    return t('买家：{buyer} · 金额：{amount}', {
      buyer: orderBuyer(item),
      amount: formatMoney(item.totalAmount),
    })
  if (item.type === 'ORDER_SHIPPED')
    return t('你的订单 {orderNo} 已发货，请留意收货', { orderNo: item.orderNo })
  return productDescription(item)
}

export const notificationTarget = (item: NotificationItem) => {
  if (item.type === 'NEW_ORDER')
    return { name: 'admin_orders', query: { orderId: String(item.orderId) } }
  if (item.type === 'ORDER_SHIPPED') return { name: 'order_detail', params: { id: item.orderId } }
  return { name: 'product_detail', params: { id: item.productId } }
}
