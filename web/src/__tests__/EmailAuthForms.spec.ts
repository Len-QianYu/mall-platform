import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import request from '@/utils/request'
import PasswordResetForm from '@/components/auth/PasswordResetForm.vue'
import EmailBindingDialog from '@/components/auth/EmailBindingDialog.vue'

vi.mock('@/utils/request', () => ({ default: { post: vi.fn<typeof request.post>() } }))
enableAutoUnmount(afterEach)

const sendResponse = { data: { status: true, payload: { resendAfterSeconds: 60 } } }

describe('email authentication forms', () => {
  beforeEach(() => vi.mocked(request.post).mockReset())

  it('sends a recovery code once, rejects mismatched passwords, and clears the new password after resetting', async () => {
    vi.mocked(request.post).mockResolvedValue(sendResponse)
    const wrapper = mount(PasswordResetForm, { props: { initialEmail: 'Owner@Example.com' } })
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '发送验证码')!
      .trigger('click')
    await flushPromises()
    expect(request.post).toHaveBeenCalledWith('/account/password-reset/code', {
      email: 'owner@example.com',
    })
    expect(wrapper.text()).toContain('60 秒后重发')
    const inputs = wrapper.findAll('input')
    await inputs[1]!.setValue('123456')
    await inputs[2]!.setValue('new-password')
    await inputs[3]!.setValue('different-password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(request.post).toHaveBeenCalledTimes(1)
    await inputs[3]!.setValue('new-password')
    vi.mocked(request.post).mockResolvedValue({ data: { status: true, payload: {} } })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(request.post).toHaveBeenLastCalledWith('/account/password-reset', {
      email: 'owner@example.com',
      code: '123456',
      newPassword: 'new-password',
    })
    expect(wrapper.emitted('success')).toEqual([['owner@example.com']])
    expect((inputs[1]!.element as HTMLInputElement).value).toBe('')
    expect((inputs[2]!.element as HTMLInputElement).value).toBe('')
    expect((inputs[3]!.element as HTMLInputElement).value).toBe('')
  })

  it('requires the current password and returns the refreshed login after binding', async () => {
    const wrapper = mount(EmailBindingDialog, {
      props: { modelValue: true, initialEmail: 'Owner@Example.com' },
      global: {
        stubs: {
          ElDialog: {
            props: ['modelValue'],
            template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
          },
        },
      },
    })
    const inputs = wrapper.findAll('input')
    await inputs[1]!.setValue('current-password')
    vi.mocked(request.post).mockResolvedValue(sendResponse)
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '发送验证码')!
      .trigger('click')
    await flushPromises()
    expect(request.post).toHaveBeenCalledWith('/account/email/code', {
      email: 'owner@example.com',
      password: 'current-password',
    })
    const account = {
      token: 'fresh-token',
      user: { username: 'owner', role: 'user', email: 'owner@example.com', emailVerified: true },
    }
    vi.mocked(request.post).mockResolvedValue({ data: { status: true, payload: account } })
    await inputs[2]!.setValue('123456')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '绑定邮箱')!
      .trigger('click')
    await flushPromises()
    expect(request.post).toHaveBeenLastCalledWith('/account/email/bind', {
      email: 'owner@example.com',
      password: 'current-password',
      code: '123456',
    })
    expect(wrapper.emitted('bound')).toEqual([[account]])
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    expect((inputs[1]!.element as HTMLInputElement).value).toBe('')
  })

  it('invalidates a typed code when the recovery email changes', async () => {
    const wrapper = mount(PasswordResetForm, { props: { initialEmail: 'owner@example.com' } })
    const inputs = wrapper.findAll('input')
    await inputs[1]!.setValue('123456')
    await inputs[0]!.setValue('another@example.com')
    expect((inputs[1]!.element as HTMLInputElement).value).toBe('')
  })
})
