<template>
  <ElButton
    text
    class="language-switch"
    :aria-label="locale === 'zh-CN' ? 'Switch to English' : '切换到中文'"
    :title="locale === 'zh-CN' ? 'Switch to English' : '切换到中文'"
    @click="toggleLocale"
  >
    <i class="bi bi-translate" aria-hidden="true"></i>
    <span aria-hidden="true">{{ locale === 'zh-CN' ? 'EN' : '中' }}</span>
  </ElButton>
</template>

<script setup lang="ts">
import { ElButton } from 'element-plus'
import { useLocale } from '@/i18n'

const { locale, toggleLocale } = useLocale()
</script>

<style lang="scss" scoped>
.language-switch {
  flex-shrink: 0;
  min-width: 48px;
  height: 32px;
  margin: 0;
  padding: 0 6px;
  font-size: 0.75rem;

  :deep(> span) {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }

  i {
    font-size: 1rem;
  }
}
</style>
