<template>
  <ElPopover
    v-model:visible="visible"
    trigger="click"
    placement="bottom-end"
    role="dialog"
    :width="380"
    :offset="12"
    :boundaries-padding="12"
    :show-arrow="false"
    :persistent="false"
    :popper-style="{
      padding: '0',
      width: 'min(380px, calc(100vw - 24px))',
      borderRadius: '12px',
      overflow: 'hidden',
      boxShadow: 'var(--el-box-shadow-light)',
    }"
    popper-class="shop-notification-popper"
  >
    <template #reference>
      <slot :open="visible" />
    </template>

    <section
      class="notification-dropdown"
      :aria-label="t('消息通知')"
      @keydown.esc.stop.prevent="visible = false"
    >
      <div class="dropdown-heading">
        <div>
          <h2>{{ t('消息通知') }}</h2>
          <p v-if="user.logged">{{ t('{count} 条未读', { count: unreadCount }) }}</p>
        </div>
        <div v-if="user.logged" class="dropdown-heading-actions">
          <ElButton
            class="dropdown-refresh"
            text
            :disabled="loading"
            :aria-label="t('刷新通知')"
            :title="loading ? t('正在刷新通知') : t('刷新通知')"
            @click="refreshNotifications"
          >
            <i class="bi bi-arrow-clockwise" aria-hidden="true"></i>
          </ElButton>
          <ElButton
            class="dropdown-read-all"
            text
            size="small"
            :loading="markingAll"
            :disabled="loading || reading.size > 0 || unreadCount === 0"
            @click="markAllRead"
          >
            {{ t('全部已读') }}
          </ElButton>
        </div>
      </div>

      <div v-if="user.logged" class="dropdown-tabs" role="tablist" :aria-label="t('通知筛选')">
        <button type="button" role="tab" :aria-selected="!unreadOnly" @click="unreadOnly = false">
          {{ t('全部通知') }}
        </button>
        <button type="button" role="tab" :aria-selected="unreadOnly" @click="unreadOnly = true">
          {{ t('未读通知') }}
        </button>
      </div>

      <div class="dropdown-notification-list" :aria-busy="loading">
        <div v-if="!user.logged" class="dropdown-state dropdown-login">
          <i class="bi bi-bell" aria-hidden="true"></i>
          <p>{{ t('登录后查看消息通知') }}</p>
          <RouterLink
            :to="{ name: 'login', query: { redirect: route.fullPath } }"
            @click="visible = false"
          >
            {{ t('登录') }}
          </RouterLink>
        </div>
        <div v-else-if="loading" class="dropdown-state" role="status">
          <i class="bi bi-arrow-clockwise is-spinning" aria-hidden="true"></i>
          <p>{{ t('正在加载通知') }}</p>
        </div>
        <div v-else-if="error" class="dropdown-state dropdown-error" role="alert">
          <p>{{ t(error) }}</p>
          <ElButton size="small" @click="refreshNotifications">{{ t('重新加载') }}</ElButton>
        </div>
        <div v-else-if="items.length === 0" class="dropdown-state">
          <i class="bi bi-bell-slash" aria-hidden="true"></i>
          <p>
            {{
              t(unreadOnly ? '暂时没有未读通知' : admin ? '暂时没有新订单通知' : '暂时没有消息通知')
            }}
          </p>
          <ElButton v-if="unreadOnly" size="small" text @click="unreadOnly = false">
            {{ t('查看全部通知') }}
          </ElButton>
          <RouterLink
            v-else
            :to="{ name: admin ? 'admin_orders' : 'products' }"
            @click="visible = false"
          >
            {{ t(admin ? '查看订单' : '去收藏好物') }}
          </RouterLink>
        </div>
        <ul v-else class="dropdown-items">
          <li
            v-for="item in items"
            :key="notificationKey(item)"
            class="dropdown-notification-item"
            :class="{ unread: item.readAt === null }"
          >
            <button
              type="button"
              class="dropdown-product"
              :disabled="markingAll || reading.has(notificationKey(item))"
              :aria-label="
                t(isProductNotification(item) ? '查看商品' : '查看订单') +
                '：' +
                notificationName(item)
              "
              @click="viewNotification(item)"
            >
              <ElImage
                v-if="isProductNotification(item) && item.productImage"
                class="dropdown-product-image"
                :src="assetUrl(item.productImage)"
                fit="cover"
                alt=""
              />
              <span v-else class="dropdown-product-image image-placeholder">
                <i
                  :class="
                    item.type === 'ORDER_SHIPPED'
                      ? 'bi bi-truck'
                      : item.type === 'NEW_ORDER'
                        ? 'bi bi-receipt'
                        : 'bi bi-box2-heart'
                  "
                  aria-hidden="true"
                ></i>
              </span>
              <span class="dropdown-product-content">
                <span class="dropdown-item-title">
                  <span class="dropdown-type" :class="item.type">{{
                    t(notificationLabel(item))
                  }}</span>
                  <span
                    v-if="item.readAt === null"
                    class="unread-dot"
                    :aria-label="t('未读')"
                  ></span>
                </span>
                <strong class="dropdown-product-name">{{ notificationName(item) }}</strong>
                <span class="dropdown-description">{{ notificationDescription(item) }}</span>
              </span>
            </button>
            <div class="dropdown-item-footer">
              <time :datetime="item.createdAt">{{ formatDateTime(item.createdAt) }}</time>
              <ElButton
                v-if="item.readAt === null"
                class="dropdown-mark-read"
                text
                size="small"
                :loading="reading.has(notificationKey(item))"
                :disabled="markingAll"
                @click="markRead(item)"
              >
                {{ t('标为已读') }}
              </ElButton>
              <span v-else>{{ t('已读') }}</span>
            </div>
          </li>
        </ul>
      </div>

      <RouterLink
        v-if="user.logged"
        class="dropdown-view-all"
        :to="{ name: admin ? 'admin_notifications' : 'notifications' }"
        @click="visible = false"
      >
        {{ t('查看全部通知') }} <i class="bi bi-arrow-right" aria-hidden="true"></i>
      </RouterLink>
    </section>
  </ElPopover>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElButton, ElImage, ElMessage, ElPopover } from 'element-plus'
