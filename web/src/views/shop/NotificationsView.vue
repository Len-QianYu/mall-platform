<template>
  <div class="shop-container">
    <div class="notification-heading">
      <div>
        <h1 class="section-title"><i class="bi bi-bell"></i>{{ t('消息通知') }}</h1>
        <p>{{ t('收藏商品上架、降价或涨价时，会在这里提醒你。') }}</p>
      </div>
      <div class="heading-actions">
        <ElButton
          class="refresh-button"
          circle
          :disabled="loading"
          :aria-busy="loading"
          :aria-label="t('刷新通知')"
          :title="loading ? t('正在刷新通知') : t('刷新通知')"
          @click="fetchNotifications"
        >
          <i
            class="bi bi-arrow-clockwise refresh-icon"
            :class="{ 'is-spinning': loading }"
            aria-hidden="true"
          ></i>
        </ElButton>
        <ElButton
          type="primary"
          plain
          :loading="markingAll"
          :disabled="notifications.unreadCount === 0"
          @click="markAllRead"
          >{{ t('全部已读') }}</ElButton
        >
      </div>
    </div>

    <div class="notification-tabs" role="tablist" :aria-label="t('通知筛选')">
      <button
        type="button"
        role="tab"
        :aria-selected="!unreadOnly"
        :class="{ active: !unreadOnly }"
        @click="setFilter(false)"
      >
        {{ t('全部通知') }}
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="unreadOnly"
        :class="{ active: unreadOnly }"
        @click="setFilter(true)"
      >
        {{ t('未读通知') }} <span>{{ notifications.unreadCount }}</span>
      </button>
    </div>

    <div v-loading="loading" class="notification-list">
      <div v-if="error" class="load-error" role="alert">
        <span>{{ t(error) }}</span>
        <ElButton @click="fetchNotifications">{{ t('重新加载') }}</ElButton>
      </div>
      <ElEmpty
        v-else-if="!loading && items.length === 0"
        :description="t(unreadOnly ? '暂时没有未读通知' : '还没有商品通知，先收藏喜欢的商品吧')"
      >
        <ElButton v-if="unreadOnly" @click="setFilter(false)">{{ t('查看全部通知') }}</ElButton>
        <RouterLink v-else :to="{ name: 'products' }" class="browse-link">{{
          t('去收藏好物')
        }}</RouterLink>
      </ElEmpty>
      <template v-else>
        <article
          v-for="item in items"
          :key="item.id"
          class="notification-item"
          :class="{ unread: item.readAt === null }"
        >
          <ElImage
            v-if="item.productImage"
            class="notification-image"
            :src="assetUrl(item.productImage)"
            fit="cover"
          />
          <div v-else class="notification-image image-placeholder"><i class="bi bi-image"></i></div>
          <div class="notification-content">
            <div class="notification-title">
              <ElTag
                :type="
                  item.type === 'PRICE_DROP'
                    ? 'success'
                    : item.type === 'PRICE_RISE'
                      ? 'warning'
                      : 'primary'
                "
                size="small"
                >{{ t(PRODUCT_NOTIFICATION_LABELS[item.type]) }}</ElTag
              >
              <span v-if="item.readAt === null" class="unread-dot" :aria-label="t('未读')"></span>
              <h2>{{ item.productName }}</h2>
            </div>
            <p class="notification-description">{{ notificationDescription(item) }}</p>
            <div class="notification-prices" :class="{ 'price-rise': item.type === 'PRICE_RISE' }">
              <template v-if="item.type !== 'LISTED'">
                <span class="old-price">{{ formatMoney(item.oldPrice) }}</span>
                <i class="bi bi-arrow-right" aria-hidden="true"></i>
              </template>
              <strong>{{ formatMoney(item.newPrice) }}</strong>
            </div>
            <time class="text-secondary" :datetime="item.createdAt">{{
              formatDateTime(item.createdAt)
            }}</time>
          </div>
          <div class="notification-actions">
            <ElButton size="small" :loading="reading.has(item.id)" @click="viewProduct(item)">{{
              t('查看商品')
            }}</ElButton>
            <ElButton
              v-if="item.readAt === null"
              size="small"
              text
              :loading="reading.has(item.id)"
              @click="markRead(item)"
              >{{ t('标为已读') }}</ElButton
            >
            <span v-else class="text-secondary">{{ t('已读') }}</span>
          </div>
        </article>
      </template>
    </div>

    <ElPagination
      v-if="pagination.total > 0"
      v-model:current-page="page"
      class="page-pagination"
      layout="prev, pager, next"
      :page-size="pagination.pageSize"
      :total="pagination.total"
    />
  </div>
</template>

<script setup lang="ts">
import { t } from '@/i18n'
import { onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElButton, ElEmpty, ElImage, ElMessage, ElPagination, ElTag, vLoading } from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, Pagination, ProductNotification } from '@/types/api'
import { assetUrl, formatDateTime, formatMoney } from '@/utils/format'
import { notificationDescription, PRODUCT_NOTIFICATION_LABELS } from '@/utils/productNotifications'
import { useNotificationsStore } from '@/stores/notifications'
import { useUserStore } from '@/stores/user'

