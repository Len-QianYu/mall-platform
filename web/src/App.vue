<template>
    <RouterView />
</template>

<script setup lang="ts">
import { watchEffect } from 'vue'
import { useTheme } from '@/utils/useTheme'

const { isDark } = useTheme()

// 日夜主题全站共用：Element Plus 的暗色变量挂在 <html class="dark"> 上，
// 挂到 body 的弹层（下拉、抽屉、消息）也会跟着切换
watchEffect(() => {
  document.documentElement.classList.toggle('dark', isDark.value)
})
</script>

<style scoped></style>
