import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import LoginView from '@/views/auth/LoginView.vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { saveRememberedAccount } from '@/utils/rememberAccount'
import PasswordResetForm from '@/components/auth/PasswordResetForm.vue'

vi.mock('@/utils/request', () => ({ default: { post: vi.fn<typeof request.post>() } }))
vi.mock('/favicon.ico', () => ({ default: '/favicon.ico' }))

const wrappers: VueWrapper[] = []

const mountLogin = async (url = '/login') => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: LoginView },
      { path: '/login/email', name: 'email_login', component: LoginView },
      { path: '/register', name: 'register', component: { template: '<p>register</p>' } },
      { path: '/', name: 'index', component: { template: '<p>shop</p>' } },
      { path: '/cart', name: 'cart', component: { template: '<p>cart</p>' } },
      { path: '/admin', name: 'admin_dashboard', component: { template: '<p>admin</p>' } },
    ],
  })
  await router.push(url)
  const wrapper = mount(RouterView, {
    attachTo: document.body,
    global: {
      plugins: [createPinia(), router],
      stubs: {
        ElForm: defineComponent({
          setup(_, { expose, slots }) {
            expose({ validate: () => Promise.resolve(true) })
            return () => h('form', slots.default?.())
          },
        }),
        ElFormItem: { template: '<div><slot /></div>' },
        ElInput: defineComponent({
          props: { modelValue: String },
          emits: ['update:modelValue'],
          setup(props, { emit }) {
            return () =>
              h('input', {
                value: props.modelValue,
                onInput: (event: Event) =>
                  emit('update:modelValue', (event.target as HTMLInputElement).value),
              })
          },
        }),
        ElButton: { template: '<button><slot /></button>' },
        ModerationIcon: true,
        PasswordResetForm: {
          name: 'PasswordResetForm',
          props: ['initialEmail'],
          template: '<div class="reset-form-stub" />',
        },
        ElDialog: {
          props: ['modelValue'],
          template: '<section v-if="modelValue"><slot /></section>',
        },
      },
    },
  })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper.getComponent(LoginView)
}

const advanceTime = async (milliseconds: number) => {
  vi.advanceTimersByTime(milliseconds)
  await nextTick()
}

