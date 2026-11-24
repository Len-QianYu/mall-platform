<template>
  <ElConfigProvider :locale="elementLocale">
    <RouterView />
  </ElConfigProvider>
</template>

<script setup lang="ts">
import { watchEffect } from 'vue'
import { ElConfigProvider } from 'element-plus'
import { useTheme } from '@/utils/useTheme'
import { useLocale } from '@/i18n'

const { isDark } = useTheme()
const { locale, elementLocale } = useLocale()

// 日夜主题全站共用：Element Plus 的暗色变量挂在 <html class="dark"> 上，
// 挂到 body 的弹层（下拉、抽屉、消息）也会跟着切换
watchEffect(() => {
  document.documentElement.classList.toggle('dark', isDark.value)
  document.documentElement.lang = locale.value
})
</script>

<style scoped></style>
