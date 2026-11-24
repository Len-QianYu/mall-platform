import { ref } from 'vue'
import { defineStore } from 'pinia'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { notificationSocketUrl } from '@/utils/productNotifications'
import { notificationKey } from '@/utils/notifications'
import type { ApiResult, NotificationItem, OrderNotification, UserNotification } from '@/types/api'

type NotificationScope = 'user' | 'order'
const endpoint = (scope: NotificationScope) =>
  scope === 'order' ? '/admin/notification' : '/notification'

export const useNotificationsStore = defineStore('notifications', () => {
  const userStore = useUserStore()
  const unreadCount = ref(0)
  const orderUnreadCount = ref(0)
  const connected = ref(false)
  const revision = ref(0)
  const orderRevision = ref(0)
  const latestNotification = ref<UserNotification | null>(null)
  const latestOrderNotification = ref<OrderNotification | null>(null)
  let active = false
  let generation = 0
  let countRequest = 0
  let orderCountRequest = 0
  let socket: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined
  let openTimer: ReturnType<typeof setTimeout> | undefined
  let heartbeat: ReturnType<typeof setInterval> | undefined
  let reconnectAttempts = 0
  let lastMessageAt = 0
  const received = new Set<string>()

  const refreshUnread = async (scope: NotificationScope = 'user'): Promise<void> => {
    if (!userStore.logged || (scope === 'order' && !userStore.isAdmin)) return
    const token = userStore.user?.token
    const current = generation
    const requestId = scope === 'order' ? ++orderCountRequest : ++countRequest
    try {
      const { data } = await request.get<ApiResult<{ unreadCount: number }>>(
        `${endpoint(scope)}/unread-count`,
      )
      if (
        data.status &&
        token === userStore.user?.token &&
        current === generation &&
        requestId === (scope === 'order' ? orderCountRequest : countRequest)
      )
        (scope === 'order' ? orderUnreadCount : unreadCount).value = data.payload.unreadCount
    } catch {
      // 下一次推送、重连或窗口重新获得焦点时重新同步。
    }
  }

  const sync = (scope?: NotificationScope) => {
    if (!scope || scope === 'user') {
      void refreshUnread()
      revision.value += 1
    }
    if ((!scope || scope === 'order') && userStore.isAdmin) {
      void refreshUnread('order')
      orderRevision.value += 1
    }
  }

  const scheduleReconnect = (current: number) => {
    if (!active || current !== generation || reconnectTimer) return
    const delay = Math.min(1000 * 2 ** reconnectAttempts++, 30000)
    reconnectTimer = setTimeout(() => {
      reconnectTimer = undefined
      void connect(current)
    }, delay)
  }

  const connect = async (current: number): Promise<void> => {
    if (!active || current !== generation || socket) return
    try {
      const { data } = await request.post<ApiResult<{ ticket: string }>>(
        '/notification/socket-ticket',
      )
      if (!active || current !== generation) return
      if (!data.status) throw new Error('获取通知连接失败')
      const connection = new WebSocket(notificationSocketUrl(data.payload.ticket))
      socket = connection
      const isCurrent = () => active && current === generation && socket === connection
      openTimer = setTimeout(() => {
        if (isCurrent() && connection.readyState !== WebSocket.OPEN) connection.close()
      }, 10000)
      connection.onopen = () => {
        if (!isCurrent()) return
        clearTimeout(openTimer)
        openTimer = undefined
        connected.value = true
        reconnectAttempts = 0
        lastMessageAt = Date.now()
        sync()
        heartbeat = setInterval(() => {
          if (!isCurrent()) return
          if (Date.now() - lastMessageAt > 60000) connection.close()
          else if (connection.readyState === WebSocket.OPEN) connection.send('ping')
        }, 20000)
      }
      connection.onmessage = (event: MessageEvent<string>) => {
        if (!isCurrent()) return
        lastMessageAt = Date.now()
        try {
          const message = JSON.parse(event.data) as {
            type?: string
            notification?: NotificationItem
          }
          if (message.type === 'refresh') sync('user')
          else if (message.type === 'order_refresh') sync('order')
          else if (
            (message.type === 'notification' || message.type === 'order_notification') &&
            typeof message.notification?.id === 'number'
          ) {
            const item = message.notification
            const order = message.type === 'order_notification'
            if (order ? !userStore.isAdmin || item.type !== 'NEW_ORDER' : item.type === 'NEW_ORDER')
              return
            const key = notificationKey(item)
            if (!received.has(key)) {
              received.add(key)
              if (received.size > 200) received.delete(received.values().next().value!)
              if (item.type === 'NEW_ORDER') latestOrderNotification.value = item
              else latestNotification.value = item
            }
            sync(order ? 'order' : 'user')
          }
        } catch {
          // 非通知消息不改变用户数据。
        }
      }
      connection.onerror = () => {
        if (isCurrent()) connection.close()
      }
      connection.onclose = () => {
        if (!isCurrent()) return
        clearTimeout(openTimer)
        openTimer = undefined
        clearInterval(heartbeat)
        heartbeat = undefined
        socket = null
        connected.value = false
        scheduleReconnect(current)
      }
    } catch {
      scheduleReconnect(current)
    }
  }

  const onFocus = () => {
    if (active) sync()
  }
  const onVisibility = () => {
    if (document.visibilityState === 'visible') onFocus()
  }

  const stop = () => {
    active = false
    generation += 1
    countRequest += 1
    orderCountRequest += 1
    clearTimeout(reconnectTimer)
    clearTimeout(openTimer)
    clearInterval(heartbeat)
    reconnectTimer = undefined
    openTimer = undefined
    heartbeat = undefined
    const previous = socket
    socket = null
    previous?.close()
    window.removeEventListener('focus', onFocus)
    document.removeEventListener('visibilitychange', onVisibility)
    connected.value = false
    unreadCount.value = 0
    orderUnreadCount.value = 0
    latestNotification.value = null
    latestOrderNotification.value = null
    received.clear()
  }

  const start = () => {
    stop()
    if (!userStore.logged) return
    active = true
    reconnectAttempts = 0
    window.addEventListener('focus', onFocus)
    document.addEventListener('visibilitychange', onVisibility)
    sync()
    void connect(generation)
  }

  const markRead = async (
    id: number,
    scope: NotificationScope = 'user',
    type?: NotificationItem['type'],
  ): Promise<void> => {
    const token = userStore.user?.token
    const current = generation
    const body = type === 'ORDER_SHIPPED' ? { id, type } : { id }
    const { data } = await request.post<ApiResult>(`${endpoint(scope)}/read`, body)
    if (!data.status) throw new Error(data.payload.message || '标记已读失败')
    if (token !== userStore.user?.token || current !== generation) return
    await refreshUnread(scope)
    if (token === userStore.user?.token && current === generation)
      (scope === 'order' ? orderRevision : revision).value += 1
  }

  const markAllRead = async (scope: NotificationScope = 'user'): Promise<void> => {
    const token = userStore.user?.token
    const current = generation
    const { data } = await request.post<ApiResult>(`${endpoint(scope)}/read-all`)
    if (!data.status) throw new Error(data.payload.message || '标记已读失败')
    if (token !== userStore.user?.token || current !== generation) return
    await refreshUnread(scope)
    if (token === userStore.user?.token && current === generation)
      (scope === 'order' ? orderRevision : revision).value += 1
  }

  return {
    unreadCount,
    orderUnreadCount,
    connected,
    revision,
    orderRevision,
    latestNotification,
    latestOrderNotification,
    refreshUnread,
    start,
    stop,
    markRead,
    markAllRead,
  }
})
