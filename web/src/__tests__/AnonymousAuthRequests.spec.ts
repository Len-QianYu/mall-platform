import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { AxiosError, type AxiosAdapter } from 'axios'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
import router from '@/router'

vi.mock('@/router', () => ({ default: { push: vi.fn<(destination: object) => Promise<void>>() } }))
afterEach(() => {
  localStorage.removeItem('user')
  vi.clearAllMocks()
})

describe('anonymous recovery requests', () => {
  it('does not attach a stale token to login or recovery while keeping authentication for profile requests', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    useUserStore().login({ token: 'old-token', user: { username: 'owner', role: 'user' } })
    const observed: unknown[] = []
    const adapter: AxiosAdapter = (config) => {
      observed.push(config.headers.get('Authorization'))
      return Promise.resolve({
        data: { status: true, payload: {} },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      })
    }
    for (const endpoint of ['/login', '/account/password-reset/code', '/account/password-reset']) {
      await request.post(endpoint, {}, { adapter })
    }
    await request.get('/user/profile', { adapter })
    useUserStore().login({ token: 'refreshed-token', user: { username: 'owner', role: 'user' } })
    await request.get('/user/profile', { adapter })
    expect(observed).toEqual([
      undefined,
      undefined,
      undefined,
      'Bearer old-token',
      'Bearer refreshed-token',
    ])
    disposePinia(pinia)
  })

  it('clears the previous login and its persisted token when a protected request returns 401', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    try {
      const store = useUserStore()
      store.login({ token: 'revoked-token', user: { username: 'owner', role: 'user' } })
      const adapter: AxiosAdapter = (config) =>
        Promise.reject(
          new AxiosError('Login revoked', 'ERR_BAD_REQUEST', config, undefined, {
            data: {},
            status: 401,
            statusText: 'Unauthorized',
            headers: {},
            config,
          }),
        )

      await expect(request.get('/user/profile', { adapter })).rejects.toMatchObject({
        response: { status: 401 },
      })
      expect(store.logged).toBe(false)
      expect(store.user).toBeNull()
      expect(localStorage.getItem('user')).toBeNull()
      expect(router.push).toHaveBeenCalledExactlyOnceWith({ name: 'login' })
    } finally {
      disposePinia(pinia)
    }
  })
})
