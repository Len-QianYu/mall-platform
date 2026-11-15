import { describe, it, expect, afterEach } from 'vitest'
import { nextTick } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { mount } from '@vue/test-utils'

import App from '../App.vue'
import { useTheme } from '@/utils/useTheme'

// App 只做两件事：渲染当前路由的页面、按日夜主题给 <html> 挂 dark 类
const mountApp = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<p class="routed-page">routed</p>' } }],
  })
  router.push('/')
  await router.isReady()
  return mount(App, { global: { plugins: [router] } })
}

describe('App', () => {
  afterEach(() => {
    useTheme().isDark.value = false
    document.documentElement.classList.remove('dark')
  })

  it('renders the current route', async () => {
    const wrapper = await mountApp()
    expect(wrapper.find('.routed-page').exists()).toBe(true)
  })

  it('toggles the dark class on <html> with the theme', async () => {
    const { isDark, toggleTheme } = useTheme()
    isDark.value = false
    await mountApp()
    expect(document.documentElement.classList.contains('dark')).toBe(false)

    toggleTheme()
    await nextTick()
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(localStorage.getItem('theme')).toBe('dark')

    toggleTheme()
    await nextTick()
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })
})
