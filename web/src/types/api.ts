import type { UserRole } from '@/stores/user'

// 后端接口的数据结构：字段与 api/ 实体的 JSON 序列化结果一致（驼峰命名）

// 统一响应（对应 ResultTemplate）：status 为 false 时 payload 里只有 message
export type ApiResult<T = object> =
  | { status: true; payload: T & { message?: string } }
  | { status: false; payload: { message?: string } }

// 分页信息（对应 Pagination）
export interface Pagination {
  total: number
  pageSize: number
  pageCount: number
  currentPage: number
}

// 用户（user 表；密码只写不读，不会下发）
export interface User {
  id: number
  username: string
  name: string | null
  gender: 'F' | 'M' | null
  email: string | null
  phone: string | null
  avatarUrl: string | null
  role: UserRole
  status: number // 1 启用 / 0 禁用
  registeredAt: string
  updatedAt: string
}

// 商品（product 表）
export interface Product {
  id: number
  name: string
  subtitle: string | null
  description: string | null
  category: string
  price: number // 单位：元
  stock: number
  sales: number
  mainImage: string | null
  status: number // 1 上架 / 0 下架
  createdAt: string
  updatedAt: string
}

export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'SHIPPED' | 'COMPLETED' | 'CANCELLED'

// 订单主表（orders 表；收货信息是下单时的快照）
export interface Order {
  id: number
  orderNo: string
  userId: number
  totalAmount: number
  status: OrderStatus
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  remark: string | null
  paidAt: string | null
  shippedAt: string | null
  completedAt: string | null
  cancelledAt: string | null
  createdAt: string
  updatedAt: string
}

// 订单明细（order_item 表；商品名、主图、单价是下单时的快照）
export interface OrderItem {
  id: number
  orderId: number
  productId: number
  productName: string
  productImage: string | null
  unitPrice: number
  quantity: number
  createdAt: string
}

export type PaymentMethod = 'ALIPAY' | 'WECHAT' | 'MOCK'

export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED'

// 支付流水（payment 表；一单可有多条流水）
export interface Payment {
  id: number
  paymentNo: string
  orderId: number
  userId: number
  amount: number
  method: PaymentMethod
  status: PaymentStatus
  paidAt: string | null
  createdAt: string
  updatedAt: string
}

// 仪表盘统计
export interface DashboardStats {
  userCount: number
  productCount: number
  orderCount: number
  salesAmount: number // 已支付、已发货、已完成订单的金额合计
}
