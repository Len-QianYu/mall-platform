<template>
  <div class="page-heading">
    <h1 class="page-title">{{ t('订单管理') }}</h1>
  </div>

  <ElCard shadow="never">
    <!-- 筛选 -->
    <ElForm :model="filter" inline class="filter-form" @submit.prevent="search">
      <ElFormItem :label="t('关键字')">
        <ElInput
          v-model="filter.keyword"
          :placeholder="t('订单号 / 收货人 / 手机号')"
          clearable
          style="width: 16rem"
        >
          <template #prefix><i class="bi bi-search"></i></template>
        </ElInput>
      </ElFormItem>
      <ElFormItem :label="t('状态')">
        <ElSelect v-model="filter.status" :placeholder="t('全部')" clearable style="width: 8rem">
          <ElOption
            v-for="(item, code) in ORDER_STATUS"
            :key="code"
            :label="t(item.label)"
            :value="code"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" native-type="submit">{{ t('查询') }}</ElButton>
        <ElButton @click="resetFilter">{{ t('重置') }}</ElButton>
      </ElFormItem>
    </ElForm>

    <ElTable v-loading="loading" :data="orders" border stripe>
      <ElTableColumn prop="orderNo" :label="t('订单号')" min-width="200" />
      <ElTableColumn :label="t('收货人')" min-width="150">
        <template #default="{ row }">
          <div>{{ row.receiverName }}</div>
          <div class="text-secondary">{{ row.receiverPhone }}</div>
        </template>
      </ElTableColumn>
      <ElTableColumn :label="t('订单金额')" width="120">
        <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
      </ElTableColumn>
      <ElTableColumn :label="t('状态')" width="100">
        <template #default="{ row }">
          <ElTag :type="ORDER_STATUS[row.status]?.type ?? 'info'">
            {{ t(ORDER_STATUS[row.status]?.label ?? row.status) }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn :label="t('下单时间')" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </ElTableColumn>
      <ElTableColumn :label="t('操作')" width="150" fixed="right">
        <template #default="{ row }">
          <ElButton link type="primary" @click="openDetail(row.id)">{{ t('详情') }}</ElButton>
          <!-- 已支付的订单才能发货 -->
          <ElPopconfirm
            v-if="row.status === 'PAID'"
            :title="t('确定该订单已发货吗？')"
            @confirm="changeStatus(row.id, 'ship')"
          >
            <template #reference>
              <ElButton link type="success">{{ t('发货') }}</ElButton>
            </template>
          </ElPopconfirm>
          <!-- 只有待支付的订单能直接取消；已支付、未发货的走退款（已发货的要先退货，暂不支持） -->
          <ElPopconfirm
            v-if="row.status === 'PENDING_PAYMENT'"
            :title="t('确定取消该订单吗？')"
            @confirm="changeStatus(row.id, 'cancel')"
          >
            <template #reference>
              <ElButton link type="danger">{{ t('取消') }}</ElButton>
            </template>
          </ElPopconfirm>
          <ElPopconfirm
            v-if="row.status === 'PAID'"
            :title="t('确定给该订单退款吗？库存会回补、销量会扣回')"
            @confirm="changeStatus(row.id, 'refund')"
          >
            <template #reference>
              <ElButton link type="danger">{{ t('退款') }}</ElButton>
            </template>
          </ElPopconfirm>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElPagination
      v-model:current-page="pagination.currentPage"
      v-model:page-size="pagination.pageSize"
      class="table-pagination"
      :page-sizes="[10, 20, 50]"
      :total="pagination.total"
      layout="total, sizes, prev, pager, next, jumper"
      background
    />
  </ElCard>

  <!-- 订单详情抽屉 -->
  <ElDrawer v-model="detailVisible" :title="t('订单详情')" size="min(640px, 100vw)">
    <div v-loading="detailLoading" class="detail-body">
      <template v-if="detail">
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem :label="t('订单号')">{{ detail.order.orderNo }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('状态')">
            <ElTag :type="ORDER_STATUS[detail.order.status]?.type ?? 'info'">
              {{ t(ORDER_STATUS[detail.order.status]?.label ?? detail.order.status) }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('订单金额')">
            {{ formatMoney(detail.order.totalAmount) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('用户 ID')">{{ detail.order.userId }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('收货人')">{{
            detail.order.receiverName
          }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('联系电话')">{{
            detail.order.receiverPhone
          }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('收货地址')" :span="2">
            {{ detail.order.receiverAddress }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('买家备注')" :span="2">
            {{ detail.order.remark || '-' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('下单时间')">
            {{ formatDateTime(detail.order.createdAt) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('支付时间')">
            {{ formatDateTime(detail.order.paidAt) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('发货时间')">
            {{ formatDateTime(detail.order.shippedAt) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('完成时间')">
            {{ formatDateTime(detail.order.completedAt) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem v-if="detail.order.cancelledAt" :label="t('取消时间')" :span="2">
            {{ formatDateTime(detail.order.cancelledAt) }}
          </ElDescriptionsItem>
        </ElDescriptions>

        <h3 class="detail-subtitle">{{ t('商品明细') }}</h3>
        <!-- 商品名、主图、单价都是下单时的快照，不随商品修改变化 -->
        <ElTable :data="detail.items" border>
          <ElTableColumn :label="t('商品')" min-width="220">
            <template #default="{ row }">
              <div class="product-cell">
                <ElImage
                  v-if="row.productImage"
                  class="product-image"
                  :src="row.productImage"
                  fit="cover"
                />
                <div v-else class="product-image image-placeholder">
                  <i class="bi bi-image"></i>
                </div>
                <span>{{ row.productName }}</span>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn :label="t('单价')" width="100">
            <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
          </ElTableColumn>
          <ElTableColumn prop="quantity" :label="t('数量')" width="70" />
          <ElTableColumn :label="t('小计')" width="110">
            <template #default="{ row }">{{ formatMoney(row.unitPrice * row.quantity) }}</template>
          </ElTableColumn>
        </ElTable>
      </template>
      <div v-if="detailError" class="order-detail-error" role="alert">
        <p>{{ t(detailError) }}</p>
        <ElButton @click="retryDetail">{{ t('重新加载') }}</ElButton>
      </div>
    </div>
  </ElDrawer>
</template>

<script setup lang="ts">
import { t } from '@/i18n'
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElPopconfirm,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, Order, OrderItem, OrderStatus, Pagination } from '@/types/api'
import { ORDER_STATUS, formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const orders = ref<Order[]>([])
const route = useRoute()
const router = useRouter()

// 下拉框清空后值为 undefined，axios 不会把 undefined 参数拼进查询串
const filter = reactive({
  keyword: '',
  status: undefined as OrderStatus | undefined,
})

const pagination = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0,
})

const fetchOrders = async () => {
  loading.value = true
  try {
    const { data } = await request.get<ApiResult<{ orders: Order[]; pagination: Pagination }>>(
      '/admin/order',
      {
        params: {
          page: pagination.currentPage,
          pageSize: pagination.pageSize,
          keyword: filter.keyword.trim() || undefined,
          status: filter.status,
        },
      },
    )
    if (!data.status) {
      ElMessage.error(t(data.payload.message || '加载订单列表失败'))
      return
    }
    orders.value = data.payload.orders
    pagination.total = data.payload.pagination.total
  } catch {
    ElMessage.error(t('加载订单列表失败'))
  } finally {
    loading.value = false
  }
}

// 翻页、改每页条数时重新查询
watch(() => [pagination.currentPage, pagination.pageSize], fetchOrders, { immediate: true })

// 按新条件从第一页查；本来就在第一页时 watch 不会触发，直接查
const search = () => {
  if (pagination.currentPage === 1) {
    fetchOrders()
  } else {
    pagination.currentPage = 1
  }
}

const resetFilter = () => {
  filter.keyword = ''
  filter.status = undefined
  search()
}

// 发货、取消会同时改订单状态和对应时间，成功后重新拉列表
const ACTION_MESSAGE = { ship: '已发货', cancel: '订单已取消', refund: '已退款' }

const changeStatus = async (id: number, action: keyof typeof ACTION_MESSAGE) => {
  try {
    const { data } = await request.post<ApiResult>(`/admin/order/${action}`, { id })
    if (!data.status) {
      ElMessage.error(t(data.payload.message || '操作失败'))
      return
    }
    ElMessage.success(t(ACTION_MESSAGE[action]))
    fetchOrders()
  } catch {
    ElMessage.error(t('操作失败'))
  }
}

// 详情抽屉
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<{ order: Order; items: OrderItem[] } | null>(null)
const detailError = ref('')
let detailSequence = 0

const openDetail = (id: number) =>
  router.push({ name: 'admin_orders', query: { ...route.query, orderId: String(id) } })

const fetchDetail = async (id: number) => {
  const current = ++detailSequence
  detail.value = null
  detailError.value = ''
  detailVisible.value = true
  detailLoading.value = true
  try {
    const { data } = await request.get<ApiResult<{ order: Order; items: OrderItem[] }>>(
      `/admin/order/${id}`,
    )
    if (current !== detailSequence || !detailVisible.value) return
    if (!data.status) {
      detailError.value = data.payload.message || '加载订单详情失败'
      return
    }
    detail.value = { order: data.payload.order, items: data.payload.items }
  } catch {
    if (current === detailSequence && detailVisible.value) detailError.value = '加载订单详情失败'
  } finally {
    if (current === detailSequence) detailLoading.value = false
  }
}

const selectedOrderId = () => {
  const value = route.query.orderId
  return typeof value === 'string' &&
    /^[1-9]\d*$/.test(value) &&
    Number.isSafeInteger(Number(value))
    ? Number(value)
    : null
}

const retryDetail = () => {
  const id = selectedOrderId()
  if (id !== null) void fetchDetail(id)
}

watch(
  () => route.query.orderId,
  () => {
    const id = selectedOrderId()
    if (id !== null) void fetchDetail(id)
    else {
      detailSequence += 1
      detailVisible.value = false
      detail.value = null
      detailError.value = ''
      detailLoading.value = false
    }
  },
  { immediate: true },
)

watch(detailVisible, (visible) => {
  if (visible || route.query.orderId === undefined) return
  detailSequence += 1
  const query = { ...route.query }
  delete query.orderId
  void router.replace({ query })
})

onBeforeUnmount(() => {
  detailSequence += 1
})
</script>

<style lang="scss" scoped>
.detail-body {
  min-height: 200px;
}
.order-detail-error {
  padding: 2rem 0;
  text-align: center;
  color: var(--el-color-danger);
}

.detail-subtitle {
  margin: 1.5rem 0 0.75rem;
  color: var(--el-text-color-primary);
  font-size: 1rem;
  font-weight: 600;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.product-image {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 6px;
}

.image-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-placeholder);
}
</style>
