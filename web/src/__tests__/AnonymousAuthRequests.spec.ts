import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import type { AxiosAdapter } from 'axios'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'

vi.mock('@/router', () => ({ default: { push: vi.fn<(destination: object) => Promise<void>>() } }))
afterEach(() => localStorage.removeItem('user'))

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
    expect(observed).toEqual([undefined, undefined, undefined, 'Bearer old-token'])
    disposePinia(pinia)
  })
})
