<template>
  <div class="page-heading">
    <h1 class="page-title">{{ t('秒杀活动') }}</h1>
    <div class="heading-actions">
      <RouterLink :to="{ name: 'seckill' }"
        ><ElButton>{{ t('查看商城') }}</ElButton></RouterLink
      >
      <ElButton type="primary" @click="openDrawer"
        ><i class="bi bi-plus-lg"></i>{{ t('创建活动') }}</ElButton
      >
    </div>
  </div>
  <ElAlert
    :title="
      t(
        '创建活动时会从商品普通库存划拨秒杀配额；关闭或到期后自动归还剩余配额。活动价和总配额在创建后固定。',
      )
    "
    type="info"
    :closable="false"
    show-icon
    class="inventory-hint"
  />
  <ElCard shadow="never">
    <div v-if="error" class="load-error" role="alert">
      {{ t(error) }} <ElButton @click="fetchActivities">{{ t('重试') }}</ElButton>
    </div>
    <ElTable v-loading="loading" :data="activities" border stripe>
      <ElTableColumn prop="id" label="ID" width="80" />
      <ElTableColumn :label="t('活动 / 商品')" min-width="230">
        <template #default="{ row }">
          <strong>{{ row.title }}</strong>
          <div class="text-secondary">
            {{ row.productName }} {{ t('· 商品 #') }}{{ row.productId }}
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn :label="t('秒杀价')" width="120">
        <template #default="{ row }">
          <strong class="seckill-price">{{ formatMoney(row.seckillPrice) }}</strong>
          <div class="text-secondary">
            <del>{{ formatMoney(row.originalPrice) }}</del>
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn :label="t('配额')" width="145">
        <template #default="{ row }">
          {{ t('剩余') }} {{ row.stock }} / {{ row.totalStock }}
          <div v-if="row.returnedStock" class="text-secondary">
            {{ t('已归还') }} {{ row.returnedStock }} {{ t('件') }}
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn :label="t('限购')" width="95"
        ><template #default="{ row }"
          >{{ t('每人') }} {{ row.limitPerUser }} {{ t('件') }}</template
        ></ElTableColumn
      >
      <ElTableColumn :label="t('活动时间')" width="185">
        <template #default="{ row }">
          <div>{{ formatDateTime(row.startsAt) }}</div>
          <div class="text-secondary">{{ t('至') }} {{ formatDateTime(row.endsAt) }}</div>
        </template>
      </ElTableColumn>
      <ElTableColumn :label="t('状态')" width="110">
        <template #default="{ row }">
          <ElTag :type="SECKILL_PHASE[row.phase as SeckillPhase].type">{{
            t(SECKILL_PHASE[row.phase as SeckillPhase].label)
          }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn :label="t('操作')" width="100" fixed="right">
        <template #default="{ row }">
          <ElPopconfirm
            v-if="row.status === 1"
            :title="t('关闭活动并归还剩余配额？已有待支付订单仍可支付或取消。')"
            :width="270"
            @confirm="closeActivity(row as SeckillActivity)"
            ><template #reference
              ><ElButton link type="danger" :loading="closingId === row.id">{{
                t('关闭活动')
              }}</ElButton></template
            ></ElPopconfirm
          >
          <span v-else class="text-secondary">{{ t('已结束') }}</span>
        </template>
      </ElTableColumn>
    </ElTable>
    <ElPagination
      v-model:current-page="pagination.currentPage"
      v-model:page-size="pagination.pageSize"
      class="table-pagination"
      :page-sizes="[10, 20, 50]"
      :total="pagination.total"
      layout="total, sizes, prev, pager, next"
      background
    />
  </ElCard>

  <ElDrawer
    v-model="drawerVisible"
    :title="t('创建秒杀活动')"
    size="min(560px, 100vw)"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :show-close="!saving"
  >
    <ElForm
      ref="formRef"
      :model="form"
      :rules="rules"
      :label-width="formLabelWidth('100px')"
      :disabled="saving"
    >
      <ElFormItem :label="t('活动商品')" prop="productId">
        <ElSelect
          v-model="form.productId"
          filterable
          remote
          :remote-method="searchProducts"
          :loading="productsLoading"
          :placeholder="t('输入商品名搜索')"
          style="width: 100%"
          @change="selectProduct"
        >
          <ElOption
            v-for="product in products"
            :key="product.id"
            :value="product.id"
            :label="product.name"
            :disabled="product.stock < 1 || product.price <= 0.01"
          >
            <span>{{ product.name }}</span>
            <span class="option-stock">{{ t('库存') }} {{ product.stock }}</span>
          </ElOption>
        </ElSelect>
      </ElFormItem>
      <ElFormItem v-if="selectedProduct" :label="t('普通库存')">
        <span
          >{{ selectedProduct.stock }} {{ t('件 · 当前售价') }}
          {{ formatMoney(selectedProduct.price) }}</span
        >
      </ElFormItem>
      <ElFormItem :label="t('活动标题')" prop="title"
        ><ElInput v-model="form.title" maxlength="128" show-word-limit
      /></ElFormItem>
      <ElFormItem :label="t('秒杀价')" prop="seckillPrice">
        <ElInputNumber
          v-model="form.seckillPrice"
          :min="0.01"
          :max="selectedProduct ? Number((selectedProduct.price - 0.01).toFixed(2)) : undefined"
          :precision="2"
          controls-position="right"
        /><span class="form-unit">{{ t('元') }}</span>
      </ElFormItem>
      <ElFormItem :label="t('活动配额')" prop="totalStock">
        <ElInputNumber
          v-model="form.totalStock"
          :min="1"
          :max="Math.min(selectedProduct?.stock ?? 1000000, 1000000)"
          step-strictly
          controls-position="right"
        />
      </ElFormItem>
      <ElFormItem :label="t('每人限购')" prop="limitPerUser">
        <ElInputNumber
          v-model="form.limitPerUser"
          :min="1"
          :max="Math.min(form.totalStock ?? 999, 999)"
          step-strictly
          controls-position="right"
        />
      </ElFormItem>
      <ElFormItem :label="t('开始时间')" prop="startsAt"
        ><ElDatePicker
          v-model="form.startsAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          :placeholder="t('选择开始时间')"
          style="width: 100%"
      /></ElFormItem>
      <ElFormItem :label="t('结束时间')" prop="endsAt"
        ><ElDatePicker
          v-model="form.endsAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          :placeholder="t('选择结束时间')"
          style="width: 100%"
      /></ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="saving" @click="drawerVisible = false">{{ t('取消') }}</ElButton>
      <ElButton type="primary" :loading="saving" @click="save">{{ t('创建活动') }}</ElButton>
    </template>
  </ElDrawer>
</template>

<script setup lang="ts">
import { t, formLabelWidth } from '@/i18n'
import { nextTick, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDatePicker,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElPagination,
  ElPopconfirm,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import type { ApiResult, Pagination, Product, SeckillActivity, SeckillPhase } from '@/types/api'
import request from '@/utils/request'
import { formatDateTime, formatMoney } from '@/utils/format'
import { SECKILL_PHASE } from '@/utils/seckill'

const activities = ref<SeckillActivity[]>([])
const loading = ref(false)
const error = ref('')
const pagination = reactive<Pagination>({ total: 0, pageSize: 10, pageCount: 1, currentPage: 1 })
const closingId = ref<number>()
let listSequence = 0
const fetchActivities = async () => {
  const sequence = ++listSequence
  loading.value = true
  try {
    const { data } = await request.get<
      ApiResult<{ activities: SeckillActivity[]; pagination: Pagination }>
    >('/admin/seckill', {
      params: { page: pagination.currentPage, pageSize: pagination.pageSize },
    })
    if (sequence !== listSequence) return
    if (!data.status) {
      error.value = data.payload.message || '加载活动失败'
      return
    }
    activities.value = data.payload.activities
    Object.assign(pagination, data.payload.pagination)
    error.value = ''
  } catch {
    if (sequence === listSequence) error.value = '加载活动失败，请重试'
  } finally {
    if (sequence === listSequence) loading.value = false
  }
}
watch(() => [pagination.currentPage, pagination.pageSize], fetchActivities, { immediate: true })

const closeActivity = async (activity: SeckillActivity) => {
  if (closingId.value !== undefined) return
  closingId.value = activity.id
  try {
    const { data } = await request.post<ApiResult>('/admin/seckill/close', { id: activity.id })
    if (data.status) ElMessage.success(t(data.payload.message || '活动已关闭'))
    else ElMessage.error(t(data.payload.message || '关闭失败'))
    await fetchActivities()
  } catch {
    ElMessage.error(t('关闭失败，请刷新列表后确认活动状态'))
  } finally {
    closingId.value = undefined
  }
}

const drawerVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const products = ref<Product[]>([])
const productsLoading = ref(false)
let productSequence = 0
const form = reactive({
  productId: undefined as number | undefined,
  title: '',
  seckillPrice: undefined as number | undefined,
  totalStock: 1 as number | undefined,
  limitPerUser: 1 as number | undefined,
  startsAt: '',
  endsAt: '',
})
const selectedProduct = ref<Product>()
const rules: FormRules<typeof form> = {
  productId: [
    {
      required: true,
      get message() {
        return t('请选择活动商品')
      },
      trigger: 'change',
    },
  ],
  title: [
    {
      required: true,
      whitespace: true,
      get message() {
        return t('请输入活动标题')
      },
      trigger: 'blur',
    },
  ],
  seckillPrice: [
    {
      required: true,
      get message() {
        return t('请输入秒杀价')
      },
      trigger: 'blur',
    },
  ],
  totalStock: [
    {
      required: true,
      get message() {
        return t('请输入活动配额')
      },
      trigger: 'blur',
    },
  ],
  limitPerUser: [
    {
      required: true,
      get message() {
        return t('请输入每人限购数量')
      },
      trigger: 'blur',
    },
  ],
  startsAt: [
    {
      required: true,
      get message() {
        return t('请选择开始时间')
      },
      trigger: 'change',
    },
  ],
  endsAt: [
    {
      required: true,
      get message() {
        return t('请选择结束时间')
      },
      trigger: 'change',
    },
  ],
}
const searchProducts = async (keyword = '') => {
  const sequence = ++productSequence
  productsLoading.value = true
  try {
    const { data } = await request.get<ApiResult<{ products: Product[] }>>('/admin/product', {
      params: { keyword, status: 1, pageSize: 20 },
    })
    if (sequence === productSequence && data.status) products.value = data.payload.products
  } catch {
    ElMessage.error(t('加载商品失败，请重新搜索'))
  } finally {
    if (sequence === productSequence) productsLoading.value = false
  }
}
const selectProduct = (id: number) => {
  selectedProduct.value = products.value.find((product) => product.id === id)
  const product = selectedProduct.value
  if (!product) return
  form.title = product.name.slice(0, 115) + ' · 限时秒杀'
  form.seckillPrice = Math.max(
    0.01,
    Math.min(Number((product.price - 0.01).toFixed(2)), Number((product.price * 0.8).toFixed(2))),
  )
  form.totalStock = Math.min(product.stock, 10)
  form.limitPerUser = 1
}
const localDate = (value: number) =>
  new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
    .format(new Date(value))
    .replace(' ', 'T')
const openDrawer = () => {
  Object.assign(form, {
    productId: undefined,
    title: '',
    seckillPrice: undefined,
    totalStock: 1,
    limitPerUser: 1,
    startsAt: localDate(Date.now() + 5 * 60000),
    endsAt: localDate(Date.now() + 65 * 60000),
  })
  selectedProduct.value = undefined
  drawerVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
  searchProducts()
}
const save = async () => {
  if (saving.value || !formRef.value) return
  if (!(await formRef.value.validate().catch(() => false))) return
  if (form.startsAt >= form.endsAt) {
    ElMessage.error(t('结束时间必须晚于开始时间'))
    return
  }
  saving.value = true
  try {
    const { data } = await request.post<ApiResult>('/admin/seckill/create', {
      ...form,
      title: form.title.trim(),
    })
    if (!data.status) {
      ElMessage.error(t(data.payload.message || '创建失败'))
      return
    }
    ElMessage.success(t('秒杀活动已创建，配额已划拨'))
    drawerVisible.value = false
    await fetchActivities()
  } catch {
    ElMessage.error(t('创建失败，请刷新活动列表确认结果后再操作'))
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.heading-actions {
  display: flex;
  gap: 0.6rem;
}
.heading-actions :deep(.el-button) {
  gap: 0.3rem;
}
.inventory-hint {
  margin-bottom: 1rem;
}
.seckill-price {
  color: var(--el-color-danger);
}
.load-error {
  margin-bottom: 1rem;
  color: var(--el-color-danger);
}
.option-stock {
  float: right;
  margin-left: 1rem;
  color: var(--el-text-color-secondary);
  font-size: 0.75rem;
}
.form-unit {
  margin-left: 0.5rem;
  color: var(--el-text-color-secondary);
}
@media (max-width: 600px) {
  .page-heading {
    flex-wrap: wrap;
    gap: 0.75rem;
  }
}
</style>
