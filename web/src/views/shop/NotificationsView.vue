<template>
  <div :class="admin ? 'admin-notifications' : 'shop-container'">
    <div class="notification-heading">
      <div>
        <h1 :class="admin ? 'page-title' : 'section-title'">
          <i class="bi bi-bell"></i>{{ t('消息通知') }}
        </h1>
        <p>
          {{
            t(
              admin
                ? '买家提交新订单后，会在这里提醒你。'
                : '订单发货、收藏商品上架或变价时，会在这里提醒你。',
            )
          }}
        </p>
      </div>
      <div class="heading-actions">
        <ElButton
          class="refresh-button"
          circle
          :disabled="loading"
          :aria-busy="loading"
          :aria-label="t('刷新通知')"
          :title="loading ? t('正在刷新通知') : t('刷新通知')"
          @click="refreshNotifications"
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
          :disabled="loading || reading.size > 0 || unreadCount === 0"
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
        {{ t('未读通知') }} <span>{{ unreadCount }}</span>
      </button>
    </div>

    <div v-loading="loading" class="notification-list">
      <div v-if="error" class="load-error" role="alert">
        <span>{{ t(error) }}</span>
        <ElButton @click="refreshNotifications">{{ t('重新加载') }}</ElButton>
      </div>
      <ElEmpty
        v-else-if="!loading && items.length === 0"
        :description="
          t(unreadOnly ? '暂时没有未读通知' : admin ? '暂时没有新订单通知' : '暂时没有消息通知')
        "
      >
        <ElButton v-if="unreadOnly" @click="setFilter(false)">{{ t('查看全部通知') }}</ElButton>
        <RouterLink
          v-else
          :to="{ name: admin ? 'admin_orders' : 'products' }"
          class="browse-link"
          >{{ t(admin ? '查看订单' : '去收藏好物') }}</RouterLink
        >
      </ElEmpty>
      <template v-else>
        <article
          v-for="item in items"
          :key="notificationKey(item)"
          class="notification-item"
          :class="{ unread: item.readAt === null }"
        >
          <ElImage
            v-if="isProductNotification(item) && item.productImage"
            class="notification-image"
            :src="assetUrl(item.productImage)"
            fit="cover"
          />
          <div v-else class="notification-image image-placeholder">
            <i
              :class="
                item.type === 'ORDER_SHIPPED'
                  ? 'bi bi-truck'
                  : item.type === 'NEW_ORDER'
                    ? 'bi bi-receipt'
                    : 'bi bi-image'
              "
              aria-hidden="true"
            ></i>
          </div>
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
                >{{ t(notificationLabel(item)) }}</ElTag
              >
              <span v-if="item.readAt === null" class="unread-dot" :aria-label="t('未读')"></span>
              <h2>{{ notificationName(item) }}</h2>
            </div>
            <p class="notification-description">{{ notificationDescription(item) }}</p>
            <div v-if="!isProductNotification(item)" class="notification-prices">
              <strong>{{ formatMoney(item.totalAmount) }}</strong>
            </div>
            <div
              v-else
              class="notification-prices"
              :class="{ 'price-rise': item.type === 'PRICE_RISE' }"
            >
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
            <ElButton
              size="small"
              :loading="reading.has(notificationKey(item))"
              :disabled="markingAll"
              @click="viewNotification(item)"
              >{{ t(isProductNotification(item) ? '查看商品' : '查看订单') }}</ElButton
            >
            <ElButton
              v-if="item.readAt === null"
              size="small"
              text
              :loading="reading.has(notificationKey(item))"
              :disabled="markingAll"
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
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElButton, ElEmpty, ElImage, ElMessage, ElPagination, ElTag, vLoading } from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, NotificationItem, Pagination } from '@/types/api'
import { assetUrl, formatDateTime, formatMoney } from '@/utils/format'
import {
  isProductNotification,
  notificationDescription,
  notificationKey,
  notificationLabel,
  notificationName,
  notificationTarget,
} from '@/utils/notifications'
import { useNotificationsStore } from '@/stores/notifications'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ admin?: boolean }>()
const notifications = useNotificationsStore()
const user = useUserStore()
const router = useRouter()
const scope = computed(() => (props.admin ? 'order' : 'user'))
const endpoint = computed(() => (props.admin ? '/admin/notification' : '/notification'))
const unreadCount = computed(() =>
  props.admin ? notifications.orderUnreadCount : notifications.unreadCount,
)
const revision = computed(() =>
  props.admin ? notifications.orderRevision : notifications.revision,
)
const items = ref<NotificationItem[]>([])
const page = ref(1)
const unreadOnly = ref(false)
const loading = ref(false)
const error = ref('')
const reading = ref(new Set<string>())
const markingAll = ref(false)
const pagination = ref<Pagination>({ total: 0, pageSize: 20, currentPage: 1, pageCount: 1 })
let sequence = 0

const fetchNotifications = async () => {
  if (!user.logged) return
  const current = ++sequence
  const token = user.user?.token
  loading.value = true
  error.value = ''
  try {
    const { data } = await request.get<
      ApiResult<{
        notifications: NotificationItem[]
        pagination: Pagination
        unreadCount: number
      }>
    >(endpoint.value, {
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

const refreshNotifications = () => {
  void notifications.refreshUnread(scope.value)
  void fetchNotifications()
}

const setFilter = (unread: boolean) => {
  page.value = 1
  unreadOnly.value = unread
}

const markRead = async (item: NotificationItem): Promise<boolean> => {
  const key = notificationKey(item)
  if (!user.logged || markingAll.value || reading.value.has(key)) return false
  if (item.readAt !== null) return true
  const token = user.user?.token
  const pending = reading.value
  pending.add(key)
  try {
    await notifications.markRead(item.id, scope.value, item.type)
    return token === user.user?.token
  } catch (failure) {
    if (token === user.user?.token)
      ElMessage.error(t(failure instanceof Error ? failure.message : '标记已读失败'))
    return false
  } finally {
    pending.delete(key)
  }
}

const viewNotification = async (item: NotificationItem) => {
  if (await markRead(item)) await router.push(notificationTarget(item))
}

const markAllRead = async () => {
  if (
    !user.logged ||
    loading.value ||
    markingAll.value ||
    reading.value.size > 0 ||
    unreadCount.value === 0
  )
    return
  const token = user.user?.token
  markingAll.value = true
  try {
    await notifications.markAllRead(scope.value)
    if (token === user.user?.token) ElMessage.success(t('已全部标为已读'))
  } catch (failure) {
    if (token === user.user?.token)
      ElMessage.error(t(failure instanceof Error ? failure.message : '标记已读失败'))
  } finally {
    if (token === user.user?.token) markingAll.value = false
  }
}

watch([page, unreadOnly, revision], fetchNotifications, { immediate: true })
void notifications.refreshUnread(scope.value)
watch(
  [() => user.user?.token, () => props.admin],
  () => {
    sequence += 1
    items.value = []
    loading.value = false
    error.value = ''
    reading.value = new Set()
    markingAll.value = false
    page.value = 1
    unreadOnly.value = false
    if (user.logged) {
      void fetchNotifications()
      void notifications.refreshUnread(scope.value)
    }
  },
  { flush: 'sync' },
)
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
.admin-notifications {
  .page-title {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-bottom: 0.5rem;
  }
  .image-placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: var(--el-fill-color-light);
    color: var(--el-text-color-placeholder);
    font-size: 1.5rem;
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
