import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, setActivePinia, type Pinia } from 'pinia'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useFavoritesStore } from '@/stores/favorites'
import FavoriteButton from '@/components/shop/FavoriteButton.vue'

type MockResponse = { data: { status: boolean; payload: object } }

const { get, post, info, success, error } = vi.hoisted(() => ({
  get: vi.fn<(url: string) => Promise<MockResponse>>(),
  post: vi.fn<
    (url: string, body: { productId: number; favorite: boolean }) => Promise<MockResponse>
  >(),
  info: vi.fn<(message: string) => void>(),
  success: vi.fn<(message: string) => void>(),
  error: vi.fn<(message: string) => void>(),
}))
vi.mock('@/utils/request', () => ({ default: { get, post } }))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessage: { info, success, error },
}))
enableAutoUnmount(afterEach)

let pinia: Pinia
const response = (payload: object) => ({ data: { status: true, payload } })
const login = (id = 7) =>
  useUserStore().login({ token: `token-${id}`, user: { id, username: `user-${id}`, role: 'user' } })

const mountButton = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/products/:id', name: 'product_detail', component: { template: '<p>product</p>' } },
      { path: '/login', name: 'login', component: { template: '<p>login</p>' } },
    ],
  })
  await router.push('/products/11')
  const wrapper = mount(FavoriteButton, {
    props: { productId: 11, productName: '测试商品', showLabel: true },
    global: { plugins: [pinia, router] },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('product favorites', () => {
  beforeEach(() => {
    localStorage.clear()
    pinia = createPinia()
    setActivePinia(pinia)
    vi.clearAllMocks()
    get.mockResolvedValue(response({ productIds: [] }))
    post.mockImplementation(async (_url, body: { favorite: boolean }) =>
      response({ favorite: body.favorite }),
    )
  })
  afterEach(() => {
    disposePinia(pinia)
  })

  it('sends guests to login and preserves the product page', async () => {
    const { wrapper, router } = await mountButton()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/products/11')
    expect(post).not.toHaveBeenCalled()
  })

  it('saves and cancels a favorite with an accessible heart state', async () => {
    login()
    const { wrapper } = await mountButton()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(post).toHaveBeenLastCalledWith('/favorite', { productId: 11, favorite: true })
    expect(wrapper.get('button').attributes('aria-pressed')).toBe('true')
    expect(wrapper.text()).toBe('已收藏')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(post).toHaveBeenLastCalledWith('/favorite', { productId: 11, favorite: false })
    expect(wrapper.get('button').attributes('aria-pressed')).toBe('false')
  })

  it('waits for saved favorites before deciding which state to send', async () => {
    let complete!: (value: ReturnType<typeof response>) => void
    get.mockReturnValueOnce(
      new Promise((resolve) => {
        complete = resolve
      }),
    )
    login()
    const favorites = useFavoritesStore()
    const action = favorites.toggle(11)
    expect(post).not.toHaveBeenCalled()
    complete(response({ productIds: [11] }))
    await action
    expect(post).toHaveBeenCalledWith('/favorite', { productId: 11, favorite: false })
  })

  it('blocks duplicate clicks across all hearts for the same product', async () => {
    login()
    const favorites = useFavoritesStore()
    await flushPromises()
    let complete!: (value: ReturnType<typeof response>) => void
    post.mockReturnValueOnce(
      new Promise((resolve) => {
        complete = resolve
      }),
    )
    const action = favorites.toggle(11)
    await flushPromises()
    expect(favorites.pending.has(11)).toBe(true)
    expect(await favorites.toggle(11)).toBeNull()
    expect(post).toHaveBeenCalledTimes(1)
    complete(response({ favorite: true }))
    await action
    expect(favorites.pending.has(11)).toBe(false)
  })

  it('keeps the previous favorite state when saving fails', async () => {
    login()
    const { wrapper } = await mountButton()
    post.mockResolvedValueOnce({ data: { status: false, payload: { message: '暂时不可用' } } })
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.get('button').attributes('aria-pressed')).toBe('false')
    expect(wrapper.get('button').attributes('disabled')).toBeUndefined()
    expect(error).toHaveBeenCalledWith('暂时不可用')
  })

  it('discards a previous accounts late favorite response', async () => {
    login()
    const favorites = useFavoritesStore()
    await flushPromises()
    let complete!: (value: ReturnType<typeof response>) => void
    post.mockReturnValueOnce(
      new Promise((resolve) => {
        complete = resolve
      }),
    )
    const action = favorites.toggle(11)
    await flushPromises()
    useUserStore().logout()
    get.mockResolvedValue(response({ productIds: [22] }))
    login(8)
    await flushPromises()
    complete(response({ favorite: true }))
    expect(await action).toBeNull()
    expect([...favorites.productIds]).toEqual([22])
  })
})
