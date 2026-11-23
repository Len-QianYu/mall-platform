import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { AxiosError, AxiosHeaders } from 'axios'
import { ElUpload } from 'element-plus'
import type { ApiResult } from '@/types/api'
import ImageUploader from '@/components/ImageUploader.vue'

const { post, errorMessage } = vi.hoisted(() => ({
  post: vi.fn<(url: string, body: FormData) => Promise<{ data: ApiResult<{ url: string }> }>>(),
  errorMessage: vi.fn<(message: string) => void>(),
}))
vi.mock('@/utils/request', () => ({ default: { post } }))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessage: { error: errorMessage },
}))
enableAutoUnmount(afterEach)

const chooseImage = async (wrapper: VueWrapper) => {
  const file = new File([new Uint8Array(2 * 1024 * 1024)], 'product.png', { type: 'image/png' })
  const input = wrapper.get<HTMLInputElement>('input[type="file"]')
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
  await input.trigger('change')
  await flushPromises()
  return file
}

describe('image uploads', () => {
  beforeEach(() => {
    post.mockReset()
    errorMessage.mockReset()
    vi.stubEnv('APP_BASE_URL', 'http://localhost:8080')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it.each(['images', 'avatars'] as const)(
    'uploads a large image for %s and saves its URL',
    async (type) => {
      const url = `/${type}/uploaded.png`
      post.mockResolvedValueOnce({ data: { status: true, payload: { url } } })
      const wrapper = mount(ImageUploader, {
        props: { type },
        global: { stubs: { ElImage: true } },
      })

      const file = await chooseImage(wrapper)

      expect(post).toHaveBeenCalledOnce()
      expect(post.mock.calls[0]?.[0]).toBe(`/upload/${type}`)
      expect(post.mock.calls[0]?.[1].get('file')).toBe(file)
      expect(wrapper.emitted('update:modelValue')).toEqual([[url]])
      expect(errorMessage).not.toHaveBeenCalled()
      expect(wrapper.findComponent(ElUpload).props('disabled')).toBe(false)
    },
  )

  it('shows the storage error, preserves the previous image and allows another upload', async () => {
    post.mockRejectedValueOnce(
      new AxiosError('Request failed', 'ERR_BAD_RESPONSE', undefined, undefined, {
        status: 503,
        statusText: 'Service Unavailable',
        data: { status: false, payload: { message: '文件服务暂不可用，请稍后重试' } },
        headers: {},
        config: { headers: new AxiosHeaders() },
      }),
    )
    const wrapper = mount(ImageUploader, {
      props: { type: 'images', modelValue: '/images/previous.png' },
      global: { stubs: { ElImage: true } },
    })

    await chooseImage(wrapper)

    expect(errorMessage).toHaveBeenCalledWith('文件服务暂不可用，请稍后重试')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.findComponent(ElUpload).props('disabled')).toBe(false)

    post.mockResolvedValueOnce({ data: { status: true, payload: { url: '/images/retry.png' } } })
    await chooseImage(wrapper)

    expect(wrapper.emitted('update:modelValue')).toEqual([['/images/retry.png']])
  })

  it.each([
    [401, '登录已失效，请重新登录后上传'],
    [403, '当前账号没有上传权限'],
  ] as const)('explains HTTP %s errors without a response message', async (status, message) => {
    post.mockRejectedValueOnce(
      new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
        status,
        statusText: status === 401 ? 'Unauthorized' : 'Forbidden',
        data: '',
        headers: {},
        config: { headers: new AxiosHeaders() },
      }),
    )
    const wrapper = mount(ImageUploader, {
      props: { type: 'images', modelValue: '/images/previous.png' },
      global: { stubs: { ElImage: true } },
    })

    await chooseImage(wrapper)

    expect(errorMessage).toHaveBeenCalledWith(message)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.findComponent(ElUpload).props('disabled')).toBe(false)
  })

  it('shows a retry message when the request has no response', async () => {
    post.mockRejectedValueOnce(new AxiosError('Network Error', 'ERR_NETWORK'))
    const wrapper = mount(ImageUploader, { props: { type: 'images' } })

    await chooseImage(wrapper)

    expect(errorMessage).toHaveBeenCalledWith('上传失败，请稍后重试')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.findComponent(ElUpload).props('disabled')).toBe(false)
  })
})
