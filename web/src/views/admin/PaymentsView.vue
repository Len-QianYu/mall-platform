<template>
  <div class="page-heading">
    <h1 class="page-title">支付流水</h1>
  </div>

  <ElCard shadow="never">
    <!-- 筛选 -->
    <ElForm :model="filter" inline class="filter-form" @submit.prevent="search">
      <ElFormItem label="流水号">
        <ElInput v-model="filter.keyword" placeholder="支付流水号" clearable style="width: 14rem">
          <template #prefix><i class="bi bi-search"></i></template>
        </ElInput>
      </ElFormItem>
      <ElFormItem label="支付方式">
        <ElSelect v-model="filter.method" placeholder="全部" clearable style="width: 8rem">
          <ElOption
            v-for="(label, code) in PAYMENT_METHOD"
            :key="code"
            :label="label"
            :value="code"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="状态">
        <ElSelect v-model="filter.status" placeholder="全部" clearable style="width: 8rem">
          <ElOption
            v-for="(item, code) in PAYMENT_STATUS"
            :key="code"
            :label="item.label"
            :value="code"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" native-type="submit">查询</ElButton>
        <ElButton @click="resetFilter">重置</ElButton>
      </ElFormItem>
    </ElForm>

    <!-- 流水只读：由支付回调写入，管理端不改 -->
    <ElTable v-loading="loading" :data="payments" border stripe>
      <ElTableColumn prop="paymentNo" label="流水号" min-width="200" />
      <ElTableColumn prop="orderId" label="订单 ID" width="100" />
      <ElTableColumn prop="userId" label="用户 ID" width="100" />
      <ElTableColumn label="金额" width="120">
        <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
      </ElTableColumn>
      <ElTableColumn label="支付方式" width="110">
        <template #default="{ row }">{{ PAYMENT_METHOD[row.method] ?? row.method }}</template>
      </ElTableColumn>
      <ElTableColumn label="状态" width="100">
        <template #default="{ row }">
          <ElTag :type="PAYMENT_STATUS[row.status]?.type ?? 'info'">
            {{ PAYMENT_STATUS[row.status]?.label ?? row.status }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="支付时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.paidAt) }}</template>
      </ElTableColumn>
      <ElTableColumn label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
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
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, Pagination, Payment, PaymentMethod, PaymentStatus } from '@/types/api'
import { PAYMENT_METHOD, PAYMENT_STATUS, formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const payments = ref<Payment[]>([])

// 下拉框清空后值为 undefined，axios 不会把 undefined 参数拼进查询串
const filter = reactive({
  keyword: '',
  method: undefined as PaymentMethod | undefined,
  status: undefined as PaymentStatus | undefined,
})

const pagination = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0,
})

const fetchPayments = async () => {
  loading.value = true
  try {
    const { data } = await request.get<ApiResult<{ payments: Payment[]; pagination: Pagination }>>(
      '/admin/payment',
      {
        params: {
          page: pagination.currentPage,
          pageSize: pagination.pageSize,
          keyword: filter.keyword.trim() || undefined,
          method: filter.method,
          status: filter.status,
        },
      },
    )
    if (!data.status) {
      ElMessage.error(data.payload.message || '加载支付流水失败')
      return
    }
    payments.value = data.payload.payments
    pagination.total = data.payload.pagination.total
  } catch {
    ElMessage.error('加载支付流水失败')
  } finally {
    loading.value = false
  }
}

// 翻页、改每页条数时重新查询
watch(() => [pagination.currentPage, pagination.pageSize], fetchPayments, { immediate: true })

// 按新条件从第一页查；本来就在第一页时 watch 不会触发，直接查
const search = () => {
  if (pagination.currentPage === 1) {
    fetchPayments()
  } else {
    pagination.currentPage = 1
  }
}

const resetFilter = () => {
  filter.keyword = ''
  filter.method = undefined
  filter.status = undefined
  search()
}
</script>
