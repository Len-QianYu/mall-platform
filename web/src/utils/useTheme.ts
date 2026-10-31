import { ref, watch } from 'vue'

const STORAGE_KEY = 'theme'

// 模块级单例：登录、注册、后台等所有页面共享同一个日夜状态，刷新后从 localStorage 恢复
const isDark = ref(localStorage.getItem(STORAGE_KEY) === 'dark')

watch(isDark, (dark) => {
  localStorage.setItem(STORAGE_KEY, dark ? 'dark' : 'light')
})

// 只管状态；<html> 上的 dark 类由 App.vue 统一挂载
export function useTheme() {
  const toggleTheme = () => {
    isDark.value = !isDark.value
  }

  return { isDark, toggleTheme }
}
