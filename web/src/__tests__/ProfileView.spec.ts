import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { ApiResult, User } from '@/types/api'
import { useUserStore } from '@/stores/user'
import ProfileView from '@/views/shop/ProfileView.vue'

const { getProfile, postProfile } = vi.hoisted(() => ({
  getProfile: vi.fn<(url: string) => Promise<{ data: ApiResult<{ user: User }> }>>(),
  postProfile:
    vi.fn<(url: string, fields: Record<string, unknown>) => Promise<{ data: ApiResult }>>(),
}))
vi.mock('@/utils/request', () => ({ default: { get: getProfile, post: postProfile } }))
enableAutoUnmount(afterEach)

const user: User = {
  id: 7,
  username: 'admin',
  name: '管理员',
  gender: 'F',
  role: 'admin',
  email: 'admin@example.com',
  phone: '13800138000',
  avatarUrl: null,
  status: 1,
  registeredAt: '2026-11-17T09:00:00',
  updatedAt: '2026-11-17T09:00:00',
}

const mountProfile = async () => {
  const pinia = createPinia()
  const store = useUserStore(pinia)
  store.login({ token: 'profile-test-token', user: { ...user } })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin', name: 'admin_dashboard', component: { template: '<p>dashboard</p>' } },
      { path: '/admin/profile', name: 'admin_profile', component: ProfileView },
      { path: '/login', name: 'login', component: { template: '<p>login</p>' } },
    ],
  })
  await router.push({ name: 'admin_profile' })
  const wrapper = mount(ProfileView, {
    props: { admin: true },
    global: {
      plugins: [pinia, router],
      stubs: {
        ElCard: { template: '<div><slot /></div>' },
        ImageUploader: {
          name: 'ImageUploader',
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<div class="avatar-uploader-stub" />',
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, store }
}

describe('administrator personal information page', () => {
  beforeEach(() => {
    localStorage.removeItem('user')
    vi.stubEnv('APP_BASE_URL', 'http://localhost:8080')
    getProfile.mockReset()
    postProfile.mockReset()
    getProfile.mockResolvedValue({ data: { status: true, payload: { user } } })
    postProfile.mockResolvedValue({ data: { status: true, payload: {} } })
  })

  afterEach(() => {
    localStorage.removeItem('user')
    vi.unstubAllEnvs()
  })

  it('loads the current account and presents the profile forms in a standalone page', async () => {
    const { wrapper } = await mountProfile()

    expect(getProfile).toHaveBeenCalledWith('/user/profile')
    expect(wrapper.get('.profile-side').text()).toContain('管理员')
    expect(wrapper.get('.profile-side').text()).toContain('@admin')
    expect(wrapper.text()).toContain('基本资料')
    expect(wrapper.text()).toContain('修改密码')
    expect(wrapper.get('h1').text()).toBe('个人中心')
    expect(wrapper.find('.side-links').exists()).toBe(false)
  })

  it('saves editable profile fields and updates the avatar while preserving token and role', async () => {
    const { wrapper, store } = await mountProfile()
    const form = wrapper.get('.profile-form')
    const inputs = form.findAll('input.el-input__inner')
    await inputs[1]!.setValue(' 新名字 ')
    wrapper
      .findComponent({ name: 'ImageUploader' })
      .vm.$emit('update:modelValue', '/avatars/new.png')
    await flushPromises()

    let complete: (result: { data: ApiResult }) => void = () => undefined
    postProfile.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          complete = resolve
        }),
    )
    await form.get('button').trigger('click')
    await flushPromises()

    expect(postProfile).toHaveBeenCalledWith('/user/update', {
      name: '新名字',
      gender: 'F',
      email: 'admin@example.com',
      phone: '13800138000',
      avatarUrl: '/avatars/new.png',
    })
    expect((inputs[1]!.element as HTMLInputElement).disabled).toBe(true)

    complete({ data: { status: true, payload: {} } })
    await flushPromises()

    expect(store.user?.token).toBe('profile-test-token')
    expect(store.user?.user.role).toBe('admin')
    expect(store.user?.user.name).toBe('新名字')
    expect(store.user?.user.avatarUrl).toBe('/avatars/new.png')
    expect((inputs[1]!.element as HTMLInputElement).disabled).toBe(false)
  })

  it('disables editing after a failed load so empty fields cannot overwrite the account', async () => {
    getProfile.mockRejectedValueOnce(new Error('offline'))
    const { wrapper } = await mountProfile()

    expect((wrapper.get('.profile-form input').element as HTMLInputElement).disabled).toBe(true)
    await wrapper.get('.profile-form button').trigger('click')
    await flushPromises()

    expect(postProfile).not.toHaveBeenCalled()
  })
})