const notifications = useNotificationsStore()
const user = useUserStore()
const router = useRouter()
const items = ref<ProductNotification[]>([])
const page = ref(1)
const unreadOnly = ref(false)
const loading = ref(false)
const error = ref('')
const reading = ref(new Set<number>())
const markingAll = ref(false)
const pagination = ref<Pagination>({ total: 0, pageSize: 20, currentPage: 1, pageCount: 1 })
let sequence = 0

const fetchNotifications = async () => {
  const current = ++sequence
  const token = user.user?.token
  loading.value = true
  error.value = ''
  try {
    const { data } = await request.get<
      ApiResult<{
        notifications: ProductNotification[]
        pagination: Pagination
        unreadCount: number
      }>
    >('/notification', {
      params: { page: page.value, pageSize: 20, unreadOnly: unreadOnly.value },
    })
    if (current !== sequence || token !== user.user?.token) return
    if (!data.status) throw new Error(t(data.payload.message || '加载通知失败'))
    items.value = data.payload.notifications
    pagination.value = data.payload.pagination
    page.value = data.payload.pagination.currentPage
  } catch {
    if (current === sequence) error.value = '加载通知失败，请稍后重试'
  } finally {
    if (current === sequence) loading.value = false
  }
}

const setFilter = (unread: boolean) => {
  page.value = 1
  unreadOnly.value = unread
}

const markRead = async (item: ProductNotification): Promise<boolean> => {
  if (item.readAt !== null) return true
  if (reading.value.has(item.id)) return false
  reading.value.add(item.id)
  try {
    await notifications.markRead(item.id)
    return true
  } catch (failure) {
    ElMessage.error(t(failure instanceof Error ? failure.message : '标记已读失败'))
    return false
  } finally {
    reading.value.delete(item.id)
  }
}

const viewProduct = async (item: ProductNotification) => {
  if (await markRead(item))
    await router.push({ name: 'product_detail', params: { id: item.productId } })
}

const markAllRead = async () => {
  markingAll.value = true
  try {
    await notifications.markAllRead()
    ElMessage.success(t('已全部标为已读'))
  } catch (failure) {
    ElMessage.error(t(failure instanceof Error ? failure.message : '标记已读失败'))
  } finally {
    markingAll.value = false
  }
}

watch([page, unreadOnly, () => notifications.revision], fetchNotifications, { immediate: true })
void notifications.refreshUnread()
onBeforeUnmount(() => {
  sequence += 1
})
</script>

<style lang="scss" scoped>
.notification-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.5rem;
  .section-title {
    margin-bottom: 0.5rem;
  }
  p {
    margin: 0;
    color: var(--el-text-color-secondary);
    font-size: 0.875rem;
  }
}
.heading-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  .el-button {
    margin: 0;
  }
}
.refresh-icon {
  display: inline-block;
  font-size: 1rem;

  &.is-spinning {
    animation: notification-refresh-spin 0.8s linear infinite;
  }
}
@keyframes notification-refresh-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .refresh-icon.is-spinning {
    animation: none;
  }
}
.notification-tabs {
  display: flex;
  gap: 1.5rem;
  border-bottom: 1px solid var(--el-border-color-light);
  margin-bottom: 1rem;
  button {
    padding: 0.75rem 0;
    border: 0;
    border-bottom: 2px solid transparent;
    background: transparent;
    color: var(--el-text-color-secondary);
    font: inherit;
    font-size: 0.875rem;
    cursor: pointer;
    &.active {
      border-bottom-color: var(--el-color-primary);
      color: var(--el-color-primary);
    }
    span {
      padding: 0.1rem 0.4rem;
      border-radius: 1rem;
      background: var(--el-fill-color);
    }
  }
}
.notification-list {
  min-height: 240px;
}
.notification-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem;
  margin-bottom: 0.75rem;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  &.unread {
    border-left: 3px solid var(--el-color-primary);
  }
}
.notification-image {
  width: 80px;
  height: 80px;
  flex-shrink: 0;
  border-radius: 6px;
  font-size: 1.75rem;
}
.notification-content {
  flex: 1;
  min-width: 0;
}
.notification-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  h2 {
    margin: 0;
    color: var(--el-text-color-primary);
    font-size: 0.9375rem;
    font-weight: 600;
    overflow-wrap: anywhere;
  }
}
.unread-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-primary);
}
.notification-description {
  margin: 0.5rem 0;
  color: var(--el-text-color-regular);
  font-size: 0.8125rem;
  line-height: 1.6;
}
.notification-prices {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  color: var(--el-color-success);
  &.price-rise {
    color: var(--el-color-warning);
  }
  .old-price {
    color: var(--el-text-color-secondary);
    text-decoration: line-through;
    font-size: 0.8125rem;
  }
  i {
    color: var(--el-text-color-placeholder);
  }
}
.notification-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  .el-button {
    margin: 0;
  }
}
.load-error {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 1rem;
  padding: 3rem 0;
  color: var(--el-color-danger);
}
.browse-link {
  color: var(--el-color-primary);
  text-decoration: none;
}
@media (max-width: 600px) {
  .notification-item {
    flex-wrap: wrap;
    padding: 1rem;
    gap: 0.75rem;
  }
  .notification-image {
    width: 56px;
    height: 56px;
    align-self: flex-start;
  }
  .notification-actions {
    width: 100%;
    flex-direction: row;
    justify-content: flex-end;
  }
}
</style>