describe('login view', () => {
  beforeEach(() => {
    localStorage.removeItem('user')
    localStorage.removeItem('remembered-account')
    vi.mocked(request.post).mockReset()
    vi.useFakeTimers({ toFake: ['Date', 'setTimeout', 'clearTimeout'] })
  })

  afterEach(() => {
    for (const wrapper of wrappers.splice(0)) wrapper.unmount()
    document.body.replaceChildren()
    localStorage.removeItem('remembered-account')
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('logs in with a linked email and remembers the email method', async () => {
    const wrapper = await mountLogin('/login?redirect=/cart')
    vi.mocked(request.post).mockResolvedValue({
      data: {
        status: true,
        payload: {
          user: {
            id: 10,
            username: 'owner',
            role: 'user',
            email: 'owner@example.com',
            emailVerified: true,
          },
          token: 'email-token',
        },
      },
    })
    await wrapper.get('.email-login-entry').trigger('click')
    await flushPromises()
    const inputs = wrapper.findAll('input')
    await inputs[0]!.setValue('OWNER@EXAMPLE.COM')
    await inputs[1]!.setValue('123456')
    await wrapper.get('#remember-account').setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(request.post).toHaveBeenCalledWith('/login', {
      email: 'owner@example.com',
      password: '123456',
      role: 'user',
    })
    expect(JSON.parse(localStorage.getItem('remembered-account')!)).toMatchObject({
      username: 'owner@example.com',
      method: 'email',
      role: 'user',
    })
    expect(wrapper.vm.$router.currentRoute.value.name).toBe('cart')
    useUserStore().logout()
    const restored = await mountLogin()
    expect(restored.vm.$router.currentRoute.value.name).toBe('email_login')
    expect(restored.find('.email-login-entry').exists()).toBe(false)
    expect(restored.find('.login-back-button').exists()).toBe(true)
    expect((restored.get('input').element as HTMLInputElement).value).toBe('owner@example.com')
  })

  it('opens the email page and returns to the original login with its account and role', async () => {
    const wrapper = await mountLogin('/login?username=new-user&redirect=/cart#account')
    const originalPath = wrapper.vm.$router.currentRoute.value.fullPath
    await wrapper.get('button[aria-label="管理员登录"]').trigger('click')
    await wrapper.findAll('input')[0]!.setValue('admin-owner')
    await wrapper.findAll('input')[1]!.setValue('username-password')
    await wrapper.get('#remember-account').setValue(true)

    const entry = wrapper.get('.email-login-entry')
    expect(entry.text()).toBe('')
    expect(entry.get('.bi-envelope').attributes('aria-hidden')).toBe('true')
    await entry.trigger('click')
    await flushPromises()

    expect(wrapper.vm.$router.currentRoute.value.path).toBe('/login/email')
    expect(wrapper.vm.$router.currentRoute.value.query).toEqual({
      username: 'new-user',
      redirect: '/cart',
    })
    expect(wrapper.get('.form-title').text()).toBe('邮箱登录')
    expect(wrapper.get('input').attributes('type')).toBe('email')
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('')
    expect(wrapper.find('.email-login-entry').exists()).toBe(false)
    await wrapper.findAll('input')[0]!.setValue('admin@example.com')
    await wrapper.findAll('input')[1]!.setValue('email-password')
    await wrapper.get('.login-back-button').trigger('click')
    await flushPromises()

    expect(wrapper.vm.$router.currentRoute.value.fullPath).toBe(originalPath)
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('admin-owner')
    expect((wrapper.findAll('input')[1]!.element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('#remember-account').element as HTMLInputElement).checked).toBe(true)
    expect(wrapper.get('button[aria-label="管理员登录"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.find('.login-back-button').exists()).toBe(false)
    expect(wrapper.find('.email-login-entry').exists()).toBe(true)
  })

  it('returns to username login when the email page is opened directly', async () => {
    const wrapper = await mountLogin('/login/email?redirect=/cart')
    expect(wrapper.get('input').attributes('type')).toBe('email')
    await wrapper.get('.login-back-button').trigger('click')
    await flushPromises()

    expect(wrapper.vm.$router.currentRoute.value.path).toBe('/login')
    expect(wrapper.vm.$router.currentRoute.value.query.redirect).toBe('/cart')
    expect(wrapper.get('input').attributes('type')).toBe('text')
    expect(wrapper.find('.email-login-entry').exists()).toBe(true)
  })

  it('opens recovery and returns to email login with an empty password after success', async () => {
    const wrapper = await mountLogin()
    useUserStore().login({ token: 'previous-token', user: { username: 'owner', role: 'user' } })
    await wrapper.findAll('input')[1]!.setValue('previous-password')
    await wrapper.get('.forgot-password-link').trigger('click')
    const resetForm = wrapper.findComponent(PasswordResetForm)
    expect(resetForm.exists()).toBe(true)
    resetForm.vm.$emit('success', 'owner@example.com')
    await flushPromises()
    expect(wrapper.find('.reset-form-stub').exists()).toBe(false)
    expect(wrapper.vm.$router.currentRoute.value.name).toBe('email_login')
    const inputs = wrapper.findAll('input')
    expect((inputs[0]!.element as HTMLInputElement).value).toBe('owner@example.com')
    expect((inputs[1]!.element as HTMLInputElement).value).toBe('')
    expect(useUserStore().logged).toBe(false)
    expect(localStorage.getItem('user')).toBeNull()
  })

  it('shows on entry and hides only after 1.5 seconds', async () => {
    const wrapper = await mountLogin()
    const group = wrapper.get('.login-role-switch')

    expect(group.classes()).not.toContain('is-hidden')
    await advanceTime(1499)
    expect(group.classes()).not.toContain('is-hidden')
    await advanceTime(1)
    expect(group.classes()).toContain('is-hidden')
  })

  it('stays visible while hovered and hides after leaving even when a button has focus', async () => {
    const wrapper = await mountLogin()
    const group = wrapper.get('.login-role-switch')
    const admin = group.get('button[aria-label="管理员登录"]')

    await advanceTime(1000)
    await group.trigger('mouseenter')
    await advanceTime(2000)
    expect(group.classes()).not.toContain('is-hidden')

    ;(admin.element as HTMLButtonElement).focus()
    await admin.trigger('click')
    expect(admin.attributes('aria-pressed')).toBe('true')
    expect(document.activeElement).toBe(admin.element)

    await group.trigger('mouseleave')
    await advanceTime(1499)
    expect(group.classes()).not.toContain('is-hidden')
    await advanceTime(1)
    expect(group.classes()).toContain('is-hidden')
    expect(document.activeElement).toBe(admin.element)
  })

  it('reveals the hidden switch and cancels an old timeout when the mouse returns', async () => {
    const wrapper = await mountLogin()
    const group = wrapper.get('.login-role-switch')

    await advanceTime(1500)
    expect(group.classes()).toContain('is-hidden')
    await group.trigger('mouseenter')
    expect(group.classes()).not.toContain('is-hidden')

    await group.trigger('mouseleave')
    await advanceTime(1000)
    await group.trigger('mouseenter')
    await advanceTime(2000)
    expect(group.classes()).not.toContain('is-hidden')

    await group.trigger('mouseleave')
    await advanceTime(1500)
    expect(group.classes()).toContain('is-hidden')
  })

  it('reveals on keyboard focus and starts hiding when focus moves outside the switch', async () => {
    const wrapper = await mountLogin()
    const group = wrapper.get('.login-role-switch')
    const user = group.get('button[aria-label="用户登录"]')
    const admin = group.get('button[aria-label="管理员登录"]')

    await advanceTime(1500)
    ;(user.element as HTMLButtonElement).focus()
    await nextTick()
    expect(group.classes()).not.toContain('is-hidden')

    ;(admin.element as HTMLButtonElement).focus()
    await advanceTime(2000)
    expect(group.classes()).not.toContain('is-hidden')

    ;(wrapper.get('input').element as HTMLInputElement).focus()
    await advanceTime(1499)
    expect(group.classes()).not.toContain('is-hidden')
    await advanceTime(1)
    expect(group.classes()).toContain('is-hidden')
  })

  it.each([
    { mode: 'user', role: 'user', destination: 'index' },
    { mode: 'admin', role: 'admin', destination: 'admin_dashboard' },
    { mode: 'admin', role: 'super_admin', destination: 'admin_dashboard' },
  ] as const)(
    'preserves the $role identity returned by $mode login',
    async ({ mode, role, destination }) => {
      const wrapper = await mountLogin()
      const user = { id: 10, username: '千羽', role }
      vi.mocked(request.post).mockResolvedValue({
        data: { status: true, payload: { user, token: 'test-token' } },
      })

      if (mode === 'admin') {
        await wrapper.get('button[aria-label="管理员登录"]').trigger('click')
      }
      const inputs = wrapper.findAll('input')
      await inputs[0]!.setValue('千羽')
      await inputs[1]!.setValue('123456')
      await wrapper.get('form').trigger('submit')
      await flushPromises()

      expect(request.post).toHaveBeenCalledWith('/login', {
        username: '千羽',
        password: '123456',
        role: mode,
      })
      const store = useUserStore()
      expect(store.role).toBe(role)
      expect(store.isAdmin).toBe(role !== 'user')
      expect(JSON.parse(localStorage.getItem('user')!).user.role).toBe(role)
      expect(localStorage.getItem('remembered-account')).toBeNull()
      expect(wrapper.vm.$router.currentRoute.value.name).toBe(destination)
    },
  )

  it.each([
    { mode: 'user', role: 'user' },
    { mode: 'admin', role: 'super_admin' },
  ] as const)('remembers the account and $mode entry for seven days', async ({ mode, role }) => {
    const wrapper = await mountLogin()
    const savedAt = Date.now()
    vi.mocked(request.post).mockResolvedValue({
      data: {
        status: true,
        payload: { user: { id: 10, username: '千羽', role }, token: 'test-token' },
      },
    })

    if (mode === 'admin') {
      await wrapper.get('button[aria-label="管理员登录"]').trigger('click')
    }
    const inputs = wrapper.findAll('input')
    await inputs[0]!.setValue('千羽')
    await inputs[1]!.setValue('123456')
    await wrapper.get('#remember-account').setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const saved = {
      username: '千羽',
      role: mode,
      expiresAt: savedAt + 7 * 24 * 60 * 60 * 1000,
    }
    expect(JSON.parse(localStorage.getItem('remembered-account')!)).toEqual(saved)

    useUserStore().logout()
    const reloaded = await mountLogin()
    const reloadedInputs = reloaded.findAll('input')
    expect((reloadedInputs[0]!.element as HTMLInputElement).value).toBe('千羽')
    expect((reloadedInputs[1]!.element as HTMLInputElement).value).toBe('')
    expect((reloaded.get('#remember-account').element as HTMLInputElement).checked).toBe(true)
    expect(
      reloaded
        .get(`button[aria-label="${mode === 'admin' ? '管理员' : '用户'}登录"]`)
        .attributes('aria-pressed'),
    ).toBe('true')
    expect(JSON.parse(localStorage.getItem('remembered-account')!)).toEqual(saved)
  })

  it('restores before the expiry boundary and clears the account exactly seven days later', async () => {
    const savedAt = Date.now()
    saveRememberedAccount('千羽', 'admin')
    const expiresAt = savedAt + 7 * 24 * 60 * 60 * 1000

    vi.setSystemTime(expiresAt - 1)
    const valid = await mountLogin()
    expect((valid.findAll('input')[0]!.element as HTMLInputElement).value).toBe('千羽')
    expect(JSON.parse(localStorage.getItem('remembered-account')!).expiresAt).toBe(expiresAt)

    vi.setSystemTime(expiresAt)
    const expired = await mountLogin()
    expect((expired.findAll('input')[0]!.element as HTMLInputElement).value).toBe('')
    expect((expired.get('#remember-account').element as HTMLInputElement).checked).toBe(false)
    expect(expired.get('button[aria-label="用户登录"]').attributes('aria-pressed')).toBe('true')
    expect(localStorage.getItem('remembered-account')).toBeNull()
  })

  it('clears a remembered account as soon as the checkbox is unchecked', async () => {
    saveRememberedAccount('千羽', 'admin')
    const wrapper = await mountLogin()

    await wrapper.get('#remember-account').setValue(false)

    expect(localStorage.getItem('remembered-account')).toBeNull()
    const reloaded = await mountLogin()
    expect((reloaded.findAll('input')[0]!.element as HTMLInputElement).value).toBe('')
    expect((reloaded.get('#remember-account').element as HTMLInputElement).checked).toBe(false)
  })

  it('does not remember an account after a failed login', async () => {
    const wrapper = await mountLogin()
    vi.mocked(request.post).mockResolvedValue({
      data: { status: false, payload: { message: '用户名或密码错误' } },
    })
    const inputs = wrapper.findAll('input')
    await inputs[0]!.setValue('千羽')
    await inputs[1]!.setValue('wrong-password')
    await wrapper.get('#remember-account').setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(localStorage.getItem('remembered-account')).toBeNull()
    expect(useUserStore().logged).toBe(false)
  })

  it.each(['response', 'network'])(
    'preserves the remembered account after a %s login failure',
    async (failure) => {
      saveRememberedAccount('千羽', 'admin')
      const saved = localStorage.getItem('remembered-account')
      const wrapper = await mountLogin()
      vi.setSystemTime(Date.now() + 1000)

      if (failure === 'network') {
        vi.mocked(request.post).mockRejectedValue(new Error('Network error'))
      } else {
        vi.mocked(request.post).mockResolvedValue({
          data: { status: false, payload: { message: '用户名或密码错误' } },
        })
      }
      const inputs = wrapper.findAll('input')
      await inputs[0]!.setValue('其他账号')
      await inputs[1]!.setValue('wrong-password')
      await wrapper.get('form').trigger('submit')
      await flushPromises()

      expect(localStorage.getItem('remembered-account')).toBe(saved)
      expect(useUserStore().logged).toBe(false)
    },
  )

  it('completes login when saving the remembered account exceeds the storage quota', async () => {
    const wrapper = await mountLogin()
    vi.mocked(request.post).mockResolvedValue({
      data: {
        status: true,
        payload: { user: { id: 10, username: '千羽', role: 'user' }, token: 'test-token' },
      },
    })
    const inputs = wrapper.findAll('input')
    await inputs[0]!.setValue('千羽')
    await inputs[1]!.setValue('123456')
    await wrapper.get('#remember-account').setValue(true)

    const setItem = Storage.prototype.setItem
    vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(function (this: Storage, key, value) {
      if (key === 'remembered-account') {
        throw new DOMException('Storage is full', 'QuotaExceededError')
      }
      setItem.call(this, key, value)
    })
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(useUserStore().logged).toBe(true)
    expect(wrapper.vm.$router.currentRoute.value.name).toBe('index')
    expect(localStorage.getItem('remembered-account')).toBeNull()
  })

  it('clears a malformed remembered account and leaves the form usable', async () => {
    localStorage.setItem('remembered-account', '{broken')
    const wrapper = await mountLogin()

    expect((wrapper.findAll('input')[0]!.element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('#remember-account').element as HTMLInputElement).checked).toBe(false)
    expect(localStorage.getItem('remembered-account')).toBeNull()
  })

  it('prefills a newly registered account in user mode ahead of the remembered admin account', async () => {
    saveRememberedAccount('千羽', 'admin')
    const wrapper = await mountLogin('/login?username=新用户')

    expect((wrapper.findAll('input')[0]!.element as HTMLInputElement).value).toBe('新用户')
    expect(wrapper.get('button[aria-label="用户登录"]').attributes('aria-pressed')).toBe('true')
  })
})
