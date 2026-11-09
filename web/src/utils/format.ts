import type { OrderStatus, PaymentMethod, PaymentStatus } from '@/types/api'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

type StatusTag = { label: string; type: TagType }

// 金额：后端 DECIMAL 序列化为数字，统一显示两位小数
export const formatMoney = (value: number | null | undefined): string =>
  value === null || value === undefined ? '-' : `¥${value.toFixed(2)}`

// 时间：后端 LocalDateTime 序列化为 2026-10-31T12:00:00，显示时把 T 换成空格
export const formatDateTime = (value: string | null | undefined): string =>
  value ? value.replace('T', ' ').slice(0, 19) : '-'

// 图片地址：上传的文件在库里存相对路径（如 /images/xxx.png），文件由后端提供，拼上后端地址才能访问；
// 完整的外链 URL 原样返回。没有图片时返回 undefined，ElAvatar 会改为显示首字母。
// 传入确定的字符串（如 v-if 判断过之后）时返回 string，可以直接放进 ElImage 的 preview-src-list
export function assetUrl(path: string): string
export function assetUrl(path: string | null | undefined): string | undefined
export function assetUrl(path: string | null | undefined): string | undefined {
  return path ? new URL(path, import.meta.env.APP_BASE_URL).href : undefined
}

// 以下字典的键与数据库里的英文编码一一对应（见 schema.sql 列注释）。
// 按 string 取值：编码来自后端，可能取不到，调用方兜底显示原编码；satisfies 保证每个编码都有条目

export const ORDER_STATUS: Record<string, StatusTag> = {
  PENDING_PAYMENT: { label: '待支付', type: 'info' },
  PAID: { label: '已支付', type: 'warning' }, // 等待发货，需要管理员处理
  SHIPPED: { label: '已发货', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'danger' },
  REFUNDED: { label: '已退款', type: 'info' },
} satisfies Record<OrderStatus, StatusTag>

export const PAYMENT_STATUS: Record<string, StatusTag> = {
  PENDING: { label: '待支付', type: 'info' },
  SUCCESS: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  REFUNDED: { label: '已退款', type: 'warning' },
} satisfies Record<PaymentStatus, StatusTag>

export const PAYMENT_METHOD: Record<string, string> = {
  ALIPAY: '支付宝',
  WECHAT: '微信',
  MOCK: '模拟支付',
} satisfies Record<PaymentMethod, string>