import { t } from '@/i18n'
import request from '@/utils/request'
import type { ApiResult, NotificationItem } from '@/types/api'
import { assetUrl, formatDateTime } from '@/utils/format'
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

defineSlots<{ default(props: { open: boolean }): unknown }>()
const props = defineProps<{ admin?: boolean }>()

const notifications = useNotificationsStore()
const user = useUserStore()
const route = useRoute()
const router = useRouter()
const scope = computed(() => (props.admin ? 'order' : 'user'))
const endpoint = computed(() => (props.admin ? '/admin/notification' : '/notification'))
const unreadCount = computed(() =>
  props.admin ? notifications.orderUnreadCount : notifications.unreadCount,
)
const revision = computed(() =>
  props.admin ? notifications.orderRevision : notifications.revision,
)
const visible = ref(false)
const unreadOnly = ref(false)
const items = ref<NotificationItem[]>([])
const loading = ref(false)
const error = ref('')
const reading = ref(new Set<string>())
const markingAll = ref(false)
let sequence = 0

const fetchNotifications = async () => {
  if (!visible.value || !user.logged) return
  const current = ++sequence
  const token = user.user?.token
  loading.value = true
  error.value = ''
  try {
    const { data } = await request.get<ApiResult<{ notifications: NotificationItem[] }>>(
      endpoint.value,
      { params: { page: 1, pageSize: 8, unreadOnly: unreadOnly.value } },
    )
    if (current !== sequence || token !== user.user?.token || !visible.value) return
    if (!data.status) {
      error.value = data.payload.message || '加载通知失败，请稍后重试'
      return
    }
    items.value = data.payload.notifications
  } catch {
    if (current === sequence && token === user.user?.token) error.value = '加载通知失败，请稍后重试'
  } finally {
    if (current === sequence) loading.value = false
  }
}

const refreshNotifications = () => {
  void notifications.refreshUnread(scope.value)
  void fetchNotifications()
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
  if (await markRead(item)) {
    visible.value = false
    await router.push(notificationTarget(item))
  }
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
  } catch (failure) {
    if (token === user.user?.token)
      ElMessage.error(t(failure instanceof Error ? failure.message : '标记已读失败'))
  } finally {
    if (token === user.user?.token) markingAll.value = false
  }
}

