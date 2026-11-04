<template>
  <div class="page-heading">
    <h1 class="page-title">商品管理</h1>
    <ElButton type="primary" @click="openDrawer()">
      <i class="bi bi-plus-lg button-icon"></i>新增商品
    </ElButton>
  </div>

  <ElCard shadow="never">
    <!-- 筛选 -->
    <ElForm :model="filter" inline class="filter-form" @submit.prevent="search">
      <ElFormItem label="商品名">
        <ElInput v-model="filter.keyword" placeholder="商品名关键字" clearable style="width: 14rem">
          <template #prefix><i class="bi bi-search"></i></template>
        </ElInput>
      </ElFormItem>
      <ElFormItem label="分类">
        <ElSelect v-model="filter.category" placeholder="全部" clearable style="width: 10rem">
          <ElOption
            v-for="category in categories"
            :key="category"
            :label="category"
            :value="category"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="状态">
        <ElSelect v-model="filter.status" placeholder="全部" clearable style="width: 8rem">
          <ElOption label="上架" :value="1" />
          <ElOption label="下架" :value="0" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" native-type="submit">查询</ElButton>
        <ElButton @click="resetFilter">重置</ElButton>
      </ElFormItem>
    </ElForm>

    <ElTable v-loading="loading" :data="products" border stripe>
      <ElTableColumn prop="id" label="ID" width="80" />
      <ElTableColumn label="商品" min-width="260">
        <template #default="{ row }">
          <div class="product-cell">
            <ElImage
              v-if="row.mainImage"
              class="product-image"
              :src="assetUrl(row.mainImage)"
              :preview-src-list="[assetUrl(row.mainImage)]"
              preview-teleported
              fit="cover"
            />
            <div v-else class="product-image image-placeholder">
              <i class="bi bi-image"></i>
            </div>
            <div class="product-text">
              <div class="product-name">{{ row.name }}</div>
              <div class="text-secondary product-subtitle">{{ row.subtitle || '-' }}</div>
            </div>
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="category" label="分类" width="110" />
      <ElTableColumn label="售价" width="110">
        <template #default="{ row }">{{ formatMoney(row.price) }}</template>
      </ElTableColumn>
      <ElTableColumn label="库存" width="90">
        <!-- 库存为 0 标红，提醒补货 -->
        <template #default="{ row }">
          <span :class="{ 'stock-empty': row.stock === 0 }">{{ row.stock }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="sales" label="销量" width="90" />
      <ElTableColumn label="状态" width="80">
        <template #default="{ row }">
          <ElTag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '上架' : '下架' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="更新时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="130" fixed="right">
        <!-- 表格行是 Element Plus 的 DefaultRow（字段为 any），传给按 Product 声明的函数前先断言 -->
        <template #default="{ row }">
          <ElButton link type="primary" @click="openDrawer(row as Product)">编辑</ElButton>
          <ElPopconfirm
            :title="
              row.status === 1 ? '下架后用户将看不到该商品，确定下架吗？' : '确定上架该商品吗？'
            "
            :width="220"
            @confirm="toggleStatus(row as Product)"
          >
            <template #reference>
              <ElButton link :type="row.status === 1 ? 'danger' : 'success'">
                {{ row.status === 1 ? '下架' : '上架' }}
              </ElButton>
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

  <!-- 新增 / 编辑抽屉 -->
  <ElDrawer
    v-model="drawerVisible"
    :title="form.id === null ? '新增商品' : '编辑商品'"
    size="520px"
  >
    <ElForm ref="formRef" :model="form" :rules="rules" label-width="80px" :disabled="saving">
      <ElFormItem label="商品名" prop="name">
        <ElInput v-model="form.name" :maxlength="128" show-word-limit />
      </ElFormItem>
      <ElFormItem label="副标题" prop="subtitle">
        <ElInput v-model="form.subtitle" :maxlength="255" placeholder="选填，一句话卖点" />
      </ElFormItem>
      <ElFormItem label="分类" prop="category">
        <!-- 可以选已有分类，也可以直接输入新分类 -->
        <ElSelect
          v-model="form.category"
          filterable
          allow-create
          default-first-option
          placeholder="选择或输入分类"
          style="width: 100%"
        >
          <ElOption
            v-for="category in categories"
            :key="category"
            :label="category"
            :value="category"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="售价" prop="price">
        <ElInputNumber
          v-model="form.price"
          :min="0.01"
          :precision="2"
          controls-position="right"
          style="width: 12rem"
        />
        <span class="form-unit">元</span>
      </ElFormItem>
      <ElFormItem label="库存" prop="stock">
        <ElInputNumber
          v-model="form.stock"
          :min="0"
          step-strictly
          controls-position="right"
          style="width: 12rem"
        />
      </ElFormItem>
      <ElFormItem label="主图" prop="mainImage">
        <ImageUploader v-model="form.mainImage" type="images" />
      </ElFormItem>
      <ElFormItem label="商品详情" prop="description">
        <ElInput v-model="form.description" type="textarea" :rows="5" placeholder="选填" />
      </ElFormItem>
      <ElFormItem label="状态" prop="status">
        <ElRadioGroup v-model="form.status">
          <ElRadio :value="1">上架</ElRadio>
          <ElRadio :value="0">下架</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="drawerVisible = false">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="save">保存</ElButton>
    </template>
  </ElDrawer>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElCard,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElPagination,
  ElPopconfirm,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  vLoading,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, Pagination, Product } from '@/types/api'
