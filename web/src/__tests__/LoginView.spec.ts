import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import LoginView from '@/views/auth/LoginView.vue'

vi.mock('@/utils/request', () => ({ default: { post: vi.fn() } }))
vi.mock('/favicon.ico', () => ({ default: '/favicon.ico' }))

const wrappers: VueWrapper[] = []

const mountLogin = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: LoginView },
      { path: '/register', name: 'register', component: { template: '<p>register</p>' } },
    ],
  })
  await router.push('/login')
  const wrapper = mount(LoginView, {
    attachTo: document.body,
    global: {
      plugins: [createPinia(), router],
      stubs: {
        ElForm: { template: '<form><slot /></form>' },
        ElFormItem: { template: '<div><slot /></div>' },
        ElInput: { template: '<input />' },
        ElButton: { template: '<button><slot /></button>' },
        ModerationIcon: true,
      },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}

const advanceTime = async (milliseconds: number) => {
  vi.advanceTimersByTime(milliseconds)
  await nextTick()
}

describe('login role switch visibility', () => {
  beforeEach(() => {
    localStorage.removeItem('user')
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  })

  afterEach(() => {
    for (const wrapper of wrappers.splice(0)) wrapper.unmount()
    document.body.replaceChildren()
    vi.useRealTimers()
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
})
