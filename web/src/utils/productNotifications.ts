import type { ProductNotification, ProductNotificationType } from '@/types/api'
import { formatMoney } from '@/utils/format'
import { t } from '@/i18n'

export const PRODUCT_NOTIFICATION_LABELS: Record<ProductNotificationType, string> = {
  LISTED: '商品上架',
  PRICE_DROP: '降价提醒',
  PRICE_RISE: '涨价提醒',
}

export const notificationDescription = (notification: ProductNotification): string => {
  if (notification.type === 'LISTED')
    return t('你收藏的「{name}」已上架，当前售价 {newPrice}', {
      name: notification.productName,
      newPrice: formatMoney(notification.newPrice),
    })
  const change = notification.type === 'PRICE_DROP' ? '降价' : '涨价'
  return t('你收藏的「{name}」{change}了，从 {oldPrice} 变为 {newPrice}', {
    name: notification.productName,
    change: t(change),
    oldPrice: formatMoney(notification.oldPrice),
    newPrice: formatMoney(notification.newPrice),
  })
}

export const notificationSocketUrl = (ticket: string): string => {
  const base = new URL(
    import.meta.env.APP_BASE_URL || window.location.origin,
    window.location.origin,
  )
  if (!base.pathname.endsWith('/')) base.pathname += '/'
  const url = new URL('ws/notifications', base)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  url.searchParams.set('ticket', ticket)
  return url.href
}
