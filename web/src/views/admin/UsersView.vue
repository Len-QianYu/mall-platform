<template>
  <div class="page-heading users-heading">
    <h1 class="page-title">用户管理</h1>
    <ElRadioGroup v-model="viewMode" size="small" aria-label="用户展示方式">
      <ElRadioButton value="list">列表视图</ElRadioButton>
      <ElRadioButton value="avatar">头像视图</ElRadioButton>
    </ElRadioGroup>
  </div>

  <ElCard shadow="never">
    <!-- 筛选 -->
    <ElForm :model="filter" inline class="filter-form" @submit.prevent="search">
      <ElFormItem label="关键字">
        <ElInput
          v-model="filter.keyword"
          placeholder="用户名 / 名字 / 手机号"
          clearable
          style="width: 14rem"
        >
          <template #prefix><i class="bi bi-search"></i></template>
        </ElInput>
      </ElFormItem>
      <ElFormItem label="角色">
        <ElSelect v-model="filter.role" placeholder="全部" clearable style="width: 8rem">
          <ElOption label="用户" value="user" />
          <ElOption label="管理员" value="admin" />
          <ElOption label="S管理员" value="super_admin" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="状态">
        <ElSelect v-model="filter.status" placeholder="全部" clearable style="width: 8rem">
          <ElOption label="启用" :value="1" />
          <ElOption label="禁用" :value="0" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" native-type="submit">查询</ElButton>
        <ElButton @click="resetFilter">重置</ElButton>
      </ElFormItem>
    </ElForm>

    <ElTable v-if="viewMode === 'list'" v-loading="loading" :data="users" border stripe>
      <ElTableColumn prop="id" label="ID" width="80" />
      <ElTableColumn label="用户" min-width="180">
        <template #default="{ row }">
          <div class="user-cell">
            <ElAvatar :size="32" :src="assetUrl(row.avatarUrl)">
              {{ row.username.charAt(0).toUpperCase() }}
            </ElAvatar>
            <div>
              <div>{{ row.username }}</div>
              <div class="text-secondary">{{ row.name || '未填写名字' }}</div>
            </div>
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="性别" width="70">
        <template #default="{ row }">
          {{ row.gender === 'M' ? '男' : row.gender === 'F' ? '女' : '-' }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="角色" width="100">
        <template #default="{ row }">
          <UserRoleTag :role="row.role" />
        </template>
      </ElTableColumn>
      <ElTableColumn label="手机号" min-width="130">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </ElTableColumn>
      <ElTableColumn label="邮箱" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.email || '-' }}</template>
      </ElTableColumn>
      <ElTableColumn label="状态" width="80">
        <template #default="{ row }">
          <ElTag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="注册时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.registeredAt) }}</template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="130" fixed="right">
        <!-- 表格行是 Element Plus 的 DefaultRow（字段为 any），传给按 User 声明的函数前先断言 -->
        <template #default="{ row }">
          <ElButton link type="primary" @click="openEdit(row as User)">编辑</ElButton>
          <!-- 不能禁用自己，否则当前管理员会被锁在系统外 -->
          <ElPopconfirm
            :title="row.status === 1 ? '禁用后该用户无法登录，确定禁用吗？' : '确定启用该用户吗？'"
            :width="220"
            @confirm="toggleStatus(row as User)"
          >
            <template #reference>
              <ElButton
                link
                :type="row.status === 1 ? 'danger' : 'success'"
                :disabled="isSelf(row.id)"
              >
                {{ row.status === 1 ? '禁用' : '启用' }}
              </ElButton>
            </template>
          </ElPopconfirm>
        </template>
      </ElTableColumn>
    </ElTable>

    <div v-else v-loading="loading" class="avatar-view">
      <div v-if="users.length" class="users-grid">
        <button
          v-for="user in users"
          :key="user.id"
          type="button"
          class="user-card"
          :class="{
            'is-admin': user.role === 'admin',
            'is-super-admin': user.role === 'super_admin',
          }"
          :title="displayName(user)"
          :aria-label="`编辑用户 ${displayName(user)}`"
          :disabled="loading"
          @click="openEdit(user)"
        >
          <ElAvatar :size="64" :src="assetUrl(user.avatarUrl)" :alt="displayName(user)">
            {{ displayName(user).charAt(0).toUpperCase() }}
          </ElAvatar>
          <span class="user-card-name">{{ displayName(user) }}</span>
        </button>
      </div>
      <ElEmpty v-else description="暂无用户" :image-size="90" />
    </div>

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

  <!-- 编辑抽屉 -->
  <ElDrawer
    v-model="drawerVisible"
    title="编辑用户"
    size="min(420px, 100vw)"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :show-close="!saving"
  >
    <ElForm ref="formRef" :model="form" :rules="rules" label-width="70px" :disabled="saving">
      <ElFormItem label="用户名">
        <ElInput :model-value="form.username" disabled />
      </ElFormItem>
      <ElFormItem label="名字" prop="name">
        <ElInput v-model="form.name" :maxlength="32" placeholder="选填" />
      </ElFormItem>
      <ElFormItem label="性别" prop="gender">
        <ElRadioGroup v-model="form.gender">
          <ElRadio value="M">男</ElRadio>
          <ElRadio value="F">女</ElRadio>
          <ElRadio value="">未设置</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="手机号" prop="phone">
        <ElInput v-model="form.phone" :maxlength="11" placeholder="选填" />
      </ElFormItem>
      <ElFormItem label="邮箱" prop="email">
        <ElInput v-model="form.email" :maxlength="128" placeholder="选填" />
      </ElFormItem>
      <ElFormItem label="角色" prop="role">
        <!-- 不能改自己的角色，避免把自己降成普通用户后失去管理权限 -->
        <ElSelect v-model="form.role" :disabled="isSelf(form.id)" style="width: 100%">
          <ElOption label="用户" value="user" />
          <ElOption label="管理员" value="admin" />
          <ElOption label="S管理员" value="super_admin" />
        </ElSelect>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="saving" @click="drawerVisible = false">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="save">保存</ElButton>
    </template>
  </ElDrawer>
