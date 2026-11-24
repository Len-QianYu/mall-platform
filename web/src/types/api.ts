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
  province: string | null
  city: string | null
  receiverAddress: string | null
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

export interface ProductFavorite {
  productId: number
  productName: string
  productImage: string | null
  category: string
  price: number
  stock: number
  productStatus: number
  favoritedAt: string
}

export type ProductNotificationType = 'LISTED' | 'PRICE_DROP' | 'PRICE_RISE'

export interface ProductNotification {
  id: number
  productId: number
  type: ProductNotificationType
  productName: string
  productImage: string | null
  oldPrice: number
  newPrice: number
  readAt: string | null
  createdAt: string
}

export interface OrderNotification {
  id: number
  type: 'NEW_ORDER'
  orderId: number
  orderNo: string
  buyerId: number
  buyerUsername: string
  buyerName: string | null
  totalAmount: number
  readAt: string | null
  createdAt: string
}

export type NotificationItem = ProductNotification | OrderNotification

export type SeckillPhase = 'UPCOMING' | 'LIVE' | 'SOLD_OUT' | 'ENDED' | 'CLOSED'

export interface SeckillActivity {
  id: number
  productId: number
  title: string
  productName: string
  productSubtitle: string | null
  productImage: string | null
  productStatus: number
  originalPrice: number
  seckillPrice: number
  totalStock: number
  stock: number
  returnedStock: number
  limitPerUser: number
  startsAt: string
  endsAt: string
  startsAtEpochMs: number
  endsAtEpochMs: number
  serverTime: number
  status: number
  phase: SeckillPhase
}

export type OrderStatus =
  'PENDING_PAYMENT' | 'PAID' | 'SHIPPED' | 'COMPLETED' | 'CANCELLED' | 'REFUNDED'

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
  seckillActivityId?: number | null
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
  tradeNo: string | null
  orderId: number
  userId: number
  amount: number
  method: PaymentMethod
  status: PaymentStatus
  paidAt: string | null
  createdAt: string
  updatedAt: string
}

export interface PaymentStartResult {
  payment: Payment
  payUrl: string | null
}

export interface PaymentQueryResult {
  order: Order
  payment: Payment | null
  paid: boolean
}

export interface PaymentMethodsResult {
  methods: PaymentMethod[]
  alipaySandbox: boolean
}

// 仪表盘统计
export interface DashboardStats {
  userCount: number
  productCount: number
  orderCount: number
  salesAmount: number // 已支付、已发货、已完成订单的金额合计
}

export interface DashboardTrendPoint {
  date: string // YYYY-MM-DD
  orderCount: number // 当日创建的全部订单
  salesAmount: number // 当日支付且未退款的订单金额
}

export interface OrderStatusCount {
  status: OrderStatus
  count: number
}

export interface DashboardData {
  stats: DashboardStats
  recentOrders: Order[]
  trend: DashboardTrendPoint[]
  orderStatuses: OrderStatusCount[]
}

/*
 * 商城前台（用户侧）接口约定（后端实现见 api/ 的 ProductController、CartController、OrderController、PaymentController、UserController）：
 *
 *   公开（游客可访问，SecurityConfiguration 只对这几个 GET 放行）：
 *     GET  /product                 page, pageSize, keyword, category, sort → { products, pagination }（只含上架商品）
 *     GET  /product/categories      → { categories }
 *     GET  /product/{id}            → { product }（不存在或已下架时 status=false）
 *
 *   需登录（只能操作自己的数据，用户 ID 取自 JWT，不信任请求体）：
 *     GET  /favorite/ids            → { productIds }
 *     GET  /favorite                page, pageSize → { favorites, pagination }（包含已下架商品）
 *     POST /favorite                { productId, favorite } → 幂等设置收藏及通知订阅
 *     GET  /notification            page, pageSize, unreadOnly → { notifications, pagination, unreadCount }
 *     GET  /notification/unread-count → { unreadCount }
 *     POST /notification/read       { id } → 仅标记自己的通知
 *     POST /notification/read-all   → 仅标记自己的全部通知
 *     POST /notification/socket-ticket → { ticket }（30 秒内一次有效）
 *     WS   /ws/notifications?ticket=... → 收藏商品的上架、降价、涨价实时提醒
 *     GET  /cart                    → { items: CartItem[] }
 *     POST /cart/create             { productId, quantity }      已在购物车则累加数量
 *     POST /cart/update             { id, quantity?, checked? }
 *     POST /cart/check-all          { checked }
 *     POST /cart/delete             { ids }
 *     POST /order/create            { requestId, items: [{ productId, quantity }], fromCart, receiverName,
 *                                     receiverPhone, receiverAddress, remark } → { order }
 *                                   价格以服务端查库为准；fromCart=true 时下单成功后从购物车移除这些商品
 *                                   新结算生成 requestId；失败重试复用，同一用户同号同内容返回已有订单
 *     GET  /order                   page, pageSize, status → { orders: OrderWithItems[], pagination }
 *     GET  /order/{id}              → { order, items }
 *     POST /order/cancel            { id }    待支付 → 已取消
 *     POST /order/confirm           { id }    已发货 → 已完成
 *     POST /order/refund            { id }    已支付（未发货）→ 已退款，回补库存、扣回销量
 *     GET  /payment/methods         → { methods, alipaySandbox }（按后端配置启用）
 *     POST /payment/pay             { orderId, method } → { payment, payUrl }
 *                                   ALIPAY 返回待支付流水和收银台 URL；MOCK 直接成功
 *     GET  /payment/query           orderId → { order, payment, paid }（查询并同步支付宝状态）
 *     POST /payment/alipay/notify    支付宝异步通知：此接口免 JWT，RSA2 验签后处理
 *     GET  /user/profile            → { user }
 *     POST /user/update             { name, gender, email, phone, province, city, receiverAddress,
 *                                     avatarUrl }
 *     POST /user/password           { oldPassword, newPassword }
 */

// 商品排序（GET /product 的 sort 参数）：综合 / 销量 / 新品 / 价格升序 / 价格降序
export type ProductSort = 'default' | 'sales' | 'newest' | 'price_asc' | 'price_desc'

// 购物车条目（cart 表关联 product）：商品名、主图、价格、库存、上下架都取商品当前值，不是快照
export interface CartItem {
  id: number
  productId: number
  quantity: number
  checked: number // 1 勾选 / 0 未勾选
  productName: string
  productImage: string | null
  price: number
  stock: number
  productStatus: number // 1 上架 / 0 下架：下架后仍留在购物车里，但不能结算
}

// 我的订单列表：订单主表带上商品明细，列表里直接展示商品
export interface OrderWithItems extends Order {
  items: OrderItem[]
}
