import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

// 用户角色
export type UserRole = 'user' | 'admin'

// 后端 /login 返回的用户信息（密码为 WRITE_ONLY，不会下发；未填写的资料为 null）
export interface UserInfo {
  id?: number
  username: string
  name?: string | null
  gender?: 'F' | 'M' | null
  role: UserRole
  email?: string | null
  phone?: string | null
  avatarUrl?: string | null
  status?: number
  registeredAt?: string
}

// 登录后写入 store 的结构：用户信息 + JWT
export interface LoginResult {
  token: string
  user: UserInfo
}

const STORAGE_KEY = 'user'

// 登录状态持久化和鉴权
export const useUserStore = defineStore('user', () => {
  const user = ref<LoginResult | null>(null) // 存储用户信息

  const name = computed(() => user.value?.user?.username || '')

  // 获取用户角色，默认为用户
  const role = computed<UserRole>(() => user.value?.user?.role || 'user')

  // 判断是否为用户
  const isUser = computed(() => role.value === 'user')

  // 判断是否为管理员
  const isAdmin = computed(() => role.value === 'admin')

  // JWT token 的登录状态检查
  const logged = computed(() => {
    if (user.value === null) return false
    return user.value.token !== null && user.value.token !== undefined
  })

  // 刷新页面后从本地存储恢复登录状态
  const granted = (): boolean => {
    return logged.value
  }

  const login = (data: LoginResult): void => {
    user.value = data // 设置用户信息
    cache(STORAGE_KEY, data)
  }

  const logout = (): void => {
    user.value = null // 清除用户信息
    clear(STORAGE_KEY)
  }

  // 更新当前用户信息
  const updateUser = (userData: Partial<UserInfo>): void => {
    if (user.value && user.value.user) {
      user.value = {
        ...user.value,
        user: { ...user.value.user, ...userData },
      }
      cache(STORAGE_KEY, user.value)
    }
  }

  const load = <T>(key: string): T | null => {
    const raw = window.localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : null
  }

  // 本地存储管理
  const cache = (key: string, value: unknown): void => {
    window.localStorage.setItem(key, JSON.stringify(value))
  }

  const clear = (key: string): void => {
    window.localStorage.removeItem(key)
  }

  // 初始化：从本地存储恢复用户信息
  const storedUser = window.localStorage.getItem(STORAGE_KEY)
  if (storedUser) {
    try {
      // JWT token 由 Spring Security 在每次请求时校验，这里无需检查过期时间
      user.value = JSON.parse(storedUser) as LoginResult
    } catch (error) {
      // 解析存储的用户数据失败，清除用户信息
      console.error('Failed to parse stored user data:', error)
      clear(STORAGE_KEY)
    }
  }

  return {
    user,
    name,
    role,
    logged,
    isUser,
    isAdmin,
    login,
    logout,
    updateUser,
    load,
    cache,
    clear,
    granted,
  }
})
