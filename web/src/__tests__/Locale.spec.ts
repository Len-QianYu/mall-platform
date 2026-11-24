import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const storageKey = 'prompt-pals-locale'

describe('shared language preference', () => {
  beforeEach(() => {
    vi.resetModules()
    localStorage.removeItem(storageKey)
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.removeItem(storageKey)
  })

  it('defaults to Chinese and ignores unsupported stored languages', async () => {
    localStorage.setItem(storageKey, 'unsupported')
    const { t, useLocale } = await import('@/i18n')
    expect(useLocale().locale.value).toBe('zh-CN')
    expect(t('首页')).toBe('首页')
    expect(useLocale().elementLocale.value.name).toBe('zh-cn')
  })

  it('restores the chosen language when the module is loaded again', async () => {
    const { setLocale } = await import('@/i18n')
    setLocale('en')
    expect(localStorage.getItem(storageKey)).toBe('en')

    vi.resetModules()
    const { t, useLocale } = await import('@/i18n')
    expect(useLocale().locale.value).toBe('en')
    expect(useLocale().elementLocale.value.name).toBe('en')
    expect(t('首页')).toBe('Home')
  })

  it('continues switching when browser storage is unavailable', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    const { t, useLocale } = await import('@/i18n')
    expect(() => useLocale().toggleLocale()).not.toThrow()
    expect(t('首页')).toBe('Home')
    useLocale().toggleLocale()
    expect(t('首页')).toBe('首页')
  })

  it('translates interface and backend messages while retaining inserted account data', async () => {
    const { setLocale, t } = await import('@/i18n')
    setLocale('en')
    expect(t('编辑用户 {name}', { name: '千羽 {count}' })).toBe('Edit user 千羽 {count}')
    expect(t('用户名或密码错误')).toBe('Incorrect username or password')
    expect(t('未收录的原文')).toBe('未收录的原文')
    expect(t('constructor')).toBe('constructor')
    expect(t('{constructor}')).toBe('{constructor}')
    expect(t('{b}<br />{c} 笔 · {d}%')).toBe('{b}<br />{c} orders · {d}%')
  })
})