</template>

<script setup lang="ts">
import { nextTick, reactive, ref, watch } from 'vue'
import {
  ElAvatar,
  ElButton,
  ElCard,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElPopconfirm,
  ElRadio,
  ElRadioButton,
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
import { useUserStore, type UserRole } from '@/stores/user'
import type { ApiResult, Pagination, User } from '@/types/api'
import { assetUrl, formatDateTime } from '@/utils/format'
import UserRoleTag from '@/components/UserRoleTag.vue'

const userStore = useUserStore()

const loading = ref(false)
const users = ref<User[]>([])
const viewMode = ref<'list' | 'avatar'>('list')
const displayName = (user: User) => user.name?.trim() || user.username

// 下拉框清空后值为 undefined，axios 不会把 undefined 参数拼进查询串
const filter = reactive({
  keyword: '',
  role: undefined as UserRole | undefined,
  status: undefined as number | undefined,
})

const pagination = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0,
})

const fetchUsers = async () => {
  loading.value = true
  try {
    const { data } = await request.get<ApiResult<{ users: User[]; pagination: Pagination }>>(
      '/admin/user',
      {
        params: {
          page: pagination.currentPage,
          pageSize: pagination.pageSize,
          keyword: filter.keyword.trim() || undefined,
          role: filter.role,
          status: filter.status,
        },
      },
    )
    if (!data.status) {
      ElMessage.error(data.payload.message || '加载用户列表失败')
      return
    }
    users.value = data.payload.users
    pagination.total = data.payload.pagination.total
  } catch {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

// 翻页、改每页条数时重新查询
watch(() => [pagination.currentPage, pagination.pageSize], fetchUsers, { immediate: true })

// 按新条件从第一页查；本来就在第一页时 watch 不会触发，直接查
const search = () => {
  if (pagination.currentPage === 1) {
    fetchUsers()
  } else {
    pagination.currentPage = 1
  }
}

const resetFilter = () => {
  filter.keyword = ''
  filter.role = undefined
  filter.status = undefined
  search()
}

// 是否为当前登录的管理员
const isSelf = (id: number) => id === userStore.user?.user.id

const toggleStatus = async (row: User) => {
  const status = row.status === 1 ? 0 : 1
  try {
    const { data } = await request.post<ApiResult>('/admin/user/status', { id: row.id, status })
    if (!data.status) {
      ElMessage.error(data.payload.message || '操作失败')
      return
    }
    row.status = status
    ElMessage.success(status === 1 ? '已启用' : '已禁用')
  } catch {
    ElMessage.error('操作失败')
  }
}

// 编辑抽屉
const drawerVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: 0,
  username: '',
  name: '',
  gender: '' as 'M' | 'F' | '',
  phone: '',
  email: '',
  role: 'user' as UserRole,
})

const rules: FormRules<typeof form> = {
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

const openEdit = (row: User) => {
  Object.assign(form, {
    id: row.id,
    username: row.username,
    name: row.name ?? '',
    gender: row.gender ?? '',
    phone: row.phone ?? '',
    email: row.email ?? '',
    role: row.role,
  })
  drawerVisible.value = true
  // 清掉上一次编辑留下的校验提示
  nextTick(() => formRef.value?.clearValidate())
}

const save = async () => {
  if (!formRef.value || saving.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    // 选填项留空时存 NULL，不存空字符串
    const { data } = await request.post<ApiResult>('/admin/user/update', {
      id: form.id,
      name: form.name.trim() || null,
      gender: form.gender || null,
      phone: form.phone.trim() || null,
      email: form.email.trim() || null,
      role: form.role,
    })
    if (!data.status) {
      ElMessage.error(data.payload.message || '保存失败')
      return
    }
    ElMessage.success('保存成功')
    drawerVisible.value = false
    fetchUsers()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.users-heading {
  flex-wrap: wrap;
  gap: 1rem;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.avatar-view {
  min-height: 240px;
}

.users-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 1rem;
}

.user-card {
  --user-card-border-color: var(--el-color-success);

  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  min-width: 0;
  min-height: 150px;
  padding: 1.25rem 1rem;
  border: 1px solid var(--user-card-border-color);
  border-radius: 8px;
  background-color: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font: inherit;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background-color 0.15s;

  &.is-admin {
    --user-card-border-color: var(--el-color-warning);
  }

  &.is-super-admin {
    --user-card-border-color: #a855f7;
  }

  &:hover {
    background-color: var(--el-color-primary-light-9);
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 3px;
  }

  &:disabled {
    cursor: wait;
  }
}

.user-card-name {
  max-width: 100%;
  overflow: hidden;
  font-size: 0.875rem;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