watch([visible, unreadOnly, revision], () => {
  if (visible.value) void fetchNotifications()
  else {
    sequence += 1
    loading.value = false
  }
})

watch(visible, (open) => {
  if (open && user.logged) void notifications.refreshUnread(scope.value)
})

watch(
  [() => user.user?.token, () => props.admin],
  () => {
    sequence += 1
    visible.value = false
    items.value = []
    loading.value = false
    error.value = ''
    unreadOnly.value = false
    reading.value = new Set()
    markingAll.value = false
  },
  { flush: 'sync' },
)

watch(
  () => route.fullPath,
  () => {
    visible.value = false
  },
)
onBeforeUnmount(() => {
  sequence += 1
})
</script>

<style lang="scss" scoped>
.notification-dropdown {
  color: var(--el-text-color-primary);
  font-size: 0.875rem;
}

.dropdown-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 1rem;

  h2 {
    margin: 0;
    font-size: 1rem;
    font-weight: 600;
  }

  p {
    margin: 0.25rem 0 0;
    color: var(--el-text-color-secondary);
    font-size: 0.75rem;
  }
}

.dropdown-heading-actions {
  display: flex;
  align-items: center;
  gap: 0.25rem;

  .el-button {
    margin: 0;
  }
}

.dropdown-refresh {
  width: 28px;
  height: 28px;
  padding: 0;
}

.dropdown-tabs {
  display: flex;
  gap: 1.25rem;
  padding: 0 1rem;
  border-bottom: 1px solid var(--el-border-color-light);

  button {
    padding: 0.5rem 0;
    border: 0;
    border-bottom: 2px solid transparent;
    background: transparent;
    color: var(--el-text-color-secondary);
    font: inherit;
    cursor: pointer;

    &[aria-selected='true'] {
      border-bottom-color: var(--el-color-primary);
      color: var(--el-color-primary);
    }
  }
}

.dropdown-notification-list {
  max-height: min(400px, calc(100dvh - 240px));
  overflow-y: auto;
  overscroll-behavior: contain;
}

.dropdown-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  min-height: 160px;
  padding: 1.5rem;
  text-align: center;
  color: var(--el-text-color-secondary);

  > .bi {
    font-size: 1.5rem;
  }

  p {
    margin: 0;
    line-height: 1.6;
  }

  a {
    color: var(--el-color-primary);
    text-decoration: none;
  }
}

.dropdown-error {
  color: var(--el-color-danger);
}

.dropdown-items {
  margin: 0;
  padding: 0;
  list-style: none;
}

.dropdown-notification-item {
  padding: 0.875rem 1rem 0.5rem;
  border-bottom: 1px solid var(--el-border-color-extra-light);

  &.unread {
    background-color: var(--el-color-primary-light-9);
  }

  &:last-child {
    border-bottom: 0;
  }
}

.dropdown-product {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &:disabled {
    cursor: wait;
  }

  &:hover .dropdown-product-name {
    color: var(--el-color-primary);
  }
}

.dropdown-product-image {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 6px;
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-placeholder);
  font-size: 1.25rem;
}

.dropdown-product-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.dropdown-item-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.dropdown-type {
  color: var(--el-color-primary);
  font-size: 0.75rem;

  &.PRICE_DROP {
    color: var(--el-color-success);
  }

  &.PRICE_RISE {
    color: var(--el-color-warning);
  }
}

.unread-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: var(--el-color-primary);
}

.dropdown-product-name {
  font-size: 0.8125rem;
  overflow-wrap: anywhere;
}

.dropdown-description {
  color: var(--el-text-color-regular);
  font-size: 0.75rem;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.dropdown-item-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  min-height: 28px;
  margin-left: 56px;
  color: var(--el-text-color-secondary);
  font-size: 0.6875rem;

  .el-button {
    margin: 0;
  }
}

.dropdown-view-all {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.875rem 1rem;
  border-top: 1px solid var(--el-border-color-light);
  color: var(--el-color-primary);
  text-decoration: none;

  &:hover {
    background-color: var(--el-fill-color-light);
  }
}

.is-spinning {
  animation: notification-dropdown-spin 0.8s linear infinite;
}

@keyframes notification-dropdown-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .is-spinning {
    animation: none;
  }
}
</style>