import { assetUrl, formatDateTime, formatMoney } from '@/utils/format'
import ImageUploader from '@/components/ImageUploader.vue'

const loading = ref(false)
const products = ref<Product[]>([])
const categories = ref<string[]>([])

// 下拉框清空后值为 undefined，axios 不会把 undefined 参数拼进查询串
const filter = reactive({
  keyword: '',
  category: undefined as string | undefined,
  status: undefined as number | undefined,
})

const pagination = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0,
})

const fetchProducts = async () => {
  loading.value = true
  try {
    const { data } = await request.get<ApiResult<{ products: Product[]; pagination: Pagination }>>(
      '/admin/product',
      {
        params: {
          page: pagination.currentPage,
          pageSize: pagination.pageSize,
          keyword: filter.keyword.trim() || undefined,
          category: filter.category,
          status: filter.status,
        },
      },
    )
    if (!data.status) {
      ElMessage.error(data.payload.message || '加载商品列表失败')
      return
    }
    products.value = data.payload.products
    pagination.total = data.payload.pagination.total
  } catch {
    ElMessage.error('加载商品列表失败')
  } finally {
    loading.value = false
  }
}

// 分类只用作下拉选项，加载失败不影响商品列表
const fetchCategories = async () => {
  try {
    const { data } = await request.get<ApiResult<{ categories: string[] }>>(
      '/admin/product/categories',
    )
    if (data.status) {
      categories.value = data.payload.categories
    }
  } catch {
    // 下拉里没有已有分类，表单里仍可手动输入
  }
}

// 翻页、改每页条数时重新查询
watch(() => [pagination.currentPage, pagination.pageSize], fetchProducts, { immediate: true })

onMounted(fetchCategories)

// 按新条件从第一页查；本来就在第一页时 watch 不会触发，直接查
const search = () => {
  if (pagination.currentPage === 1) {
    fetchProducts()
  } else {
    pagination.currentPage = 1
  }
}

const resetFilter = () => {
  filter.keyword = ''
  filter.category = undefined
  filter.status = undefined
  search()
}

const toggleStatus = async (row: Product) => {
  const status = row.status === 1 ? 0 : 1
  try {
    const { data } = await request.post<ApiResult>('/admin/product/status', {
      id: row.id,
      status,
    })
    if (!data.status) {
      ElMessage.error(data.payload.message || '操作失败')
      return
    }
    row.status = status
    ElMessage.success(status === 1 ? '已上架' : '已下架')
  } catch {
    ElMessage.error('操作失败')
  }
}

// 新增 / 编辑抽屉
const drawerVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: null as number | null, // null 表示新增
  name: '',
  subtitle: '',
  category: '',
  price: undefined as number | undefined,
  stock: 0,
  mainImage: '',
  description: '',
  status: 1,
})

const rules: FormRules<typeof form> = {
  name: [{ required: true, whitespace: true, message: '请输入商品名', trigger: 'blur' }],
  category: [
    { required: true, whitespace: true, message: '请选择或输入分类', trigger: 'change' },
    { max: 32, message: '分类最多32个字符', trigger: 'change' },
  ],
  price: [{ required: true, message: '请输入售价', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }],
}

// 传入商品为编辑，不传为新增
const openDrawer = (product?: Product) => {
  Object.assign(form, {
    id: product?.id ?? null,
    name: product?.name ?? '',
    subtitle: product?.subtitle ?? '',
    category: product?.category ?? '',
    price: product?.price,
    stock: product?.stock ?? 0,
    mainImage: product?.mainImage ?? '',
    description: product?.description ?? '',
    status: product?.status ?? 1,
  })
  drawerVisible.value = true
  // 清掉上一次编辑留下的校验提示
  nextTick(() => formRef.value?.clearValidate())
}

const save = async () => {
  if (!formRef.value || saving.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const creating = form.id === null
  saving.value = true
  try {
    // 选填项留空时存 NULL，不存空字符串
    const { data } = await request.post<ApiResult>(
      creating ? '/admin/product/create' : '/admin/product/update',
      {
        id: form.id ?? undefined,
        name: form.name.trim(),
        subtitle: form.subtitle.trim() || null,
        category: form.category.trim(),
        price: form.price,
        stock: form.stock,
        mainImage: form.mainImage || null,
        description: form.description.trim() || null,
        status: form.status,
      },
    )
    if (!data.status) {
      ElMessage.error(data.payload.message || '保存失败')
      return
    }
    ElMessage.success(creating ? '商品已新增' : '保存成功')
    drawerVisible.value = false
    fetchProducts()
    fetchCategories() // 可能新增了分类
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.button-icon {
  margin-right: 0.25rem;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.product-image {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 6px;
}

.image-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-placeholder);
  font-size: 1.25rem;
}

// 商品名、副标题过长时省略
.product-text {
  min-width: 0;
}

.product-name,
.product-subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stock-empty {
  color: var(--el-color-danger);
  font-weight: 600;
}

.form-unit {
  margin-left: 0.5rem;
  color: var(--el-text-color-secondary);
}
</style>
