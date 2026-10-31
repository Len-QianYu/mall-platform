import axios, { type AxiosInstance, type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.APP_BASE_URL,
})

// 请求拦截器：自动添加 Authorization 头
instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const userStore = useUserStore()

  if (userStore.logged && userStore.user) {
    // Spring Security 资源服务器要求 Bearer 前缀
    config.headers.Authorization = `Bearer ${userStore.user.token}`
  }

  return config
})

// 响应拦截器：处理 token 过期
instance.interceptors.response.use(
  (response) => {
    return response
  },
  (error: AxiosError) => {
    // 检查是否是 401 未授权错误
    if (error.response && error.response.status === 401) {
      const userStore = useUserStore()

      // 清除用户登录状态
      userStore.logout()

      // 跳转到登录页
      router.push({ name: 'login' })
    }
    return Promise.reject(error)
  },
)

export default instance
