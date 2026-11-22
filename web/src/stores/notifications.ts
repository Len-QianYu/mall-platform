import { ref } from 'vue'
import { defineStore } from 'pinia'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { notificationSocketUrl } from '@/utils/productNotifications'
import type { ApiResult, ProductNotification } from '@/types/api'

export const useNotificationsStore = defineStore('notifications', () => {
  const userStore = useUserStore()
  const unreadCount = ref(0)
  const connected = ref(false)
  const revision = ref(0)
  const latestNotification = ref<ProductNotification | null>(null)
  let active = false
  let generation = 0
  let countRequest = 0
  let socket: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined
  let openTimer: ReturnType<typeof setTimeout> | undefined
  let heartbeat: ReturnType<typeof setInterval> | undefined
  let reconnectAttempts = 0
  let lastMessageAt = 0
  const received = new Set<number>()

  const refreshUnread = async (): Promise<void> => {
    if (!userStore.logged) return
    const token = userStore.user?.token
    const current = generation
    const requestId = ++countRequest
    try {
      const { data } = await request.get<ApiResult<{ unreadCount: number }>>(
        '/notification/unread-count',
      )
      if (
        data.status &&
        token === userStore.user?.token &&
        current === generation &&
        requestId === countRequest
      )
        unreadCount.value = data.payload.unreadCount
    } catch {
      // 下一次推送、重连或窗口重新获得焦点时重新同步。
    }
  }

  const sync = () => {
    void refreshUnread()
    revision.value += 1
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
            notification?: ProductNotification
          }
          if (message.type === 'refresh') sync()
          else if (
            message.type === 'notification' &&
            typeof message.notification?.id === 'number'
          ) {
            if (!received.has(message.notification.id)) {
              received.add(message.notification.id)
              if (received.size > 200) received.delete(received.values().next().value!)
              latestNotification.value = message.notification
            }
            sync()
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
    latestNotification.value = null
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

  const markRead = async (id: number): Promise<void> => {
    const { data } = await request.post<ApiResult>('/notification/read', { id })
    if (!data.status) throw new Error(data.payload.message || '标记已读失败')
    await refreshUnread()
    revision.value += 1
  }

  const markAllRead = async (): Promise<void> => {
    const { data } = await request.post<ApiResult>('/notification/read-all')
    if (!data.status) throw new Error(data.payload.message || '标记已读失败')
    await refreshUnread()
    revision.value += 1
  }

  return {
    unreadCount,
    connected,
    revision,
    latestNotification,
    refreshUnread,
    start,
    stop,
    markRead,
    markAllRead,
  }
})
