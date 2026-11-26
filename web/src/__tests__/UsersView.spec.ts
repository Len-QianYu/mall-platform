import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { ElFormItem, ElInput, ElSelect } from 'element-plus'
import type { ApiResult, Pagination, User } from '@/types/api'
import { useUserStore } from '@/stores/user'
import UsersView from '@/views/admin/UsersView.vue'

const { getUsers, postUser } = vi.hoisted(() => ({
  getUsers:
    vi.fn<
      (url: string) => Promise<{ data: ApiResult<{ users: User[]; pagination: Pagination }> }>
    >(),
  postUser: vi.fn<(url: string, fields: Record<string, unknown>) => Promise<{ data: ApiResult }>>(),
}))
vi.mock('@/utils/request', () => ({ default: { get: getUsers, post: postUser } }))
enableAutoUnmount(afterEach)

const account = (id: number, role: User['role'], status = 1): User => ({
  id,
  username: `account${id}`,
  name: null,
  gender: null,
  email: null,
  phone: null,
  province: null,
  city: null,
  receiverAddress: null,
  avatarUrl: null,
  role,
  status,
  registeredAt: '2026-11-25T09:00:00',
  updatedAt: '2026-11-25T09:00:00',
})

const mountUsers = async () => {
  const pinia = createPinia()
  useUserStore(pinia).login({ token: 'admin-test-token', user: account(1, 'admin') })
  const wrapper = mount(UsersView, {
    global: {
      plugins: [pinia],
      stubs: {
        ElPopconfirm: { name: 'ElPopconfirm', template: '<div><slot name="reference" /></div>' },
        ElDrawer: {
          props: ['modelValue'],
          template:
            '<aside v-if="modelValue" class="drawer-stub"><slot /><slot name="footer" /></aside>',
        },
      },
    },
  })
  await flushPromises()
  return wrapper
}

const tableRow = (wrapper: Awaited<ReturnType<typeof mountUsers>>, id: number) =>
  wrapper.findAll('.el-table__body tbody tr').find((row) => row.find('td').text() === String(id))!
const roleSelect = (wrapper: Awaited<ReturnType<typeof mountUsers>>) =>
  wrapper.findAllComponents(ElSelect).find((select) => select.element.closest('.drawer-stub'))!

describe('user management account protection', () => {
  beforeEach(() => {
    localStorage.removeItem('user')
    getUsers.mockReset()
    postUser.mockReset()
    getUsers.mockResolvedValue({
      data: {
        status: true,
        payload: {
          users: [
            account(1, 'admin'),
            account(2, 'super_admin'),
            account(3, 'user'),
            account(4, 'super_admin', 0),
          ],
          pagination: { currentPage: 1, pageSize: 10, total: 4, pageCount: 1 },
        },
      },
    })
    postUser.mockResolvedValue({ data: { status: true, payload: {} } })
  })

  afterEach(() => localStorage.removeItem('user'))

  it('disables the super admin and current account buttons and ignores forced confirmations', async () => {
    const wrapper = await mountUsers()
    for (const id of [1, 2]) {
      const row = tableRow(wrapper, id)
      expect(row.findAll('button')[1]!.attributes('disabled')).toBeDefined()
      row.findComponent({ name: 'ElPopconfirm' }).vm.$emit('confirm')
    }
    await flushPromises()
    expect(postUser).not.toHaveBeenCalled()

    const ordinary = tableRow(wrapper, 3)
    expect(ordinary.findAll('button')[1]!.attributes('disabled')).toBeUndefined()
    ordinary.findComponent({ name: 'ElPopconfirm' }).vm.$emit('confirm')
    await flushPromises()
    expect(postUser).toHaveBeenCalledWith('/admin/user/status', { id: 3, status: 0 })
    expect(tableRow(wrapper, 3).text()).toContain('禁用')
  })

  it('allows a previously disabled super admin to be enabled', async () => {
    const wrapper = await mountUsers()
    const row = tableRow(wrapper, 4)
    expect(row.findAll('button')[1]!.attributes('disabled')).toBeUndefined()
    row.findComponent({ name: 'ElPopconfirm' }).vm.$emit('confirm')
    await flushPromises()

    expect(postUser).toHaveBeenCalledWith('/admin/user/status', { id: 4, status: 1 })
  })

  it('locks a super admins role while still allowing profile edits', async () => {
    const wrapper = await mountUsers()
    await tableRow(wrapper, 2).findAll('button')[0]!.trigger('click')
    await flushPromises()
    expect(roleSelect(wrapper).props('disabled')).toBe(true)

    const nameField = wrapper
      .findAllComponents(ElFormItem)
      .find((item) => item.props('label') === '名字')!
    await nameField.findComponent(ElInput).get('input').setValue('修改后的名字')
    await wrapper
      .get('.drawer-stub')
      .findAll('button')
      .find((button) => button.text() === '保存')!
      .trigger('click')
    await flushPromises()
    expect(postUser).toHaveBeenCalledWith('/admin/user/update', {
      id: 2,
      name: '修改后的名字',
      gender: null,
      phone: null,
      email: null,
      role: 'super_admin',
    })

    await tableRow(wrapper, 3).findAll('button')[0]!.trigger('click')
    await flushPromises()
    expect(roleSelect(wrapper).props('disabled')).toBe(false)
  })
})
