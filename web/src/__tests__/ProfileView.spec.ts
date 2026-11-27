import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ElAvatar, ElOption, ElSelect } from 'element-plus'
import type { ApiResult, User } from '@/types/api'
import { useUserStore, type LoginResult } from '@/stores/user'
import ProfileView from '@/views/shop/ProfileView.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import EmailBindingDialog from '@/components/auth/EmailBindingDialog.vue'

type ProfileResponse = {
  data: ApiResult<{ url?: string } | { resendAfterSeconds: number } | LoginResult>
}

const { getProfile, postProfile } = vi.hoisted(() => ({
  getProfile: vi.fn<(url: string) => Promise<{ data: ApiResult<{ user: User }> }>>(),
  postProfile:
    vi.fn<(url: string, fields: Record<string, unknown> | FormData) => Promise<ProfileResponse>>(),
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
  emailVerified: false,
  phone: '13800138000',
  province: '北京市',
  city: '海淀区',
  receiverAddress: '北京市海淀区中关村大街1号',
  avatarUrl: null,
  status: 1,
  registeredAt: '2026-11-17T09:00:00',
  updatedAt: '2026-11-17T09:00:00',
}

const mountProfile = async (admin = true, query: Record<string, string> = {}) => {
  const pinia = createPinia()
  const store = useUserStore(pinia)
  store.login({ token: 'profile-test-token', user: { ...user, role: admin ? 'admin' : 'user' } })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin', name: 'admin_dashboard', component: { template: '<p>dashboard</p>' } },
      { path: '/admin/profile', name: 'admin_profile', component: ProfileView },
      { path: '/profile', name: 'profile', component: ProfileView },
      { path: '/checkout', name: 'checkout', component: { template: '<p>checkout</p>' } },
      { path: '/login', name: 'login', component: { template: '<p>login</p>' } },
    ],
  })
  await router.push({ name: admin ? 'admin_profile' : 'profile', query })
  const wrapper = mount(ProfileView, {
    props: { admin },
    global: {
      plugins: [pinia, router],
      stubs: {
        ElCard: { template: '<div><slot /></div>' },
        ElDialog: {
          props: ['modelValue'],
          template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>',
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, store, router }
}

const chooseAvatar = async (wrapper: VueWrapper) => {
  const file = new File([new Uint8Array(32)], 'avatar.png', { type: 'image/png' })
  const input = wrapper.get<HTMLInputElement>('input[type="file"]')
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
  await input.trigger('change')
  await flushPromises()
  return file
}

describe('personal information page', () => {
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

  it.each([true, false])(
    'updates the avatar after upload without saving invalid profile edits or returning to checkout (admin: %s)',
    async (admin) => {
      const { wrapper, store, router } = await mountProfile(admin, {
        redirect: '/checkout?productId=11&quantity=2',
      })
      const inputs = wrapper.get('.profile-form').findAll('input.el-input__inner')
      await inputs[1]!.setValue('未保存的名字')
      await inputs[2]!.setValue('invalid-phone')

      const input = wrapper.get<HTMLInputElement>('input[type="file"]')
      const openFilePicker = vi.spyOn(input.element, 'click')
      await wrapper.get('.avatar-trigger').trigger('click')
      expect(openFilePicker).toHaveBeenCalledOnce()

      let complete: (result: ProfileResponse) => void = () => undefined
      postProfile.mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            complete = resolve
          }),
      )
      const file = await chooseAvatar(wrapper)

      expect(postProfile).toHaveBeenCalledOnce()
      expect(postProfile).toHaveBeenCalledWith('/upload/avatars', expect.any(FormData))
      expect((postProfile.mock.calls[0]![1] as FormData).get('file')).toBe(file)
      expect(store.user?.user.avatarUrl).toBeNull()
      for (const form of wrapper.findAll('.profile-form')) {
        expect((form.get('button').element as HTMLButtonElement).disabled).toBe(true)
      }
      await wrapper.get('.profile-form .save-profile-button').trigger('click')
      expect(postProfile).toHaveBeenCalledOnce()

      complete({ data: { status: true, payload: { url: '/avatars/new.png' } } })
      await flushPromises()

      expect(store.user?.user.avatarUrl).toBe('/avatars/new.png')
      expect(wrapper.getComponent(ElAvatar).props('src')).toContain('/avatars/new.png')
      expect(JSON.parse(localStorage.getItem('user')!).user.avatarUrl).toBe('/avatars/new.png')
      expect(store.user?.user.name).toBe(user.name)
      expect(store.user?.user.email).toBe(user.email)
      expect(store.user?.user.phone).toBe(user.phone)
      expect((inputs[1]!.element as HTMLInputElement).value).toBe('未保存的名字')
      expect((inputs[2]!.element as HTMLInputElement).value).toBe('invalid-phone')
      expect(store.user?.token).toBe('profile-test-token')
      expect(store.user?.user.role).toBe(admin ? 'admin' : 'user')
      expect(router.currentRoute.value.name).toBe(admin ? 'admin_profile' : 'profile')
      expect(wrapper.getComponent(ImageUploader).props('uploading')).toBe(false)
      expect(postProfile).toHaveBeenCalledOnce()
    },
  )

  it('keeps the previous avatar after a failed save and allows retrying the upload', async () => {
    getProfile.mockResolvedValueOnce({
      data: { status: true, payload: { user: { ...user, avatarUrl: '/avatars/previous.png' } } },
    })
    const { wrapper, store } = await mountProfile()
    store.updateUser({ avatarUrl: '/avatars/previous.png' })
    postProfile.mockResolvedValueOnce({
      data: { status: false, payload: { message: '用户不存在，头像保存失败' } },
    })

    await chooseAvatar(wrapper)

    expect(store.user?.user.avatarUrl).toBe('/avatars/previous.png')
    expect(wrapper.getComponent(ElAvatar).props('src')).toContain('/avatars/previous.png')
    expect(JSON.parse(localStorage.getItem('user')!).user.avatarUrl).toBe('/avatars/previous.png')
    expect(wrapper.getComponent(ImageUploader).props('uploading')).toBe(false)
    expect(
      (wrapper.get('.profile-form .save-profile-button').element as HTMLButtonElement).disabled,
    ).toBe(false)

    postProfile.mockResolvedValueOnce({
      data: { status: true, payload: { url: '/avatars/retry.png' } },
    })
    await chooseAvatar(wrapper)

    expect(store.user?.user.avatarUrl).toBe('/avatars/retry.png')
    expect(wrapper.getComponent(ElAvatar).props('src')).toContain('/avatars/retry.png')
  })

  it('saves editable profile fields without reverting the automatically saved avatar', async () => {
    const { wrapper, store } = await mountProfile()
    const form = wrapper.get('.profile-form')
    const inputs = form.findAll('input.el-input__inner')
    await inputs[1]!.setValue(' 新名字 ')
    wrapper
      .findComponent({ name: 'ImageUploader' })
      .vm.$emit('update:modelValue', '/avatars/new.png')
    await flushPromises()

    expect(postProfile).not.toHaveBeenCalled()
    expect(store.user?.user.avatarUrl).toBe('/avatars/new.png')
    expect(store.user?.user.name).toBe(user.name)

    let complete: (result: ProfileResponse) => void = () => undefined
    postProfile.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          complete = resolve
        }),
    )
    await form.get('.save-profile-button').trigger('click')
    await flushPromises()

    expect(postProfile).toHaveBeenCalledWith('/user/update', {
      name: '新名字',
      gender: 'F',
      email: 'admin@example.com',
      phone: '13800138000',
      province: '北京市',
      city: '海淀区',
      receiverAddress: '北京市海淀区中关村大街1号',
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
    expect((wrapper.get('.profile-form textarea').element as HTMLTextAreaElement).disabled).toBe(
      true,
    )
    await wrapper.get('.profile-form .save-profile-button').trigger('click')
    await flushPromises()

    expect(postProfile).not.toHaveBeenCalled()
  })

  it.each([true, false])(
    'shows the verified email returned by the profile API (admin: %s)',
    async (admin) => {
      getProfile.mockResolvedValueOnce({
        data: {
          status: true,
          payload: { user: { ...user, email: 'bound@example.com', emailVerified: true } },
        },
      })
      const { wrapper } = await mountProfile(admin)
      const emailField = wrapper.get('.profile-email-field')
      const emailInput = emailField.get<HTMLInputElement>('input')

      expect(emailInput.element.value).toBe('bound@example.com')
      expect(emailInput.element.readOnly).toBe(true)
      expect(emailField.find('button').exists()).toBe(false)
    },
  )

  it.each([true, false])(
    'opens the email dialog and updates the account after verification (admin: %s)',
    async (admin) => {
      const { wrapper, store } = await mountProfile(admin)
      const emailField = wrapper.get('.profile-email-field')
      expect(emailField.findAll('button')).toHaveLength(1)
      expect(emailField.get('button').text()).toBe('绑定邮箱')
      expect(emailField.find('input').exists()).toBe(false)
      expect(emailField.find('.el-tag').exists()).toBe(false)
      await wrapper.get('.profile-email-field button').trigger('click')
      const dialog = wrapper.findComponent(EmailBindingDialog)
      expect(dialog.props('modelValue')).toBe(true)
      expect(dialog.props('initialEmail')).toBe(user.email)
      const inputs = dialog.findAll('input')
      await inputs[0]!.setValue('BOUND@EXAMPLE.COM')
      await inputs[1]!.setValue('current-password')
      postProfile.mockResolvedValueOnce({
        data: { status: true, payload: { resendAfterSeconds: 60 } },
      })
      await dialog
        .findAll('button')
        .find((button) => button.text() === '发送验证码')!
        .trigger('click')
      await flushPromises()

      expect(postProfile).toHaveBeenCalledWith('/account/email/code', {
        email: 'bound@example.com',
        password: 'current-password',
      })
      expect(store.user?.token).toBe('profile-test-token')
      expect(dialog.props('modelValue')).toBe(true)
      expect(emailField.find('input').exists()).toBe(false)
      expect(emailField.get('button').text()).toBe('绑定邮箱')
      const account = {
        token: 'refreshed-profile-token',
        user: {
          ...user,
          role: admin ? ('admin' as const) : ('user' as const),
          email: 'bound@example.com',
          emailVerified: true,
        },
      }
      postProfile.mockResolvedValueOnce({ data: { status: true, payload: account } })
      await inputs[2]!.setValue('123456')
      await dialog.get('button.el-button--primary').trigger('click')
      await flushPromises()

      expect(postProfile).toHaveBeenLastCalledWith('/account/email/bind', {
        email: 'bound@example.com',
        password: 'current-password',
        code: '123456',
      })
      expect(store.user).toEqual(account)
      expect(JSON.parse(localStorage.getItem('user')!)).toEqual(account)
      const emailInput = emailField.get<HTMLInputElement>('input')
      expect(emailInput.element.value).toBe('bound@example.com')
      expect(emailInput.element.readOnly).toBe(true)
      expect(emailField.find('.el-tag').exists()).toBe(false)
      expect(emailField.find('button').exists()).toBe(false)
      expect(dialog.props('initialEmail')).toBe('bound@example.com')
      expect(dialog.props('modelValue')).toBe(false)
    },
  )

  it.each([true, false])(
    'restores searchable province and city selections (admin: %s)',
    async (admin) => {
      const { wrapper } = await mountProfile(admin)
      const [province, city] = wrapper.findAllComponents(ElSelect)

      expect(province!.props('modelValue')).toBe('北京市')
      expect(city!.props('modelValue')).toBe('海淀区')
      expect(province!.props('filterable')).toBe(true)
      expect(city!.props('filterable')).toBe(true)
      const cities = city!.findAllComponents(ElOption).map((option) => option.props('value'))
      expect(cities).toContain('海淀区')
      expect(cities).not.toContain('杭州市')
    },
  )

  it('disables city selection until a province is selected', async () => {
    getProfile.mockResolvedValueOnce({
      data: { status: true, payload: { user: { ...user, province: null, city: null } } },
    })
    const { wrapper } = await mountProfile()
    const [province, city] = wrapper.findAllComponents(ElSelect)

    expect(province!.props('modelValue')).toBe('')
    expect(city!.props('modelValue')).toBe('')
    expect(city!.props('disabled')).toBe(true)
    expect(city!.props('placeholder')).toBe('请先选择省份')
  })

  it('resets the old city when switching provinces and persists the new location on save', async () => {
    const { wrapper, store } = await mountProfile()
    const [province, city] = wrapper.findAllComponents(ElSelect)
    province!.vm.$emit('update:modelValue', '浙江省')
    province!.vm.$emit('change', '浙江省')
    await flushPromises()

    expect(city!.props('modelValue')).toBe('')
    const cities = city!.findAllComponents(ElOption).map((option) => option.props('value'))
    expect(cities).toContain('杭州市')
    expect(cities).not.toContain('海淀区')
    expect(store.user?.user.city).toBe('海淀区')

    city!.vm.$emit('update:modelValue', '杭州市')
    await flushPromises()
    await wrapper.get('.profile-form .save-profile-button').trigger('click')
    await flushPromises()

    expect(postProfile).toHaveBeenCalledWith(
      '/user/update',
      expect.objectContaining({ province: '浙江省', city: '杭州市' }),
    )
    expect(store.user?.user.province).toBe('浙江省')
    expect(store.user?.user.city).toBe('杭州市')
    expect(JSON.parse(localStorage.getItem('user')!).user).toMatchObject({
      province: '浙江省',
      city: '杭州市',
    })
  })

  it('clears both location fields when the province is cleared', async () => {
    const { wrapper, store } = await mountProfile()
    const [province, city] = wrapper.findAllComponents(ElSelect)
    province!.vm.$emit('update:modelValue', '')
    province!.vm.$emit('change', '')
    await flushPromises()

    expect(city!.props('modelValue')).toBe('')
    expect(city!.props('disabled')).toBe(true)
    expect(city!.findAllComponents(ElOption)).toHaveLength(0)
    await wrapper.get('.profile-form .save-profile-button').trigger('click')
    await flushPromises()

    expect(postProfile).toHaveBeenCalledWith(
      '/user/update',
      expect.objectContaining({ province: null, city: null }),
    )
    expect(store.user?.user.province).toBeNull()
    expect(store.user?.user.city).toBeNull()
  })

  it('preserves the saved location if saving a new selection fails', async () => {
    const { wrapper, store } = await mountProfile()
    const [province, city] = wrapper.findAllComponents(ElSelect)
    province!.vm.$emit('update:modelValue', '浙江省')
    province!.vm.$emit('change', '浙江省')
    await flushPromises()
    city!.vm.$emit('update:modelValue', '杭州市')
    await flushPromises()
    postProfile.mockResolvedValueOnce({ data: { status: false, payload: { message: '保存失败' } } })

    await wrapper.get('.profile-form .save-profile-button').trigger('click')
    await flushPromises()

    expect(store.user?.user.province).toBe('北京市')
    expect(store.user?.user.city).toBe('海淀区')
    expect(province!.props('modelValue')).toBe('浙江省')
    expect(city!.props('modelValue')).toBe('杭州市')
  })

  it('restores the saved shipping address and persists edits with the personal information', async () => {
    const { wrapper, store } = await mountProfile(false)
    const address = wrapper.get('.profile-form textarea')
    expect((address.element as HTMLTextAreaElement).value).toBe(user.receiverAddress)

    await address.setValue(' 浙江省杭州市西湖区文三路1号 ')
    await wrapper.get('.profile-form .save-profile-button').trigger('click')
    await flushPromises()

    expect(postProfile).toHaveBeenCalledWith(
      '/user/update',
      expect.objectContaining({ receiverAddress: '浙江省杭州市西湖区文三路1号' }),
    )
    expect(store.user?.user.receiverAddress).toBe('浙江省杭州市西湖区文三路1号')
    expect(JSON.parse(localStorage.getItem('user')!).user.receiverAddress).toBe(
      '浙江省杭州市西湖区文三路1号',
    )
  })

  it('allows clearing the saved shipping address', async () => {
    const { wrapper, store } = await mountProfile(false)
    await wrapper.get('.profile-form textarea').setValue('   ')
    await wrapper.get('.profile-form .save-profile-button').trigger('click')
    await flushPromises()

    expect(postProfile).toHaveBeenCalledWith(
      '/user/update',
      expect.objectContaining({ receiverAddress: null }),
    )
    expect(store.user?.user.receiverAddress).toBeNull()
  })

  it('returns to the original checkout only after successfully saving the new address', async () => {
    const { wrapper, store, router } = await mountProfile(false, {
      redirect: '/checkout?productId=11&quantity=2',
    })
    const save = wrapper.get('.profile-form .save-profile-button')
    expect(save.text()).toBe('保存并返回下单')
    await wrapper.get('.profile-form textarea').setValue('浙江省杭州市西湖区文三路1号')
    postProfile.mockResolvedValueOnce({ data: { status: false, payload: { message: '保存失败' } } })

    await save.trigger('click')
    await flushPromises()
    expect(store.user?.user.receiverAddress).toBe(user.receiverAddress)
    expect(router.currentRoute.value.name).toBe('profile')

    await save.trigger('click')
    await flushPromises()
    expect(store.user?.user.receiverAddress).toBe('浙江省杭州市西湖区文三路1号')
    expect(router.currentRoute.value.name).toBe('checkout')
    expect(router.currentRoute.value.query).toEqual({ productId: '11', quantity: '2' })
  })
})
